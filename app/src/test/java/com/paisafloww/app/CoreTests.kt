package com.paisafloww.app

import com.paisafloww.app.data.BackupCodec
import com.paisafloww.app.data.BackupData
import com.paisafloww.app.data.BackupException
import com.paisafloww.app.data.CategoryEntity
import com.paisafloww.app.data.Direction
import com.paisafloww.app.data.LoanEntity
import com.paisafloww.app.data.RepaymentEntity
import com.paisafloww.app.data.SavingsEntryEntity
import com.paisafloww.app.data.SavingsKind
import com.paisafloww.app.data.SettingEntity
import com.paisafloww.app.data.SettingKeys
import com.paisafloww.app.data.TransactionEntity
import com.paisafloww.app.data.TxType
import com.paisafloww.app.domain.Accounting
import com.paisafloww.app.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val DAY = 20_000L

private fun rs(rupees: Long): Long = rupees * 100

private fun tx(id: Long, type: String, paise: Long, day: Long = DAY) =
    TransactionEntity(id = id, type = type, amountPaise = paise, category = "Food", dateEpochDay = day, createdAt = 1L)

private fun loan(id: Long, dir: String, paise: Long, writtenOff: Long = 0L) = LoanEntity(
    id = id, friendName = "Rahul", direction = dir, principalPaise = paise, dateEpochDay = DAY,
    writtenOffPaise = writtenOff, createdAt = 1L, updatedAt = 1L
)

private fun rep(id: Long, loanId: Long, paise: Long) =
    RepaymentEntity(id = id, loanId = loanId, amountPaise = paise, dateEpochDay = DAY, createdAt = 1L)

private fun sav(id: Long, kind: String, paise: Long) =
    SavingsEntryEntity(id = id, kind = kind, amountPaise = paise, dateEpochDay = DAY, createdAt = 1L)

private fun balance(
    start: Long,
    txs: List<TransactionEntity> = emptyList(),
    loans: List<LoanEntity> = emptyList(),
    reps: List<RepaymentEntity> = emptyList(),
    savings: List<SavingsEntryEntity> = emptyList()
) = Accounting.availableBalance(start, txs, loans, reps, savings)

class LoanAccountingTest {

    @Test fun test1_borrowing_isNotIncome() {
        val txs = listOf(tx(1, TxType.EXPENSE, rs(500)))
        val loans = listOf(loan(1, Direction.BORROWED, rs(2000)))
        assertEquals(rs(6500), balance(rs(5000), txs, loans))
        val views = Accounting.loanViews(loans, emptyList())
        assertEquals(rs(2000), Accounting.loanTotals(views, DAY).borrowedOwedPaise)
        val summary = Accounting.periodSummary(txs, null, null)
        assertEquals(0L, summary.income)
        assertEquals(rs(500), summary.expense)
    }

    @Test fun test2_partialRepayment() {
        val views = Accounting.loanViews(listOf(loan(1, Direction.BORROWED, rs(2000))), listOf(rep(1, 1, rs(500))))
        assertEquals(rs(1500), views[0].outstandingPaise)
    }

    @Test fun test2b_repaymentIsNotAnExpenseAndReducesBalance() {
        val txs = listOf(tx(1, TxType.EXPENSE, rs(500)))
        val loans = listOf(loan(1, Direction.BORROWED, rs(2000)))
        val reps = listOf(rep(1, 1, rs(500)))
        assertEquals(rs(6000), balance(rs(5000), txs, loans, reps))
        assertEquals(rs(500), Accounting.periodSummary(txs, null, null).expense)
    }

    @Test fun test3_multipleRepayments() {
        val reps = listOf(rep(1, 1, rs(500)), rep(2, 1, rs(300)))
        val views = Accounting.loanViews(listOf(loan(1, Direction.BORROWED, rs(2000))), reps)
        assertEquals(rs(1200), views[0].outstandingPaise)
        assertFalse(views[0].isPaid)
    }

    @Test fun test4_fullyRepaidIsPaid() {
        val reps = listOf(rep(1, 1, rs(1500)), rep(2, 1, rs(500)))
        val views = Accounting.loanViews(listOf(loan(1, Direction.BORROWED, rs(2000))), reps)
        assertEquals(0L, views[0].outstandingPaise)
        assertTrue(views[0].isPaid)
    }

    @Test fun test5_excessRepaymentRejected() {
        assertNotNull(Accounting.validateRepayment(rs(2000), 0L, rs(1500), rs(600)))
        assertNull(Accounting.validateRepayment(rs(2000), 0L, rs(1500), rs(500)))
        assertNotNull(Accounting.validateRepayment(rs(2000), 0L, rs(2000), rs(1))) // nothing left
        assertNotNull(Accounting.validateRepayment(rs(2000), rs(500), rs(1000), rs(600))) // settled part counts
    }

