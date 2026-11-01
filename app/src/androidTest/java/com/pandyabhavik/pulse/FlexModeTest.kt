package com.pandyabhavik.pulse

import android.graphics.Rect
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.window.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import androidx.window.testing.layout.FoldingFeature as testFoldingFeature

/**
 * Flex mode: the inner screen half-folded like a laptop (a horizontal hinge, HALF_OPENED). androidx.window's
 * test publisher feeds the app that posture. The top half must be the big status display (ring, steps,
 * weight) and the bottom half the details and the navigation, with nothing across the hinge. Only on the
 * inner screen: the cover screen doesn't fold.
 */
@RunWith(AndroidJUnit4::class)
class FlexModeTest {
    private val publisher = WindowLayoutInfoPublisherRule()
    private val compose = createEmptyComposeRule()
    private val app = AppDriver(compose)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(publisher).around(compose)

    @Before
    fun setUp() = app.setUp()

    @After
    fun tearDown() = app.tearDown()

    /**
     * Tells the app it is half-opened with a horizontal hinge across the middle, and waits for the flex
     * layout (the nav row below the hinge). The publisher doesn't replay to late collectors, so it says it
     * again until the app has heard. Returns the hinge in window px.
     */
    private fun halfOpen(): Rect {
        var feature: FoldingFeature? = null
        app.scenario!!.onActivity {
            feature = testFoldingFeature(
                activity = it,
                state = FoldingFeature.State.HALF_OPENED,
                orientation = FoldingFeature.Orientation.HORIZONTAL,
            )
        }
        val f = checkNotNull(feature)
        val hinge = f.bounds
        fun inFlex() = app.exists("nav_today") && top("nav_today") >= hinge.bottom - 2
        repeat(20) {
            publisher.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(f)))
            if (app.within(500) { inFlex() }) return hinge
        }
        throw AssertionError("The app never switched to flex mode (nav row below the hinge at y ${hinge.bottom})")
    }

    private fun top(tag: String) = app.node(tag).fetchSemanticsNode().boundsInWindow.top
    private fun bottom(tag: String) = app.node(tag).fetchSemanticsNode().boundsInWindow.bottom

    private fun assertAbove(tag: String, hinge: Rect) {
        val b = bottom(tag)
        if (b > hinge.top + 2) throw AssertionError("In flex mode '$tag' should sit above the hinge (y ${hinge.top}) but reaches down to $b")
    }

    private fun assertBelow(tag: String, hinge: Rect) {
        val t = top(tag)
        if (t < hinge.bottom - 2) throw AssertionError("In flex mode '$tag' should sit below the hinge (y ${hinge.bottom}) but starts at $t")
    }

    @Test
    fun halfFoldedOnTheInnerScreen() {
        app.open(Scene.UNDER)
        assumeTrue("Flex mode is an inner-screen posture", app.wide)
        app.waitTag("today_screen")
        // Flex mode puts the nav row (with Settings) at the bottom instead of the rail.
        val hinge = halfOpen()

        app.waitTag("balance_value")
        app.assertTagShows("balance_value", n(TestData.TODAY_BALANCE), substring = false)
        app.assertTagShows("steps_value", n(TestData.TODAY_STEPS))
        val trend = app.seeded?.trendKg?.let { String.format(java.util.Locale.US, "%.1f", it) }
        if (trend != null) app.assertTagShows("weight_trend_value", trend)
        for (t in listOf("balance_ring", "balance_value", "steps_value", "weight_trend_value")) assertAbove(t, hinge)
        for (t in listOf("nav_today", "nav_journey", "nav_history", "nav_settings")) assertBelow(t, hinge)
        assertBelow("today_screen", hinge)
        app.assertNoneClipped(
            required = listOf("balance_ring", "balance_value", "balance_label", "steps_value", "weight_trend_value", "nav_today", "nav_settings"),
            optional = listOf("heart_resting", "sleep_value"),
        )
        app.shot("70-flex-today")

        // The other screens keep the status display on top and show themselves below.
        app.tab("journey")
        assertBelow("journey_screen", hinge)
        assertAbove("balance_ring", hinge)
        app.shot("71-flex-journey")

        app.tab("history")
        assertBelow("history_screen", hinge)
        app.shot("72-flex-history")

        app.tab("settings")
        assertBelow("settings_screen", hinge)
        app.shot("73-flex-settings")

        // Over: the ring turns red up top, the calm line sits below.
        app.open(Scene.OVER)
        val hinge2 = halfOpen()
        app.assertTagShows("balance_value", n(TestData.OVER_BY), substring = false)
        app.assertTagShows("balance_label", "kcal over")
        assertAbove("balance_value", hinge2)
        app.shot("74-flex-today-over")
    }
}
