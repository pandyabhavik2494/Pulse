package com.pandyabhavik.pulse.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/** Streaks, wins and all motivational copy. Plain English, no emoji, never guilt. */
object Motivation {
    private const val EPS = 1e-9

    /** By 8 PM a few hours of resting burn are still to come (about 300 kcal), so keeping this much
     *  under now still ends the day with a solid deficit of roughly 500. Used for the snack-room line. */
    const val EVENING_KEEP_KCAL = 200

    /**
     * Forgiving streak of "under" days ending yesterday (and including today if today is already under).
     * A day over does NOT break it if the 7-day average balance ending that day is still under.
     * A day with no food logged is skipped (neither extends nor breaks). Over days that are forgiven don't add.
     * A balance of exactly 0 counts as over (not under). Today never breaks the streak: it isn't finished.
     */
    fun streak(days: List<DayLog>, today: LocalDate): Int {
        val byDate = days.associateBy { it.date }
        val earliest = byDate.keys.minOrNull() ?: return 0
        var count = 0
        val todayBalance = byDate[today]?.balance
        if (todayBalance != null && todayBalance > 0) count++
        var d = today.minusDays(1)
        while (!d.isBefore(earliest)) {
            val b = byDate[d]?.balance
            if (b != null) {
                if (b > 0) count++
                else {
                    val avg = avgBalance(byDate, d, 7)
                    if (avg == null || avg <= 0.0) break
                }
            }
            d = d.minusDays(1)
        }
        return count
    }

    /** Average daily balance over the 7 days before [today] that have a balance; null if none. */
    fun avg7(days: List<DayLog>, today: LocalDate): Int? =
        avgBalance(days.associateBy { it.date }, today.minusDays(1), 7)?.roundToInt()

    /** Mean balance of the [n] calendar days ending at [end] (inclusive) that have one. */
    private fun avgBalance(byDate: Map<LocalDate, DayLog>, end: LocalDate, n: Int): Double? {
        val values = (0 until n).mapNotNull { byDate[end.minusDays(it.toLong())]?.balance }
        return if (values.isEmpty()) null else values.average()
    }

    /** A small win every 1 kg below the start weight, measured on trend. */
    fun winsDone(p: Profile, trendKg: Double): Int =
        floor(p.startKg - trendKg + EPS).toInt().coerceIn(0, winsTotal(p))

    fun winsTotal(p: Profile): Int = floor(p.startKg - p.goalKg + EPS).toInt().coerceAtLeast(0)

    /** kg left to the next 1 kg win (e.g. 0.6), rounded to 0.1 and at least 0.1. 0.0 once every win is done. */
    fun toNextWin(p: Profile, trendKg: Double): Double {
        val done = winsDone(p, trendKg)
        if (done >= winsTotal(p)) return 0.0
        val left = (done + 1) - (p.startKg - trendKg)
        return Format.round1(left).coerceAtLeast(0.1)
    }

    /** kg -> comparison, heaviest last. [tangible] picks the nearest entry at or below the amount. */
    internal val TANGIBLES: List<Pair<Double, String>> = listOf(
        0.25 to "a block of butter",
        0.5 to "a tub of butter",
        1.0 to "a bag of sugar",
        2.0 to "two big water bottles",
        3.0 to "a house cat",
        3.5 to "a newborn baby",
        5.0 to "a bowling ball",
        7.0 to "a big watermelon",
        10.0 to "a car tyre",
        15.0 to "a full suitcase",
        20.0 to "a big bag of dog food",
        25.0 to "a sack of potatoes",
        30.0 to "a golden retriever",
        35.0 to "a sack of potatoes and a car tyre",
        45.0 to "two sacks of potatoes",
    )

    /** A physical comparison for [kgLost], e.g. 2.0 -> "two big water bottles". Never empty for kgLost >= 0.5 (or 0.25). Empty below that. */
    fun tangible(kgLost: Double): String =
        TANGIBLES.lastOrNull { it.first <= kgLost + EPS }?.second ?: ""

    /** The calm line for an over day, e.g. "One day over doesn't move the trend much. Your 7-day average is still 410 kcal under." */
    fun spillLine(overBy: Int, avg7: Int?): String {
        val first = "One day of eating more than you burned doesn't move the trend much."
        return when {
            avg7 == null -> "$first Tomorrow is a fresh start."
            avg7 > 0 -> "$first Over the last 7 days you still burned ${Format.int(avg7)} kcal a day more than you ate."
            avg7 == 0 -> "$first Your last 7 days are right at even, so one lighter day tips it back."
            else -> "$first A couple of lighter days will bring your last 7 days back to burning more than you eat."
        }
    }

