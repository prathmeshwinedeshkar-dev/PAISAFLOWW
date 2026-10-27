package com.paisafloww.app.domain

import com.paisafloww.app.data.Direction
import com.paisafloww.app.data.LoanEntity
import com.paisafloww.app.data.RepaymentEntity
import com.paisafloww.app.data.SavingsEntryEntity
import com.paisafloww.app.data.SavingsKind
import com.paisafloww.app.data.TransactionEntity
import com.paisafloww.app.data.TxType
import java.time.LocalDate
import java.time.YearMonth

/**
 * ACCOUNTING MODEL (derived balance - nothing is stored twice, so nothing can drift):
 *
 * available = startingBalance
 *           + income - expenses                       (ordinary transactions)
 *           + borrowed principal   - repayments made by you   (loans you took)
 *           - lent principal       + repayments received      (loans you gave)
 *           - savedBalance                                    (money reserved for savings)
 *
 * Borrowing / lending / repayments / savings transfers are NEVER income or expenses.
 * A manual settlement ("written off") closes the outstanding amount WITHOUT moving cash.
 */
data class LoanView(val loan: LoanEntity, val repaidPaise: Long) {
    val outstandingPaise: Long get() = loan.principalPaise - repaidPaise - loan.writtenOffPaise
    val isPaid: Boolean get() = outstandingPaise <= 0L
    val isBorrowed: Boolean get() = loan.direction == Direction.BORROWED
    fun isOverdue(today: Long): Boolean {
        val due = loan.dueEpochDay
        return !isPaid && due != null && due < today
    }
    fun isDueSoon(today: Long, days: Long = 7): Boolean {
        val due = loan.dueEpochDay
        return !isPaid && due != null && due >= today && due <= today + days
    }
}

