package com.deniz0706.metabol

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
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

private val NutritionBg = Color(0xFF070B12)
private val NutritionPanel = Color(0xFF121C28)
private val NutritionMint = Color(0xFF71E8C4)
private val NutritionMuted = Color(0xFF8B9CB2)
private val NutritionWhite = Color(0xFFF4F8FC)

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
    val storageKey = "meals_$selectedDay"
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
    var onlineResults by remember { mutableStateOf<List<FoodReference>>(emptyList()) }
    var onlineLoading by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    var onlineSearched by remember { mutableStateOf(false) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    fun searchOnline(query: String) {
        if (query.trim().length < 2 || onlineLoading) return
        onlineLoading = true
        onlineError = null
        onlineResults = emptyList()
        onlineSearched = true
        val generation = ++searchGeneration
        Thread {
            val result = runCatching {
                if (query.trim().matches(Regex("[0-9]{8,14}"))) OpenFoodFacts.lookupBarcode(query.trim())
                else OpenFoodFacts.search(query)
            }
            Handler(Looper.getMainLooper()).post {
                if (generation == searchGeneration) {
                    onlineLoading = false
                    result.onSuccess { onlineResults = it }
                        .onFailure { onlineError = "Bağlantı kurulamadı. Tekrar dene veya çevrimdışı kataloğu kullan." }
                }
            }
        }.start()
    }
    var selectedFood by remember { mutableStateOf<FoodReference?>(null) }
    var selectedGrams by remember { mutableStateOf("100") }
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
                else FoodReference(name, kcal, protein, carbs, fat, "Önceden kaydettiğin ürün · Etiketten doğrula")
            }
        }.getOrDefault(emptyList()))
    }
    fun saveFoodReference(food: FoodReference) {
        val updated = (listOf(food) + savedFoods.filterNot { it.name == food.name }).take(30)
        savedFoods = updated
        val array = JSONArray()
        updated.forEach { item ->
            array.put(JSONObject().put("name", item.name).put("kcal", item.kcal100)
                .put("protein", item.protein100).put("carbs", item.carbs100).put("fat", item.fat100))
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
    val calories = meals.sumOf { it.calories }
    val protein = meals.sumOf { it.protein }
    val carbs = meals.sumOf { it.carbs }
    val fat = meals.sumOf { it.fat }
    val macroCalories = protein * 4.0 + carbs * 4.0 + fat * 9.0
    LazyColumn(modifier.fillMaxSize().background(NutritionBg), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("M E T A B O L", color = NutritionMint,
                        fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 3.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("NUTRITION / GÜNLÜK TAKİP", color = NutritionMuted,
                        fontSize = 10.sp, letterSpacing = 1.2.sp)
                }
                Box(Modifier.size(46.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF274E4D), Color(0xFF172D39))),
                        CircleShape), contentAlignment = Alignment.Center) {
                    Text("N", color = NutritionMint, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Beslenme", color = NutritionWhite, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text("Kayıtların, alışkanlıkların, ilerlemen.", color = NutritionMuted, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
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
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1C3740), Color(0xFF172535), NutritionPanel)), RoundedCornerShape(28.dp)).border(1.dp, Color(0xFF2B4850), RoundedCornerShape(28.dp)).padding(23.dp)) {
                Text("TOPLAM ENERJİ", color = NutritionMuted, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$calories", color = NutritionWhite, fontSize = 49.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Text("kcal", color = NutritionMint, fontSize = 17.sp,
                        modifier = Modifier.padding(bottom = 10.dp))
                }
                Text("${meals.size} öğün kaydedildi", color = NutritionMuted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF2A394B))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MacroValue("Protein", protein, NutritionMint)
                    MacroValue("Karbonhidrat", carbs, Color(0xFFA99BFF))
                    MacroValue("Yağ", fat, Color(0xFFFFC483))
                }
                if (meals.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF2A394B))
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
                Text("SON 7 GÜN · KALORİ", color = NutritionMuted, fontSize = 11.sp, letterSpacing = 1.5.sp)
                val maxCalories = (lastSevenDays.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                lastSevenDays.forEach { (day, total) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(day.takeLast(5), color = NutritionMuted, fontSize = 11.sp, modifier = Modifier.width(45.dp))
                        LinearProgressIndicator(
                            progress = { total.toFloat() / maxCalories },
                            modifier = Modifier.weight(1f).height(8.dp),
                            color = NutritionMint,
                            trackColor = Color(0xFF2A394B)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("$total", color = NutritionWhite, fontSize = 12.sp, modifier = Modifier.width(45.dp))
                    }
                }
                val recordedDays = lastSevenDays.count { (day, _) ->
                    prefs.contains("meals_$day")
                }
                val sevenDayTotal = lastSevenDays.sumOf { it.second }
                HorizontalDivider(color = Color(0xFF2A394B))
                Text("7 günlük kayıt toplamı: $sevenDayTotal kcal", color = NutritionWhite, fontSize = 13.sp)
                Text("Kayıt bulunan gün: $recordedDays / 7", color = NutritionMuted, fontSize = 12.sp)
                Text("Boş günler sıfır tüketim anlamına gelmez. Grafik kaydedilen enerjiyi gösterir.",
                    color = NutritionMuted, fontSize = 11.sp)
            }
        }
        item { Text("SEÇİLİ GÜNÜN KAYITLARI", color = NutritionMuted, fontSize = 11.sp, letterSpacing = 2.sp) }
        if (meals.isEmpty()) {
            item {
                Text("Henüz öğün eklemedin. Değerleri besin etiketinden girerek başlayabilirsin.",
                    color = NutritionMuted, modifier = Modifier.padding(vertical = 20.dp))
            }
        } else {
            items(meals.size) { index ->
                val meal = meals[index]
                Row(Modifier.fillMaxWidth().background(NutritionPanel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(meal.name, color = NutritionWhite, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${meal.calories} kcal", color = NutritionMint,
                                fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            Text("P ${meal.protein.toInt()} · K ${meal.carbs.toInt()} · Y ${meal.fat.toInt()} g",
                                color = NutritionMuted, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        val macroEnergy = meal.protein * 4 + meal.carbs * 4 + meal.fat * 9
                        if (macroEnergy > 0) {
                            Row(Modifier.fillMaxWidth().height(4.dp)
                                .background(Color(0xFF263544), RoundedCornerShape(4.dp))) {
                                Box(Modifier.fillMaxHeight()
                                    .weight((meal.protein * 4 / macroEnergy).toFloat().coerceAtLeast(0.001f))
                                    .background(Color(0xFF71E8C4)))
                                Box(Modifier.fillMaxHeight()
                                    .weight((meal.carbs * 4 / macroEnergy).toFloat().coerceAtLeast(0.001f))
                                    .background(Color(0xFFA99BFF)))
                                Box(Modifier.fillMaxHeight()
                                    .weight((meal.fat * 9 / macroEnergy).toFloat().coerceAtLeast(0.001f))
                                    .background(Color(0xFFFFC483)))
                            }
                        }
                    }
                    TextButton(onClick = { editingMeal = meal; dialogOpen = true }) {
                        Text("Düzenle", color = NutritionMint)
                    }
                    TextButton(onClick = { pendingDeleteMeal = meal }) {
                        Text("Sil", color = Color(0xFFFF879A))
                    }
                }
            }
        }
        item {
            Text("Besin değerleri kullanıcı girişidir; otomatik doğrulanmaz.", color = NutritionMuted, fontSize = 11.sp)
        }
    }

    if (catalogOpen) {
        var query by remember { mutableStateOf("") }
        val savedResults = remember(query, savedFoods) {
            val needle = query.trim()
            savedFoods.filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
        }
        val localResults = remember(query, recentFoodNames) {
            FoodCatalog.search(query).sortedWith(
                compareBy<FoodReference> {
                    if (query.isBlank()) {
                        val index = recentFoodNames.indexOf(it.name)
                        if (index == -1) Int.MAX_VALUE else index
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
                OutlinedButton(
                    onClick = {
                        val options = GmsBarcodeScannerOptions.Builder()
                            .setBarcodeFormats(
                                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                                Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E
                            ).enableAutoZoom().build()
                        runCatching {
                            GmsBarcodeScanning.getClient(context, options).startScan()
                                .addOnSuccessListener { barcode ->
                                    val code = barcode.rawValue.orEmpty()
                                    if (code.matches(Regex("[0-9]{8,14}"))) {
                                        query = code
                                        searchOnline(code)
                                    } else {
                                        onlineError = "Geçerli bir ürün barkodu okunamadı."
                                    }
                                }
                                .addOnFailureListener {
                                    onlineError = "Kamera tarayıcısı kullanılamıyor. Barkodu elle yazabilirsin."
                                }
                        }.onFailure {
                            onlineError = "Tarayıcı başlatılamadı. Barkodu elle yazabilirsin."
                        }
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
                                selectedGrams = "100"
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
                                        selectedGrams = "100"
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
                        Text("ÇEVRİMDIŞI KATALOG · ${localResults.size} BESİN",
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
                    items(localResults) { food ->
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
        val grams = selectedGrams.replace(',', '.').toDoubleOrNull()
        val validGrams = grams != null && grams.isFinite() && grams in 1.0..5000.0
        val portion = if (validGrams) FoodCatalog.calculate(food, grams!!) else null
        AlertDialog(
            onDismissRequest = { selectedFood = null },
            containerColor = NutritionPanel,
            title = { Text(food.name, color = NutritionWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Miktarı gram olarak gir; besin değerleri otomatik hesaplanır.",
                        color = NutritionMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        selectedGrams, { selectedGrams = it },
                        label = { Text("Miktar (gram)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        save(meals + MealEntry(System.currentTimeMillis(), food.name,
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
                    val copied = previousMeals.mapIndexed { index, meal ->
                        var id = System.currentTimeMillis() + index
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
            title = { Text("Öğünü sil?") },
            text = { Text("Bu öğün seçili günün kayıtlarından silinecek.") },
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
            onDismissRequest = { dialogOpen = false },
            containerColor = NutritionPanel,
            title = { Text(if (editingMeal == null) "Öğün ekle" else "Öğünü düzenle", color = NutritionWhite) },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Besin / öğün adı") }, singleLine = true)
                    OutlinedTextField(kcal, { kcal = it }, label = { Text("Kalori (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(p, { p = it }, label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(c, { c = it }, label = { Text("Karbonhidrat (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(f, { f = it }, label = { Text("Yağ (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    if (valid) {
                        val newEntry = MealEntry(editingMeal?.id ?: System.currentTimeMillis(), name.trim(), kcal.toInt(),
                            p.replace(',', '.').toDouble(), c.replace(',', '.').toDouble(),
                            f.replace(',', '.').toDouble())
                        save(if (editingMeal == null) meals + newEntry else meals.map { if (it.id == editingMeal!!.id) newEntry else it })
                        dialogOpen = false
                    }
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("İptal") } }
        )
    }
}

@Composable
private fun MacroValue(label: String, value: Double, color: Color) {
    Column {
        Text(label, color = NutritionMuted, fontSize = 11.sp)
        Spacer(Modifier.height(7.dp))
        Text(String.format(Locale.US, "%.1f g", value), color = color, fontSize = 17.sp,
            fontWeight = FontWeight.Bold)
    }
}
