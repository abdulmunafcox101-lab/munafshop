package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 1,
    exportSchema = false
)
abstract class MunafDatabase : RoomDatabase() {
    abstract fun munafDao(): MunafDao

    companion object {
        @Volatile
        private var INSTANCE: MunafDatabase? = null

        fun getInstance(context: Context): MunafDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MunafDatabase::class.java,
                    "munaf_online_shop.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
