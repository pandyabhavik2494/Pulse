package com.pandyabhavik.pulse.core

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How a number on the dashboard is doing, in calm words (Bhavik: encouraging, never scary).
 * GOOD = on track, STEADY = fine, GROW = room to grow, WAIT = not enough data yet / too early / just info.
 * There is deliberately no "bad" state.
 */
enum class Mood { GOOD, STEADY, GROW, WAIT }

/**
 * [word] is the short label, [why] the one kind line under the number, [bar] a 0..1 fill (null = no bar).
 * [win] is a sentence of praise for the verdict when GOOD; [tip] one small next step when STEADY/GROW.
 */
data class Status(
    val mood: Mood,
    val word: String,
    val why: String,
    val bar: Float? = null,
    val win: String? = null,
    val tip: String? = null,
)

object Moods {
    const val ON_TRACK = "On track"
    const val STEADY = "Steady"
    const val GROW = "Room to grow"
    const val FILLING = "Filling in"

    /** Share of the waking day (7:00 to 22:00) gone by [t], 0..1. Steps happen while awake. */
    fun wakingShare(t: LocalTime): Double = (((t.hour + t.minute / 60.0) - 7.0) / 15.0).coerceIn(0.0, 1.0)

    /** Share of the whole day gone by [t], 0..1. Fitbit's burned total counts from midnight. */
    fun dayShare(t: LocalTime): Double = t.toSecondOfDay() / 86_400.0

    private fun n(v: Int) = String.format(java.util.Locale.US, "%,d", v)

