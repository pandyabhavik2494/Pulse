package com.pandyabhavik.pulse.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MoodTest {
    private val seven = LocalTime.of(6, 58)
    private val noon = LocalTime.of(12, 0)
    private val eight = LocalTime.of(20, 5)

    @Test fun earlyStepsAreNeverJudged() {
        val s = Moods.steps(60, 10_000, seven)
        assertEquals(Mood.WAIT, s.mood)
        assertEquals("Early", s.word)
    }

    @Test fun stepsFollowThePaceOfTheDay() {
        // Noon is a third of the waking day: ~3,333 expected.
        assertEquals(Mood.GOOD, Moods.steps(3_200, 10_000, noon).mood)
        assertEquals(Mood.STEADY, Moods.steps(2_400, 10_000, noon).mood)
        assertEquals(Mood.GROW, Moods.steps(900, 10_000, noon).mood)
        assertEquals(Mood.GOOD, Moods.steps(10_500, 10_000, seven).mood)
        assertEquals(Mood.STEADY, Moods.steps(6_240, 10_000, eight).mood)
    }

    @Test fun burnedAgainstTheUsualByNow() {
        // 2,450 a day; by 6:58 about 711 is usual.
        assertEquals(Mood.GOOD, Moods.burned(634, 2_450, seven).mood)
        assertEquals(Mood.WAIT, Moods.burned(null, 2_450, seven).mood)
        assertEquals(Mood.WAIT, Moods.burned(100, 2_450, LocalTime.of(1, 0)).mood)
        assertEquals(Mood.GROW, Moods.burned(300, 2_450, noon).mood)
    }

    @Test fun foodStates() {
        assertEquals(Mood.WAIT, Moods.eaten(null, 1_600).mood)
        assertEquals(Mood.GOOD, Moods.eaten(1_500, 1_600).mood)
        assertEquals(Mood.STEADY, Moods.eaten(1_750, 1_600).mood)
        val over = Moods.eaten(2_610, 2_310)
        assertEquals(Mood.GROW, over.mood)
        assertTrue(over.why.contains("300 above"))
        assertEquals(Mood.GOOD, Moods.balance(520).mood)
        assertEquals(Mood.STEADY, Moods.balance(-150).mood)
        assertEquals(Mood.GROW, Moods.balance(-500).mood)
        assertEquals(Mood.GOOD, Moods.left(1_599, 1_599).mood)
        assertEquals(Mood.STEADY, Moods.left(120, 1_599).mood)
        assertEquals(Mood.GROW, Moods.left(-170, 1_599).mood)
    }

    @Test fun weightIsNeverWorseThanSteady() {
        assertEquals(Mood.GOOD, Moods.weight(119.8, 120.1)!!.mood)
        assertEquals(Mood.STEADY, Moods.weight(120.2, 120.1)!!.mood)
        assertEquals(Mood.STEADY, Moods.weight(122.5, 120.1)!!.mood)
        assertNull(Moods.weight(null, 120.0))
    }

    @Test fun trendNeedsAWeek() {
        val today = LocalDate.of(2026, 11, 2)
        assertEquals(Mood.WAIT, Moods.trend(listOf(TrendPoint(today, 120.2, 120.1)), today).mood)
        val down = (0..10).map { TrendPoint(today.minusDays(10L - it), 120.0 - it * 0.1, 120.0 - it * 0.08) }
        assertEquals(Mood.GOOD, Moods.trend(down, today).mood)
        val up = (0..10).map { TrendPoint(today.minusDays(10L - it), 118.0 + it * 0.1, 118.0 + it * 0.08) }
        assertEquals(Mood.GROW, Moods.trend(up, today).mood)
    }

    @Test fun restingAgainstOwnUsual() {
        assertEquals(Mood.STEADY, Moods.resting(84, emptyList())!!.mood)
        val usual = List(10) { 80 }
        assertEquals(Mood.GOOD, Moods.resting(79, usual)!!.mood)
        assertEquals(Mood.STEADY, Moods.resting(83, usual)!!.mood)
        assertEquals(Mood.GROW, Moods.resting(88, usual)!!.mood)
    }

    @Test fun sleepAndDeep() {
        assertEquals(Mood.GOOD, Moods.sleep(425)!!.mood)
        assertEquals("You slept 7h 5m, nice.", Moods.sleep(425)!!.win)
        assertEquals(Mood.STEADY, Moods.sleep(390)!!.mood)
        assertEquals(Mood.GROW, Moods.sleep(300)!!.mood)
        assertEquals(Mood.STEADY, Moods.deep(56)!!.mood)
        assertEquals(Mood.GOOD, Moods.deep(70)!!.mood)
        assertEquals(Mood.GROW, Moods.deep(30)!!.mood)
    }

    @Test fun headlinesStayKind() {
        assertEquals("Good start", Moods.headline(listOf(Mood.GOOD, Mood.GOOD, Mood.STEADY, Mood.WAIT), seven))
        assertEquals("Going well", Moods.headline(listOf(Mood.GOOD), eight))
        assertEquals("Close to plan", Moods.headline(listOf(Mood.GROW, Mood.GROW, Mood.GOOD), eight))
        val words = listOf("Good start", "Going well", "Steady day", "Mostly on track", "Close to plan")
        assertTrue(words.none { it.contains("over", ignoreCase = true) || it.contains("bad", ignoreCase = true) })
        assertFalse(Mood.values().any { it.name == "BAD" })
    }
}
