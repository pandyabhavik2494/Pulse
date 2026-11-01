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
    /** What the last [readDays] couldn't read ("Heart rate: rate limited"), empty when everything came back. */
    val problems: List<String> get() = emptyList()

    /** One line per data type for Settings' data check: is it allowed, is anything there, who writes it. */
    suspend fun check(now: java.time.Instant): List<TypeCheck> = emptyList()
}

/** What Health Connect holds for one data type over the last 7 days. */
data class TypeCheck(
    val name: String,
    val allowed: Boolean,
    val records: Int,
    /** The apps that wrote it (app names where the phone knows them). */
    val sources: List<String>,
    val latest: java.time.Instant?,
    val error: String? = null,
    /** Today's value as each app wrote it ("Fitbit" to "8,412"), to compare with what Pulse shows. */
    val today: List<Pair<String, String>> = emptyList(),
)
