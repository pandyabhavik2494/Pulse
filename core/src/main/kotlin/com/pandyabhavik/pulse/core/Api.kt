package com.pandyabhavik.pulse.core

import java.time.LocalDate
import java.time.LocalDateTime

// SHARED CONTRACT: function signatures the app uses. Bodies are implemented in the core module.

object Energy {
    const val KCAL_PER_KG = 7700.0

    /** Mifflin-St Jeor basal metabolic rate, kcal/day. */
    fun bmr(p: Profile, weightKg: Double): Int = TODO()

    /** Resting kcal burned so far today: bmr * fraction of the day elapsed at [now]. */
    fun restingSoFar(p: Profile, weightKg: Double, now: LocalDateTime): Int = TODO()

    /**
     * Splits a day's burn into (resting, active). Uses total and active from Fitbit when both exist;
     * if only total exists, active = total - restingEstimate (min 0); if only active, resting = restingEstimate.
     * Returns null when neither exists.
     */
    fun split(total: Int?, active: Int?, restingEstimate: Int): Pair<Int, Int>? = TODO()
}

object Trend {
    /** Smoothing factor per weigh-in day (exponential moving average). */
    const val ALPHA = 0.1

    /**
     * One point per calendar day from the first weigh-in (or [from], whichever is later) to [to].
     * Days without a weigh-in carry the trend forward. Multiple logs per day: use the day's DayLog.weightKg.
     * Seeded with the first weigh-in. Empty if there are no weigh-ins.
     */
    fun of(days: List<DayLog>, from: LocalDate, to: LocalDate): List<TrendPoint> = TODO()
}

object Forecaster {
    const val STARTING_GUESS_KG_PER_WEEK = 0.75
    const val MAX_KG_PER_WEEK = 1.5

    /** The milestone weights and labels for a profile: every 5 kg, 5/10/15/20/25% down, crossing 100 kg, and the goal. */
    fun targets(p: Profile): List<Pair<Double, String>> = TODO()

    /** The forecast as of [asOf], using only days up to and including asOf - 1 for averages (today is incomplete). */
    fun forecast(p: Profile, days: List<DayLog>, trend: List<TrendPoint>, asOf: LocalDate): Forecast = TODO()

    /** Days the next unreached milestone moved: positive = earlier than [previous] projected. Null if not comparable. */
    fun shiftDays(current: Forecast, previous: Forecast): Int? = TODO()

    /** Goal date if Bhavik averaged [deficitPerDay] kcal under from [from] at [trendKg]. Null if deficit <= 0. */
    fun whatIf(p: Profile, trendKg: Double, from: LocalDate, deficitPerDay: Int): LocalDate? = TODO()
}

object Motivation {
    /**
     * Forgiving streak of "under" days ending yesterday (and including today if today is already under).
     * A day over does NOT break it if the 7-day average balance ending that day is still under.
     * A day with no food logged is skipped (neither extends nor breaks). Over days that are forgiven don't add.
     */
    fun streak(days: List<DayLog>, today: LocalDate): Int = TODO()

    /** Average daily balance over the 7 days before [today] that have a balance; null if none. */
    fun avg7(days: List<DayLog>, today: LocalDate): Int? = TODO()

    /** A small win every 1 kg below the start weight, measured on trend. */
    fun winsDone(p: Profile, trendKg: Double): Int = TODO()
    fun winsTotal(p: Profile): Int = TODO()
    /** kg left to the next 1 kg win (e.g. 0.6). */
    fun toNextWin(p: Profile, trendKg: Double): Double = TODO()

    /** A physical comparison for [kgLost], e.g. 2.0 -> "two big water bottles". Never empty for kgLost >= 0.5. */
    fun tangible(kgLost: Double): String = TODO()

    /** The calm line for an over day, e.g. "One day over doesn't move the trend much. Your 7-day average is still 410 kcal under." */
    fun spillLine(overBy: Int, avg7: Int?): String = TODO()

    /** Morning notification after the weigh-in syncs: (title, body). */
    fun morningNudge(trendChangeKg: Double, trendKg: Double, next: Milestone?, shift: Int?): Pair<String, String> = TODO()

    /** Evening (8 PM) notification: (title, body). balance = so far today (positive under). */
    fun eveningNudge(balance: Int?, streak: Int): Pair<String, String> = TODO()

    /** One sentence for Journey, e.g. "A strong week moved 115 kg 3 days earlier, to 17 Dec." Null if nothing to say. */
    fun journeyLine(next: Milestone?, shift: Int?): String? = TODO()

    /** The Monday recap of last week, or null if [today] isn't in the first 2 days of a week or there's no data. */
    fun weeklyRecap(p: Profile, days: List<DayLog>, trend: List<TrendPoint>, today: LocalDate): WeeklyRecap? = TODO()

    /** Ring style unlocked after [milestonesReached] milestones (0 = MINT). */
    fun ringStyle(milestonesReached: Int): RingStyle = TODO()

    /** Celebration copy for a reached milestone, e.g. "2 kg gone in 18 days. That's two big water bottles you no longer carry up every flight of stairs." */
    fun celebrationLine(p: Profile, m: Milestone, daysTaken: Long): String = TODO()
}