    /**
     * Morning notification after the weigh-in syncs: (title, body).
     * e.g. ("Down 0.2 kg on trend", "117.6 kg. 115 kg is still on track for 17 Dec.").
     * [today] only decides whether dates show a year.
     */
    fun morningNudge(
        trendChangeKg: Double,
        trendKg: Double,
        next: Milestone?,
        shift: Int?,
        today: LocalDate = LocalDate.now(),
    ): Pair<String, String> {
        val change = Format.round1(trendChangeKg)
        val title = when {
            change < 0 -> "Down ${Format.kg(-change)} kg on trend"
            change > 0 -> "Up ${Format.kg(change)} kg on trend"
            else -> "Steady on trend"
        }
        val sb = StringBuilder("${Format.kg(trendKg)} kg.")
        if (change > 0) sb.append(" Normal day-to-day swing.")
        val projected = next?.projected
        when {
            next == null -> sb.append(" You're at your goal. Every milestone is done.")
            projected == null -> sb.append(" Keep logging and the date for ${Format.kg(next.kg)} kg will come back.")
            shift != null && shift > 0 ->
                sb.append(" ${Format.kg(next.kg)} kg moved ${Format.days(shift.toLong())} earlier, to ${Format.date(projected, today)}.")
            shift != null && shift < 0 ->
                sb.append(" ${Format.kg(next.kg)} kg is now set for ${Format.date(projected, today)}.")
            else -> sb.append(" ${Format.kg(next.kg)} kg is still on track for ${Format.date(projected, today)}.")
        }
        return title to sb.toString()
    }

    /**
     * Evening (8 PM) notification: (title, body). balance = so far today (positive under).
     * Snack room = balance - [EVENING_KEEP_KCAL], rounded down to 50 kcal, shown when at least 100.
     */
    fun eveningNudge(balance: Int?, streak: Int): Pair<String, String> {
        if (balance == null) {
            return "Log today's food?" to
                "Nothing in MyFitnessPal yet today. Log when you can and Pulse will show your balance."
        }
        if (balance > 0) {
            val title = if (streak >= 2) "Burned ${Format.int(balance)} kcal more than eaten, day $streak of your streak"
            else "Burned ${Format.int(balance)} kcal more than eaten so far"
            val room = (balance - EVENING_KEEP_KCAL) / 50 * 50
            val body = if (room >= 100) "You have room for a ${Format.int(room)} kcal snack and still keep a solid deficit."
            else "You've burned more than you've eaten today. A calm evening keeps it that way."
            return title to body
        }
        val title = if (balance == 0) "Right at even so far" else "Eaten ${Format.int(-balance)} kcal more than burned so far"
        val body = if (streak >= 1) "One day doesn't move the trend much, and your streak is still alive. Tomorrow is a fresh start."
        else "One day doesn't move the trend much. Tomorrow is a fresh start."
        return title to body
    }

    /**
     * One sentence for Journey, e.g. "A strong week moved 115 kg 3 days earlier, to 17 Dec." Null if nothing to say
     * (no next milestone, no projected date, or no shift). [today] only decides whether dates show a year.
     */
    fun journeyLine(next: Milestone?, shift: Int?, today: LocalDate = LocalDate.now()): String? {
        if (next == null || shift == null || shift == 0) return null
        val projected = next.projected ?: return null
        val what = if (next.isGoal) "your ${Format.kg(next.kg)} kg goal" else "${Format.kg(next.kg)} kg"
        val date = Format.date(projected, today)
        return when {
            shift >= 3 -> "A strong week moved $what ${Format.days(shift.toLong())} earlier, to $date."
            shift > 0 -> "Recent days moved $what ${Format.days(shift.toLong())} earlier, to $date."
            else -> Format.capitalized("$what is now set for $date, ${Format.days(-shift.toLong())} later. A steady week pulls it back.")
        }
    }

    /**
     * The Monday recap of last week (Monday..Sunday), or null if [today] isn't a Monday or Tuesday,
     * last week ended before the profile's start week, or there's no data for last week.
     * Week 1 = the Monday-to-Sunday week containing profile.startDate.
     * Focus tip: the weekday that was over most often in the last 4 weeks (at least twice), else a steps
     * tip when steps averaged under the step goal, else a protein tip.
     */
    fun weeklyRecap(p: Profile, days: List<DayLog>, trend: List<TrendPoint>, today: LocalDate): WeeklyRecap? {
        if (today.dayOfWeek != DayOfWeek.MONDAY && today.dayOfWeek != DayOfWeek.TUESDAY) return null
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        val weekEnd = weekStart.plusDays(6)
        val firstMonday = p.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekNumber = (ChronoUnit.WEEKS.between(firstMonday, weekStart) + 1).toInt()
        if (weekStart.isBefore(firstMonday)) return null

        val byDate = days.associateBy { it.date }
        val week = (0L..6L).mapNotNull { byDate[weekStart.plusDays(it)] }
        val hasData = week.any { it.steps != null || it.eaten != null || it.burnedTotal != null || it.weightKg != null }
        if (!hasData) return null

        val sortedTrend = trend.sortedBy { it.date }
        val change = weekChange(sortedTrend, weekStart)
        val logged = week.filter { it.eaten != null }
        val withBalance = week.filter { it.balance != null }
        val daysUnder = withBalance.count { it.balance!! > 0 }
        val best = withBalance.filter { it.balance!! > 0 }.maxByOrNull { it.balance!! }
        val totalSteps = week.sumOf { it.steps ?: 0 }

        return WeeklyRecap(
            weekNumber = weekNumber,
            weekStart = weekStart,
            trendChangeKg = Format.round1(change ?: 0.0),
            daysUnder = daysUnder,
            daysLogged = logged.size,
            totalSteps = totalSteps,
            bestDay = best?.date,
            bestDayBalance = best?.balance,
            focusDaysUnder = minOf(7, daysUnder + 1),
            focusTip = focusTip(p, byDate, week, weekEnd, firstMonday),
            headline = headline(sortedTrend, change, weekNumber, firstMonday),
        )
    }

