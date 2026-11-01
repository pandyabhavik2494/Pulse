package com.pandyabhavik.pulse.ui

import android.app.Application
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

/** The UI's single door to the repository. All numbers come from [state]; actions go straight to the repo. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo: PulseRepository = Graph.repo(app)

    val state: StateFlow<HomeState?> = repo.state

    private val _syncing = MutableStateFlow(false)

    /** True while a sync started from the UI (pull-to-refresh, sync chip) runs. */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastSyncFailed = MutableStateFlow(false)
    val lastSyncFailed: StateFlow<Boolean> = _lastSyncFailed.asStateFlow()

    fun sync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            try {
                val ok = runCatching { repo.sync() }.getOrDefault(false)
                _lastSyncFailed.value = !ok
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
}
