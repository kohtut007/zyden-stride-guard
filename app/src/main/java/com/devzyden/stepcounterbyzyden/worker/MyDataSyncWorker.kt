package com.devzyden.stepcounterbyzyden.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class MyDataSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Retrieve input data if needed
        val filename = inputData.getString("KEY_FILENAME")

        return try {
            // Perform your background task here (e.g., Network API call, Database cleanup)
            simulateNetworkSync()

            Result.success()
        } catch (e: Exception) {
            // Retries the task automatically using backoff policy
            Result.retry()
        }
    }

    private suspend fun simulateNetworkSync() {
        delay(3000) // Simulating long-running work safely in a coroutine scope
    }
}