data class Summary(val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

data class LoanTotals(
    val borrowedOwedPaise: Long,
    val lentOwedPaise: Long,
    val activeBorrowed: Int,
    val activeLent: Int,
    val overdue: Int,
    val dueSoon: Int
)

data class MonthTotals(val month: YearMonth, val income: Long, val expense: Long)

data class Movement(
    val income: Long,
    val expense: Long,
    val borrowed: Long,
    val repaidByYou: Long,
    val lent: Long,
    val repaymentsReceived: Long,
    val savedIn: Long,
    val savedOut: Long
)

object Periods {
    val labels = listOf("This month", "Last month", "All time")

    fun range(index: Int, today: Long): Pair<Long?, Long?> {
        val current = YearMonth.from(LocalDate.ofEpochDay(today))
        return when (index) {
            0 -> month(current)
            1 -> month(current.minusMonths(1))
            else -> Pair(null, null)
        }
    }

    private fun month(ym: YearMonth): Pair<Long?, Long?> =
        Pair(ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay())
}

object Accounting {

    fun loanViews(loans: List<LoanEntity>, repayments: List<RepaymentEntity>): List<LoanView> {
        val sums = repayments.groupBy { it.loanId }.mapValues { entry -> entry.value.sumOf { it.amountPaise } }
        return loans.map { LoanView(it, sums[it.id] ?: 0L) }
    }

    fun savedBalance(entries: List<SavingsEntryEntity>): Long =
        entries.sumOf { if (it.kind == SavingsKind.DEPOSIT) it.amountPaise else -it.amountPaise }

    fun availableBalance(
        startingBalance: Long,
        transactions: List<TransactionEntity>,
        loans: List<LoanEntity>,
        repayments: List<RepaymentEntity>,
        savings: List<SavingsEntryEntity>
    ): Long {
        var balance = startingBalance
        for (t in transactions) {
            balance += if (t.type == TxType.INCOME) t.amountPaise else -t.amountPaise
        }
        val directions = loans.associate { it.id to it.direction }
        for (l in loans) {
            balance += if (l.direction == Direction.BORROWED) l.principalPaise else -l.principalPaise
        }
        for (r in repayments) {
            when (directions[r.loanId]) {
                Direction.BORROWED -> balance -= r.amountPaise
                Direction.LENT -> balance += r.amountPaise
                else -> Unit
            }
        }
        balance -= savedBalance(savings)
        return balance
    }

    private fun inRange(day: Long, from: Long?, to: Long?): Boolean =
        (from == null || day >= from) && (to == null || day <= to)

    /** Ordinary income/expense only. Loans, repayments and savings transfers are excluded on purpose. */
    fun periodSummary(transactions: List<TransactionEntity>, from: Long?, to: Long?): Summary {
        var income = 0L
        var expense = 0L
        for (t in transactions) {
            if (!inRange(t.dateEpochDay, from, to)) continue
            if (t.type == TxType.INCOME) income += t.amountPaise else expense += t.amountPaise
        }
        return Summary(income, expense)
    }

    fun categoryBreakdown(
        transactions: List<TransactionEntity>,
        type: String,
        from: Long?,
        to: Long?
    ): List<Pair<String, Long>> =
        transactions
            .filter { it.type == type && inRange(it.dateEpochDay, from, to) }
            .groupBy { it.category }
            .map { Pair(it.key, it.value.sumOf { x -> x.amountPaise }) }
            .sortedByDescending { it.second }

    fun monthlySeries(transactions: List<TransactionEntity>, today: Long, count: Int = 6): List<MonthTotals> {
        val end = YearMonth.from(LocalDate.ofEpochDay(today))
        return (count - 1 downTo 0).map { k ->
            val ym = end.minusMonths(k.toLong())
            val s = periodSummary(transactions, ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay())
            MonthTotals(ym, s.income, s.expense)
        }
    }

    fun loanTotals(views: List<LoanView>, today: Long): LoanTotals {
        val active = views.filter { !it.isPaid && !it.loan.archived }
        val borrowed = active.filter { it.isBorrowed }
        val lent = active.filter { !it.isBorrowed }
        return LoanTotals(
            borrowedOwedPaise = borrowed.sumOf { it.outstandingPaise },
            lentOwedPaise = lent.sumOf { it.outstandingPaise },
            activeBorrowed = borrowed.size,
            activeLent = lent.size,
            overdue = active.count { it.isOverdue(today) },
            dueSoon = active.count { it.isDueSoon(today) }
        )
    }

    /** Every kind of money movement in a period, kept separate so nothing is double counted. */
    fun movement(
        transactions: List<TransactionEntity>,
        loans: List<LoanEntity>,
        repayments: List<RepaymentEntity>,
        savings: List<SavingsEntryEntity>,
        from: Long?,
        to: Long?
    ): Movement {
        val s = periodSummary(transactions, from, to)
        val directions = loans.associate { it.id to it.direction }
        val borrowed = loans.filter { it.direction == Direction.BORROWED && inRange(it.dateEpochDay, from, to) }
            .sumOf { it.principalPaise }
        val lent = loans.filter { it.direction == Direction.LENT && inRange(it.dateEpochDay, from, to) }
            .sumOf { it.principalPaise }
        val repaidByYou = repayments
            .filter { directions[it.loanId] == Direction.BORROWED && inRange(it.dateEpochDay, from, to) }
            .sumOf { it.amountPaise }
        val received = repayments
            .filter { directions[it.loanId] == Direction.LENT && inRange(it.dateEpochDay, from, to) }
            .sumOf { it.amountPaise }
        val savedIn = savings.filter { it.kind == SavingsKind.DEPOSIT && inRange(it.dateEpochDay, from, to) }
            .sumOf { it.amountPaise }
        val savedOut = savings.filter { it.kind == SavingsKind.WITHDRAW && inRange(it.dateEpochDay, from, to) }
            .sumOf { it.amountPaise }
        return Movement(s.income, s.expense, borrowed, repaidByYou, lent, received, savedIn, savedOut)
    }

    // ------------------------------------------------------------ validation rules

    /** Returns an error message, or null when the repayment is acceptable. */
    fun validateRepayment(principal: Long, writtenOff: Long, otherRepaid: Long, amount: Long): String? {
        Money.checkStored(amount)?.let { return it }
        val outstanding = principal - writtenOff - otherRepaid
        if (outstanding <= 0L) return "This loan has nothing left to repay"
        if (amount > outstanding) {
            return "Repayment is more than the outstanding balance of " + Money.format(outstanding)
        }
        return null
    }

    /** Principal may never drop below what has already been repaid or settled. */
    fun validatePrincipal(newPrincipal: Long, repaid: Long, writtenOff: Long): String? {
        Money.checkStored(newPrincipal)?.let { return it }
        if (newPrincipal < repaid + writtenOff) {
            return "Amount can't be less than what is already repaid or settled (" +
                Money.format(repaid + writtenOff) + ")"
        }
        return null
    }

    /**
     * Savings rule: the reserved balance can never go negative, and a change may not push
     * the available balance below zero.
     */
    fun checkSavingsChange(availableBefore: Long, availableAfter: Long, savedAfter: Long): String? {
        if (savedAfter < 0L) return "Savings can't go below zero"
        if (availableAfter < availableBefore && availableAfter < 0L) {
            return "Not enough available balance for this savings amount"
        }
        return null
    }
}
