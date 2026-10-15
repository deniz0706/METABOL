package com.deniz0706.metabol

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

internal data class RecipeIngredient(val food: FoodReference, val grams: Double)
internal data class SavedRecipe(val name: String, val servings: Int, val ingredients: List<RecipeIngredient>)

internal object RecipeStore {
    private const val KEY = "favorite_recipes"
    fun load(context: Context): List<SavedRecipe> = runCatching {
        val raw = context.getSharedPreferences("metabol_nutrition", Context.MODE_PRIVATE).getString(KEY, "[]")
        val arr = JSONArray(raw)
        (0 until arr.length()).map { index ->
            val recipe = arr.getJSONObject(index)
            val ingredients = recipe.getJSONArray("ingredients")
            SavedRecipe(recipe.getString("name"), recipe.getInt("servings"),
                (0 until ingredients.length()).map { i ->
                    val item = ingredients.getJSONObject(i)
                    RecipeIngredient(FoodReference(item.getString("name"), item.getDouble("kcal"),
                        item.getDouble("protein"), item.getDouble("carbs"), item.getDouble("fat")),
                        item.getDouble("grams"))
                })
        }.filter { it.servings in 1..100 && it.ingredients.isNotEmpty() }
    }.getOrDefault(emptyList())
    fun save(context: Context, recipes: List<SavedRecipe>) {
        val arr = JSONArray()
        recipes.take(30).forEach { recipe ->
            val ingredients = JSONArray()
            recipe.ingredients.forEach { entry ->
                ingredients.put(JSONObject().put("name", entry.food.name).put("kcal", entry.food.kcal100)
                    .put("protein", entry.food.protein100).put("carbs", entry.food.carbs100)
                    .put("fat", entry.food.fat100).put("grams", entry.grams))
            }
            arr.put(JSONObject().put("name", recipe.name).put("servings", recipe.servings)
                .put("ingredients", ingredients))
        }
        context.getSharedPreferences("metabol_nutrition", Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
    fun portion(recipe: SavedRecipe): FoodPortion {
        val servings = recipe.servings.toDouble()
        return FoodPortion(
            (recipe.ingredients.sumOf { it.food.kcal100 * it.grams / 100 } / servings).roundToInt(),
            recipe.ingredients.sumOf { it.food.protein100 * it.grams / 100 } / servings,
            recipe.ingredients.sumOf { it.food.carbs100 * it.grams / 100 } / servings,
            recipe.ingredients.sumOf { it.food.fat100 * it.grams / 100 } / servings
        )
    }
}

@Composable
internal fun RecipeDialog(context: Context, onClose: () -> Unit,
    onAdd: (String, FoodPortion) -> Unit) {
    var recipes by remember { mutableStateOf(RecipeStore.load(context)) }
    var name by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("1") }
    var query by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("100") }
    var ingredients by remember { mutableStateOf<List<RecipeIngredient>>(emptyList()) }
    val selected = remember(query) { FoodCatalog.foods.firstOrNull { it.name == query.trim() } }
    val current = SavedRecipe(name.trim(), servings.toIntOrNull() ?: 0, ingredients)
    val valid = current.name.isNotBlank() && current.name.length <= 120 &&
        current.servings in 1..100 && ingredients.isNotEmpty()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Tarif oluşturucu ve favoriler") },
        text = {
            Column(Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(120) }, label = { Text("Tarif adı") },
                    singleLine = true)
                OutlinedTextField(servings, { servings = it.filter(Char::isDigit).take(3) },
                    label = { Text("Porsiyon sayısı (1–100)") }, singleLine = true)
                OutlinedTextField(query, { query = it.take(120) },
                    label = { Text("Malzeme ara") }, singleLine = true)
                if (selected == null && query.isNotBlank()) {
                    FoodCatalog.search(query).take(5).forEach { food ->
                        TextButton(onClick = { query = food.name }) { Text(food.name) }
                    }
                }
                OutlinedTextField(grams, { grams = it.replace(',', '.').take(8) },
                    label = { Text("Malzeme gramı") }, singleLine = true)
                Button(onClick = {
                    val amount = grams.toDoubleOrNull()
                    if (selected != null && amount != null && amount in 1.0..10000.0 &&
                        ingredients.size < 50) {
                        ingredients = ingredients + RecipeIngredient(selected, amount)
                        query = ""
                    }
                }, enabled = selected != null && (grams.toDoubleOrNull() ?: 0.0) in 1.0..10000.0 &&
                    ingredients.size < 50) { Text("Malzemeyi ekle") }
                ingredients.forEachIndexed { index, item ->
                    Row {
                        Text("${item.food.name} · ${item.grams} g", modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            ingredients = ingredients.filterIndexed { i, _ -> i != index }
                        }) { Text("Sil") }
                    }
                }
                if (valid) {
                    val p = RecipeStore.portion(current)
                    Text("Porsiyon başına: ${p.calories} kcal · P ${"%.1f".format(Locale.US,p.protein)} g · K ${"%.1f".format(Locale.US,p.carbs)} g · Y ${"%.1f".format(Locale.US,p.fat)} g")
                    Button(onClick = {
                        recipes = (listOf(current) + recipes.filterNot { it.name == current.name }).take(30)
                        RecipeStore.save(context, recipes)
                    }) { Text("Tarifi favorilere kaydet") }
                    OutlinedButton(onClick = { onAdd(current.name, p); onClose() }) {
                        Text("Bir porsiyonu günlüğe ekle")
                    }
                }
                HorizontalDivider()
                Text("Favori tarifler (${recipes.size}/30)")
                recipes.forEach { recipe ->
                    val p = RecipeStore.portion(recipe)
                    Text("${recipe.name} · ${p.calories} kcal/porsiyon")
                    Row {
                        TextButton(onClick = { onAdd(recipe.name, p); onClose() }) {
                            Text("Günlüğe ekle")
                        }
                        TextButton(onClick = {
                            name = recipe.name
                            servings = recipe.servings.toString()
                            ingredients = recipe.ingredients
                        }) { Text("Düzenlemek için yükle") }
                        TextButton(onClick = {
                            recipes = recipes.filterNot { it.name == recipe.name }
                            RecipeStore.save(context, recipes)
                        }) { Text("Sil") }
                    }
                }
                Text("Katalog değerleri yaklaşık olabilir; pişirme, su kaybı ve etiket farkları hesapları etkiler.")
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Kapat") } }
    )
}
