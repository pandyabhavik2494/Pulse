package com.pandyabhavik.pulse

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pandyabhavik.pulse.widget.Widgets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Puts Pulse's four home-screen widgets on a test "home screen" (this process hosts them, the way a
 * launcher does) at the sizes they get on the screen the run is on: on the cover screen Today 2x2,
 * Journey 2x2, Next milestone 4x1 and Today full 4x2 at cover cell sizes; on the inner screen the same at
 * the inner screen's bigger cells. Screenshots them under, over and before onboarding, checking each draws
 * something (and the right numbers), never Glance's error layout. Then renders each widget's picker
 * preview (its previewLayout, through RemoteViews as a launcher does) and screenshots those too.
 */
@RunWith(AndroidJUnit4::class)
class WidgetsTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val repo get() = Graph.repo(context)

    /** Counts the RemoteViews the widget pushes, so the test knows when it has drawn (and redrawn). */
    private class CountingHostView(context: Context) : AppWidgetHostView(context) {
        val renders = AtomicInteger()
        @Volatile var lastLayoutId = 0

        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)
            // null is the host's own "loading" placeholder, not something the widget drew.
            if (remoteViews != null) {
                lastLayoutId = remoteViews.layoutId
                renders.incrementAndGet()
            }
        }
    }

    private class CountingHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
            CountingHostView(context)
    }

    /** One widget on the test home screen. Sizes are in dp. */
    private class Placed(val name: String, receiver: String, val widthDp: Int, val heightDp: Int, packageName: String) {
        val provider = ComponentName(packageName, "com.pandyabhavik.pulse.widget.$receiver")
        var id = AppWidgetManager.INVALID_APPWIDGET_ID
        var view: CountingHostView? = null
        val label get() = "$name ${widthDp}x$heightDp"
        fun renders() = view?.renders?.get() ?: 0

        fun options(): Bundle = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            if (Build.VERSION.SDK_INT >= 31) {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat())),
                )
            }
        }
    }

    private var root: FrameLayout? = null
    private var wide = false

    @Before
    fun setUp() {
        TestData.install()
        TestData.quietWorkManager(context)
    }

    @After
    fun tearDown() {
        // Leave the app as a fresh install would be, for whatever runs next.
        runBlocking { runCatching { repo.resetForTests() } }
    }

    private fun launchHome(): ActivityScenario<ComponentActivity> {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            wide = activity.resources.configuration.screenWidthDp >= 600
            val wallpaper = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(WALL_TOP, WALL_BOTTOM))
            activity.window.setBackgroundDrawable(ColorDrawable(WALL_TOP))
            val container = FrameLayout(activity).apply { background = wallpaper }
            root = container
            activity.setContentView(container)
        }
        waitForRootLayout(scenario)
        return scenario
    }

    @Test
    fun everyWidgetInEveryState() {
        runBlocking { TestData.seed(context, Scene.UNDER) }
        val pkg = context.packageName
        val scenario = launchHome()
        // Sizes in dp: the cover screen's home grid has ~156dp-wide 2x2 cells (mocks), the inner screen's ~190dp.
        val sq = if (wide) INNER_SQUARE else COVER_SQUARE
        val wideW = if (wide) INNER_WIDE else COVER_WIDE
        val stripH = if (wide) INNER_STRIP else COVER_STRIP
        val today = Placed("Today", "TodayWidgetReceiver", sq, sq, pkg)
        val journey = Placed("Journey", "JourneyWidgetReceiver", sq, sq, pkg)
        val milestone = Placed("Next milestone", "MilestoneWidgetReceiver", wideW, stripH, pkg)
        val full = Placed("Today full", "TodayFullWidgetReceiver", wideW, sq, pkg)
        val placed = listOf(today, journey, milestone, full)
        val screen = if (wide) "inner" else "cover"

        val manager = AppWidgetManager.getInstance(context)
        val installed = manager.getInstalledProvidersForPackage(pkg, null)
        val missing = placed.map { it.provider }.distinct().filter { p -> installed.none { it.provider == p } }
        if (missing.isNotEmpty()) fail("Widget providers not in the manifest: ${missing.joinToString { it.className }}")

        // Instrumentation runs in the app's process, so the app is the widget host and needs the bind grant
        // a launcher has. It is given per install, and Gradle has just (re)installed the app.
        val grant = Shots.shell("appwidget grantbind --package $pkg --user ${Process.myUid() / 100_000}").trim()
        Log.i(TAG, "appwidget grantbind: '$grant'")

        val host = CountingHost(context, HOST_ID)
        // A run that died half way may have left widgets bound to this host.
        runCatching { host.deleteHost() }
        host.startListening()
        val errors = mutableListOf<String>()
        try {
            for (w in placed) {
                w.id = host.allocateAppWidgetId()
                if (!manager.bindAppWidgetIdIfAllowed(w.id, w.provider, w.options())) {
                    fail("Not allowed to bind the ${w.label} widget (${w.provider.className}): `appwidget grantbind` didn't take effect (it said: '$grant').")
                }
            }
            scenario.onActivity { activity -> layOut(activity, host, placed, installed) }
            for (w in placed) {
                context.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setComponent(w.provider)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(w.id)),
                )
            }
            awaitRenders(placed, emptyMap(), 25_000)?.let { fail(it) }

            val under = TestData.TODAY_BALANCE
            val over = TestData.OVER_BY
            val steps = n(TestData.TODAY_STEPS)
            val moments = listOf(
                Moment(
                    Scene.UNDER, "80-widgets-$screen-under",
                    mapOf(
                        today to listOf("$under", "kcal under", "$steps steps"),
                        journey to listOf("JOURNEY", "kg trend"),
                        milestone to listOf("kg"),
                        full to listOf("$under", "under", steps, "WEIGHT"),
                    ),
                ),
                Moment(
                    Scene.OVER, "81-widgets-$screen-over",
                    mapOf(
                        today to listOf("$over", "kcal over"),
                        journey to listOf("kg trend"),
                        milestone to listOf("kg"),
                        full to listOf("$over", "over", steps),
                    ),
                ),
                Moment(
                    Scene.FRESH, "82-widgets-$screen-not-onboarded",
                    placed.associateWith { listOf("Open Pulse to start") },
                ),
            )
            for (m in moments) {
                val seeded = runBlocking { TestData.seed(context, m.scene) }
                // The trend is the repository's own number: check the widgets show the same one.
                val trend = seeded.trendKg?.let { String.format(java.util.Locale.US, "%.1f", it) }
                val expect = if (trend != null && m.scene != Scene.FRESH) {
                    m.expect.mapValues { (w, texts) -> if (w === journey || w === milestone || w === full) texts + trend else texts }
                } else {
                    m.expect
                }
                runBlocking { Widgets.refresh(context) }
                // Still take the screenshot when a widget is wrong: the picture shows which one.
                awaitContent(expect, 20_000)?.let { errors += "${m.scene}: $it" }
                settle(placed)
                checkDrawn(placed, m.scene.name)?.let { errors += it }
                shot(m.shot)
            }
        } finally {
            for (w in placed) {
                if (w.id != AppWidgetManager.INVALID_APPWIDGET_ID) runCatching { host.deleteAppWidgetId(w.id) }
            }
            runCatching { host.stopListening() }
            runCatching { host.deleteHost() }
            runCatching { scenario.close() }
            root = null
        }
        if (errors.isNotEmpty()) fail(errors.joinToString("\n"))
    }

    /**
     * The widget picker's previews: each provider's previewLayout, applied through RemoteViews the way a
     * launcher does (so a view RemoteViews doesn't allow fails here as it would there), at the widget's
     * target size, all on one screen.
     */
    @Test
    fun pickerPreviews() {
        if (Build.VERSION.SDK_INT < 31) return
        val pkg = context.packageName
        val installed = AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(pkg, null)
            .sortedBy { it.provider.className }
        if (installed.size < 4) fail("Expected 4 widget providers, found ${installed.map { it.provider.className }}")
        val scenario = launchHome()
        val errors = mutableListOf<String>()
        val previews = mutableListOf<Pair<String, View>>()
        try {
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                fun px(dp: Int) = (dp * density).roundToInt()
                val sq = if (wide) INNER_SQUARE else COVER_SQUARE
                val wideW = if (wide) INNER_WIDE else COVER_WIDE
                val stripH = if (wide) INNER_STRIP else COVER_STRIP
                val grid = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(px(PAD), px(PAD), px(PAD), px(PAD))
                }
                for (info in installed) {
                    val name = info.provider.className.substringAfterLast('.').removeSuffix("WidgetReceiver")
                    val layoutId = info.previewLayout
                    if (layoutId == 0) {
                        errors += "$name has no previewLayout"
                        continue
                    }
                    // Its target size: 4 cells wide when it asks for 4, 1 row high when it asks for 1.
                    val wDp = if (info.targetCellWidth >= 4) wideW else sq
                    val hDp = if (info.targetCellHeight <= 1) stripH else sq
                    val parent = FrameLayout(activity)
                    val view = try {
                        RemoteViews(pkg, layoutId).apply(activity, parent)
                    } catch (e: Exception) {
                        Log.e(TAG, "RemoteViews can't apply $name's preview", e)
                        errors += "$name's previewLayout can't be applied as RemoteViews (${e.javaClass.simpleName}: ${e.message}); a launcher would show nothing"
                        // Still show it, inflated directly, so the screenshot says what's wrong.
                        LayoutInflater.from(activity).inflate(layoutId, parent, false)
                    }
                    parent.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    previews += name to view
                    grid.addView(caption(activity, "$name preview ${wDp} × ${hDp} dp"), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(CAPTION)))
                    grid.addView(parent, LinearLayout.LayoutParams(px(wDp), px(hDp)).apply { bottomMargin = px(GAP) })
                }
                checkNotNull(root).addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            instrumentation.waitForIdleSync()
            for ((name, view) in previews) {
                var texts = emptyList<String>()
                var size = 0 to 0
                instrumentation.runOnMainSync {
                    texts = textsIn(view)
                    size = view.width to view.height
                }
                if (size.first <= 0 || size.second <= 0) errors += "$name's preview has no size ($size)"
                if (texts.none { it.isNotBlank() } && !hasImage(view)) errors += "$name's preview is empty"
            }
            shot("90-widget-previews-${if (wide) "inner" else "cover"}")
        } finally {
            runCatching { scenario.close() }
            root = null
        }
        if (errors.isNotEmpty()) fail(errors.joinToString("\n"))
    }

    private class Moment(val scene: Scene, val shot: String, val expect: Map<Placed, List<String>>)

    private fun caption(context: Context, text: String) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
        setTextColor(Color.WHITE)
        setShadowLayer(3f, 0f, 1f, 0x80000000.toInt())
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
    }

    private fun waitForRootLayout(scenario: ActivityScenario<ComponentActivity>) {
        val end = SystemClock.uptimeMillis() + 5_000
        while (true) {
            var laidOut = false
            scenario.onActivity { laidOut = (root?.width ?: 0) > 0 && (root?.height ?: 0) > 0 }
            if (laidOut) return
            if (SystemClock.uptimeMillis() > end) fail("The widget test's host activity never laid out")
            Thread.sleep(100)
        }
    }

    /**
     * Wraps the widgets into rows that fit the screen's width, each under a small caption. Widgets keep their
     * real size; if the grid is taller than the screen it is scaled down as a picture so all are in the shot.
     */
    private fun layOut(activity: ComponentActivity, host: AppWidgetHost, placed: List<Placed>, installed: List<AppWidgetProviderInfo>) {
        val root = checkNotNull(this.root)
        val density = activity.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt()
        val gap = px(GAP)
        val pad = px(PAD)
        val caption = px(CAPTION)

        val maxRowWidth = root.width - 2 * pad
        val rows = mutableListOf<MutableList<Placed>>()
        for (w in placed) {
            val row = rows.lastOrNull()
            val used = row?.sumOf { px(it.widthDp) + gap } ?: 0
            if (row != null && used + px(w.widthDp) <= maxRowWidth) row += w else rows += mutableListOf(w)
        }

        val grid = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
        }
        var total = 2 * pad
        rows.forEachIndexed { r, row ->
            val rowView = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
            }
            row.forEachIndexed { i, w ->
                val info = installed.first { it.provider == w.provider }
                val view = host.createView(activity, w.id, info) as? CountingHostView
                    ?: fail("Couldn't create a view for the ${w.label} widget")
                view.setPadding(0, 0, 0, 0)
                w.view = view
                val cell = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(caption(activity, w.label.replace("x", " × ") + " dp"), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, caption))
                    addView(view, LinearLayout.LayoutParams(px(w.widthDp), px(w.heightDp)))
                }
                rowView.addView(
                    cell,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        if (i > 0) leftMargin = gap
                    },
                )
            }
            grid.addView(
                rowView,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (r > 0) topMargin = gap
                },
            )
            total += caption + row.maxOf { px(it.heightDp) } + if (r > 0) gap else 0
        }

        root.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, total))
        if (total > root.height) {
            val scale = root.height.toFloat() / total
            grid.pivotX = root.width / 2f
            grid.pivotY = 0f
            grid.scaleX = scale
            grid.scaleY = scale
        }
    }

    /** Null once every widget has pushed a new RemoteViews since [before], else what's still missing. */
    private fun awaitRenders(placed: List<Placed>, before: Map<Placed, Int>, timeoutMs: Long): String? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            val waiting = placed.filter { it.renders() <= (before[it] ?: 0) }
            if (waiting.isEmpty()) return null
            if (SystemClock.uptimeMillis() > end) return "No render within ${timeoutMs / 1000} s from: ${waiting.joinToString { it.label }}"
            Thread.sleep(200)
        }
    }

    private fun textsIn(root: View): List<String> {
        val out = ArrayList<String>()
        fun walk(v: View) {
            if (v is TextView) v.text?.let { out += it.toString() }
            v.contentDescription?.let { out += it.toString() }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return out
    }

    private fun hasImage(root: View): Boolean {
        if (root is android.widget.ImageView && root.drawable != null) return true
        if (root is ViewGroup) for (i in 0 until root.childCount) if (hasImage(root.getChildAt(i))) return true
        return false
    }

    /** The texts and content descriptions a widget shows right now. */
    private fun shown(w: Placed): List<String> {
        var out = emptyList<String>()
        instrumentation.runOnMainSync { w.view?.let { out = textsIn(it) } }
        return out
    }

    /**
     * Glance draws asynchronously (and can push a stale frame first), so this waits until every widget shows
     * each of its expected texts (in part). Null when they all do, else which don't and what they show.
     */
    private fun awaitContent(expect: Map<Placed, List<String>>, timeoutMs: Long): String? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        fun missing(w: Placed, texts: List<String>): List<String> {
            val got = shown(w).map(::norm)
            return texts.filter { t -> got.none { it.contains(norm(t)) } }
        }
        while (true) {
            val wrong = expect.filter { (w, texts) -> missing(w, texts).isNotEmpty() }
            if (wrong.isEmpty()) return null
            if (SystemClock.uptimeMillis() > end) {
                return wrong.entries.joinToString("; ") { (w, texts) -> "${w.label} should show ${missing(w, texts)} but shows ${shown(w)}" }
            }
            Thread.sleep(200)
        }
    }

    /** Glance can push more than once (the data flow and the refresh); wait until it's quiet for 1.5 s. */
    private fun settle(placed: List<Placed>) {
        val end = SystemClock.uptimeMillis() + 10_000
        var last = placed.sumOf { it.renders() }
        var quietSince = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() < end && SystemClock.uptimeMillis() - quietSince < 1_500) {
            Thread.sleep(100)
            val now = placed.sumOf { it.renders() }
            if (now != last) {
                last = now
                quietSince = SystemClock.uptimeMillis()
            }
        }
    }

    /** Every widget drew something real: not Glance's error layout, not an empty view. */
    private fun checkDrawn(placed: List<Placed>, moment: String): String? {
        @Suppress("DiscouragedApi")
        val errorLayout = context.resources.getIdentifier("glance_error_layout", "layout", context.packageName)
        val problems = ArrayList<String>()
        for (w in placed) {
            if (errorLayout != 0 && w.view?.lastLayoutId == errorLayout) problems += "${w.label}: Glance's error layout (see logcat)"
            var children = 0
            var size = 0 to 0
            instrumentation.runOnMainSync {
                children = w.view?.childCount ?: 0
                size = (w.view?.width ?: 0) to (w.view?.height ?: 0)
            }
            if (children == 0) problems += "${w.label}: the hosted view is empty"
            if (size.first <= 0 || size.second <= 0) problems += "${w.label}: the hosted view has no size"
            if (shown(w).none { it.isNotBlank() }) problems += "${w.label}: shows no text"
        }
        return if (problems.isEmpty()) null else "$moment: ${problems.joinToString("; ")}"
    }

    private fun fail(message: String): Nothing = throw AssertionError(message)

    private fun shot(name: String) {
        instrumentation.waitForIdleSync()
        Shots.save(name)
    }

    companion object {
        private const val TAG = "WidgetsTest"
        private const val HOST_ID = 0x9A15
        private const val COVER_SQUARE = 156
        private const val COVER_WIDE = 328
        private const val COVER_STRIP = 72
        private const val INNER_SQUARE = 190
        private const val INNER_WIDE = 400
        private const val INNER_STRIP = 88
        private const val GAP = 12
        private const val PAD = 16
        private const val CAPTION = 20
        private val WALL_TOP = 0xFF1B2333.toInt()
        private val WALL_BOTTOM = 0xFF3A2D4F.toInt()
    }
}
