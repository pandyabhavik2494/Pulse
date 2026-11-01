package com.pandyabhavik.pulse.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Text formatting shared by all copy. Plain English, fixed US-style digits so output never depends on the phone locale. */
object Format {
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

    /** "17 Dec", or "31 Jan 2027" when [date] is not in the same year as [today]. */
    fun date(date: LocalDate, today: LocalDate): String =
        if (date.year == today.year) date.format(DAY_MONTH) else date.format(DAY_MONTH_YEAR)

    /** 1140 -> "1,140", -2300 -> "-2,300". */
    fun int(n: Int): String = String.format(Locale.US, "%,d", n)

    /** 115.0 -> "115", 117.64 -> "117.6", 0.04 -> "0". At most one decimal, no trailing ".0". */
    fun kg(kg: Double): String {
        val tenths = (kg * 10).roundToLong()
        return if (tenths % 10 == 0L) (tenths / 10).toString()
        else String.format(Locale.US, "%.1f", tenths / 10.0)
    }

    /** "1 day" / "3 days". */
    fun days(n: Long): String = if (abs(n) == 1L) "$n day" else "$n days"

    fun round1(x: Double): Double = (x * 10).roundToInt() / 10.0

    private val WORDS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve",
    )

    /** 3 -> "three"; numbers past twelve stay as digits. */
    fun word(n: Int): String = if (n in WORDS.indices) WORDS[n] else int(n)

    fun capitalized(s: String): String = s.replaceFirstChar { it.uppercaseChar() }
}
