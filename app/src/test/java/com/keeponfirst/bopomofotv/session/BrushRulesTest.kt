package com.keeponfirst.bopomofotv.session

import com.keeponfirst.bopomofotv.data.BrushLines
import com.keeponfirst.bopomofotv.data.BrushZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 刷牙要刷滿 2 分鐘、6 區每區一樣久，孩子不用按鍵，語音照時間自己播、不疊播。 */
class BrushRulesTest {
    private val plan = BrushRules.schedule(cheers = 2)
    private fun zoneEvents(p: List<BrushEvent>) = p.filter { e -> e.lines.any { it.startsWith("br_zone_") } }
    private fun cheerEvents(p: List<BrushEvent>) = p.filter { e -> e.lines.any { it.startsWith("br_cheer_") } }

    @Test
    fun twoMinutesSixZonesOfTwentySecondsInTicketOrder() {
        assertEquals(20_000L, BRUSH_ZONE_MILLIS)
        assertEquals(120_000L, BrushRules.totalMillis())
        // 順序：上左、上前、上右、下左、下前、下右。
        assertEquals(listOf("ul", "uf", "ur", "ll", "lf", "lr"), BRUSH_ZONE_CODES)
        assertEquals(listOf("上左", "上前", "上右", "下左", "下前", "下右"), BRUSH_ZONE_NAMES)
        val zones = zoneEvents(plan)
        assertEquals((0 until 6).map { it * 20_000L }, zones.map { it.atMillis })
        assertEquals(BRUSH_ZONE_CODES.map { "br_zone_$it" }, zones.map { it.lines.last() })
    }

    @Test
    fun startCueThenSwitchHintBeforeEveryLaterZone() {
        val zones = zoneEvents(plan)
        assertEquals(listOf(BrushLine.GO, BrushLine.zone(0)), zones.first().lines)
        zones.drop(1).forEach { assertEquals(BrushLine.NEXT, it.lines.first()) }
        // 口令與換區提示不能被略過。
        zones.forEach { assertFalse(it.optional) }
    }

    @Test
    fun cheerInTheMiddleOfEachZoneRotatesAndIsSkippable() {
        val cheers = cheerEvents(plan)
        assertEquals((0 until 6).map { it * 20_000L + 10_000L }, cheers.map { it.atMillis })
        assertEquals(listOf(1, 2, 1, 2, 1, 2).map { "br_cheer_$it" }, cheers.map { it.lines.single() })
        cheers.forEach { assertTrue("鼓勵句遇到別句在播要能跳過", it.optional) }
        assertEquals(listOf(1, 2, 3, 1, 2, 3).map { "br_cheer_$it" }, cheerEvents(BrushRules.schedule(3)).map { it.lines.single() })
        assertTrue(cheerEvents(BrushRules.schedule(0)).isEmpty())
    }

    @Test
    fun endsWithDoneAdultAndSpitAtTwoMinutes() {
        assertEquals(BrushEvent(120_000L, listOf(BrushLine.DONE, BrushLine.ADULT, BrushLine.RINSE)), plan.last())
        assertEquals(plan.sortedBy { it.atMillis }, plan)
    }

    @Test
    fun stateCountsDownAndTicksZones() {
        assertEquals(BrushState(0, 0, 120, false), BrushRules.state(0))
        assertEquals(BrushState(0, 19_999, 101, false), BrushRules.state(19_999))
        assertEquals(BrushState(1, 0, 100, false), BrushRules.state(20_000))
        assertEquals(BrushState(5, 19_000, 1, false), BrushRules.state(119_000))
        assertEquals(BrushState(6, 0, 0, true), BrushRules.state(120_000))
        assertTrue(BrushRules.state(500_000).done)
    }

    @Test
    fun shortZoneForScreenshotsKeepsTheSameShape() {
        val short = BrushRules.schedule(2, zoneMillis = 4_000)
        assertEquals(plan.map { it.lines }, short.map { it.lines })
        assertEquals(24_000L, short.last().atMillis)
        assertEquals(2, BrushRules.state(9_000, 4_000).zone)
    }

    @Test
    fun dueFiresEachEventOnceAndOnlyTheLatestAfterAJump() {
        val (first, i1) = BrushRules.due(plan, 0, 0)
        assertEquals(plan[0], first)
        val (none, i2) = BrushRules.due(plan, i1, 9_999)
        assertNull(none)
        assertEquals(i1, i2)
        // 一次跳過 3 個事件：只播最後一個（第 2 區的鼓勵），不疊成一串。
        val (latest, i3) = BrushRules.due(plan, i2, 30_000)
        assertEquals(BrushEvent(30_000, listOf(BrushLine.cheer(1)), optional = true), latest)
        assertEquals(4, i3)
        val (end, i4) = BrushRules.due(plan, i3, 999_999)
        assertEquals(plan.last(), end)
        assertEquals(plan.size, i4)
        assertNull(BrushRules.due(plan, i4, 999_999).first)
    }

    @Test
    fun linesTsvOverridesDefaultsAndDecidesCheerCount() {
        assertEquals(2, BrushLines.cheerCount(BrushLines.DEFAULTS))
        val tsv = "檔名\t台詞\r\n# 註解\n\nbr_intro_1.m4a\t拿好牙刷！\nbr_cheer_1\t好棒\nbr_zone_xx\t\n"
        val lines = BrushLines.parse(tsv)
        assertEquals("拿好牙刷！", lines["br_intro_1"])
        assertEquals(1, BrushLines.cheerCount(lines))
        assertFalse("內建的第 2 句不混進來", BrushLine.cheer(1) in lines)
        assertEquals(BrushLines.DEFAULTS[BrushLine.RINSE], lines[BrushLine.RINSE])
        assertFalse("br_zone_xx" in lines)
    }

    /** 單元測試的工作目錄是 app/。assets 裡的台詞檔，每一句排程要用的都要有。 */
    @Test
    fun everyScheduledLineHasTextInDefaultsAndInSyncedTsv() {
        val needed = plan.flatMap { it.lines } + BrushLine.INTRO
        needed.forEach { assertTrue("內建缺台詞 $it", it in BrushLines.DEFAULTS) }
        val tsv = File("src/main/assets/${BrushLines.PATH}")
        assertTrue("lines.tsv 要進 repo", tsv.isFile)
        val ids = tsv.readLines().map { it.substringBefore('\t').trim().removeSuffix(".m4a") }.toSet()
        needed.forEach { assertTrue("lines.tsv 缺 $it", it in ids) }
    }

    @Test
    fun linesNeverTellKidsToSwallow() {
        BrushLines.DEFAULTS.values.filter { "吞" in it }.forEach { assertTrue(it, "不要吞" in it) }
    }

    @Test
    fun zonesJsonHasAllSixZones() {
        val f = File("src/main/assets/${BrushZones.PATH}")
        assertTrue("zones.json 要進 repo", f.isFile)
        val zones = BrushZones.parse(f.readText(), BRUSH_ZONE_CODES)
        assertNotNull(zones)
        zones!!.zones.values.forEach { z -> assertTrue(z.germs.isNotEmpty()) }
        assertNull("缺區就退回程式畫的", BrushZones.parse("""{"canvas":[1,1],"germ_canvas":1,"zones":{}}""", BRUSH_ZONE_CODES))
    }
}
