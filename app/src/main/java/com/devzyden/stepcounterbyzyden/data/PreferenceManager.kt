package com.devzyden.stepcounterbyzyden.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PreferenceManager(context: Context) {

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(
        "zyden_tracker_preferences",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_MONTHLY_ACCUMULATION = "accumulated_monthly_steps_"
    }

    fun saveValidatedSteps(additionalSteps: Int) {
        val currentMonthKey = resolveMonthlyIdentifier()
        val existingTotal = fetchMonthlySteps()
        sharedPrefs.edit().putInt(currentMonthKey, existingTotal + additionalSteps).apply()
    }

    fun fetchMonthlySteps(): Int {
        val currentMonthKey = resolveMonthlyIdentifier()
        return sharedPrefs.getInt(currentMonthKey, 0)
    }

    private fun resolveMonthlyIdentifier(): String {
        val formatStructure = SimpleDateFormat("yyyy_MM", Locale.US)
        return KEY_MONTHLY_ACCUMULATION + formatStructure.format(Calendar.getInstance().time)
    }
}
