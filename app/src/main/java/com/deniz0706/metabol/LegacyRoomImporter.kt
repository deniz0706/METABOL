package com.deniz0706.metabol

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.LocalDate

/**
 * One-way, repeatable snapshot of legacy preferences into Room.
 * Preferences are NEVER deleted or modified here and remain the live source.
 * Call after a successful build, then verify row counts before switching reads.
 */
internal object LegacyRoomImporter {
    data class Report(val weights: Int, val meals: Int, val journals: Int)

    suspend fun importSnapshot(context: Context): Result<Report> = withContext(Dispatchers.IO) {
        runCatching {
            val weightPrefs = context.getSharedPreferences("metabol_local", Context.MODE_PRIVATE)
            val nutritionPrefs = context.getSharedPreferences("metabol_nutrition", Context.MODE_PRIVATE)
            val weightsJson = JSONArray(weightPrefs.getString("weights", "[]"))
            val weights = (0 until weightsJson.length()).map { i ->
                val row = weightsJson.getJSONObject(i)
                WeightEntity(row.getLong("time"), row.getDouble("kg").also {
                    require(it.isFinite() && it in 20.0..500.0)
                })
            }.distinctBy { it.timestamp }

            val meals = mutableListOf<NutritionEntity>()
            val journal = mutableListOf<DailyJournalEntity>()
            val dayPattern = Regex("\\d{4}-\\d{2}-\\d{2}")
            for ((key, value) in nutritionPrefs.all) {
                val raw = value as? String ?: continue
                when {
                    key.startsWith("meals_") && key.removePrefix("meals_").matches(dayPattern) -> {
                        val day = key.removePrefix("meals_")
                        LocalDate.parse(day)
                        val array = JSONArray(raw)
                        for (i in 0 until array.length()) {
                            val row = array.getJSONObject(i)
                            val calories = row.getInt("calories")
                            val protein = row.getDouble("protein")
                            val carbs = row.getDouble("carbs")
                            val fat = row.getDouble("fat")
                            require(calories in 0..10000 && listOf(protein, carbs, fat)
                                .all { it.isFinite() && it in 0.0..1000.0 })
                            meals += NutritionEntity(row.getLong("id"), day, row.getString("name"),
                                calories, protein, carbs, fat)
                        }
                    }
                    key.startsWith("water_") || key.startsWith("note_") -> {
                        // Journals are combined in a second pass below.
                    }
                }
            }
            val days = nutritionPrefs.all.keys.mapNotNull { key ->
                when {
                    key.startsWith("water_") -> key.removePrefix("water_")
                    key.startsWith("note_") -> key.removePrefix("note_")
                    else -> null
                }
            }.filter { it.matches(dayPattern) && runCatching { LocalDate.parse(it) }.isSuccess }
                .distinct()
            for (day in days) {
                val water = nutritionPrefs.getString("water_$day", "0")?.toIntOrNull() ?: 0
                val note = nutritionPrefs.getString("note_$day", "") ?: ""
                require(water in 0..10000 && note.length <= 2000)
                journal += DailyJournalEntity(day, water, note)
            }

            val db = MetabolDatabase.get(context)
            db.withTransaction {
                val dao = db.dao()
                dao.clearWeights()
                dao.clearNutrition()
                dao.clearJournal()
                dao.putWeights(weights)
                dao.putNutrition(meals)
                journal.forEach { dao.putJournal(it) }
                require(dao.weightCount() == weights.size)
                require(dao.nutritionCount() == meals.size)
            }
            Report(weights.size, meals.size, journal.size)
        }
    }
}
