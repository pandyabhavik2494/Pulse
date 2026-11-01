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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val restingHr: Int? = null,
    val hrAvg: Int? = null,
    val hrMin: Int? = null,
    val hrMax: Int? = null,
    val distanceM: Int? = null,
    val carbsG: Int? = null,
    val fatG: Int? = null,
    val fiberG: Int? = null,
    val sugarG: Int? = null,
    val sodiumMg: Int? = null,
    val waterMl: Int? = null,
    val floors: Int? = null,
    val exerciseMin: Int? = null,
    val bodyFatPct: Double? = null,
    val leanKg: Double? = null,
    val hrvMs: Int? = null,
    val spo2Pct: Double? = null,
    val breathingRate: Double? = null,
    val vo2Max: Double? = null,
    val sleepDeepMin: Int? = null,
    val sleepRemMin: Int? = null,
    val sleepLightMin: Int? = null,
    val sleepAwakeMin: Int? = null,
    val bedTime: String? = null,
    val wakeTime: String? = null,
    val hrLatest: Int? = null,
    val hrLatestAt: String? = null,
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
        restingHr = restingHr,
        hrAvg = hrAvg,
        hrMin = hrMin,
        hrMax = hrMax,
        distanceM = distanceM,
        carbsG = carbsG,
        fatG = fatG,
        fiberG = fiberG,
        sugarG = sugarG,
        sodiumMg = sodiumMg,
        waterMl = waterMl,
        floors = floors,
        exerciseMin = exerciseMin,
        bodyFatPct = bodyFatPct,
        leanKg = leanKg,
        hrvMs = hrvMs,
        spo2Pct = spo2Pct,
        breathingRate = breathingRate,
        vo2Max = vo2Max,
        sleepDeepMin = sleepDeepMin,
        sleepRemMin = sleepRemMin,
        sleepLightMin = sleepLightMin,
        sleepAwakeMin = sleepAwakeMin,
        bedTime = bedTime,
        wakeTime = wakeTime,
        hrLatest = hrLatest,
        hrLatestAt = hrLatestAt,
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
    restingHr = restingHr,
    hrAvg = hrAvg,
    hrMin = hrMin,
    hrMax = hrMax,
    distanceM = distanceM,
    carbsG = carbsG,
    fatG = fatG,
    fiberG = fiberG,
    sugarG = sugarG,
    sodiumMg = sodiumMg,
    waterMl = waterMl,
    floors = floors,
    exerciseMin = exerciseMin,
    bodyFatPct = bodyFatPct,
    leanKg = leanKg,
    hrvMs = hrvMs,
    spo2Pct = spo2Pct,
    breathingRate = breathingRate,
    vo2Max = vo2Max,
    sleepDeepMin = sleepDeepMin,
    sleepRemMin = sleepRemMin,
    sleepLightMin = sleepLightMin,
    sleepAwakeMin = sleepAwakeMin,
    bedTime = bedTime,
    wakeTime = wakeTime,
    hrLatest = hrLatest,
    hrLatestAt = hrLatestAt,
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

@Database(entities = [DayEntity::class], version = 3, exportSchema = false)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun dao(): DayDao

    companion object {
        fun create(context: Context, name: String = "pulse.db"): PulseDatabase =
            Room.databaseBuilder(context, PulseDatabase::class.java, name).addMigrations(V1_TO_V2, V2_TO_V3).build()

        /** Version 3 adds the day's newest heart-rate reading and its time. */
        private val V2_TO_V3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE days ADD COLUMN hrLatest INTEGER")
                db.execSQL("ALTER TABLE days ADD COLUMN hrLatestAt TEXT")
            }
        }

        /** Version 2 adds heart rate, distance and the rest of Health Connect's data. Old days keep their data; the new columns start empty. */
        private val V1_TO_V2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = listOf(
                    "restingHr" to "INTEGER", "hrAvg" to "INTEGER", "hrMin" to "INTEGER", "hrMax" to "INTEGER", "distanceM" to "INTEGER",
                    "carbsG" to "INTEGER",
                    "fatG" to "INTEGER",
                    "fiberG" to "INTEGER",
                    "sugarG" to "INTEGER",
                    "sodiumMg" to "INTEGER",
                    "waterMl" to "INTEGER",
                    "floors" to "INTEGER",
                    "exerciseMin" to "INTEGER",
                    "bodyFatPct" to "REAL",
                    "leanKg" to "REAL",
                    "hrvMs" to "INTEGER",
                    "spo2Pct" to "REAL",
                    "breathingRate" to "REAL",
                    "vo2Max" to "REAL",
                    "sleepDeepMin" to "INTEGER",
                    "sleepRemMin" to "INTEGER",
                    "sleepLightMin" to "INTEGER",
                    "sleepAwakeMin" to "INTEGER",
                    "bedTime" to "TEXT",
                    "wakeTime" to "TEXT",
                )
                for ((c, type) in columns) db.execSQL("ALTER TABLE days ADD COLUMN $c $type")
            }
        }
    }
}
