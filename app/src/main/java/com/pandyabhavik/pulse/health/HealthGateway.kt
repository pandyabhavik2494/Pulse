package com.pandyabhavik.pulse.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.pandyabhavik.pulse.core.DayLog
import com.pandyabhavik.pulse.core.Meals
import com.pandyabhavik.pulse.data.HealthStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt
import kotlin.reflect.KClass

/**
 * Reads Health Connect: steps (Fitbit and the phone, de-duplicated by Health Connect's own priority
 * list), calories burned (Fitbit: total and active), food from MyFitnessPal, weight from the scale's app
 * and sleep from the Fitbit.
 *
 * The three daily totals use one aggregateGroupByPeriod call each over the whole range, so the first
 * 90-day read is a handful of calls rather than hundreds.
 */
class HealthGateway(private val context: Context) : HealthSource {
    override fun status(): HealthStatus = when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NEEDS_UPDATE
        else -> HealthStatus.NOT_INSTALLED
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override val permissions: Set<String> = ALL

    override suspend fun granted(): Set<String> =
        if (status() == HealthStatus.AVAILABLE) client.permissionController.getGrantedPermissions() else emptySet()

    override fun installIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
            .setPackage("com.android.vending")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    override fun settingsIntent(): Intent =
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Volatile override var problems: List<String> = emptyList()
        private set

