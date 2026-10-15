package com.deniz0706.metabol

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

private val ABack = Color(0xFF070B12)
private val APanel = Color(0xFF121C28)
private val AText = Color(0xFFF4F8FC)
private val AMuted = Color(0xFF8B9CB2)
private val AMint = Color(0xFF71E8C4)
private val AEdge = Color(0xFF2A394B)
private val fmt = DecimalFormat("0.0")

internal data class WeightPoint(val timestamp: Long, val kg: Float)
internal data class TrendSummary(
    val count: Int,
    val first: Float?,
    val last: Float?,
    val min: Float?,
    val max: Float?,
    val change: Float?,
    val average: Float?,
    val weeklyRate: Double?,
    val smoothedLast: Float?
)

internal fun summarizeWeights(points: List<WeightPoint>): TrendSummary {
    val sorted = points.sortedBy { it.timestamp }
    val first = sorted.firstOrNull()
    val last = sorted.lastOrNull()
    val deltaDays = if (first != null && last != null) (last.timestamp - first.timestamp) / 86_400_000.0 else 0.0
    val weeklyRate = if (sorted.size >= 2 && deltaDays >= 7.0) {
        (last!!.kg - first!!.kg) * 7.0 / deltaDays
    } else null
    return TrendSummary(
        sorted.size, first?.kg, last?.kg, sorted.minOfOrNull { it.kg },
        sorted.maxOfOrNull { it.kg },
        if (first != null && last != null) last.kg - first.kg else null,
        if (sorted.isEmpty()) null else sorted.map { it.kg }.average().toFloat(),
        weeklyRate,
        sorted.takeLast(7).takeIf { it.isNotEmpty() }?.map { it.kg }?.average()?.toFloat()
    )
}

@Composable
internal fun AnalysisScreen(points: List<WeightPoint>, modifier: Modifier = Modifier) {
    var period by remember { mutableIntStateOf(0) }
    val now = System.currentTimeMillis()
    val filtered = remember(points, period, now / 86_400_000L) {
        val days = when (period) { 1 -> 7L; 2 -> 30L; 3 -> 90L; else -> null }
        if (days == null) points else points.filter { it.timestamp >= now - days * 86_400_000L && it.timestamp <= now }
    }
    val stats = remember(filtered) { summarizeWeights(filtered) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(modifier.fillMaxSize().background(ABack),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SciencePanel(context, points) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("M E T A B O L", color = AMint, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp, fontSize = 13.sp)
                Text("INSIGHTS  /  02", color = AMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(24.dp))
            Text("Analiz", color = AText, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text("Sayıların ardındaki değişimi keşfet.", color = AMuted, fontSize = 13.sp)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Tümü", "7 gün", "30 gün", "90 gün").forEachIndexed { index, label ->
                    FilterChip(
                        selected = period == index, onClick = { period = index },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AMint, selectedLabelColor = ABack,
                            containerColor = APanel, labelColor = AMuted
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF21473F), Color(0xFF172C37), APanel)),
                    RoundedCornerShape(28.dp))
                .border(1.dp, Color(0xFF2C554F), RoundedCornerShape(28.dp))
                .padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NET KİLO DEĞİŞİMİ", color = AMint, fontSize = 10.sp, letterSpacing = 1.8.sp)
                Text(stats.change?.let { (if (it > 0f) "+" else "") + fmt.format(it) + " kg" } ?: "—",
                    color = AText, fontSize = 43.sp, fontWeight = FontWeight.ExtraBold)
                Text("${stats.count} kayıt · ${listOf("Tüm zamanlar", "Son 7 gün", "Son 30 gün", "Son 90 gün")[period]}",
                    color = AMuted, fontSize = 12.sp)
                HorizontalDivider(color = Color(0xFF315047))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("İLK ÖLÇÜM", color = AMuted, fontSize = 10.sp)
                        Text(stats.first?.let { fmt.format(it) + " kg" } ?: "—",
                            color = AText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("SON ÖLÇÜM", color = AMuted, fontSize = 10.sp)
                        Text(stats.last?.let { fmt.format(it) + " kg" } ?: "—",
                            color = AMint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(APanel, RoundedCornerShape(24.dp))
                .border(1.dp, AEdge, RoundedCornerShape(24.dp)).padding(20.dp)) {
                Text("ÖLÇÜM ÖZETİ", color = AMuted, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnalysisValue("İlk ölçüm", stats.first?.let { fmt.format(it) + " kg" } ?: "—", Modifier.weight(1f))
                    AnalysisValue("Son ölçüm", stats.last?.let { fmt.format(it) + " kg" } ?: "—", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = AEdge)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnalysisValue("En düşük", stats.min?.let { fmt.format(it) + " kg" } ?: "—", Modifier.weight(1f))
                    AnalysisValue("En yüksek", stats.max?.let { fmt.format(it) + " kg" } ?: "—", Modifier.weight(1f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalysisTile("TOPLAM DEĞİŞİM", stats.change?.let { (if (it > 0) "+" else "") + fmt.format(it) + " kg" } ?: "—", Modifier.weight(1f))
                AnalysisTile("ORTALAMA", stats.average?.let { fmt.format(it) + " kg" } ?: "—", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalysisTile("HAFTALIK HIZ", stats.weeklyRate?.let {
                    (if (it > 0) "+" else "") + fmt.format(it) + " kg"
                } ?: "—", Modifier.weight(1f))
                AnalysisTile("SON 7 ÖLÇÜM", stats.smoothedLast?.let { fmt.format(it) + " kg" } ?: "—", Modifier.weight(1f))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(APanel, RoundedCornerShape(24.dp)).padding(20.dp)) {
                Text("Veriyi doğru yorumla", color = AText, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (stats.count < 2) "Değişim analizi için en az iki farklı tarihte ölçüm kaydet."
                    else if (stats.weeklyRate == null) "Haftalık hız için en az 7 güne yayılan ölçümler gerekiyor."
                    else "Haftalık hız ilk ve son ölçüm arasındaki ortalama değişimdir; gelecekteki kiloyu kesin olarak tahmin etmez.",
                    color = AMuted, fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                Text("Günlük su dengesi ve ölçüm saati tartıyı etkileyebilir. Son 7 ölçüm ortalaması, 7 günlük zaman ortalaması değildir.",
                    color = AMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AnalysisValue(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = AMuted, fontSize = 12.sp)
        Text(value, color = AText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AnalysisTile(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(APanel, RoundedCornerShape(20.dp)).padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, color = AMuted, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = AMint, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}
