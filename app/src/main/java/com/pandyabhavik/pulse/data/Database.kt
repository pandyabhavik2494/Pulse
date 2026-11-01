package com.pandyabhavik.pulse.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import com.pandyabhavik.pulse.core.DayLog
import com.pandyabhavik.pulse.core.Meals
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * One calendar day as Health Connect last reported it. Pulse only reads, so every column is a copy of
 * Health Connect data; null means "no data", never zero.
 */
@Entity(tableName = "days")
data class DayEntity(
    /** ISO date (2026-11-24), so ORDER BY date is chronological. */
    @PrimaryKey val date: String,
    val steps: Int? = null,
    val burnedTotal: Int? = null,
    val burnedActive: Int? = null,
    val eaten: Int? = null,
    val proteinG: Int? = null,
    val breakfast: Int = 0,
    val lunch: Int = 0,
    val dinner: Int = 0,
    val snacks: Int = 0,
    val weightKg: Double? = null,
    val sleepMinutes: Int? = null,
) {
    fun toDayLog() = DayLog(
        date = LocalDate.parse(date),
        steps = steps,
        burnedTotal = burnedTotal,
        burnedActive = burnedActive,
        eaten = eaten,
        proteinG = proteinG,
        meals = Meals(breakfast, lunch, dinner, snacks),
        weightKg = weightKg,
        sleepMinutes = sleepMinutes,
    )
}

fun DayLog.toEntity() = DayEntity(
    date = date.toString(),
    steps = steps,
    burnedTotal = burnedTotal,
    burnedActive = burnedActive,
    eaten = eaten,
    proteinG = proteinG,
    breakfast = meals.breakfast,
    lunch = meals.lunch,
    dinner = meals.dinner,
    snacks = meals.snacks,
    weightKg = weightKg,
    sleepMinutes = sleepMinutes,
)

@Dao
interface DayDao {
    @Query("SELECT * FROM days ORDER BY date")
    fun daysFlow(): Flow<List<DayEntity>>

    @Query("SELECT * FROM days ORDER BY date")
    suspend fun days(): List<DayEntity>

    @Query("SELECT * FROM days WHERE date = :date")
    suspend fun day(date: String): DayEntity?

    @Upsert
    suspend fun upsertDays(days: List<DayEntity>)

    /** [from] and [to] are ISO dates, both inclusive. */
    @Query("DELETE FROM days WHERE date >= :from AND date <= :to")
    suspend fun deleteRange(from: String, to: String)

    @Query("DELETE FROM days")
    suspend fun clear()

    /**
     * Swaps in a fresh read of [from]..[to]: a day Health Connect no longer has anything for (data deleted
     * in the source app) disappears instead of keeping its old numbers.
     */
    @Transaction
    suspend fun replaceRange(from: String, to: String, days: List<DayEntity>) {
        deleteRange(from, to)
        upsertDays(days)
    }
}

@Database(entities = [DayEntity::class], version = 1, exportSchema = false)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun dao(): DayDao

    companion object {
        fun create(context: Context, name: String = "pulse.db"): PulseDatabase =
            Room.databaseBuilder(context, PulseDatabase::class.java, name).build()
    }
}
