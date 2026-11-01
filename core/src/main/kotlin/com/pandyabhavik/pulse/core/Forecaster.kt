package com.pandyabhavik.pulse.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Milestones and projected dates.
 *
 * v1 projects with a constant rate. Real loss slows a little as weight drops (a lighter body burns less),
 * so far-off dates are optimistic; the rate is recomputed every day from fresh data, and once 14+ days of
 * trend exist it is blended with what the scale actually did, which corrects the drift over time.
 */
object Forecaster {
    const val STARTING_GUESS_KG_PER_WEEK = 0.75
    const val MAX_KG_PER_WEEK = 1.5

    /** Days of balance (and trend) data needed before the starting guess is replaced. */
    const val MIN_DAYS = 14
    /** Longest window used to compare the scale trend with logged food. */
    const val SCALE_WINDOW_DAYS = 28
    /** Targets closer than this are merged into one milestone. */
    const val MERGE_KG = 0.5

    private const val EPS = 1e-9

    // Label priority when merging targets that are within MERGE_KG of each other.
    private const val PRI_KG = 0
    private const val PRI_PERCENT = 1
    private const val PRI_UNDER_100 = 2
    private const val PRI_GOAL = 3

    private data class Target(val kg: Double, val label: String, val priority: Int)

    /**
     * The milestone weights and labels for a profile: every 5 kg ("First 5 kg", "10 kg down", ...),
     * 5/10/15/20/25% down ("5% down", ...), "Under 100 kg" when the journey crosses 100 kg, and the goal ("Goal").
     * Only targets strictly between start and goal are kept, plus the goal itself, which is always last.
     * Targets within 0.5 kg of each other merge into one, keeping the more meaningful label
     * (Goal > Under 100 kg > percentage > kg step). Sorted from heaviest to lightest.
     * Percentage targets are rounded to 0.1 kg.
     */
    fun targets(p: Profile): List<Pair<Double, String>> {
        val start = p.startKg
        val goal = p.goalKg
        val between = { kg: Double -> kg < start - EPS && kg > goal + EPS }
        val raw = ArrayList<Target>()
        var k = 1
        while (true) {
            val kg = start - 5.0 * k
            if (kg <= goal + EPS) break
            raw += Target(kg, if (k == 1) "First 5 kg" else "${5 * k} kg down", PRI_KG)
            k++
        }
        for (pct in listOf(5, 10, 15, 20, 25)) {
            val kg = Format.round1(start * (100 - pct) / 100.0)
            if (between(kg)) raw += Target(kg, "$pct% down", PRI_PERCENT)
        }
        if (start > 100.0 && goal < 100.0) raw += Target(100.0, "Under 100 kg", PRI_UNDER_100)

        // Anything within MERGE_KG of the goal folds into the goal.
        val candidates = raw.filter { between(it.kg) && it.kg - goal >= MERGE_KG - EPS }
            .sortedWith(compareByDescending<Target> { it.kg }.thenByDescending { it.priority })

        // Greedy clustering from heaviest down: a target joins the current cluster if it is within
        // MERGE_KG of the cluster's heaviest member. Each cluster keeps its highest-priority target.
        val merged = ArrayList<Target>()
        var cluster = ArrayList<Target>()
        for (t in candidates) {
            if (cluster.isNotEmpty() && cluster.first().kg - t.kg >= MERGE_KG - EPS) {
                merged += cluster.maxBy { it.priority }
                cluster = ArrayList()
            }
            cluster += t
        }
        if (cluster.isNotEmpty()) merged += cluster.maxBy { it.priority }

        return merged.map { it.kg to it.label } + (goal to "Goal")
    }

