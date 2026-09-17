package com.devzyden.stepcounterbyzyden.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {

    @Query("SELECT * FROM daily_steps WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): StepEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(step: StepEntity): Long

    @Query(
        """
        UPDATE daily_steps
        SET validatedSteps = validatedSteps + :additionalSteps
        WHERE date = :date
        """
    )
    suspend fun incrementExisting(date: String, additionalSteps: Int): Int

    @Transaction
    suspend fun addValidatedStepsAtomic(
        date: String,
        additionalSteps: Int
    ) {
        insertIfMissing(
            StepEntity(
                date = date,
                validatedSteps = 0
            )
        )

        incrementExisting(
            date = date,
            additionalSteps = additionalSteps
        )
    }

    @Query(
        """
        SELECT COALESCE(SUM(validatedSteps), 0)
        FROM daily_steps
        WHERE date >= :startDate AND date < :endDate
        """
    )
    suspend fun getStepsBetween(startDate: String, endDate: String): Int

    @Query(
        """
    SELECT * FROM daily_steps
    ORDER BY date DESC
    """
    )
    suspend fun getAllDays(): List<StepEntity>

    @Query(
        """
    SELECT COALESCE(SUM(validatedSteps), 0)
    FROM daily_steps
    WHERE date >= :startDate AND date < :endDate
    """
    )
    fun observeStepsBetween(startDate: String, endDate: String): Flow<Int>
}