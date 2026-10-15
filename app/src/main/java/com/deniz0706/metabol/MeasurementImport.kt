package com.deniz0706.metabol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** Imports the user-facing measurement history format without overwriting existing entries. */
internal object MeasurementImport {
    data class Result(val weightsAdded: Int, val bodyAdded: Int, val skipped: Int)

    fun import(context: Context, source: String): Result {
        require(source.length <= 1_000_000) { "Dosya çok büyük" }
        val root = JSONObject(source)
        require(root.optString("format") == "metabol-measurement-import" &&
            root.optInt("schemaVersion") == 1) { "Ölçüm dosyası tanınmadı" }
        val weights = root.getJSONArray("weightMeasurements")
        val bodies = root.getJSONArray("bodyCompositionMeasurements")
        require(weights.length() <= 5000 && bodies.length() <= 5000) { "Çok fazla kayıt" }
        val zone = ZoneId.of("Europe/Istanbul")
        fun timestamp(day: String): Long {
            val date = LocalDate.parse(day)
            require(!date.isAfter(LocalDate.now(zone)) && !date.isBefore(LocalDate.of(2000, 1, 1))) {
                "Geçersiz tarih: $day"
            }
            return date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        }
        val weightPrefs = context.getSharedPreferences("metabol_local", Context.MODE_PRIVATE)
        val bodyPrefs = context.getSharedPreferences("metabol_body", Context.MODE_PRIVATE)
        val existingWeights = JSONArray(weightPrefs.getString("weights", "[]"))
        val existingBodies = JSONArray(bodyPrefs.getString("records", "[]"))
        val weightDates = (0 until existingWeights.length()).map {
            java.time.Instant.ofEpochMilli(existingWeights.getJSONObject(it).getLong("time"))
                .atZone(zone).toLocalDate().toString()
        }.toMutableSet()
        val bodyDates = (0 until existingBodies.length()).map {
            java.time.Instant.ofEpochMilli(existingBodies.getJSONObject(it).getLong("id"))
                .atZone(zone).toLocalDate().toString()
        }.toMutableSet()
        var addedWeights = 0
        var addedBodies = 0
        var skipped = 0
        for (i in 0 until weights.length()) {
            val entry = weights.getJSONObject(i)
            val date = entry.getString("date")
            val time = timestamp(date)
            val kg = entry.getDouble("kg")
            require(kg.isFinite() && kg in 20.0..500.0) { "Geçersiz kilo" }
            if (!weightDates.add(date)) { skipped++; continue }
            existingWeights.put(JSONObject().put("time", time).put("kg", kg))
            addedWeights++
        }
        for (i in 0 until bodies.length()) {
            val entry = bodies.getJSONObject(i)
            val date = entry.getString("date")
            val time = timestamp(date)
            fun optional(name: String, limit: Double): Double? {
                if (!entry.has(name) || entry.isNull(name)) return null
                val value = entry.getDouble(name)
                require(value.isFinite() && value in 0.0..limit) { "Geçersiz ölçüm: $name" }
                return value
            }
            val fat = optional("bodyFatPercent", 75.0)
            val muscle = optional("muscleKg", 300.0)
            val waist = optional("abdomenCm", 300.0)
            if (!bodyDates.add(date)) { skipped++; continue }
            existingBodies.put(JSONObject().put("id", time).put("fat", fat)
                .put("muscle", muscle).put("waist", waist)
                .put("bone", optional("boneKg", 20.0))
                .put("water", optional("waterPercent", 100.0))
                .put("chest", optional("chestCm", 300.0))
                .put("underChest", optional("underChestCm", 300.0))
                .put("metabolicAge", optional("metabolicAge", 120.0))
                .put("visceralFat", optional("visceralFatIndex", 100.0) ?: optional("visceralFat", 100.0)))
            addedBodies++
        }
        // Keep the original import document, including extra body fields and uncertain
        // dates, so data not yet represented in the UI is not discarded.
        val archive = context.getSharedPreferences("metabol_import_archive", Context.MODE_PRIVATE)
        require(weightPrefs.edit().putString("weights", existingWeights.toString()).commit()) { "Kilo kayıtları kaydedilemedi" }
        require(bodyPrefs.edit().putString("records", existingBodies.toString()).commit()) { "Vücut kayıtları kaydedilemedi" }
        require(archive.edit().putString("measurement_source", source).commit()) { "Kaynak dosya arşivlenemedi" }
        return Result(addedWeights, addedBodies, skipped)
    }

    /** Adds estimated daily nutrition totals without replacing days the user already logged. */
    fun importNutrition(context: Context, source: String): Pair<Int, Int> {
        require(source.length <= 1_000_000) { "Dosya çok büyük" }
        val root = JSONObject(source)
        require(root.optString("format") == "metabol-estimated-nutrition-history" &&
            root.optInt("schemaVersion") == 1) { "Beslenme dosyası tanınmadı" }
        val days = root.getJSONArray("dailyNutrition")
        require(days.length() <= 5000) { "Çok fazla gün var" }
        val prefs = context.getSharedPreferences("metabol_nutrition", Context.MODE_PRIVATE)
        val zone = ZoneId.of("Europe/Istanbul")
        val prepared = linkedMapOf<String, String>()
        var skipped = 0
        for (i in 0 until days.length()) {
            val item = days.getJSONObject(i)
            val day = LocalDate.parse(item.getString("date"))
            require(!day.isAfter(LocalDate.now(zone)) && !day.isBefore(LocalDate.of(2000, 1, 1))) {
                "Geçersiz tarih: $day"
            }
            val key = "meals_$day"
            if (prefs.contains(key) || prepared.containsKey(key)) { skipped++; continue }
            val calories = item.getInt("totalCaloriesKcal")
            val protein = item.getDouble("proteinG")
            val carbs = item.getDouble("carbsG")
            val fat = item.getDouble("fatG")
            require(calories in 0..10000 && listOf(protein, carbs, fat).all {
                it.isFinite() && it in 0.0..1000.0
            }) { "Geçersiz beslenme verisi: $day" }
            // The source only contains daily totals, not a verified meal breakdown.
            val meal = JSONObject().put("id", day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
                .put("name", "Tahmini günlük toplam (gerçek öğün kaydı değil)")
                .put("calories", calories).put("protein", protein)
                .put("carbs", carbs).put("fat", fat)
            prepared[key] = JSONArray().put(meal).toString()
        }
        val editor = prefs.edit()
        prepared.forEach { (key, value) -> editor.putString(key, value) }
        require(editor.commit()) { "Beslenme verileri kaydedilemedi" }
        return prepared.size to skipped
    }
}
