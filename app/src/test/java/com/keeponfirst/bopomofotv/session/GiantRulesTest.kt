package com.keeponfirst.bopomofotv.session

import com.keeponfirst.bopomofotv.data.GiantLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 巨人小螞蟻：3 組固定順序輪流、8 真 2 假、慢中快 6／5／4 秒；
 * 語音不疊播：長句（ga_fake_after 6 秒）在 4 秒的快段也要等它播完才出下一個口令；連玩 3 輪要休息。
 */
class GiantRulesTest {
    /** 音檔的實際長度（毫秒，afinfo 2026-10-24 量）。 */
    private val measured = mapOf(
        "ga_ant" to 1464L, "ga_end_1" to 3600L, "ga_end_2" to 5928L, "ga_end_3" to 5304L, "ga_fake_after" to 6024L,
        "ga_fake_bird" to 1248L, "ga_fake_elephant" to 1296L, "ga_fake_fish" to 1224L, "ga_fake_watermelon" to 1560L,
        "ga_faster" to 1488L, "ga_fastest" to 1488L, "ga_giant" to 1320L, "ga_go" to 2256L, "ga_intro_1" to 4992L,
        "ga_intro_2" to 4032L, "ga_intro_3" to 6552L, "ga_practice" to 2064L, "ga_rest" to 5304L, "ga_rule_ant" to 5016L,
        "ga_rule_easy" to 6288L, "ga_rule_fake" to 6144L, "ga_rule_giant" to 4824L, "ga_safe_slow" to 5736L,
    )
    private val speech: (String) -> Long = { measured.getValue(it) }

    private fun plan(set: Int, kind: GiantRound = GiantRound.FIRST, roundsDone: Int = 0, s: (String) -> Long = speech) =
        GiantRules.schedule(kind, GIANT_SETS[set], roundsDone, s)

    private fun allPlans(s: (String) -> Long = speech) =
        GIANT_SETS.indices.flatMap { i -> GiantRound.entries.map { k -> plan(i, k, s = s) } }

    // ---- 3 組順序（設計規格的 6 條規則）

    @Test
    fun everySetHasEightRealAndTwoFakeCalls() {
        assertEquals(3, GIANT_SETS.size)
        GIANT_SETS.forEach { set ->
            assertEquals(GIANT_CALLS, set.size)
            assertEquals(4, set.count { it == GiantCall.GIANT })
            assertEquals(4, set.count { it == GiantCall.ANT })
            assertEquals(2, set.count { it.fake })
        }
    }

    @Test
    fun fakeCallsSitAtTheSpecPositionsAndNeverInTheSlowPart() {
        // 位置 0 起算：組 1［大象］第 6 個、［小魚］第 9 個；組 2［小鳥］第 5、［大西瓜］第 8；組 3［大西瓜］第 7、［小鳥］第 9。
        assertEquals(listOf(5, 8), GIANT_SETS[0].indices.filter { GIANT_SETS[0][it].fake })
        assertEquals(listOf(4, 7), GIANT_SETS[1].indices.filter { GIANT_SETS[1][it].fake })
        assertEquals(listOf(6, 8), GIANT_SETS[2].indices.filter { GIANT_SETS[2][it].fake })
        assertEquals(listOf(GiantCall.ELEPHANT, GiantCall.FISH), GIANT_SETS[0].filter { it.fake })
        assertEquals(listOf(GiantCall.BIRD, GiantCall.WATERMELON), GIANT_SETS[1].filter { it.fake })
        assertEquals(listOf(GiantCall.WATERMELON, GiantCall.BIRD), GIANT_SETS[2].filter { it.fake })
        GIANT_SETS.forEach { set -> assertTrue("慢段沒有假口令", set.take(3).none { it.fake }) }
    }

    @Test
    fun fakesAreApartAndTheKidAlwaysSwitchesPose() {
        GIANT_SETS.forEach { set ->
            set.zipWithNext().forEach { (a, b) -> assertFalse("兩個假口令不相連 $set", a.fake && b.fake) }
            // 真口令不連續重複（不出「巨、巨」），跨過假口令也一樣：假口令後一定要「換」。
            set.filter { !it.fake }.zipWithNext().forEach { (a, b) -> assertTrue("真口令連續重複 $set", a != b) }
            set.indices.filter { set[it].fake }.forEach { i -> assertTrue(set[i - 1] != set[i + 1]) }
        }
    }

