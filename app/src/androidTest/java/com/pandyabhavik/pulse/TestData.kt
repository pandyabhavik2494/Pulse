package com.pandyabhavik.pulse

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.pandyabhavik.pulse.core.DayLog
import com.pandyabhavik.pulse.core.Meals
import com.pandyabhavik.pulse.core.Profile
import com.pandyabhavik.pulse.data.HealthStatus
import com.pandyabhavik.pulse.data.HomeState
import com.pandyabhavik.pulse.data.PulseRepository
import com.pandyabhavik.pulse.data.Settings
import com.pandyabhavik.pulse.health.HealthGateway
import com.pandyabhavik.pulse.health.HealthSource
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The states the tests put Pulse in. Each has its own pinned "now"; the data is Bhavik's realistic first
 * 24 days (1 to 24 Nov 2026), changed only where the scene needs it.
 */
enum class Scene(val now: LocalDateTime) {
    /** First launch: nothing saved, onboarding shows. */
    FRESH(TestData.NOW),
    /** Tue 24 Nov, 7:42 PM: 520 kcal under (the approved mock). */
    UNDER(TestData.NOW),
    /** Same evening, a big dinner: 180 kcal over. */
    OVER(TestData.NOW),
    /** Yesterday was 240 over, today is under: the forgiving streak keeps going. */
    OVER_YESTERDAY(TestData.NOW),
    /** Two weeks with no deficit and a flat scale: no dates can be projected. */
    STALLED(TestData.NOW),
    /** The trend crossed 115 kg (First 5 kg) this morning and it hasn't been celebrated yet. */
    MILESTONE(TestData.NOW),
    /** Monday 23 Nov, 7:30 AM: last week's recap card is up. */
    MONDAY(LocalDateTime.of(2026, 11, 23, 7, 30)),
    /** Tue 24 Nov, 9:10 AM: weighed and walked, nothing eaten yet. */
    NO_FOOD(LocalDateTime.of(2026, 11, 24, 9, 10)),
    /** Onboarded, but Health Connect access was taken away (nothing granted). */
    NO_PERMISSIONS(TestData.NOW),
    ;

    val today: LocalDate get() = now.toLocalDate()
}

/**
 * Health Connect stand-in: every permission granted, AVAILABLE, and day data the test controls. The
 * repository reads it through [Graph.healthOverride]; one instance lives for the whole test run and the
 * scenes swap its data.
 */
class FakeHealthSource : HealthSource {
    @Volatile var days: List<DayLog> = emptyList()
    @Volatile var grantedNow: Set<String> = HealthGateway.ALL
    @Volatile var statusNow: HealthStatus = HealthStatus.AVAILABLE
    @Volatile var reads = 0
        private set

    override val permissions: Set<String> = HealthGateway.ALL

    override fun status(): HealthStatus = statusNow

    override suspend fun granted(): Set<String> = if (statusNow == HealthStatus.AVAILABLE) grantedNow else emptySet()

    override suspend fun readDays(from: LocalDate, to: LocalDate, zone: ZoneId): List<DayLog> {
        reads++
        // The real client throws when the read permissions aren't granted.
        if (!grantedNow.containsAll(HealthGateway.DATA)) throw SecurityException("Fake Health Connect: read permission not granted")
        return days.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }
    }

    override fun installIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${HealthGateway.PROVIDER}"))

    override fun settingsIntent(): Intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
}

object TestData {
    const val TAG = "PulseTest"

    val START: LocalDate = LocalDate.of(2026, 11, 1)
    val NOW: LocalDateTime = LocalDateTime.of(2026, 11, 24, 19, 42)

    val PROFILE = Profile(
        name = "Bhavik",
        heightCm = 175.3,
        age = 32,
        male = true,
        startKg = 120.0,
        goalKg = 85.0,
        startDate = START,
    )

