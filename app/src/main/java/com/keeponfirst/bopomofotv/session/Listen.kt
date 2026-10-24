package com.keeponfirst.bopomofotv.session

import kotlin.random.Random

/**
 * 聽音找符號的一題：播 [target] 的音，畫面放 [options] 這幾張卡（含 [target]），順序已打亂。
 * [intro]＝新卡題：[target] 是孩子還沒看過的，出題前先示範它的音。
 */
data class ListenQuestion(val target: String, val options: List<String>, val intro: Boolean = false)

/**
 * 聽音找符號的規則。全部是純函式，亂數由呼叫端傳入。
 *
 * 3 張卡要一樣眼熟：原本題目只出看過的、其他卡從全部挑，孩子選「眼熟那張」就對，不用聽。
 */
object ListenRules {
    /** 一題放幾張卡。4～5 歲 3 張剛好。 */
    const val OPTIONS = 3

    /** 看過的不到 [OPTIONS] 個時，題目和其他卡都從牌組前幾張挑。 */
    const val FRONT = 6

    /** 每幾題插 1 題新卡題（第 5、10、15… 題）。 */
    const val INTRO_EVERY = 5

    /** 每次進聽聽看最多教幾張新卡。每 5 題 1 張，10 分鐘會教到 10～14 張，對 4～5 歲太快。 */
    const val MAX_INTROS = 2

    /**
     * 出第 [number] 題（從 0 起算）。[pool] 照牌組順序；[previous] 是上一題的題目，不連續出同一個。
     * - 看過的（[seen]）有 3 個以上：題目和其他卡全部從看過的挑。
     * - 看過的不到 3 個：題目和其他卡全部從牌組前 [FRONT] 張挑，3 張一樣陌生或一樣眼熟。
     * - 看過的有 3 個以上，而且是第 [INTRO_EVERY] 的倍數題：出新卡題。題目是牌組裡第一張沒看過的，其他卡從看過的挑。
     *   新卡題示範後，呼叫端要把它加進 [seen]，之後它也會當其他卡，不會一直是「唯一陌生那張」。
     *   [intros]＝這次已出幾題新卡題；到 [MAX_INTROS] 就不再出，照一般題出。
     * [pool] 不到 2 個回 null（沒辦法選）。
     */
    fun next(pool: List<String>, seen: Set<String>, previous: String?, random: Random, number: Int = 0, intros: Int = 0): ListenQuestion? {
        if (pool.size < 2) return null
        val known = pool.filter { it in seen }
        if (known.size < OPTIONS) return pick(pool.take(FRONT), previous, random)
        if (intros < MAX_INTROS && number % INTRO_EVERY == INTRO_EVERY - 1) {
            val fresh = pool.firstOrNull { it !in seen }
            if (fresh != null) return question(fresh, known, random, intro = true)
        }
        return pick(known, previous, random)
    }

    /** 從 [cards] 挑題目（不跟 [previous] 一樣），其他卡也從 [cards] 挑。 */
    private fun pick(cards: List<String>, previous: String?, random: Random): ListenQuestion =
        question(cards.filter { it != previous }.ifEmpty { cards }.random(random), cards, random)

    private fun question(target: String, from: List<String>, random: Random, intro: Boolean = false): ListenQuestion {
        val others = from.filter { it != target }.shuffled(random).take(OPTIONS - 1)
        return ListenQuestion(target, (others + target).shuffled(random), intro)
    }

    /**
     * 孩子看過的：自己玩／ABC 從頭翻到上次那張（[deckKeys] 照卡片順序、[savedKey] 是上次停的符號或字母，含它），
     * 加上已有貼紙的（[stickers]）與大人陪練過的（[practiced]）。
     */
    fun seen(deckKeys: List<String>, savedKey: String?, stickers: Set<String>, practiced: Set<String> = emptySet()): Set<String> {
        val upTo = savedKey?.let { deckKeys.indexOf(it) } ?: -1
        return deckKeys.take(upTo + 1).toSet() + stickers + practiced
    }
}
