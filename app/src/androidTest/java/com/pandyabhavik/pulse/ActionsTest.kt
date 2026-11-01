package com.pandyabhavik.pulse

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pandyabhavik.pulse.core.Forecaster
import com.pandyabhavik.pulse.core.Meals
import com.pandyabhavik.pulse.core.Motivation
import com.pandyabhavik.pulse.ui.F
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Does what a person can do in Pulse, end to end, and checks each action reached the stored settings or
 * the repository's state (not only the screen), and that it survives reopening the app. Screenshots of the
 * important moments go next to the screen tests' ones (40-69).
 */
@RunWith(AndroidJUnit4::class)
class ActionsTest {
    private val compose = createEmptyComposeRule()
    private val app = AppDriver(compose)

    @get:Rule
    val rule = compose

    @Before
    fun setUp() = app.setUp()

    @After
    fun tearDown() = app.tearDown()

    private fun shot(name: String) = app.shot(name)

    private fun kg1(v: Double) = String.format(Locale.US, "%.1f", v)

    // ---- Onboarding --------------------------------------------------------------------------------

    @Test
    fun onboardingEndToEndSavesTheProfile() {
        app.open(Scene.FRESH)
        app.waitTag("onboarding_screen")
        assertEquals(null, app.settings().profile)
        Onboarding.fillStep1(app)
        shot("40-onboarding-filled")
        app.click("onboarding_next")
        app.waitTag("onboarding_start")
        app.click("onboarding_start")
        app.backToApp()
        app.waitFor("the profile to be saved", 15_000) { app.settings().profile != null }
        val p = checkNotNull(app.settings().profile)
        assertEquals("Bhavik", p.name)
        assertEquals(175.3, p.heightCm, 0.6)
        assertEquals(32, p.age)
        assertTrue("male", p.male)
        assertEquals(120.0, p.startKg, 0.05)
        assertEquals(85.0, p.goalKg, 0.05)
        app.waitTag("today_screen", 20_000)
        // The first sync ran over the fake: today's numbers are in.
        app.waitTag("balance_value", 15_000)
        app.assertTagShows("balance_value", n(TestData.TODAY_BALANCE), substring = false)
        shot("41-onboarding-saved-today")

        app.relaunch()
        app.waitTag("today_screen")
        assertFalse("onboarding shouldn't come back", app.exists("onboarding_screen"))
    }

    // ---- Celebration -------------------------------------------------------------------------------

    @Test
    fun celebrationShowsOnceThenNeverAgain() {
        app.open(Scene.MILESTONE)
        app.waitTag("celebration", 15_000)
        assertFalse(app.settings().isCelebrated(115.0))
        shot("42-celebration")
        app.click("celebration_continue")
        app.waitGone("celebration")
        app.waitFor("115 kg to be stored as celebrated") { app.settings().isCelebrated(115.0) }
        assertEquals(null, app.state().celebrate)

        app.relaunch()
        app.waitTag("today_screen")
        // Give it the time it'd take to fade in, then make sure it didn't.
        assertFalse("the celebration came back after reopening", app.within(3_000) { app.exists("celebration") })
        shot("43-after-celebration-reopened")
    }

    // ---- Monday recap ------------------------------------------------------------------------------

    @Test
    fun committingToTheRecapPersists() {
        app.open(Scene.MONDAY)
        app.waitTag("recap_commit")
        val week = checkNotNull(app.state().recap).weekStart
        assertEquals(null, app.settings().recapCommittedWeek)
        app.click("recap_commit")
        app.waitFor("the commitment to be stored") { app.settings().recapCommittedWeek == week }
        assertTrue(app.state().recapCommitted)
        app.waitGone("recap_commit")
        shot("44-recap-committed")

        app.relaunch()
        app.waitTag("recap_card")
        assertFalse("'I'm in' should stay done after reopening", app.exists("recap_commit"))
        app.assertText("You're in.", substring = true)
        shot("45-recap-committed-reopened")
    }

    // ---- Nudges ------------------------------------------------------------------------------------

