package com.example.mobileaudiowhatsapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CallLog::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callLogDao(): CallLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE call_logs ADD COLUMN failure_reason TEXT DEFAULT NULL")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "call_transcriptions.db"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { INSTANCE = it }
            }
    }
}
