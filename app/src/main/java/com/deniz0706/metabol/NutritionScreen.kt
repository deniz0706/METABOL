package com.deniz0706.metabol

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val storageKey = "meals_$today"
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
    fun save(updated: List<MealEntry>) {
        meals = updated
        val json = JSONArray()
        updated.forEach { item ->
            json.put(JSONObject().put("id", item.id).put("name", item.name)
                .put("calories", item.calories).put("protein", item.protein)
                .put("carbs", item.carbs).put("fat", item.fat))
        }
        prefs.edit().putString(storageKey, json.toString()).apply()
    }
    val calories = meals.sumOf { it.calories }
    val protein = meals.sumOf { it.protein }
    val carbs = meals.sumOf { it.carbs }
    val fat = meals.sumOf { it.fat }
    LazyColumn(modifier.fillMaxSize().background(NutritionBg), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("M E T A B O L", color = NutritionMint, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(24.dp))
            Text("BESLENME GÜNLÜĞÜ", color = NutritionMuted, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Bugün ne yedin?", color = NutritionWhite, fontSize = 29.sp, fontWeight = FontWeight.Bold)
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
            }
        }
        item {
            Button(onClick = { dialogOpen = true }, modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NutritionMint, contentColor = NutritionBg)) {
                Text("+  Öğün ekle", fontWeight = FontWeight.Bold)
            }
        }
        item { Text("BUGÜNÜN KAYITLARI", color = NutritionMuted, fontSize = 11.sp, letterSpacing = 2.sp) }
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
                    TextButton(onClick = { save(meals.filterNot { it.id == meal.id }) }) {
                        Text("Sil", color = Color(0xFFFF879A))
                    }
                }
            }
        }
        item {
            Text("Besin değerleri kullanıcı girişidir; otomatik doğrulanmaz.", color = NutritionMuted, fontSize = 11.sp)
        }
    }
    if (dialogOpen) {
        var name by remember { mutableStateOf("") }
        var kcal by remember { mutableStateOf("") }
        var p by remember { mutableStateOf("") }
        var c by remember { mutableStateOf("") }
        var f by remember { mutableStateOf("") }
        val valid = name.isNotBlank() && (kcal.toIntOrNull() ?: -1) in 0..10000 &&
            listOf(p, c, f).all { (it.replace(',', '.').toDoubleOrNull() ?: -1.0) in 0.0..1000.0 }
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            containerColor = NutritionPanel,
            title = { Text("Öğün ekle", color = NutritionWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        save(meals + MealEntry(System.currentTimeMillis(), name.trim(), kcal.toInt(),
                            p.replace(',', '.').toDouble(), c.replace(',', '.').toDouble(),
                            f.replace(',', '.').toDouble()))
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
