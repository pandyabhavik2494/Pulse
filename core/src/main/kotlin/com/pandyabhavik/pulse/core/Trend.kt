package com.pandyabhavik.pulse.core

import java.time.LocalDate

object Trend {
    /** Smoothing factor per weigh-in day (exponential moving average). */
    const val ALPHA = 0.1

    /**
     * One point per calendar day from the first weigh-in (or [from], whichever is later) to [to].
     * Days without a weigh-in carry the trend forward. Multiple logs per day: use the day's DayLog.weightKg
     * (if the list holds the same date twice, the last non-null weight wins).
     * Seeded with the first weigh-in. Empty if there are no weigh-ins.
     *
     * The average always starts from the very first weigh-in on record, even if that is before [from],
     * so a narrower window shows the same trend values as the full history.
     */
    fun of(days: List<DayLog>, from: LocalDate, to: LocalDate): List<TrendPoint> {
        val weights = sortedMapOf<LocalDate, Double>()
        for (d in days) {
            val w = d.weightKg ?: continue
            if (w.isNaN() || w <= 0.0 || d.date.isAfter(to)) continue
            weights[d.date] = w
        }
        if (weights.isEmpty()) return emptyList()
        val first = weights.firstKey()
        val start = if (from.isAfter(first)) from else first
        val out = ArrayList<TrendPoint>()
        var trend = weights.getValue(first)
        var day = first
        while (!day.isAfter(to)) {
            val raw = weights[day]
            if (raw != null) trend += ALPHA * (raw - trend)
            if (!day.isBefore(start)) out += TrendPoint(day, raw, trend)
            day = day.plusDays(1)
        }
        return out
    }
}