    private fun toggleState(tag: String): ToggleableState? {
        fun find(n: androidx.compose.ui.semantics.SemanticsNode): ToggleableState? {
            if (SemanticsProperties.ToggleableState in n.config) return n.config[SemanticsProperties.ToggleableState]
            for (c in n.children) find(c)?.let { return it }
            return null
        }
        return runCatching { find(app.node(tag).fetchSemanticsNode()) }.getOrNull()
    }

    @Test
    fun nudgeTogglesPersist() {
        app.open(Scene.UNDER)
        app.tab("settings")
        app.waitTag("settings_nudge_morning")
        assertTrue(app.settings().morningNudge)
        assertTrue(app.settings().eveningNudge)
        toggleState("settings_nudge_morning")?.let { assertEquals(ToggleableState.On, it) }

        app.click("settings_nudge_morning")
        app.waitFor("the morning nudge to be off") { !app.settings().morningNudge }
        // The evening switch saves both values from what the screen shows, so let the screen catch up first.
        app.waitFor("the morning switch to show off") { toggleState("settings_nudge_morning") != ToggleableState.On }
        assertTrue("the evening nudge shouldn't change", app.settings().eveningNudge)
        app.click("settings_nudge_evening")
        app.waitFor("the evening nudge to be off") { !app.settings().eveningNudge }
        toggleState("settings_nudge_morning")?.let { assertEquals(ToggleableState.Off, it) }
        shot("46-nudges-off")

        app.relaunch()
        app.tab("settings")
        app.waitTag("settings_nudge_morning")
        toggleState("settings_nudge_morning")?.let { assertEquals("morning switch after reopening", ToggleableState.Off, it) }
        toggleState("settings_nudge_evening")?.let { assertEquals("evening switch after reopening", ToggleableState.Off, it) }
        assertFalse(app.settings().morningNudge)
        assertFalse(app.settings().eveningNudge)

        app.click("settings_nudge_morning")
        app.waitFor("the morning nudge to be back on") { app.settings().morningNudge }
        app.waitFor("the morning switch to show on") { toggleState("settings_nudge_morning") != ToggleableState.Off }
        assertFalse(app.settings().eveningNudge)
        shot("47-nudges-morning-back-on")
    }

    // ---- What if -----------------------------------------------------------------------------------

