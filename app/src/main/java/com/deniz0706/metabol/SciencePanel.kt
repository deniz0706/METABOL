package com.deniz0706.metabol

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun SciencePanel(context: Context, points: List<WeightPoint>) {
    val profilePrefs = remember(context) { context.getSharedPreferences("metabol_profile", Context.MODE_PRIVATE) }
    val nutritionPrefs = remember(context) { context.getSharedPreferences("metabol_nutrition", Context.MODE_PRIVATE) }
    val profile = remember {
        val age = profilePrefs.getString("science_age", null)?.toIntOrNull()
        val height = profilePrefs.getString("science_height", null)?.toDoubleOrNull()
        val activity = profilePrefs.getString("science_activity", null)?.toDoubleOrNull()
        val sex = profilePrefs.getString("science_sex", null)
        if (age in 18..100 && height != null && height in 100.0..250.0 &&
            activity != null && activity in 1.2..1.9 && sex in listOf("male", "female"))
            MetabolicScience.Profile(age!!, height, sex == "male", activity)
        else null
    }
    val estimate = remember(points) {
        val zone = ZoneId.systemDefault()
        val weights = points.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, values) -> values.maxByOrNull { it.timestamp }!!.kg.toDouble() }
        val dates = (0..89).map { LocalDate.now().minusDays(it.toLong()) }
        val data = dates.map { day ->
            val raw = nutritionPrefs.getString("meals_$day", null)
            val calories = if (raw == null) null else runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).sumOf { array.getJSONObject(it).getInt("calories") }
            }.getOrNull()
            MetabolicScience.Day(day, weights[day], calories)
        }
        MetabolicScience.estimate(profile, data, LocalDate.now())
    }
    val mint = Color(0xFF71E8C4)
    val muted = Color(0xFF8B9CB2)
    val white = Color(0xFFF4F8FC)
    Column(Modifier.fillMaxWidth().background(Color(0xFF121C28), RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("METABOL · BİLİMSEL ANALİZ", color = mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Enerji dengesi ve kişisel tahminler", color = white, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        @Composable fun Metric(label: String, value: String) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = muted, fontSize = 12.sp)
                Text(value, color = white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        fun kcal(value: Double?) = value?.let { String.format(Locale.US, "%.0f kcal/gün", it) } ?: "Veri yetersiz"
        Metric("BMR · Mifflin–St Jeor", kcal(estimate.bmr))
        Metric("TDEE · Aktiviteye dayalı", kcal(estimate.initialTdee))
        Metric("AME · Kayıtlardan tahmin", kcal(estimate.inferredTdee))
        Metric("28 günlük beslenme kaydı", "${estimate.recordedDays}/28 gün")
        Metric("28 günlük kilo kaydı", "${estimate.observedDays} gün")
        Metric("Kayıtlı günlerde ort. enerji", kcal(estimate.dailyCalorieAverage))
        Metric("Kilo değişim hızı", estimate.trendKgPerWeek?.let {
            String.format(Locale.US, "%+.2f kg/hafta", it)
        } ?: "Veri yetersiz")
        Metric("Kilo duraklaması", when (estimate.plateau) {
            true -> "Olası (trend ±0,15 kg/hafta)"
            false -> "Trend devam ediyor"
            null -> "Veri yetersiz"
        })
        HorizontalDivider(color = Color(0xFF2A394B))
        Metric("30 gün sonrası · Senaryo", estimate.forecast30Kg?.let {
            String.format(Locale.US, "%.1f kg", it)
        } ?: "Veri yetersiz")
        Metric("Belirsizlik bandı", if (estimate.forecastLowKg != null && estimate.forecastHighKg != null)
            String.format(Locale.US, "%.1f–%.1f kg", estimate.forecastLowKg, estimate.forecastHighKg)
            else "—")
        Text(estimate.message, color = mint, fontSize = 12.sp)
        Text("BMR bir denklem tahminidir; TDEE aktivite varsayımına dayanır. AME kayıtlı kaloriler ve kilo trendinden yaklaşık çıkarımdır. 30 günlük öngörü, enerji harcamasının kiloyla değişimini hesaba katan basitleştirilmiş senaryodur; Kevin Hall modelinin uygulaması veya klinik olarak kalibre edilmiş güven aralığı değildir. Eksik öğün kayıtları sonucu bozabilir. Su değişimleri yağ değişimi sayılmaz.",
            color = muted, fontSize = 11.sp)
    }
}
