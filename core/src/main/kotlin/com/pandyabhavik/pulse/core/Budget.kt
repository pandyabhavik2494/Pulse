package com.pandyabhavik.pulse.core

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Today's food budget: how much Bhavik can eat today and still lose at the plan's pace
 * (0.75 kg a week = 825 kcal a day burned more than eaten).
 *
 * The burn it starts from is his usual full-day burn (average of the last 7 complete days from Fitbit),
 * or today's burn so far if that's already higher, or an estimate from his body when there's no Fitbit
 * data yet. So there's always a number, and it gets more accurate as days come in.
 */
object Budget {
    val PACE_KCAL: Int = (Forecaster.STARTING_GUESS_KG_PER_WEEK * Energy.KCAL_PER_KG / 7.0).roundToInt()

    /** Lightly active: what a desk job plus some walking burns, on top of BMR. */
    const val ESTIMATE_FACTOR = 1.3

    data class Day(
        /** The full-day burn the budget assumes. */
        val burn: Int,
        /** burn - PACE_KCAL. */
        val budget: Int,
        val eaten: Int,
        /** budget - eaten; negative = past the budget. */
        val left: Int,
        /** Complete Fitbit days behind [burn]; 0 = estimated from height, weight and age. */
        val burnDays: Int,
    )

    fun today(p: Profile, days: List<DayLog>, today: LocalDate, weightKg: Double?): Day {
        val past = days.filter { it.date.isBefore(today) && !it.date.isBefore(today.minusDays(14)) && it.burnedTotal != null }
            .sortedByDescending { it.date }.take(7)
        val todayLog = days.firstOrNull { it.date == today }
        val usual = if (past.isEmpty()) null else past.map { it.burnedTotal!! }.average().roundToInt()
        val estimate = (Energy.bmr(p, weightKg ?: p.startKg) * ESTIMATE_FACTOR).roundToInt()
        val burn = max(usual ?: estimate, todayLog?.burnedTotal ?: 0)
        val budget = burn - PACE_KCAL
        val eaten = todayLog?.eaten ?: 0
        return Day(burn, budget, eaten, budget - eaten, past.size)
    }
}