    @Test fun test6_lending() {
        val loans = listOf(loan(1, Direction.LENT, rs(1000)))
        assertEquals(rs(4000), balance(rs(5000), loans = loans))
        val views = Accounting.loanViews(loans, emptyList())
        assertEquals(rs(1000), Accounting.loanTotals(views, DAY).lentOwedPaise)
        assertEquals(0L, Accounting.periodSummary(emptyList(), null, null).expense)
    }

    @Test fun test7_repaymentReceived() {
        val loans = listOf(loan(1, Direction.LENT, rs(1000)))
        val reps = listOf(rep(1, 1, rs(400)))
        assertEquals(rs(4400), balance(rs(5000), loans = loans, reps = reps))
        assertEquals(rs(600), Accounting.loanViews(loans, reps)[0].outstandingPaise)
    }

    @Test fun manualSettlement_closesLoanWithoutMovingCash() {
        val loans = listOf(loan(1, Direction.BORROWED, rs(1000), writtenOff = rs(1000)))
        val views = Accounting.loanViews(loans, emptyList())
        assertTrue(views[0].isPaid)
        assertEquals(rs(6000), balance(rs(5000), loans = loans)) // cash from borrowing stays, no repayment cash out
    }

    @Test fun principalEditCannotGoBelowRepaid() {
        assertNotNull(Accounting.validatePrincipal(rs(400), rs(500), 0L))
        assertNull(Accounting.validatePrincipal(rs(500), rs(500), 0L))
    }

    @Test fun dashboardTotalsKeepBorrowedAndLentSeparate() {
        val loans = listOf(loan(1, Direction.BORROWED, rs(300)), loan(2, Direction.LENT, rs(700)))
        val t = Accounting.loanTotals(Accounting.loanViews(loans, emptyList()), DAY)
        assertEquals(rs(300), t.borrowedOwedPaise)
        assertEquals(rs(700), t.lentOwedPaise)
        assertEquals(1, t.activeBorrowed)
        assertEquals(1, t.activeLent)
    }

    @Test fun overdueAndDueSoon() {
        val l = loan(1, Direction.BORROWED, rs(100)).copy(dueEpochDay = DAY - 1)
        val soon = loan(2, Direction.LENT, rs(100)).copy(dueEpochDay = DAY + 3)
        val t = Accounting.loanTotals(Accounting.loanViews(listOf(l, soon), emptyList()), DAY)
        assertEquals(1, t.overdue)
        assertEquals(1, t.dueSoon)
    }

    @Test fun test10_editingTransactionsAndRepaymentsUpdatesDerivedTotals() {
        val loans = listOf(loan(1, Direction.BORROWED, rs(2000)))
        var txs = listOf(tx(1, TxType.EXPENSE, rs(500)))
        var reps = listOf(rep(1, 1, rs(500)))
        assertEquals(rs(6000), balance(rs(5000), txs, loans, reps))
        // edit expense 500 -> 700
        txs = txs.map { if (it.id == 1L) it.copy(amountPaise = rs(700)) else it }
        assertEquals(rs(5800), balance(rs(5000), txs, loans, reps))
        // edit repayment 500 -> 800
        reps = reps.map { it.copy(amountPaise = rs(800)) }
        assertEquals(rs(1200), Accounting.loanViews(loans, reps)[0].outstandingPaise)
        assertEquals(rs(5500), balance(rs(5000), txs, loans, reps))
        // delete the repayment
        assertEquals(rs(2000), Accounting.loanViews(loans, emptyList())[0].outstandingPaise)
        assertEquals(rs(6300), balance(rs(5000), txs, loans, emptyList()))
    }
}

class SavingsTest {
    @Test fun test11_savingsAreTransfersNotIncomeOrExpense() {
        val txs = listOf(tx(1, TxType.EXPENSE, rs(500)))
        val savings = listOf(sav(1, SavingsKind.DEPOSIT, rs(1000)))
        assertEquals(rs(1000), Accounting.savedBalance(savings))
        assertEquals(rs(3500), balance(rs(5000), txs, savings = savings))
        val s = Accounting.periodSummary(txs, null, null)
        assertEquals(0L, s.income)
        assertEquals(rs(500), s.expense)
        // withdraw 400
        val after = savings + sav(2, SavingsKind.WITHDRAW, rs(400))
        assertEquals(rs(600), Accounting.savedBalance(after))
        assertEquals(rs(3900), balance(rs(5000), txs, savings = after))
    }

    @Test fun savingsRulesRejectOverdraw() {
        assertNotNull(Accounting.checkSavingsChange(rs(100), rs(-50), rs(150)))   // more than available
        assertNotNull(Accounting.checkSavingsChange(rs(100), rs(200), rs(-10)))   // negative savings
        assertNull(Accounting.checkSavingsChange(rs(100), rs(0), rs(100)))
    }
}

class MoneyTest {
    @Test fun parsesExactPaise() {
        assertEquals(10050L, Money.parse("100.50").paise)
        assertEquals(10000L, Money.parse("100").paise)
        assertEquals(1990L, Money.parse("19.9").paise)
        assertEquals(123456L, Money.parse("1,234.56").paise)
    }

