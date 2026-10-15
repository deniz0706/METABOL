package com.deniz0706.metabol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NutritionInsightsTest {
    private val today = LocalDate.of(2026, 10, 15)
    private fun day(offset: Long, calories: Int = 1000, protein: Double = 50.0) =
        NutritionDay(today.minusDays(offset), calories, protein, 100.0, 40.0, 2)

    @Test fun missingDaysAreNotZeroConsumption() {
        val result = NutritionInsights.summarize(listOf(day(0, 1800), day(2, 1200)), 7, today)
        assertEquals(2, result.recordedDays)
        assertEquals(1500, result.averageRecordedCalories)
        assertEquals(4, result.totalMeals)
    }

    @Test fun noEntriesYieldNoAverage() {
        val result = NutritionInsights.summarize(emptyList(), 30, today)
        assertNull(result.averageRecordedCalories)
        assertNull(result.averageRecordedProtein)
        assertEquals(0, result.longestRecordingStreak)
    }

    @Test fun streaksHandleGaps() {
        val result = NutritionInsights.summarize(
            listOf(day(0), day(1), day(3), day(4), day(5)), 7, today)
        assertEquals(3, result.longestRecordingStreak)
        assertEquals(2, result.currentRecordingStreak)
    }

    @Test fun windowExcludesOldAndFutureDays() {
        val result = NutritionInsights.summarize(
            listOf(day(0), day(7), day(-1)), 7, today)
        assertEquals(1, result.recordedDays)
        assertEquals(1000, result.totalCalories)
    }

    @Test fun duplicatesCountAsOneRecordedDate() {
        val result = NutritionInsights.summarize(listOf(day(0), day(0), day(1)), 7, today)
        assertEquals(2, result.recordedDays)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidWindow() {
        NutritionInsights.summarize(emptyList(), 0, today)
    }
}
