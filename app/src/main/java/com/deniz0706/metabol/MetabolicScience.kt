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
        val plateau: Boolean?, val message: String
    )
    fun bmr(profile: Profile, weightKg: Double): Double {
        require(profile.age in 18..100 && profile.heightCm in 100.0..250.0 && weightKg in 20.0..500.0)
        return 10 * weightKg + 6.25 * profile.heightCm - 5 * profile.age + if (profile.male) 5.0 else -161.0
    }
    fun estimate(profile: Profile?, days: List<Day>, today: LocalDate): Estimate {
        val valid = days.filter { !it.date.isAfter(today) }.sortedBy { it.date }
        val weights = valid.filter { it.weightKg != null && it.weightKg in 20.0..500.0 }
        val lastWeight = weights.lastOrNull()?.weightKg
        val bmr = if (profile != null && lastWeight != null) bmr(profile, lastWeight) else null
        val initial = bmr?.times(profile!!.activity)
        val windowStart = today.minusDays(27)
        val window = valid.filter { !it.date.isBefore(windowStart) }
        val recorded = window.filter { it.calories != null && it.calories in 0..10000 }
        val average = recorded.takeIf { it.isNotEmpty() }?.map { it.calories!!.toDouble() }?.average()
        val recentWeights = weights.filter { !it.date.isBefore(windowStart) }
        val first = recentWeights.firstOrNull()
        val last = recentWeights.lastOrNull()
        val span = if (first != null && last != null) java.time.temporal.ChronoUnit.DAYS.between(first.date, last.date).toDouble() else 0.0
        val trend = if (span >= 14 && recentWeights.size >= 5)
            (last!!.weightKg!! - first!!.weightKg!!) * 7 / span else null
        // Energy balance is a rough diagnostic. 7700 kcal/kg is NOT used as a physiological
        // prediction law; water changes, incomplete food logging and adaptive expenditure matter.
        val sufficient = recorded.size >= 21 && recentWeights.size >= 8 && span >= 21 &&
            recorded.size.toDouble() / 28 >= 0.75 && trend != null
        val inferred = if (sufficient) (average!! - trend!! * 7700 / 7)
            .takeIf { it in 900.0..6500.0 } else null
        val plateau = if (trend != null) abs(trend) < 0.15 else null
        val expenditure = inferred ?: initial
        // Heuristic projection: expenditure relaxes toward new weight-dependent baseline;
        // bounded weight response and wide uncertainty, NOT Kevin Hall's validated model.
        var projected = lastWeight
        if (projected != null && expenditure != null && average != null &&
            recorded.size >= 21 && recentWeights.size >= 8 && span >= 21) {
            val starting = projected
            for (day in 1..30) {
                val adaptive = expenditure + (projected - starting) * 10.0
                val gap = (average - adaptive).coerceIn(-1500.0, 1500.0)
                projected += gap / 7700.0 * (0.85 + 0.15 * exp(-day / 30.0))
            }
        } else projected = null
        val fluctuations = if (recentWeights.size >= 5) {
            val values = recentWeights.map { it.weightKg!! }
            sqrt(values.map { (it - values.average()) * (it - values.average()) }.average())
        } else 1.0
        val uncertainty = max(1.5, fluctuations * 2.5 + 0.5)
        val message = when {
            profile == null -> "Yaş, boy, biyolojik cinsiyet ve aktivite düzeyini Ayarlar'dan gir."
            lastWeight == null -> "Önce kilo ölçümü kaydet."
            !sufficient -> "Kişisel enerji harcaması için son 28 günde en az 21 günlük beslenme ve 21 güne yayılan en az 8 kilo kaydı gerekli."
            inferred == null -> "Verilerden makul bir enerji harcaması tahmini çıkarılamadı; kayıtları kontrol et."
            else -> "Kişisel enerji harcaması yalnızca kayıtların eksiksiz olduğu varsayımıyla tahmin edilir."
        }
        return Estimate(bmr, initial, inferred, recorded.size, recentWeights.size, trend,
            average, projected, projected?.minus(uncertainty), projected?.plus(uncertainty),
            plateau, message)
    }
}
