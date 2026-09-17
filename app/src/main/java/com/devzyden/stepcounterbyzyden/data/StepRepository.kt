package com.devzyden.stepcounterbyzyden.data

import com.devzyden.stepcounterbyzyden.data.local.StepDao
import com.devzyden.stepcounterbyzyden.data.local.StepEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StepRepository(
    private val stepDao: StepDao
) {

    suspend fun addValidatedSteps(additionalSteps: Int) {
        if (additionalSteps <= 0) return

        stepDao.addValidatedStepsAtomic(
            date = currentDateKey(), additionalSteps = additionalSteps
        )
    }

    suspend fun getTodaySteps(): Int {
        return stepDao.getByDate(currentDateKey())?.validatedSteps ?: 0
    }

    suspend fun getCurrentMonthSteps(): Int {
        val calendar = Calendar.getInstance()

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startDate = currentDateKey(calendar)

        calendar.add(Calendar.MONTH, 1)
        val endDate = currentDateKey(calendar)

        return stepDao.getStepsBetween(startDate, endDate)
    }

    fun observeCurrentMonthSteps(): Flow<Int> {
        val calendar = Calendar.getInstance()

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startDate = currentDateKey(calendar)

        calendar.add(Calendar.MONTH, 1)
        val endDate = currentDateKey(calendar)

        return stepDao.observeStepsBetween(startDate, endDate)
    }

    private fun currentDateKey(calendar: Calendar = Calendar.getInstance()): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return format.format(calendar.time)
    }

    suspend fun getAllDays(): List<StepEntity> {
        return stepDao.getAllDays()
    }
}