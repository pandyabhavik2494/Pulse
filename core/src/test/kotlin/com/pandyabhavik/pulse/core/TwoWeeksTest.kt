package com.pandyabhavik.pulse.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TwoWeeksTest {
    private val p = Fixtures.BHAVIK
    private val today = LocalDate.of(2026, 11, 29)

    private fun day(back: Long, burned: Int? = null, eaten: Int? = null, kg: Double? = null) =
        DayLog(today.minusDays(back), burnedTotal = burned, eaten = eaten, weightKg = kg)

    @Test
    fun changeFollowsTheBalance() {
        assertEquals(-1.0, TwoWeeks.changeFor(550.0), 1e-9)   // 550 burned more than eaten for 14 days = 1 kg off
        assertEquals(0.5, TwoWeeks.changeFor(-275.0), 1e-9)   // eating more than burned = a gain
    }

    @Test
    fun noDataStillGivesAStartingEstimate() {
        val o = TwoWeeks.outlook(p, emptyList(), today)
        assertEquals(TwoWeeks.Basis.STARTING_GUESS, o.basis)
        assertEquals(120.0, o.fromKg, 1e-9)
        assertEquals(-1.5, o.changeKg, 1e-9)
        assertTrue(o.early)
    }

    @Test
    fun todayAloneIsUsedWhenThereIsNothingElse() {
        val o = TwoWeeks.outlook(p, listOf(day(0, burned = 2000, eaten = 2550)), today)
        assertEquals(TwoWeeks.Basis.TODAY, o.basis)
        assertEquals(1.0, o.changeKg, 1e-9) // 550 over today -> +1 kg in two weeks
    }

    @Test
    fun oneDayOfFoodIsAnEarlyEstimateAndAWeekIsNot() {
        val one = TwoWeeks.outlook(p, listOf(day(1, burned = 2800, eaten = 2250)), today)
        assertEquals(TwoWeeks.Basis.FOOD, one.basis)
        assertEquals(1, one.foodDays)
        assertEquals(-1.0, one.changeKg, 1e-9)
        assertTrue(one.early)

        val week = (1L..7L).map { day(it, burned = 2800, eaten = 2250) }
        val w = TwoWeeks.outlook(p, week, today)
        assertEquals(7, w.foodDays)
        assertFalse(w.early)
    }

    @Test
    fun eatingALotShowsAGain() {
        val o = TwoWeeks.outlook(p, (1L..7L).map { day(it, burned = 2500, eaten = 3600) }, today)
        assertTrue("1,100 over a day should be a gain", o.changeKg > 0)
        assertEquals(2.0, o.changeKg, 1e-9)
        assertEquals(-1100, o.avgBalance)
    }

    @Test
    fun onlyTheLatestSevenFoodDaysCount() {
        val old = (8L..14L).map { day(it, burned = 2500, eaten = 3600) }   // a bad week before
        val recent = (1L..7L).map { day(it, burned = 2800, eaten = 2250) } // a good one now
        val o = TwoWeeks.outlook(p, old + recent, today)
        assertEquals(550, o.avgBalance)
    }

    @Test
    fun weighInsAloneGiveAScaleOutlook() {
        // 0.1 kg a day down over 10 days.
        val days = (0L..10L).map { day(it, kg = 110.0 + it * 0.1) }
        val o = TwoWeeks.outlook(p, days, today)
        assertEquals(TwoWeeks.Basis.SCALE, o.basis)
        assertEquals(-1.4, o.changeKg, 1e-6)
    }

    @Test
    fun foodAndScaleBlendWithFoodLeading() {
        val days = (0L..10L).map { day(it, kg = 110.0 + it * 0.1) } +
            (1L..7L).map { day(it, burned = 2800, eaten = 2250) }.map { it.copy(weightKg = 110.0 + it.date.until(today).days * 0.1) }
        val o = TwoWeeks.outlook(p, days.groupBy { it.date }.map { (_, v) -> v.last() }, today)
        assertEquals(TwoWeeks.Basis.FOOD_AND_SCALE, o.basis)
        assertEquals((2 * -1.0 + -1.4) / 3, o.changeKg, 1e-6)
    }

    @Test
    fun periodsWalkBackInTwoWeekSteps() {
        val days = (0L..40L).map { day(it, kg = 110.0 + it * 0.1) }
        val ps = TwoWeeks.periods(days, today)
        assertEquals(2, ps.size)
        assertEquals(today, ps[0].end)
        assertEquals(14, ps[0].days)
        assertEquals(ps[0].start, ps[1].end)
        assertTrue(ps.all { it.changeKg < 0 })
    }

    @Test
    fun aShortFirstStretchWhenThereIsLessThanTwoWeeks() {
        val days = listOf(day(5, kg = 112.0), day(0, kg = 111.0))
        val ps = TwoWeeks.periods(days, today)
        assertEquals(1, ps.size)
        assertEquals(5, ps[0].days)
        assertTrue(ps[0].changeKg < 0)
    }

    @Test
    fun noWeighInsNoPeriods() {
        assertTrue(TwoWeeks.periods(emptyList(), today).isEmpty())
        assertTrue(TwoWeeks.periods(listOf(day(0, kg = 100.0)), today).isEmpty())
    }
}
