package com.pandyabhavik.pulse.core

import com.pandyabhavik.pulse.core.Fixtures.BHAVIK
import com.pandyabhavik.pulse.core.Fixtures.START
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ForecasterTest {

    // ---- targets ----

    @Test fun targetsFor120To85() {
        val expected = listOf(
            115.0 to "First 5 kg",
            114.0 to "5% down",
            110.0 to "10 kg down",
            108.0 to "10% down",
            105.0 to "15 kg down",
            102.0 to "15% down",
            100.0 to "Under 100 kg",   // merged with "20 kg down"
            96.0 to "20% down",
            95.0 to "25 kg down",
            90.0 to "25% down",        // merged with "30 kg down"
            85.0 to "Goal",            // "35 kg down" is the goal itself
        )
        val actual = Forecaster.targets(BHAVIK)
        assertEquals(expected.map { it.second }, actual.map { it.second })
        expected.zip(actual).forEach { (e, a) -> assertEquals(e.first, a.first, 1e-9) }
    }

    @Test fun targetsMergeWithinHalfKgAndFoldIntoGoal() {
        val p = BHAVIK.copy(startKg = 100.4, goalKg = 80.0)
        val t = Forecaster.targets(p)
        assertEquals(listOf("Under 100 kg", "5% down", "10% down", "15% down", "Goal"), t.map { it.second })
        assertEquals(listOf(100.0, 95.4, 90.4, 85.3, 80.0), t.map { it.first })
    }

    @Test fun targetsSortedHeaviestFirstAndGoalLast() {
        for (start in listOf(87.0, 93.3, 101.0, 120.0, 150.0)) {
            val t = Forecaster.targets(BHAVIK.copy(startKg = start, goalKg = 80.0))
            assertEquals(t.sortedByDescending { it.first }, t)
            assertEquals("Goal" to 80.0, t.last().second to t.last().first)
            t.zipWithNext().forEach { (a, b) -> assertTrue("$a vs $b", a.first - b.first >= 0.5 - 1e-9) }
            assertTrue(t.dropLast(1).all { it.first < start && it.first > 80.0 })
        }
    }

    @Test fun noUnder100WhenJourneyDoesNotCross() {
        assertFalse(Forecaster.targets(BHAVIK.copy(startKg = 95.0, goalKg = 80.0)).any { it.second == "Under 100 kg" })
        assertFalse(Forecaster.targets(BHAVIK.copy(startKg = 130.0, goalKg = 100.0)).any { it.second == "Under 100 kg" })
    }

    @Test fun smallJourneyIsJustTheGoal() {
        assertEquals(listOf(85.0 to "Goal"), Forecaster.targets(BHAVIK.copy(startKg = 86.0)))
        assertEquals(listOf(85.0 to "Goal"), Forecaster.targets(BHAVIK.copy(startKg = 84.0)))
    }

    // ---- forecast ----

    @Test fun startingGuessOnDayOne() {
        val f = Forecaster.forecast(BHAVIK, emptyList(), emptyList(), START)
        assertEquals(RateSource.STARTING_GUESS, f.source)
        assertEquals(0.75, f.rateKgPerWeek, 1e-9)
        assertEquals(120.0, f.currentTrendKg, 1e-9)
        assertNull(f.avgDeficit)
        assertNull(f.learnedBurn)
        assertEquals(11, f.milestones.size)
        // 5 kg at 0.75/7 kg a day = 46.7 -> 47 days
        assertEquals(START.plusDays(47), f.next!!.projected)
        assertEquals(START.plusDays(327), f.goal.projected)
        assertTrue(f.goal.isGoal)
        assertEquals(1, f.milestones.count { it.isGoal })
        assertTrue(f.milestones.all { it.reachedOn == null })
    }

    @Test fun thirteenDaysIsStillTheStartingGuess() {
        val days = Fixtures.bhavikDays(13)
        val asOf = START.plusDays(13)
        val f = Forecaster.forecast(BHAVIK, days, Trend.of(days, START, asOf), asOf)
        assertEquals(RateSource.STARTING_GUESS, f.source)
        assertEquals(0.75, f.rateKgPerWeek, 1e-9)
    }

    @Test fun todayIsExcludedFromTheAverage() {
        // 14 days of logs, but the 14th is today -> only 13 complete days.
        val days = Fixtures.bhavikDays(14)
        val f = Forecaster.forecast(BHAVIK, days, Trend.of(days, START, START.plusDays(13)), START.plusDays(13))
        assertEquals(RateSource.STARTING_GUESS, f.source)
    }

    @Test fun fourteenDaysUsesDeficits() {
        val days = Fixtures.bhavikDays(14)
        val asOf = START.plusDays(14)
        val f = Forecaster.forecast(BHAVIK, days, Trend.of(days, START, asOf), asOf)
        assertEquals(RateSource.DEFICITS, f.source)   // trend spans only 13 days
        assertEquals(825, f.avgDeficit)
        assertEquals(0.75, f.rateKgPerWeek, 1e-9)
        assertNull(f.learnedBurn)
    }

    @Test fun bhavikAfter24DaysBlendsDeficitsAndScale() {
        val days = Fixtures.bhavikDays(24)
        val asOf = START.plusDays(24)
        val trend = Trend.of(days, START, asOf)
        val f = Forecaster.forecast(BHAVIK, days, trend, asOf)
        assertEquals(RateSource.DEFICITS_AND_SCALE, f.source)
        assertEquals(825, f.avgDeficit)
        // The trend lags the scale early on, so the blend is under the deficit-only 0.75.
        assertTrue("${f.rateKgPerWeek}", f.rateKgPerWeek > 0.4 && f.rateKgPerWeek < 0.75)
        val learned = f.learnedBurn!!
        assertTrue("$learned", learned in 2100..2800)
        assertEquals(trend.last().trend, f.currentTrendKg, 1e-9)

        // learnedBurn = avg eaten + trend drop * 7700 / window (23 days here)
        val end = trend.first { it.date == asOf.minusDays(1) }
        val drop = trend.first().trend - end.trend
        val eaten = days.drop(1).map { it.eaten!! }.average()
        assertEquals(Math.round(eaten + drop * 7700 / 23).toInt(), learned)
        val deficitRate = 825.0 / 7700 * 7
        val scaleRate = drop / 23 * 7
        assertEquals((deficitRate + scaleRate) / 2, f.rateKgPerWeek, 1e-9)
    }

    @Test fun scaleWindowCapsAt28Days() {
        val days = Fixtures.bhavikDays(60)
        val asOf = START.plusDays(60)
        val trend = Trend.of(days, START, asOf)
        val f = Forecaster.forecast(BHAVIK, days, trend, asOf)
        val end = trend.first { it.date == asOf.minusDays(1) }.trend
        val start = trend.first { it.date == asOf.minusDays(29) }.trend
        val scaleRate = (start - end) / 28 * 7
        assertEquals((0.75 + scaleRate) / 2, f.rateKgPerWeek, 1e-9)
        // after two months the trend has caught up with the scale: close to 0.75
        assertTrue(f.rateKgPerWeek > 0.68)
    }

    @Test fun deficitsUseLast14DaysWithBalanceSkippingGaps() {
        val asOf = LocalDate.of(2026, 12, 1)
        val days = ArrayList<DayLog>()
        // 20 days with 500 under, then the most recent 14 logged days at 1,000 under, with gaps.
        for (i in 40 downTo 21) days += Fixtures.day(asOf.minusDays(i.toLong()), 500)
        var d = asOf.minusDays(1); var n = 0
        while (n < 14) { if (d.dayOfMonth % 3 != 0) { days += Fixtures.day(d, 1000); n++ }; d = d.minusDays(1) }
        days += Fixtures.day(asOf, -3000) // today: ignored
        val f = Forecaster.forecast(BHAVIK, days, emptyList(), asOf)
        assertEquals(RateSource.DEFICITS, f.source)
        assertEquals(1000, f.avgDeficit)
        assertEquals(1000.0 / 7700 * 7, f.rateKgPerWeek, 1e-9)
    }

    @Test fun rateIsClampedTo1Point5() {
        val days = (1..14).map { Fixtures.day(START.plusDays(it.toLong()), 2500) }
        val f = Forecaster.forecast(BHAVIK, days, emptyList(), START.plusDays(15))
        assertEquals(RateSource.DEFICITS, f.source)
        assertEquals(1.5, f.rateKgPerWeek, 1e-9)
        assertEquals(START.plusDays(15).plusDays(24), f.next!!.projected) // 5 / (1.5/7) = 23.3 -> 24
    }

    @Test fun stalledWhenNotUnder() {
        val days = (1..14).map { Fixtures.day(START.plusDays(it.toLong()), if (it % 2 == 0) -200 else 100) }
        val f = Forecaster.forecast(BHAVIK, days, emptyList(), START.plusDays(15))
        assertEquals(RateSource.STALLED, f.source)
        assertEquals(0.0, f.rateKgPerWeek, 1e-9)
        assertEquals(-50, f.avgDeficit)
        assertTrue(f.milestones.all { it.projected == null })
        assertNotNull(f.next)
    }

    @Test fun stalledWhenExactlyEven() {
        val days = (1..14).map { Fixtures.day(START.plusDays(it.toLong()), 0) }
        assertEquals(RateSource.STALLED, Forecaster.forecast(BHAVIK, days, emptyList(), START.plusDays(15)).source)
    }

    @Test fun reachedMilestonesUseFirstTrendDayAtOrBelow() {
        val trend = listOf(
            TrendPoint(START, 120.0, 120.0),
            TrendPoint(START.plusDays(30), null, 115.4),
            TrendPoint(START.plusDays(31), 114.0, 115.0),   // First 5 kg reached exactly at 115
            TrendPoint(START.plusDays(32), 113.0, 114.2),
            TrendPoint(START.plusDays(33), 116.0, 114.5),   // bounced back: still reached on day 31
        )
        val asOf = START.plusDays(34)
        val f = Forecaster.forecast(BHAVIK, emptyList(), trend, asOf)
        val first = f.milestones[0]
        assertEquals("First 5 kg", first.label)
        assertEquals(START.plusDays(31), first.reachedOn)
        assertNull(first.projected)
        assertNull(f.milestones[1].reachedOn) // 114 not reached (lowest 114.2)
        assertEquals("5% down", f.next!!.label)
        assertEquals(114.5, f.currentTrendKg, 1e-9)
        // 0.5 kg at 0.75/7 a day = 4.67 -> 5 days
        assertEquals(asOf.plusDays(5), f.next!!.projected)
    }

    @Test fun trendPointsAfterAsOfAreIgnored() {
        val trend = listOf(TrendPoint(START, 120.0, 120.0), TrendPoint(START.plusDays(5), 100.0, 110.0))
        val f = Forecaster.forecast(BHAVIK, emptyList(), trend, START.plusDays(2))
        assertEquals(120.0, f.currentTrendKg, 1e-9)
        assertTrue(f.milestones.all { it.reachedOn == null })
    }

    @Test fun allReachedMeansNoNext() {
        val trend = listOf(TrendPoint(START, 120.0, 120.0), TrendPoint(START.plusDays(300), 84.0, 84.9))
        val f = Forecaster.forecast(BHAVIK, emptyList(), trend, START.plusDays(301))
        assertNull(f.next)
        assertEquals(START.plusDays(300), f.goal.reachedOn)
    }

    // ---- shiftDays ----

    private fun forecastWithNext(projected: LocalDate?, kg: Double = 115.0) = Forecast(
        asOf = START, currentTrendKg = 117.0, rateKgPerWeek = 0.75, source = RateSource.DEFICITS,
        avgDeficit = 825, learnedBurn = null,
        milestones = listOf(
            Milestone(118.0, "x", START, null),
            Milestone(kg, "First 5 kg", null, projected),
            Milestone(85.0, "Goal", null, projected?.plusDays(300), isGoal = true),
        ),
    )

    @Test fun shiftEarlierIsPositive() {
        val prev = forecastWithNext(LocalDate.of(2026, 12, 20))
        val cur = forecastWithNext(LocalDate.of(2026, 12, 17))
        assertEquals(3, Forecaster.shiftDays(cur, prev))
        assertEquals(-3, Forecaster.shiftDays(prev, cur))
        assertEquals(0, Forecaster.shiftDays(cur, cur))
    }

    @Test fun shiftNullWhenNotComparable() {
        val ok = forecastWithNext(LocalDate.of(2026, 12, 17))
        assertNull(Forecaster.shiftDays(forecastWithNext(null), ok))
        assertNull(Forecaster.shiftDays(ok, forecastWithNext(null)))
        assertNull(Forecaster.shiftDays(ok, forecastWithNext(LocalDate.of(2026, 12, 17), kg = 114.0)))
        val done = ok.copy(milestones = ok.milestones.map { it.copy(reachedOn = START, projected = null) })
        assertNull(Forecaster.shiftDays(done, ok))
    }

    @Test fun shiftBetweenRealForecasts() {
        val slow = (1..14).map { Fixtures.day(START.plusDays(it.toLong()), 500) }
        val fast = (1..14).map { Fixtures.day(START.plusDays(it.toLong()), 1000) }
        val asOf = START.plusDays(15)
        val prev = Forecaster.forecast(BHAVIK, slow, emptyList(), asOf)
        val cur = Forecaster.forecast(BHAVIK, fast, emptyList(), asOf)
        val s = Forecaster.shiftDays(cur, prev)!!
        assertTrue(s > 0)
        assertEquals(java.time.temporal.ChronoUnit.DAYS.between(cur.next!!.projected, prev.next!!.projected).toInt(), s)
    }

    // ---- whatIf ----

    @Test fun whatIfConstantDeficit() {
        // 770 kcal/day = 0.1 kg/day; 35 kg -> 350 days
        assertEquals(START.plusDays(350), Forecaster.whatIf(BHAVIK, 120.0, START, 770))
        // 32.6 kg at 1,000/day: 32.6 / 0.12987 = 251.02 -> 252
        assertEquals(START.plusDays(252), Forecaster.whatIf(BHAVIK, 117.6, START, 1000))
    }

    @Test fun whatIfMoreDeficitIsSooner() {
        val a = Forecaster.whatIf(BHAVIK, 117.6, START, 500)!!
        val b = Forecaster.whatIf(BHAVIK, 117.6, START, 1500)!!
        assertTrue(b.isBefore(a))
    }

    @Test fun whatIfNoDeficitIsNull() {
        assertNull(Forecaster.whatIf(BHAVIK, 117.6, START, 0))
        assertNull(Forecaster.whatIf(BHAVIK, 117.6, START, -300))
    }

    @Test fun whatIfAtGoalIsToday() {
        assertEquals(START, Forecaster.whatIf(BHAVIK, 85.0, START, 500))
        assertEquals(START, Forecaster.whatIf(BHAVIK, 84.0, START, 500))
    }
}
