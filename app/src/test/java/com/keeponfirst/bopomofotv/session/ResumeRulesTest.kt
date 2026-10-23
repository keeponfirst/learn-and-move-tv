package com.keeponfirst.bopomofotv.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「從上次繼續」：被 10 分鐘上限或返回鍵打斷的孩子，再打開 App 可以接著玩。
 * 太久以前的（超過 1 天）不再提，免得首頁一直掛著舊的。
 */
class ResumeRulesTest {
    private val now = 1_761_200_000_000L
    private val hour = 60 * 60 * 1000L

    @Test
    fun shownWithinOneDay() {
        assertNotNull(ResumeRules.shown(ResumePoint(ResumeMode.SELF, now - 23 * hour), now))
        assertNotNull(ResumeRules.shown(ResumePoint(ResumeMode.SELF, now - 24 * hour), now))
    }

    @Test
    fun hiddenAfterOneDay() {
        assertNull(ResumeRules.shown(ResumePoint(ResumeMode.SELF, now - 24 * hour - 1), now))
    }

    @Test
    fun hiddenWhenNothingSavedOrClockWentBackwards() {
        assertNull(ResumeRules.shown(null, now))
        // 電視時鐘被調回去：存的時間在未來，不顯示（不知道到底多久了）。
        assertNull(ResumeRules.shown(ResumePoint(ResumeMode.ABC, now + hour), now))
    }

    @Test
    fun yogaPointRoundTripsThroughStoredText() {
        val p = ResumePoint(ResumeMode.YOGA, now, "animal", 7)
        assertEquals(p, ResumeRules.decode(ResumeRules.encode(p)))
        val q = ResumePoint(ResumeMode.JOURNEY, now)
        assertEquals(q, ResumeRules.decode(ResumeRules.encode(q)))
    }

    @Test
    fun unreadableTextIsIgnoredNotCrash() {
        listOf(null, "", "yoga", "dance|1|x|0", "self|abc|x|0", "a|b|c|d|e").forEach {
            assertNull(it.toString(), ResumeRules.decode(it))
        }
    }

    @Test
    fun storedKeysStayStable() {
        // 存進 DataStore 的字，改了舊紀錄會讀不到。
        assertEquals(listOf("self", "abc", "yoga", "journey"), ResumeMode.entries.map { it.key })
    }

    @Test
    fun yogaResumeSkipsIntroAndStartsAtThatPoseCue() {
        val start = YogaRules.start(listOf(10, 20, 30), rounds = 2)
        val s = YogaRules.resumeAt(start, 4)
        assertEquals(YogaPhase.CUE, s.phase)
        assertEquals(4, s.step)
        assertEquals(1, s.poseIndex)
        assertEquals(2, s.round)
    }

    @Test
    fun yogaResumeOutOfRangeStartsFromFirstPose() {
        val start = YogaRules.start(listOf(10, 20), rounds = 1)
        assertEquals(0, YogaRules.resumeAt(start, 5).step)
        assertEquals(0, YogaRules.resumeAt(start, -1).step)
        assertEquals(YogaPhase.CUE, YogaRules.resumeAt(start, 5).phase)
    }
}
