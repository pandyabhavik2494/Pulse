package com.pandyabhavik.pulse.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
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

        // Sleep: a night counts on the day it ends (Monday night's sleep is Tuesday's), so read from the
        // day before [from]. Awake and out-of-bed stages don't count as sleep.
        val sleep = HashMap<LocalDate, Int>()
        if (READ_SLEEP in granted) part("Sleep", Unit, failed) {
            val sleepRange = TimeRangeFilter.between(from.minusDays(1).atStartOfDay(zone).toInstant(), end)
            readAll(SleepSessionRecord::class, sleepRange).forEach { r ->
                val date = r.endTime.atZone(r.endZoneOffset ?: zone).toLocalDate()
                if (date.isBefore(from) || date.isAfter(to)) return@forEach
                val awake = r.stages
                    .filter { it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE || it.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED }
                    .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                val minutes = (Duration.between(r.startTime, r.endTime).toMinutes() - awake).coerceAtLeast(0)
                sleep[date] = (sleep[date] ?: 0) + minutes.toInt()
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

        fun add(r: NutritionRecord) {
            val e = r.energy?.inKilocalories ?: 0.0
            kcal += e
            r.protein?.let {
                protein += it.inGrams
                hasProtein = true
            }
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
        val READ_HEART_RATE = HealthPermission.getReadPermission(HeartRateRecord::class)
        val READ_RESTING_HEART_RATE = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        const val READ_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        /** The data types. Background reading is asked for too, but isn't needed to sync in the app. */
        val DATA = setOf(
            READ_STEPS, READ_TOTAL_CALORIES, READ_ACTIVE_CALORIES, READ_NUTRITION, READ_WEIGHT, READ_SLEEP,
            READ_HEART_RATE, READ_RESTING_HEART_RATE,
        )
        val ALL = DATA + READ_IN_BACKGROUND
    }
}
