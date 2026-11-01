package com.pandyabhavik.pulse.data

import android.content.Context
import android.util.Log
import com.pandyabhavik.pulse.core.DayLog
import com.pandyabhavik.pulse.core.Energy
import com.pandyabhavik.pulse.core.Forecaster
import com.pandyabhavik.pulse.core.Motivation
import com.pandyabhavik.pulse.core.Profile
import com.pandyabhavik.pulse.core.Trend
import com.pandyabhavik.pulse.health.HealthSource
import com.pandyabhavik.pulse.widget.Widgets
import com.pandyabhavik.pulse.work.Notifier
import com.pandyabhavik.pulse.work.Nudge
import com.pandyabhavik.pulse.work.appInForeground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The one place Pulse's data comes together: Health Connect days in Room, settings in DataStore, and
 * the [HomeState] every screen, widget and notification draws from. The maths lives in the core module;
 * this class only gathers the inputs and calls it.
 *
 * [health] and [clock] are read through providers on every use, so tests can swap in a fake Health
 * Connect and a pinned "now" (Graph.healthOverride / Graph.clockOverride) even after the repository
 * already exists.
 */
class PulseRepository(
    private val context: Context,
    val dao: DayDao,
    val store: SettingsStore,
    private val healthProvider: () -> HealthSource,
    private val clockProvider: () -> Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val syncLock = Mutex()
    private val buildLock = Mutex()
    private val ticker = StepTicker()

    val health: HealthSource get() = healthProvider()
    fun clock(): Clock = clockProvider()
    fun zone(): ZoneId = clock().zone
    fun now(): ZonedDateTime = ZonedDateTime.now(clock())
    fun today(): LocalDate = now().toLocalDate()

    private val _state = MutableStateFlow<HomeState?>(null)
    /** Null only until the first build, a moment after the repository is created. */
    val state: StateFlow<HomeState?> = _state.asStateFlow()

    /** The current settings. Starts as Settings() for the moment before DataStore has loaded. */
    val settings: StateFlow<Settings> = store.flow.stateIn(scope, SharingStarted.Eagerly, Settings())

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    init {
        scope.launch { runCatching { snapshot() }.onFailure { Log.w(TAG, "First build failed", it) } }
        // "Now" moves on without new data: the resting-burn estimate grows, 8 PM and midnight arrive.
        // Rebuild once a minute while something (a screen) is watching.
        scope.launch {
            while (true) {
                delay(60_000)
                if (_state.subscriptionCount.value > 0) runCatching { snapshot() }
            }
        }
    }

    // ---- Sync ----------------------------------------------------------------------------------

    /**
     * Reads Health Connect into Room: the last 90 days the first time, then the last 3 days (a late Fitbit
     * sync or a meal logged after midnight can still change them). Then rebuilds the state, redraws the
     * widgets and sends any nudge that is due. Runs when the app opens, on pull-down, and every 15
     * minutes in the background. False when Health Connect couldn't be read. [quick] (from the UI) leaves the
     * widget redraw and nudges running after it returns.
     */
    suspend fun sync(quick: Boolean = false): Boolean = syncLock.withLock {
        _syncing.value = true
        try {
            val ok = try {
                // Health Connect can stall (a busy provider, a slow first 90-day read); never hold the
                // sync, and the Sync now button with it, for longer than this.
                withTimeoutOrNull(READ_TIMEOUT_MS) { read() } ?: run {
                    lastError = "Health Connect took too long to answer. Try again in a minute."
                    false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed", e)
                lastError = e.message ?: e.javaClass.simpleName
                false
            }
            val s = snapshot()
            val extras: suspend () -> Unit = {
                refreshWidgets()
                runCatching { postNudges(s) }.onFailure { Log.w(TAG, "Nudges failed", it) }
            }
            // [quick] (the app on screen): widgets and nudges don't hold up the sync, the screen already has
            // the new numbers. In the background they run inline, so the work isn't done before they are.
            if (quick) scope.launch { extras() } else extras()
            ok
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun read(): Boolean {
        val source = health
        lastError = null
        when (source.status()) {
            HealthStatus.NOT_INSTALLED -> { lastError = "Health Connect isn't installed"; return false }
            HealthStatus.NEEDS_UPDATE -> { lastError = "Health Connect needs an update"; return false }
            HealthStatus.AVAILABLE -> Unit
        }
        val granted = source.granted()
        if (source.permissions.none { it in granted && !it.endsWith(BACKGROUND_SUFFIX) && !it.endsWith(HISTORY_SUFFIX) }) {
            lastError = "Pulse has no Health Connect access yet"
            return false
        }
        val today = today()
        // A full read the first time, and again whenever more history becomes readable (the history
        // permission was just allowed, or Pulse reads further back than before): early trends need it.
        val backfill = "$FETCH_DAYS-${granted.any { it.endsWith(HISTORY_SUFFIX) }}"
        val settingsNow = store.current()
        val first = !settingsNow.firstSyncDone || settingsNow.backfill != backfill
        val from = if (first) today.minusDays(FETCH_DAYS - 1) else today.minusDays(RECENT_DAYS - 1)
        val logs = source.readDays(from, today, zone())
            .filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        val problems = source.problems
        lastProblems = problems
        if (problems.isEmpty()) {
            dao.replaceRange(from.toString(), today.toString(), logs.map { it.toEntity() })
        } else {
            // Some types couldn't be read this time: keep what was stored for them instead of wiping it.
            val old = dao.days().map { it.toDayLog() }.associateBy { it.date }
            val fresh = logs.associateBy { it.date }
            val merged = (old.keys.filter { !it.isBefore(from) && !it.isAfter(today) } + fresh.keys).distinct().map { d ->
                val n = fresh[d] ?: DayLog(d)
                val o = old[d] ?: DayLog(d)
                n.copy(
                    steps = n.steps ?: o.steps,
                    burnedTotal = n.burnedTotal ?: o.burnedTotal,
                    burnedActive = n.burnedActive ?: o.burnedActive,
                    eaten = n.eaten ?: o.eaten,
                    proteinG = n.proteinG ?: o.proteinG,
                    meals = if (n.eaten != null) n.meals else o.meals,
                    weightKg = n.weightKg ?: o.weightKg,
                    sleepMinutes = n.sleepMinutes ?: o.sleepMinutes,
                    restingHr = n.restingHr ?: o.restingHr,
                    hrAvg = n.hrAvg ?: o.hrAvg,
                    hrMin = n.hrMin ?: o.hrMin,
                    hrMax = n.hrMax ?: o.hrMax,
                    distanceM = n.distanceM ?: o.distanceM,
                    carbsG = n.carbsG ?: o.carbsG,
                    fatG = n.fatG ?: o.fatG,
                    fiberG = n.fiberG ?: o.fiberG,
                    sugarG = n.sugarG ?: o.sugarG,
                    sodiumMg = n.sodiumMg ?: o.sodiumMg,
                    waterMl = n.waterMl ?: o.waterMl,
                    floors = n.floors ?: o.floors,
                    exerciseMin = n.exerciseMin ?: o.exerciseMin,
                    bodyFatPct = n.bodyFatPct ?: o.bodyFatPct,
                    leanKg = n.leanKg ?: o.leanKg,
                    hrvMs = n.hrvMs ?: o.hrvMs,
                    spo2Pct = n.spo2Pct ?: o.spo2Pct,
                    breathingRate = n.breathingRate ?: o.breathingRate,
                    vo2Max = n.vo2Max ?: o.vo2Max,
                    sleepDeepMin = n.sleepDeepMin ?: o.sleepDeepMin,
                    sleepRemMin = n.sleepRemMin ?: o.sleepRemMin,
                    sleepLightMin = n.sleepLightMin ?: o.sleepLightMin,
                    sleepAwakeMin = n.sleepAwakeMin ?: o.sleepAwakeMin,
                    bedTime = n.bedTime ?: o.bedTime,
                    wakeTime = n.wakeTime ?: o.wakeTime,
                )
            }
            dao.upsertDays(merged.map { it.toEntity() })
        }
        // Nothing came back and something failed: a failed sync. Some types failing still counts, with
        // [lastProblems] saying which.
        if (logs.isEmpty() && problems.isNotEmpty()) {
            lastError = "Couldn't read Health Connect (${problems.first()})"
            return false
        }
        val at = clock().instant()
        store.update { it.copy(firstSyncDone = true, lastSync = at, backfill = backfill) }
        return true
    }

    /** What the last sync couldn't read, for Settings. Empty when it all came back. */
    @Volatile var lastProblems: List<String> = emptyList()
        private set

    /** Why the last sync failed outright (Health Connect missing, no access, an error), or null. */
    @Volatile var lastError: String? = null
        private set

    private suspend fun refreshWidgets() {
        try {
            Widgets.refresh(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Widget refresh failed", e)
        }
    }

    /**
     * Sends the nudges that are due. While Pulse is on screen it doesn't notify, but the nudge still
     * counts as delivered for the day: Bhavik is looking at the same numbers right now.
     */
    private suspend fun postNudges(s: HomeState) {
        val due = Notifier.due(s, store.current())
        if (due.isEmpty()) return
        val foreground = appInForeground()
        val today = s.now.toLocalDate()
        for (n in due) {
            if (!foreground) {
                // No permission yet: leave it unsent, so it can still go out today once allowed.
                if (!Notifier.allowed(context)) continue
                Notifier.show(context, n)
            }
            store.update {
                when (n.kind) {
                    Nudge.Kind.MORNING -> it.copy(lastMorningNudge = today)
                    Nudge.Kind.EVENING -> it.copy(lastEveningNudge = today)
                }
            }
        }
    }

    // ---- State ---------------------------------------------------------------------------------

    /** Builds the state from what's stored now, publishes it on [state] and returns it. */
    suspend fun snapshot(): HomeState = buildLock.withLock {
        val built = build()
        _state.value = built
        built
    }

    private suspend fun build(): HomeState {
        val now = now()
        val today = now.toLocalDate()
        val s = store.current()
        val p = s.profile
        val days = dao.days().map { it.toDayLog() }.filter { !it.date.isAfter(today) }
        val byDate = days.associateBy { it.date }
        val todayLog = byDate[today] ?: DayLog(today)

        val source = health
        val status = runCatching { source.status() }.getOrDefault(HealthStatus.NOT_INSTALLED)
        val granted = if (status == HealthStatus.AVAILABLE) runCatching { source.granted() }.getOrDefault(emptySet()) else emptySet()
        val missing = source.permissions - granted

        // ---- Weight and journey
        val trend = Trend.of(days, p?.startDate ?: today.minusDays(HISTORY_DAYS - 1), today)
        val trendKg = trend.lastOrNull()?.trend
        val forecast = if (p != null && trend.isNotEmpty()) Forecaster.forecast(p, days, trend, today) else null
        // Yesterday's forecast, from yesterday's trend, to say how far the next milestone moved today.
        val yesterday = today.minusDays(1)
        val trendYesterday = trend.filter { !it.date.isAfter(yesterday) }
        val previous = if (p != null && trendYesterday.isNotEmpty()) Forecaster.forecast(p, days, trendYesterday, yesterday) else null
        val shift = if (forecast != null && previous != null) Forecaster.shiftDays(forecast, previous) else null

        // ---- Today's energy. Fitbit's total when it exists; else resting so far (from the profile) + active.
        val bodyKg = trendKg ?: days.lastOrNull { it.weightKg != null }?.weightKg ?: p?.startKg
        val restingNow = if (p != null && bodyKg != null) Energy.restingSoFar(p, bodyKg, now.toLocalDateTime()) else null
        val split = restingNow?.let { Energy.split(todayLog.burnedTotal, todayLog.burnedActive, it) }
        val resting = split?.first ?: restingNow
        val active = split?.second ?: todayLog.burnedActive
        val burned = todayLog.burnedTotal ?: resting?.let { it + (active ?: 0) }
        val eaten = todayLog.eaten
        val balance = if (burned != null && eaten != null) burned - eaten else null

        // ---- Motivation
        val recapRaw = p?.let { Motivation.weeklyRecap(it, days, trend, today) }
        val recap = recapRaw?.takeIf { it.weekStart != s.recapDismissedWeek }
        val reached = forecast?.milestones.orEmpty().filter { it.reachedOn != null }
        val celebrate = reached.filter { !s.isCelebrated(it.kg) }.minByOrNull { it.reachedOn!! }
        val celebratedCount = if (forecast != null) reached.count { s.isCelebrated(it.kg) } else s.celebratedKg.size

        // ---- Live steps
        val hcSteps = todayLog.steps ?: 0
        ticker.onHealth(today, hcSteps)

        return HomeState(
            now = now,
            profile = p,
            health = status,
            missingPermissions = missing,
            lastSync = s.lastSync,
            today = todayLog,
            liveSteps = ticker.display(today, hcSteps),
            liveActive = ticker.active,
            burned = burned,
            resting = resting,
            active = active,
            eaten = eaten,
            balance = balance,
            trend = trend,
            trendKg = trendKg,
            forecast = forecast,
            shiftDays = shift,
            journeyLine = if (forecast != null) Motivation.journeyLine(forecast.next, shift, today) else null,
            streak = Motivation.streak(days, today),
            avg7 = Motivation.avg7(days, today),
            winsDone = if (p != null && trendKg != null) Motivation.winsDone(p, trendKg) else 0,
            winsTotal = p?.let { Motivation.winsTotal(it) } ?: 0,
            toNextWin = if (p != null && trendKg != null) Motivation.toNextWin(p, trendKg) else null,
            recap = recap,
            recapCommitted = recap != null && recap.weekStart == s.recapCommittedWeek,
            celebrate = celebrate,
            ringStyle = Motivation.ringStyle(celebratedCount),
            history = history(days, byDate, p, today),
        )
    }

    /** Every date from the first known day (at most 90 days back) to today, newest first; gaps are empty days. */
    private fun history(days: List<DayLog>, byDate: Map<LocalDate, DayLog>, p: Profile?, today: LocalDate): List<DayLog> {
        val first = listOfNotNull(days.firstOrNull()?.date, p?.startDate).minOrNull() ?: today
        val from = minOf(maxOf(first, today.minusDays(HISTORY_DAYS - 1)), today)
        val out = ArrayList<DayLog>()
        var d = today
        while (!d.isBefore(from)) {
            out += byDate[d] ?: DayLog(d)
            d = d.minusDays(1)
        }
        return out
    }

    // ---- Live step ticker ----------------------------------------------------------------------

    /** A phone step-counter reading (from LiveSteps). Updates the shown steps without a full rebuild. */
    fun onSensorSteps(totalSinceBoot: Long) {
        val today = today()
        ticker.onSensor(today, totalSinceBoot)
        val current = _state.value
        if (current == null || current.today.date != today) {
            // A new day (or no state yet): rebuild properly.
            scope.launch { runCatching { snapshot() } }
            return
        }
        _state.update { s ->
            s?.copy(liveSteps = ticker.display(s.today.date, s.today.steps ?: 0), liveActive = true)
        }
    }

    /** The step sensor stopped (app in the background). */
    fun onSensorStopped() {
        ticker.onStopped()
        _state.update { s -> s?.copy(liveActive = false, liveSteps = ticker.display(s.today.date, s.today.steps ?: 0)) }
    }

    // ---- Actions -------------------------------------------------------------------------------

    /** Onboarding and Settings. */
    suspend fun saveProfile(p: Profile) {
        store.update { it.copy(profile = p) }
        snapshot()
        refreshWidgets()
    }

    /** The celebration for the milestone at [kg] was shown; the next ring style unlocks. */
    suspend fun markCelebrated(kg: Double) {
        store.update { if (it.isCelebrated(kg)) it else it.copy(celebratedKg = it.celebratedKg + kg) }
        snapshot()
        refreshWidgets()
    }

    /** "Beat it this week" on the Monday recap. */
    suspend fun commitRecap() {
        val week = (_state.value ?: snapshot()).recap?.weekStart ?: return
        store.update { it.copy(recapCommittedWeek = week) }
        snapshot()
    }

    /** Hides this week's recap. */
    suspend fun dismissRecap() {
        val week = (_state.value ?: snapshot()).recap?.weekStart ?: return
        store.update { it.copy(recapDismissedWeek = week) }
        snapshot()
    }

    suspend fun setNudges(morning: Boolean, evening: Boolean) {
        store.update { it.copy(morningNudge = morning, eveningNudge = evening) }
    }

    /** Saves which tiles the Today dashboard shows, in order. An empty choice goes back to the default. */
    suspend fun setDashboard(tiles: List<String>) {
        store.update { it.copy(dashboard = tiles.distinct().ifEmpty { Settings.DEFAULT_DASHBOARD }) }
    }

    /** Wipes every stored day and setting (a fresh install, as far as Pulse knows). */
    suspend fun resetForTests() {
        syncLock.withLock {
            dao.clear()
            store.clear()
            ticker.reset()
            snapshot()
        }
    }

    companion object {
        private const val TAG = "PulseRepository"
        const val HISTORY_DAYS = 90L

        /** How far back a full read goes: a year, so trends have something to start from on day one. */
        const val FETCH_DAYS = 365L
        const val RECENT_DAYS = 3L
        private const val READ_TIMEOUT_MS = 45_000L
        private const val BACKGROUND_SUFFIX = "READ_HEALTH_DATA_IN_BACKGROUND"
        private const val HISTORY_SUFFIX = "READ_HEALTH_DATA_HISTORY"
    }
}
