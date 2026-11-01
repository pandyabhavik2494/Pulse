package com.pandyabhavik.pulse.core

import com.pandyabhavik.pulse.core.Fixtures.BHAVIK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class EnergyTest {
    @Test fun bmrMale() {
        // 10*120 + 6.25*175.3 - 5*32 + 5 = 2140.625
        assertEquals(2141, Energy.bmr(BHAVIK, 120.0))
    }

    @Test fun bmrFemale() {
        assertEquals(1975, Energy.bmr(BHAVIK.copy(male = false), 120.0))
    }

    @Test fun bmrDropsWithWeight() {
        assertEquals(2141 - 350, Energy.bmr(BHAVIK, 85.0))
    }

    @Test fun restingSoFar() {
        assertEquals(0, Energy.restingSoFar(BHAVIK, 120.0, LocalDateTime.of(2026, 11, 1, 0, 0)))
        assertEquals(1071, Energy.restingSoFar(BHAVIK, 120.0, LocalDateTime.of(2026, 11, 1, 12, 0)))
        assertEquals(1784, Energy.restingSoFar(BHAVIK, 120.0, LocalDateTime.of(2026, 11, 1, 20, 0)))
        assertEquals(2141, Energy.restingSoFar(BHAVIK, 120.0, LocalDateTime.of(2026, 11, 1, 23, 59, 59, 999_999_999)))
    }

    @Test fun splitBoth() {
        assertEquals(1820 to 660, Energy.split(2480, 660, 2000))
    }

    @Test fun splitActiveLargerThanTotalClampsResting() {
        assertEquals(0 to 700, Energy.split(500, 700, 2000))
    }

    @Test fun splitTotalOnly() {
        assertEquals(2000 to 480, Energy.split(2480, null, 2000))
        assertEquals(1500 to 0, Energy.split(1500, null, 2000))
    }

    @Test fun splitActiveOnly() {
        assertEquals(2000 to 300, Energy.split(null, 300, 2000))
    }

    @Test fun splitNeither() {
        assertNull(Energy.split(null, null, 2000))
    }
}
