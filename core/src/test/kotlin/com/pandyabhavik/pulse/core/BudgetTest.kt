package com.pandyabhavik.pulse.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BudgetTest {
    private val p = Fixtures.BHAVIK
    private val today = LocalDate.of(2026, 11, 29)

    @Test
    fun paceIs825ADay() = assertEquals(825, Budget.PACE_KCAL)

    @Test
    fun usesTheUsualBurn() {
        val days = (1L..7L).map { DayLog(today.minusDays(it), burnedTotal = 3000) } +
            DayLog(today, burnedTotal = 1200, eaten = 1500)
        val b = Budget.today(p, days, today, 118.0)
        assertEquals(3000, b.burn)
        assertEquals(2175, b.budget)
        assertEquals(675, b.left)
        assertEquals(7, b.burnDays)
    }

    @Test
    fun aBigDayRaisesTheBudget() {
        val days = listOf(DayLog(today.minusDays(1), burnedTotal = 2800), DayLog(today, burnedTotal = 3400, eaten = 3000))
        val b = Budget.today(p, days, today, 118.0)
        assertEquals(3400, b.burn)
        assertEquals(3400 - 825 - 3000, b.left)
    }

    @Test
    fun noDataStillGivesABudget() {
        val b = Budget.today(p, emptyList(), today, null)
        assertEquals(0, b.burnDays)
        assertEquals((Energy.bmr(p, 120.0) * 1.3).toInt(), b.burn, 1)
        assertEquals(b.budget, b.left)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) =
        org.junit.Assert.assertEquals(expected.toDouble(), actual.toDouble(), tolerance.toDouble())
}
