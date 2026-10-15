package com.deniz0706.metabol

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal object OpenFoodFacts {
    fun search(query: String): List<FoodReference> {
        val term = query.trim().take(80)
        require(term.length >= 2)
        val address = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=" +
            URLEncoder.encode(term, "UTF-8") +
            "&search_simple=1&action=process&json=1&page_size=20&fields=product_name,brands,nutriments"
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "METABOL Android nutrition tracker")
            if (connection.responseCode !in 200..299) error("Service unavailable")
            val data = connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 1_500_000) error("Response too large")
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
            return parse(data)
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
            val kcal = n.optDouble("energy-kcal_100g", Double.NaN)
            val p = n.optDouble("proteins_100g", Double.NaN)
            val c = n.optDouble("carbohydrates_100g", Double.NaN)
            val f = n.optDouble("fat_100g", Double.NaN)
            if (!listOf(kcal,p,c,f).all { it.isFinite() } ||
                kcal !in 0.0..1000.0 || listOf(p,c,f).any { it !in 0.0..100.0 }) return@mapNotNull null
            FoodReference(name,kcal,p,c,f,"Open Food Facts · Etiketten doğrula")
        }.distinctBy { it.name }.take(20)
    }
}
