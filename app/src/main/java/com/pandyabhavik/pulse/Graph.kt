package com.pandyabhavik.pulse

import android.content.Context
import com.pandyabhavik.pulse.data.HomeState
import com.pandyabhavik.pulse.data.LiveSteps
import com.pandyabhavik.pulse.data.PulseDatabase
import com.pandyabhavik.pulse.data.PulseRepository
import com.pandyabhavik.pulse.data.SettingsStore
import com.pandyabhavik.pulse.health.HealthGateway
import com.pandyabhavik.pulse.health.HealthSource
import java.time.Clock

/** The app's single repository, shared by the screens, the workers and the widgets. */
object Graph {
    /** Tests set a fake Health Connect here. Read on every use, so it works even after the repo exists. */
    @Volatile var healthOverride: HealthSource? = null

    /** Tests pin "now" here (e.g. 2026-11-24 19:42). Read on every use, like [healthOverride]. */
    @Volatile var clockOverride: Clock? = null

    @Volatile private var repository: PulseRepository? = null
    @Volatile private var gateway: HealthGateway? = null
    @Volatile private var live: LiveSteps? = null

    fun repo(context: Context): PulseRepository = repository ?: synchronized(this) {
        repository ?: run {
            val app = context.applicationContext
            PulseRepository(
                context = app,
                dao = PulseDatabase.create(app).dao(),
                store = SettingsStore(app),
                healthProvider = { health(app) },
                clockProvider = { clock() },
            ).also { repository = it }
        }
    }

    /** Health Connect, or the test fake. */
    fun health(context: Context): HealthSource = healthOverride ?: gateway ?: synchronized(this) {
        gateway ?: HealthGateway(context.applicationContext).also { gateway = it }
    }

    fun clock(): Clock = clockOverride ?: Clock.systemDefaultZone()

    /** The phone step-counter listener feeding the live ticker. */
    fun liveSteps(context: Context): LiveSteps = live ?: synchronized(this) {
        live ?: LiveSteps(context.applicationContext, repo(context)).also { live = it }
    }

    /** The state right now, for widgets, notifications and workers. */
    suspend fun snapshot(context: Context): HomeState = repo(context).snapshot()
}
