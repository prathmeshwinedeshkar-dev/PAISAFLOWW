package com.paisafloww.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    // ---- transactions
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransaction(id: Long): TransactionEntity?
    @Insert suspend fun insertTransaction(t: TransactionEntity): Long
    @Insert suspend fun insertTransactions(list: List<TransactionEntity>)
    @Update suspend fun updateTransaction(t: TransactionEntity)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTransaction(id: Long)
    @Query("SELECT * FROM transactions ORDER BY id") suspend fun allTransactions(): List<TransactionEntity>
    @Query("DELETE FROM transactions") suspend fun clearTransactions()

    // ---- loans
    @Query("SELECT * FROM loans ORDER BY id DESC")
    fun observeLoans(): Flow<List<LoanEntity>>
    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getLoan(id: Long): LoanEntity?
    @Insert suspend fun insertLoan(l: LoanEntity): Long
    @Insert suspend fun insertLoans(list: List<LoanEntity>)
    @Update suspend fun updateLoan(l: LoanEntity)
    @Query("DELETE FROM loans WHERE id = :id") suspend fun deleteLoan(id: Long)
    @Query("SELECT * FROM loans ORDER BY id") suspend fun allLoans(): List<LoanEntity>
    @Query("DELETE FROM loans") suspend fun clearLoans()

    // ---- repayments
    @Query("SELECT * FROM repayments ORDER BY dateEpochDay DESC, id DESC")
    fun observeRepayments(): Flow<List<RepaymentEntity>>
    @Query("SELECT * FROM repayments WHERE id = :id")
    suspend fun getRepayment(id: Long): RepaymentEntity?
    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM repayments WHERE loanId = :loanId AND id != :excludeId")
    suspend fun sumRepayments(loanId: Long, excludeId: Long): Long
    @Insert suspend fun insertRepayment(r: RepaymentEntity): Long
    @Insert suspend fun insertRepayments(list: List<RepaymentEntity>)
    @Update suspend fun updateRepayment(r: RepaymentEntity)
    @Query("DELETE FROM repayments WHERE id = :id") suspend fun deleteRepayment(id: Long)
    @Query("SELECT * FROM repayments ORDER BY id") suspend fun allRepayments(): List<RepaymentEntity>
    @Query("DELETE FROM repayments") suspend fun clearRepayments()

    // ---- savings
    @Query("SELECT * FROM savings_entries ORDER BY dateEpochDay DESC, id DESC")
    fun observeSavings(): Flow<List<SavingsEntryEntity>>
    @Insert suspend fun insertSaving(s: SavingsEntryEntity): Long
    @Insert suspend fun insertSavings(list: List<SavingsEntryEntity>)
    @Update suspend fun updateSaving(s: SavingsEntryEntity)
    @Query("DELETE FROM savings_entries WHERE id = :id") suspend fun deleteSaving(id: Long)
    @Query("SELECT * FROM savings_entries ORDER BY id") suspend fun allSavings(): List<SavingsEntryEntity>
    @Query("DELETE FROM savings_entries") suspend fun clearSavings()

    // ---- categories
    @Query("SELECT * FROM categories ORDER BY name")
    fun observeCategories(): Flow<List<CategoryEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategory(c: CategoryEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategories(list: List<CategoryEntity>)
    @Query("SELECT * FROM categories ORDER BY type, name") suspend fun allCategories(): List<CategoryEntity>
    @Query("DELETE FROM categories") suspend fun clearCategories()

    // ---- settings
    @Query("SELECT * FROM settings")
    fun observeSettings(): Flow<List<SettingEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSetting(s: SettingEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSettings(list: List<SettingEntity>)
    @Query("SELECT * FROM settings ORDER BY key") suspend fun allSettings(): List<SettingEntity>
    @Query("DELETE FROM settings") suspend fun clearSettings()
}
