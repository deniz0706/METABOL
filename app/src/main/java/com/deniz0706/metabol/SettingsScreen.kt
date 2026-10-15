package com.deniz0706.metabol

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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

private val SBack = Color(0xFF0B1220)
private val SCard = Color(0xFF172337)
private val SMint = Color(0xFF57D6C1)
private val SText = Color(0xFFF3F8FF)
private val SMuted = Color(0xFF9AAFC5)
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
        for ((field, unit) in listOf("fat" to "percent", "muscle" to "kg", "waist" to "cm",
            "bone" to "kg", "water" to "percent", "chest" to "cm",
            "underChest" to "cm", "metabolicAge" to "years", "visceralFat" to "index")) {
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
            // Reject unknown keys rather than importing unchecked future or corrupted data.
            val allowed = when (name) {
                "metabol_local" -> key == "weights"
                "metabol_body" -> key == "records"
                "metabol_profile" -> key in listOf("goal_weight", "calorie_target", "protein_target", "science_age", "science_height", "science_sex", "science_activity")
                "metabol_nutrition" -> key.startsWith("meals_") || key.startsWith("water_") || key.startsWith("note_") ||
                    key == "recent_foods" || key == "saved_foods" || key == "meal_templates" || key == "favorite_recipes"
                else -> false
            }
            if (!allowed) return false
            when {
                name == "metabol_nutrition" && key == "recent_foods" -> {
                    val array = JSONArray(value)
                    if (array.length() > 8) return false
                    for (i in 0 until array.length()) {
                        if (array.getString(i).length !in 1..120) return false
                    }
                }
                name == "metabol_nutrition" && key == "favorite_recipes" -> {
                    val recipes = JSONArray(value)
                    if (recipes.length() > 30) return false
                    for (i in 0 until recipes.length()) {
                        val recipe = recipes.getJSONObject(i)
                        if (recipe.getString("name").length !in 1..120 ||
                            recipe.getInt("servings") !in 1..100) return false
                        val ingredients = recipe.getJSONArray("ingredients")
                        if (ingredients.length() !in 1..50) return false
                        for (j in 0 until ingredients.length()) {
                            val item = ingredients.getJSONObject(j)
                            if (item.getString("name").length !in 1..120) return false
                            val grams = item.getDouble("grams")
                            if (!grams.isFinite() || grams !in 1.0..10000.0) return false
                            for (field in listOf("kcal", "protein", "carbs", "fat")) {
                                val amount = item.getDouble(field)
                                if (!amount.isFinite() || amount !in 0.0..1000.0) return false
                            }
                        }
                    }
                }
                name == "metabol_nutrition" && key == "meal_templates" -> {
                    val array = JSONArray(value)
                    if (array.length() > 30) return false
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        if (item.getString("name").length !in 1..300 ||
                            item.getInt("calories") !in 0..10000) return false
                        for (field in listOf("protein", "carbs", "fat")) {
                            val amount = item.getDouble(field)
                            if (!amount.isFinite() || amount !in 0.0..1000.0) return false
                        }
                    }
                }
                name == "metabol_nutrition" && key == "saved_foods" -> {
                    val array = JSONArray(value)
                    if (array.length() > 30) return false
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        if (item.getString("name").length !in 1..120) return false
                        val kcal = item.getDouble("kcal")
                        if (!kcal.isFinite() || kcal !in 0.0..1000.0) return false
                        for (field in listOf("protein", "carbs", "fat")) {
                            val macro = item.getDouble(field)
                            if (!macro.isFinite() || macro !in 0.0..100.0) return false
                        }
                    }
                }
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
                        for ((field, limit) in listOf("fat" to 75.0, "muscle" to 300.0, "waist" to 300.0,
                            "bone" to 20.0, "water" to 100.0, "chest" to 300.0,
                            "underChest" to 300.0, "metabolicAge" to 120.0, "visceralFat" to 100.0)) {
                            if (!item.isNull(field)) {
                                val measurement = item.getDouble(field)
                                if (!measurement.isFinite() || measurement < 0.0 || measurement > limit) return false
                            }
                        }
                    }
                }
                name == "metabol_nutrition" && key.startsWith("note_") -> {
                    if (!key.matches(Regex("note_\\d{4}-\\d{2}-\\d{2}"))) return false
                    if (value.length > 2000) return false
                }
                name == "metabol_nutrition" && key.startsWith("water_") -> {
                    if (!key.matches(Regex("water_\\d{4}-\\d{2}-\\d{2}"))) return false
                    if ((value.toIntOrNull() ?: -1) !in 0..10000) return false
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
                name == "metabol_profile" && key == "calorie_target" -> {
                    if ((value.toIntOrNull() ?: -1) !in 500..10000) return false
                }
                name == "metabol_profile" && key == "protein_target" -> {
                    val target = value.toDoubleOrNull() ?: return false
                    if (!target.isFinite() || target !in 1.0..500.0) return false
                }
                name == "metabol_profile" && key == "science_age" -> {
                    if ((value.toIntOrNull() ?: -1) !in 18..100) return false
                }
                name == "metabol_profile" && key == "science_height" -> {
                    if ((value.toDoubleOrNull() ?: -1.0) !in 100.0..250.0) return false
                }
                name == "metabol_profile" && key == "science_sex" -> {
                    if (value !in listOf("male", "female")) return false
                }
                name == "metabol_profile" && key == "science_activity" -> {
                    if (value !in listOf("1.2", "1.375", "1.55", "1.725", "1.9")) return false
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
    var scienceExpanded by remember { mutableStateOf(false) }
    val profilePrefs = remember(context) { context.getSharedPreferences("metabol_profile", Context.MODE_PRIVATE) }
    var goalInput by remember { mutableStateOf(profilePrefs.getString("goal_weight", "") ?: "") }
    val goalValue = goalInput.replace(',', '.').toFloatOrNull()
    val goalValid = goalValue != null && goalValue in 20f..500f
    var calorieTargetInput by remember {
        mutableStateOf(profilePrefs.getString("calorie_target", "") ?: "")
    }
    var proteinTargetInput by remember {
        mutableStateOf(profilePrefs.getString("protein_target", "") ?: "")
    }
    val calorieTarget = calorieTargetInput.trim().toIntOrNull()
    val proteinTarget = proteinTargetInput.trim().replace(',', '.').toDoubleOrNull()
    val calorieTargetValid = calorieTargetInput.isBlank() ||
        (calorieTarget != null && calorieTarget in 500..10000)
    val proteinTargetValid = proteinTargetInput.isBlank() ||
        (proteinTarget != null && proteinTarget.isFinite() && proteinTarget in 1.0..500.0)
    var scienceAge by remember { mutableStateOf(profilePrefs.getString("science_age", "") ?: "") }
    var scienceHeight by remember { mutableStateOf(profilePrefs.getString("science_height", "") ?: "") }
    var scienceSex by remember { mutableStateOf(profilePrefs.getString("science_sex", "male") ?: "male") }
    var scienceActivity by remember { mutableStateOf(profilePrefs.getString("science_activity", "1.375") ?: "1.375") }
    val scienceValid = (scienceAge.toIntOrNull() ?: 0) in 18..100 &&
        (scienceHeight.replace(',', '.').toDoubleOrNull() ?: 0.0) in 100.0..250.0
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
    val measurementLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            message = runCatching {
                val source = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    it.readText()
                } ?: error("Dosya okunamadı")
                val result = MeasurementImport.import(context, source)
                (context as? android.app.Activity)?.recreate()
                "Ölçümler aktarıldı: ${result.weightsAdded} kilo, ${result.bodyAdded} vücut analizi. ${result.skipped} tarih atlandı."
            }.getOrElse { "Ölçüm aktarımı başarısız: ${it.message}" }
        }
    }
    val nutritionImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            message = runCatching {
                val source = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    it.readText()
                } ?: error("Dosya okunamadı")
                val (added, skipped) = MeasurementImport.importNutrition(context, source)
                (context as? android.app.Activity)?.recreate()
                "Beslenme geçmişi: $added gün eklendi, $skipped gün atlandı."
            }.getOrElse { "Beslenme aktarımı başarısız: ${it.message}" }
        }
    }
    LazyColumn(modifier.fillMaxSize().background(SBack), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("M E T A B O L", color = SMint, fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Text("Senin alanın", color = SText, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(5.dp))
            Text("Uygulamanı kendi ihtiyaçlarına göre düzenle.", color = SMuted, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF68415C), Color(0xFF352742), SCard)),
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
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().clickable { scienceExpanded = !scienceExpanded },
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Metabolizma profilin", color = SText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (scienceExpanded) "−  Kapat" else "+  Düzenle", color = SMint, fontSize = 12.sp)
                }
                Text("Mifflin–St Jeor denklemi için gerekli bilgiler. Hesaplar tahmindir.", color = SMuted, fontSize = 12.sp)
                AnimatedVisibility(visible = scienceExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(scienceAge, { scienceAge = it }, label = { Text("Yaş") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(scienceHeight, { scienceHeight = it }, label = { Text("Boy (cm)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("Denklemde kullanılan biyolojik cinsiyet", color = SMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("male" to "Erkek", "female" to "Kadın").forEach { (value, label) ->
                        FilterChip(selected = scienceSex == value, onClick = { scienceSex = value }, label = { Text(label) })
                    }
                }
                Text("Aktivite katsayısı", color = SMuted, fontSize = 12.sp)
                listOf("1.2" to "Hareketsiz", "1.375" to "Hafif aktif",
                    "1.55" to "Orta aktif", "1.725" to "Çok aktif", "1.9" to "Aşırı aktif").forEach { (value, label) ->
                    FilterChip(selected = scienceActivity == value,
                        onClick = { scienceActivity = value }, label = { Text(label) })
                }
                Button(onClick = {
                    val success = profilePrefs.edit()
                        .putString("science_age", scienceAge)
                        .putString("science_height", scienceHeight.replace(',', '.'))
                        .putString("science_sex", scienceSex)
                        .putString("science_activity", scienceActivity).commit()
                    message = if (success) "Metabolizma profili kaydedildi." else "Profil kaydedilemedi."
                }, enabled = scienceValid, modifier = Modifier.fillMaxWidth()) {
                    Text("Bilimsel profili kaydet")
                }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(SCard, RoundedCornerShape(24.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("Beslenme hedefleri", color = SText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Kendi belirlediğin hedefleri takip et. METABOL otomatik kalori kısıtlaması önermez.",
                    color = SMuted, fontSize = 12.sp)
                OutlinedTextField(
                    value = calorieTargetInput, onValueChange = { calorieTargetInput = it },
                    label = { Text("Günlük enerji hedefi (kcal) · isteğe bağlı") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = proteinTargetInput, onValueChange = { proteinTargetInput = it },
                    label = { Text("Günlük protein hedefi (g) · isteğe bağlı") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (!calorieTargetValid || !proteinTargetValid) {
                    Text("Kalori hedefi 500–10000, protein hedefi 1–500 arasında olmalı.",
                        color = Color(0xFFFF879A), fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val editor = profilePrefs.edit()
                        if (calorieTarget == null) editor.remove("calorie_target")
                        else editor.putString("calorie_target", calorieTarget.toString())
                        if (proteinTarget == null) editor.remove("protein_target")
                        else editor.putString("protein_target", proteinTarget.toString())
                        if (editor.commit()) message = "Beslenme hedefleri kaydedildi."
                        else message = "Hedefler kaydedilemedi."
                    },
                    enabled = calorieTargetValid && proteinTargetValid,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SMint, contentColor = SBack)
                ) { Text("Beslenme hedeflerini kaydet") }
                Text("Alanları boş bırakıp kaydederek hedefleri kaldırabilirsin.",
                    color = SMuted, fontSize = 11.sp)
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
                OutlinedButton(onClick = {
                    measurementLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Geçmiş ölçümleri JSON'dan ekle", color = SMint)
                }
                OutlinedButton(onClick = {
                    nutritionImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Tahmini beslenme geçmişini JSON'dan ekle", color = SMint)
                }
                Text("Tahmini günlük toplamlar ayrı bir kayıt olarak eklenir. Mevcut beslenme günleri değiştirilmez; bu veriler gerçek öğün kaydı değildir.",
                    color = SMuted, fontSize = 12.sp)
                Text("Bu seçenek mevcut ölçümleri silmez; aynı tarihli kayıtları atlar. Kesin tarihi olmayan veriler yalnızca kaynak dosyada korunur. Aktarmadan önce yedek al.",
                    color = SMuted, fontSize = 12.sp)
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
