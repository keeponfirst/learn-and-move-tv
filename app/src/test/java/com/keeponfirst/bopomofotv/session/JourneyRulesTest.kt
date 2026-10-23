package com.keeponfirst.bopomofotv.session

import com.keeponfirst.bopomofotv.record.StickerId
import com.keeponfirst.bopomofotv.record.StickerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 小旅程：每天排 3 站、同一天接著走、跨日換新的。 */
class JourneyRulesTest {
    private val zhuyin = listOf("ㄅ", "ㄆ", "ㄇ", "ㄈ")
    private val abc = listOf("A", "B", "C")
    private val yoga = listOf("mountain", "tree_left", "frog")
    private val pool = JourneyPool(zhuyin, abc, yoga)
    private val today = LocalDate.of(2026, 10, 23)

    private fun z(s: String) = StickerId(StickerKind.ZHUYIN, s)
    private fun a(s: String) = StickerId(StickerKind.ABC, s)
    private fun y(s: String) = StickerId(StickerKind.YOGA, s)
    private val allOwned = (zhuyin.map(::z) + abc.map(::a) + yoga.map(::y)).toSet()

    @Test
    fun planHasOneZhuyinThenOneLetterThenOneYogaPose() {
        val stations = JourneyRules.plan(today, pool, emptySet(), emptySet())
        assertEquals(listOf(StickerKind.ZHUYIN, StickerKind.ABC, StickerKind.YOGA), stations.map { it.kind })
    }

    /** 小孩已經有的貼紙不要一直排，先去還沒拿到的。 */
    @Test
    fun planPicksItemsWithoutStickerFirst() {
        val owned = allOwned - z("ㄇ") - a("C") - y("frog")
        repeat(30) { d ->
            val stations = JourneyRules.plan(today.plusDays(d.toLong()), pool, owned, emptySet())
            assertEquals(listOf(z("ㄇ"), a("C"), y("frog")), stations)
        }
    }

    /** 大人陪按過「再練」的符號，就算已經有貼紙，也排進注音站複習。 */
    @Test
    fun zhuyinStationAlsoPicksSymbolsMarkedAgain() {
        val owned = allOwned - z("ㄅ")
        val picked = (0 until 20).map { JourneyRules.plan(today.plusDays(it.toLong()), pool, owned, setOf("ㄈ")).first() }.toSet()
        assertEquals(setOf(z("ㄅ"), z("ㄈ")), picked)
    }

    /** 全部都拿到了，旅程照樣有站（從全部輪流），不會變空。 */
    @Test
    fun everythingOwnedStillGivesThreeStations() {
        val stations = JourneyRules.plan(today, pool, allOwned, emptySet())
        assertEquals(3, stations.size)
        assertTrue(stations.all { it in allOwned })
    }

    @Test
    fun missingContentSkipsThatStation() {
        val stations = JourneyRules.plan(today, JourneyPool(zhuyin, emptyList(), yoga), emptySet(), emptySet())
        assertEquals(listOf(StickerKind.ZHUYIN, StickerKind.YOGA), stations.map { it.kind })
    }

    /** 中途離開，同一天回來：同一份旅程，從那一站接著。拿到新貼紙也不會換站。 */
    @Test
    fun sameDayResumesFromTheSameStation() {
        val first = JourneyRules.today(null, today, pool, emptySet(), emptySet())
        val walked = JourneyRules.complete(first, 0)
        val back = JourneyRules.today(walked, today, pool, setOf(first.stations[0]), emptySet())
        assertEquals(walked, back)
        assertEquals(1, back.done)
    }

    /** 換日就排新的，從第 1 站開始；昨天走完的不影響今天。 */
    @Test
    fun nextDayStartsANewJourney() {
        val yesterday = JourneyRules.today(null, today, pool, emptySet(), emptySet())
        val finished = (0 until 3).fold(yesterday) { d, i -> JourneyRules.complete(d, i) }
        assertTrue(finished.finished)
        val next = JourneyRules.today(finished, today.plusDays(1), pool, finished.stations.toSet(), emptySet())
        assertEquals(today.plusDays(1), next.date)
        assertEquals(0, next.done)
        assertFalse(next.finished)
        // 昨天三站的貼紙都拿到了，今天換別的。
        assertTrue(next.stations.none { it in finished.stations })
    }

    /** 素材換過、存的站已經不在了：重排，不要卡在打不開的站。 */
    @Test
    fun savedStationNoLongerAvailableIsReplanned() {
        val saved = JourneyDay(today, listOf(z("ㄅ"), a("Z"), y("frog")), done = 1)
        val day = JourneyRules.today(saved, today, pool, emptySet(), emptySet())
        assertEquals(0, day.done)
        assertTrue(day.stations.all { it.key in zhuyin + abc + yoga })
    }

    /** 只有目前這站算數：同一站走完兩次、或跳站，都不會多算。 */
    @Test
    fun completeOnlyCountsTheCurrentStationOnce() {
        val day = JourneyDay(today, listOf(z("ㄅ"), a("A"), y("frog")))
        val one = JourneyRules.complete(day, 0)
        assertEquals(1, JourneyRules.complete(one, 0).done)
        assertEquals(1, JourneyRules.complete(one, 2).done)
        val done = JourneyRules.complete(JourneyRules.complete(one, 1), 2)
        assertTrue(done.finished)
        assertEquals(3, JourneyRules.complete(done, 3).done)
    }

    @Test
    fun stationsSurviveSaveAndLoad() {
        val stations = listOf(z("ㄅ"), a("A"), y("tree_left"))
        assertEquals(stations, JourneyRules.decodeStations(JourneyRules.encodeStations(stations)))
        assertEquals(emptyList<StickerId>(), JourneyRules.decodeStations(""))
    }
}
