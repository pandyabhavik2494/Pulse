package com.pandyabhavik.pulse.core

import com.pandyabhavik.pulse.core.Fixtures.BHAVIK
import com.pandyabhavik.pulse.core.Fixtures.START
import com.pandyabhavik.pulse.core.Fixtures.day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class MotivationTest {
    private val today = LocalDate.of(2026, 11, 20)
    private fun ago(n: Int) = today.minusDays(n.toLong())

    // ---- streak ----

    @Test fun streakEmpty() {
        assertEquals(0, Motivation.streak(emptyList(), today))
    }

    @Test fun streakCountsUnderDaysEndingYesterday() {
        val days = (1..5).map { day(ago(it), 500) } + day(ago(6), -800)
        assertEquals(5, Motivation.streak(days, today))
    }

    @Test fun streakIncludesTodayWhenAlreadyUnder() {
        val days = (1..5).map { day(ago(it), 500) } + day(today, 300)
        assertEquals(6, Motivation.streak(days, today))
    }

    @Test fun overTodayDoesNotBreakIt() {
        val days = (1..5).map { day(ago(it), 500) } + day(today, -900)
        assertEquals(5, Motivation.streak(days, today))
    }

    @Test fun yesterdayOverAndNotForgivenIsZero() {
        val days = listOf(day(ago(1), -500), day(ago(2), 300))
        assertEquals(0, Motivation.streak(days, today))
    }

    @Test fun noFoodDaysAreSkipped() {
        val days = listOf(day(ago(1), 500), day(ago(2), null), day(ago(3), 400), day(ago(5), 600), day(ago(6), -900))
        assertEquals(3, Motivation.streak(days, today))
    }

    @Test fun overDayForgivenWhenSevenDayAverageStillUnder() {
        // ago(3) is 300 over, but the 7 days ending then average well under.
        val days = listOf(
            day(ago(1), 600), day(ago(2), 500), day(ago(3), -300),
            day(ago(4), 700), day(ago(5), 800), day(ago(6), 600), day(ago(7), 900),
            day(ago(11), -2000), day(ago(12), -2000),
        )
        // ago(3): avg of ago(3..9) = 540 under -> forgiven, adds nothing. ago(11): avg of ago(11..17) is over -> breaks.
        assertEquals(6, Motivation.streak(days, today))
    }

    @Test fun overDayNotForgivenWhenAverageIsOver() {
        val days = listOf(day(ago(1), 600), day(ago(2), -3000), day(ago(3), 500), day(ago(4), 500))
        assertEquals(1, Motivation.streak(days, today))
    }

    @Test fun evenDayCountsAsOverButCanBeForgiven() {
        val days = listOf(day(ago(1), 600), day(ago(2), 0), day(ago(3), 500))
        assertEquals(2, Motivation.streak(days, today))
        assertEquals(1, Motivation.streak(listOf(day(ago(1), 600), day(ago(2), 0)), today))
    }

    @Test fun streakForBhavikFixture() {
        val days = Fixtures.bhavikDays(24)
        assertEquals(24, Motivation.streak(days, START.plusDays(24)))
        assertEquals(20, Motivation.streak(days, START.plusDays(19))) // today (day 20) included: under already
    }

    // ---- avg7 ----

    @Test fun avg7Null() {
        assertNull(Motivation.avg7(emptyList(), today))
        assertNull(Motivation.avg7(listOf(day(today, 500), day(ago(8), 500), day(ago(1), null)), today))
    }

    @Test fun avg7UsesSevenDaysBeforeToday() {
        val days = listOf(
            day(today, -5000), day(ago(1), 400), day(ago(2), 420), day(ago(4), 410), day(ago(7), 410), day(ago(8), -9000),
        )
        assertEquals(410, Motivation.avg7(days, today))
    }

    @Test fun avg7Fixture() {
        assertEquals(825, Motivation.avg7(Fixtures.bhavikDays(24), START.plusDays(24)))
    }

    // ---- wins ----

    @Test fun wins() {
        assertEquals(35, Motivation.winsTotal(BHAVIK))
        assertEquals(0, Motivation.winsDone(BHAVIK, 120.0))
        assertEquals(0, Motivation.winsDone(BHAVIK, 121.5))
        assertEquals(2, Motivation.winsDone(BHAVIK, 117.6))
        assertEquals(3, Motivation.winsDone(BHAVIK, 117.0))
        assertEquals(35, Motivation.winsDone(BHAVIK, 84.0))
        assertEquals(34, Motivation.winsTotal(BHAVIK.copy(goalKg = 85.5)))
        assertEquals(0, Motivation.winsTotal(BHAVIK.copy(goalKg = 125.0)))
    }

    @Test fun toNextWin() {
        assertEquals(0.6, Motivation.toNextWin(BHAVIK, 117.6), 1e-9)
        assertEquals(1.0, Motivation.toNextWin(BHAVIK, 120.0), 1e-9)
        assertEquals(1.0, Motivation.toNextWin(BHAVIK, 117.0), 1e-9)
        assertEquals(0.1, Motivation.toNextWin(BHAVIK, 117.01), 1e-9)
        assertEquals(2.5, Motivation.toNextWin(BHAVIK, 121.5), 1e-9)
        assertEquals(0.0, Motivation.toNextWin(BHAVIK, 85.0), 1e-9)
        assertEquals(0.0, Motivation.toNextWin(BHAVIK, 80.0), 1e-9)
    }

    // ---- tangible ----

    @Test fun tangibleTable() {
        assertEquals("a tub of butter", Motivation.tangible(0.5))
        assertEquals("a tub of butter", Motivation.tangible(0.9))
        assertEquals("a bag of sugar", Motivation.tangible(1.0))
        assertEquals("two big water bottles", Motivation.tangible(2.0))
        assertEquals("two big water bottles", Motivation.tangible(2.9))
        assertEquals("a house cat", Motivation.tangible(3.0))
        assertEquals("a bowling ball", Motivation.tangible(5.0))
        assertEquals("a car tyre", Motivation.tangible(10.0))
        assertEquals("a full suitcase", Motivation.tangible(15.0))
        assertEquals("a sack of potatoes and a car tyre", Motivation.tangible(35.0))
        assertEquals("two sacks of potatoes", Motivation.tangible(120.0))
    }

    @Test fun tangibleNeverEmptyFromHalfAKilo() {
        var kg = 0.5
        while (kg < 60) { assertTrue("$kg", Motivation.tangible(kg).isNotEmpty()); kg += 0.1 }
        assertEquals("", Motivation.tangible(0.1))
        assertEquals("", Motivation.tangible(0.0))
    }

    @Test fun tangibleHandlesFloatingNoise() {
        assertEquals("a bag of sugar", Motivation.tangible(120.0 - 119.0))
        assertEquals("two big water bottles", Motivation.tangible(0.1 * 20))
    }

    // ---- spill ----

    @Test fun spillLine() {
        assertEquals(
            "One day of eating more than you burned doesn't move the trend much. Over the last 7 days you still burned 410 kcal a day more than you ate.",
            Motivation.spillLine(300, 410),
        )
        assertEquals(
            "One day of eating more than you burned doesn't move the trend much. Over the last 7 days you still burned 1,140 kcal a day more than you ate.",
            Motivation.spillLine(300, 1140),
        )
        assertEquals("One day of eating more than you burned doesn't move the trend much. Tomorrow is a fresh start.", Motivation.spillLine(300, null))
        assertTrue(Motivation.spillLine(300, -120).contains("back to burning more"))
        assertTrue(Motivation.spillLine(300, 0).contains("even"))
        for (a in listOf(null, -500, 0, 500)) assertNoGuilt(Motivation.spillLine(800, a))
    }

    // ---- nudges ----

    private val m115 = Milestone(115.0, "First 5 kg", null, LocalDate.of(2026, 12, 17))

    @Test fun morningNudgeOnTrack() {
        assertEquals(
            "Down 0.2 kg on trend" to "117.6 kg. 115 kg is still on track for 17 Dec.",
            Motivation.morningNudge(-0.2, 117.6, m115, 0, today),
        )
        assertEquals(
            "Down 0.2 kg on trend" to "117.6 kg. 115 kg is still on track for 17 Dec.",
            Motivation.morningNudge(-0.23, 117.64, m115, null, today),
        )
    }

    @Test fun morningNudgeShifted() {
        assertEquals(
            "117.6 kg. 115 kg moved 3 days earlier, to 17 Dec.",
            Motivation.morningNudge(-0.2, 117.6, m115, 3, today).second,
        )
        assertEquals(
            "117.6 kg. 115 kg moved 1 day earlier, to 17 Dec.",
            Motivation.morningNudge(-0.2, 117.6, m115, 1, today).second,
        )
        assertEquals("117.6 kg. 115 kg is now set for 17 Dec.", Motivation.morningNudge(-0.1, 117.6, m115, -2, today).second)
    }

    @Test fun morningNudgeUpAndSteady() {
        val up = Motivation.morningNudge(0.1, 117.7, m115, -1, today)
        assertEquals("Up 0.1 kg on trend", up.first)
        assertTrue(up.second.startsWith("117.7 kg. Normal day-to-day swing."))
        assertNoGuilt(up.first + up.second)
        assertEquals("Steady on trend", Motivation.morningNudge(0.02, 117.6, m115, 0, today).first)
        assertEquals("Steady on trend", Motivation.morningNudge(-0.04, 117.6, m115, 0, today).first)
    }

    @Test fun morningNudgeNoDateOrNoNext() {
        val stalled = m115.copy(projected = null)
        assertEquals(
            "117.6 kg. Keep logging and the date for 115 kg will come back.",
            Motivation.morningNudge(0.0, 117.6, stalled, null, today).second,
        )
        assertTrue(Motivation.morningNudge(-0.1, 84.9, null, null, today).second.contains("goal"))
    }

    @Test fun morningNudgeYearWhenNotThisYear() {
        val m = Milestone(110.0, "10 kg down", null, LocalDate.of(2027, 1, 31))
        assertEquals("112.4 kg. 110 kg is still on track for 31 Jan 2027.", Motivation.morningNudge(-0.1, 112.4, m, 0, today).second)
        assertEquals("112.4 kg. 110 kg is still on track for 31 Jan.", Motivation.morningNudge(-0.1, 112.4, m, 0, LocalDate.of(2027, 1, 2)).second)
    }

    @Test fun eveningNudgeUnder() {
        assertEquals(
            "Burned 520 kcal more than eaten, day 5 of your streak" to "You have room for a 300 kcal snack and still keep a solid deficit.",
            Motivation.eveningNudge(520, 5),
        )
        assertEquals("Burned 1,140 kcal more than eaten so far", Motivation.eveningNudge(1140, 1).first)
        assertEquals("You have room for a 900 kcal snack and still keep a solid deficit.", Motivation.eveningNudge(1140, 1).second)
        assertEquals("You've burned more than you've eaten today. A calm evening keeps it that way.", Motivation.eveningNudge(250, 0).second)
    }

    @Test fun eveningNudgeOverAndEvenAreKind() {
        val over = Motivation.eveningNudge(-300, 4)
        assertEquals("Eaten 300 kcal more than burned so far", over.first)
        assertTrue(over.second.contains("streak is still alive"))
        assertEquals("Right at even so far", Motivation.eveningNudge(0, 0).first)
        assertEquals("Eaten 1,250 kcal more than burned so far", Motivation.eveningNudge(-1250, 0).first)
        for (b in listOf(-1250, -300, 0)) for (s in listOf(0, 4)) {
            val (t, body) = Motivation.eveningNudge(b, s); assertNoGuilt(t + body)
        }
    }

    @Test fun eveningNudgeNoFood() {
        val (t, b) = Motivation.eveningNudge(null, 3)
        assertEquals("Log today's food?", t)
        assertTrue(b.contains("MyFitnessPal"))
        assertNoGuilt(t + b)
    }

    // ---- journey line ----

    @Test fun journeyLine() {
        assertEquals("A strong week moved 115 kg 3 days earlier, to 17 Dec.", Motivation.journeyLine(m115, 3, today))
        assertEquals("Recent days moved 115 kg 1 day earlier, to 17 Dec.", Motivation.journeyLine(m115, 1, today))
        assertEquals("115 kg is now set for 17 Dec, 2 days later. A steady week pulls it back.", Motivation.journeyLine(m115, -2, today))
        val goal = Milestone(85.0, "Goal", null, LocalDate.of(2027, 9, 24), isGoal = true)
        assertEquals("A strong week moved your 85 kg goal 5 days earlier, to 24 Sep 2027.", Motivation.journeyLine(goal, 5, today))
        assertEquals("Your 85 kg goal is now set for 24 Sep 2027, 1 day later. A steady week pulls it back.", Motivation.journeyLine(goal, -1, today))
    }

    @Test fun journeyLineNothingToSay() {
        assertNull(Motivation.journeyLine(null, 3, today))
        assertNull(Motivation.journeyLine(m115, null, today))
        assertNull(Motivation.journeyLine(m115, 0, today))
        assertNull(Motivation.journeyLine(m115.copy(projected = null), 3, today))
    }

    // ---- weekly recap ----

    private val monday23 = LocalDate.of(2026, 11, 23)

    private fun recap(days: List<DayLog>, on: LocalDate): WeeklyRecap? =
        Motivation.weeklyRecap(BHAVIK, days, Trend.of(days, START, on), on)

    @Test fun recapOnlyOnMondayAndTuesday() {
        val days = Fixtures.bhavikDays(30)
        assertEquals(DayOfWeek.MONDAY, monday23.dayOfWeek)
        assertNotNull(recap(days, monday23))
        assertNotNull(recap(days, monday23.plusDays(1)))
        for (i in 2..6) assertNull(recap(days, monday23.plusDays(i.toLong())))
        assertEquals(recap(days, monday23), recap(days, monday23.plusDays(1)))
    }

    @Test fun recapForBhavikWeek() {
        val days = Fixtures.bhavikDays(24)
        val r = recap(days, monday23)!!
        // 1 Nov 2026 is a Sunday, so week 1 is 26 Oct - 1 Nov and 16-22 Nov is week 4.
        assertEquals(4, r.weekNumber)
        assertEquals(LocalDate.of(2026, 11, 16), r.weekStart)
        assertEquals(7, r.daysUnder)
        assertEquals(7, r.daysLogged)
        assertEquals(7, r.focusDaysUnder)
        val week = days.filter { !it.date.isBefore(r.weekStart) && !it.date.isAfter(r.weekStart.plusDays(6)) }
        assertEquals(week.sumOf { it.steps!! }, r.totalSteps)
        val best = week.maxBy { it.balance!! }
        assertEquals(best.date, r.bestDay)
        assertEquals(best.balance, r.bestDayBalance)
        assertTrue(r.trendChangeKg < 0)
        val trend = Trend.of(days, START, monday23)
        val expected = trend.first { it.date == LocalDate.of(2026, 11, 22) }.trend - trend.first { it.date == LocalDate.of(2026, 11, 15) }.trend
        assertEquals(Format.round1(expected), r.trendChangeKg, 1e-9)
        assertEquals("Your best week yet, and three weeks in a row of losing.", r.headline)
        // steps average ~9,000 < 10,000 and no over days -> steps tip
        assertTrue(r.focusTip, r.focusTip.startsWith("Your steps averaged"))
        assertTrue(r.focusTip.contains("10,000"))
    }

    @Test fun recapFirstWeek() {
        val days = Fixtures.bhavikDays(1)
        val r = recap(days, LocalDate.of(2026, 11, 2))!!
        assertEquals(1, r.weekNumber)
        assertEquals(LocalDate.of(2026, 10, 26), r.weekStart)
        assertEquals(1, r.daysLogged)
        assertEquals(1, r.daysUnder)
        assertEquals(2, r.focusDaysUnder)
        assertEquals(0.0, r.trendChangeKg, 1e-9)
        assertEquals("A steady week. The trend held, and that's fine.", r.headline)
    }

    @Test fun recapNullBeforeStartOrWithoutData() {
        val days = Fixtures.bhavikDays(24)
        assertNull(recap(days, LocalDate.of(2026, 10, 26)))           // last week is before week 1
        assertNull(recap(days, LocalDate.of(2026, 12, 7)))            // 30 Nov - 6 Dec: no data
        assertNull(recap(emptyList(), monday23))
    }

    @Test fun recapFocusTipFromRepeatedOverWeekday() {
        // Saturdays 7, 14 and 21 Nov over, mostly dinner.
        val saturdays = setOf(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 21))
        val days = Fixtures.bhavikDays(24).map {
            if (it.date in saturdays) it.copy(eaten = 3400, meals = Meals(400, 600, 2000, 400)) else it
        }
        val r = recap(days, monday23)!!
        assertEquals("Saturdays were the days you ate more than you burned three times. Plan Saturday dinner by Friday night.", r.focusTip)
        assertEquals(6, r.daysUnder)
        assertEquals(7, r.focusDaysUnder)
        // the tip names the meal that was biggest on those days
        val snacky = Fixtures.bhavikDays(24).map {
            if (it.date in saturdays.take(2)) it.copy(eaten = 3400, meals = Meals(300, 500, 600, 2000)) else it
        }
        assertEquals("Saturdays were the days you ate more than you burned twice. Plan Saturday snacks by Friday night.", recap(snacky, monday23)!!.focusTip)
    }

    @Test fun recapSingleOverDayFallsBackToProteinWhenStepsAreGood() {
        val days = Fixtures.bhavikDays(24, steps = 11_000).map {
            if (it.date == LocalDate.of(2026, 11, 18)) it.copy(eaten = 3400) else it
        }
        val r = recap(days, monday23)!!
        assertTrue(r.focusTip, r.focusTip.contains("protein"))
        assertEquals(6, r.daysUnder)
    }

    @Test fun recapUpWeekIsKind() {
        val days = (0 until 24).map { i ->
            DayLog(START.plusDays(i.toLong()), steps = 5000, burnedTotal = 2400, eaten = 2600, weightKg = 120.0 + i * 0.1)
        }
        val r = recap(days, monday23)!!
        assertTrue(r.trendChangeKg > 0)
        assertEquals(0, r.daysUnder)
        assertNull(r.bestDay)
        assertNull(r.bestDayBalance)
        assertEquals(1, r.focusDaysUnder)
        assertTrue(r.headline, r.headline.contains("fresh start"))
        assertNoGuilt(r.headline + r.focusTip)
    }

    // ---- ring style ----

    @Test fun ringStyleCycles() {
        assertEquals(RingStyle.MINT, Motivation.ringStyle(0))
        assertEquals(RingStyle.AURORA, Motivation.ringStyle(1))
        assertEquals(RingStyle.SUNRISE, Motivation.ringStyle(2))
        assertEquals(RingStyle.PRISM, Motivation.ringStyle(7))
        assertEquals(RingStyle.MINT, Motivation.ringStyle(8))
        assertEquals(RingStyle.AURORA, Motivation.ringStyle(9))
        assertEquals(RingStyle.MINT, Motivation.ringStyle(-3))
    }

    // ---- celebration ----

    @Test fun celebrationLine() {
        assertEquals(
            "2 kg gone in 18 days. That's two big water bottles you no longer carry up every flight of stairs.",
            Motivation.celebrationLine(BHAVIK, Milestone(118.0, "2 kg", START.plusDays(18), null), 18),
        )
        assertEquals(
            "5 kg gone in 1 day. That's a bowling ball you no longer carry up every flight of stairs.",
            Motivation.celebrationLine(BHAVIK, Milestone(115.0, "First 5 kg", START, null), 1),
        )
        assertEquals(
            "You reached your goal. 35 kg gone in 330 days. That's a sack of potatoes and a car tyre you no longer carry up every flight of stairs.",
            Motivation.celebrationLine(BHAVIK, Milestone(85.0, "Goal", START, null, isGoal = true), 330),
        )
        assertEquals(
            "6 kg gone in 40 days. That's a bowling ball you no longer carry up every flight of stairs.",
            Motivation.celebrationLine(BHAVIK, Milestone(114.0, "5% down", START, null), 40),
        )
    }

    private fun assertNoGuilt(s: String) {
        val lower = s.lowercase()
        for (bad in listOf("fail", "bad", "guilt", "cheat", "ruin", "blew", "should have", "!")) {
            assertFalse("'$bad' in: $s", lower.contains(bad))
        }
    }
}
