package com.pandyabhavik.pulse.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pandyabhavik.pulse.Graph
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * The background sync: every 15 minutes, the shortest period Android gives background work. Health
 * Connect has no push, so this is as fresh as the data can be without the app open. The repository's
 * sync also redraws the widgets and sends the morning and evening nudges when they are due.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            Graph.repo(applicationContext).sync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SyncWorker", "Sync failed", e)
        }
        // A failed read is retried by the next periodic run anyway; backing off would only delay it.
        return Result.success()
    }
}

object Work {
    private const val SYNC = "sync"
    private const val SYNC_ONCE = "sync-once"

    /** Books the periodic sync. KEEP, so calling it at every app start doesn't reset the period. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.NONE)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SYNC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One sync as soon as possible, outside the app's process lifetime. */
    fun syncSoon(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_ONCE, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SyncWorker>().build(),
        )
    }
}

/** Books the background sync again after a restart or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Work.schedule(context)
                Work.syncSoon(context)
            }
        }
    }
}

fun appInForeground(): Boolean =
    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
