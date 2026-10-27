package com.paisafloww.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        TransactionEntity::class,
        LoanEntity::class,
        RepaymentEntity::class,
        SavingsEntryEntity::class,
        CategoryEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao

    companion object {
        /**
         * Add a Migration here every time the schema version is raised.
         * Destructive migration is deliberately NOT enabled: real financial data must never be dropped.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "paisafloww.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
