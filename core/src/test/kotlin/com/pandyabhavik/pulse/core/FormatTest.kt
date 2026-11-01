package com.pandyabhavik.pulse.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormatTest {
    @Test fun dates() {
        val today = LocalDate.of(2026, 11, 20)
        assertEquals("17 Dec", Format.date(LocalDate.of(2026, 12, 17), today))
        assertEquals("1 Nov", Format.date(LocalDate.of(2026, 11, 1), today))
        assertEquals("31 Jan 2027", Format.date(LocalDate.of(2027, 1, 31), today))
        assertEquals("7 Sep 2027", Format.date(LocalDate.of(2027, 9, 7), today))
    }

    @Test fun ints() {
        assertEquals("520", Format.int(520))
        assertEquals("1,140", Format.int(1140))
        assertEquals("10,000", Format.int(10_000))
        assertEquals("-2,300", Format.int(-2300))
    }

    @Test fun kg() {
        assertEquals("115", Format.kg(115.0))
        assertEquals("117.6", Format.kg(117.64))
        assertEquals("2", Format.kg(120.0 - 118.0))
        assertEquals("0.2", Format.kg(0.2))
        assertEquals("96", Format.kg(95.999999))
    }

    @Test fun days() {
        assertEquals("1 day", Format.days(1))
        assertEquals("0 days", Format.days(0))
        assertEquals("3 days", Format.days(3))
    }

    @Test fun words() {
        assertEquals("three", Format.word(3))
        assertEquals("15", Format.word(15))
        assertEquals("Three", Format.capitalized(Format.word(3)))
    }
}