    @Test fun test12_invalidInputsRejected() {
        val bad = listOf("", " ", "0", "0.00", "-5", "abc", "1.234", "1e5", "1..2", ".5", "99999999999999", "12,34,56,78,90,12,34")
        for (b in bad) {
            val r = Money.parse(b)
            assertNull("should reject '$b'", r.paise)
            assertNotNull(r.error)
        }
        assertNull(Money.parse("0", allowZero = false).paise)
        assertEquals(0L, Money.parse("0", allowZero = true).paise)
        assertNotNull(Money.checkStored(0L))
        assertNotNull(Money.checkStored(-1L))
        assertNotNull(Money.checkStored(Money.MAX_PAISE + 1))
        assertNull(Money.checkStored(Money.MAX_PAISE))
    }

    @Test fun indianGroupingFormat() {
        assertEquals("\u20B90.00", Money.format(0))
        assertEquals("\u20B9999.99", Money.format(99999))
        assertEquals("\u20B91,000.00", Money.format(100000))
        assertEquals("\u20B91,23,456.50", Money.format(12345650))
        assertEquals("\u20B91,00,00,000.00", Money.format(1_000_000_000))
        assertEquals("-\u20B9500.00", Money.format(-50000))
    }

    @Test fun toInputRoundTrips() {
        assertEquals("100.50", Money.toInput(10050))
        assertEquals("0.05", Money.toInput(5))
        assertEquals(10050L, Money.parse(Money.toInput(10050)).paise)
    }
}

class BackupTest {
    private fun sample(): BackupData {
        val loans = listOf(
            loan(1, Direction.BORROWED, rs(2000)),
            loan(2, Direction.LENT, rs(1000)).copy(dueEpochDay = DAY + 10, phone = "+91 98765 43210", notes = "n \"quoted\"")
        )
        return BackupData(
            schemaVersion = BackupCodec.SCHEMA_VERSION,
            exportedAt = 1_700_000_000_000L,
            appVersion = "1.0.0",
            transactions = listOf(
                tx(1, TxType.INCOME, rs(5000)),
                tx(2, TxType.EXPENSE, 12345).copy(note = "Dinner, with \u20B9 sign")
            ),
            loans = loans,
            repayments = listOf(rep(1, 1, rs(500)), rep(2, 2, rs(400))),
            savings = listOf(sav(1, SavingsKind.DEPOSIT, rs(1000))),
            categories = listOf(CategoryEntity(TxType.EXPENSE, "Chai")),
            settings = listOf(SettingEntity(SettingKeys.START, rs(5000).toString()), SettingEntity(SettingKeys.THEME, "dark"))
        )
    }

    @Test fun test8_roundTripKeepsEverything() {
        val original = sample()
        val back = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original, back)
        // derived state must be identical too
        val a = balance(rs(5000), original.transactions, original.loans, original.repayments, original.savings)
        val b = balance(rs(5000), back.transactions, back.loans, back.repayments, back.savings)
        assertEquals(a, b)
    }

    private fun expectReject(text: String) {
        try {
            BackupCodec.decode(text)
            fail("backup should have been rejected")
        } catch (e: BackupException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test fun rejectsMalformedFiles() {
        expectReject("")
        expectReject("not json")
        expectReject("{}")
        expectReject("[1,2,3]")
        expectReject("{\"format\":\"other\"}")
    }

    @Test fun rejectsBadContent() {
        val good = BackupCodec.encode(sample())
        // repayment pointing at a missing loan
        expectReject(good.replace("\"loanId\": 2", "\"loanId\": 99"))
        // negative / zero amounts
        expectReject(good.replace("\"amountPaise\": 12345", "\"amountPaise\": -12345"))
        expectReject(good.replace("\"amountPaise\": 12345", "\"amountPaise\": 0"))
        // fractional amount must not be silently truncated
        expectReject(good.replace("\"amountPaise\": 12345", "\"amountPaise\": 123.45"))
        // unknown type
        expectReject(good.replace("\"INCOME\"", "\"BONUS\""))
        // newer schema
        expectReject(good.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99"))
        // unknown setting must not be dropped silently
        expectReject(good.replace("\"theme\"", "\"mystery\""))
    }

    @Test fun rejectsDuplicateIds() {
        val d = sample().copy(transactions = listOf(tx(1, TxType.INCOME, 100), tx(1, TxType.EXPENSE, 200)))
        expectReject(BackupCodec.encode(d))
    }

    @Test fun rejectsRepaymentsLargerThanLoan() {
        val d = sample().copy(repayments = listOf(rep(1, 1, rs(1500)), rep(2, 1, rs(600))))
        expectReject(BackupCodec.encode(d))
    }

    @Test fun csvNeutralisesFormulas() {
        val csv = com.paisafloww.app.data.CsvExport.transactions(
            listOf(tx(1, TxType.EXPENSE, 10050).copy(note = "=HYPERLINK(\"x\")"))
        )
        assertTrue(csv.contains("'=HYPERLINK"))
        assertTrue(csv.contains("100.50"))
    }
}
