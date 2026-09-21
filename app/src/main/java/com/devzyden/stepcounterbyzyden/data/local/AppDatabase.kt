package com.devzyden.stepcounterbyzyden.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [StepEntity::class, TrackerStateEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao
}