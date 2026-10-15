package com.deniz0706.metabol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Bg = Color(0xFF080D15)
private val Surface = Color(0xFF131C2A)
private val Surface2 = Color(0xFF1B2839)
private val Mint = Color(0xFF71E8C4)
private val Violet = Color(0xFFA99BFF)
private val Muted = Color(0xFF8B9CB2)
private val White = Color(0xFFF4F8FC)
private val Edge = Color(0xFF2A394B)
private val dateFormat get() = SimpleDateFormat("dd MMM", Locale("tr", "TR"))
private val numberFormat get() = java.text.DecimalFormat("0.0")

private data class WeightRecord(val time: Long, val kg: Float)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("metabol_local", MODE_PRIVATE)
        setContent {
            var entries by remember {
                mutableStateOf(runCatching {
                    val array = JSONArray(prefs.getString("weights", "[]"))
                    (0 until array.length()).map {
                        val item = array.getJSONObject(it)
                        WeightRecord(item.getLong("time"), item.getDouble("kg").toFloat())
                    }.sortedBy { it.time }
                }.getOrDefault(emptyList()))
            }
            fun persist(updated: List<WeightRecord>) {
                entries = updated.sortedBy { it.time }
                val json = JSONArray()
                entries.forEach { json.put(JSONObject().put("time", it.time).put("kg", it.kg.toDouble())) }
                prefs.edit().putString("weights", json.toString()).apply()
            }
            MaterialTheme(colorScheme = darkColorScheme(primary = Mint, background = Bg, surface = Surface)) {
                MetabolApp(entries, onAdd = { persist(entries + it) }, onDelete = {
                    persist(entries.filterNot { entry -> entry == it })
                })
            }
        }
    }
}

@Composable
private fun MetabolApp(entries: List<WeightRecord>, onAdd: (WeightRecord) -> Unit, onDelete: (WeightRecord) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var addDialog by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Surface, tonalElevation = 0.dp) {
                listOf("Özet", "Beslenme", "Analiz", "Vücut", "Ayarlar").forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = tab == index, onClick = { tab = index },
                        icon = { Text(listOf("◈", "◉", "⌁", "◇", "⚙")[index], fontSize = 20.sp) },
                        label = { Text(title, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Mint, selectedTextColor = Mint, indicatorColor = Surface2)
                    )
                }
            }
        }
    ) { insets ->
        if (tab == 0) {
            Dashboard(entries, tab, onAddClick = { addDialog = true }, onDelete = onDelete, modifier = Modifier.padding(insets))
        } else if (tab == 2) {
            AnalysisScreen(entries.map { WeightPoint(it.time, it.kg) }, Modifier.padding(insets))
        } else if (tab == 1) {
            NutritionScreen(androidx.compose.ui.platform.LocalContext.current, Modifier.padding(insets))
        } else if (tab == 3) {
            BodyCompositionScreen(androidx.compose.ui.platform.LocalContext.current, Modifier.padding(insets))
        } else {
            SettingsScreen(androidx.compose.ui.platform.LocalContext.current, Modifier.padding(insets))
        }
    }
    if (addDialog) {
        var value by remember { mutableStateOf("") }
        var dateValue by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
        val inputDateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false } }
        val parsedDate = remember(dateValue) {
            val position = ParsePosition(0)
            val parsed = inputDateFormat.parse(dateValue, position)
            parsed?.takeIf { position.index == dateValue.length && it.time <= System.currentTimeMillis() }
        }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            containerColor = Surface,
            title = { Text("Yeni kilo ölçümü", color = White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Geçmiş tarihli ölçümlerini de kaydedebilirsin.", color = Muted)
                    OutlinedTextField(
                        value = dateValue, onValueChange = { dateValue = it },
                        label = { Text("Tarih (YYYY-AA-GG)") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White)
                    )
                    OutlinedTextField(
                        value = value, onValueChange = { value = it },
                        label = { Text("Kilo (kg)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val kg = value.replace(',', '.').toFloatOrNull()
                    if (kg != null && kg in 20f..500f && parsedDate != null) {
                        val calendar = Calendar.getInstance().apply { time = parsedDate; set(Calendar.HOUR_OF_DAY, 12) }
                        onAdd(WeightRecord(calendar.timeInMillis, kg))
                        addDialog = false
                    }
                }) { Text("Kaydet", color = Mint) }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("İptal", color = Muted) } }
        )
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("M E T A B O L", color = Mint, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
            Spacer(Modifier.height(5.dp))
            Text("Vücudunun veriye dönüşen hikâyesi.", color = Muted, fontSize = 12.sp)
        }
        Box(Modifier.size(44.dp).background(Surface2, CircleShape).border(1.dp, Edge, CircleShape), contentAlignment = Alignment.Center) {
            Text("M", color = Mint, fontWeight = FontWeight.Black, fontSize = 19.sp)
        }
    }
}

