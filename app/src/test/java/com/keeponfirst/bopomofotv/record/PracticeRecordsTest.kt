package com.keeponfirst.bopomofotv.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PracticeRecordsTest {
    private val day = LocalDate.of(2026, 10, 12)

    @Test
    fun keyRoundTripsDateSymbolAndResult() {
        val key = PracticeRecords.key(day, "ㄅ", PracticeResult.AGAIN)
        assertEquals(PracticeEntry(day, "ㄅ", PracticeResult.AGAIN, 2), PracticeRecords.parse(key, 2))
    }

    @Test
    fun nonRecordKeysAreIgnored() {
        assertNull(PracticeRecords.parse("parent_unit", 5))
    }
}
