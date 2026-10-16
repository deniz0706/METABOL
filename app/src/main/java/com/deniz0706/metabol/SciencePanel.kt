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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SciencePanel(context: Context, points: List<WeightPoint>) {
    val profilePrefs = remember(context) { context.getSharedPreferences("metabol_profile", Context.MODE_PRIVATE) }
    val nutritionPrefs = remember(context) { context.getSharedPreferences("metabol_nutrition", Context.MODE_PRIVATE) }
    // Read the saved profile on recomposition so returning from Settings refreshes the estimate.
    val profile = run {
        val age = profilePrefs.getString("science_age", null)?.toIntOrNull()
        val height = profilePrefs.getString("science_height", null)?.toDoubleOrNull()
        val activity = profilePrefs.getString("science_activity", null)?.toDoubleOrNull()
        val sex = profilePrefs.getString("science_sex", null)
        if (age != null && age in 18..100 && height != null && height in 100.0..250.0 &&
            activity != null && activity.isFinite() && activity in setOf(1.2, 1.375, 1.55, 1.725, 1.9) && sex in listOf("male", "female"))
            MetabolicScience.Profile(age!!, height, sex == "male", activity)
        else null
    }
    val estimate by produceState<MetabolicScience.Estimate?>(initialValue = null, key1 = points, key2 = profile) {
        value = withContext(Dispatchers.Default) {
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
    }
    val mint = Color(0xFF57D6C1)
    val muted = Color(0xFF9AAFC5)
    val white = Color(0xFFF3F8FF)
    val result = estimate
    Column(Modifier.fillMaxWidth().background(Color(0xFF172337), RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("METABOL · METABOLİZMA REHBERİ", color = mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Vücudunun yaklaşık enerji ihtiyacı", color = white, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        @Composable fun Metric(label: String, value: String) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = muted, fontSize = 12.sp)
                Text(value, color = white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        fun kcal(value: Double?) = value?.let { String.format(Locale.US, "%.0f kcal/gün", it) } ?: "Veri yetersiz"
        if (result == null) {
            Text("Bilimsel analiz hesaplanıyor…", color = muted, fontSize = 12.sp)
        } else {
        Metric("Dinlenirken harcadığın enerji (BMR)", kcal(result.bmr))
        Text("Hiç egzersiz yapmasan bile vücudunun temel işlevleri için harcadığı yaklaşık enerji.", color = muted, fontSize = 11.sp)
        Metric("Hareket dahil günlük ihtiyaç (TDEE)", kcal(result.initialTdee))
        Text("Yaş, boy, kilo ve seçtiğin hareket düzeyine göre hesaplanır.", color = muted, fontSize = 11.sp)
        Metric("Kayıtlarına göre günlük harcama (AME)", kcal(result.inferredTdee))
        Text("Yemek ve tartı kayıtlarından çıkarılır; kayıtlar eksikse yanıltıcı olabilir.", color = muted, fontSize = 11.sp)
        val recentWeight = points.maxByOrNull { it.timestamp }?.kg?.toDouble()
            ?.takeIf { it in 20.0..500.0 &&
                System.currentTimeMillis() - (points.maxOfOrNull { it.timestamp } ?: 0L) <= 14L * 86_400_000L }
        val maintenance = result.inferredTdee ?: result.initialTdee
        if (maintenance != null) {
            Metric("Tahmini koruma kalorisi", kcal(maintenance))
            Metric("Hafif açık örneği (−250 kcal)", kcal((maintenance - 250).coerceAtLeast(1200.0)))
            Text("Kalori önerileri tıbbi reçete değildir; gerçek gereksinim kişiye ve koşullara göre değişir.",
                color = muted, fontSize = 11.sp)
        }
        if (recentWeight != null) {
            Metric("Protein · genel aralık (1,2–1,6 g/kg)",
                String.format(Locale.US, "%.0f–%.0f g/gün", recentWeight * 1.2, recentWeight * 1.6))
            Text("Bu aralık genel bir referanstır; hedef kilo, yağsız kütle, aktivite ve sağlık durumuna göre uyarlanmalıdır.",
                color = muted, fontSize = 11.sp)
        }
        Metric("Tahmin için veri yeterliliği", "${result.reliability}/100 · ${result.reliabilityLabel}")
        Text("Bu puan ölçüm doğruluğunu değil, kayıtların yeterliliğini gösterir.",
            color = muted, fontSize = 11.sp)
        Metric("Kilo değişimine bağlı tahmini enerji farkı", result.expectedTdeeChange?.let {
            String.format(Locale.US, "%+.0f kcal/gün", it)
        } ?: "Veri yetersiz")
        Metric("Son 14 gündeki tahmin sapması", result.forecastErrorKg?.let {
            String.format(Locale.US, "%.1f kg", it)
        } ?: "Geriye dönük veri yetersiz")
        Metric("Son 28 günde yemek kaydettiğin günler", "${result.recordedDays}/28 gün")
        Metric("Son 28 günde tartıldığın günler", "${result.observedDays} gün")
        Metric("Yemek kaydettiğin günlerin kalori ortalaması", kcal(result.dailyCalorieAverage))
        Text("Kilo trendi, ilk ve son haftanın medyan ölçümleriyle hesaplanır; tek günlük artışlar yağ kazanımı olarak değerlendirilmez.",
            color = muted, fontSize = 11.sp)
        Metric("Haftalık ortalama kilo değişimi", result.trendKgPerWeek?.let {
            String.format(Locale.US, "%+.2f kg/hafta", it)
        } ?: "Veri yetersiz")
        Metric("Kilo kaybı yavaşlamış olabilir mi?", when (result.plateau) {
            true -> "Olası (en az 21 günlük trend)"
            false -> "Trend devam ediyor"
            null -> "Veri yetersiz"
        })
        HorizontalDivider(color = Color(0xFF30445C))
        Text("Aşağıdaki 30 günlük değer bir senaryodur; hedef veya gerçekleşeceği kesin bir sonuç değildir.",
            color = muted, fontSize = 11.sp)
        Metric("Bu hız sürerse 30 gün sonraki tahmin", result.forecast30Kg?.let {
            String.format(Locale.US, "%.1f kg", it)
        } ?: "Veri yetersiz")
        Metric("Tahmini alt ve üst kilo aralığı", if (result.forecastLowKg != null && result.forecastHighKg != null)
            String.format(Locale.US, "%.1f–%.1f kg", result.forecastLowKg, result.forecastHighKg)
            else "—")
        Text(result.message, color = mint, fontSize = 12.sp)
        Text("BMR: Vücudunun dinlenirken harcadığı tahmini enerji. TDEE: Günlük hareketlerin dahil yaklaşık toplam enerji ihtiyacın. AME: Girdiğin yemek ve tartı kayıtlarından hesaplanan alternatif tahmin; eksik kayıtlar sonucu bozabilir. 30 günlük kilo değeri kesin sonuç değil, bugünkü eğilimin sürmesi durumunda bir örnektir. Tartıdaki su değişimleri yağ değişimi değildir. Bunlar tıbbi ölçüm değil, yaklaşık hesaplamalardır.",
            color = muted, fontSize = 11.sp)
        }
    }
}
