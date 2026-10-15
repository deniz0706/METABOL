package com.deniz0706.metabol

import java.time.LocalDate

/**
 * Missing diary dates are not treated as days of zero consumption.
 * All values describe *recorded* meals, not actual energy expenditure or intake.
 */
internal data class NutritionDay(
    val date: LocalDate,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val meals: Int
)

internal data class NutritionTrend(
    val recordedDays: Int,
    val windowDays: Int,
    val totalCalories: Int,
    val averageRecordedCalories: Int?,
    val averageRecordedProtein: Double?,
    val totalMeals: Int,
    val longestRecordingStreak: Int,
    val currentRecordingStreak: Int
)

internal object NutritionInsights {
    fun summarize(days: List<NutritionDay>, windowDays: Int, today: LocalDate): NutritionTrend {
        require(windowDays in 1..365)
        val first = today.minusDays(windowDays.toLong() - 1L)
        val valid = days.filter { !it.date.isBefore(first) && !it.date.isAfter(today) }
            .distinctBy { it.date }
            .sortedBy { it.date }
        val count = valid.size
        val total = valid.sumOf { it.calories.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val average = if (count == 0) null else (total.toDouble() / count + 0.5).toInt()
        val protein = if (count == 0) null else valid.sumOf { it.protein } / count
        var longest = 0
        var current = 0
        var previous: LocalDate? = null
        valid.forEach { day ->
            current = if (previous?.plusDays(1) == day.date) current + 1 else 1
            if (current > longest) longest = current
            previous = day.date
        }
        var active = 0
        var cursor = today
        val dates = valid.map { it.date }.toSet()
        while (cursor in dates) {
            active++
            cursor = cursor.minusDays(1)
        }
        return NutritionTrend(count, windowDays, total, average, protein,
            valid.sumOf { it.meals }, longest, active)
    }
}
