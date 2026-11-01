package com.pandyabhavik.pulse.core

import com.pandyabhavik.pulse.core.Fixtures.START
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendTest {
    private fun w(i: Long, kg: Double?) = DayLog(START.plusDays(i), weightKg = kg)

    @Test fun emptyWithoutWeighIns() {
        assertTrue(Trend.of(emptyList(), START, START.plusDays(5)).isEmpty())
        assertTrue(Trend.of(listOf(DayLog(START, steps = 100)), START, START.plusDays(5)).isEmpty())
    }

    @Test fun seededWithFirstWeighIn() {
        val t = Trend.of(listOf(w(0, 120.0)), START, START)
        assertEquals(1, t.size)
        assertEquals(120.0, t[0].trend, 1e-9)
        assertEquals(120.0, t[0].raw!!, 1e-9)
    }

    @Test fun emaStep() {
        val t = Trend.of(listOf(w(0, 120.0), w(1, 119.0), w(2, 118.0)), START, START.plusDays(2))
        assertEquals(119.9, t[1].trend, 1e-9)
        assertEquals(119.9 + 0.1 * (118.0 - 119.9), t[2].trend, 1e-9)
    }

    @Test fun gapsCarryForward() {
        val t = Trend.of(listOf(w(0, 120.0), w(3, 110.0)), START, START.plusDays(5))
        assertEquals(6, t.size)
        assertEquals(listOf(120.0, 120.0, 120.0, 119.0, 119.0, 119.0), t.map { it.trend })
        assertNull(t[1].raw)
        assertEquals(110.0, t[3].raw!!, 1e-9)
        assertEquals((0L..5L).map { START.plusDays(it) }, t.map { it.date })
    }

    @Test fun startsAtFirstWeighInWhenFromIsEarlier() {
        val t = Trend.of(listOf(w(2, 120.0)), START.minusDays(10), START.plusDays(3))
        assertEquals(START.plusDays(2), t.first().date)
        assertEquals(2, t.size)
    }

    @Test fun laterFromKeepsTheSameValues() {
        val days = Fixtures.bhavikDays(24)
        val full = Trend.of(days, START, START.plusDays(23))
        val window = Trend.of(days, START.plusDays(10), START.plusDays(23))
        assertEquals(14, window.size)
        assertEquals(full.drop(10), window)
    }

    @Test fun toBeforeFirstWeighInIsEmpty() {
        assertTrue(Trend.of(listOf(w(5, 120.0)), START, START.plusDays(2)).isEmpty())
    }

    @Test fun weighInsAfterToAreIgnored() {
        val t = Trend.of(listOf(w(0, 120.0), w(3, 100.0)), START, START.plusDays(2))
        assertEquals(3, t.size)
        assertTrue(t.all { it.trend == 120.0 })
    }

    @Test fun duplicateDateLastNonNullWins() {
        val t = Trend.of(listOf(w(0, 120.0), w(1, 118.0), w(1, 119.0), w(1, null)), START, START.plusDays(1))
        assertEquals(119.0, t[1].raw!!, 1e-9)
        assertEquals(119.9, t[1].trend, 1e-9)
    }

    @Test fun unsortedInput() {
        val t = Trend.of(listOf(w(1, 119.0), w(0, 120.0)), START, START.plusDays(1))
        assertEquals(120.0, t[0].trend, 1e-9)
        assertEquals(119.9, t[1].trend, 1e-9)
    }

    @Test fun fixtureTrendFalls() {
        val t = Trend.of(Fixtures.bhavikDays(24), START, START.plusDays(23))
        assertEquals(24, t.size)
        assertTrue(t.last().trend < 119.0)
        assertTrue(t.last().trend > 117.0)
    }
}