    /** Trend at the end of the week minus trend just before it (or the week's first trend point). */
    private fun weekChange(trend: List<TrendPoint>, weekStart: LocalDate): Double? {
        val weekEnd = weekStart.plusDays(6)
        val end = Forecaster.trendOn(trend, weekEnd) ?: return null
        val before = Forecaster.trendOn(trend, weekStart.minusDays(1))
            ?: trend.firstOrNull { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }?.trend
            ?: return null
        return end - before
    }

    private fun headline(trend: List<TrendPoint>, change: Double?, weekNumber: Int, firstMonday: LocalDate): String {
        val c = change ?: 0.0
        if (c > -0.05 && c < 0.05) return "A steady week. The trend held, and that's fine."
        if (c >= 0.05) return "The trend rose ${Format.kg(c)} kg. One week is noise, and this one is a fresh start."
        val earlier = (1 until weekNumber).map { weekChange(trend, firstMonday.plusWeeks((it - 1).toLong())) }
        var run = 1
        for (w in earlier.reversed()) if (w != null && w <= -0.05) run++ else break
        val best = earlier.any { it != null && it <= -0.05 } && earlier.all { it == null || c < it - EPS }
        return when {
            best && run >= 2 -> "Your best week yet, and ${Format.word(run)} weeks in a row of losing."
            best -> "Your best week yet: down ${Format.kg(-c)} kg on trend."
            run >= 2 -> "${Format.capitalized(Format.word(run))} weeks in a row of losing. Steady wins this."
            else -> "Down ${Format.kg(-c)} kg on trend. A good week."
        }
    }

    private fun focusTip(
        p: Profile,
        byDate: Map<LocalDate, DayLog>,
        week: List<DayLog>,
        weekEnd: LocalDate,
        firstMonday: LocalDate,
    ): String {
        val overDays = (0L until 28L).map { weekEnd.minusDays(it) }
            .filter { !it.isBefore(firstMonday) }
            .mapNotNull { byDate[it] }
            .filter { (it.balance ?: 0) < 0 }
        val worst = overDays.groupBy { it.date.dayOfWeek }
            .maxWithOrNull(compareBy<Map.Entry<DayOfWeek, List<DayLog>>> { it.value.size }.thenBy { e -> e.value.maxOf { it.date } })
        if (worst != null && worst.value.size >= 2) {
            val day = worst.key.getDisplayName(TextStyle.FULL, Locale.US)
            val before = worst.key.minus(1).getDisplayName(TextStyle.FULL, Locale.US)
            val times = if (worst.value.size == 2) "twice" else "${Format.word(worst.value.size)} times"
            val meals = worst.value.map { it.meals }
            val meal = listOf(
                "breakfast" to meals.sumOf { it.breakfast },
                "lunch" to meals.sumOf { it.lunch },
                "dinner" to meals.sumOf { it.dinner },
                "snacks" to meals.sumOf { it.snacks },
            ).filter { it.second > 0 }.maxByOrNull { it.second }?.first ?: "dinner"
            return "${day}s were the days you ate more than you burned $times. Plan $day $meal by $before night."
        }
        val steps = week.mapNotNull { it.steps }
        if (steps.isNotEmpty() && steps.average() < p.stepGoal) {
            return "Your steps averaged ${Format.int(steps.average().roundToInt())} a day. " +
                "A 15-minute walk after dinner closes a good part of the gap to ${Format.int(p.stepGoal)}."
        }
        return "Put protein in every meal this week. It keeps you full on fewer calories."
    }

    /** Ring style unlocked after [milestonesReached] milestones (0 = MINT); cycles after the last style. */
    fun ringStyle(milestonesReached: Int): RingStyle {
        val all = RingStyle.values()
        return if (milestonesReached <= 0) all[0] else all[milestonesReached % all.size]
    }

    /** Celebration copy for a reached milestone, e.g. "2 kg gone in 18 days. That's two big water bottles you no longer carry up every flight of stairs." */
    fun celebrationLine(p: Profile, m: Milestone, daysTaken: Long): String {
        val lost = p.startKg - m.kg
        val sb = StringBuilder()
        if (m.isGoal) sb.append("You reached your goal. ")
        sb.append("${Format.kg(lost)} kg gone in ${Format.days(daysTaken.coerceAtLeast(0))}.")
        val thing = tangible(lost)
        if (thing.isNotEmpty()) sb.append(" That's $thing you no longer carry up every flight of stairs.")
        return sb.toString()
    }
}
