package com.pandyabhavik.pulse.health

import android.content.Intent
import com.pandyabhavik.pulse.core.DayLog
import com.pandyabhavik.pulse.data.HealthStatus
import java.time.LocalDate
import java.time.ZoneId

// SHARED CONTRACT: where day data comes from. HealthGateway reads Health Connect; tests swap in a fake
// through Graph.healthOverride.
interface HealthSource {
    fun status(): HealthStatus
    suspend fun granted(): Set<String>
    /** Reads [from]..[to] inclusive, one DayLog per date (dates with no data at all may be omitted). */
    suspend fun readDays(from: LocalDate, to: LocalDate, zone: ZoneId): List<DayLog>
    fun installIntent(): Intent
    fun settingsIntent(): Intent
    /** Permission strings Pulse asks for (Health Connect read permissions + background read). */
    val permissions: Set<String>
}
