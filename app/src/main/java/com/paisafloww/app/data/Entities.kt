package com.paisafloww.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object TxType {
    const val INCOME = "INCOME"
    const val EXPENSE = "EXPENSE"
}

object Direction {
    const val BORROWED = "BORROWED"
    const val LENT = "LENT"
}

object SavingsKind {
    const val DEPOSIT = "DEPOSIT"
    const val WITHDRAW = "WITHDRAW"
}

/** All money values are stored as integer paise (Rs 100.50 = 10050). Dates are epoch days. */
@Entity(tableName = "transactions", indices = [Index("dateEpochDay")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amountPaise: Long,
    val category: String,
    val note: String = "",
    val dateEpochDay: Long,
    val createdAt: Long
)

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val friendName: String,
    val direction: String,
    val principalPaise: Long,
    val dateEpochDay: Long,
    val dueEpochDay: Long? = null,
    val phone: String = "",
    val reason: String = "",
    val notes: String = "",
    val archived: Boolean = false,
    /** Amount closed by a manual settlement WITHOUT a recorded repayment (no cash moved). */
    val writtenOffPaise: Long = 0,
    val settlementReason: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "repayments",
    foreignKeys = [ForeignKey(
        entity = LoanEntity::class,
        parentColumns = ["id"],
        childColumns = ["loanId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("loanId")]
)
data class RepaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val amountPaise: Long,
    val dateEpochDay: Long,
    val note: String = "",
    val createdAt: Long
)

/** Savings ledger: moves money between "available" and "reserved for savings". Not income/expense. */
@Entity(tableName = "savings_entries")
data class SavingsEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val amountPaise: Long,
    val dateEpochDay: Long,
    val note: String = "",
    val createdAt: Long
)

@Entity(tableName = "categories", primaryKeys = ["type", "name"])
data class CategoryEntity(
    val type: String,
    val name: String
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