    /** The forecast as of [asOf], using only days up to and including asOf - 1 for averages (today is incomplete). */
    fun forecast(p: Profile, days: List<DayLog>, trend: List<TrendPoint>, asOf: LocalDate): Forecast {
        val sortedTrend = trend.filter { !it.date.isAfter(asOf) }.sortedBy { it.date }
        val current = sortedTrend.lastOrNull()?.trend ?: p.startKg
        val yesterday = asOf.minusDays(1)

        // One log per date (last wins), only complete days.
        val byDate = days.filter { it.date.isBefore(asOf) }.associateBy { it.date }
        val balances = byDate.values.filter { it.balance != null }.sortedByDescending { it.date }

        var avgDeficit: Int? = null
        var learnedBurn: Int? = null
        val rate: Double
        val source: RateSource

        if (balances.size < MIN_DAYS) {
            rate = STARTING_GUESS_KG_PER_WEEK
            source = RateSource.STARTING_GUESS
        } else {
            val avg = balances.take(MIN_DAYS).map { it.balance!! }.average()
            avgDeficit = avg.roundToInt()
            val deficitRate = avg / Energy.KCAL_PER_KG * 7.0

            val past = sortedTrend.filter { !it.date.isAfter(yesterday) }
            val span = if (past.isEmpty()) 0L else ChronoUnit.DAYS.between(past.first().date, past.last().date)
            if (span >= MIN_DAYS) {
                val end = past.last()
                val window = min(span, SCALE_WINDOW_DAYS.toLong())
                val windowStart = end.date.minusDays(window)
                val startTrend = trendOn(past, windowStart)!!
                val drop = startTrend - end.trend
                val scaleRate = drop / window * 7.0
                val eaten = byDate.values.filter { it.eaten != null && it.date.isAfter(windowStart) && !it.date.isAfter(end.date) }
                if (eaten.isNotEmpty()) {
                    learnedBurn = (eaten.map { it.eaten!! }.average() + drop * Energy.KCAL_PER_KG / window).roundToInt()
                }
                rate = (deficitRate + scaleRate) / 2.0
                source = RateSource.DEFICITS_AND_SCALE
            } else {
                rate = deficitRate
                source = RateSource.DEFICITS
            }
        }

        val stalled = rate <= 0.0
        val finalRate = if (stalled) 0.0 else min(rate, MAX_KG_PER_WEEK)
        val finalSource = if (stalled) RateSource.STALLED else source

        val milestones = targets(p).map { (kg, label) ->
            val reached = sortedTrend.firstOrNull { it.trend <= kg + EPS }?.date
            val projected = if (reached != null || stalled) null else {
                val daysNeeded = ceil((current - kg) / (finalRate / 7.0) - EPS).toLong().coerceAtLeast(1L)
                asOf.plusDays(daysNeeded)
            }
            Milestone(kg, label, reached, projected, isGoal = label == "Goal")
        }
        return Forecast(asOf, current, finalRate, finalSource, avgDeficit, learnedBurn, milestones)
    }

    /** Days the next unreached milestone moved: positive = earlier than [previous] projected. Null if not comparable. */
    fun shiftDays(current: Forecast, previous: Forecast): Int? {
        val next = current.next ?: return null
        val now = next.projected ?: return null
        val before = previous.milestones.firstOrNull { abs(it.kg - next.kg) < 1e-6 }?.projected ?: return null
        return ChronoUnit.DAYS.between(now, before).toInt()
    }

    /**
     * Goal date if Bhavik averaged [deficitPerDay] kcal under from [from] at [trendKg]: a constant
     * deficitPerDay / 7700 kg a day. Null if deficit <= 0. Returns [from] if already at or below goal.
     */
    fun whatIf(p: Profile, trendKg: Double, from: LocalDate, deficitPerDay: Int): LocalDate? {
        if (deficitPerDay <= 0) return null
        val toLose = trendKg - p.goalKg
        if (toLose <= 0.0) return from
        val perDay = deficitPerDay / Energy.KCAL_PER_KG
        return from.plusDays(ceil(toLose / perDay - EPS).toLong())
    }

    /** The trend value on [date]: the last point on or before it, or null. [points] must be sorted by date. */
    internal fun trendOn(points: List<TrendPoint>, date: LocalDate): Double? =
        points.lastOrNull { !it.date.isAfter(date) }?.trend
}
