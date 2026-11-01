# Pulse build contract (for everyone working on the code)

Package `com.pandyabhavik.pulse`. Kotlin 2.1, Compose (BOM 2024.12.01), Room 2.6.1, WorkManager 2.10,
Health Connect client 1.1.0-alpha10, Glance 1.1.1, androidx.window 1.3.0. minSdk 30, target 35.
There is NO Android SDK in this sandbox: the app module can't be compiled here. CI compiles it. So write
Kotlin carefully: exact imports, no guessed APIs. The `core` module is plain Kotlin and CAN be built and
tested here with `gradle -p core test` (see core/ standalone note in core/README.md).

Approved designs: /mnt/project-files/Pulse for Android/Pulse-mocks.html (read it; match it).
Concept: weight-loss buddy. Home = 3 numbers: calorie balance (burned - eaten, live), steps (live), trend weight + next milestone date.

## Ownership (each worker edits only its own files)
- core/**                                  -> core worker
- app/.../data/**, health/**, work/**, Graph.kt, PulseApp.kt, MainActivity.kt, PrivacyActivity.kt,
  AndroidManifest.xml (except the widget <receiver> blocks), res/values/strings.xml -> data worker
- app/.../ui/**                            -> UI worker
- app/.../widget/**, res/xml/widget_*.xml, res/layout/widget_preview_*.xml, res/drawable/widget_*,
  res/values/widget_strings.xml, the widget <receiver> blocks (delivered as app/WIDGET_MANIFEST_SNIPPET.xml,
  the data worker pastes them in) -> widget worker
- app/src/androidTest/**, ci/**, .github/** -> test worker

## Fixed names
- `Graph` (object, Graph.kt):
  - `fun repo(context: Context): PulseRepository`
  - `suspend fun snapshot(context: Context): HomeState`
  - `@Volatile var healthOverride: HealthSource?` (tests set a fake before first use)
  - `@Volatile var clockOverride: java.time.Clock?` (tests pin "now")
- `PulseRepository` (data/PulseRepository.kt):
  - `val state: kotlinx.coroutines.flow.StateFlow<HomeState?>`
  - `suspend fun sync(): Boolean` reads Health Connect (last 90 days first time, then last 3 days), stores in Room, recomputes state, refreshes widgets. Returns false on failure.
  - `suspend fun snapshot(): HomeState`
  - `suspend fun saveProfile(p: Profile)` (onboarding / settings)
  - `suspend fun markCelebrated(kg: Double)`; `suspend fun commitRecap()`; `suspend fun dismissRecap()`
  - `fun onSensorSteps(totalSinceBoot: Long)` live ticker feed from the phone step counter
  - `suspend fun setNudges(morning: Boolean, evening: Boolean)`; `val settings` exposes current Settings
  - `suspend fun resetForTests()`
- `data/Settings.kt`: `data class Settings(profile: Profile?, morningNudge: Boolean = true, eveningNudge: Boolean = true, celebratedKg: Set<Double>, recapCommittedWeek: LocalDate?, recapDismissedWeek: LocalDate?, lastMorningNudge: LocalDate?, lastEveningNudge: LocalDate?, firstSyncDone: Boolean)`
- `ui/MainViewModel` (AndroidViewModel): `val state: StateFlow<HomeState?>`, `fun sync()`, `fun saveProfile(p)`, `fun celebrated(kg)`, `fun commitRecap()`, `fun dismissRecap()`, `fun setNudges(m,e)`, `val repo`.
- `ui/AppRoot(vm, requestedTab: MutableState<String?>, askHealth: () -> Unit, askActivity: () -> Unit, askNotifications: () -> Unit)`
- `ui/PulseTheme { }` composable; `ui/C` object with colours: bg 0xFF050608, s1 0xFF0C0E13, s2 0xFF14171F, s3 0xFF1C202A, line 0xFF232836, ink 0xFFF3F4F8, mute 0xFF8D93A6, dim 0xFF5A6072, under 0xFF2EF2A8, over 0xFFFF4D6D, steps 0xFF4DA8FF, wt 0xFFB48CFF, wt2 0xFFFF7AD9, sleep 0xFF7C8CFF, food 0xFFFFB86B, hot 0xFFFF8A5C.
- Fonts: R.font.bricolage (display), R.font.figtree (body).
- `MainActivity.EXTRA_TAB` = "tab"; values "today", "journey", "history", "settings".
- `widget/Widgets.refresh(context: Context)` suspend: redraws all widgets. Called by the repository after each sync.
- `work/Work.schedule(context)`: periodic 15-min sync worker + boot receiver. `work/Notifier`: channel "nudges".

## Test tags (Compose Modifier.testTag) the UI must set and tests use
today_screen, journey_screen, history_screen, settings_screen, onboarding_screen,
nav_today, nav_journey, nav_history, nav_settings,
balance_ring, balance_value (text e.g. "520"), balance_label ("kcal under"/"kcal over"), breakdown_sheet,
steps_value, weight_trend_value, next_milestone, streak_chip, win_chip, spill_line,
journey_chart, milestone_list, whatif_slider, whatif_result, journey_line,
history_list, history_calendar, day_detail,
recap_card, recap_commit, celebration, celebration_continue,
onboarding_name, onboarding_height, onboarding_age, onboarding_weight, onboarding_goal, onboarding_male, onboarding_next, onboarding_connect, onboarding_start,
settings_nudge_morning, settings_nudge_evening, settings_goal

## Layouts (Galaxy Z Fold 8)
- Cover: window width < 600dp -> bottom nav (Today / Journey / History), settings via top-right icon.
- Inner: width >= 600dp -> nav rail on the left (+ Settings at the bottom) and two panes per screen.
- Flex mode: a horizontal FoldingFeature in HALF_OPENED state (androidx.window WindowInfoTracker) ->
  top half status display (ring + steps + weight), bottom half details + nav. Nothing across the hinge.
- CI emulator sizes: cover 1080x2520 @420dpi with font scale 1.15; inner 1968x2184 @374dpi.
