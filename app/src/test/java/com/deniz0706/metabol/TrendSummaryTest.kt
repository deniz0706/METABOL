package com.deniz0706.metabol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendSummaryTest {
    private val day = 86_400_000L

    @Test fun emptyInputHasNoStatistics() {
        val result = summarizeWeights(emptyList())
        assertEquals(0, result.count)
        assertNull(result.last)
        assertNull(result.change)
        assertNull(result.weeklyRate)
    }

    @Test fun sortsMeasurementsBeforeComputingChange() {
        val result = summarizeWeights(
            listOf(WeightPoint(14 * day, 102f), WeightPoint(0L, 110f), WeightPoint(7 * day, 106f))
        )
        assertEquals(3, result.count)
        assertEquals(110f, result.first!!, 0.001f)
        assertEquals(102f, result.last!!, 0.001f)
        assertEquals(-8f, result.change!!, 0.001f)
        assertEquals(-4.0, result.weeklyRate!!, 0.001)
        assertEquals(106f, result.average!!, 0.001f)
    }

    @Test fun weeklyRateRequiresAtLeastSevenDays() {
        val result = summarizeWeights(listOf(WeightPoint(0L, 110f), WeightPoint(day, 109f)))
        assertNull(result.weeklyRate)
    }

    @Test fun oneMeasurementDoesNotInventChangeRate() {
        val result = summarizeWeights(listOf(WeightPoint(0L, 100f)))
        assertEquals(0f, result.change!!, 0.001f)
        assertNull(result.weeklyRate)
    }

    @Test fun sameDayMeasurementsDoNotProduceArtificialWeeklyRate() {
        val result = summarizeWeights(listOf(
            WeightPoint(10_000L, 103f),
            WeightPoint(10_000L, 101f)
        ))
        assertNull(result.weeklyRate)
        assertEquals(-2f, result.change!!, 0.001f)
    }

    @Test fun longRangeWeeklyRateIsNormalizedByElapsedDays() {
        val result = summarizeWeights(listOf(
            WeightPoint(0L, 100f),
            WeightPoint(28 * day, 96f)
        ))
        assertEquals(-1.0, result.weeklyRate!!, 0.001)
    }

    @Test fun smoothingUsesLastSevenMeasurements() {
        val entries = (0..9).map { WeightPoint(it * day, (90 + it).toFloat()) }
        val result = summarizeWeights(entries)
        assertEquals(96f, result.smoothedLast!!, 0.001f)
    }
}
