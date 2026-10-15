package com.deniz0706.metabol

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room foundation. Existing SharedPreferences remain the source of truth until
 * migration, verification and rollback are implemented. Do not delete legacy data.
 */
@Entity(tableName = "weight_entries")
internal data class WeightEntity(
    @PrimaryKey val timestamp: Long,
    val kilograms: Double
)

@Entity(tableName = "nutrition_entries", indices = [Index("day")])
internal data class NutritionEntity(
    @PrimaryKey val id: Long,
    val day: String,
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

@Entity(tableName = "daily_journal")
internal data class DailyJournalEntity(
    @PrimaryKey val day: String,
    val waterMl: Int,
    val note: String
)

@Dao
internal interface MetabolDao {
    @Query("SELECT * FROM weight_entries ORDER BY timestamp")
    suspend fun weights(): List<WeightEntity>

    @Query("SELECT * FROM nutrition_entries WHERE day = :day ORDER BY id")
    suspend fun nutrition(day: String): List<NutritionEntity>

    @Query("SELECT * FROM daily_journal WHERE day = :day LIMIT 1")
    suspend fun journal(day: String): DailyJournalEntity?

    @Query("DELETE FROM weight_entries")
    suspend fun clearWeights()

    @Query("DELETE FROM nutrition_entries")
    suspend fun clearNutrition()

    @Query("DELETE FROM daily_journal")
    suspend fun clearJournal()

    @Query("SELECT COUNT(*) FROM weight_entries")
    suspend fun weightCount(): Int

    @Query("SELECT COUNT(*) FROM nutrition_entries")
    suspend fun nutritionCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putWeights(rows: List<WeightEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putNutrition(rows: List<NutritionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putJournal(row: DailyJournalEntity)
}

@Database(entities = [WeightEntity::class, NutritionEntity::class, DailyJournalEntity::class],
    version = 1, exportSchema = true)
internal abstract class MetabolDatabase : RoomDatabase() {
    abstract fun dao(): MetabolDao

    companion object {
        @Volatile private var instance: MetabolDatabase? = null
        fun get(context: Context): MetabolDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext,
                    MetabolDatabase::class.java, "metabol.db")
                    .build().also { instance = it }
            }
    }
}
