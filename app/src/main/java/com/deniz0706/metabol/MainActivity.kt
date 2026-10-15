package com.deniz0706.metabol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF0B101B)
private val Panel = Color(0xFF171F30)
private val Mint = Color(0xFF7FE9D0)
private val Muted = Color(0xFF9DAAC0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MetabolHome() }
    }
}

@Composable
private fun MetabolHome() {
    val weights = remember { listOf(115.1f, 110.8f, 108.7f, 109.6f, 108.8f, 106.7f, 108.1f, 108f) }
    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Panel, primary = Mint)) {
        Column(
            modifier = Modifier.fillMaxSize().background(Ink).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text("METABOL", color = Mint, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 4.sp)
            Text("Understand your body.", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("GÜNCEL AĞIRLIK", color = Muted, fontSize = 12.sp, letterSpacing = 2.sp)
                    Text("108,0 kg", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    Text("Başlangıçtan beri −7,1 kg", color = Mint)
                    WeightChart(weights, Modifier.fillMaxWidth().height(210.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Başlangıç", color = Muted, fontSize = 12.sp)
                        Text("Son ölçüm", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Bilimsel analiz", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Ağırlık değişimi günlük dalgalanmalardan etkilenir. Düzenli ölçümlerle trend daha güvenilir hale gelir.", color = Muted)
                }
            }
            Text("Önizleme • Örnek veriler • Kayıt sistemi sonraki aşamada", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WeightChart(values: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val low = 104f
        val high = 116f
        val pad = 14.dp.toPx()
        val width = size.width - 2 * pad
        val height = size.height - 2 * pad
        repeat(4) { i ->
            val y = pad + i * height / 3
            drawLine(Muted.copy(alpha = 0.18f), Offset(pad, y), Offset(size.width - pad, y), 1.dp.toPx())
        }
        val points = values.mapIndexed { index, value ->
            Offset(pad + width * index / (values.size - 1), pad + (high - value) / (high - low) * height)
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, Mint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.forEach { drawCircle(Mint, radius = 4.dp.toPx(), center = it) }
    }
}