    @Test
    fun fakeWordsSoundLikeTheRealOnesButAreNotYogaOrJumpingAnimals() {
        GiantCall.entries.filter { it.fake }.forEach { c ->
            val text = GiantLines.DEFAULTS.getValue(c.line)
            assertTrue("假口令帶「大」或「小」：$text", text.startsWith("變大") || text.startsWith("變小"))
            listOf("兔", "青蛙", "袋鼠", "高山", "星星", "大樹", "貓", "狗", "蝴蝶", "石頭").forEach { assertFalse(text, it in text) }
        }
    }

    @Test
    fun threeSetsRotateInFixedOrderWithoutRandomness() {
        assertEquals((0 until 7).map { GIANT_SETS[it % 3] }, (0 until 7).map { GiantRules.setFor(it) })
        assertEquals(GiantRules.setFor(4), GiantRules.setFor(4))
    }

    // ---- 節奏

    @Test
    fun beatsAreSixFiveFourAndNeverFasterThanFour() {
        assertEquals(listOf(6, 6, 6, 5, 5, 5, 5, 4, 4, 4).map { it * 1000L }, (0 until GIANT_CALLS).map { giantBeatMillis(it) })
        allPlans().forEach { p ->
            val calls = p.filter { it.callIndex >= 0 }
            assertEquals((0 until GIANT_CALLS).toList(), calls.map { it.callIndex })
            // 口令都短（≤ 2 秒），每個口令就是它的節奏秒數。
            calls.forEach { assertEquals(giantBeatMillis(it.callIndex), it.slotMillis) }
            p.filter { it.call != null }.forEach { assertTrue(it.slotMillis >= 4_000L) }
        }
    }

    @Test
    fun fasterAndFastestPromptsComeRightBeforeTheMiddleAndFastParts() {
        allPlans().forEach { p ->
            val i4 = p.indexOfFirst { it.callIndex == 3 }
            val i8 = p.indexOfFirst { it.callIndex == 7 }
            assertEquals(listOf(GiantLine.FASTER), p[i4 - 1].lines)
            assertEquals(listOf(GiantLine.FASTEST), p[i8 - 1].lines)
        }
    }

    // ---- 不疊播：每段等上一段播完

    @Test
    fun nextCueNeverStartsBeforeThePreviousSpeechEnds() {
        allPlans().forEach { p -> assertNoOverlap(p, speech) }
        GiantRules.schedule(GiantRound.FIRST, GIANT_SETS[0], 2, speech).let { assertNoOverlap(it, speech) }
    }

    @Test
    fun longFakeAfterInTheFourSecondPartPushesTheNextCallBack() {
        // 組 1 快段：巨 ［小魚］ 蟻。小魚 4 秒 → ga_fake_after（6.0 秒）播完 → 才出「蟻」。
        val p = plan(0)
        val fish = p.first { it.call == GiantCall.FISH }
        assertEquals(8, fish.callIndex)
        assertEquals(4_000L, fish.slotMillis)
        val after = p[p.indexOf(fish) + 1]
        assertEquals(listOf(GiantLine.FAKE_AFTER), after.lines)
        assertEquals(fish.atMillis + 4_000L, after.atMillis)
        assertEquals(6_024L + GIANT_GAP, after.slotMillis)
        val ant = p[p.indexOf(after) + 1]
        assertEquals(9, ant.callIndex)
        assertEquals(after.atMillis + 6_024L + GIANT_GAP, ant.atMillis)
    }

    @Test
    fun everyFakeIsFollowedByFakeAfterAndOnlyFakes() {
        allPlans().forEach { p ->
            p.forEachIndexed { i, c ->
                if (c.call?.fake == true) assertEquals(GiantScene.FAKE_AFTER, p[i + 1].scene)
                if (c.scene == GiantScene.FAKE_AFTER) assertTrue(p[i - 1].call?.fake == true)
            }
        }
    }

    @Test
    fun evenIfEveryLineWereSevenSecondsNothingOverlaps() {
        val slow: (String) -> Long = { 7_000L }
        allPlans(slow).forEach { p ->
            assertNoOverlap(p, slow)
            // 口令比節奏長時，口令段被拉長到播完，不會切掉。
            p.filter { it.call != null }.forEach { assertEquals(7_000L + GIANT_GAP, it.slotMillis) }
        }
    }

