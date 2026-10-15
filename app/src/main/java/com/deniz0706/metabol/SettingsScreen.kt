package com.deniz0706.metabol

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SBack = Color(0xFF070B12)
private val SCard = Color(0xFF121C28)
private val SMint = Color(0xFF71E8C4)
private val SText = Color(0xFFF4F8FC)
private val SMuted = Color(0xFF8B9CB2)
private val preferenceNames = listOf("metabol_local", "metabol_nutrition", "metabol_body", "metabol_profile")

internal fun createBackup(context: Context): String {
    val root = JSONObject()
    root.put("format", "METABOL-backup")
    root.put("version", 1)
    root.put("createdAt", System.currentTimeMillis())
    val data = JSONObject()
    preferenceNames.forEach { name ->
        val group = JSONObject()
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
            if (value is String) group.put(key, value)
        }
        data.put(name, group)
    }
    root.put("data", data)
    return root.toString(2)
}

private fun csvCell(value: String): String {
    val safe = value.replace("\r", " ").replace("\n", " ")
    // Avoid spreadsheet formula interpretation of user-provided food names.
    val escaped = if (safe.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t')) "'" + safe else safe
    return "\"" + escaped.replace("\"", "\"\"") + "\""
}

internal fun exportMeasurementsCsv(context: Context): String {
    val lines = mutableListOf("category,date,value,unit,detail")
    val weightPrefs = context.getSharedPreferences("metabol_local", Context.MODE_PRIVATE)
    val weights = runCatching { JSONArray(weightPrefs.getString("weights", "[]")) }.getOrDefault(JSONArray())
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    for (i in 0 until weights.length()) {
        val item = weights.getJSONObject(i)
        lines += listOf("weight", formatter.format(Date(item.getLong("time"))),
            item.getDouble("kg").toString(), "kg", "").joinToString(",") { csvCell(it) }
    }
    val bodyPrefs = context.getSharedPreferences("metabol_body", Context.MODE_PRIVATE)
    val body = runCatching { JSONArray(bodyPrefs.getString("records", "[]")) }.getOrDefault(JSONArray())
    for (i in 0 until body.length()) {
        val item = body.getJSONObject(i)
        val date = formatter.format(Date(item.getLong("id")))
        for ((field, unit) in listOf("fat" to "percent", "muscle" to "kg", "waist" to "cm")) {
            if (!item.isNull(field)) {
                lines += listOf("body", date, item.getDouble(field).toString(), unit, field)
                    .joinToString(",") { csvCell(it) }
            }
        }
    }
    val nutrition = context.getSharedPreferences("metabol_nutrition", Context.MODE_PRIVATE)
    nutrition.all.toSortedMap().forEach { (key, raw) ->
        if (key.startsWith("meals_") && raw is String) {
            val day = key.removePrefix("meals_")
            val meals = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
            for (i in 0 until meals.length()) {
                val meal = meals.getJSONObject(i)
                val name = meal.getString("name")
                lines += listOf("nutrition", day, meal.getInt("calories").toString(), "kcal", name)
                    .joinToString(",") { csvCell(it) }
                for ((field, unit) in listOf("protein" to "g", "carbs" to "g", "fat" to "g")) {
                    lines += listOf("nutrition", day, meal.getDouble(field).toString(), unit, "$name / $field")
                        .joinToString(",") { csvCell(it) }
                }
            }
        }
    }
    return "\uFEFF" + lines.joinToString("\n") + "\n"
}

internal fun restoreBackup(context: Context, source: String): Boolean {
    if (source.length > 5_000_000) return false
    val root = JSONObject(source)
    if (root.optString("format") != "METABOL-backup" || root.optInt("version") != 1) return false
    val data = root.getJSONObject("data")
    // Fully parse and validate before touching existing preferences.
    val prepared = preferenceNames.associateWith { name ->
        val group = data.optJSONObject(name) ?: if (name == "metabol_profile") JSONObject() else return false
        val entries = mutableMapOf<String, String>()
        val iterator = group.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key.length > 100 || !group.has(key) || group.isNull(key)) return false
            val value = group.getString(key)
            if (value.length > 2_000_000) return false
            when {
                name == "metabol_local" && key == "weights" -> {
                    val array = org.json.JSONArray(value)
                    if (array.length() > 100_000) return false
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val kg = item.getDouble("kg")
                        if (item.getLong("time") < 0 || !kg.isFinite() || kg !in 20.0..500.0) return false
                    }
                }
                name == "metabol_body" && key == "records" -> {
                    val array = org.json.JSONArray(value)
                    if (array.length() > 100_000) return false
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        if (item.getLong("id") < 0) return false
                        for ((field, limit) in listOf("fat" to 75.0, "muscle" to 300.0, "waist" to 300.0)) {
                            if (!item.isNull(field)) {
                                val measurement = item.getDouble(field)
                                if (!measurement.isFinite() || measurement < 0.0 || measurement > limit) return false
                            }
                        }
                    }
                }
                name == "metabol_nutrition" && key.startsWith("meals_") -> {
                    if (!key.matches(Regex("meals_\\d{4}-\\d{2}-\\d{2}"))) return false
                    val array = org.json.JSONArray(value)
                    if (array.length() > 100_000) return false
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        if (item.getLong("id") < 0 || item.getString("name").length > 300 ||
                            item.getInt("calories") !in 0..10000) return false
                        for (field in listOf("protein", "carbs", "fat")) {
                            val macro = item.getDouble(field)
                            if (!macro.isFinite() || macro !in 0.0..1000.0) return false
                        }
                    }
                }
                name == "metabol_profile" && key == "goal_weight" -> {
                    if ((value.toFloatOrNull() ?: -1f) !in 20f..500f) return false
                }
            }
            entries[key] = value
        }
        entries
    }
    // Commit synchronously so callers can report a write failure.
    prepared.forEach { (name, entries) ->
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        entries.forEach { (key, value) -> editor.putString(key, value) }
        if (!editor.commit()) return false
    }
    return true
}