    /**
     * Runs one data type's read. A failure (a revoked permission, Health Connect's rate limit, a bad
     * record from some app) costs only that type: it's logged, noted in [problems], and the rest still syncs.
     */
    private suspend fun <T> part(name: String, empty: T, failed: MutableList<String>, read: suspend () -> T): T = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("HealthGateway", "Reading $name failed", e)
        failed += "$name: ${e.message ?: e.javaClass.simpleName}"
        empty
    }

    override suspend fun check(now: Instant): List<TypeCheck> {
        if (status() != HealthStatus.AVAILABLE) return emptyList()
        val granted = granted()
        val range = TimeRangeFilter.between(now.minus(Duration.ofDays(7)), now)
        val pm = context.packageManager
        fun label(pkg: String): String = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        // When each record was last written to Health Connect: shows whether an app is still syncing.
        fun timeOf(r: Record): Instant? = r.metadata.lastModifiedTime
        val types: List<Triple<String, String, KClass<out Record>>> = listOf(
            Triple("Steps", READ_STEPS, StepsRecord::class),
            Triple("Calories burned", READ_TOTAL_CALORIES, TotalCaloriesBurnedRecord::class),
            Triple("Active calories", READ_ACTIVE_CALORIES, ActiveCaloriesBurnedRecord::class),
            Triple("Food", READ_NUTRITION, NutritionRecord::class),
            Triple("Weight", READ_WEIGHT, WeightRecord::class),
            Triple("Sleep", READ_SLEEP, SleepSessionRecord::class),
            Triple("Heart rate", READ_HEART_RATE, HeartRateRecord::class),
            Triple("Resting heart rate", READ_RESTING_HEART_RATE, RestingHeartRateRecord::class),
            Triple("Distance", READ_DISTANCE, DistanceRecord::class),
            Triple("Floors", READ_FLOORS, FloorsClimbedRecord::class),
            Triple("Exercise", READ_EXERCISE, ExerciseSessionRecord::class),
            Triple("Water", READ_HYDRATION, HydrationRecord::class),
            Triple("Body fat", READ_BODY_FAT, BodyFatRecord::class),
            Triple("Lean mass", READ_LEAN_MASS, LeanBodyMassRecord::class),
            Triple("HRV", READ_HRV, HeartRateVariabilityRmssdRecord::class),
            Triple("Blood oxygen", READ_SPO2, OxygenSaturationRecord::class),
            Triple("Breathing rate", READ_BREATHING, RespiratoryRateRecord::class),
            Triple("VO2 max", READ_VO2_MAX, Vo2MaxRecord::class),
        )
        return types.map { (name, perm, type) ->
            if (perm !in granted) return@map TypeCheck(name, false, 0, emptyList(), null)
            try {
                val recs = client.readRecords(ReadRecordsRequest(type, range, pageSize = 1000)).records
                TypeCheck(
                    name, true, recs.size,
                    recs.map { it.metadata.dataOrigin.packageName }.distinct().map(::label),
                    recs.mapNotNull(::timeOf).maxOrNull(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TypeCheck(name, true, 0, emptyList(), null, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    override suspend fun readDays(from: LocalDate, to: LocalDate, zone: ZoneId): List<DayLog> {
        if (status() != HealthStatus.AVAILABLE || to.isBefore(from)) return emptyList()
        val granted = granted()
        val failed = ArrayList<String>()
        try {
            return readParts(from, to, zone, granted, failed)
        } finally {
            problems = failed
        }
    }

    private suspend fun readParts(from: LocalDate, to: LocalDate, zone: ZoneId, granted: Set<String>, failed: MutableList<String>): List<DayLog> {

        // Daily totals. Health Connect buckets these by each record's own local time.
        val none = emptyMap<LocalDate, Int>()
        val steps = if (READ_STEPS in granted) part("Steps", none, failed) {
            daily(StepsRecord.COUNT_TOTAL, from, to).mapValues { it.value.toInt() }
        } else none
        val total = if (READ_TOTAL_CALORIES in granted) part("Calories burned", none, failed) {
            daily(TotalCaloriesBurnedRecord.ENERGY_TOTAL, from, to).mapValues { it.value.inKilocalories.roundToInt() }
        } else none
        val active = if (READ_ACTIVE_CALORIES in granted) part("Active calories", none, failed) {
            daily(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, from, to).mapValues { it.value.inKilocalories.roundToInt() }
        } else none
        val distance = if (READ_DISTANCE in granted) part("Distance", none, failed) {
            daily(DistanceRecord.DISTANCE_TOTAL, from, to).mapValues { it.value.inMeters.roundToInt() }
        } else none
        val floors = if (READ_FLOORS in granted) part("Floors", none, failed) {
            daily(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL, from, to).mapValues { it.value.roundToInt() }
        } else none
        val exercise = if (READ_EXERCISE in granted) part("Exercise", none, failed) {
            daily(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL, from, to).mapValues { it.value.toMinutes().toInt() }
        } else none
        val water = if (READ_HYDRATION in granted) part("Water", none, failed) {
            daily(HydrationRecord.VOLUME_TOTAL, from, to).mapValues { it.value.inMilliliters.roundToInt() }
        } else none
        val hrAvg = if (READ_HEART_RATE in granted) part("Heart rate", none, failed) {
            daily(HeartRateRecord.BPM_AVG, from, to).mapValues { it.value.toInt() }
        } else none
        val hrMin = if (READ_HEART_RATE in granted) part("Heart rate", none, failed) {
            daily(HeartRateRecord.BPM_MIN, from, to).mapValues { it.value.toInt() }
        } else none
        val hrMax = if (READ_HEART_RATE in granted) part("Heart rate", none, failed) {
            daily(HeartRateRecord.BPM_MAX, from, to).mapValues { it.value.toInt() }
        } else none
        val restingHr = if (READ_RESTING_HEART_RATE in granted) part("Resting heart rate", none, failed) {
            daily(RestingHeartRateRecord.BPM_AVG, from, to).mapValues { it.value.toInt() }
        } else none

        val start = from.atStartOfDay(zone).toInstant()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant()
        val range = TimeRangeFilter.between(start, end)

        // Food: one entry per logged item; kcal and protein summed per day, kcal also per meal.
        val food = HashMap<LocalDate, FoodDay>()
        if (READ_NUTRITION in granted) part("Food", Unit, failed) {
            readAll(NutritionRecord::class, range)
                .filter { it.metadata.dataOrigin.packageName != context.packageName }
                .forEach { r ->
                    val date = r.startTime.atZone(r.startZoneOffset ?: zone).toLocalDate()
                    if (date.isBefore(from) || date.isAfter(to)) return@forEach
                    food.getOrPut(date) { FoodDay() }.add(r)
                }
        }

        // Weight: the latest weigh-in of each day.
        val weight = HashMap<LocalDate, Double>()
        if (READ_WEIGHT in granted) part("Weight", Unit, failed) {
            readAll(WeightRecord::class, range)
                .groupBy { it.time.atZone(it.zoneOffset ?: zone).toLocalDate() }
                .forEach { (date, records) ->
                    if (date.isBefore(from) || date.isAfter(to)) return@forEach
                    records.maxByOrNull { it.time }?.let { weight[date] = it.weight.inKilograms }
                }
        }

        // Body composition and vitals: one value per day (the latest for the scale, the average for vitals).
        val noneD = emptyMap<LocalDate, Double>()
        val bodyFat = if (READ_BODY_FAT in granted) part("Body fat", noneD, failed) {
            perDay(readAll(BodyFatRecord::class, range), zone, from, to, latest = true, { it.time }, { it.zoneOffset }) { it.percentage.value }
        } else noneD
        val lean = if (READ_LEAN_MASS in granted) part("Lean mass", noneD, failed) {
            perDay(readAll(LeanBodyMassRecord::class, range), zone, from, to, latest = true, { it.time }, { it.zoneOffset }) { it.mass.inKilograms }
        } else noneD
        val hrv = if (READ_HRV in granted) part("HRV", noneD, failed) {
            perDay(readAll(HeartRateVariabilityRmssdRecord::class, range), zone, from, to, latest = false, { it.time }, { it.zoneOffset }) { it.heartRateVariabilityMillis }
        } else noneD
        val spo2 = if (READ_SPO2 in granted) part("Blood oxygen", noneD, failed) {
            perDay(readAll(OxygenSaturationRecord::class, range), zone, from, to, latest = false, { it.time }, { it.zoneOffset }) { it.percentage.value }
        } else noneD
        val breathing = if (READ_BREATHING in granted) part("Breathing rate", noneD, failed) {
            perDay(readAll(RespiratoryRateRecord::class, range), zone, from, to, latest = false, { it.time }, { it.zoneOffset }) { it.rate }
        } else noneD
        val vo2 = if (READ_VO2_MAX in granted) part("VO2 max", noneD, failed) {
            perDay(readAll(Vo2MaxRecord::class, range), zone, from, to, latest = true, { it.time }, { it.zoneOffset }) { it.vo2MillilitersPerMinuteKilogram }
        } else noneD

        // Sleep: a night counts on the day it ends (Monday night's sleep is Tuesday's), so read from the
        // day before [from]. Awake and out-of-bed stages don't count as sleep.
        val sleep = HashMap<LocalDate, Int>()
        val stages = HashMap<LocalDate, IntArray>() // deep, REM, light, awake (minutes)
        val longest = HashMap<LocalDate, SleepSessionRecord>()
        if (READ_SLEEP in granted) part("Sleep", Unit, failed) {
            val sleepRange = TimeRangeFilter.between(from.minusDays(1).atStartOfDay(zone).toInstant(), end)
            readAll(SleepSessionRecord::class, sleepRange).forEach { r ->
                val date = r.endTime.atZone(r.endZoneOffset ?: zone).toLocalDate()
                if (date.isBefore(from) || date.isAfter(to)) return@forEach
                fun mins(vararg types: Int) = r.stages.filter { it.stage in types }
                    .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
                val awake = mins(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)
                val minutes = (Duration.between(r.startTime, r.endTime).toMinutes() - awake).coerceAtLeast(0)
                sleep[date] = (sleep[date] ?: 0) + minutes.toInt()
                val st = stages.getOrPut(date) { IntArray(4) }
                st[0] += mins(SleepSessionRecord.STAGE_TYPE_DEEP)
                st[1] += mins(SleepSessionRecord.STAGE_TYPE_REM)
                st[2] += mins(SleepSessionRecord.STAGE_TYPE_LIGHT)
                st[3] += awake
                val prev = longest[date]
                if (prev == null || Duration.between(r.startTime, r.endTime) > Duration.between(prev.startTime, prev.endTime)) longest[date] = r
            }
        }

        val out = ArrayList<DayLog>()
        var d = from
        while (!d.isAfter(to)) {
            val f = food[d]
            val log = DayLog(
                date = d,
                steps = steps[d],
                burnedTotal = total[d],
                burnedActive = active[d],
                eaten = f?.kcal?.roundToInt(),
                proteinG = f?.let { if (it.hasProtein) it.protein.roundToInt() else null },
                meals = f?.meals() ?: Meals(),
                weightKg = weight[d],
                sleepMinutes = sleep[d],
                restingHr = restingHr[d],
                hrAvg = hrAvg[d],
                hrMin = hrMin[d],
                hrMax = hrMax[d],
                distanceM = distance[d],
                carbsG = f?.carbs?.roundToInt(),
                fatG = f?.fat?.roundToInt(),
                fiberG = f?.fiber?.roundToInt(),
                sugarG = f?.sugar?.roundToInt(),
                sodiumMg = f?.sodium?.roundToInt(),
                waterMl = water[d],
                floors = floors[d],
                exerciseMin = exercise[d],
                bodyFatPct = bodyFat[d],
                leanKg = lean[d],
                hrvMs = hrv[d]?.roundToInt(),
                spo2Pct = spo2[d],
                breathingRate = breathing[d],
                vo2Max = vo2[d],
                sleepDeepMin = stages[d]?.get(0)?.takeIf { it > 0 },
                sleepRemMin = stages[d]?.get(1)?.takeIf { it > 0 },
                sleepLightMin = stages[d]?.get(2)?.takeIf { it > 0 },
                sleepAwakeMin = stages[d]?.let { if (it[0] + it[1] + it[2] > 0) it[3] else null },
                bedTime = longest[d]?.let { clock(it.startTime, it.startZoneOffset, zone) },
                wakeTime = longest[d]?.let { clock(it.endTime, it.endZoneOffset, zone) },
            )
            // Dates with nothing at all are left out; the repository treats a missing date as "no data".
            if (log != DayLog(d)) out += log
            d = d.plusDays(1)
        }
        return out
    }

    /** One value per local day in [from]..[to] for [metric]; a day no app wrote anything for is left out. */
    private suspend fun <T : Any> daily(metric: AggregateMetric<T>, from: LocalDate, to: LocalDate): Map<LocalDate, T> {
        val range = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
        val groups = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(metrics = setOf(metric), timeRangeFilter = range, timeRangeSlicer = Period.ofDays(1)),
        )
        val out = HashMap<LocalDate, T>()
        groups.forEach { g ->
            val value = g.result[metric] ?: return@forEach
            // No data origin means Health Connect made the number up itself (it fills total calories with an
            // estimated basal rate). Pulse makes its own estimate from the profile instead, so skip it.
            if (g.result.dataOrigins.isEmpty()) return@forEach
            out[g.startTime.toLocalDate()] = value
        }
        return out
    }

    /** "23:41" for [t] in the record's own offset (or [zone]). */
    private fun clock(t: Instant, offset: ZoneOffset?, zone: ZoneId): String {
        val lt = t.atZone(offset ?: zone).toLocalTime()
        return String.format(java.util.Locale.US, "%02d:%02d", lt.hour, lt.minute)
    }

    /** One value per day from point-in-time records: the latest of the day, or the day's average. */
    private fun <T> perDay(
        records: List<T>,
        zone: ZoneId,
        from: LocalDate,
        to: LocalDate,
        latest: Boolean,
        time: (T) -> Instant,
        offset: (T) -> ZoneOffset?,
        value: (T) -> Double,
    ): Map<LocalDate, Double> = records
        .groupBy { time(it).atZone(offset(it) ?: zone).toLocalDate() }
        .filterKeys { !it.isBefore(from) && !it.isAfter(to) }
        .mapValues { (_, rs) -> if (latest) value(rs.maxBy(time)) else rs.map(value).average() }

    private suspend fun <T : Record> readAll(type: KClass<T>, range: TimeRangeFilter): List<T> {
        val out = ArrayList<T>()
        var token: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, range, pageSize = 1000, pageToken = token))
            out += response.records
            token = response.pageToken
        } while (!token.isNullOrEmpty())
        return out
    }

    /** Running totals for one day of food. */
    private class FoodDay {
        var kcal = 0.0
        var protein = 0.0
        var hasProtein = false
        val perMeal = DoubleArray(4)
        // Carbs, fat, fibre, sugar (g) and sodium (mg); null until some entry carries it.
        var carbs: Double? = null
        var fat: Double? = null
        var fiber: Double? = null
        var sugar: Double? = null
        var sodium: Double? = null

        private fun plus(a: Double?, b: Double?): Double? = if (b == null) a else (a ?: 0.0) + b

        fun add(r: NutritionRecord) {
            val e = r.energy?.inKilocalories ?: 0.0
            kcal += e
            r.protein?.let {
                protein += it.inGrams
                hasProtein = true
            }
            carbs = plus(carbs, r.totalCarbohydrate?.inGrams)
            fat = plus(fat, r.totalFat?.inGrams)
            fiber = plus(fiber, r.dietaryFiber?.inGrams)
            sugar = plus(sugar, r.sugar?.inGrams)
            sodium = plus(sodium, r.sodium?.inMilligrams)
            val slot = when (r.mealType) {
                MealType.MEAL_TYPE_BREAKFAST -> 0
                MealType.MEAL_TYPE_LUNCH -> 1
                MealType.MEAL_TYPE_DINNER -> 2
                // Snacks, and food with no meal type.
                else -> 3
            }
            perMeal[slot] += e
        }

        fun meals() = Meals(
            breakfast = perMeal[0].roundToInt(),
            lunch = perMeal[1].roundToInt(),
            dinner = perMeal[2].roundToInt(),
            snacks = perMeal[3].roundToInt(),
        )
    }

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"

        val READ_STEPS = HealthPermission.getReadPermission(StepsRecord::class)
        val READ_TOTAL_CALORIES = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        val READ_ACTIVE_CALORIES = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        val READ_NUTRITION = HealthPermission.getReadPermission(NutritionRecord::class)
        val READ_WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)
        val READ_SLEEP = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val READ_DISTANCE = HealthPermission.getReadPermission(DistanceRecord::class)
        val READ_FLOORS = HealthPermission.getReadPermission(FloorsClimbedRecord::class)
        val READ_EXERCISE = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        val READ_HYDRATION = HealthPermission.getReadPermission(HydrationRecord::class)
        val READ_BODY_FAT = HealthPermission.getReadPermission(BodyFatRecord::class)
        val READ_LEAN_MASS = HealthPermission.getReadPermission(LeanBodyMassRecord::class)
        val READ_HRV = HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
        val READ_SPO2 = HealthPermission.getReadPermission(OxygenSaturationRecord::class)
        val READ_BREATHING = HealthPermission.getReadPermission(RespiratoryRateRecord::class)
        val READ_VO2_MAX = HealthPermission.getReadPermission(Vo2MaxRecord::class)
        val READ_HEART_RATE = HealthPermission.getReadPermission(HeartRateRecord::class)
        val READ_RESTING_HEART_RATE = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        const val READ_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        /** Lets Pulse read data from before it was first given access (otherwise Health Connect stops at 30 days). */
        const val READ_HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"

        /** The data types. Background reading is asked for too, but isn't needed to sync in the app. */
        val DATA = setOf(
            READ_STEPS, READ_TOTAL_CALORIES, READ_ACTIVE_CALORIES, READ_NUTRITION, READ_WEIGHT, READ_SLEEP,
            READ_HEART_RATE, READ_RESTING_HEART_RATE, READ_DISTANCE, READ_FLOORS, READ_EXERCISE, READ_HYDRATION,
            READ_BODY_FAT, READ_LEAN_MASS, READ_HRV, READ_SPO2, READ_BREATHING, READ_VO2_MAX,
        )
        val ALL = DATA + READ_IN_BACKGROUND + READ_HISTORY
    }
}
