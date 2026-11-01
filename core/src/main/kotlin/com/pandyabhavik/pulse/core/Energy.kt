package com.pandyabhavik.pulse.core

import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.roundToInt

object Energy {
    const val KCAL_PER_KG = 7700.0

    /**
     * Mifflin-St Jeor basal metabolic rate, kcal/day:
     * 10 * weight(kg) + 6.25 * height(cm) - 5 * age + 5 (men) or - 161 (women), rounded.
     */
    fun bmr(p: Profile, weightKg: Double): Int {
        val base = 10.0 * weightKg + 6.25 * p.heightCm - 5.0 * p.age
        return (base + if (p.male) 5.0 else -161.0).roundToInt()
    }

    /** Resting kcal burned so far today: bmr * fraction of the day elapsed at [now]. */
    fun restingSoFar(p: Profile, weightKg: Double, now: LocalDateTime): Int {
        val seconds = now.toLocalTime().toSecondOfDay().toDouble() + now.nano / 1e9
        return (bmr(p, weightKg) * (seconds / 86_400.0)).roundToInt()
    }

    /**
     * Splits a day's burn into (resting, active). Uses total and active from Fitbit when both exist
     * (resting = total - active, min 0); if only total exists, active = total - restingEstimate (min 0)
     * and resting is the rest of the total; if only active, resting = restingEstimate.
     * Returns null when neither exists.
     */
    fun split(total: Int?, active: Int?, restingEstimate: Int): Pair<Int, Int>? = when {
        total != null && active != null -> Pair(max(0, total - active), active)
        total != null -> {
            val a = max(0, total - restingEstimate)
            Pair(total - a, a)
        }
        active != null -> Pair(restingEstimate, active)
        else -> null
    }
}
