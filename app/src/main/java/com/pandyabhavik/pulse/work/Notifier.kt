package com.pandyabhavik.pulse.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pandyabhavik.pulse.MainActivity
import com.pandyabhavik.pulse.R
import com.pandyabhavik.pulse.core.Motivation
import com.pandyabhavik.pulse.data.HomeState
import com.pandyabhavik.pulse.data.Settings

/** A nudge that is due now: which one, and what it says. */
data class Nudge(val kind: Kind, val title: String, val body: String) {
    enum class Kind { MORNING, EVENING }
}

/**
 * The two daily nudges, on one channel ("nudges"):
 *  - Morning: once a day, at the first sync after today's weigh-in has arrived (trend and next milestone).
 *  - Evening: once a day, at the first sync at or after 8 PM (today's balance so far and the streak).
 * The repository decides after each sync ([due]) and records that each was sent.
 */
object Notifier {
    const val CHANNEL = "nudges"
    const val MORNING_ID = 20
    const val EVENING_ID = 21
    const val EVENING_HOUR = 20

    fun createChannels(context: Context) {
        val m = context.getSystemService(NotificationManager::class.java) ?: return
        m.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_nudges), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_nudges_description)
            },
        )
    }

    fun allowed(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    fun openApp(context: Context, tab: String? = null): PendingIntent = PendingIntent.getActivity(
        context, tab.hashCode(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (tab != null) putExtra(MainActivity.EXTRA_TAB, tab) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The nudges due for [s] given what was already sent in [settings]. Pure, so tests can call it. */
    fun due(s: HomeState, settings: Settings): List<Nudge> {
        if (s.profile == null) return emptyList()
        val today = s.now.toLocalDate()
        val out = ArrayList<Nudge>(2)
        val trendKg = s.trendKg
        if (settings.morningNudge && settings.lastMorningNudge != today && s.today.weightKg != null && trendKg != null) {
            // The trend's move since yesterday: today's point minus the one before it.
            val change = if (s.trend.size >= 2) s.trend.last().trend - s.trend[s.trend.size - 2].trend else 0.0
            val (title, body) = Motivation.morningNudge(change, trendKg, s.forecast?.next, s.shiftDays, today)
            out += Nudge(Nudge.Kind.MORNING, title, body)
        }
        if (settings.eveningNudge && settings.lastEveningNudge != today && s.now.hour >= EVENING_HOUR) {
            val (title, body) = Motivation.eveningNudge(s.balance, s.streak)
            out += Nudge(Nudge.Kind.EVENING, title, body)
        }
        return out
    }

    fun show(context: Context, nudge: Nudge) {
        if (!allowed(context)) return
        val morning = nudge.kind == Nudge.Kind.MORNING
        NotificationManagerCompat.from(context).notify(
            if (morning) MORNING_ID else EVENING_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_pulse)
                .setContentTitle(nudge.title)
                .setContentText(nudge.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.body))
                .setAutoCancel(true)
                // Morning is about the weight trend and milestones; evening about today's balance.
                .setContentIntent(openApp(context, if (morning) "journey" else "today"))
                .setColor(if (morning) 0xFFB48CFF.toInt() else 0xFF2EF2A8.toInt())
                .build(),
        )
    }
}