@Composable
internal fun SettingsScreen(context: Context, modifier: Modifier = Modifier, onGoalChanged: () -> Unit = {}) {
    var message by remember { mutableStateOf("") }
    val profilePrefs = remember(context) { context.getSharedPreferences("metabol_profile", Context.MODE_PRIVATE) }
    var goalInput by remember { mutableStateOf(profilePrefs.getString("goal_weight", "") ?: "") }
    val goalValue = goalInput.replace(',', '.').toFloatOrNull()
    val goalValid = goalValue != null && goalValue in 20f..500f
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            message = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(createBackup(context).toByteArray(Charsets.UTF_8))
                } ?: error("Dosya açılamadı")
                "Yedek dosyası oluşturuldu."
            }.getOrElse { "Dışa aktarma başarısız: ${it.message}" }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            message = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(exportMeasurementsCsv(context).toByteArray(Charsets.UTF_8))
                } ?: error("Dosya açılamadı")
                "CSV dışa aktarıldı."
            }.getOrElse { "CSV dışa aktarılamadı: ${it.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            message = runCatching {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val buffer = CharArray(8192)
                    val output = StringBuilder()
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        if (output.length + count > 5_000_000) error("Dosya çok büyük")
                        output.append(buffer, 0, count)
                    }
                    output.toString()
                } ?: error("Dosya okunamadı")
                val json = JSONObject(content)
                if (json.optString("format") != "METABOL-backup" || json.optInt("version") != 1) {
                    error("Geçersiz METABOL yedeği")
                }
                pendingImport = content
                "Yedek doğrulandı. İçe aktarmayı onayla."
            }.getOrElse { "İçe aktarma başarısız: ${it.message}" }
        }
    }
    LazyColumn(modifier.fillMaxSize().background(SBack), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("M E T A B O L", color = SMint, fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Text("Ayarlar", color = SText, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(5.dp))
            Text("Uygulamanı kendi ihtiyaçlarına göre düzenle.", color = SMuted, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF21473F), Color(0xFF172C37), SCard)),
                    RoundedCornerShape(26.dp)).padding(21.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("VERİLERİNİN KONTROLÜ SENDE", color = SMint, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Text("Önce gizlilik.", color = SText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Kayıtlar cihazında saklanır. Yedeklerini istediğin zaman dışa aktarabilirsin.",
                    color = SMuted, fontSize = 12.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(SCard, RoundedCornerShape(24.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Kişisel hedef", color = SText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Kilo hedefini istediğin zaman değiştirebilirsin. Hedef tamamen sana aittir.",
                    color = SMuted, fontSize = 12.sp)
                OutlinedTextField(
                    value = goalInput, onValueChange = { goalInput = it },
                    label = { Text("Hedef kilo (kg)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Button(onClick = {
                    if (goalValid) {
                        profilePrefs.edit().putString("goal_weight", goalValue.toString()).apply()
                        message = "Hedef kaydedildi."
                        onGoalChanged()
                    }
                }, enabled = goalValid, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SMint, contentColor = SBack)) {
                    Text("Hedefi kaydet")
                }
                TextButton(onClick = {
                    goalInput = ""
                    profilePrefs.edit().remove("goal_weight").apply()
                    message = "Hedef kaldırıldı."
                    onGoalChanged()
                }) { Text("Hedefi kaldır", color = SMuted) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(SCard, RoundedCornerShape(24.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Yedekleme ve geri yükleme", color = SText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Kilo, vücut ölçümleri ve beslenme kayıtlarını JSON olarak dışa aktar. İnternet gerekmez.",
                    color = SMuted, fontSize = 13.sp)
                Button(onClick = {
                    val name = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    exportLauncher.launch("metabol-backup-$name.json")
                }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SMint, contentColor = SBack)) {
                    Text("Yedeği dışa aktar")
                }
                OutlinedButton(onClick = {
                    val name = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    csvLauncher.launch("metabol-measurements-$name.csv")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ölçümleri CSV olarak dışa aktar", color = SMint)
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth()) { Text("Yedekten geri yükle", color = SMint) }
                Text("Geri yükleme mevcut kayıtların yerine geçer. Önce yedek alman önerilir.",
                    color = SMuted, fontSize = 12.sp)
            }
        }
        if (message.isNotBlank()) item {
            Text(message, color = SMint, fontSize = 13.sp)
        }
        item {
            Column(Modifier.fillMaxWidth().background(SCard, RoundedCornerShape(24.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gizlilik", color = SText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Bu sürümde kayıtlar cihazda tutulur. Hesap veya sunucu senkronizasyonu yoktur.",
                    color = SMuted, fontSize = 13.sp)
                Text("METABOL · Developed by Deniz", color = SMint, fontSize = 12.sp)
            }
        }
    }
    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = SCard,
            title = { Text("Mevcut kayıtlar değiştirilecek", color = SText) },
            text = { Text("Yedekteki veriler cihazdaki METABOL kayıtlarının yerini alacak. İşlemden sonra uygulamayı yeniden aç.", color = SMuted) },
            confirmButton = {
                TextButton(onClick = {
                    message = runCatching {
                        if (restoreBackup(context, pendingImport!!)) {
                            (context as? android.app.Activity)?.recreate()
                            "Yedek geri yüklendi."
                        }
                        else "Geçersiz yedek dosyası."
                    }.getOrElse { "Geri yükleme başarısız: ${it.message}" }
                    pendingImport = null
                }) { Text("Geri yükle", color = SMint) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("İptal") } }
        )
    }
}