    private fun assertNoOverlap(p: List<GiantCue>, s: (String) -> Long) {
        assertEquals(0L, p.first().atMillis)
        p.zipWithNext().forEach { (a, b) ->
            val end = a.atMillis + a.lines.sumOf(s)
            assertTrue("${a.lines} 播到 $end ms，下一段 ${b.lines} 在 ${b.atMillis} ms 就開始", b.atMillis >= end + GIANT_GAP)
            assertEquals(a.atMillis + a.slotMillis, b.atMillis)
        }
    }

    // ---- 一輪的流程

    @Test
    fun firstRoundHasSafetyRulesPracticeThenTenCallsThenEnding() {
        val p = plan(0)
        assertEquals(GiantLine.INTRO, p[0].lines)
        assertEquals(listOf(GiantLine.SAFE), p[1].lines)
        assertEquals(
            listOf(GiantLine.RULE_GIANT, GiantLine.RULE_ANT, GiantLine.RULE_EASY, GiantLine.RULE_FAKE, GiantLine.PRACTICE),
            p.subList(2, 7).map { it.lines.single() },
        )
        // 練習：變巨人、變小螞蟻各 6 秒，不算正式口令（腳印不亮）。
        val practice = p.subList(7, 9)
        assertEquals(listOf(GiantCall.GIANT, GiantCall.ANT), practice.map { it.call })
        practice.forEach {
            assertEquals(-1, it.callIndex)
            assertEquals(GIANT_PRACTICE_BEAT, it.slotMillis)
        }
        assertEquals(listOf(GiantLine.GO), p[9].lines)
        assertEquals(GIANT_SETS[0], p.filter { it.callIndex >= 0 }.map { it.call })
        assertEquals(listOf(GiantLine.END_HIGHFIVE, GiantLine.END_BREATHE, GiantLine.END_ASK), p.takeLast(3).map { it.lines.single() })
        assertTrue("收操呼吸要停夠久", p[p.size - 2].slotMillis >= GIANT_BREATHE_MILLIS)
    }

    @Test
    fun everyRoundPlaysTheSafetyReminderBeforeTheFirstCall() {
        allPlans().forEach { p ->
            val firstCall = p.indexOfFirst { it.call != null }
            val before = p.take(firstCall).flatMap { it.lines }
            assertTrue("安全提醒一定要播", GiantLine.SAFE in before)
            assertTrue("空間檢查一定要播", GiantLine.SPACE in before)
        }
        val again = plan(1, GiantRound.AGAIN)
        assertEquals(listOf(GiantLine.SPACE, GiantLine.SAFE), again[0].lines)
        assertEquals(listOf(GiantLine.GO), again[1].lines)
        assertTrue("再玩一輪不重播規則與練習", again.none { it.scene == GiantScene.PRACTICE || it.scene == GiantScene.RULE_GIANT })
    }

    @Test
    fun noLineTellsKidsToJump() {
        GiantLines.DEFAULTS.values.forEach { assertFalse(it, "跳" in it) }
        GiantLines.parse(File("src/main/assets/${GiantLines.PATH}").readText()).values.forEach { assertFalse(it, "跳" in it) }
    }

    // ---- 休息提醒

    @Test
    fun restAfterEveryThreeRoundsInOneVisit() {
        assertEquals(listOf(false, false, false, true, false, false, true, false), (0..7).map { GiantRules.needsRest(it) })
        // 第 1、2 輪結尾問「還想玩」；第 3 輪結尾改播 ga_rest；休息後再玩，第 6 輪又休息。
        assertEquals(GiantLine.END_ASK, plan(0, roundsDone = 0).last().lines.single())
        assertEquals(GiantLine.END_ASK, plan(1, GiantRound.AGAIN, roundsDone = 1).last().lines.single())
        assertEquals(GiantLine.REST, plan(2, GiantRound.AGAIN, roundsDone = 2).last().lines.single())
        assertEquals(GiantScene.REST, plan(2, GiantRound.AGAIN, roundsDone = 2).last().scene)
        assertEquals(GiantLine.END_ASK, plan(0, GiantRound.AGAIN, roundsDone = 3).last().lines.single())
        assertEquals(GiantLine.REST, plan(2, GiantRound.AGAIN, roundsDone = 5).last().lines.single())
        // 休息那輪仍有擊掌與收操呼吸，只換最後一句。
        assertEquals(listOf(GiantLine.END_HIGHFIVE, GiantLine.END_BREATHE), plan(2, GiantRound.AGAIN, roundsDone = 2).takeLast(3).take(2).map { it.lines.single() })
    }

