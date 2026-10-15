package com.deniz0706.metabol

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MetabolicScienceTest {
    private val profile = MetabolicScience.Profile(25, 180.0, true, 1.375)
    @Test fun mifflinStJeorEquation() {
        assertEquals(1805.0, MetabolicScience.bmr(profile, 80.0), 0.001)
    }
    @Test fun insufficientRecordsDoNotInventAdaptiveEstimate() {
        val now = LocalDate.of(2026, 10, 15)
        val days = (0..6).map { MetabolicScience.Day(now.minusDays(it.toLong()), 80.0, 1800) }
        val result = MetabolicScience.estimate(profile, days, now)
        assertNull(result.inferredTdee)
        assertNull(result.forecast30Kg)
        assertNotNull(result.initialTdee)
    }
    @Test fun completeStableLogProducesConditionalEstimate() {
        val now = LocalDate.of(2026, 10, 15)
        val days = (0..27).map { offset ->
            MetabolicScience.Day(now.minusDays(offset.toLong()), 80.0, 2200)
        }
        val result = MetabolicScience.estimate(profile, days, now)
        assertEquals(2200.0, result.inferredTdee!!, 0.01)
        assertEquals(80.0, result.forecast30Kg!!, 0.01)
        assertEquals(true, result.plateau)
        assertTrue(result.forecastLowKg!! < result.forecastHighKg!!)
    }
    @Test fun isolatedWaterSpikeDoesNotDistortTrend() {
        val now = LocalDate.of(2026, 10, 15)
        val days = (0..27).map { offset ->
            val weight = if (offset == 0) 85.0 else 80.0
            MetabolicScience.Day(now.minusDays(offset.toLong()), weight, 2200)
        }.sortedBy { it.date }
        val trend = MetabolicScience.robustTrend(days)
        assertNotNull(trend)
        assertEquals(0.0, trend!!, 0.01)
    }

    @Test fun plateauRequiresThreeWeeksOfEvidence() {
        val now = LocalDate.of(2026, 10, 15)
        val days = (0..15).map { MetabolicScience.Day(now.minusDays(it.toLong()), 80.0, 2000) }
        val result = MetabolicScience.estimate(profile, days, now)
        assertNull(result.plateau)
        val complete = (0..27).map { MetabolicScience.Day(now.minusDays(it.toLong()), 80.0, 2000) }
        assertTrue(result.reliability < MetabolicScience.estimate(profile, complete, now).reliability)
    }

    @Test fun reliabilityImprovesWithCompleteRecords() {
        val now = LocalDate.of(2026, 10, 15)
        val sparse = (0..5).map { MetabolicScience.Day(now.minusDays(it.toLong()), 80.0, null) }
        val full = (0..27).map { MetabolicScience.Day(now.minusDays(it.toLong()), 80.0, 2000) }
        assertTrue(MetabolicScience.estimate(profile, full, now).reliability >
            MetabolicScience.estimate(profile, sparse, now).reliability)
    }

}
