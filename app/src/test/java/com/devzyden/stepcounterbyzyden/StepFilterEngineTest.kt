package com.devzyden.stepcounterbyzyden

import com.devzyden.stepcounterbyzyden.engine.StepFilterEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepFilterEngineTest {

    @Test
    fun first_valid_step_is_accepted() {
        val engine = StepFilterEngine()

        assertTrue(engine.verifyStepValidity(1_000L, 0f, 0f, 9.8f))
    }

    @Test
    fun force_below_minimum_is_rejected() {
        val engine = StepFilterEngine()

        assertFalse(engine.verifyStepValidity(1_000L, 0f, 0f, 9.4f))
    }

    @Test
    fun force_above_maximum_is_rejected() {
        val engine = StepFilterEngine()

        assertFalse(engine.verifyStepValidity(1_000L, 0f, 0f, 16.1f))
    }

    @Test
    fun step_at_exactly_333ms_is_rejected() {
        val engine = StepFilterEngine()

        assertTrue(engine.verifyStepValidity(1_000L, 0f, 0f, 9.8f))
        assertFalse(engine.verifyStepValidity(1_333L, 0f, 0f, 9.8f))
    }

    @Test
    fun step_after_more_than_333ms_is_accepted() {
        val engine = StepFilterEngine()

        assertTrue(engine.verifyStepValidity(1_000L, 0f, 0f, 9.8f))
        assertTrue(engine.verifyStepValidity(1_334L, 0f, 0f, 9.8f))
    }

    @Test
    fun rejected_fast_step_does_not_reset_last_accepted_time() {
        val engine = StepFilterEngine()

        assertTrue(engine.verifyStepValidity(1_000L, 0f, 0f, 9.8f))
        assertFalse(engine.verifyStepValidity(1_300L, 0f, 0f, 9.8f))
        assertTrue(engine.verifyStepValidity(1_400L, 0f, 0f, 9.8f))
    }

}
