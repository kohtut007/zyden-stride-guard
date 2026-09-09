package com.devzyden.stepcounterbyzyden

import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.devzyden.stepcounterbyzyden.worker.MyDataSyncWorker

// 1. Define constraints
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED) // Requires internet connection
    .setRequiresCharging(true) // Only runs when device is charging
    .build()

// 2. Build input data using the ktx utility 'workDataOf'
val inputData = workDataOf("KEY_FILENAME" to "backup_data.json")

// 3. Create the request
val uploadWorkRequest = OneTimeWorkRequestBuilder<MyDataSyncWorker>()
    .setConstraints(constraints)
    .setInputData(inputData)
    .build()

