package com.keeponfirst.bopomofotv.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YogaRulesTest {
    private val seconds = listOf(3, 5)

    /** 把整段口令跑完：開場 → 第一個口令。 */
    private fun afterIntro(rounds: Int = 2) = YogaRules.cueFinished(YogaRules.start(seconds, rounds))

    private fun repeatTick(s: YogaSession, n: Int) = (1..n).fold(s) { acc, _ -> YogaRules.tick(acc) }

    @Test
    fun countdownStartsOnlyAfterCueFinishes() {
        // 口令還在播，時間不能先走：小孩要先聽完怎麼做。
        val cue = afterIntro()
        assertEquals(YogaPhase.CUE, cue.phase)
        assertEquals(cue, YogaRules.tick(cue))

        val counting = YogaRules.cueFinished(cue)
        assertEquals(YogaPhase.COUNTDOWN, counting.phase)
        assertEquals(3, counting.remaining)
    }

    @Test
    fun countdownToZeroMovesToNextPoseCue() {
        val counting = YogaRules.cueFinished(afterIntro())
        val next = repeatTick(counting, 3)
        assertEquals(YogaPhase.CUE, next.phase)
        assertEquals(1, next.step)
        assertEquals(1, next.poseIndex)
    }

    @Test
    fun twoRoundsThenDone() {
        // 每個姿勢都要等口令播完、倒數完，2 輪共 4 個姿勢後才結束。
        var s = afterIntro(rounds = 2)
        val seen = mutableListOf<Pair<Int, Int>>()
        while (s.phase != YogaPhase.DONE) {
            assertEquals(YogaPhase.CUE, s.phase)
            seen += s.round to s.poseIndex
            s = YogaRules.cueFinished(s)
            s = repeatTick(s, s.remaining)
        }
        assertEquals(listOf(1 to 0, 1 to 1, 2 to 0, 2 to 1), seen)
    }

    @Test
    fun pauseFreezesCountdownAndResumeContinues() {
        val counting = YogaRules.tick(YogaRules.cueFinished(afterIntro()))
        assertEquals(2, counting.remaining)

        val paused = YogaRules.togglePause(counting)
        assertTrue(paused.paused)
        assertEquals(paused, repeatTick(paused, 10))

        val resumed = YogaRules.togglePause(paused)
        assertFalse(resumed.paused)
        assertEquals(1, YogaRules.tick(resumed).remaining)
    }

    @Test
    fun pausedCueIgnoresLateFinish() {
        // 暫停會停掉口令；暫停中不能因為舊的播完事件就開始倒數。
        val paused = YogaRules.togglePause(afterIntro())
        assertEquals(paused, YogaRules.cueFinished(paused))
    }

    @Test
    fun skipGoesToNextCueAndClearsPause() {
        val paused = YogaRules.togglePause(YogaRules.cueFinished(afterIntro()))
        val skipped = YogaRules.next(paused)
        assertEquals(YogaPhase.CUE, skipped.phase)
        assertEquals(1, skipped.step)
        assertFalse(skipped.paused)
    }

    @Test
    fun skipFromIntroGoesToFirstPose() {
        val skipped = YogaRules.next(YogaRules.start(seconds))
        assertEquals(YogaPhase.CUE, skipped.phase)
        assertEquals(0, skipped.step)
    }

    @Test
    fun skipOnLastPoseEnds() {
        var s = afterIntro(rounds = 1)
        s = YogaRules.next(s)
        assertEquals(YogaPhase.DONE, YogaRules.next(s).phase)
    }
}
