package com.deniz0706.metabol

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Date
import java.util.Locale

private val BodyBg = Color(0xFF080D15)
private val BodyCard = Color(0xFF131C2A)
private val BodyMint = Color(0xFF71E8C4)
private val BodyText = Color(0xFFF4F8FC)
private val BodyMuted = Color(0xFF8B9CB2)

private data class BodyRecord(
    val id: Long, val fatPercent: Double?, val muscleKg: Double?, val waistCm: Double?
)

@Composable
internal fun BodyCompositionScreen(context: Context, modifier: Modifier = Modifier) {
    val prefs = remember(context) { context.getSharedPreferences("metabol_body", Context.MODE_PRIVATE) }
    var records by remember {
        mutableStateOf(runCatching {
            val array = JSONArray(prefs.getString("records", "[]"))
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                fun optional(key: String) = if (obj.isNull(key)) null else obj.getDouble(key)
                BodyRecord(obj.getLong("id"), optional("fat"), optional("muscle"), optional("waist"))
            }.sortedBy { it.id }
        }.getOrDefault(emptyList()))
    }
    fun save(updated: List<BodyRecord>) {
        records = updated.sortedBy { it.id }
        val array = JSONArray()
        records.forEach { r ->
            array.put(JSONObject().put("id", r.id).put("fat", r.fatPercent)
                .put("muscle", r.muscleKg).put("waist", r.waistCm))
        }
        prefs.edit().putString("records", array.toString()).apply()
    }
    var showAdd by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<BodyRecord?>(null) }
    var pendingDeleteRecord by remember { mutableStateOf<BodyRecord?>(null) }
    val latest = records.lastOrNull()
    val previous = records.dropLast(1).lastOrNull()
    LazyColumn(modifier.fillMaxSize().background(BodyBg), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("M E T A B O L", color = BodyMint, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(24.dp))
            Text("VÜCUT KOMPOZİSYONU", color = BodyMuted, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Ölçümlerini takip et.", color = BodyText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BodyMetric("YAĞ ORANI", latest?.fatPercent, "%", Modifier.weight(1f))
                BodyMetric("KAS KÜTLESİ", latest?.muscleKg, "kg", Modifier.weight(1f))
            }
        }
        item { BodyMetric("BEL ÇEVRESİ", latest?.waistCm, "cm", Modifier.fillMaxWidth()) }
        if (previous != null && latest != null) {
            item {
                Column(Modifier.fillMaxWidth().background(BodyCard, RoundedCornerShape(22.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("SON İKİ ÖLÇÜM ARASINDAKİ FARK", color = BodyMuted,
                        fontSize = 10.sp, letterSpacing = 1.5.sp)
                    listOf(
                        Triple("Yağ oranı", latest.fatPercent, previous.fatPercent),
                        Triple("Kas kütlesi", latest.muscleKg, previous.muscleKg),
                        Triple("Bel çevresi", latest.waistCm, previous.waistCm)
                    ).forEach { (label, current, prior) ->
                        if (current != null && prior != null) {
                            val delta = current - prior
                            val unit = if (label == "Yağ oranı") "puan" else if (label == "Kas kütlesi") "kg" else "cm"
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, color = BodyMuted, fontSize = 13.sp)
                                Text(String.format(Locale.US, "%+.1f %s", delta, unit),
                                    color = if (delta <= 0.0) BodyMint else BodyText,
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = { editingRecord = null; showAdd = true }, modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BodyMint, contentColor = BodyBg)) {
                Text("+  Vücut ölçümü ekle", fontWeight = FontWeight.Bold)
            }
        }
        if (records.size >= 2) {
            item {
                var chartMode by remember { mutableIntStateOf(0) }
                Column(Modifier.fillMaxWidth().background(BodyCard, RoundedCornerShape(22.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("VÜCUT ÖLÇÜM EĞİLİMİ", color = BodyMuted, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Yağ %", "Kas kg", "Bel cm").forEachIndexed { index, title ->
                            FilterChip(
                                selected = chartMode == index, onClick = { chartMode = index },
                                label = { Text(title, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BodyMint,
                                    selectedLabelColor = BodyBg
                                )
                            )
                        }
                    }
                    val data = records.mapNotNull { record ->
                        val value = when (chartMode) {
                            0 -> record.fatPercent
                            1 -> record.muscleKg
                            else -> record.waistCm
                        }
                        value?.let { record.id to it }
                    }
                    if (data.size < 2) {
                        Text("Grafik için bu alanda en az iki ölçüm gerekli.",
                            color = BodyMuted, fontSize = 12.sp)
                    } else {
                        val minimum = data.minOf { it.second }
                        val maximum = data.maxOf { it.second }
                        val padding = ((maximum - minimum) * 0.12).coerceAtLeast(0.5)
                        val low = minimum - padding
                        val high = maximum + padding
                        val firstTime = data.first().first
                        val timeSpan = (data.last().first - firstTime).coerceAtLeast(1L)
                        Text(String.format(Locale.US, "Min %.1f    Maks %.1f", minimum, maximum),
                            color = BodyMint, fontSize = 12.sp)
                        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                            val left = 8.dp.toPx()
                            val right = size.width - 8.dp.toPx()
                            val top = 8.dp.toPx()
                            val bottom = size.height - 8.dp.toPx()
                            repeat(4) { row ->
                                val y = top + row * (bottom - top) / 3f
                                drawLine(Color(0xFF2A394B), Offset(left, y), Offset(right, y), 1.dp.toPx())
                            }
                            val points = data.map { (time, value) ->
                                Offset(
                                    left + (time - firstTime).toFloat() / timeSpan * (right - left),
                                    top + ((high - value) / (high - low)).toFloat() * (bottom - top)
                                )
                            }
                            val path = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(path, BodyMint, style = Stroke(2.5.dp.toPx()))
                            points.forEach { drawCircle(BodyMint, 3.5.dp.toPx(), it) }
                        }
                    }
                }
            }
        }
        item { Text("ÖLÇÜM GEÇMİŞİ", color = BodyMuted, fontSize = 11.sp, letterSpacing = 2.sp) }
        if (records.isEmpty()) {
            item { Text("Henüz vücut ölçümü kaydedilmedi.", color = BodyMuted) }
        } else {
            items(records.size) { index ->
                val r = records.asReversed()[index]
                Row(Modifier.fillMaxWidth().background(BodyCard, RoundedCornerShape(18.dp)).padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR")).format(Date(r.id)),
                            color = BodyText, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        val values = listOfNotNull(r.fatPercent?.let { "Yağ $it%" },
                            r.muscleKg?.let { "Kas $it kg" }, r.waistCm?.let { "Bel $it cm" })
                        Text(values.joinToString("  ·  "), color = BodyMuted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { editingRecord = r; showAdd = true }) {
                        Text("Düzenle", color = BodyMint)
                    }
                    TextButton(onClick = { pendingDeleteRecord = r }) {
                        Text("Sil", color = Color(0xFFFF879A))
                    }
                }
            }
        }
        item {
            Text("Akıllı tartı ölçümleri tahminidir; özellikle kas ve yağ oranı günlük dalgalanabilir.",
                color = BodyMuted, fontSize = 11.sp)
        }
    }
    if (pendingDeleteRecord != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteRecord = null },
            title = { Text("Vücut ölçümünü sil?") },
            text = { Text("Bu ölçüm kalıcı olarak silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteRecord?.let { target ->
                        save(records.filterNot { it.id == target.id })
                    }
                    pendingDeleteRecord = null
                }) { Text("Sil", color = Color(0xFFFF879A)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRecord = null }) { Text("Vazgeç") }
            }
        )
    }
    if (showAdd) {
        var dateText by remember(editingRecord) {
            mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Date(editingRecord?.id ?: System.currentTimeMillis())))
        }
        val inputDateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false } }
        val parsedDate = remember(dateText) {
            val pos = ParsePosition(0)
            inputDateFormat.parse(dateText, pos)?.takeIf {
                pos.index == dateText.length && it.time <= System.currentTimeMillis()
            }
        }
        var fat by remember(editingRecord) { mutableStateOf(editingRecord?.fatPercent?.toString() ?: "") }
        var muscle by remember(editingRecord) { mutableStateOf(editingRecord?.muscleKg?.toString() ?: "") }
        var waist by remember(editingRecord) { mutableStateOf(editingRecord?.waistCm?.toString() ?: "") }
        fun parse(value: String) = value.replace(',', '.').toDoubleOrNull()
        val valid = parsedDate != null && listOf(fat, muscle, waist).any { it.isNotBlank() } &&
            (fat.isBlank() || (parse(fat) ?: -1.0) in 0.0..75.0) &&
            (muscle.isBlank() || (parse(muscle) ?: -1.0) in 0.0..300.0) &&
            (waist.isBlank() || (parse(waist) ?: -1.0) in 20.0..300.0)
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = BodyCard,
            title = { Text(if (editingRecord == null) "Yeni vücut ölçümü" else "Ölçümü düzenle", color = BodyText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bilmediğin alanları boş bırakabilirsin.", color = BodyMuted, fontSize = 12.sp)
                    OutlinedTextField(dateText, { dateText = it }, label = { Text("Tarih (YYYY-AA-GG)") }, singleLine = true)
                    OutlinedTextField(fat, { fat = it }, label = { Text("Yağ oranı (%)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(muscle, { muscle = it }, label = { Text("Kas kütlesi (kg)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(waist, { waist = it }, label = { Text("Bel çevresi (cm)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    if (valid) {
                        val updated = BodyRecord(parsedDate!!.time + 12 * 3_600_000L,
                            parse(fat), parse(muscle), parse(waist))
                        save(if (editingRecord == null) records + updated else records.map {
                            if (it.id == editingRecord!!.id) updated else it
                        })
                        showAdd = false
                    }
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("İptal") } }
        )
    }
}

@Composable
private fun BodyMetric(title: String, value: Double?, unit: String, modifier: Modifier = Modifier) {
    Column(modifier.background(BodyCard, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = BodyMuted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(value?.let { String.format(Locale.US, "%.1f %s", it, unit) } ?: "—",
            color = BodyText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    }
}