    // ---- 播放、暫停、腳印

    @Test
    fun footprintsLightOnePerFormalCallAndNotDuringPractice() {
        val p = plan(1)
        val go = p.first { it.lines == listOf(GiantLine.GO) }
        assertEquals(0, GiantRules.footprints(p, go.atMillis))
        p.filter { it.callIndex >= 0 }.forEach { assertEquals(it.callIndex + 1, GiantRules.footprints(p, it.atMillis)) }
        assertEquals(10, GiantRules.footprints(p, GiantRules.totalMillis(p)))
    }

    @Test
    fun resumeRestartsTheCurrentCueFromItsStart() {
        val p = plan(0)
        val c = p[12]
        assertEquals(c.atMillis to 12, GiantRules.resume(p, c.atMillis + c.slotMillis / 2))
        assertEquals(0L to 0, GiantRules.resume(p, 0))
        val (cue, next) = GiantRules.due(p, 12, c.atMillis)
        assertEquals(c, cue)
        assertEquals(13, next)
    }

    @Test
    fun dueFiresEachCueOnceAndOnlyTheLatestAfterAJump() {
        val p = plan(0)
        val (first, i1) = GiantRules.due(p, 0, 0)
        assertEquals(p[0], first)
        assertNull(GiantRules.due(p, i1, p[1].atMillis - 1).first)
        val (latest, i3) = GiantRules.due(p, i1, p[4].atMillis)
        assertEquals(p[4], latest)
        assertEquals(5, i3)
        assertEquals(p.size, GiantRules.due(p, i3, Long.MAX_VALUE).second)
    }

    @Test
    fun roundLengthWithTheDeliveredAudio() {
        // 第一輪：開場、規則、練習比原本估的長（語音實際較長）；正式段至少 10 個節奏 50 秒。
        val first = plan(0)
        val go = first.first { it.lines == listOf(GiantLine.GO) }
        val formalEnd = first.last { it.callIndex == 9 }.let { it.atMillis + it.slotMillis }
        assertTrue(formalEnd - go.atMillis >= 50_000L)
        println("巨人小螞蟻第一輪 ${GiantRules.totalMillis(first)} ms；再玩一輪 ${GiantRules.totalMillis(plan(1, GiantRound.AGAIN))} ms；正式段（ga_go 起）${formalEnd - go.atMillis} ms")
    }

    // ---- 台詞

    /** 單元測試的工作目錄是 app/。排程用到的每一句，內建和 sync 進來的 lines.tsv 都要有。 */
    @Test
    fun everyScheduledLineHasTextInDefaultsAndInSyncedTsv() {
        val plans: List<List<GiantCue>> = allPlans() + listOf(plan(2, GiantRound.AGAIN, roundsDone = 2))
        val needed = plans.flatMap { p -> p.flatMap { it.lines } }.toSet()
        needed.forEach { assertTrue("內建缺台詞 $it", it in GiantLines.DEFAULTS) }
        val tsv = File("src/main/assets/${GiantLines.PATH}")
        assertTrue("lines.tsv 要進 repo", tsv.isFile)
        val ids = tsv.readLines().map { it.substringBefore('\t').trim().removeSuffix(".m4a") }.toSet()
        needed.forEach { assertTrue("lines.tsv 缺 $it", it in ids) }
        assertEquals(measured.keys, GiantLines.DEFAULTS.keys)
    }

    @Test
    fun linesTsvOverridesDefaults() {
        val lines = GiantLines.parse("檔名\t台詞\r\n# 註解\n\nga_go.m4a\t開始囉！\nga_xx\t\nbr_go\t刷\n")
        assertEquals("開始囉！", lines["ga_go"])
        assertEquals(GiantLines.DEFAULTS["ga_ant"], lines["ga_ant"])
        assertFalse("ga_xx" in lines)
        assertFalse("br_go" in lines)
        // 沒有音檔時的估計要比實際音檔長，才不會疊播。
        measured.forEach { (id, ms) -> assertTrue(id, GiantLines.estimateMillis(GiantLines.DEFAULTS.getValue(id)) >= ms) }
    }
}
