package com.devzyden.stepcounterbyzyden.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseProvider {

    @Volatile
    private var instance: AppDatabase? = null

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS tracker_state (
                    id INTEGER NOT NULL,
                    sessionId TEXT NOT NULL,
                    baselineSensorSteps INTEGER NOT NULL,
                    lastAcceptedSensorSteps INTEGER NOT NULL,
                    sessionSteps INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
            """.trimIndent())
        }
    }

    fun getDatabase(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "zyden_nstep_database"
            )
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
