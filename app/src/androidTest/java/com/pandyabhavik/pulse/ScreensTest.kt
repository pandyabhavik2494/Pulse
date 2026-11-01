package com.pandyabhavik.pulse

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pandyabhavik.pulse.core.Forecaster
import com.pandyabhavik.pulse.ui.F
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Walks every screen in realistic states over a fake Health Connect with a pinned clock, and saves a
 * screenshot of each to /sdcard/Download/pulse-screens (pulled into the CI results). CI runs it at the Fold's
 * cover-screen size (with font scale 1.15) and at its inner-screen size; the app picks its layout from the
 * width, and so do these tests. Key numbers and texts are asserted, and the main elements are checked to
 * be fully on screen and not cut off, so a broken screen fails the run instead of only looking wrong.
 */
@RunWith(AndroidJUnit4::class)
class ScreensTest {
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

    /** Today's tagged essentials, all on screen and whole. */
    private val todayEssentials = listOf("balance_ring", "balance_value", "balance_label", "steps_value", "weight_trend_value", "next_milestone", "streak_chip", "heart_resting", "sleep_value")

    @Test
    fun onboarding() {
        app.open(Scene.FRESH)
        app.waitTag("onboarding_screen")
        Onboarding.assertStep1(app)
        shot("01-onboarding-about-you")
        app.assertNoneClipped(required = listOf("onboarding_name", "onboarding_height", "onboarding_age", "onboarding_weight", "onboarding_goal", "onboarding_next"))

        Onboarding.fillStep1(app)
        shot("02-onboarding-filled")

        app.click("onboarding_next")
        app.waitTag("onboarding_start")
        app.assertText("Connect your data")
        shot("03-onboarding-connect")
        app.assertNoneClipped(required = listOf("onboarding_start"), optional = listOf("onboarding_connect"))
        // The fake Health Connect has every permission already; Start finishes.
        app.click("onboarding_start")
        app.backToApp()
        app.waitTag("today_screen", 20_000)
        app.waitFor("the profile to be saved") { app.settings().profile != null }
        shot("04-onboarding-done-today")
    }

    @Test
    fun todayUnder() {
        app.open(Scene.UNDER)
        app.waitTag("today_screen")
        app.waitTag("balance_value")
        // Burned 2,480 - eaten 1,960 = 520 under, as in the approved mock.
        app.assertTagShows("balance_value", n(TestData.TODAY_BALANCE), substring = false)
        app.assertTagShows("balance_label", "kcal under", substring = false)
        app.assertTagShows("steps_value", n(TestData.TODAY_STEPS))
        val s = checkNotNull(app.seeded)
        app.assertTagShows("weight_trend_value", kg1(checkNotNull(s.trendKg)))
        assertTrue("sections start collapsed", !app.exists("streak_chip") && !app.exists("next_milestone"))
        app.assertTagShows("heart_resting", "${TestData.RESTING_HR}")
        app.assertTagShows("sleep_value", TestData.TODAY_SLEEP_SHORT)
        app.waitTag("dashboard")
        shot("05-today-under")
        app.expand("balance_ring", "streak_chip")
        app.expand("section_body", "next_milestone")
        app.assertTagShows("streak_chip", "${s.streak} days under in a row")
        assertTrue("streak should run across the whole month, was ${s.streak}", s.streak >= 10)
        val next = checkNotNull(s.forecast?.next)
        app.assertTagShows("next_milestone", "${F.kgShort(next.kg)} kg")
        next.projected?.let { app.assertTagShows("next_milestone", F.date(it, s.now.toLocalDate())) }
        assertTrue("no recap on Tuesday once dismissed", !app.exists("recap_card"))
        assertTrue("no celebration", !app.exists("celebration"))
        app.assertText("Evening, Bhavik", substring = true)
        app.scrollTo("balance_ring")
        shot("05b-today-calories-open")
        app.assertNoneClipped(required = todayEssentials)
        app.expand("section_activity", "distance_value")
        app.expand("section_heart", "heart_rows")
        app.expand("section_sleep", "sleep_rows")
        app.scrollTo("section_heart")
        shot("05c-today-all-open")
    }

    @Test
    fun todayOver() {
        app.open(Scene.OVER)
        app.waitTag("balance_value")
        app.assertTagShows("balance_value", n(TestData.OVER_BY), substring = false)
        app.assertTagShows("balance_label", "kcal over", substring = false)
        shot("06-today-over")
        app.expand("balance_ring", "streak_chip")
        app.assertText("Eaten more than burned")
        app.assertNoneClipped(required = listOf("balance_ring", "balance_value", "balance_label", "steps_value"))
        if (app.scrollDownToShow("weight_trend_value")) shot("07-today-over-scrolled")
    }

