package com.deniz0706.metabol

import android.content.Context
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

private val NutritionBg = Color(0xFF080D15)
private val NutritionPanel = Color(0xFF131C2A)
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
    var selectedFood by remember { mutableStateOf<FoodReference?>(null) }
    var selectedGrams by remember { mutableStateOf("100") }
    var pendingDeleteMeal by remember { mutableStateOf<MealEntry?>(null) }
    var historyRevision by remember { mutableIntStateOf(0) }
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
            Text("M E T A B O L", color = NutritionMint, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(24.dp))
            Text("BESLENME GÜNLÜĞÜ", color = NutritionMuted, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Beslenme kayıtların.", color = NutritionWhite, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedButton(onClick = { shiftDay(-1) }) { Text("‹  Önceki", color = NutritionMint) }
                Text(selectedDay, color = NutritionWhite, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { shiftDay(1) }, enabled = selectedDay < today) {
                    Text("Sonraki  ›", color = NutritionMint)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(NutritionPanel, RoundedCornerShape(24.dp)).padding(22.dp)) {
                Text("TOPLAM ENERJİ", color = NutritionMuted, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                Text("$calories kcal", color = NutritionWhite, fontSize = 39.sp, fontWeight = FontWeight.Bold)
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
        item {
            Text("Besin arama çevrimdışı örnek katalog kullanır. Değerler yaklaşık ortalamadır; marka, pişirme ve yağ miktarına göre değişebilir.",
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
                        Text("${meal.calories} kcal · P ${meal.protein.toInt()} g · K ${meal.carbs.toInt()} g · Y ${meal.fat.toInt()} g",
                            color = NutritionMuted, fontSize = 11.sp)
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
        AlertDialog(
            onDismissRequest = { catalogOpen = false },
            containerColor = NutritionPanel,
            title = { Text("Besin kütüphanesi", color = NutritionWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        label = { Text("Besin ara: muz, tavuk, pilav...") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Text("Yaklaşık değerler · 100 gram başına", color = NutritionMuted, fontSize = 11.sp)
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val results = FoodCatalog.search(query)
                        if (results.isEmpty()) Text("Sonuç bulunamadı. Manuel giriş yapabilirsin.", color = NutritionMuted)
                        results.forEach { food ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(Color(0xFF1B2839), RoundedCornerShape(14.dp))
                                    .clickable {
                                        selectedFood = food
                                        selectedGrams = "100"
                                        catalogOpen = false
                                    }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(food.name, color = NutritionWhite, fontWeight = FontWeight.Medium)
                                    Text("P ${food.protein100} · K ${food.carbs100} · Y ${food.fat100} g",
                                        color = NutritionMuted, fontSize = 11.sp)
                                }
                                Text("${food.kcal100.toInt()} kcal", color = NutritionMint,
                                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { catalogOpen = false }) { Text("Kapat", color = NutritionMint) }
            }
        )
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
                    Text("Tahmini ortalama · Etiket ve tarif farklı olabilir.", color = NutritionMuted,
                        fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = portion != null, onClick = {
                    if (portion != null) {
                        save(meals + MealEntry(System.currentTimeMillis(), food.name,
                            portion.calories, portion.protein, portion.carbs, portion.fat))
                        selectedFood = null
                    }
                }) { Text("Günlüğe ekle", color = NutritionMint) }
            },
            dismissButton = {
                TextButton(onClick = { selectedFood = null }) { Text("Vazgeç", color = NutritionMuted) }
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
