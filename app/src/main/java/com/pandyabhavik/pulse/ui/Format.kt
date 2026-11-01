package com.pandyabhavik.pulse.ui

import com.pandyabhavik.pulse.core.DayLog
import com.pandyabhavik.pulse.core.RateSource
import com.pandyabhavik.pulse.data.HomeState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formatting helpers only: no business rules live here. */
object F {
    const val NBSP = ' '
    const val MINUS = '−'

    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.UK)
    private val longWeekdayDayMonth = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.UK)
    val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)
    val monthShort: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.UK)
    val weekdayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.UK)

    /** 8412 -> "8,412". */
    fun n(v: Int): String = String.format(Locale.US, "%,d", v)

    /** 117.62 -> "117.6". */
    fun kg(v: Double): String = String.format(Locale.US, "%.1f", v)

    /** Milestone weights: whole numbers without a decimal ("115"), others with one ("113.9"). */
    fun kgShort(v: Double): String = if (abs(v - v.roundToInt()) < 0.05) v.roundToInt().toString() else kg(v)

    /** -2.4 -> "−2.4", 0.3 -> "+0.3". */
    fun signedKg(v: Double): String = (if (v < 0) "$MINUS" else "+") + kg(abs(v))

    /** "17 Dec", or "20 Sep 2027" outside [today]'s year. */
    fun date(d: LocalDate, today: LocalDate): String =
        if (d.year == today.year) dayMonth.format(d) else dayMonthYear.format(d)

    fun dateWithYear(d: LocalDate): String = dayMonthYear.format(d)

    /** "Tue, 24 Nov". */
    fun dayShort(d: LocalDate): String = weekdayDayMonth.format(d)

    /** "Monday, 23 Nov". */
    fun dayLong(d: LocalDate): String = longWeekdayDayMonth.format(d)

    /** "Morning", "Afternoon" or "Evening". */
    fun partOfDay(now: ZonedDateTime): String = when (now.hour) {
        in 4..11 -> "Morning"
        in 12..16 -> "Afternoon"
        else -> "Evening"
    }

    /** "Fitbit · 3 min ago". */
    fun syncAgo(last: Instant?, now: ZonedDateTime): String {
        if (last == null) return "Not synced yet"
        val m = Duration.between(last, now.toInstant()).toMinutes().coerceAtLeast(0)
        val ago = when {
            m < 1 -> "just now"
            m < 60 -> "$m min ago"
            m < 24 * 60 -> "${m / 60} h ago"
            else -> "${m / (24 * 60)} d ago"
        }
        return "Fitbit · $ago"
    }

    /** 6 h 40 m. */
    fun sleep(minutes: Int): String = "${minutes / 60} h ${minutes % 60} m"

    /** 64,200 -> "64k". */
    fun compact(v: Int): String = when {
        v >= 10_000 -> "${(v / 1000.0).roundToInt()}k"
        v >= 1000 -> String.format(Locale.US, "%.1fk", v / 1000.0).replace(".0k", "k")
        else -> v.toString()
    }

    /** "520 under" / "180 over". */
    fun balanceShort(b: Int): String = if (b >= 0) "${n(b)} under" else "${n(-b)} over"

    /** "+3 days earlier" style copy for a milestone shift. */
    fun shift(days: Int): String = when {
        days == 1 -> "1 day earlier"
        days > 1 -> "$days days earlier"
        days == -1 -> "1 day later"
        else -> "${-days} days later"
    }

    /** Difference between two dates in friendly units: "6 weeks sooner", "4 days later". */
    fun sooner(newDate: LocalDate, oldDate: LocalDate): String {
        val d = ChronoUnit.DAYS.between(newDate, oldDate)
        if (d == 0L) return "same as now"
        val a = abs(d)
        val unit = when {
            a >= 14 -> "${(a / 7.0).roundToInt()} weeks"
            a == 1L -> "1 day"
            else -> "$a days"
        }
        return if (d > 0) "$unit sooner" else "$unit later"
    }

    /** Caption under the Journey chart. */
    fun pace(s: HomeState): String {
        val f = s.forecast ?: return "Pace appears after your first weigh-ins"
        return when (f.source) {
            RateSource.STARTING_GUESS -> "Starting guess until 14 days of data"
            RateSource.STALLED -> "Pace paused: not losing right now"
            RateSource.DEFICITS -> "Pace: ${String.format(Locale.US, "%.2f", f.rateKgPerWeek)} kg/week (last 14 days)"
            RateSource.DEFICITS_AND_SCALE -> "Pace: ${String.format(Locale.US, "%.2f", f.rateKgPerWeek)} kg/week (last 14 days, scale-checked)"
        }
    }

    /** Meal rows in the mock's order, only the meals with food. */
    fun meals(d: DayLog): List<Pair<String, Int>> = listOf(
        "Breakfast" to d.meals.breakfast,
        "Lunch" to d.meals.lunch,
        "Snacks" to d.meals.snacks,
        "Dinner" to d.meals.dinner,
    ).filter { it.second > 0 }

    /** "1 of 2", "Week 3". */
    fun ordinalWeek(n: Int): String = "Week $n"

    fun num(n: Int): String = when (n) {
        1 -> "one"; 2 -> "two"; 3 -> "three"; 4 -> "four"; 5 -> "five"; 6 -> "six"; 7 -> "seven"
        else -> n.toString()
    }
}
