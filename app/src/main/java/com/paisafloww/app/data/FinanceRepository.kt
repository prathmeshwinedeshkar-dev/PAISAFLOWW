package com.paisafloww.app.data

import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import com.paisafloww.app.domain.Accounting
import com.paisafloww.app.domain.Dates
import com.paisafloww.app.domain.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.coroutines.cancellation.CancellationException

object SettingKeys {
    const val START = "startingBalancePaise"
    const val GOAL = "savingsGoalPaise"
    const val THEME = "theme"
    const val DEFAULT_GOAL = 500_000L // Rs 5,000
    val THEMES = setOf("system", "light", "dark")
}

data class AppSettings(
    val startingBalancePaise: Long = 0L,
    val savingsGoalPaise: Long = SettingKeys.DEFAULT_GOAL,
    val theme: String = "system"
)

fun parseSettings(list: List<SettingEntity>): AppSettings {
    val m = list.associate { it.key to it.value }
    return AppSettings(
        startingBalancePaise = m[SettingKeys.START]?.toLongOrNull()?.takeIf { it >= 0 } ?: 0L,
        savingsGoalPaise = m[SettingKeys.GOAL]?.toLongOrNull()?.takeIf { it > 0 } ?: SettingKeys.DEFAULT_GOAL,
        theme = m[SettingKeys.THEME]?.takeIf { it in SettingKeys.THEMES } ?: "system"
    )
}

class Snapshot(
    val transactions: List<TransactionEntity>,
    val loans: List<LoanEntity>,
    val repayments: List<RepaymentEntity>,
    val savings: List<SavingsEntryEntity>,
    val categories: List<CategoryEntity>,
    val settings: AppSettings
)

/**
 * Every write returns null on success or a human-readable error message.
 * Validation lives here (not only in the UI) and multi-record changes run inside one DB transaction.
 */
class FinanceRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    val snapshot: Flow<Snapshot> = combine(
        combine(dao.observeTransactions(), dao.observeLoans(), dao.observeRepayments()) { a, b, c ->
            Triple(a, b, c)
        },
        combine(dao.observeSavings(), dao.observeCategories(), dao.observeSettings()) { a, b, c ->
            Triple(a, b, c)
        }
    ) { x, y ->
        Snapshot(x.first, x.second, x.third, y.first, y.second, parseSettings(y.third))
    }

    private suspend fun guarded(block: suspend () -> String?): String? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SQLiteFullException) {
        "Not enough storage space on the phone."
    } catch (e: Exception) {
        "Could not save (" + e.javaClass.simpleName + "). Nothing was changed."
    }

    // ------------------------------------------------------------ transactions

    suspend fun saveTransaction(
        id: Long?, type: String, amount: Long, category: String, note: String, date: Long
    ): String? {
        if (type != TxType.INCOME && type != TxType.EXPENSE) return "Invalid transaction type"
        Money.checkStored(amount)?.let { return it }
        val cat = category.trim()
        if (cat.isEmpty()) return "Choose a category"
        if (cat.length > 40) return "Category name is too long"
        if (note.length > 500) return "Note is too long"
        if (!Dates.valid(date)) return "Invalid date"
        return guarded {
            db.withTransaction<String?> {
                val now = System.currentTimeMillis()
                if (id == null || id == 0L) {
                    dao.insertTransaction(
                        TransactionEntity(
                            type = type, amountPaise = amount, category = cat,
                            note = note.trim(), dateEpochDay = date, createdAt = now
                        )
                    )
                } else {
                    val old = dao.getTransaction(id) ?: return@withTransaction "Transaction not found"
                    dao.updateTransaction(
                        old.copy(type = type, amountPaise = amount, category = cat, note = note.trim(), dateEpochDay = date)
                    )
                }
                dao.insertCategory(CategoryEntity(type, cat))
                null
            }
        }
    }

    suspend fun deleteTransaction(id: Long): String? = guarded {
        dao.deleteTransaction(id)
        null
    }

    // ------------------------------------------------------------ loans

    suspend fun saveLoan(
        id: Long?, friend: String, direction: String, principal: Long, date: Long,
        due: Long?, phone: String, reason: String, notes: String
    ): String? {
        val name = friend.trim()
        if (name.isEmpty()) return "Enter your friend's name"
        if (name.length > 60) return "Name is too long"
        if (direction != Direction.BORROWED && direction != Direction.LENT) return "Choose borrowed or lent"
        Money.checkStored(principal)?.let { return it }
        if (!Dates.valid(date)) return "Invalid date"
        if (due != null) {
            if (!Dates.valid(due)) return "Invalid due date"
            if (due < date) return "Due date can't be before the loan date"
        }
        val ph = phone.trim()
        if (ph.length > 20 || !ph.all { it.isDigit() || it in "+-() " }) return "Phone number looks invalid"
        if (reason.length > 200 || notes.length > 1000) return "Text is too long"
        return guarded {
            db.withTransaction<String?> {
                val now = System.currentTimeMillis()
                if (id == null || id == 0L) {
                    dao.insertLoan(
                        LoanEntity(
                            friendName = name, direction = direction, principalPaise = principal,
                            dateEpochDay = date, dueEpochDay = due, phone = ph,
                            reason = reason.trim(), notes = notes.trim(), createdAt = now, updatedAt = now
                        )
                    )
                    null
                } else {
                    val old = dao.getLoan(id) ?: return@withTransaction "Loan not found"
                    val repaid = dao.sumRepayments(id, -1L)
                    Accounting.validatePrincipal(principal, repaid, old.writtenOffPaise)
                        ?.let { return@withTransaction it }
                    dao.updateLoan(
                        old.copy(
                            friendName = name, principalPaise = principal, dateEpochDay = date,
                            dueEpochDay = due, phone = ph, reason = reason.trim(),
                            notes = notes.trim(), updatedAt = now
                        )
                    )
                    null
                }
            }
        }
    }

    suspend fun saveRepayment(id: Long?, loanId: Long, amount: Long, date: Long, note: String): String? {
        Money.checkStored(amount)?.let { return it }
        if (!Dates.valid(date)) return "Invalid date"
        if (note.length > 500) return "Note is too long"
        return guarded {
            db.withTransaction<String?> {
                val loan = dao.getLoan(loanId) ?: return@withTransaction "Loan not found"
                val excludeId = if (id == null || id == 0L) -1L else id
                val others = dao.sumRepayments(loanId, excludeId)
                Accounting.validateRepayment(loan.principalPaise, loan.writtenOffPaise, others, amount)
                    ?.let { return@withTransaction it }
                val now = System.currentTimeMillis()
                if (excludeId == -1L) {
                    dao.insertRepayment(
                        RepaymentEntity(loanId = loanId, amountPaise = amount, dateEpochDay = date, note = note.trim(), createdAt = now)
                    )
                } else {
                    val old = dao.getRepayment(excludeId) ?: return@withTransaction "Repayment not found"
                    if (old.loanId != loanId) return@withTransaction "Repayment belongs to a different loan"
                    dao.updateRepayment(old.copy(amountPaise = amount, dateEpochDay = date, note = note.trim()))
                }
                dao.updateLoan(loan.copy(updatedAt = now))
                null
            }
        }
    }

    suspend fun deleteRepayment(id: Long): String? = guarded {
        db.withTransaction<String?> {
            val old = dao.getRepayment(id) ?: return@withTransaction "Repayment not found"
            dao.deleteRepayment(id)
            dao.getLoan(old.loanId)?.let { dao.updateLoan(it.copy(updatedAt = System.currentTimeMillis())) }
            null
        }
    }

    /** Manual settlement: closes the outstanding amount without recording any cash movement. */
    suspend fun settleLoan(loanId: Long, reason: String): String? {
        val why = reason.trim()
        if (why.isEmpty()) return "Enter a reason for settling this loan"
        if (why.length > 200) return "Reason is too long"
        return guarded {
            db.withTransaction<String?> {
                val loan = dao.getLoan(loanId) ?: return@withTransaction "Loan not found"
                val repaid = dao.sumRepayments(loanId, -1L)
                val outstanding = loan.principalPaise - repaid - loan.writtenOffPaise
                if (outstanding <= 0L) return@withTransaction "This loan is already paid"
                dao.updateLoan(
                    loan.copy(
                        writtenOffPaise = loan.writtenOffPaise + outstanding,
                        settlementReason = why,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                null
            }
        }
    }

    suspend fun reopenLoan(loanId: Long): String? = guarded {
        db.withTransaction<String?> {
            val loan = dao.getLoan(loanId) ?: return@withTransaction "Loan not found"
            if (loan.writtenOffPaise == 0L) {
                return@withTransaction "This loan was repaid with recorded repayments. Edit or delete a repayment instead."
            }
            dao.updateLoan(
                loan.copy(writtenOffPaise = 0L, settlementReason = "", archived = false, updatedAt = System.currentTimeMillis())
            )
            null
        }
    }

    suspend fun setArchived(loanId: Long, archived: Boolean): String? = guarded {
        db.withTransaction<String?> {
            val loan = dao.getLoan(loanId) ?: return@withTransaction "Loan not found"
            val repaid = dao.sumRepayments(loanId, -1L)
            if (archived && loan.principalPaise - repaid - loan.writtenOffPaise > 0L) {
                return@withTransaction "Only paid loans can be archived"
            }
            dao.updateLoan(loan.copy(archived = archived, updatedAt = System.currentTimeMillis()))
            null
        }
    }

    /** Deletes the loan AND all of its repayments (foreign-key cascade). */
    suspend fun deleteLoan(loanId: Long): String? = guarded {
        dao.deleteLoan(loanId)
        null
    }

    // ------------------------------------------------------------ savings

    suspend fun saveSaving(id: Long?, kind: String, amount: Long, date: Long, note: String): String? {
        if (kind != SavingsKind.DEPOSIT && kind != SavingsKind.WITHDRAW) return "Invalid savings type"
        Money.checkStored(amount)?.let { return it }
        if (!Dates.valid(date)) return "Invalid date"
        if (note.length > 500) return "Note is too long"
        return guarded {
            db.withTransaction<String?> {
                val settings = parseSettings(dao.allSettings())
                val txs = dao.allTransactions()
                val loans = dao.allLoans()
                val reps = dao.allRepayments()
                val sav = dao.allSavings()
                val now = System.currentTimeMillis()
                val isNew = id == null || id == 0L
                if (!isNew && sav.none { it.id == id }) return@withTransaction "Entry not found"
                val newList = if (isNew) {
                    sav + SavingsEntryEntity(kind = kind, amountPaise = amount, dateEpochDay = date, note = note.trim(), createdAt = now)
                } else {
                    sav.map {
                        if (it.id == id) it.copy(kind = kind, amountPaise = amount, dateEpochDay = date, note = note.trim()) else it
                    }
                }
                val before = Accounting.availableBalance(settings.startingBalancePaise, txs, loans, reps, sav)
                val after = Accounting.availableBalance(settings.startingBalancePaise, txs, loans, reps, newList)
                Accounting.checkSavingsChange(before, after, Accounting.savedBalance(newList))
                    ?.let { return@withTransaction it }
                if (isNew) {
                    dao.insertSaving(SavingsEntryEntity(kind = kind, amountPaise = amount, dateEpochDay = date, note = note.trim(), createdAt = now))
                } else {
                    val old = sav.first { it.id == id }
                    dao.updateSaving(old.copy(kind = kind, amountPaise = amount, dateEpochDay = date, note = note.trim()))
                }
                null
            }
        }
    }

    suspend fun deleteSaving(id: Long): String? = guarded {
        db.withTransaction<String?> {
            val sav = dao.allSavings()
            val newList = sav.filter { it.id != id }
            val saved = Accounting.savedBalance(newList)
            if (saved < 0L) return@withTransaction "Deleting this entry would make your savings negative"
            dao.deleteSaving(id)
            null
        }
    }

    // ------------------------------------------------------------ settings

    suspend fun setSavingsGoal(paise: Long): String? {
        Money.checkStored(paise)?.let { return it }
        return guarded {
            dao.upsertSetting(SettingEntity(SettingKeys.GOAL, paise.toString()))
            null
        }
    }

    suspend fun setStartingBalance(paise: Long): String? {
        if (paise < 0L || paise > Money.MAX_PAISE) return "Amount is out of range"
        return guarded {
            dao.upsertSetting(SettingEntity(SettingKeys.START, paise.toString()))
            null
        }
    }

    suspend fun setTheme(theme: String): String? {
        if (theme !in SettingKeys.THEMES) return "Invalid theme"
        return guarded {
            dao.upsertSetting(SettingEntity(SettingKeys.THEME, theme))
            null
        }
    }

    // ------------------------------------------------------------ backup / restore

    suspend fun exportData(appVersion: String): BackupData = db.withTransaction {
        BackupData(
            schemaVersion = BackupCodec.SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion,
            transactions = dao.allTransactions(),
            loans = dao.allLoans(),
            repayments = dao.allRepayments(),
            savings = dao.allSavings(),
            categories = dao.allCategories(),
            settings = dao.allSettings()
        )
    }

    /**
     * All-or-nothing: the data was already fully validated by BackupCodec.decode, and the replace
     * runs in ONE transaction, so a failure leaves the existing data untouched.
     */
    suspend fun restore(data: BackupData): String? = guarded {
        db.withTransaction<String?> {
            dao.clearRepayments()
            dao.clearLoans()
            dao.clearTransactions()
            dao.clearSavings()
            dao.clearCategories()
            dao.clearSettings()
            dao.insertTransactions(data.transactions)
            dao.insertLoans(data.loans)
            dao.insertRepayments(data.repayments)
            dao.insertSavings(data.savings)
            dao.insertCategories(data.categories)
            dao.insertSettings(data.settings)
            null
        }
    }
}