@Composable
private fun Dashboard(
    entries: List<WeightRecord>, tab: Int, onAddClick: () -> Unit,
    onDelete: (WeightRecord) -> Unit, modifier: Modifier = Modifier
) {
    val latest = entries.lastOrNull()
    val first = entries.firstOrNull()
    val change = if (latest != null && first != null) latest.kg - first.kg else null
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Header() }
        item {
            Column {
                Text(if (tab == 0) "GENEL BAKIŞ" else if (tab == 2) "ANALİZ MERKEZİ" else "VÜCUT ÖLÇÜMLERİ",
                    color = Muted, fontSize = 11.sp, letterSpacing = 2.5.sp)
                Spacer(Modifier.height(6.dp))
                Text(if (tab == 0) "İlerlemeni keşfet." else "Verilerini keşfet.", color = White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(Color(0xFF1C3040), Surface, Surface)), RoundedCornerShape(28.dp)
                ).border(1.dp, Edge, RoundedCornerShape(28.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("GÜNCEL AĞIRLIK", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("●  YEREL VERİ", color = Mint, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(latest?.let { numberFormat.format(it.kg) } ?: "—", color = White, fontSize = 53.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("kg", color = Muted, fontSize = 22.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                if (change != null) {
                    Text((if (change > 0) "+" else "") + numberFormat.format(change) + " kg  ·  ilk ölçümden beri",
                        color = if (change <= 0f) Mint else Violet, fontSize = 13.sp)
                } else Text("İlk ölçümünü ekleyerek başla.", color = Muted, fontSize = 13.sp)
                HorizontalDivider(color = Edge)
                WeightChart(entries, Modifier.fillMaxWidth().height(248.dp))
                Button(onClick = onAddClick, modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg)) {
                    Text("+   Kilo ölçümü ekle", fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("ÖLÇÜM SAYISI", entries.size.toString(), "toplam kayıt", Modifier.weight(1f))
                MetricCard("DEĞİŞİM", change?.let { (if (it > 0) "+" else "") + numberFormat.format(it) } ?: "—",
                    "kilogram", Modifier.weight(1f))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(24.dp)).border(1.dp, Edge, RoundedCornerShape(24.dp)).padding(20.dp)) {
                Text("Ölçüm geçmişi", color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                if (entries.isEmpty()) Text("Henüz kayıt yok. İlk kilonu ekle.", color = Muted)
                entries.takeLast(8).reversed().forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(dateFormat.format(Date(entry.time)), color = Muted, modifier = Modifier.weight(1f))
                        Text(numberFormat.format(entry.kg) + " kg", color = White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(14.dp))
                        Text("Sil", color = Color(0xFFFF879A), fontSize = 12.sp,
                            modifier = Modifier.clickable { onDelete(entry) }.padding(5.dp))
                    }
                }
            }
        }
        item {
            Text("METABOL  ·  Developed by Deniz  ·  Yerel önizleme sürümü", color = Muted,
                fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, caption: String, modifier: Modifier) {
    Column(modifier.background(Surface, RoundedCornerShape(22.dp)).border(1.dp, Edge, RoundedCornerShape(22.dp)).padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Muted, fontSize = 10.sp, letterSpacing = 1.3.sp)
        Text(value, color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(caption, color = Mint, fontSize = 11.sp)
    }
}

@Composable
private fun WeightChart(entries: List<WeightRecord>, modifier: Modifier = Modifier) {
    var selected by remember(entries) { mutableIntStateOf(-1) }
    val records = entries.sortedBy { it.time }
    val min = (records.minOfOrNull { it.kg } ?: 80f) - 2f
    val max = (records.maxOfOrNull { it.kg } ?: 120f) + 2f
    val range = (max - min).coerceAtLeast(1f)
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("AĞIRLIK EĞİLİMİ", color = Muted, fontSize = 10.sp, letterSpacing = 1.6.sp)
            Text(if (selected in records.indices)
                dateFormat.format(Date(records[selected].time)) + "  ·  " + numberFormat.format(records[selected].kg) + " kg"
                else "Grafiğe dokun", color = Mint, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (records.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Grafik için ölçüm ekle", color = Muted)
            }
        } else {
            val start = records.first().time
            val span = (records.last().time - start).coerceAtLeast(1L)
            Canvas(Modifier.fillMaxWidth().weight(1f).pointerInput(records) {
                detectTapGestures { touch ->
                    val left = 12.dp.toPx()
                    val right = size.width - 48.dp.toPx()
                    val width = (right - left).coerceAtLeast(1f)
                    val fraction = ((touch.x - left) / width).coerceIn(0f, 1f)
                    selected = records.indices.minByOrNull { idx ->
                        abs((records[idx].time - start).toFloat() / span - fraction)
                    } ?: -1
                }
            }) {
                val left = 12.dp.toPx()
                val right = size.width - 48.dp.toPx()
                val top = 12.dp.toPx()
                val bottom = size.height - 22.dp.toPx()
                val width = (right - left).coerceAtLeast(1f)
                val height = (bottom - top).coerceAtLeast(1f)
                fun point(record: WeightRecord): Offset {
                    val x = if (records.size == 1) 0.5f else (record.time - start).toFloat() / span
                    return Offset(left + x * width, top + (max - record.kg) / range * height)
                }
                val axisPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(139, 156, 178)
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.LEFT
                }
                repeat(4) { index ->
                    val y = top + index * height / 3f
                    drawLine(Edge, Offset(left, y), Offset(right, y), 1.dp.toPx())
                    val tickValue = max - range * index / 3f
                    drawContext.canvas.nativeCanvas.drawText(
                        numberFormat.format(tickValue), right + 6.dp.toPx(),
                        y + 4.dp.toPx(), axisPaint
                    )
                }
                val points = records.map(::point)
                if (points.size > 1) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    val area = Path().apply {
                        addPath(path)
                        lineTo(points.last().x, bottom)
                        lineTo(points.first().x, bottom)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(Mint.copy(alpha = .24f), Mint.copy(alpha = 0f))))
                    drawPath(path, Mint, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
                points.forEachIndexed { index, point ->
                    val active = index == selected
                    drawCircle(if (active) Bg else Mint, if (active) 7.dp.toPx() else 3.dp.toPx(), point)
                    if (active) {
                        drawLine(Violet.copy(alpha = .75f), Offset(point.x, top), Offset(point.x, bottom), 1.dp.toPx())
                        drawCircle(Mint, 5.dp.toPx(), point)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dateFormat.format(Date(records.first().time)), color = Muted, fontSize = 11.sp)
                Text(dateFormat.format(Date(records.last().time)), color = Muted, fontSize = 11.sp)
            }
        }
    }
}
