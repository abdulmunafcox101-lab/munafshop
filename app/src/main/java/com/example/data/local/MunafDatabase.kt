package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BusinessProfileEntity::class,
        BranchEntity::class,
        ProductEntity::class,
        PartyEntity::class,
        TransactionEntity::class,
        StockLogEntity::class,
        EmployeeEntity::class,
        VoiceNoteEntity::class,
        NotificationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MunafDatabase : RoomDatabase() {
    abstract fun munafDao(): MunafDao

    companion object {
        @Volatile
        private var INSTANCE: MunafDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_profiles ADD COLUMN subscriptionStartMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE business_profiles ADD COLUMN accountStatus TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE business_profiles ADD COLUMN banReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE business_profiles ADD COLUMN suspendedUntilMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE business_profiles ADD COLUMN lastExpiryWarningDays INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): MunafDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MunafDatabase::class.java,
                    "munaf_online_shop.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
