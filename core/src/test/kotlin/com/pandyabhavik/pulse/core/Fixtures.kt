package com.pandyabhavik.pulse.core

import java.time.LocalDate

/** Bhavik's real numbers: 5 ft 9 in, 32, 120 kg -> 85 kg from Sunday 1 Nov 2026. */
object Fixtures {
    val START: LocalDate = LocalDate.of(2026, 11, 1)
    val BHAVIK = Profile(
        name = "Bhavik", heightCm = 175.3, age = 32, male = true,
        startKg = 120.0, goalKg = 85.0, startDate = START,
    )

    private val EATEN_WOBBLE = listOf(0, 120, -80, 60, -100, 40, -40)        // sums to 0
    private val DEFICIT_WOBBLE = listOf(100, -60, 40, -120, 60, -20, 0)      // sums to 0
    private val SCALE_NOISE = listOf(0.3, -0.2, 0.1, -0.3, 0.2, 0.0, -0.1)   // sums to 0

    /** [n] days from 1 Nov: ~0.75 kg a week on the scale, exactly 825 kcal average deficit per 7 days. */
    fun bhavikDays(n: Int = 24, steps: Int = 8000): List<DayLog> = (0 until n).map { i ->
        val eaten = 1900 + EATEN_WOBBLE[i % 7]
        val deficit = 825 + DEFICIT_WOBBLE[i % 7]
        DayLog(
            date = START.plusDays(i.toLong()),
            steps = steps + (i % 5) * 500,
            burnedTotal = eaten + deficit,
            burnedActive = 600,
            eaten = eaten,
            proteinG = 120,
            meals = Meals(breakfast = 400, lunch = 600, dinner = eaten - 1200, snacks = 200),
            weightKg = 120.0 - 0.75 / 7 * i + SCALE_NOISE[i % 7],
        )
    }

    fun day(date: LocalDate, balance: Int?, eaten: Int = 2000): DayLog =
        if (balance == null) DayLog(date, burnedTotal = 2600)
        else DayLog(date, burnedTotal = eaten + balance, eaten = eaten)
}
