package com.pandyabhavik.pulse

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.health.connect.client.PermissionController
import com.pandyabhavik.pulse.data.HealthStatus
import com.pandyabhavik.pulse.ui.AppRoot
import com.pandyabhavik.pulse.ui.MainViewModel
import com.pandyabhavik.pulse.ui.PulseTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    /** The tab a notification or widget asked for: "today", "journey", "history" or "settings". */
    val requestedTab = mutableStateOf<String?>(null)

    private val healthPermissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        vm.sync()
    }

    private val activityPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) Graph.liveSteps(this).start()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so the bar icons are always light, whatever the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        requestedTab.value = intent?.getStringExtra(EXTRA_TAB)
        setContent {
            PulseTheme {
                AppRoot(
                    vm = vm,
                    requestedTab = requestedTab,
                    askHealth = { askHealth() },
                    askActivity = { askActivity() },
                    askNotifications = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_TAB)?.let { requestedTab.value = it }
    }

    override fun onStart() {
        super.onStart()
        // Every time Pulse comes on screen, read what changed in Health Connect since last time.
        vm.sync()
    }

    /** Health Connect's permission screen, or the Play Store when Health Connect is missing or outdated. */
    fun askHealth() {
        val health = Graph.health(this)
        when (health.status()) {
            HealthStatus.AVAILABLE ->
                runCatching { healthPermissions.launch(health.permissions) }
                    .onFailure { runCatching { startActivity(health.settingsIntent()) } }
            else -> runCatching { startActivity(health.installIntent()) }
        }
    }

    /** The phone step counter for the live ticker. Starts it right away when already allowed. */
    fun askActivity() {
        val live = Graph.liveSteps(this)
        if (live.allowed()) live.start() else activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}
