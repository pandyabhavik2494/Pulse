package com.pandyabhavik.pulse.data

import com.pandyabhavik.pulse.core.DayLog
import com.pandyabhavik.pulse.core.Forecast
import com.pandyabhavik.pulse.core.Milestone
import com.pandyabhavik.pulse.core.Profile
import com.pandyabhavik.pulse.core.RingStyle
import com.pandyabhavik.pulse.core.TrendPoint
import com.pandyabhavik.pulse.core.WeeklyRecap
import java.time.Instant
import java.time.ZonedDateTime

// SHARED CONTRACT: everything the screens, widgets and notifications draw comes from one HomeState.
// Built by PulseRepository.snapshot(); never computed inside UI code.

enum class HealthStatus { AVAILABLE, NEEDS_UPDATE, NOT_INSTALLED }

data class HomeState(
    val now: ZonedDateTime,
    val profile: Profile?,                 // null until onboarding is done
    val health: HealthStatus,
    val missingPermissions: Set<String>,   // Health Connect permissions not yet granted
    val lastSync: Instant?,                // last successful Health Connect read

    // ---- Today
    val today: DayLog,
    /** Steps to show: Health Connect steps plus phone-sensor steps taken since the last Health Connect change. */
    val liveSteps: Int,
    /** True while the phone step sensor is feeding the ticker (app in foreground, permission granted). */
    val liveActive: Boolean,
    val burned: Int?,                      // today.burnedTotal (Fitbit: resting + active), else estimate
    val resting: Int?,
    val active: Int?,
    val eaten: Int?,                       // null = nothing logged yet
    val balance: Int?,                     // burned - eaten; positive = under, negative = over

    // ---- Weight and journey
    val trend: List<TrendPoint>,           // from start date (or first weigh-in) to today
    val trendKg: Double?,                  // latest trend weight
    val forecast: Forecast?,
    val shiftDays: Int?,                   // next milestone moved this many days earlier (+) / later (-) since yesterday
    val journeyLine: String?,

    // ---- Motivation
    val streak: Int,
    val avg7: Int?,
    val winsDone: Int,
    val winsTotal: Int,
    val toNextWin: Double?,
    val recap: WeeklyRecap?,               // shown on Today on Monday/Tuesday until dismissed
    val recapCommitted: Boolean,
    val celebrate: Milestone?,             // a reached milestone not yet celebrated
    val ringStyle: RingStyle,

    // ---- History (newest first, up to 90 days, today included)
    val history: List<DayLog>,
) {
    val onboarded: Boolean get() = profile != null
    val isOver: Boolean get() = (balance ?: 0) < 0
}
