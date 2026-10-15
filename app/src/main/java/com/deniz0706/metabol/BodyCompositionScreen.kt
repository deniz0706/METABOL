package com.deniz0706.metabol

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
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
    val latest = records.lastOrNull()
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
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BodyMint, contentColor = BodyBg)) {
                Text("+  Vücut ölçümü ekle", fontWeight = FontWeight.Bold)
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
                    TextButton(onClick = { save(records.filterNot { it.id == r.id }) }) {
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
    if (showAdd) {
        var fat by remember { mutableStateOf("") }
        var muscle by remember { mutableStateOf("") }
        var waist by remember { mutableStateOf("") }
        fun parse(value: String) = value.replace(',', '.').toDoubleOrNull()
        val valid = listOf(fat, muscle, waist).any { it.isNotBlank() } &&
            (fat.isBlank() || (parse(fat) ?: -1.0) in 0.0..75.0) &&
            (muscle.isBlank() || (parse(muscle) ?: -1.0) in 0.0..300.0) &&
            (waist.isBlank() || (parse(waist) ?: -1.0) in 20.0..300.0)
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = BodyCard,
            title = { Text("Yeni vücut ölçümü", color = BodyText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bilmediğin alanları boş bırakabilirsin.", color = BodyMuted, fontSize = 12.sp)
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
                        save(records + BodyRecord(System.currentTimeMillis(), parse(fat), parse(muscle), parse(waist)))
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
