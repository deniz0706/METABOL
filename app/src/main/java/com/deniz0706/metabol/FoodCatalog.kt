package com.deniz0706.metabol

import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Small, transparent offline reference catalog. Values are illustrative averages per 100 g,
 * not a verified brand/product database. Check package labels for actual products.
 */
internal data class FoodReference(
    val name: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val note: String = "Yaklaşık ortalama · 100 g"
)

internal data class FoodPortion(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

internal object FoodCatalog {
    private val coreFoods = listOf(
        FoodReference("Muz", 89.0, 1.1, 22.8, 0.3),
        FoodReference("Elma", 52.0, 0.3, 13.8, 0.2),
        FoodReference("Yumurta - bütün, çiğ", 143.0, 12.6, 0.7, 9.5),
        FoodReference("Tavuk göğsü (pişmiş)", 165.0, 31.0, 0.0, 3.6),
        FoodReference("Pirinç (pişmiş, sade)", 130.0, 2.7, 28.2, 0.3),
        FoodReference("Bulgur (pişmiş)", 83.0, 3.1, 18.6, 0.2),
        FoodReference("Yoğurt (sade, tam yağlı)", 61.0, 3.5, 4.7, 3.3),
        FoodReference("Süt (tam yağlı)", 61.0, 3.2, 4.8, 3.3),
        FoodReference("Yulaf ezmesi (kuru)", 389.0, 16.9, 66.3, 6.9),
        FoodReference("Beyaz ekmek", 265.0, 9.0, 49.0, 3.2),
        FoodReference("Beyaz peynir (ortalama)", 264.0, 14.0, 4.0, 21.0),
        FoodReference("Domates", 18.0, 0.9, 3.9, 0.2),
        FoodReference("Salatalık", 15.0, 0.7, 3.6, 0.1),
        FoodReference("Patates (haşlanmış)", 87.0, 1.9, 20.1, 0.1),
        FoodReference("Mercimek (haşlanmış)", 116.0, 9.0, 20.1, 0.4),
        FoodReference("Badem", 579.0, 21.2, 21.6, 49.9),
        FoodReference("Zeytinyağı", 884.0, 0.0, 0.0, 100.0),
        FoodReference("Ton balığı (suda, süzülmüş)", 116.0, 25.5, 0.0, 0.8),
        FoodReference("Dana kıyma (pişmiş, orta yağlı)", 250.0, 26.0, 0.0, 15.0),
        FoodReference("Portakal", 47.0, 0.9, 11.8, 0.1)
    )

    val foods: List<FoodReference> = (coreFoods + ExtendedFoodCatalog.foods + MealCatalog.foods)
        .distinctBy { it.name }
        .sortedBy { it.name }

    private fun normalize(input: String): String =
        Normalizer.normalize(input.lowercase(Locale("tr", "TR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('ı', 'i')

    val categories = listOf("Tümü", "Yemekler", "Et ve balık", "Kahvaltı", "Meyve", "Sebze", "Tahıl", "Süt ürünleri", "Kuruyemiş")

    fun categoryOf(food: FoodReference): String {
        val name = food.name.lowercase(Locale("tr", "TR"))
        return when {
            food.note.contains("YEMEK") || food.note.contains("HAZIR YEMEK") -> "Yemekler"
            name.contains("yumurta") || name.contains("peynir") || name.contains("zeytin") -> "Kahvaltı"
            food.note.contains("PROTEIN") -> "Et ve balık"
            food.note.contains("MEYVE") -> "Meyve"
            food.note.contains("SEBZE") -> "Sebze"
            food.note.contains("TAHIL") -> "Tahıl"
            food.note.contains("SUT") -> "Süt ürünleri"
            food.note.contains("KURUYEMIS") -> "Kuruyemiş"
            else -> when {
                name.contains("ekmek") || name.contains("pirinç") || name.contains("bulgur") || name.contains("yulaf") -> "Tahıl"
                name.contains("süt") || name.contains("yoğurt") -> "Süt ürünleri"
                else -> "Yemekler"
            }
        }
    }

    /** Standardized approximate serving weights; manual gram entry always remains available. */
    fun serving(food: FoodReference): Pair<String, Int>? {
        val name = food.name.lowercase(Locale("tr", "TR"))
        return when {
            name.contains("yumurta") && !name.contains("beyazı") && !name.contains("sarısı") -> "adet" to 50
            name.contains("yumurta beyazı") -> "adet beyaz" to 33
            name.contains("yumurta sarısı") -> "adet sarı" to 17
            name.contains("iskender") -> "porsiyon" to 400
            name.contains("çorbası") || name.contains("çorba") -> "kase" to 250
            name.contains("ekmek") && !name.contains("balık ekmek") && !name.contains("etli ekmek") -> "dilim" to 30
            name.contains("lahmacun") -> "adet" to 150
            name.contains("simit") -> "adet" to 100
            name.contains("pizza") -> "dilim" to 110
            name.contains("pilav") -> "porsiyon" to 200
            name.contains("mantı") -> "porsiyon" to 250
            name.contains("köfte") && !name.contains("çiğ köfte") -> "adet" to 35
            name.contains("muz") -> "adet" to 120
            name.contains("elma") -> "adet" to 180
            name.contains("mandalina") -> "adet" to 90
            name.contains("portakal") -> "adet" to 150
            name.contains("kruvasan") -> "adet" to 70
            name.contains("poğaça") -> "adet" to 80
            name.contains("açma") -> "adet" to 90
            else -> null
        }
    }

    private val indexedFoods by lazy { foods.map { it to normalize(it.name) } }

    fun search(query: String): List<FoodReference> {
        val normalized = normalize(query.trim())
        if (normalized.isEmpty()) return foods
        return indexedFoods.filter { it.second.contains(normalized) }.map { it.first }
    }

    fun calculate(food: FoodReference, grams: Double): FoodPortion {
        require(grams.isFinite() && grams in 1.0..5000.0)
        val factor = grams / 100.0
        return FoodPortion(
            (food.kcal100 * factor).roundToInt(),
            food.protein100 * factor,
            food.carbs100 * factor,
            food.fat100 * factor
        )
    }
}