    /** One day of Bhavik's data: [i] days after 1 Nov. */
    private class D(
        val i: Int, val steps: Int, val burned: Int, val active: Int, val eaten: Int,
        val breakfast: Int, val lunch: Int, val dinner: Int, val snacks: Int, val protein: Int,
        val kg: Double, val sleep: Int,
    ) {
        fun log() = DayLog(
            date = START.plusDays(i.toLong()),
            steps = steps,
            burnedTotal = burned,
            burnedActive = active,
            eaten = eaten,
            proteinG = protein,
            meals = Meals(breakfast = breakfast, lunch = lunch, dinner = dinner, snacks = snacks),
            weightKg = kg,
            sleepMinutes = sleep,
        )
    }

    // Resting 1,820 kcal a day from the Fitbit plus active burn from the walking. Average deficit ~820 kcal,
    // over on three Saturdays-ish slips (7, 14, 21 Nov) and one Wednesday; the trend runs 120.0 -> 117.6.
    //           i   steps  burned active eaten   bf  lunch dinner snack prot   kg     sleep
    private val TABLE = listOf(
        D(0, 7240, 2440, 620, 1670, 370, 570, 550, 180, 114, 120.0, 452), // Sun 01 Nov: +770
        D(1, 9810, 2650, 830, 1610, 350, 550, 530, 180, 109, 119.6, 401), // Mon 02 Nov: +1040
        D(2, 11250, 2780, 960, 1600, 350, 540, 530, 180, 109, 119.3, 436), // Tue 03 Nov: +1180
        D(3, 8630, 2550, 730, 1550, 340, 530, 510, 170, 105, 119.4, 388), // Wed 04 Nov: +1000
        D(4, 10420, 2710, 890, 1600, 350, 540, 530, 180, 109, 118.9, 465), // Thu 05 Nov: +1110
        D(5, 6980, 2410, 590, 1560, 340, 530, 520, 170, 106, 118.7, 372), // Fri 06 Nov: +850
        D(6, 6150, 2340, 520, 2450, 540, 830, 810, 270, 167, 118.8, 478), // Sat 07 Nov: -110
        D(7, 7420, 2450, 630, 1890, 420, 640, 620, 210, 129, 119.0, 447), // Sun 08 Nov: +560
        D(8, 10880, 2740, 920, 1600, 350, 540, 530, 180, 109, 118.5, 395), // Mon 09 Nov: +1140
        D(9, 11960, 2840, 1020, 1590, 350, 540, 530, 170, 108, 118.2, 428), // Tue 10 Nov: +1250
        D(10, 9240, 2610, 790, 1590, 350, 540, 530, 170, 108, 118.0, 412), // Wed 11 Nov: +1020
        D(11, 12010, 2840, 1020, 1530, 340, 520, 500, 170, 104, 117.9, 384), // Thu 12 Nov: +1310
        D(12, 8170, 2510, 690, 1620, 360, 550, 530, 180, 110, 117.6, 366), // Fri 13 Nov: +890
        D(13, 6420, 2370, 550, 2520, 550, 860, 830, 280, 171, 117.8, 474), // Sat 14 Nov: -150
        D(14, 7890, 2490, 670, 1840, 400, 630, 610, 200, 125, 118.0, 459), // Sun 15 Nov: +650
        D(15, 10310, 2700, 880, 1610, 350, 550, 530, 180, 109, 117.4, 405), // Mon 16 Nov: +1090
        D(16, 11040, 2760, 940, 1550, 340, 530, 510, 170, 105, 117.3, 437), // Tue 17 Nov: +1210
        D(17, 8150, 2510, 690, 2570, 570, 870, 850, 280, 175, 117.0, 391), // Wed 18 Nov: -60
        D(18, 11730, 2820, 1000, 1550, 340, 530, 510, 170, 105, 116.9, 420), // Thu 19 Nov: +1270
        D(19, 9480, 2630, 810, 1630, 360, 550, 540, 180, 111, 116.7, 398), // Fri 20 Nov: +1000
        D(20, 6930, 2450, 630, 2630, 580, 890, 870, 290, 179, 117.0, 400), // Sat 21 Nov: -180
        D(21, 8820, 2570, 750, 1750, 380, 600, 580, 190, 119, 117.2, 468), // Sun 22 Nov: +820
        D(22, 10650, 2730, 910, 1550, 340, 530, 510, 170, 105, 116.6, 431), // Mon 23 Nov: +1180
        D(23, 8412, 2480, 660, 1960, 420, 680, 650, 210, 128, 116.4, 412), // Tue 24 Nov so far: +520
    )

