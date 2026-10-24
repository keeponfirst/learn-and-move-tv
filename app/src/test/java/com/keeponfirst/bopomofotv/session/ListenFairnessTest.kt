package com.keeponfirst.bopomofotv.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** 照牌組順序的注音與 ABC（和 App 的 Decks 順序相同）。 */
internal val ZHUYIN = "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙㄧㄨㄩㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦ".map { it.toString() }
internal val ABC = ('A'..'Z').map { it.toString() }

/** 模擬一次聽聽看：和 ListenScreen 一樣，示範過的新卡加進看過的、數新卡題，上一題不重複。 */
internal fun playSession(pool: List<String>, seen: Set<String>, count: Int, random: Random): List<Pair<ListenQuestion, Set<String>>> {
    var known = seen
    var previous: String? = null
    var intros = 0
    return (0 until count).map { n ->
        val q = ListenRules.next(pool, known, previous, random, n, intros)!!
        val before = known
        if (q.intro) {
            known = known + q.target
            intros++
        }
        previous = q.target
        q to before
    }
}

/**
 * 陪玩時發現：聽聽看的答案常常是孩子眼熟的那張。
 * 原因是題目只出看過的、其他卡常是沒看過的，孩子選眼熟那張就對，不用聽。
 * 這裡鎖住：3 張卡要一樣眼熟，靠眼熟猜不會比亂猜好。
 */
class ListenFairnessTest {
    private val decks = listOf(ZHUYIN, ABC)

    @Test
    fun withThreeOrMoreSeenEveryOptionOfARegularQuestionIsSeen() {
        decks.forEach { deck ->
            listOf(3, 5, 15).forEach { n ->
                val seen = deck.take(n).toSet()
                playSession(deck, seen, 200, Random(n)).filter { !it.first.intro }.forEach { (q, known) ->
                    assertTrue("${q.options} 有沒看過的卡（看過 $known）", known.containsAll(q.options))
                }
            }
        }
    }

    /** 孩子不聽，只選「唯一眼熟的那張」（沒有或不只一張就亂選）。答對率不能明顯高於亂猜的 1/3。 */
    @Test
    fun pickingTheFamiliarCardIsNoBetterThanGuessing() {
        decks.forEach { deck ->
            (0..15).forEach { n ->
                val seen = deck.take(n).toSet()
                val r = Random(100 + n)
                val regular = playSession(deck, seen, 600, r).filter { !it.first.intro }
                val hits = regular.count { (q, known) ->
                    val familiar = q.options.filter { it in known }
                    val pick = if (familiar.size == 1) familiar.single() else q.options.random(r)
                    pick == q.target
                }
                val rate = hits.toDouble() / regular.size
                assertTrue("看過 $n 張：靠眼熟答對率 $rate", rate < 0.45)
            }
        }
    }

    /** 答案不能老是在同一個位置（焦點從中間開始，老在中間就是一按就對）。 */
    @Test
    fun answerPositionIsSpreadOverAllThreeSlots() {
        decks.forEach { deck ->
            listOf(1, 5).forEach { n ->
                val slots = playSession(deck, deck.take(n).toSet(), 900, Random(n)).groupingBy { (q, _) -> q.options.indexOf(q.target) }.eachCount()
                (0 until ListenRules.OPTIONS).forEach { assertTrue("看過 $n 張，位置 $it：$slots", (slots[it] ?: 0) in 220..380) }
            }
        }
    }

    @Test
    fun everyFifthQuestionIntroducesTheNextUnseenCardAmongSeenOnes() {
        decks.forEach { deck ->
            val seen = deck.take(5).toSet()
            // 只看前 10 題：第 3 張起受 MAX_INTROS 限制，另一個測試鎖。
            val session = playSession(deck, seen, 10, Random(7))
            session.forEachIndexed { i, (q, known) ->
                assertEquals("第 ${i + 1} 題", i % 5 == 4, q.intro)
                if (q.intro) {
                    assertEquals(deck.first { it !in known }, q.target)
                    assertTrue(known.containsAll(q.options - q.target))
                }
            }
        }
    }

    /**
     * 一次不會教太多新卡。玩滿 10 分鐘（約 70 題）也最多 2 張新卡，仍在第 5、10 題；
     * 之後的第 15、20… 題照一般題出，3 張都是看過的。
     */
    @Test
    fun oneVisitIntroducesAtMostTwoNewCards() {
        decks.forEach { deck ->
            listOf(3, 5, 15).forEach { n ->
                val session = playSession(deck, deck.take(n).toSet(), 70, Random(358 + n))
                val introAt = session.indices.filter { session[it].first.intro }
                assertEquals("${deck.first()} 牌組看過 $n 張", listOf(4, 9), introAt)
                session.drop(10).forEach { (q, known) -> assertTrue("${q.options} 有沒看過的卡", known.containsAll(q.options)) }
            }
        }
    }

    /** 新卡題示範後算看過的：之後它也會當其他卡，「選陌生那張」不會一直對。 */
    @Test
    fun anIntroducedCardLaterAppearsAsAWrongOption() {
        decks.forEach { deck ->
            val session = playSession(deck, deck.take(3).toSet(), 50, Random(8))
            val introduced = session.first { it.first.intro }.first.target
            assertTrue(session.any { (q, _) -> !q.intro && introduced in q.options && q.target != introduced })
        }
    }

    @Test
    fun noIntroWhenEverythingIsSeenOrFewerThanThreeSeen() {
        decks.forEach { deck ->
            assertTrue(playSession(deck, deck.toSet(), 20, Random(9)).none { it.first.intro })
            assertTrue(playSession(deck, deck.take(2).toSet(), 20, Random(9)).none { it.first.intro })
        }
    }

    /** 固定種子各 50 題的統計，寫到 build/reports/listen-sim.txt。 */
    @Test
    fun simulationReport() {
        val out = StringBuilder()
        listOf("注音" to ZHUYIN, "ABC" to ABC).forEach { (name, deck) ->
            listOf(2, 5, 15).forEach { n ->
                val seen = deck.take(n).toSet()
                val session = playSession(deck, seen, 50, Random(356))
                val targets = session.groupingBy { it.first.target }.eachCount().toList().sortedBy { deck.indexOf(it.first) }
                val unseenOptions = session.count { (q, known) -> !q.intro && !known.containsAll(q.options) }
                val unseenTargets = session.count { (q, known) -> !q.intro && q.target !in known }
                val intros = session.filter { it.first.intro }.map { it.first.target }
                out.appendLine("$name 看過 $n 張（${seen.joinToString("")}）：")
                out.appendLine("  題目分布：${targets.joinToString(" ") { "${it.first}=${it.second}" }}")
                out.appendLine("  一般題混到沒看過的選項：$unseenOptions 題；一般題題目沒看過：$unseenTargets 題")
                out.appendLine("  新卡題：${intros.size} 題（${intros.joinToString("")}）")
                out.appendLine("  結束時看過：${(seen + intros).size} 張")
            }
        }
        File("build/reports").mkdirs()
        File("build/reports/listen-sim.txt").writeText(out.toString())
    }
}
