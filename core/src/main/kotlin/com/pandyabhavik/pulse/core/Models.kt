package com.pandyabhavik.pulse.core

import java.time.LocalDate

// SHARED CONTRACT. Other parts of the app compile against these names and shapes.
// Change a signature only if every caller is updated too.

/** Who Bhavik is and where he's heading. Weight in kg, height in cm. */
data class Profile(
    val name: String,
    val heightCm: Double,
    val age: Int,
    val male: Boolean,
    val startKg: Double,
    val goalKg: Double,
    val startDate: LocalDate,
    val stepGoal: Int = 10_000,
)

/** Food from MyFitnessPal for one day, kcal per meal. Food with no meal type counts as snacks. */
data class Meals(
    val breakfast: Int = 0,
    val lunch: Int = 0,
    val dinner: Int = 0,
    val snacks: Int = 0,
) {
    val total: Int get() = breakfast + lunch + dinner + snacks
}

/**
 * Everything Health Connect said about one calendar day. Null means "no data", not zero.
 * [burnedTotal] is resting + active from the Fitbit (TotalCaloriesBurned). [burnedActive] is
 * ActiveCaloriesBurned. [eaten] is null when no food at all was logged that day.
 */
data class DayLog(
    val date: LocalDate,
    val steps: Int? = null,
    val burnedTotal: Int? = null,
    val burnedActive: Int? = null,
    val eaten: Int? = null,
    val proteinG: Int? = null,
    val meals: Meals = Meals(),
    val weightKg: Double? = null,
    val sleepMinutes: Int? = null,
) {
    /** Burned minus eaten. Positive = under (deficit), negative = over (spill-over). Null if either side is missing. */
    val balance: Int? get() = if (burnedTotal != null && eaten != null) burnedTotal - eaten else null
}

/** One day on the weight line: [raw] is that day's weigh-in if any, [trend] the smoothed weight. */
data class TrendPoint(val date: LocalDate, val raw: Double?, val trend: Double)

enum class RateSource {
    /** Under 14 days of data: 0.75 kg a week, the agreed starting guess. */
    STARTING_GUESS,
    /** From the average deficit of the last 14 days with food logged. */
    DEFICITS,
    /** Deficits, cross-checked against what the scale trend actually did (learned burn). */
    DEFICITS_AND_SCALE,
    /** Not losing at the moment (average deficit <= 0): no dates can be projected. */
    STALLED,
}

data class Milestone(
    val kg: Double,
    /** "First 5 kg", "5% down", "10 kg down", "Under 100 kg", "Goal" ... */
    val label: String,
    /** The first day the trend reached it, or null. */
    val reachedOn: LocalDate?,
    /** Projected date while not reached (null when STALLED or reached). */
    val projected: LocalDate?,
    val isGoal: Boolean = false,
)

data class Forecast(
    val asOf: LocalDate,
    val currentTrendKg: Double,
    val rateKgPerWeek: Double,
    val source: RateSource,
    /** Average daily balance used (kcal, positive = under), null if not enough data. */
    val avgDeficit: Int?,
    /** Learned true daily burn (kcal) once 14+ days of food and weigh-ins exist, else null. */
    val learnedBurn: Int?,
    val milestones: List<Milestone>,
) {
    val next: Milestone? get() = milestones.firstOrNull { it.reachedOn == null }
    val goal: Milestone get() = milestones.last()
}

/** Monday-morning recap of the previous Monday..Sunday week. */
data class WeeklyRecap(
    val weekNumber: Int,            // week 1 = the week containing profile.startDate
    val weekStart: LocalDate,       // the Monday
    val trendChangeKg: Double,      // negative = lost
    val daysUnder: Int,
    val daysLogged: Int,
    val totalSteps: Int,
    val bestDay: LocalDate?,
    val bestDayBalance: Int?,
    val focusDaysUnder: Int,        // the number to beat this week (daysUnder + 1, max 7)
    val focusTip: String,           // e.g. "Saturdays were your over days twice. Plan Saturday dinner by Friday night."
    val headline: String,           // e.g. "Your best week yet, and three weeks in a row of losing."
)

/** Ring colour styles. Each reached milestone unlocks the next one (a "surprise" order fixed per index). */
enum class RingStyle(val label: String) {
    MINT("Mint"), AURORA("Aurora"), SUNRISE("Sunrise"), OCEAN("Ocean"), NEON("Neon"), EMBER("Ember"), GLACIER("Glacier"), PRISM("Prism"),
}