    /** Today (24 Nov) at 7:42 PM, as in the mock. */
    const val TODAY_BURNED = 2480
    const val TODAY_EATEN = 1960
    const val TODAY_BALANCE = TODAY_BURNED - TODAY_EATEN // 520
    const val TODAY_STEPS = 8412
    /** The over evening: same burn, 2,660 eaten. */
    const val OVER_EATEN = 2660
    const val OVER_BY = OVER_EATEN - TODAY_BURNED // 180
    val SAT_21: LocalDate = LocalDate.of(2026, 11, 21)
    const val SAT_21_SLEEP = "6 h 40 m"

    /** The 24 days as Health Connect would give them on 24 Nov at 7:42 PM. */
    fun baseDays(): List<DayLog> = TABLE.map { it.log() }

    private fun List<DayLog>.replace(date: LocalDate, change: (DayLog) -> DayLog) =
        map { if (it.date == date) change(it) else it }

    /** The fake's data for [scene], as of the scene's own "now". */
    fun daysFor(scene: Scene): List<DayLog> {
        val today = scene.today
        val base = baseDays()
        return when (scene) {
            Scene.OVER -> base.replace(today) {
                it.copy(eaten = OVER_EATEN, proteinG = 141, meals = Meals(breakfast = 420, lunch = 680, dinner = 1080, snacks = 480))
            }
            Scene.OVER_YESTERDAY -> base.replace(today.minusDays(1)) {
                // Burned 2,730, ate 2,970: 240 over.
                it.copy(eaten = 2970, proteinG = 190, meals = Meals(breakfast = 520, lunch = 900, dinner = 1160, snacks = 390))
            }
            Scene.STALLED -> base.mapIndexed { i, d ->
                if (d.date == today) {
                    d.copy(eaten = 2390, meals = Meals(breakfast = 480, lunch = 760, dinner = 860, snacks = 290), weightKg = 119.6)
                } else {
                    // Eating what he burns, give or take: the 14-day average is slightly over, and the scale is flat.
                    val drift = intArrayOf(-60, 40, -30, 20, -80, 10, -40)[i % 7]
                    val eaten = d.burnedTotal!! - drift
                    d.copy(
                        eaten = eaten,
                        meals = Meals(breakfast = eaten * 22 / 100, lunch = eaten * 34 / 100, dinner = eaten - eaten * 22 / 100 - eaten * 34 / 100 - eaten * 11 / 100, snacks = eaten * 11 / 100),
                        // From 119.4 on 1 Nov, hovering around 119.7: the trend creeps up, not down.
                        weightKg = 119.6 + doubleArrayOf(-0.2, 0.2, -0.1, 0.3, 0.0, 0.1, 0.2)[i % 7],
                    )
                }
            }
            // Shifting every weigh-in after the first by 3 kg takes the trend from 115.01 (23 Nov) to 114.85
            // (24 Nov): the First 5 kg milestone is reached this morning.
            Scene.MILESTONE -> base.map { if (it.date == START) it else it.copy(weightKg = it.weightKg!! - 3.0) }
            Scene.MONDAY -> base.filter { it.date.isBefore(today) } + DayLog(
                date = today, steps = 640, burnedTotal = 610, burnedActive = 40, eaten = null, proteinG = null,
                meals = Meals(), weightKg = 116.6, sleepMinutes = 431,
            )
            Scene.NO_FOOD -> base.filter { it.date.isBefore(today) } + DayLog(
                date = today, steps = 2140, burnedTotal = 880, burnedActive = 190, eaten = null, proteinG = null,
                meals = Meals(), weightKg = 116.4, sleepMinutes = 412,
            )
            else -> base
        }.filter { !it.date.isAfter(today) }
    }