    @Test
    fun breakdown() {
        app.open(Scene.UNDER)
        app.waitTag("balance_ring")
        app.expand("balance_ring", "meal_breakdown")
        app.click("meal_breakdown")
        app.waitTag("breakdown_sheet")
        app.assertText(n(TestData.TODAY_BURNED))
        app.assertText("Breakfast")
        app.assertText("420")
        app.assertText("Dinner")
        app.assertText("128 g")
        app.assertText("520 under")
        shot("08-breakdown-sheet")
        app.back()
        app.waitGone("breakdown_sheet")
    }

    @Test
    fun celebration() {
        app.open(Scene.MILESTONE)
        app.waitTag("celebration", 15_000)
        val m = checkNotNull(app.seeded?.celebrate) { "The milestone scene should have a milestone to celebrate" }
        assertEquals(115.0, m.kg, 0.001)
        app.assertText("115", substring = true)
        shot("09-celebration")
        app.assertNoneClipped(required = listOf("celebration_continue"))
        app.click("celebration_continue")
        app.waitGone("celebration")
        app.waitTag("today_screen")
        shot("10-today-after-celebration")
    }

    @Test
    fun mondayRecap() {
        app.open(Scene.MONDAY)
        app.waitTag("recap_card")
        val r = checkNotNull(app.seeded?.recap) { "Monday 23 Nov should have last week's recap" }
        app.assertText("Beat last week: ${r.focusDaysUnder} days under.")
        app.assertText(r.focusTip, substring = true)
        shot("11-monday-recap")
        app.assertNoneClipped(required = listOf("recap_card", "recap_commit"))
        app.click("recap_commit")
        app.waitGone("recap_commit")
        app.waitText("You're in.", substring = true)
        shot("12-monday-recap-committed")
    }

    @Test
    fun noFoodYet() {
        app.open(Scene.NO_FOOD)
        app.waitTag("today_screen")
        app.waitText("No food logged yet")
        assertTrue("no balance number before any food", !app.exists("balance_value"))
        shot("13-today-no-food-yet")
        app.assertNoneClipped(required = listOf("balance_ring", "steps_value", "weight_trend_value"))
    }

    @Test
    fun stalled() {
        app.open(Scene.STALLED)
        app.waitTag("today_screen")
        assertEquals(com.pandyabhavik.pulse.core.RateSource.STALLED, app.seeded?.forecast?.source)
        shot("14-today-stalled")
        app.tab("journey")
        app.waitTag("journey_line")
        app.assertTagShows("journey_line", "Get back under", substring = true)
        shot("15-journey-stalled")
    }

    @Test
    fun healthConnectAccessOff() {
        app.open(Scene.NO_PERMISSIONS)
        app.waitTag("today_screen")
        app.waitText("Health Connect access is off", substring = true)
        shot("16-today-health-access-off")
        assertTrue(app.state().missingPermissions.isNotEmpty())
        app.tab("settings")
        app.scrollTo("settings_health")
        app.assertTagShows("settings_health", "Allow access")
        app.assertNotClipped("settings_health")
        shot("17-settings-health-access-off")
    }

