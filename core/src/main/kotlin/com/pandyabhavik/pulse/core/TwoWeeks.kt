package com.pandyabhavik.pulse.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Weight in two-week steps: where the last 7 days of eating take the weight in two weeks (gain or loss),
 * and what the scale trend actually did in the last few two-week stretches.
 *
 * There's always an answer. With little data it's an early estimate and says so; each day of food and
 * each weigh-in makes it sharper.
 */
object TwoWeeks {
    const val DAYS = 14

    /** How many complete days of food the outlook averages (the latest ones). */
    const val FOOD_DAYS = 7

    /** How far back to look for food days and weigh-ins. */
    const val LOOKBACK_DAYS = 28L

    /** What the outlook is based on, from most to least reliable. */
    enum class Basis {
        /** Food logs, cross-checked against what the scale did. */
        FOOD_AND_SCALE,
        /** Food logs only (no scale trend long enough yet). */
        FOOD,
        /** Weigh-ins only (no complete day of food yet). */
        SCALE,
        /** Only today so far: eaten vs burned up to now. */
        TODAY,
        /** No food or weigh-ins yet: the plan's starting pace. */
        STARTING_GUESS,
    }

    data class Outlook(
        val fromKg: Double,
        /** Negative = loss, positive = gain. */
        val changeKg: Double,
        val basis: Basis,
        /** Complete days of food behind it (0..7). */
        val foodDays: Int,
        /** Average balance of those days, kcal a day (positive = burned more than eaten). */
        val avgBalance: Int?,
    ) {
        val toKg: Double get() = fromKg + changeKg

        /** Fewer than a full week of food: say it's early. */
        val early: Boolean get() = basis != Basis.FOOD_AND_SCALE && !(basis == Basis.FOOD && foodDays >= FOOD_DAYS)
    }

    /** Kg change over two weeks at [balance] kcal a day (positive = burned more than eaten). */
    fun changeFor(balance: Double): Double = -balance * DAYS / Energy.KCAL_PER_KG

    /** Kept for callers that have the 7-day average already. */
    fun outlookKg(avg7Balance: Int?): Double? = avg7Balance?.let { changeFor(it.toDouble()) }

    /**
     * The outlook from every stored day ([days] may reach back before the start date, which helps early on).
     * [p] gives the starting weight when there's no weigh-in at all.
     */
    fun outlook(p: Profile, days: List<DayLog>, today: LocalDate): Outlook {
        val past = days.filter { it.date.isBefore(today) && !it.date.isBefore(today.minusDays(LOOKBACK_DAYS)) }
        val food = past.filter { it.balance != null }.sortedByDescending { it.date }.take(FOOD_DAYS)
        val trend = Trend.of(days, days.minOfOrNull { it.date } ?: today, today)
        val fromKg = trend.lastOrNull()?.trend ?: days.filter { it.weightKg != null }.maxByOrNull { it.date }?.weightKg ?: p.startKg

        val foodChange = if (food.isEmpty()) null else changeFor(food.map { it.balance!! }.average())
        val scaleChange = scaleRatePerDay(days, today)?.let { it * DAYS }
        val avg = if (food.isEmpty()) null else food.map { it.balance!! }.average().roundToInt()

        return when {
            foodChange != null && scaleChange != null && food.size >= 3 ->
                // Eating moves first, so it leads; the scale keeps it honest.
                Outlook(fromKg, (2 * foodChange + scaleChange) / 3, Basis.FOOD_AND_SCALE, food.size, avg)
            foodChange != null -> Outlook(fromKg, foodChange, Basis.FOOD, food.size, avg)
            scaleChange != null -> Outlook(fromKg, scaleChange, Basis.SCALE, 0, null)
            else -> {
                val t = days.firstOrNull { it.date == today }?.balance
                if (t != null) Outlook(fromKg, changeFor(t.toDouble()), Basis.TODAY, 0, t)
                else Outlook(fromKg, -Forecaster.STARTING_GUESS_KG_PER_WEEK * DAYS / 7.0, Basis.STARTING_GUESS, 0, null)
            }
        }
    }

    /**
     * Kg a day from the raw weigh-ins of the last 4 weeks (least-squares line), or null with fewer than
     * 3 weigh-ins or under a week between the first and last.
     */
    fun scaleRatePerDay(days: List<DayLog>, today: LocalDate): Double? {
        val from = today.minusDays(LOOKBACK_DAYS)
        val pts = days.filter { it.weightKg != null && !it.date.isBefore(from) && !it.date.isAfter(today) }
            .map { ChronoUnit.DAYS.between(from, it.date).toDouble() to it.weightKg!! }
        if (pts.size < 3) return null
        if (pts.maxOf { it.first } - pts.minOf { it.first } < 7) return null
        val mx = pts.map { it.first }.average()
        val my = pts.map { it.second }.average()
        val sxx = pts.sumOf { (x, _) -> (x - mx) * (x - mx) }
        if (sxx == 0.0) return null
        return pts.sumOf { (x, y) -> (x - mx) * (y - my) } / sxx
    }

    /** One stretch of the trend. [changeKg] negative = lost. [days] is 14 except for a first, shorter one. */
    data class Period(val start: LocalDate, val end: LocalDate, val changeKg: Double) {
        val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt()
    }

    /**
     * The last [count] two-week stretches ending today, newest first, on the smoothed trend of every
     * weigh-in in [days]. When the weigh-ins don't reach back two weeks yet, one shorter stretch from the
     * first weigh-in is given instead, so there's a trend from the second weigh-in on.
     */
    fun periods(days: List<DayLog>, today: LocalDate, count: Int = 3): List<Period> {
        val first = days.filter { it.weightKg != null }.minOfOrNull { it.date } ?: return emptyList()
        val trend = Trend.of(days, first, today)
        if (trend.isEmpty()) return emptyList()
        fun at(d: LocalDate): Double? = trend.lastOrNull { !it.date.isAfter(d) }?.trend
        val out = mutableListOf<Period>()
        var end = today
        repeat(count) {
            val start = end.minusDays(DAYS.toLong())
            if (start.isBefore(first)) {
                // A shorter first stretch, from the first weigh-in, when nothing longer fits yet.
                if (out.isEmpty() && end.isAfter(first) && trend.count { it.raw != null } >= 2) {
                    out += Period(first, end, at(end)!! - at(first)!!)
                }
                return out
            }
            out += Period(start, end, at(end)!! - at(start)!!)
            end = start
        }
        return out
    }
}