    // ---- Overrides ---------------------------------------------------------------------------------

    val zone: ZoneId get() = ZoneId.systemDefault()

    /** The one fake for the whole run, installed before the repository is first used. */
    val health = FakeHealthSource()

    fun clockAt(now: LocalDateTime): Clock = Clock.fixed(now.atZone(zone).toInstant(), zone)

    /** Points Graph at the fake and pins the clock. Safe to call again with another time. */
    fun install(now: LocalDateTime = NOW) {
        Graph.healthOverride = health
        Graph.clockOverride = clockAt(now)
    }

    fun pin(now: LocalDateTime) {
        Graph.clockOverride = clockAt(now)
    }

    /**
     * Clears everything and puts the app in [scene]: saves Bhavik's profile, syncs the fake's data, and
     * (except in [Scene.MILESTONE]) marks any reached milestone celebrated so no overlay covers the screen.
     */
    suspend fun seed(context: Context, scene: Scene): HomeState {
        install(scene.now)
        health.days = daysFor(scene)
        health.statusNow = HealthStatus.AVAILABLE
        health.grantedNow = HealthGateway.ALL
        val repo = Graph.repo(context)
        repo.resetForTests()
        if (scene == Scene.FRESH) return repo.snapshot()

        // Nudges off while seeding: the app isn't on screen yet, so a due nudge would post a real
        // notification over the screenshots. Back on (the default) once the data is in.
        repo.setNudges(morning = false, evening = false)
        repo.saveProfile(PROFILE)
        val ok = repo.sync()
        if (!ok) Log.w(TAG, "Seeding $scene: sync returned false")
        if (scene != Scene.MILESTONE) {
            val reached = repo.snapshot().forecast?.milestones.orEmpty().filter { it.reachedOn != null }
            for (m in reached) repo.markCelebrated(m.kg)
        }
        // The recap shows on Monday and Tuesday; Tuesday's screens (the mocks) are without it.
        if (scene != Scene.MONDAY && repo.snapshot().recap != null) repo.dismissRecap()
        if (scene == Scene.NO_PERMISSIONS) {
            // Data stays from the earlier sync; then access is taken away.
            health.grantedNow = emptySet()
            repo.sync()
        }
        repo.setNudges(morning = true, evening = true)
        return repo.snapshot()
    }

    // ---- WorkManager -------------------------------------------------------------------------------

    private class NoopWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result = Result.success()
    }

    @Volatile private var workManagerReady = false

    /**
     * Swaps WorkManager for the test one, with Pulse's own workers doing nothing: a background sync or a
     * nudge firing mid-test would change the data under the screens and post notifications. Glance runs
     * widget sessions as WorkManager jobs, so those (androidx.*) still run for real.
     */
    fun quietWorkManager(context: Context) {
        if (workManagerReady) return
        synchronized(this) {
            if (workManagerReady) return
            val config = Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setMinimumLoggingLevel(Log.INFO)
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker? =
                            if (workerClassName.startsWith("com.pandyabhavik.pulse.")) NoopWorker(appContext, workerParameters) else null
                    },
                )
                .build()
            WorkManagerTestInitHelper.initializeTestWorkManager(context.applicationContext, config)
            workManagerReady = true
        }
    }
}

/** A no-break (U+00A0), narrow no-break (U+202F) or thin space as a plain one, and a minus sign as a hyphen. */
fun norm(s: String): String = s.replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2009', ' ').replace('\u2212', '-')

/** 8412 -> "8,412" (the app's own digit style). */
fun n(v: Int): String = String.format(java.util.Locale.US, "%,d", v)

/** Screenshots, saved where CI pulls them from. */
object Shots {
    const val DIR = "/sdcard/Download/pulse-screens"

