package com.keeponfirst.bopomofotv.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 聽音找符號：從「看」進到「聽了會認」。
 * 題目要先出孩子看過的（沒看過的符號聽了也不會認），一題要有一張對的、其他張不重複。
 */
class ListenRulesTest {
    private val pool = "ㄅㄆㄇㄈㄉㄊㄋㄌ".map { it.toString() }

    @Test
    fun everyQuestionHasTheAnswerAmongDistinctCards() {
        val r = Random(1)
        repeat(200) {
            val q = ListenRules.next(pool, emptySet(), null, r)!!
            assertEquals(ListenRules.OPTIONS, q.options.size)
            assertEquals(q.options.size, q.options.toSet().size)
            assertTrue(q.target in q.options)
        }
    }

    @Test
    fun targetComesFromSeenSymbolsFirst() {
        val seen = setOf("ㄅ", "ㄆ", "ㄇ")
        val r = Random(2)
        repeat(200) { assertTrue(ListenRules.next(pool, seen, null, r)!!.target in seen) }
    }

    @Test
    fun sameSymbolIsNotAskedTwiceInARow() {
        val r = Random(3)
        var previous: String? = null
        repeat(200) {
            val q = ListenRules.next(pool, setOf("ㄅ", "ㄆ"), previous, r)!!
            assertNotEquals(previous, q.target)
            previous = q.target
        }
    }

    @Test
    fun onlyOneSeenSymbolAndItWasJustAskedThenAskAnother() {
        // 只看過 ㄅ、上一題剛問 ㄅ：不要卡在 ㄅ，從其他的選。
        val q = ListenRules.next(pool, setOf("ㄅ"), "ㄅ", Random(4))!!
        assertNotEquals("ㄅ", q.target)
    }

    @Test
    fun dayOneStillWorksWithCardsFromTheFrontOfTheDeck() {
        // 只看過 ㄅ：3 張都從前 6 張挑，題目不一定是 ㄅ（不然選眼熟的 ㄅ 就對）。
        val front = pool.take(ListenRules.FRONT).toSet()
        val r = Random(5)
        val targets = (1..200).map {
            val q = ListenRules.next(pool, setOf("ㄅ"), null, r)!!
            assertEquals(3, q.options.size)
            assertTrue(front.containsAll(q.options))
            q.target
        }
        assertTrue(targets.toSet().size > 1)
    }

    @Test
    fun smallPoolStillWorksAndTooSmallReturnsNull() {
        val q = ListenRules.next(listOf("A", "B"), emptySet(), null, Random(6))!!
        assertEquals(setOf("A", "B"), q.options.toSet())
        assertNull(ListenRules.next(listOf("A"), emptySet(), null, Random(6)))
        assertNull(ListenRules.next(emptyList(), emptySet(), null, Random(6)))
    }

    @Test
    fun seenIsEverythingUpToLastCardPlusStickersAndPractice() {
        val seen = ListenRules.seen(pool, savedKey = "ㄇ", stickers = setOf("ㄌ"), practiced = setOf("ㄉ"))
        assertEquals(setOf("ㄅ", "ㄆ", "ㄇ", "ㄌ", "ㄉ"), seen)
    }

    @Test
    fun seenWithNoSavedPositionIsOnlyStickersAndPractice() {
        assertEquals(setOf("ㄌ"), ListenRules.seen(pool, savedKey = null, stickers = setOf("ㄌ")))
        assertEquals(emptySet<String>(), ListenRules.seen(pool, savedKey = "不在裡面", stickers = emptySet()))
    }
}
