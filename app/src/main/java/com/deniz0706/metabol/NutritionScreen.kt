package com.deniz0706.metabol

import android.content.Context
import android.app.DatePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import java.util.Calendar
import java.util.Locale
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val NutritionBg = Color(0xFF0B1220)
private val NutritionPanel = Color(0xFF172337)
private val NutritionMint = Color(0xFF57D6C1)
private val NutritionMuted = Color(0xFF9AAFC5)
private val NutritionWhite = Color(0xFFF3F8FF)

private data class MealEntry(
    val id: Long,
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

@Composable
private fun FoodSearchRow(food: FoodReference, online: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (online) Color(0xFF1C3540) else NutritionPanel, RoundedCornerShape(18.dp))
            .border(1.dp, if (online) Color(0xFF305A5C) else Color(0xFF263746),
                RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(food.name, color = NutritionWhite, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp)
            Text("P ${food.protein100} · K ${food.carbs100} · Y ${food.fat100} g / 100 g",
                color = NutritionMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${food.kcal100.toInt()}", color = NutritionMint,
                fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text("kcal / 100 g", color = NutritionMuted, fontSize = 10.sp)
        }
    }
}

@Composable
internal fun NutritionScreen(context: Context, modifier: Modifier = Modifier) {
    val prefs = remember(context) { context.getSharedPreferences("metabol_nutrition", Context.MODE_PRIVATE) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var selectedDay by remember { mutableStateOf(today) }
    val noteKey = "note_$selectedDay"
    var dailyNote by remember(noteKey) {
        mutableStateOf(prefs.getString(noteKey, "") ?: "")
    }
    var noteSaved by remember(noteKey) { mutableStateOf(false) }
    val storageKey = "meals_$selectedDay"
    val waterKey = "water_$selectedDay"
    var waterMl by remember(waterKey) {
        mutableIntStateOf(prefs.getString(waterKey, "0")?.toIntOrNull()?.coerceIn(0, 10000) ?: 0)
    }
    var customWaterMl by remember { mutableStateOf("") }
    fun updateWater(delta: Int) {
        val next = (waterMl + delta).coerceIn(0, 10000)
        waterMl = next
        prefs.edit().putString(waterKey, next.toString()).apply()
    }

    fun shiftDay(days: Int) {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val current = format.parse(selectedDay) ?: Date()
        val calendar = Calendar.getInstance().apply { time = current; add(Calendar.DATE, days) }
        val candidate = format.format(calendar.time)
        if (candidate <= today) selectedDay = candidate
    }
    var meals by remember(storageKey) {
        mutableStateOf(runCatching {
            val array = JSONArray(prefs.getString(storageKey, "[]"))
            (0 until array.length()).map {
                val item = array.getJSONObject(it)
                MealEntry(
                    item.getLong("id"), item.getString("name"), item.getInt("calories"),
                    item.getDouble("protein"), item.getDouble("carbs"), item.getDouble("fat")
                )
            }
        }.getOrDefault(emptyList()))
    }
    var dialogOpen by remember { mutableStateOf(false) }
    var editingMeal by remember { mutableStateOf<MealEntry?>(null) }
    var catalogOpen by remember { mutableStateOf(false) }
    var compareOpen by remember { mutableStateOf(false) }
    var recipeOpen by remember { mutableStateOf(false) }
    var onlineResults by remember { mutableStateOf<List<FoodReference>>(emptyList()) }
    var onlineLoading by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    var onlineSearched by remember { mutableStateOf(false) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    // Invalidate pending responses when the explorer closes, including dismiss/back.
    LaunchedEffect(catalogOpen) {
        if (!catalogOpen) {
            searchGeneration++
            onlineLoading = false
        }
    }
    var selectedFood by remember { mutableStateOf<FoodReference?>(null) }
    fun initialGrams(food: FoodReference): String =
        Regex("""Paket: (\d+) g""").find(food.note)?.groupValues?.get(1) ?: "100"
    var selectedGrams by remember { mutableStateOf("100") }
    val searchExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(searchExecutor) {
        onDispose {
            searchGeneration++
            searchExecutor.shutdownNow()
        }
    }
    fun searchOnline(query: String) {
        if (query.trim().length < 2) return
        onlineLoading = true
        onlineError = null
        onlineResults = emptyList()
        onlineSearched = true
        val generation = ++searchGeneration
        searchExecutor.execute {
            val result = runCatching {
                if (query.trim().matches(Regex("[0-9]{8,14}"))) OpenFoodFacts.lookupBarcode(query.trim())
                else OpenFoodFacts.search(query)
            }
            Handler(Looper.getMainLooper()).post {
                if (generation == searchGeneration && catalogOpen) {
                    onlineLoading = false
                    result.onSuccess { foods ->
                        onlineResults = foods
                        if (query.trim().matches(Regex("[0-9]{8,14}")) && foods.size == 1) {
                            selectedFood = foods.first()
                            selectedGrams = initialGrams(foods.first())
                            catalogOpen = false
                        }
                    }
                        .onFailure { onlineError = "Bağlantı kurulamadı. Tekrar dene veya çevrimdışı kataloğu kullan." }
                }
            }
        }
    }
    var savedFoods by remember {
        mutableStateOf(runCatching {
            val array = JSONArray(prefs.getString("saved_foods", "[]"))
            (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val name = item.getString("name")
                val kcal = item.getDouble("kcal")
                val protein = item.getDouble("protein")
                val carbs = item.getDouble("carbs")
                val fat = item.getDouble("fat")
                if (name.isBlank() || !listOf(kcal, protein, carbs, fat).all { it.isFinite() } ||
                    kcal !in 0.0..1000.0 || listOf(protein, carbs, fat).any { it !in 0.0..100.0 }) null
                else FoodReference(name, kcal, protein, carbs, fat, item.optString("note").ifBlank { "Önceden kaydettiğin ürün · Etiketten doğrula" })
            }
        }.getOrDefault(emptyList()))
    }
    fun saveFoodReference(food: FoodReference) {
        val updated = (listOf(food) + savedFoods.filterNot { it.name == food.name }).take(30)
        savedFoods = updated
        val array = JSONArray()
        updated.forEach { item ->
            array.put(JSONObject().put("name", item.name).put("kcal", item.kcal100)
                .put("protein", item.protein100).put("carbs", item.carbs100).put("fat", item.fat100)
                .put("note", item.note))
        }
        prefs.edit().putString("saved_foods", array.toString()).apply()
    }
    var recentFoodNames by remember {
        mutableStateOf(runCatching {
            val saved = JSONArray(prefs.getString("recent_foods", "[]"))
            (0 until saved.length()).map { saved.getString(it) }
        }.getOrDefault(emptyList()))
    }
    fun rememberFood(name: String) {
        val updated = (listOf(name) + recentFoodNames.filterNot { it == name }).take(8)
        recentFoodNames = updated
        val array = JSONArray()
        updated.forEach { array.put(it) }
        prefs.edit().putString("recent_foods", array.toString()).apply()
    }
    var templates by remember {
        mutableStateOf(runCatching {
            val array = JSONArray(prefs.getString("meal_templates", "[]"))
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                MealEntry(index.toLong(), item.getString("name"), item.getInt("calories"),
                    item.getDouble("protein"), item.getDouble("carbs"), item.getDouble("fat"))
            }
        }.getOrDefault(emptyList()))
    }
    var templatePickerOpen by remember { mutableStateOf(false) }
    fun storeTemplates(updated: List<MealEntry>) {
        templates = updated.take(30)
        val array = JSONArray()
        templates.forEach { meal ->
            array.put(JSONObject().put("name", meal.name).put("calories", meal.calories)
                .put("protein", meal.protein).put("carbs", meal.carbs).put("fat", meal.fat))
        }
        prefs.edit().putString("meal_templates", array.toString()).apply()
    }
    var pendingDeleteMeal by remember { mutableStateOf<MealEntry?>(null) }
    var confirmCopyPrevious by remember { mutableStateOf(false) }
    var historyRevision by remember { mutableIntStateOf(0) }
    val previousDay = remember(selectedDay) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance().apply {
            time = fmt.parse(selectedDay) ?: Date()
            add(Calendar.DATE, -1)
        }
        fmt.format(calendar.time)
    }
    val previousMeals = remember(previousDay, historyRevision) {
        runCatching {
            val array = JSONArray(prefs.getString("meals_$previousDay", "[]"))
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                MealEntry(item.getLong("id"), item.getString("name"),
                    item.getInt("calories"), item.getDouble("protein"),
                    item.getDouble("carbs"), item.getDouble("fat"))
            }
        }.getOrDefault(emptyList())
    }
    val lastSevenDays = remember(historyRevision, selectedDay) {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = format.parse(selectedDay) ?: Date()
        (6 downTo 0).map { offset ->
            val cal = Calendar.getInstance().apply { time = date; add(Calendar.DATE, -offset) }
            val dayKey = format.format(cal.time)
            val array = runCatching { JSONArray(prefs.getString("meals_$dayKey", "[]")) }.getOrDefault(JSONArray())
            val total = (0 until array.length()).sumOf { index ->
                runCatching { array.getJSONObject(index).getInt("calories") }.getOrDefault(0)
            }
            dayKey to total
        }
    }
    fun save(updated: List<MealEntry>) {
        meals = updated
        val json = JSONArray()
        updated.forEach { item ->
            json.put(JSONObject().put("id", item.id).put("name", item.name)
                .put("calories", item.calories).put("protein", item.protein)
                .put("carbs", item.carbs).put("fat", item.fat))
        }
        prefs.edit().putString(storageKey, json.toString()).apply()
        historyRevision++
    }
    var insightsWindow by remember { mutableIntStateOf(30) }
    val insights by produceState(initialValue = NutritionInsights.summarize(emptyList(), insightsWindow, LocalDate.parse(selectedDay)),
        key1 = selectedDay, key2 = historyRevision, key3 = insightsWindow) {
        value = withContext(Dispatchers.Default) {
        val anchor = runCatching { LocalDate.parse(selectedDay) }.getOrDefault(LocalDate.now())
        val recorded = (0 until insightsWindow).mapNotNull { offset ->
            val date = anchor.minusDays(offset.toLong())
            val key = "meals_$date"
            if (!prefs.contains(key)) return@mapNotNull null
            val array = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrNull()
                ?: return@mapNotNull null
            var kcal = 0
            var proteinSum = 0.0
            var carbSum = 0.0
            var fatSum = 0.0
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                kcal += item.optInt("calories", 0).coerceAtLeast(0)
                proteinSum += item.optDouble("protein", 0.0).coerceAtLeast(0.0)
                carbSum += item.optDouble("carbs", 0.0).coerceAtLeast(0.0)
                fatSum += item.optDouble("fat", 0.0).coerceAtLeast(0.0)
            }
            NutritionDay(date, kcal, proteinSum, carbSum, fatSum, array.length())
        }
        NutritionInsights.summarize(recorded, insightsWindow, anchor)
        }
    }
    val profilePrefs = remember(context) { context.getSharedPreferences("metabol_profile", Context.MODE_PRIVATE) }
    val calorieTarget = profilePrefs.getString("calorie_target", null)?.toIntOrNull()
        ?.takeIf { it in 500..10000 }
    val proteinTarget = profilePrefs.getString("protein_target", null)?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it in 1.0..500.0 }
    val recordedSevenDays = remember(lastSevenDays, historyRevision) {
        lastSevenDays.map { (day, _) -> day to prefs.contains("meals_$day") }.toMap()
    }
    // Water and dialog state changes should not recalculate the entire meal list.
    val totals = remember(meals) {
        doubleArrayOf(
            meals.sumOf { it.calories }.toDouble(),
            meals.sumOf { it.protein },
            meals.sumOf { it.carbs },
            meals.sumOf { it.fat }
        )
    }
    val calories = totals[0].toInt()
    val protein = totals[1]
    val carbs = totals[2]
    val fat = totals[3]
    val macroCalories = protein * 4.0 + carbs * 4.0 + fat * 9.0
    val animatedCalories by animateIntAsState(calories, tween(700), label = "calorie-count")
    val animatedWater by animateIntAsState(waterMl, tween(700), label = "water-count")
    val animatedWaterProgress by animateFloatAsState(
        (waterMl / 2000f).coerceIn(0f, 1f), tween(650), label = "water-fill"
    )
    val animatedEnergyProgress by animateFloatAsState(
        calorieTarget?.let { (calories.toFloat() / it).coerceIn(0f, 1f) } ?: 0f,
        tween(650), label = "energy-fill"
    )
    LazyColumn(modifier.fillMaxSize().background(NutritionBg), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("M E T A B O L", color = NutritionMint,
                        fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 3.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("STUDIO / BESLENME GÜNLÜĞÜ", color = NutritionMuted,
                        fontSize = 10.sp, letterSpacing = 1.2.sp)
                }
                Box(Modifier.size(46.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF66415C), Color(0xFF35273E))),
                        CircleShape), contentAlignment = Alignment.Center) {
                    Text("N", color = NutritionMint, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Bugün ne yedin?", color = NutritionWhite, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text("Öğünlerini kolayca ekle, gününü tek bakışta gör.", color = NutritionMuted, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = {
                    val parsed = runCatching { LocalDate.parse(selectedDay) }
                        .getOrDefault(LocalDate.now())
                    DatePickerDialog(context, { _, year, month, day ->
                        selectedDay = LocalDate.of(year, month + 1, day).toString()
                    }, parsed.year, parsed.monthValue - 1, parsed.dayOfMonth).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("▦  Takvimden tarih seç", color = NutritionMint) }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().background(NutritionPanel, RoundedCornerShape(18.dp))
                .padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { shiftDay(-1) },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF213345), contentColor = NutritionMint)) {
                    Text("‹", fontSize = 25.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (selectedDay == today) "BUGÜN" else "SEÇİLEN GÜN",
                        color = NutritionMint, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Text(selectedDay, color = NutritionWhite,
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                FilledTonalIconButton(onClick = { shiftDay(1) }, enabled = selectedDay < today,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF213345), contentColor = NutritionMint)) {
                    Text("›", fontSize = 25.sp)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF24485D), Color(0xFF152D42), NutritionPanel)), RoundedCornerShape(28.dp)).border(1.dp, Color(0xFF34586D), RoundedCornerShape(28.dp)).padding(23.dp)) {
                Text("TOPLAM ENERJİ", color = NutritionMuted, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$animatedCalories", color = NutritionWhite, fontSize = 49.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Text("kcal", color = NutritionMint, fontSize = 17.sp,
                        modifier = Modifier.padding(bottom = 10.dp))
                }
                Text("${meals.size} öğün kaydedildi", color = NutritionMuted, fontSize = 12.sp)
                if (calorieTarget != null) {
                    Spacer(Modifier.height(17.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("GÜNLÜK ENERJİ HEDEFİ", color = NutritionMuted, fontSize = 10.sp)
                        Text("$calories / $calorieTarget kcal", color = NutritionMint,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { animatedEnergyProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = NutritionMint, trackColor = Color(0xFF30445C)
                    )
                    if (calories > calorieTarget) {
                        Text("Hedefin ${calories - calorieTarget} kcal üzerinde kayıt var.",
                            color = NutritionMuted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF30445C))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top) {
                    MacroValue("Protein", protein, NutritionMint, macroCalories, 4.0)
                    MacroValue("Karbonhidrat", carbs, Color(0xFF80A9FF), macroCalories, 4.0)
                    MacroValue("Yağ", fat, Color(0xFFFFC483), macroCalories, 9.0)
                }
                if (proteinTarget != null) {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("PROTEİN HEDEFİ", color = NutritionMuted, fontSize = 10.sp)
                        Text("${String.format(Locale.US, "%.1f", protein)} / ${String.format(Locale.US, "%.1f", proteinTarget)} g",
                            color = NutritionMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (protein / proteinTarget).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = NutritionMint, trackColor = Color(0xFF30445C)
                    )
                }
                if (meals.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF30445C))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Makrolardan hesaplanan enerji: " + String.format(Locale.US, "%.0f kcal", macroCalories),
                        color = NutritionMuted, fontSize = 12.sp
                    )
                    Text("Etiket kalorisi ile fark; lif, yuvarlama ve eksik makro verilerinden kaynaklanabilir.",
                        color = NutritionMuted, fontSize = 11.sp)
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF24465C), Color(0xFF172A40))),
                        RoundedCornerShape(26.dp)
                    )
                    .border(1.dp, Color(0xFF38527C), RoundedCornerShape(26.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GÜNLÜK SU", color = Color(0xFF8CDCF9),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(String.format(Locale.US, "%.2f", animatedWater / 1000.0),
                                color = NutritionWhite, fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.width(7.dp))
                            Text("litre", color = NutritionMuted, fontSize = 15.sp,
                                modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    Box(
                        Modifier.size(58.dp)
                            .background(Color(0xFF214E64), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💧", fontSize = 27.sp)
                    }
                }
                LinearProgressIndicator(
                    progress = { animatedWaterProgress },
                    modifier = Modifier.fillMaxWidth().height(9.dp),
                    color = Color(0xFF57D6C1),
                    trackColor = Color(0xFF29445A)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$animatedWater ml kaydedildi", color = NutritionWhite, fontSize = 12.sp)
                    Text("2 L görsel referans", color = NutritionMuted, fontSize = 11.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(150, 250, 500).forEach { amount ->
                        Button(
                            onClick = { updateWater(amount) },
                            enabled = waterMl + amount <= 10000,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF29546B),
                                contentColor = NutritionWhite
                            )
                        ) { Text("+$amount", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customWaterMl,
                        onValueChange = { customWaterMl = it.filter(Char::isDigit).take(5) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Özel miktar (ml)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    val amount = customWaterMl.toIntOrNull()
                    Button(
                        onClick = {
                            if (amount != null && amount in 1..10000 && waterMl + amount <= 10000) {
                                updateWater(amount)
                                customWaterMl = ""
                            }
                        },
                        enabled = amount != null && amount in 1..10000 && waterMl + amount <= 10000,
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Ekle") }
                }
                OutlinedButton(
                    onClick = { updateWater(-250) },
                    enabled = waterMl >= 250,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Son 250 ml'yi geri al", color = Color(0xFFB6DDEB))
                }
                Text("2 litre yalnızca ilerleme çubuğunun referansıdır; kişisel su ihtiyacı veya zorunlu hedef değildir.",
                    color = NutritionMuted, fontSize = 11.sp)
            }
        }
        item {
            Button(onClick = { catalogOpen = true }, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NutritionMint, contentColor = NutritionBg)) {
                Text("⌕   Besin ara ve gramajla ekle", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { editingMeal = null; dialogOpen = true },
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(17.dp)) {
                Text("+   Manuel öğün ekle", color = NutritionMint, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(NutritionPanel, RoundedCornerShape(24.dp))
                .padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("GÜNLÜK NOT", color = NutritionMint, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                OutlinedTextField(
                    value = dailyNote,
                    onValueChange = { dailyNote = it.take(2000); noteSaved = false },
                    label = { Text("Açlık, tokluk, egzersiz veya gününle ilgili not") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
                )
                Button(onClick = {
                    if (dailyNote.isBlank()) prefs.edit().remove(noteKey).apply()
                    else prefs.edit().putString(noteKey, dailyNote.trim()).apply()
                    noteSaved = true
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (noteSaved) "Kaydedildi" else "Notu kaydet")
                }
            }
        }
        if (previousMeals.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().background(NutritionPanel, RoundedCornerShape(18.dp))
                    .padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dünkü öğünleri tekrar kullan", color = NutritionWhite,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("$previousDay · ${previousMeals.size} kayıt", color = NutritionMuted, fontSize = 11.sp)
                    }
                    TextButton(onClick = { confirmCopyPrevious = true }) {
                        Text("Kopyala", color = NutritionMint)
                    }
                }
            }
        }
        item {
            Text("Yerel katalog yaklaşık değerler içerir. Çevrimiçi ürün bilgilerini etiketle doğrula.",
                color = NutritionMuted, fontSize = 11.sp)
        }
        item {
            Column(Modifier.fillMaxWidth().background(NutritionPanel, RoundedCornerShape(22.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SON 7 GÜN · KAYDEDİLEN KALORİ", color = NutritionMuted, fontSize = 11.sp, letterSpacing = 1.2.sp)
                val maxCalories = (lastSevenDays.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                lastSevenDays.forEach { (day, total) ->
                    val hasEntry = recordedSevenDays[day] == true
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(day.takeLast(5), color = NutritionMuted, fontSize = 11.sp,
                            modifier = Modifier.width(45.dp))
                        if (hasEntry) {
                            LinearProgressIndicator(
                                progress = { total.toFloat() / maxCalories },
                                modifier = Modifier.weight(1f).height(8.dp),
                                color = NutritionMint,
                                trackColor = Color(0xFF30445C)
                            )
                        } else {
                            Box(Modifier.weight(1f).height(8.dp)
                                .background(Color(0xFF202C39), RoundedCornerShape(8.dp)))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(if (hasEntry) "$total" else "—",
                            color = if (hasEntry) NutritionWhite else NutritionMuted,
                            fontSize = 12.sp, modifier = Modifier.width(45.dp))
                    }
                }
                val recordedDays = lastSevenDays.count { (day, _) -> recordedSevenDays[day] == true }
                val sevenDayTotal = lastSevenDays.sumOf { it.second }
                HorizontalDivider(color = Color(0xFF30445C))
                Text("7 günlük kayıt toplamı: $sevenDayTotal kcal", color = NutritionWhite, fontSize = 13.sp)
                Text("Kayıt bulunan gün: $recordedDays / 7", color = NutritionMuted, fontSize = 12.sp)
                Text(
                    if (recordedDays > 0)
                        "Kayıtlı gün ortalaması: ${String.format(Locale("tr", "TR"), "%.0f", sevenDayTotal.toDouble() / recordedDays)} kcal"
                    else "Kayıtlı gün ortalaması: —",
                    color = NutritionMint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Text("Boş günler sıfır tüketim anlamına gelmez. Grafik kaydedilen enerjiyi gösterir.",
                    color = NutritionMuted, fontSize = 11.sp)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF1C3340), NutritionPanel)),
                        RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFF2B4650), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("KAYIT ANALİZİ", color = NutritionMint, fontSize = 11.sp, letterSpacing = 1.6.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(7, 30, 90).forEach { window ->
                            FilterChip(
                                selected = insightsWindow == window,
                                onClick = { insightsWindow = window },
                                label = { Text("$window G", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NutritionMint,
                                    selectedLabelColor = NutritionBg)
                            )
                        }
                    }
                }
                Text("${insights.recordedDays} / ${insights.windowDays} gün kayıtlı",
                    color = NutritionWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                LinearProgressIndicator(
                    progress = { insights.recordedDays.toFloat() / insights.windowDays },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = NutritionMint, trackColor = Color(0xFF30445C)
                )
                HorizontalDivider(color = Color(0xFF30445C))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("KAYITLI GÜN ORT.", color = NutritionMuted, fontSize = 10.sp)
                        Text(insights.averageRecordedCalories?.let { "$it kcal" } ?: "—",
                            color = NutritionWhite, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("PROTEİN ORT.", color = NutritionMuted, fontSize = 10.sp)
                        Text(insights.averageRecordedProtein?.let {
                            String.format(Locale.US, "%.1f g", it)
                        } ?: "—", color = NutritionMint, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Toplam kaydedilen öğün", color = NutritionMuted, fontSize = 12.sp)
                    Text("${insights.totalMeals}", color = NutritionWhite, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("En uzun kayıt serisi", color = NutritionMuted, fontSize = 12.sp)
                    Text("${insights.longestRecordingStreak} gün", color = NutritionWhite,
                        fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Seçili güne kadar kayıt serisi", color = NutritionMuted, fontSize = 12.sp)
                    Text("${insights.currentRecordingStreak} gün", color = NutritionMint,
                        fontWeight = FontWeight.Bold)
                }
                Text("Ortalama yalnızca kayıt bulunan günlerden hesaplanır. Kayıt olmayan günler sıfır tüketim sayılmaz.",
                    color = NutritionMuted, fontSize = 11.sp)
                OutlinedButton(
                    onClick = {
                        val report = buildString {
                            appendLine("METABOL · Beslenme kayıt özeti")
                            appendLine("Bitiş: $selectedDay · Son ${insights.windowDays} gün")
                            appendLine("Kayıtlı gün: ${insights.recordedDays}/${insights.windowDays}")
                            appendLine("Kaydedilen öğün: ${insights.totalMeals}")
                            appendLine("Kayıtlı gün ortalaması: ${insights.averageRecordedCalories?.toString() ?: "—"} kcal")
                            appendLine("Ortalama protein: " +
                                (insights.averageRecordedProtein?.let {
                                    String.format(Locale.US, "%.1f g", it)
                                } ?: "—"))
                            appendLine("Kayıt olmayan günler hesaplamaya dahil edilmedi.")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(intent, "Beslenme özetini paylaş"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) { Text("↗  Bu özeti paylaş", color = NutritionMint) }
            }
        }
        item {
            OutlinedButton(onClick = { templatePickerOpen = true },
                enabled = templates.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Öğün şablonundan ekle (${templates.size})", color = NutritionMint)
            }
        }
        item {
            OutlinedButton(onClick = { compareOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Besin karşılaştır · 100 gram üzerinden", color = NutritionMint)
            }
        }
        item {
            OutlinedButton(onClick = { recipeOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Tarif oluştur ve favori tarifleri kullan", color = NutritionMint)
            }
        }
        item { Text("SEÇİLİ GÜNÜN KAYITLARI", color = NutritionMuted, fontSize = 11.sp, letterSpacing = 2.sp) }
        if (meals.isEmpty()) {
            item {
                Text("Henüz öğün eklemedin. Değerleri besin etiketinden girerek başlayabilirsin.",
                    color = NutritionMuted, modifier = Modifier.padding(vertical = 20.dp))
            }
        } else {
            items(meals, key = { it.id }) { meal ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(NutritionPanel, RoundedCornerShape(18.dp))
                        .border(1.dp, Color(0xFF30445C), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top) {
                        Text(meal.name, color = NutritionWhite,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Text("${meal.calories} kcal", color = NutritionMint,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("P ${meal.protein.toInt()} g", color = NutritionMint, fontSize = 11.sp)
                        Text("K ${meal.carbs.toInt()} g", color = Color(0xFF80A9FF), fontSize = 11.sp)
                        Text("Y ${meal.fat.toInt()} g", color = Color(0xFFFFC483), fontSize = 11.sp)
                    }
                    val proteinEnergy = (meal.protein * 4).coerceAtLeast(0.0)
                    val carbEnergy = (meal.carbs * 4).coerceAtLeast(0.0)
                    val fatEnergy = (meal.fat * 9).coerceAtLeast(0.0)
                    val macroEnergy = proteinEnergy + carbEnergy + fatEnergy
                    if (macroEnergy > 0.0) {
                        Row(Modifier.fillMaxWidth().height(5.dp)
                            .background(Color(0xFF263544), RoundedCornerShape(4.dp))) {
                            if (proteinEnergy > 0.0) Box(Modifier.fillMaxHeight()
                                .weight(proteinEnergy.toFloat()).background(NutritionMint))
                            if (carbEnergy > 0.0) Box(Modifier.fillMaxHeight()
                                .weight(carbEnergy.toFloat()).background(Color(0xFF80A9FF)))
                            if (fatEnergy > 0.0) Box(Modifier.fillMaxHeight()
                                .weight(fatEnergy.toFloat()).background(Color(0xFFFFC483)))
                        }
                    }
                    HorizontalDivider(color = Color(0xFF30445C))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            if (templates.none { it.name == meal.name &&
                                    it.calories == meal.calories &&
                                    it.protein == meal.protein &&
                                    it.carbs == meal.carbs && it.fat == meal.fat }) {
                                storeTemplates(templates + meal)
                            }
                        }, enabled = templates.size < 30) {
                            Text("Şablon", color = NutritionMint, fontSize = 12.sp)
                        }
                        TextButton(onClick = { editingMeal = meal; dialogOpen = true }) {
                            Text("Düzenle", color = NutritionMint, fontSize = 12.sp)
                        }
                        TextButton(onClick = { pendingDeleteMeal = meal }) {
                            Text("Sil", color = Color(0xFFFF879A), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Text("Besin değerleri kullanıcı girişidir; otomatik doğrulanmaz.", color = NutritionMuted, fontSize = 11.sp)
        }
    }

    if (recipeOpen) {
        RecipeDialog(context, onClose = { recipeOpen = false }, onAdd = { name, portion ->
            val id = generateSequence(System.currentTimeMillis()) { it + 1 }
                .first { candidate -> meals.none { it.id == candidate } }
            save(meals + MealEntry(id, name + " · 1 porsiyon", portion.calories,
                portion.protein, portion.carbs, portion.fat))
        })
    }

    if (compareOpen) {
        var queryA by remember { mutableStateOf("") }
        var queryB by remember { mutableStateOf("") }
        val first = remember(queryA, savedFoods) {
            (savedFoods + FoodCatalog.search(queryA)).firstOrNull {
                it.name.equals(queryA.trim(), ignoreCase = true)
            }
        }
        val second = remember(queryB, savedFoods) {
            (savedFoods + FoodCatalog.search(queryB)).firstOrNull {
                it.name.equals(queryB.trim(), ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { compareOpen = false },
            title = { Text("Besin karşılaştırma") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = queryA, onValueChange = { queryA = it.take(120) },
                        label = { Text("Birinci besin · tam ad") }, singleLine = true)
                    OutlinedTextField(value = queryB, onValueChange = { queryB = it.take(120) },
                        label = { Text("İkinci besin · tam ad") }, singleLine = true)
                    Text("Katalogda veya kayıtlı ürünlerinde bulunan tam besin adını yaz.",
                        fontSize = 11.sp)
                    if (first != null && second != null) {
                        Text("100 gram için", fontWeight = FontWeight.Bold)
                        Text("Enerji: ${first.kcal100.toInt()} / ${second.kcal100.toInt()} kcal")
                        Text("Protein: ${first.protein100} / ${second.protein100} g")
                        Text("Karbonhidrat: ${first.carbs100} / ${second.carbs100} g")
                        Text("Yağ: ${first.fat100} / ${second.fat100} g")
                    } else Text("Karşılaştırma için iki besini de eşleştir.", fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { compareOpen = false }) { Text("Kapat") }
            }
        )
    }

    if (catalogOpen) {
        var query by remember { mutableStateOf("") }
    val barcodeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val code = result.data?.getStringExtra(BarcodeScannerActivity.EXTRA_BARCODE)
            if (code != null && code.matches(Regex("[0-9]{8,14}"))) {
                query = code
                searchOnline(code)
            } else onlineError = "Barkod numarası alınamadı."
        }
    }
        var category by remember { mutableStateOf("Tümü") }
        val savedResults = remember(query, savedFoods) {
            val needle = query.trim()
            savedFoods.filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
        }
        val localResults = remember(query, category, recentFoodNames) {
            FoodCatalog.search(query).filter { category == "Tümü" || FoodCatalog.categoryOf(it) == category }.sortedWith(
                compareBy<FoodReference> {
                    if (query.isBlank()) {
                        val index = recentFoodNames.indexOf(it.name)
                        if (index >= 0) index else if (FoodCatalog.categoryOf(it) == "Yemekler") 100 else 200
                    } else 0
                }.thenBy { it.name }
            )
        }
        Dialog(
            onDismissRequest = { catalogOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier.fillMaxSize().background(NutritionBg)
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("M E T A B O L  /  NUTRITION", color = NutritionMint,
                            fontSize = 11.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Besin keşfet", color = NutritionWhite, fontSize = 31.sp,
                            fontWeight = FontWeight.ExtraBold)
                        Text("Ara, miktarı seç, günlüğüne ekle.", color = NutritionMuted, fontSize = 12.sp)
                    }
                    FilledTonalIconButton(onClick = { catalogOpen = false },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = NutritionPanel, contentColor = NutritionWhite)) {
                        Text("✕", fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onlineSearched = false
                        onlineResults = emptyList()
                        onlineError = null
                        searchGeneration++
                        onlineLoading = false
                    },
                    label = { Text("Besin adı veya barkod numarası") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp)
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(FoodCatalog.categories) { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(option, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NutritionMint,
                                selectedLabelColor = NutritionBg
                            )
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        onlineError = null
                        barcodeLauncher.launch(Intent(context, BarcodeScannerActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("▣  Kamerayla barkod tara", color = NutritionMint,
                        fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { searchOnline(query) },
                    enabled = query.trim().length >= 2 && !onlineLoading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NutritionMint, contentColor = NutritionBg)
                ) {
                    Text(
                        if (onlineLoading) "Aranıyor..."
                        else if (query.trim().matches(Regex("[0-9]{8,14}"))) "Barkod numarasıyla sorgula"
                        else "Open Food Facts'te ara",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("İnternet araması isteğe bağlıdır; yalnızca arama metni üçüncü taraf servise gönderilir.",
                    color = NutritionMuted, fontSize = 10.sp)
                if (onlineLoading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = NutritionMint)
                }
                onlineError?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Color(0xFFFF879A), fontSize = 12.sp)
                }
                Spacer(Modifier.height(18.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (onlineResults.isNotEmpty()) {
                        item {
                            Text("ÇEVRİMİÇİ SONUÇLAR · ${onlineResults.size}",
                                color = NutritionMint, fontSize = 11.sp, letterSpacing = 1.4.sp)
                            Spacer(Modifier.height(5.dp))
                        }
                        items(onlineResults) { food ->
                            FoodSearchRow(food, true) {
                                selectedFood = food
                                selectedGrams = initialGrams(food)
                                catalogOpen = false
                            }
                        }
                    } else if (onlineSearched && !onlineLoading && onlineError == null) {
                        item {
                            Text("Çevrimiçi sonuç bulunamadı. Yerel katalog aşağıda.",
                                color = NutritionMuted, fontSize = 12.sp)
                        }
                    }
                    if (savedResults.isNotEmpty()) {
                        item {
                            Text("KAYDETTİĞİN ÜRÜNLER · ${savedResults.size}",
                                color = NutritionMint, fontSize = 11.sp, letterSpacing = 1.4.sp)
                        }
                        items(savedResults) { food ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    FoodSearchRow(food, true) {
                                        selectedFood = food
                                        selectedGrams = initialGrams(food)
                                        catalogOpen = false
                                    }
                                }
                                TextButton(onClick = {
                                    val updated = savedFoods.filterNot { it.name == food.name }
                                    savedFoods = updated
                                    val array = JSONArray()
                                    updated.forEach { item ->
                                        array.put(JSONObject().put("name", item.name)
                                            .put("kcal", item.kcal100).put("protein", item.protein100)
                                            .put("carbs", item.carbs100).put("fat", item.fat100))
                                    }
                                    prefs.edit().putString("saved_foods", array.toString()).apply()
                                }) { Text("Sil", color = Color(0xFFFF879A), fontSize = 11.sp) }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("ÇEVRİMDIŞI KATALOG · ${localResults.size} / ${FoodCatalog.foods.size} BESİN",
                            color = NutritionMuted, fontSize = 11.sp, letterSpacing = 1.4.sp)
                        if (query.isBlank() && recentFoodNames.isNotEmpty()) {
                            Text("Son kullanılan besinler üstte", color = NutritionMint, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    if (localResults.isEmpty()) {
                        item {
                            Text("Yerel katalogda eşleşme yok. Manuel öğün ekleyebilirsin.",
                                color = NutritionMuted, fontSize = 12.sp)
                        }
                    }
                    items(localResults, key = { it.name }) { food ->
                        FoodSearchRow(food, false) {
                            selectedFood = food
                            selectedGrams = "100"
                            catalogOpen = false
                        }
                    }
                }
            }
        }
    }
    selectedFood?.let { food ->
        val serving = remember(food) { FoodCatalog.serving(food) }
        val grams = selectedGrams.replace(',', '.').toDoubleOrNull()
        val validGrams = grams != null && grams.isFinite() && grams in 1.0..5000.0
        val portion = if (validGrams) FoodCatalog.calculate(food, grams!!) else null
        AlertDialog(
            onDismissRequest = { selectedFood = null },
            containerColor = NutritionPanel,
            title = { Text(food.name, color = NutritionWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Değerler 100 g üzerinden verilir. Paket gramajı biliniyorsa otomatik seçilir.",
                        color = NutritionMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        selectedGrams, { selectedGrams = it },
                        label = { Text("Miktar (gram)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (serving != null) {
                        Text("Yaklaşık 1 ${serving.first} = ${serving.second} g",
                            color = NutritionMuted, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3).forEach { count ->
                                val total = count * serving.second
                                FilterChip(
                                    selected = selectedGrams == total.toString(),
                                    onClick = { selectedGrams = total.toString() },
                                    label = { Text("$count ${serving.first}", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NutritionMint,
                                        selectedLabelColor = NutritionBg
                                    )
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(50, 100, 150, 200).forEach { gramsOption ->
                            FilterChip(
                                selected = selectedGrams == gramsOption.toString(),
                                onClick = { selectedGrams = gramsOption.toString() },
                                label = { Text("$gramsOption g", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NutritionMint,
                                    selectedLabelColor = NutritionBg,
                                    containerColor = Color(0xFF1B2839),
                                    labelColor = NutritionMuted
                                )
                            )
                        }
                    }
                    if (portion != null) {
                        Column(Modifier.fillMaxWidth().background(Color(0xFF1B2839), RoundedCornerShape(16.dp))
                            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("${portion.calories} kcal", color = NutritionMint,
                                fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text(String.format(Locale.US, "Protein %.1f g", portion.protein), color = NutritionWhite)
                            Text(String.format(Locale.US, "Karbonhidrat %.1f g", portion.carbs), color = NutritionWhite)
                            Text(String.format(Locale.US, "Yağ %.1f g", portion.fat), color = NutritionWhite)
                        }
                    }
                    Text(food.note + " · Etiket ve tarif farklı olabilir. Kaydettikten sonra öğünü düzenleyebilirsin.", color = NutritionMuted,
                        fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = portion != null, onClick = {
                    if (portion != null) {
                        save(meals + MealEntry(System.currentTimeMillis(),
                            food.name + " · " + String.format(Locale.US, "%.0f g", grams!!),
                            portion.calories, portion.protein, portion.carbs, portion.fat))
                        rememberFood(food.name)
                        if (food.note.startsWith("Open Food Facts")) saveFoodReference(food)
                        selectedFood = null
                    }
                }) { Text("Günlüğe ekle", color = NutritionMint) }
            },
            dismissButton = {
                TextButton(onClick = { selectedFood = null }) { Text("Vazgeç", color = NutritionMuted) }
            }
        )
    }

    if (templatePickerOpen) {
        AlertDialog(
            onDismissRequest = { templatePickerOpen = false },
            title = { Text("Öğün şablonları") },
            text = {
                Column(Modifier.heightIn(max = 360.dp)) {
                    templates.forEach { template ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(modifier = Modifier.weight(1f), onClick = {
                                val nextId = generateSequence(System.currentTimeMillis()) { it + 1 }
                                    .first { candidate -> meals.none { it.id == candidate } }
                                save(meals + template.copy(id = nextId))
                                templatePickerOpen = false
                            }) { Text("${template.name} · ${template.calories} kcal") }
                            TextButton(onClick = {
                                storeTemplates(templates.filterNot {
                                    it.name == template.name && it.calories == template.calories &&
                                        it.protein == template.protein && it.carbs == template.carbs && it.fat == template.fat
                                })
                                if (templates.isEmpty()) templatePickerOpen = false
                            }) { Text("Sil") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { templatePickerOpen = false }) { Text("Kapat") }
            }
        )
    }

    if (confirmCopyPrevious) {
        AlertDialog(
            onDismissRequest = { confirmCopyPrevious = false },
            containerColor = NutritionPanel,
            title = { Text("Önceki günün öğünlerini kopyala?", color = NutritionWhite) },
            text = {
                Text("$previousDay tarihindeki ${previousMeals.size} öğün, $selectedDay gününe eklenecek. Var olan öğünlerin silinmeyecek.",
                    color = NutritionMuted)
            },
            confirmButton = {
                TextButton(onClick = {
                    val existing = meals.map { it.id }.toMutableSet()
                    val copied = previousMeals.map { meal ->
                        var id = System.currentTimeMillis()
                        while (id in existing) id++
                        existing.add(id)
                        meal.copy(id = id)
                    }
                    save(meals + copied)
                    confirmCopyPrevious = false
                }) { Text("Öğünleri ekle", color = NutritionMint) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCopyPrevious = false }) {
                    Text("Vazgeç", color = NutritionMuted)
                }
            }
        )
    }

    if (pendingDeleteMeal != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteMeal = null },
            containerColor = NutritionPanel,
            title = { Text("Öğünü sil?", color = NutritionWhite) },
            text = { Text("“${pendingDeleteMeal?.name.orEmpty()}” seçili günün kayıtlarından silinecek. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteMeal?.let { target -> save(meals.filterNot { it.id == target.id }) }
                    pendingDeleteMeal = null
                }) { Text("Sil", color = Color(0xFFFF879A)) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteMeal = null }) { Text("Vazgeç") } }
        )
    }
    if (dialogOpen) {
        var name by remember(editingMeal) { mutableStateOf(editingMeal?.name ?: "") }
        var kcal by remember(editingMeal) { mutableStateOf(editingMeal?.calories?.toString() ?: "") }
        var p by remember(editingMeal) { mutableStateOf(editingMeal?.protein?.toString() ?: "") }
        var c by remember(editingMeal) { mutableStateOf(editingMeal?.carbs?.toString() ?: "") }
        var f by remember(editingMeal) { mutableStateOf(editingMeal?.fat?.toString() ?: "") }
        val valid = name.isNotBlank() && name.length <= 120 && (kcal.toIntOrNull() ?: -1) in 0..10000 &&
            listOf(p, c, f).all { (it.replace(',', '.').toDoubleOrNull() ?: -1.0) in 0.0..1000.0 }
        AlertDialog(
            onDismissRequest = { dialogOpen = false; editingMeal = null },
            containerColor = NutritionPanel,
            title = { Text(if (editingMeal == null) "Öğün ekle" else "Öğünü düzenle", color = NutritionWhite) },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Besin / öğün adı") },
                        singleLine = true,
                        isError = name.isNotEmpty() && name.isBlank()
                    )
                    OutlinedTextField(
                        value = kcal,
                        onValueChange = { kcal = it.take(12) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Kalori (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = kcal.isNotBlank() && kcal.toIntOrNull()?.let { it in 0..10000 } != true
                    )
                    OutlinedTextField(
                        value = p,
                        onValueChange = { p = it.take(12) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = p.isNotBlank() && p.replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it in 0.0..1000.0 } != true
                    )
                    OutlinedTextField(
                        value = c,
                        onValueChange = { c = it.take(12) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Karbonhidrat (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = c.isNotBlank() && c.replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it in 0.0..1000.0 } != true
                    )
                    OutlinedTextField(
                        value = f,
                        onValueChange = { f = it.take(12) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Yağ (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = f.isNotBlank() && f.replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it in 0.0..1000.0 } != true
                    )
                    val previewProtein = p.replace(',', '.').toDoubleOrNull()
                    val previewCarbs = c.replace(',', '.').toDoubleOrNull()
                    val previewFat = f.replace(',', '.').toDoubleOrNull()
                    val previewValid = listOf(previewProtein, previewCarbs, previewFat)
                        .all { it != null && it.isFinite() && it in 0.0..1000.0 }
                    val energyPreview = if (previewValid)
                        (previewProtein!! * 4 + previewCarbs!! * 4 + previewFat!! * 9)
                    else null
                    Text(
                        if (energyPreview != null)
                            "Makrolardan tahmini enerji: ${energyPreview.toInt()} kcal"
                        else "Kalori hesabı için geçerli makro değerleri gir.",
                        color = if (energyPreview != null) NutritionMint else NutritionMuted,
                        fontSize = 12.sp
                    )
                    TextButton(
                        enabled = energyPreview != null && energyPreview in 0.0..10000.0,
                        onClick = { energyPreview?.let { kcal = it.toInt().toString() } }
                    ) { Text("Hesaplanan kaloriyi kullan") }
                    val enteredCalories = kcal.toIntOrNull()
                    if (energyPreview != null && enteredCalories != null &&
                        energyPreview > 0.0 &&
                        kotlin.math.abs(enteredCalories - energyPreview) > energyPreview * 0.2
                    ) {
                        Text(
                            "Girilen kalori ile makrolardan hesaplanan enerji arasında %20'den fazla fark var. Etiket değerlerini kontrol et.",
                            color = Color(0xFFFFC483), fontSize = 11.sp
                        )
                    }
                    Text("4/4/9 kcal/g tahminidir; etiket kalorisi farklı olabilir.",
                        color = NutritionMuted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    if (valid) {
                        val entryId = editingMeal?.id ?: generateSequence(System.currentTimeMillis()) { it + 1 }
                            .first { candidate -> meals.none { it.id == candidate } }
                        val newEntry = MealEntry(
                            entryId, name.trim(), kcal.toInt(),
                            p.replace(',', '.').toDouble(),
                            c.replace(',', '.').toDouble(),
                            f.replace(',', '.').toDouble()
                        )
                        val updatedMeals = if (editingMeal == null) {
                            meals + newEntry
                        } else {
                            meals.map { existing ->
                                if (existing.id == entryId) newEntry else existing
                            }
                        }
                        save(updatedMeals)
                        dialogOpen = false
                        editingMeal = null
                    }
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { dialogOpen = false; editingMeal = null }) { Text("İptal") } }
        )
    }
}

@Composable
private fun MacroValue(label: String, value: Double, color: Color, totalMacroCalories: Double, caloriesPerGram: Double) {
    val animatedValue by animateFloatAsState(
        targetValue = value.toFloat(), animationSpec = tween(650),
        label = "macro-${label}"
    )
    val share = if (totalMacroCalories > 0.0)
        ((value * caloriesPerGram / totalMacroCalories) * 100.0).coerceIn(0.0, 100.0)
    else 0.0
    Column(
        modifier = Modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = {
                    if (totalMacroCalories > 0.0)
                        ((animatedValue * caloriesPerGram) / totalMacroCalories)
                            .toFloat().coerceIn(0f, 1f)
                    else 0f
                },
                modifier = Modifier.fillMaxSize(),
                color = color,
                trackColor = Color(0xFF30445C),
                strokeWidth = 6.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale("tr", "TR"), "%.0f", animatedValue),
                    color = NutritionWhite, fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold)
                Text("gram", color = NutritionMuted, fontSize = 9.sp)
            }
        }
        Text(label, color = NutritionWhite, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(String.format(Locale("tr", "TR"), "%%%1.0f enerji", share),
            color = color, fontSize = 10.sp)
    }
}
