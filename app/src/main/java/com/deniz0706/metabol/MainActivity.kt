package com.deniz0706.metabol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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

private val Bg = Color(0xFF070B12)
private val Surface = Color(0xFF121C28)
private val Surface2 = Color(0xFF203140)
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
                MetabolApp(entries, onAdd = { persist(entries + it) }, onEdit = { old, updated ->
                    persist(entries.map { if (it == old) updated else it })
                }, onDelete = {
                    persist(entries.filterNot { entry -> entry == it })
                })
            }
        }
    }
}

@Composable
private fun MetabolApp(entries: List<WeightRecord>, onAdd: (WeightRecord) -> Unit, onEdit: (WeightRecord, WeightRecord) -> Unit, onDelete: (WeightRecord) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var addDialog by remember { mutableStateOf(false) }
    var goalRevision by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<WeightRecord?>(null) }
    var pendingDelete by remember { mutableStateOf<WeightRecord?>(null) }
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            Surface(color = Bg) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                    .background(Surface, RoundedCornerShape(25.dp))
                    .border(1.dp, Edge, RoundedCornerShape(25.dp))
                    .padding(horizontal = 7.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    val labels = listOf("Özet", "Beslenme", "Analiz", "Vücut", "Ayarlar")
                    val symbols = listOf("◈", "◉", "⌁", "◇", "⚙")
                    labels.forEachIndexed { index, title ->
                        val active = tab == index
                        Column(
                            Modifier.weight(1f)
                                .background(if (active) Surface2 else Color.Transparent, RoundedCornerShape(17.dp))
                                .clickable { tab = index }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(symbols[index], fontSize = 20.sp,
                                color = if (active) Mint else Muted)
                            Text(title, fontSize = 10.sp,
                                color = if (active) White else Muted,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    ) { insets ->
        // Render only the active screen. Crossfade kept two full screens (including
        // charts and food search) composing simultaneously on every tab switch.
        key(tab) {
            when (tab) {
                0 -> Dashboard(entries, tab, goalRevision,
                    onAddClick = { editing = null; addDialog = true },
                    onEdit = { editing = it; addDialog = true },
                    onDelete = { pendingDelete = it }, onNutritionClick = { tab = 1 },
                    modifier = Modifier.padding(insets))
                1 -> NutritionScreen(androidx.compose.ui.platform.LocalContext.current,
                    Modifier.padding(insets))
                2 -> AnalysisScreen(entries.map { WeightPoint(it.time, it.kg) },
                    Modifier.padding(insets))
                3 -> BodyCompositionScreen(androidx.compose.ui.platform.LocalContext.current,
                    Modifier.padding(insets))
                else -> SettingsScreen(androidx.compose.ui.platform.LocalContext.current,
                    Modifier.padding(insets), onGoalChanged = { goalRevision++ })
            }
        }
    }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Ölçümü sil?") },
            text = { Text("Bu kilo ölçümü kalıcı olarak silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete?.let(onDelete)
                    pendingDelete = null
                }) { Text("Sil", color = Color(0xFFFF879A)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Vazgeç") }
            }
        )
    }
    if (addDialog) {
        var value by remember(editing) { mutableStateOf(editing?.kg?.let { numberFormat.format(it) } ?: "") }
        var dateValue by remember(editing) { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(editing?.time ?: System.currentTimeMillis()))) }
        val inputDateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false } }
        val parsedDate = remember(dateValue) {
            val position = ParsePosition(0)
            val parsed = inputDateFormat.parse(dateValue, position)
            parsed?.takeIf { position.index == dateValue.length && it.time <= System.currentTimeMillis() }
        }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            containerColor = Surface,
            title = { Text(if (editing == null) "Yeni kilo ölçümü" else "Ölçümü düzenle", color = White) },
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
                        val updated = WeightRecord(calendar.timeInMillis, kg)
                        if (editing == null) onAdd(updated) else onEdit(editing!!, updated)
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
    entries: List<WeightRecord>, tab: Int, goalRevision: Int, onAddClick: () -> Unit,
    onEdit: (WeightRecord) -> Unit, onDelete: (WeightRecord) -> Unit,
    onNutritionClick: () -> Unit, modifier: Modifier = Modifier
) {
    val latest = entries.lastOrNull()
    val context = androidx.compose.ui.platform.LocalContext.current
    val goal = remember(goalRevision) {
        context.getSharedPreferences("metabol_profile", android.content.Context.MODE_PRIVATE)
            .getString("goal_weight", null)?.toFloatOrNull()
    }
    val todayNutrition = remember {
        val nutrition = context.getSharedPreferences("metabol_nutrition", android.content.Context.MODE_PRIVATE)
        val todayKey = "meals_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        runCatching {
            val array = JSONArray(nutrition.getString(todayKey, "[]"))
            var kcal = 0
            var protein = 0.0
            for (i in 0 until array.length()) {
                val meal = array.getJSONObject(i)
                kcal += meal.optInt("calories", 0)
                protein += meal.optDouble("protein", 0.0)
            }
            Triple(array.length(), kcal, protein)
        }.getOrDefault(Triple(0, 0, 0.0))
    }
    val first = entries.firstOrNull()
    val change = if (latest != null && first != null) latest.kg - first.kg else null
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Header() }
        item {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("YOUR OVERVIEW", color = Mint, fontSize = 10.sp, letterSpacing = 1.8.sp)
                    Text(SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR")).format(Date()),
                        color = Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text("Genel Bakış", color = White, fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text("İlerlemen tek bir yerde.", color = Muted, fontSize = 13.sp)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF21453F), Color(0xFF182D39), Surface)), RoundedCornerShape(28.dp)
                ).border(1.dp, Edge, RoundedCornerShape(28.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("GÜNCEL AĞIRLIK", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("●  CİHAZINDA", color = Mint, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(latest?.let { numberFormat.format(it.kg) } ?: "—", color = White, fontSize = 62.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Text("kg", color = Muted, fontSize = 22.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                if (change != null) {
                    Text((if (change > 0) "+" else "") + numberFormat.format(change) + " kg  ·  ilk ölçümden beri",
                        color = if (change <= 0f) Mint else Violet, fontSize = 13.sp)
                } else Text("İlk ölçümünü ekleyerek başla.", color = Muted, fontSize = 13.sp)
                if (goal != null && latest != null) {
                    HorizontalDivider(color = Edge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("KİŞİSEL HEDEF", color = Muted, fontSize = 11.sp)
                        Text(numberFormat.format(goal) + " kg", color = Mint, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Hedefe fark: " + numberFormat.format(abs(latest.kg - goal)) + " kg",
                        color = White, fontSize = 13.sp
                    )
                    if (first != null && abs(first.kg - goal) > 0.05f) {
                        val progress = (1f - abs(latest.kg - goal) / abs(first.kg - goal)).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = Mint,
                            trackColor = Surface2
                        )
                        Text("İlk ölçüme göre hedef ilerlemesi: " + (progress * 100).roundToInt() + "%",
                            color = Muted, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = Edge)
                WeightChart(entries, Modifier.fillMaxWidth().height(340.dp))
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
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF1B2E3B), Surface)),
                        RoundedCornerShape(24.dp))
                    .border(1.dp, Edge, RoundedCornerShape(24.dp))
                    .clickable(onClick = onNutritionClick).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("BUGÜNKÜ BESLENME", color = Mint, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Text("Günlüğe git  ›", color = Mint, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${todayNutrition.second}", color = White,
                            fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text("kayıtlı kcal", color = Muted, fontSize = 11.sp)
                    }
                    Column {
                        Text(String.format(Locale.US, "%.1f g", todayNutrition.third),
                            color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("protein", color = Muted, fontSize = 11.sp)
                    }
                    Column {
                        Text("${todayNutrition.first}", color = White,
                            fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("öğün", color = Muted, fontSize = 11.sp)
                    }
                }
                if (todayNutrition.first == 0) {
                    Text("Bugün henüz öğün kaydı yok.", color = Muted, fontSize = 12.sp)
                }
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
                        Spacer(Modifier.width(12.dp))
                        Text("Düzenle", color = Mint, fontSize = 12.sp,
                            modifier = Modifier.clickable { onEdit(entry) }.padding(5.dp))
                        Spacer(Modifier.width(8.dp))
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
    var smoothLine by remember { mutableStateOf(true) }
    var showDots by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }
    val revealProgress by animateFloatAsState(
        targetValue = if (reveal) 1f else 0f,
        animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing),
        label = "Kilo grafiği çizim animasyonu"
    )
    var chartPeriod by remember { mutableIntStateOf(0) }
    val today = System.currentTimeMillis()
    val records = remember(entries, chartPeriod) {
        entries.sortedBy { it.time }.filter {
            val days = when (chartPeriod) { 1 -> 7L; 2 -> 30L; 3 -> 90L; else -> null }
            days == null || (it.time >= today - days * 86_400_000L && it.time <= today)
        }
    }
    LaunchedEffect(records) {
        reveal = false
        withFrameNanos { }
        reveal = true
    }
    val axisPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(139, 156, 178)
            textAlign = android.graphics.Paint.Align.LEFT
        }
    }
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
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Tümü", "7G", "30G", "90G").forEachIndexed { index, label ->
                FilterChip(
                    selected = chartPeriod == index,
                    onClick = { chartPeriod = index; selected = -1 },
                    label = { Text(label, fontSize = 10.sp) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Mint, selectedLabelColor = Bg,
                        containerColor = Surface2, labelColor = Muted
                    )
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Yumuşak çizgi", color = Muted, fontSize = 11.sp)
            Switch(checked = smoothLine, onCheckedChange = { smoothLine = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Bg, checkedTrackColor = Mint))
            Spacer(Modifier.width(8.dp))
            Text("Noktalar", color = Muted, fontSize = 11.sp)
            Switch(checked = showDots, onCheckedChange = { showDots = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Bg, checkedTrackColor = Mint))
        }
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
                axisPaint.textSize = 10.sp.toPx()
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
                        points.drop(1).forEachIndexed { index, point ->
                            val previous = points[index]
                            if (smoothLine) {
                                val midX = (previous.x + point.x) / 2f
                                cubicTo(midX, previous.y, midX, point.y, point.x, point.y)
                            } else {
                                lineTo(point.x, point.y)
                            }
                        }
                    }
                    val area = Path().apply {
                        addPath(path)
                        lineTo(points.last().x, bottom)
                        lineTo(points.first().x, bottom)
                        close()
                    }
                    clipRect(left = left, top = top, right = left + width * revealProgress, bottom = bottom + 1.dp.toPx()) {
                        drawPath(area, Brush.verticalGradient(listOf(Mint.copy(alpha = .22f), Mint.copy(alpha = 0f))))
                        drawPath(path, Mint, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                points.forEachIndexed { index, point ->
                    if (point.x > left + width * revealProgress + 1.dp.toPx()) return@forEachIndexed
                    val active = index == selected
                    if (active) {
                        drawLine(Violet.copy(alpha = .65f), Offset(point.x, top), Offset(point.x, bottom), 1.dp.toPx())
                        drawCircle(Mint.copy(alpha = .20f), 11.dp.toPx(), point)
                        drawCircle(Bg, 6.dp.toPx(), point)
                        drawCircle(Mint, 4.dp.toPx(), point)
                    } else if (showDots) {
                        drawCircle(Surface, 4.dp.toPx(), point)
                        drawCircle(Mint, 2.5.dp.toPx(), point)
                    }
                }
            }
            if (selected in records.indices) {
                val chosen = records[selected]
                Surface(
                    color = Surface2,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR"))
                                .format(Date(chosen.time)),
                            color = White, fontSize = 13.sp
                        )
                        Text(
                            "${numberFormat.format(chosen.kg)} kg",
                            color = Mint, fontWeight = FontWeight.Bold, fontSize = 20.sp
                        )
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