    fun steps(steps: Int, goal: Int, t: LocalTime): Status {
        val bar = (steps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
        if (steps >= goal) return Status(Mood.GOOD, ON_TRACK, "Goal reached", 1f, win = "You hit your step goal.")
        val share = wakingShare(t)
        if (share < 0.15) return Status(Mood.WAIT, "Early", "The day’s just started", bar)
        val expected = (goal * share).roundToInt().coerceAtLeast(1)
        val ratio = steps.toDouble() / expected
        return when {
            ratio >= 0.9 -> Status(Mood.GOOD, ON_TRACK, "On pace for ${n(goal)}", bar, win = "Your steps are on pace.")
            ratio >= 0.6 -> Status(Mood.STEADY, STEADY, "About ${n(expected - steps)} behind your pace", bar, tip = "A 10-minute walk adds about 1,000 steps.")
            else -> Status(Mood.GROW, GROW, "A walk later would catch you up", bar, tip = "A 10-minute walk adds about 1,000 steps.")
        }
    }

    /** [usual] is the usual full-day burn (Budget.Day.burn). */
    fun burned(burned: Int?, usual: Int, t: LocalTime): Status {
        if (burned == null) return Status(Mood.WAIT, FILLING, "Shows when your Fitbit syncs")
        val share = dayShare(t)
        val bar = (burned.toFloat() / usual.coerceAtLeast(1)).coerceIn(0f, 1f)
        if (share < 0.1) return Status(Mood.WAIT, "Early", "The day’s just started", bar)
        val expected = (usual * share).roundToInt().coerceAtLeast(1)
        val ratio = burned.toDouble() / expected
        return when {
            ratio >= 0.85 -> Status(Mood.GOOD, ON_TRACK, if (ratio >= 1.02) "A bit ahead of your usual" else "Right on your usual pace", bar, win = "You’re burning right on pace.")
            ratio >= 0.7 -> Status(Mood.STEADY, STEADY, "Close to your usual by now", bar, tip = "A short walk gives burned a nudge.")
            else -> Status(Mood.GROW, GROW, "Quieter than usual so far", bar, tip = "A short walk gives burned a nudge.")
        }
    }

    /** [budget] is today's food budget (Budget.Day.budget). */
    fun eaten(eaten: Int?, budget: Int): Status {
        if (eaten == null) return Status(Mood.WAIT, FILLING, "Shows once you log in MyFitnessPal")
        val bar = (eaten.toFloat() / budget.coerceAtLeast(1)).coerceIn(0f, 1f)
        return when {
            eaten <= budget -> Status(Mood.GOOD, ON_TRACK, "Within today’s plan", bar, win = "Your eating is within plan.")
            eaten <= budget + 200 -> Status(Mood.STEADY, STEADY, "Just above today’s plan", bar, tip = "A short walk after dinner evens it out.")
            else -> Status(Mood.GROW, GROW, "${n(eaten - budget)} above today’s plan", bar, tip = "A 20-minute walk after dinner brings today back toward plan.")
        }
    }

    /** [balance] = burned - eaten so far (positive = burned more). */
    fun balance(balance: Int?): Status = when {
        balance == null -> Status(Mood.WAIT, FILLING, "Shows once food is logged")
        balance >= 0 -> Status(Mood.GOOD, ON_TRACK, "Burned more than eaten so far", win = "You’ve burned more than you’ve eaten.")
        balance >= -200 -> Status(Mood.STEADY, STEADY, "Nearly even", tip = "A short walk tips it back.")
        else -> Status(Mood.GROW, GROW, "A short walk evens this out", tip = "A 20-minute walk after dinner brings today back toward plan.")
    }

    /** [left] = budget - eaten (negative = above plan). */
    fun left(left: Int, budget: Int): Status {
        val bar = if (budget <= 0) null else (left.toFloat() / budget).coerceIn(0f, 1f)
        return when {
            left >= 200 -> Status(Mood.GOOD, ON_TRACK, if (left >= budget) "Your whole day’s budget is open" else "Room left in today’s plan", bar, win = "Your budget’s in good shape.")
            left >= 0 -> Status(Mood.STEADY, STEADY, "Nearly used for today", bar)
            else -> Status(Mood.GROW, GROW, "Tomorrow is a fresh budget", 1f, tip = "A 20-minute walk after dinner brings today back toward plan.")
        }
    }

    /** Last weigh-in against the trend. Never worse than steady: daily weight is mostly water. */
    fun weight(kg: Double?, trendKg: Double?): Status? {
        kg ?: return null
        if (trendKg == null) return Status(Mood.WAIT, FILLING, "A trend forms after a few weigh-ins")
        val diff = kg - trendKg
        return when {
            diff <= -0.1 -> Status(Mood.GOOD, ON_TRACK, "Below your trend", win = "You weighed in below your trend.")
            diff <= 1.0 -> Status(Mood.STEADY, STEADY, "Within the normal daily swing")
            else -> Status(Mood.STEADY, STEADY, "Water and salt move this a lot")
        }
    }

    /** The trend's change over the last week; needs a week of trend with at least 3 weigh-ins. */
    fun trend(points: List<TrendPoint>, today: LocalDate): Status {
        val past = points.filter { !it.date.isAfter(today) }.sortedBy { it.date }
        val weekAgo = past.lastOrNull { !it.date.isAfter(today.minusDays(7)) }
        val now = past.lastOrNull()
        if (weekAgo == null || now == null || past.count { it.raw != null } < 3) {
            return Status(Mood.WAIT, FILLING, "Gets clearer after a week of weigh-ins")
        }
        val change = now.trend - weekAgo.trend
        val kg = String.format(java.util.Locale.US, "%.1f", abs(change))
        return when {
            change <= -0.1 -> Status(Mood.GOOD, ON_TRACK, "Down $kg kg this week", win = "Your trend is heading down.")
            change < 0.1 -> Status(Mood.STEADY, STEADY, "Holding steady this week")
            else -> Status(Mood.GROW, GROW, "A few days under budget usually turns it", tip = "A few days under budget usually turns the trend.")
        }
    }

    /** Resting heart rate against Bhavik's own usual (median of up to 30 readings), or general ranges before there's a usual. */
    fun resting(bpm: Int?, recent: List<Int>): Status? {
        bpm ?: return null
        if (recent.size >= 7) {
            val usual = recent.sorted()[recent.size / 2]
            return when {
                bpm <= usual -> Status(Mood.GOOD, ON_TRACK, "At or below your usual $usual", win = "Your heart is nicely rested.")
                bpm <= usual + 3 -> Status(Mood.STEADY, STEADY, "Close to your usual $usual")
                else -> Status(Mood.GROW, GROW, "A little above your usual $usual", tip = "Water and an easy evening help your resting heart.")
            }
        }
        return when {
            bpm < 70 -> Status(Mood.GOOD, ON_TRACK, "Nicely rested", win = "Your heart is nicely rested.")
            bpm < 90 -> Status(Mood.STEADY, STEADY, "Tends to drift down with regular walks")
            else -> Status(Mood.GROW, GROW, "Tends to drift down with regular walks", tip = "Regular walks help your resting heart come down.")
        }
    }

    fun sleep(min: Int?): Status? {
        min ?: return null
        val bar = (min / 480f).coerceIn(0f, 1f)
        return when {
            min >= 7 * 60 -> Status(Mood.GOOD, ON_TRACK, "Right in the 7 to 9 hour zone", bar, win = "You slept ${min / 60}h ${min % 60}m, nice.")
            min >= 6 * 60 -> Status(Mood.STEADY, STEADY, "Close to the 7 hour mark", bar, tip = "An earlier night tonight would help.")
            else -> Status(Mood.GROW, GROW, "An earlier night tonight would help", bar, tip = "An earlier night tonight would help.")
        }
    }

    fun deep(min: Int?): Status? {
        min ?: return null
        val bar = (min / 75f).coerceIn(0f, 1f)
        return when {
            min >= 60 -> Status(Mood.GOOD, ON_TRACK, "Plenty of deep sleep", bar, win = "Good deep sleep last night.")
            min >= 40 -> Status(Mood.STEADY, STEADY, "Around 1h is the sweet spot", bar, tip = "Winding down a little earlier tonight helps deep sleep.")
            else -> Status(Mood.GROW, GROW, "Around 1h is the sweet spot", bar, tip = "Winding down a little earlier tonight helps deep sleep.")
        }
    }

    /** The verdict's headline from the shown statuses, morning-aware. Never alarming. */
    fun headline(moods: List<Mood>, t: LocalTime): String {
        val good = moods.count { it == Mood.GOOD }
        val grow = moods.count { it == Mood.GROW }
        val steady = moods.count { it == Mood.STEADY }
        return when {
            grow == 0 && good >= steady -> if (t.hour < 12) "Good start" else "Going well"
            grow == 0 -> "Steady day"
            good >= grow -> "Mostly on track"
            else -> "Close to plan"
        }
    }
}
