package com.pandyabhavik.pulse.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pandyabhavik.pulse.Graph
import com.pandyabhavik.pulse.core.Profile
import com.pandyabhavik.pulse.data.HomeState
import com.pandyabhavik.pulse.data.PulseRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The UI's single door to the repository. All numbers come from [state]; actions go straight to the repo. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo: PulseRepository = Graph.repo(app)

    val state: StateFlow<HomeState?> = repo.state

    private val _syncing = MutableStateFlow(false)

    /** True while a sync started from the UI (pull-to-refresh, sync chip) runs. */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastSyncFailed = MutableStateFlow(false)
    val lastSyncFailed: StateFlow<Boolean> = _lastSyncFailed.asStateFlow()

    private val _syncNote = MutableStateFlow<String?>(null)
    /** One line on how the last UI sync went ("Synced · all data read", or why it failed), for Settings. */
    val syncNote: StateFlow<String?> = _syncNote.asStateFlow()

    /** Syncs now. With [announce], a short toast says how it went (the Sync now button and the sync chip). */
    fun sync(announce: Boolean = false) {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            try {
                // A background sync may hold the lock; give up waiting rather than spin forever.
                val result = withTimeoutOrNull(60_000) { runCatching { repo.sync(quick = true) }.getOrDefault(false) }
                val ok = result == true
                _lastSyncFailed.value = !ok
                val problems = repo.lastProblems
                val note = when {
                    result == null -> "Sync is taking too long. Try again in a minute."
                    !ok -> repo.lastError ?: "Couldn't read Health Connect"
                    problems.isEmpty() -> "Synced just now"
                    else -> "Synced, but couldn't read ${problems.joinToString { it.substringBefore(':') }}"
                }
                _syncNote.value = note
                if (announce) Toast.makeText(getApplication(), note, Toast.LENGTH_SHORT).show()
                // Long enough for the pull indicator to read as "checked" even when the sync is instant.
                delay(350)
            } finally {
                _syncing.value = false
            }
        }
    }

    fun saveProfile(p: Profile) {
        viewModelScope.launch {
            repo.saveProfile(p)
            sync()
        }
    }

    fun celebrated(kg: Double) {
        viewModelScope.launch { repo.markCelebrated(kg) }
    }

    fun commitRecap() {
        viewModelScope.launch { repo.commitRecap() }
    }

    fun dismissRecap() {
        viewModelScope.launch { repo.dismissRecap() }
    }

    fun setNudges(morning: Boolean, evening: Boolean) {
        viewModelScope.launch { repo.setNudges(morning, evening) }
    }

    fun setDashboard(tiles: List<String>) {
        viewModelScope.launch { repo.setDashboard(tiles) }
    }

    private val _checks = MutableStateFlow<List<com.pandyabhavik.pulse.health.TypeCheck>?>(null)
    /** The last data check (null until one ran). */
    val checks: StateFlow<List<com.pandyabhavik.pulse.health.TypeCheck>?> = _checks.asStateFlow()
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    fun checkData() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            try {
                _checks.value = withTimeoutOrNull(30_000) { repo.checkData() } ?: emptyList()
            } finally {
                _checking.value = false
            }
        }
    }
}
