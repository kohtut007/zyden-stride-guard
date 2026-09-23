package com.devzyden.stepcounterbyzyden

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devzyden.stepcounterbyzyden.engine.StepFilterEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StepFilterEngineInstrumentedTest {

    @Test
    fun null_location_is_accepted() {
        val engine = StepFilterEngine()

        assertTrue(engine.verifyUserIsNotInVehicle(null))
    }

    @Test
    fun speed_below_20_kmh_is_accepted() {
        val engine = StepFilterEngine()
        val location = Location("test").apply { speed = 19.9f / 3.6f }

        assertTrue(engine.verifyUserIsNotInVehicle(location))
    }

    @Test
    fun speed_just_below_20_kmh_is_accepted() {
        val engine = StepFilterEngine()
        val location = Location("test").apply { speed = 19.999f / 3.6f }

        assertTrue(engine.verifyUserIsNotInVehicle(location))
    }

    @Test
    fun speed_above_20_kmh_is_rejected() {
        val engine = StepFilterEngine()
        val location = Location("test").apply { speed = 20.1f / 3.6f }

        assertFalse(engine.verifyUserIsNotInVehicle(location))
    }

}
