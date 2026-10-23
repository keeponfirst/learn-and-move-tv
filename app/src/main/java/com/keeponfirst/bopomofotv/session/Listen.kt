package com.keeponfirst.bopomofotv.session

import kotlin.random.Random

/** 聽音找符號的一題：播 [target] 的音，畫面放 [options] 這幾張卡（含 [target]），順序已打亂。 */
data class ListenQuestion(val target: String, val options: List<String>)

/** 聽音找符號的規則。全部是純函式，亂數由呼叫端傳入。 */
object ListenRules {
    /** 一題放幾張卡。4～5 歲 3 張剛好。 */
    const val OPTIONS = 3

    /**
     * 出下一題。題目（[ListenQuestion.target]）優先從孩子看過的（[seen]）裡選，而且不跟上一題（[previous]）一樣；
     * 看過的不夠就從全部選。其他卡從全部（不含題目）隨機選。[pool] 不到 2 個回 null（沒辦法選）。
     */
    fun next(pool: List<String>, seen: Set<String>, previous: String?, random: Random): ListenQuestion? {
        if (pool.size < 2) return null
        val fresh = pool.filter { it != previous }.ifEmpty { pool }
        val candidates = fresh.filter { it in seen }.ifEmpty { fresh }
        val target = candidates.random(random)
        val others = pool.filter { it != target }.shuffled(random).take(OPTIONS - 1)
        return ListenQuestion(target, (others + target).shuffled(random))
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
