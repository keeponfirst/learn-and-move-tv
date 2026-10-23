package com.keeponfirst.bopomofotv.session

import com.keeponfirst.bopomofotv.data.Card
import com.keeponfirst.bopomofotv.data.CardAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class ParentRulesTest {
    private fun card(unit: Int, order: Int, symbol: String) =
        Card(unit, order, symbol, "", "k", "", "", audio = CardAudio("s", "k"))

    private val cards = listOf(card(1, 1, "ㄅ"), card(1, 2, "ㄆ"), card(1, 3, "ㄇ"), card(2, 4, "ㄉ"), card(2, 5, "ㄊ"))
    private val units = cards.map { it.unit }
    private val random = Random(7)

    private fun ParentSession.right() = ParentRules.next(this, random).session

    // 1. 10 分鐘到：記住沒做完的卡，下次從那張接著做。
    @Test
    fun pausedSessionResumesFromUnfinishedCard() {
        val s = ParentRules.start(cards, ParentProgress()).right()
        val saved = ParentRules.paused(ParentProgress(), s)
        assertEquals(listOf("ㄆ", "ㄇ"), saved.resumeQueue)
        val resumed = ParentRules.start(cards, saved)
        assertEquals("ㄆ", resumed.current)
        assertEquals(Phase.NEW, resumed.phase)
    }

    // 2. 新卡做完 → 打亂進複習段，並提示播 p_practice_again。
    @Test
    fun lastNewCardLeadsToShuffledReviewWithPracticeAgainCue() {
        val s = ParentRules.start(cards, ParentProgress()).right().right()
        val step = ParentRules.next(s, random)
        assertEquals(Cue.PRACTICE_AGAIN, step.cue)
        assertEquals(Phase.REVIEW, step.session.phase)
        assertEquals(setOf("ㄅ", "ㄆ", "ㄇ"), step.session.queue.toSet())
        assertEquals(Cue.DONE, ParentRules.next(step.session.right().right(), random).cue)
    }

    // 3. 按「再練」：這張卡排到本輪最後，停在原卡重播。
    @Test
    fun againMovesCardToEndOfRound() {
        val step = ParentRules.markAgain(ParentRules.start(cards, ParentProgress()), random)
        assertNull(step.cue)
        assertEquals(listOf("ㄅ", "ㄆ", "ㄇ", "ㄅ"), step.session.queue)
        assertEquals("ㄅ", step.session.current)
    }

    // 3. 同一張卡第 2 次「再練」不再排，直接換卡。
    @Test
    fun secondAgainSkipsToNextCardWithoutRequeue() {
        val once = ParentRules.markAgain(ParentRules.start(cards, ParentProgress()), random).session
        val twice = ParentRules.markAgain(once, random)
        assertEquals(Cue.NEXT, twice.cue)
        assertEquals(listOf("ㄅ", "ㄆ", "ㄇ", "ㄅ"), twice.session.queue)
        assertEquals("ㄆ", twice.session.current)
    }

    // 4. 連續 2 次每張卡第一次就答對 → 下次進下一單元。
    @Test
    fun twoPerfectSessionsAdvanceToNextUnit() {
        val perfect = ParentRules.start(cards, ParentProgress()).let { s ->
            s.copy(firstTry = s.unitSymbols.associateWith { true })
        }
        val afterOne = ParentRules.completed(ParentProgress(), perfect, units)
        assertEquals(1, afterOne.unit)
        assertEquals(2, ParentRules.completed(afterOne, perfect, units).unit)
    }

    // 4. 沒過就重練；練 4 次還沒過就先進下一單元。
    @Test
    fun fourAttemptsWithoutPassAdvanceAnyway() {
        val imperfect = ParentRules.markAgain(ParentRules.start(cards, ParentProgress()), random).session
        var progress = ParentProgress()
        repeat(3) { progress = ParentRules.completed(progress, imperfect, units) }
        assertEquals(1, progress.unit)
        assertEquals(2, ParentRules.completed(progress, imperfect, units).unit)
    }

    @Test
    fun lastUnitWrapsToFirst() {
        assertEquals(1, ParentRules.nextUnit(units, 2))
    }
}
