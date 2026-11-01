package com.pandyabhavik.pulse.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pandyabhavik.pulse.core.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

data class Settings(
    /** Null until onboarding is done. */
    val profile: Profile? = null,
    val morningNudge: Boolean = true,
    val eveningNudge: Boolean = true,
    /** Milestones (by kg) whose celebration has been shown. Each one unlocks the next ring style. */
    val celebratedKg: Set<Double> = emptySet(),
    /** The Monday of the recap week Bhavik committed to beat. */
    val recapCommittedWeek: LocalDate? = null,
    /** The Monday of the recap week that was dismissed, so it stays hidden. */
    val recapDismissedWeek: LocalDate? = null,
    /** The days a nudge was already sent (or seen in the app), so each goes out once a day. */
    val lastMorningNudge: LocalDate? = null,
    val lastEveningNudge: LocalDate? = null,
    /** False until the first 90-day read has finished; after that each sync reads the last 3 days. */
    val firstSyncDone: Boolean = false,
    /** The last successful Health Connect read. */
    val lastSync: Instant? = null,
    /** The tiles on Today's dashboard, in order (ids from the dashboard catalogue). */
    val dashboard: List<String> = DEFAULT_DASHBOARD,
    /** Which full history read was done last ("365-true" = a year, with the history permission). */
    val backfill: String? = null,
) {
    companion object {
        val DEFAULT_DASHBOARD = listOf("calories", "left_to_eat", "steps", "weight", "in_2_weeks", "resting_hr", "sleep", "burned")
    }

    /** Milestone weights are computed, so compare with a tolerance rather than exact Double equality. */
    fun isCelebrated(kg: Double): Boolean = celebratedKg.any { abs(it - kg) < 0.001 }
}

private val Context.store: DataStore<Preferences> by preferencesDataStore(
    "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsStore(context: Context) {
    private val store = context.store

    private object K {
        val name = stringPreferencesKey("p_name")
        val height = doublePreferencesKey("p_height_cm")
        val age = intPreferencesKey("p_age")
        val male = booleanPreferencesKey("p_male")
        val startKg = doublePreferencesKey("p_start_kg")
        val goalKg = doublePreferencesKey("p_goal_kg")
        val startDate = stringPreferencesKey("p_start_date")
        val stepGoal = intPreferencesKey("p_step_goal")
        val morning = booleanPreferencesKey("n_morning")
        val evening = booleanPreferencesKey("n_evening")
        val celebrated = stringSetPreferencesKey("celebrated_kg")
        val recapCommitted = stringPreferencesKey("recap_committed")
        val recapDismissed = stringPreferencesKey("recap_dismissed")
        val lastMorning = stringPreferencesKey("last_morning")
        val lastEvening = stringPreferencesKey("last_evening")
        val firstSync = booleanPreferencesKey("first_sync_done")
        val lastSync = longPreferencesKey("last_sync")
        val dashboard = stringPreferencesKey("dashboard_tiles")
        val backfill = stringPreferencesKey("backfill")
    }

    val flow: Flow<Settings> = store.data.map { read(it) }

    suspend fun current(): Settings = flow.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit { p -> write(p, change(read(p))) }
    }

    private fun read(p: Preferences): Settings = Settings(
        profile = readProfile(p),
        morningNudge = p[K.morning] ?: true,
        eveningNudge = p[K.evening] ?: true,
        celebratedKg = p[K.celebrated].orEmpty().mapNotNull { it.toDoubleOrNull() }.toSet(),
        recapCommittedWeek = p[K.recapCommitted]?.let { date(it) },
        recapDismissedWeek = p[K.recapDismissed]?.let { date(it) },
        lastMorningNudge = p[K.lastMorning]?.let { date(it) },
        lastEveningNudge = p[K.lastEvening]?.let { date(it) },
        firstSyncDone = p[K.firstSync] ?: false,
        lastSync = p[K.lastSync]?.let(Instant::ofEpochMilli),
        dashboard = p[K.dashboard]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
            ?: Settings.DEFAULT_DASHBOARD,
        backfill = p[K.backfill],
    )

    /** A profile exists once all of its required fields were written by onboarding. */
    private fun readProfile(p: Preferences): Profile? {
        val name = p[K.name] ?: return null
        return Profile(
            name = name,
            heightCm = p[K.height] ?: return null,
            age = p[K.age] ?: return null,
            male = p[K.male] ?: true,
            startKg = p[K.startKg] ?: return null,
            goalKg = p[K.goalKg] ?: return null,
            startDate = p[K.startDate]?.let { date(it) } ?: return null,
            stepGoal = p[K.stepGoal] ?: 10_000,
        )
    }

    private fun write(p: MutablePreferences, s: Settings) {
        val pr = s.profile
        if (pr != null) {
            p[K.name] = pr.name
            p[K.height] = pr.heightCm
            p[K.age] = pr.age
            p[K.male] = pr.male
            p[K.startKg] = pr.startKg
            p[K.goalKg] = pr.goalKg
            p[K.startDate] = pr.startDate.toString()
            p[K.stepGoal] = pr.stepGoal
        } else {
            listOf(K.name, K.startDate).forEach { p.remove(it) }
            listOf(K.height, K.startKg, K.goalKg).forEach { p.remove(it) }
            listOf(K.age, K.stepGoal).forEach { p.remove(it) }
            p.remove(K.male)
        }
        p[K.morning] = s.morningNudge
        p[K.evening] = s.eveningNudge
        p[K.celebrated] = s.celebratedKg.map { it.toString() }.toSet()
        putDate(p, K.recapCommitted, s.recapCommittedWeek)
        putDate(p, K.recapDismissed, s.recapDismissedWeek)
        putDate(p, K.lastMorning, s.lastMorningNudge)
        putDate(p, K.lastEvening, s.lastEveningNudge)
        p[K.firstSync] = s.firstSyncDone
        if (s.lastSync != null) p[K.lastSync] = s.lastSync.toEpochMilli() else p.remove(K.lastSync)
        p[K.dashboard] = s.dashboard.joinToString(",")
        if (s.backfill != null) p[K.backfill] = s.backfill else p.remove(K.backfill)
    }

    private fun putDate(p: MutablePreferences, key: Preferences.Key<String>, value: LocalDate?) {
        if (value != null) p[key] = value.toString() else p.remove(key)
    }

    private fun date(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

    suspend fun clear() {
        store.edit { it.clear() }
    }
}
