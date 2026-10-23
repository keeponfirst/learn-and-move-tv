package com.keeponfirst.bopomofotv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 大人設定的入口：小孩亂按（短按、連按）進不去，大人按住 2 秒才進得去。 */
class HoldGateTest {
    @Test
    fun shortPressOnlyShowsHint() {
        val gate = HoldGate()
        gate.down(1_000)
        assertFalse(gate.poll(1_500))
        assertEquals(Release.HINT, gate.up(1_300))
    }

    @Test
    fun justUnderTwoSecondsIsStillShort() {
        val gate = HoldGate()
        gate.down(0)
        assertFalse(gate.poll(ADULT_HOLD_MILLIS - 1))
        assertEquals(Release.HINT, gate.up(ADULT_HOLD_MILLIS - 1))
    }

    @Test
    fun holdingTwoSecondsOpensOnceWithoutWaitingForRelease() {
        val gate = HoldGate()
        gate.down(0)
        assertTrue(gate.poll(ADULT_HOLD_MILLIS))
        assertFalse("同一次按住只開一次", gate.poll(ADULT_HOLD_MILLIS + 500))
        assertEquals("已經開過，放開不再做事", Release.NONE, gate.up(ADULT_HOLD_MILLIS + 800))
    }

    @Test
    fun releaseRightAtTwoSecondsBeforeTimerStillOpens() {
        val gate = HoldGate()
        gate.down(0)
        assertEquals(Release.OPEN, gate.up(ADULT_HOLD_MILLIS))
    }

    /** 按住時遙控器會一直送重複的 KeyDown，不能每次都重設起點。 */
    @Test
    fun repeatedKeyDownDoesNotRestartTheTimer() {
        val gate = HoldGate()
        assertTrue(gate.down(0))
        assertFalse(gate.down(600))
        assertFalse(gate.down(1_200))
        assertTrue(gate.poll(ADULT_HOLD_MILLIS))
    }

    @Test
    fun tappingManyTimesNeverOpens() {
        val gate = HoldGate()
        repeat(10) { k ->
            val t = k * 300L
            gate.down(t)
            assertFalse(gate.poll(t + 200))
            assertEquals(Release.HINT, gate.up(t + 200))
        }
    }

    @Test
    fun releaseWithoutPressDoesNothing() {
        assertEquals(Release.NONE, HoldGate().up(5_000))
    }
}
