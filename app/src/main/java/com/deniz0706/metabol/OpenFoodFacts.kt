package com.deniz0706.metabol

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal object OpenFoodFacts {
    private const val MAX_RESPONSE_BYTES = 1_500_000
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private data class Cached(val savedAt: Long, val foods: List<FoodReference>)
    private val cache = object : LinkedHashMap<String, Cached>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?) =
            size > 32
    }

    private fun cached(key: String, fetch: () -> List<FoodReference>): List<FoodReference> {
        val now = System.currentTimeMillis()
        synchronized(cache) {
            cache[key]?.takeIf { now >= it.savedAt && now - it.savedAt < CACHE_TTL_MS }?.let {
                return it.foods
            }
        }
        val foods = fetch()
        synchronized(cache) { cache[key] = Cached(System.currentTimeMillis(), foods) }
        return foods
    }

    fun search(query: String): List<FoodReference> {
        val term = query.trim().take(80)
        require(term.length >= 2)
        return cached("search:${term.lowercase(java.util.Locale.ROOT)}") {
            val address = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=" +
                URLEncoder.encode(term, "UTF-8") +
                "&search_simple=1&action=process&json=1&page_size=60&" +
                "fields=product_name,product_name_tr,brands,quantity,product_quantity,product_quantity_unit,nutriments"
            parse(request(address))
        }
    }

    fun lookupBarcode(code: String): List<FoodReference> {
        require(code.matches(Regex("[0-9]{8,14}")))
        return cached("barcode:$code") {
            val address = "https://world.openfoodfacts.org/api/v2/product/$code.json?" +
                "fields=product_name,product_name_tr,brands,quantity,product_quantity,product_quantity_unit,nutriments"
            val root = JSONObject(request(address))
            if (root.optInt("status") != 1) emptyList()
            else {
                val product = root.optJSONObject("product")
                val wrapper = JSONObject().put("products", org.json.JSONArray().put(product))
                parse(wrapper.toString())
            }
        }
    }

    private fun request(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "METABOL Android nutrition tracker")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) error("Service unavailable")
            return connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_RESPONSE_BYTES) error("Response too large")
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(json: String): List<FoodReference> {
        val products = JSONObject(json).optJSONArray("products") ?: return emptyList()
        return (0 until products.length()).mapNotNull { i ->
            val product = products.optJSONObject(i) ?: return@mapNotNull null
            val name = product.optString("product_name_tr").ifBlank { product.optString("product_name") }.trim().take(100)
            val n = product.optJSONObject("nutriments") ?: return@mapNotNull null
            if (name.isEmpty()) return@mapNotNull null
            val kcalDirect = n.optDouble("energy-kcal_100g", Double.NaN)
            val energyKj = n.optDouble("energy-kj_100g", Double.NaN)
            val kcal = if (kcalDirect.isFinite()) kcalDirect
                else if (energyKj.isFinite()) energyKj / 4.184
                else Double.NaN
            val p = n.optDouble("proteins_100g", Double.NaN)
            val c = n.optDouble("carbohydrates_100g", Double.NaN)
            val f = n.optDouble("fat_100g", Double.NaN)
            if (!listOf(kcal,p,c,f).all { it.isFinite() } ||
                kcal !in 0.0..1000.0 || listOf(p,c,f).any { it !in 0.0..100.0 }) return@mapNotNull null
            val brand = product.optString("brands").trim().take(32)
            val displayName = if (brand.isBlank() || name.contains(brand, ignoreCase = true)) name
                else "$brand · $name"
            val quantity = product.optString("quantity")
            val quantityNumber = product.optDouble("product_quantity", Double.NaN)
            val quantityUnit = product.optString("product_quantity_unit")
            // OFF quantity may be "35 g", "2 x 35 g", "0.5 kg" or numeric metadata.
            // Do not interpret ml/L as grams: density is unknown.
            val normalizedUnit = quantityUnit.trim().lowercase(java.util.Locale.ROOT)
            val metadataGrams = when (normalizedUnit) {
                "g", "gr", "gram", "grams" -> quantityNumber
                "kg", "kilogram", "kilograms" -> quantityNumber * 1000.0
                else -> Double.NaN
            }
            val quantityMatch = Regex(
                """(?i)(?:(\d{1,2})\s*[x×]\s*)?(\d{1,4}(?:[.,]\d+)?)\s*(kg|kilograms?|grams?|gr|g)\b"""
            ).find(quantity)
            val textGrams = quantityMatch?.let { match ->
                val count = match.groupValues[1].toIntOrNull() ?: 1
                val amount = match.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
                val multiplier = if (match.groupValues[3].lowercase().startsWith("k")) 1000.0 else 1.0
                amount * multiplier
            } ?: Double.NaN
            // For "2 x 35 g" start with one 35 g serving, not the entire 70 g pack.
            // The user can still enter the total amount in the portion dialog.
            val multipackCount = quantityMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val rawGrams = if (textGrams.isFinite()) textGrams else metadataGrams
            val packageGrams = rawGrams.takeIf { it.isFinite() && it in 1.0..5000.0 }
                ?.let { kotlin.math.round(it).toInt() }?.takeIf { it > 0 }
            val note = if (packageGrams != null) {
                val portionLabel = if (multipackCount > 1) "1 adet" else "Paket"
                "Open Food Facts · ${portionLabel}: ${packageGrams} g · Etiketten doğrula"
            } else "Open Food Facts · Etiketten doğrula"
            FoodReference(displayName.take(120),kcal,p,c,f,note)
        }.distinctBy { it.name }.take(60)
    }
}
