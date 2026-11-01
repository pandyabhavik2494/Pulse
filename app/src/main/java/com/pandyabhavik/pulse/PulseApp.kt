package com.pandyabhavik.pulse

import android.app.Application
import com.pandyabhavik.pulse.work.Notifier
import com.pandyabhavik.pulse.work.Work

class PulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        // The 15-minute background sync. KEEP policy, so this doesn't reset the period on every start.
        Work.schedule(this)
        // The live step ticker follows the app's foreground state (ProcessLifecycleOwner, main thread).
        Graph.liveSteps(this).attach()
    }
}