    @Test
    fun journey() {
        app.open(Scene.UNDER)
        app.tab("journey")
        app.waitTag("journey_chart")
        app.waitTag("milestone_list")
        app.waitTag("journey_line")
        val s = checkNotNull(app.seeded)
        // The milestones: 115 First 5 kg next, then 114 (5% down), down to 85.
        app.assertText("First 5 kg", substring = true)
        app.assertText("5% down", substring = true)
        shot("18-journey")
        app.assertNoneClipped(required = listOf("journey_chart", "journey_line"))

        // What if: drag the slider to 1,000 kcal a day; the goal date follows.
        app.scrollTo("whatif_slider")
        val before = app.textOf("whatif_result")
        app.node("whatif_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(1000f) }
        app.idle()
        app.waitText("1,000 kcal under", substring = true)
        val after = app.textOf("whatif_result")
        assertNotEquals("the what-if result should change with the slider", before, after)
        val expected = Forecaster.whatIf(TestData.PROFILE, checkNotNull(s.trendKg), s.now.toLocalDate(), 1000)
        assertNotNull(expected)
        app.assertTagShows("whatif_result", F.date(expected!!, s.now.toLocalDate()))
        shot("19-journey-what-if-1000")
        app.assertNoneClipped(required = listOf("whatif_slider", "whatif_result"))
    }

    @Test
    fun history() {
        app.open(Scene.UNDER)
        app.tab("history")
        val sat = TestData.SAT_21
        if (app.wide) {
            // Inner: the month calendar beside the selected day (today at first).
            app.waitTag("history_calendar")
            app.waitTag("day_detail")
            app.assertText(F.monthYear.format(TestData.NOW), substring = true)
            shot("20-history-calendar")
            app.assertNoneClipped(required = listOf("history_calendar", "day_detail", "day_$sat"))
            app.click("day_$sat")
            app.waitFor("Sat 21 Nov's numbers") { app.textOf("day_detail").contains(TestData.SAT_21_SLEEP) }
            app.assertTagShows("day_detail", F.dayShort(sat))
            app.assertTagShows("day_detail", "180")
            app.assertTagShows("day_detail", "kcal over")
            app.assertTagShows("day_detail", "2,630")
            shot("21-history-calendar-sat-21")
            app.assertNoneClipped(required = listOf("day_detail"))
        } else {
            // Cover: the last 7 days in rows; a tap opens the day (with its sleep) in a sheet.
            app.waitTag("history_list")
            app.waitTag("history_row_$sat")
            app.assertText("180 over")
            app.assertText("520 under")
            shot("20-history-list")
            app.assertNoneClipped(required = listOf("history_list", "history_row_$sat"))
            app.click("history_row_$sat")
            app.waitTag("day_sheet")
            app.waitTag("day_detail")
            app.assertTagShows("day_detail", F.dayShort(sat))
            app.assertTagShows("day_detail", "180")
            app.assertTagShows("day_detail", TestData.SAT_21_SLEEP)
            shot("21-history-day-sat-21")
            app.back()
            app.waitGone("day_sheet")
            // The cover's month calendar.
            app.click("history_toggle")
            app.waitTag("history_calendar")
            shot("22-history-cover-calendar")
            app.assertNoneClipped(required = listOf("history_calendar"))
        }
    }

    @Test
    fun settings() {
        app.open(Scene.UNDER)
        app.tab("settings")
        app.waitTag("settings_nudge_morning")
        app.waitTag("settings_nudge_evening")
        app.waitTag("settings_goal")
        app.assertTagShows("settings_goal", "85", substring = true)
        app.assertTagShows("settings_name", "Bhavik")
        shot("23-settings")
        app.assertNoneClipped(required = listOf("settings_goal", "settings_name"))
        app.assertNoneClipped(required = listOf("settings_nudge_morning", "settings_nudge_evening"))
        app.waitText("all access allowed", substring = true)
        if (app.scrollDownToShow("settings_health")) shot("24-settings-scrolled")
    }

    /**
     * Day one with nothing in Health Connect yet: every screen must still read sensibly, with plain
     * "nothing yet" lines instead of numbers, and Sync now must say what happened.
     */
    @Test
    fun emptyDayOne() {
        app.open(Scene.EMPTY)
        app.waitTag("today_screen")
        val s = checkNotNull(app.seeded)
        assertTrue("no days in the empty scene", s.history.all { it.steps == null && it.eaten == null && it.weightKg == null })
        app.waitText("No food logged yet")
        assertTrue("no balance number before any food", !app.exists("balance_value"))
        app.assertTagShows("steps_value", "0")
        app.assertText("No heart data yet")
        app.assertText("No sleep recorded last night")
        shot("30-empty-today")
        app.expand("balance_ring", "streak_chip")
        app.expand("section_body", "next_milestone")
        app.assertNoneClipped(required = listOf("balance_ring", "balance_label", "steps_value", "next_milestone", "streak_chip"))
        if (!app.wide && app.scrollDownToShow("streak_chip")) shot("30b-empty-today-scrolled")

        app.tab("journey")
        app.waitTag("journey_screen")
        shot("31-empty-journey")

        app.tab("history")
        app.waitTag("history_screen")
        shot("32-empty-history")

        app.tab("settings")
        app.waitTag("settings_screen")
        app.scrollTo("settings_sync")
        app.click("settings_sync")
        app.waitTag("settings_sync_note")
        app.waitFor("the sync note") { app.textOf("settings_sync_note").contains("Synced") }
        app.assertNotClipped("settings_sync_note")
        shot("33-empty-settings-synced")
    }
}
