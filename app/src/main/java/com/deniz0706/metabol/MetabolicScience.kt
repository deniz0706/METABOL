package com.deniz0706.metabol

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** Educational estimates, not a validated individual metabolic measurement. */
internal object MetabolicScience {
    data class Profile(val age: Int, val heightCm: Double, val male: Boolean, val activity: Double)
    data class Day(val date: LocalDate, val weightKg: Double?, val calories: Int?)
    data class Estimate(
        val bmr: Double?, val initialTdee: Double?, val inferredTdee: Double?,
        val recordedDays: Int, val observedDays: Int, val trendKgPerWeek: Double?,
        val dailyCalorieAverage: Double?, val forecast30Kg: Double?,
        val forecastLowKg: Double?, val forecastHighKg: Double?,
        val plateau: Boolean?, val message: String,
        val reliability: Int = 0, val reliabilityLabel: String = "Yetersiz veri",
        val expectedTdeeChange: Double? = null, val forecastErrorKg: Double? = null
    )
    fun bmr(profile: Profile, weightKg: Double): Double {
        require(profile.age in 18..100 && profile.heightCm in 100.0..250.0 && weightKg in 20.0..500.0)
        return 10 * weightKg + 6.25 * profile.heightCm - 5 * profile.age + if (profile.male) 5.0 else -161.0
    }
    fun estimate(profile: Profile?, days: List<Day>, today: LocalDate): Estimate {
        // One calendar day must count only once toward evidence thresholds. Merge partial
        // records rather than treating duplicate entries as additional days.
        val valid = days.filter { !it.date.isAfter(today) }
            .groupBy { it.date }
            .map { (date, entries) ->
                Day(date, entries.lastOrNull { it.weightKg != null }?.weightKg,
                    entries.lastOrNull { it.calories != null }?.calories)
            }.sortedBy { it.date }
        val weights = valid.filter { it.weightKg != null && it.weightKg in 20.0..500.0 }
        // Use the median of the latest seven calendar days to dampen transient
        // water-weight spikes before computing baseline and forecasts.
        val latestDate = weights.lastOrNull()?.date
        val lastWeight = latestDate?.let { end ->
            weights.filter { !it.date.isBefore(end.minusDays(6)) }
                .mapNotNull { it.weightKg }.takeIf { it.size >= 3 }?.let(::median)
        } ?: weights.lastOrNull()?.weightKg
        // Old weigh-ins must not be presented as a current metabolic baseline.
        val freshWeight = latestDate != null && !latestDate.isBefore(today.minusDays(14))
        val bmr = if (profile != null && lastWeight != null && freshWeight) bmr(profile, lastWeight) else null
        val initial = bmr?.times(profile!!.activity)
        val windowStart = today.minusDays(27)
        val window = valid.filter { !it.date.isBefore(windowStart) }
        val recorded = window.filter { it.calories != null && it.calories in 0..10000 }
        val average = recorded.takeIf { it.isNotEmpty() }?.map { it.calories!!.toDouble() }?.average()
        val recentWeights = weights.filter { !it.date.isBefore(windowStart) }
        val first = recentWeights.firstOrNull()
        val last = recentWeights.lastOrNull()
        val span = if (first != null && last != null) java.time.temporal.ChronoUnit.DAYS.between(first.date, last.date).toDouble() else 0.0
        val trend = robustTrend(recentWeights)
        // Energy balance is a rough diagnostic. 7700 kcal/kg is NOT used as a physiological
        // prediction law; water changes, incomplete food logging and adaptive expenditure matter.
        val sufficient = recorded.size >= 21 && recentWeights.size >= 8 && span >= 21 &&
            recorded.size.toDouble() / 28 >= 0.75 && trend != null
        val inferred = if (sufficient) (average!! - trend!! * 7700 / 7)
            .takeIf { it in 900.0..6500.0 } else null
        val reliability = ((recorded.size.coerceIn(0,28) / 28.0) * 45 +
            (recentWeights.size.coerceIn(0,12) / 12.0) * 25 +
            (span.coerceIn(0.0,27.0) / 27.0) * 20 +
            (if (profile != null) 10 else 0)).toInt().coerceIn(0,100)
        val reliabilityLabel = when {
            reliability < 35 -> "Düşük"
            reliability < 70 -> "Orta"
            else -> "Yüksek (kayıt düzenliliği)"
        }
        val plateau = if (span >= 21 && recentWeights.size >= 8 && trend != null)
            abs(trend) < 0.15 else null
        val expectedTdeeChange = if (first?.weightKg != null && last?.weightKg != null)
            (last.weightKg - first.weightKg) * 10.0 else null
        val forecastError = retrospectiveError(profile, valid, today)
        val expenditure = inferred ?: initial
        // Heuristic projection: expenditure relaxes toward new weight-dependent baseline;
        // bounded weight response and wide uncertainty, NOT Kevin Hall's validated model.
        var projected = lastWeight
        if (freshWeight && projected != null && expenditure != null && average != null &&
            recorded.size >= 21 && recentWeights.size >= 8 && span >= 21) {
            val starting = projected
            for (day in 1..30) {
                val adaptive = expenditure + (projected - starting) * 10.0
                val gap = (average - adaptive).coerceIn(-1500.0, 1500.0)
                // Approximate time-varying energy density, not a validated Hall model.
                val density = (if (gap < 0) 7200.0 else 8000.0) +
                    500.0 * (1.0 - exp(-day / 90.0))
                projected += gap / density * (0.85 + 0.15 * exp(-day / 30.0))
            }
        } else projected = null
        val fluctuations = if (recentWeights.size >= 5) {
            val values = recentWeights.map { it.weightKg!! }
            sqrt(values.map { (it - values.average()) * (it - values.average()) }.average())
        } else 1.0
        val uncertainty = max(1.5, fluctuations * 2.5 + 0.5) +
            (if (reliability < 70) 1.0 else 0.0) + (forecastError ?: 0.0)
        val message = when {
            profile == null -> "Yaş, boy, biyolojik cinsiyet ve aktivite düzeyini Ayarlar'dan gir."
            lastWeight == null -> "Önce kilo ölçümü kaydet."
            !freshWeight -> "Son kilo ölçümün 14 günden eski. Güncel metabolizma tahmini için yeni bir ölçüm kaydet."
            !sufficient -> "Kişisel enerji harcaması için son 28 günde en az 21 günlük beslenme ve 21 güne yayılan en az 8 kilo kaydı gerekli."
            inferred == null -> "Verilerden makul bir enerji harcaması tahmini çıkarılamadı; kayıtları kontrol et."
            else -> "Kişisel enerji harcaması yalnızca kayıtların eksiksiz olduğu varsayımıyla tahmin edilir."
        }
        return Estimate(bmr, initial, inferred, recorded.size, recentWeights.size, trend,
            average, projected, projected?.minus(uncertainty), projected?.plus(uncertainty),
            plateau, message, reliability, reliabilityLabel, expectedTdeeChange, forecastError)
    }
    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    }

    // Median of first and last calendar-week observations, robust to isolated fluctuations.
    internal fun robustTrend(weights: List<Day>): Double? {
        if (weights.size < 5) return null
        val first = weights.first().date
        val last = weights.last().date
        if (java.time.temporal.ChronoUnit.DAYS.between(first, last) < 14) return null
        val head = weights.filter { it.date <= first.plusDays(6) }
        val tail = weights.filter { it.date >= last.minusDays(6) }
        if (head.size < 2 || tail.size < 2) return null
        val start = head.map { it.date.toEpochDay().toDouble() }.average()
        val end = tail.map { it.date.toEpochDay().toDouble() }.average()
        if (end - start < 7) return null
        return (median(tail.map { it.weightKg!! }) - median(head.map { it.weightKg!! })) *
            7 / (end - start)
    }

    // Retrospective out-of-sample 14-day error; diagnostic, not model training.
    private fun retrospectiveError(profile: Profile?, valid: List<Day>, today: LocalDate): Double? {
        if (profile == null) return null
        val split = today.minusDays(14)
        val past = valid.filter { it.date <= split && it.date >= split.minusDays(27) }
        val actual = valid.filter { it.date >= today.minusDays(2) }.mapNotNull { it.weightKg }
        val start = past.mapNotNull { it.weightKg }.lastOrNull() ?: return null
        val intake = past.mapNotNull { it.calories }.filter { it in 0..10000 }
        if (intake.size < 18 || actual.isEmpty() || past.count { it.weightKg != null } < 5)
            return null
        val predicted = start + (intake.average() - bmr(profile, start) * profile.activity)
            .coerceIn(-1500.0, 1500.0) * 14 / 7700.0
        return abs(median(actual) - predicted).coerceAtMost(10.0)
    }

}