    fun shell(command: String): String {
        val pipe = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pipe).bufferedReader().use { it.readText() }
    }

    fun save(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(800)
        shell("mkdir -p $DIR")
        shell("screencap -p $DIR/$name.png")
        Log.i(TestData.TAG, "Saved $DIR/$name.png")
    }

    /** Runtime permissions the app may declare; one it doesn't declare just fails quietly. */
    fun grantRuntime(pkg: String) {
        for (p in listOf("android.permission.POST_NOTIFICATIONS", "android.permission.ACTIVITY_RECOGNITION")) {
            runCatching { shell("pm grant $pkg $p") }
        }
    }
}

/**
 * Drives MainActivity for the screen and action tests: seeds a [Scene], launches the app, waits for its
 * first screen, and has the finders, waits and layout checks the tests share.
 */
class AppDriver(val compose: ComposeTestRule) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context: Context get() = instrumentation.targetContext
    val repo: PulseRepository get() = Graph.repo(context)
    val device: UiDevice by lazy { UiDevice.getInstance(instrumentation) }
    var scenario: ActivityScenario<MainActivity>? = null
        private set
    var scene: Scene = Scene.UNDER
        private set
    /** What the seed left: the numbers the screens must show. */
    var seeded: HomeState? = null
        private set

    /** The inner screen's layout (rail and two panes) rather than the cover screen's (bottom bar). */
    var wide: Boolean = false
        private set

    fun setUp() {
        // Before anything touches Graph.repo, so the repository is built over the fake.
        TestData.install()
        TestData.quietWorkManager(context)
        Shots.grantRuntime(context.packageName)
    }

    fun tearDown() {
        runCatching { scenario?.close() }
        scenario = null
    }

    fun open(scene: Scene, tab: String? = null) {
        runCatching { scenario?.close() }
        scenario = null
        this.scene = scene
        seeded = runBlocking { TestData.seed(context, scene) }
        launch(tab)
    }

    /** Opens the app again over the data as it is now (no reseed). */
    fun relaunch(tab: String? = null) {
        runCatching { scenario?.close() }
        scenario = null
        launch(tab)
    }

    private fun launch(tab: String?) {
        val intent = Intent(context, MainActivity::class.java)
        if (tab != null) intent.putExtra(MainActivity.EXTRA_TAB, tab)
        val s = ActivityScenario.launch<MainActivity>(intent)
        scenario = s
        s.onActivity { wide = it.resources.configuration.screenWidthDp >= 600 }
        waitFor("the app to show its first screen", 20_000) {
            exists("today_screen") || exists("onboarding_screen") || exists("journey_screen") ||
                exists("history_screen") || exists("settings_screen")
        }
        backToApp()
        idle()
    }

    /**
     * If the app sent us to a system screen (the Health Connect permission sheet, say), come back: the
     * tests look at Pulse, and the emulator's Health Connect isn't what's being tested.
     */
    fun backToApp() {
        repeat(3) {
            Thread.sleep(300)
            if (resumed()) return
            Log.i(TestData.TAG, "Pulse isn't in front; pressing Back")
            device.pressBack()
        }
    }

    fun resumed(): Boolean {
        var r = false
        instrumentation.runOnMainSync {
            r = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is MainActivity }
        }
        return r
    }

    fun idle() {
        runCatching { compose.waitForIdle() }
        instrumentation.waitForIdleSync()
    }

    fun shot(name: String) {
        idle()
        Shots.save(name)
    }

    fun state(): HomeState = runBlocking { repo.snapshot() }

    fun settings(): Settings = runBlocking { repo.store.current() }

    // ---- Finding things ----------------------------------------------------------------------------

    /** The texts a node shows (its own, unmerged). */
    fun shown(node: SemanticsNode): List<String> {
        val c = node.config
        val texts = if (SemanticsProperties.Text in c) c[SemanticsProperties.Text].map { it.text } else emptyList()
        val edit = if (SemanticsProperties.EditableText in c) listOf(c[SemanticsProperties.EditableText].text) else emptyList()
        return texts + edit
    }

    /** All the text under the node tagged [tag], joined with spaces and normalised. */
    fun textOf(tag: String): String {
        val out = ArrayList<String>()
        fun walk(n: SemanticsNode) {
            out += shown(n)
            n.children.forEach(::walk)
        }
        walk(node(tag).fetchSemanticsNode())
        return norm(out.joinToString(" "))
    }

    fun hasShownText(text: String, substring: Boolean = false): SemanticsMatcher {
        val want = norm(text)
        return SemanticsMatcher("shows '$text'${if (substring) " (in part)" else ""}") { n ->
            shown(n).any { val got = norm(it); if (substring) got.contains(want) else got == want }
        }
    }

    /** The node tagged [tag] (or its children) shows [expected], exactly or in part. */
    fun assertTagShows(tag: String, expected: String, substring: Boolean = true) {
        val got = textOf(tag)
        val ok = if (substring) got.contains(norm(expected)) else got == norm(expected)
        if (!ok) throw AssertionError("'$tag' should show '$expected' but shows '$got'")
    }

    fun exists(tag: String): Boolean = exists(hasTestTag(tag))

    fun exists(matcher: SemanticsMatcher): Boolean =
        runCatching { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    fun text(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        compose.onNodeWithText(text, substring = substring, useUnmergedTree = true)

    fun assertText(text: String, substring: Boolean = false) {
        if (!exists(hasShownText(text, substring = substring))) {
            throw AssertionError("Expected the text '$text'${if (substring) " (in part)" else ""} on screen")
        }
    }

    fun waitFor(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (!runCatching(condition).getOrDefault(false)) {
            if (SystemClock.uptimeMillis() > end) throw AssertionError("Timed out after ${timeoutMs / 1000} s waiting for $what")
            runCatching { compose.waitForIdle() }
            Thread.sleep(50)
        }
        idle()
    }

    /** Like [waitFor], but false instead of failing. */
    fun within(timeoutMs: Long, condition: () -> Boolean): Boolean =
        runCatching { waitFor("", timeoutMs, condition); true }.getOrDefault(false)

    fun waitTag(tag: String, timeoutMs: Long = 10_000) = waitFor("'$tag' to show", timeoutMs) { exists(tag) }

    fun waitGone(tag: String, timeoutMs: Long = 10_000) = waitFor("'$tag' to go away", timeoutMs) { !exists(tag) }

    fun waitText(text: String, substring: Boolean = false, timeoutMs: Long = 10_000) =
        waitFor("the text '$text'", timeoutMs) { exists(hasShownText(text, substring = substring)) }

    /** Scrolls [tag] into view when it sits in a scrolling container; does nothing when it doesn't. */
    fun scrollTo(tag: String): SemanticsNodeInteraction {
        val n = node(tag)
        runCatching { n.performScrollTo() }
        return n
    }

    fun click(tag: String) {
        scrollTo(tag).performClick()
        idle()
    }

    /**
     * Scrolls until [tag] is in view. True when the screen moved (so a screenshot now shows something the
     * last one didn't); false when it was already in view, as it often is on the inner screen.
     */
    fun scrollDownToShow(tag: String): Boolean {
        if (!exists(tag)) return false
        val before = runCatching { node(tag).fetchSemanticsNode().positionInRoot.y }.getOrNull() ?: return false
        scrollTo(tag)
        idle()
        val after = runCatching { node(tag).fetchSemanticsNode().positionInRoot.y }.getOrNull() ?: return false
        return kotlin.math.abs(after - before) > 1f
    }

    fun back() {
        idle()
        device.pressBack()
        idle()
    }

    /** Puts the keyboard away (typing opens it), so it doesn't cover the screenshot. */
    fun hideKeyboard() {
        scenario?.onActivity {
            WindowCompat.getInsetsController(it.window, it.window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        Thread.sleep(400)
        idle()
    }

    // ---- Navigation --------------------------------------------------------------------------------

    /** "today", "journey", "history" or "settings": the bottom bar / rail item (settings: the top-right icon on the cover screen). */
    fun tab(key: String) {
        click("nav_$key")
        waitTag("${key}_screen")
    }

    // ---- Layout checks -----------------------------------------------------------------------------

    /** The app window's bounds in px (the whole screen, as the emulator's wm size sets it). */
    fun windowBounds(): Rect {
        var r = Rect()
        val s = scenario
        if (s != null) {
            s.onActivity { r = Rect(it.windowManager.currentWindowMetrics.bounds) }
        } else {
            val m = context.resources.displayMetrics
            r = Rect(0, 0, m.widthPixels, m.heightPixels)
        }
        return Rect(0, 0, r.width(), r.height())
    }

    /**
     * Checks the node tagged [tag] is fully on screen and not cut off: its bounds sit inside the window,
     * no ancestor clips it (its visible bounds are its whole size), and, for text, nothing is ellipsised or
     * spills out of the text's own box. Scrolls it into view first when it sits in a scrolling column.
     */
    fun assertNotClipped(tag: String) {
        val n = scrollTo(tag)
        idle()
        val node = n.fetchSemanticsNode("'$tag' should be on screen")
        val w = windowBounds()
        val b = node.boundsInWindow
        val slack = 2f
        val problems = ArrayList<String>()
        if (b.width <= 0f || b.height <= 0f) problems += "has no visible size (bounds $b)"
        if (b.left < -slack || b.top < -slack || b.right > w.width() + slack || b.bottom > w.height() + slack) {
            problems += "is outside the ${w.width()}x${w.height()} window (bounds $b)"
        }
        if (b.width + slack < node.size.width || b.height + slack < node.size.height) {
            problems += "is cut off by its container: ${b.width.toInt()}x${b.height.toInt()} visible of ${node.size.width}x${node.size.height}"
        }
        val c = node.config
        if (SemanticsActions.GetTextLayoutResult in c) {
            val results = ArrayList<TextLayoutResult>()
            runCatching { c[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
            val layout = results.firstOrNull()
            if (layout != null && layout.hasVisualOverflow) {
                problems += "text '${shown(node).joinToString()}' overflows (ellipsised or cut: ${layout.size} in ${layout.layoutInput.constraints})"
            }
        }
        if (problems.isNotEmpty()) throw AssertionError("'$tag' ${problems.joinToString("; ")}")
    }

    /** [assertNotClipped] for every tag in [tags] that's on screen now; [required] ones must exist. */
    fun assertNoneClipped(required: List<String>, optional: List<String> = emptyList()) {
        val errors = ArrayList<String>()
        for (t in required + optional) {
            if (!exists(t)) {
                if (t in required) errors += "'$t' is missing"
                continue
            }
            runCatching { assertNotClipped(t) }.onFailure { errors += it.message ?: "'$t' is clipped" }
        }
        if (errors.isNotEmpty()) throw AssertionError("Layout problems on ${scene.name}${if (wide) " (inner screen)" else " (cover screen)"}:\n" + errors.joinToString("\n"))
    }
}

/** Bhavik's way through onboarding step 1: name, 175.3 cm, 32, male, 120 -> 85 kg. */
object Onboarding {
    fun assertStep1(app: AppDriver) {
        app.waitTag("onboarding_name")
        app.assertText("STEP 1 OF 2")
        // The name field starts filled in.
        app.assertTagShows("onboarding_name", TestData.PROFILE.name)
    }

    private fun type(app: AppDriver, tag: String, text: String) {
        app.scrollTo(tag).performTextReplacement(text)
        app.idle()
    }

    fun fillStep1(app: AppDriver) {
        app.waitTag("onboarding_name")
        val p = TestData.PROFILE
        type(app, "onboarding_name", p.name)
        // Height in cm is the default unit.
        type(app, "onboarding_height", "175.3")
        type(app, "onboarding_age", p.age.toString())
        app.click("onboarding_male")
        type(app, "onboarding_weight", "120")
        type(app, "onboarding_goal", "85")
        app.hideKeyboard()
        app.assertText("35 kg to go", substring = true)
    }
}
