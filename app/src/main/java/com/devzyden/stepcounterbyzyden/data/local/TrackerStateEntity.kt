package com.devzyden.stepcounterbyzyden.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracker_state")
data class TrackerStateEntity(
    @PrimaryKey
    val id: Int = 1,
    val sessionId: String,
    val baselineSensorSteps: Int,
    val lastAcceptedSensorSteps: Int,
    val sessionSteps: Int
)
