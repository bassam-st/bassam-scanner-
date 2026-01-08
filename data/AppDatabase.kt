package com.bassam.scanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScanEntry::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scanEntryDao(): ScanEntryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bassam_scanner_db"
                ).build()
                INSTANCE = db
                db
            }
        }
    }
}