    @Test
    fun whatIfFollowsTheSlider() {
        app.open(Scene.UNDER)
        app.tab("journey")
        app.scrollTo("whatif_slider")
        val s = checkNotNull(app.seeded)
        val today = s.now.toLocalDate()
        val trend = checkNotNull(s.trendKg)
        val results = mutableListOf<String>()
        for ((i, kcal) in listOf(500, 1000, 1500).withIndex()) {
            app.node("whatif_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(kcal.toFloat()) }
            app.idle()
            app.waitText("${n(kcal)} kcal under", substring = true)
            val date = checkNotNull(Forecaster.whatIf(TestData.PROFILE, trend, today, kcal))
            app.assertTagShows("whatif_result", F.date(date, today))
            results += app.textOf("whatif_result")
            shot("${48 + i}-what-if-$kcal")
        }
        assertEquals("each slider position should give its own date: $results", 3, results.distinct().size)
        // Zero has no date at all.
        app.node("whatif_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        app.idle()
        app.assertTagShows("whatif_result", "Pick a deficit above 0", substring = true)
        // The plan itself didn't change.
        assertEquals(s.forecast?.goal?.projected, app.state().forecast?.goal?.projected)
    }

    // ---- Sync --------------------------------------------------------------------------------------

    @Test
    fun syncWithNewDataUpdatesBalanceAndForecast() {
        app.open(Scene.UNDER)
        app.waitTag("balance_value")
        app.assertTagShows("balance_value", n(TestData.TODAY_BALANCE), substring = false)
        val before = app.state()
        val beforeTrend = app.textOf("weight_trend_value")
        shot("51-before-sync")

        // A 300 kcal snack logged, a lighter weigh-in this morning, and yesterday's dinner corrected down.
        val today = TestData.NOW.toLocalDate()
        TestData.health.days = TestData.daysFor(Scene.UNDER).map {
            when (it.date) {
                today -> it.copy(eaten = 2260, meals = Meals(breakfast = 420, lunch = 680, dinner = 650, snacks = 510), weightKg = 115.4)
                today.minusDays(1) -> it.copy(eaten = 1350, meals = it.meals.copy(dinner = it.meals.dinner - 200))
                else -> it
            }
        }
        assertTrue("sync over the fake should succeed", runBlocking { app.repo.sync() })
        val after = app.state()
        assertEquals(TestData.TODAY_BURNED - 2260, after.balance)
        app.waitFor("the balance to update to 220") { norm(app.textOf("balance_value")) == n(220) }
        val newTrend = checkNotNull(after.trendKg)
        assertTrue("trend should drop with the lighter weigh-in", newTrend < checkNotNull(before.trendKg))
        app.waitFor("the trend to update") { app.textOf("weight_trend_value").contains(kg1(newTrend)) }
        assertNotEquals(beforeTrend, app.textOf("weight_trend_value"))
        val nextBefore = before.forecast?.next?.projected
        val nextAfter = after.forecast?.next?.projected
        assertNotNull(nextAfter)
        assertTrue("the next milestone should come no later ($nextBefore -> $nextAfter)", nextBefore == null || !nextAfter!!.isAfter(nextBefore))
        assertNotEquals("the forecast should change", before.forecast, after.forecast)
        app.expand("section_body", "next_milestone")
        nextAfter?.let { app.assertTagShows("next_milestone", F.date(it, today)) }
        shot("52-after-sync")
    }

    // ---- Forgiving streak --------------------------------------------------------------------------

    @Test
    fun forgivingStreakSurvivesAnOverDay() {
        app.open(Scene.OVER_YESTERDAY)
        val s = checkNotNull(app.seeded)
        val today = s.now.toLocalDate()
        val yesterday = s.history.first { it.date == today.minusDays(1) }
        assertTrue("yesterday should be over", (yesterday.balance ?: 0) < 0)
        val expected = Motivation.streak(TestData.daysFor(Scene.OVER_YESTERDAY), today)
        assertEquals(expected, s.streak)
        assertTrue("one over day mustn't reset the streak (was ${s.streak})", s.streak > 2)
        app.expand("balance_ring", "streak_chip")
        app.assertTagShows("streak_chip", "${s.streak} days under in a row")
        app.assertNotClipped("streak_chip")
        shot("53-forgiving-streak")
    }

    // ---- Dashboard ---------------------------------------------------------------------------------

    @Test
    fun dashboardCanBeCustomized() {
        app.open(Scene.UNDER)
        app.waitTag("dashboard")
        assertEquals(com.pandyabhavik.pulse.data.Settings.DEFAULT_DASHBOARD, app.settings().dashboard)
        assertTrue(app.exists("dash_burned"))
        assertFalse(app.exists("dash_hrv"))
        app.click("dashboard_edit")
        app.waitTag("dashboard_sheet")
        shot("54-dashboard-customize")
        // Drop Burned, add HRV and distance.
        app.click("dash_opt_burned")
        app.scrollTo("dash_opt_distance")
        app.click("dash_opt_distance")
        app.scrollTo("dash_opt_hrv")
        app.click("dash_opt_hrv")
        app.scrollTo("dashboard_save")
        app.click("dashboard_save")
        app.waitGone("dashboard_sheet")
        val want = listOf("calories", "steps", "weight", "resting_hr", "sleep", "distance", "hrv")
        app.waitFor("the dashboard choice to be saved") { app.settings().dashboard == want }
        app.waitTag("dash_hrv")
        assertFalse(app.exists("dash_burned"))
        app.assertTagShows("dash_hrv", "${app.state().history.take(7).firstNotNullOf { it.hrvMs }} ms")
        app.assertNoneClipped(listOf("dash_hrv", "dash_distance", "dash_calories"))
        shot("55-dashboard-customized")

        app.relaunch()
        app.waitTag("dash_hrv")
        assertFalse(app.exists("dash_burned"))

        // Reset puts the default six back.
        app.click("dashboard_edit")
        app.waitTag("dashboard_sheet")
        app.scrollTo("dashboard_reset")
        app.click("dashboard_reset")
        app.scrollTo("dashboard_save")
        app.click("dashboard_save")
        app.waitFor("the default dashboard to be back") { app.settings().dashboard == com.pandyabhavik.pulse.data.Settings.DEFAULT_DASHBOARD }
        app.waitTag("dash_burned")
    }
}
