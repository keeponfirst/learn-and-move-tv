package com.keeponfirst.bopomofotv.session

import com.keeponfirst.bopomofotv.record.StickerId
import com.keeponfirst.bopomofotv.record.StickerKind
import java.time.LocalDate

/**
 * 某一天的小旅程：依序 [stations] 幾站（注音、字母、瑜伽各 1 站），已完成 [done] 站。
 * 同一天回來從第 [done] 站接著；換日就排新的。
 */
data class JourneyDay(val date: LocalDate, val stations: List<StickerId>, val done: Int = 0) {
    val finished: Boolean get() = done >= stations.size
}

/** 每類可以排進旅程的項目，照貼紙冊的順序。某類是空的，旅程就少那一站。 */
data class JourneyPool(val zhuyin: List<String>, val abc: List<String>, val yoga: List<String>)

/** 小旅程規則。全部是純函式，日期由呼叫端傳入。 */
object JourneyRules {
    /**
     * 排 [date] 這天的站：1 個注音、1 個字母、1 個瑜伽動作。
     * 注音優先從「還沒拿到貼紙」或「大人陪按過再練（[again]）」的符號裡選；字母、瑜伽優先選還沒拿到貼紙的。
     * 優先清單是空的（全拿到了）就從全部選。同一天結果固定：用日期輪流取，每天換一個。
     */
    fun plan(date: LocalDate, pool: JourneyPool, owned: Set<StickerId>, again: Set<String>): List<StickerId> {
        fun pick(kind: StickerKind, keys: List<String>, priority: (String) -> Boolean): StickerId? {
            if (keys.isEmpty()) return null
            val candidates = keys.filter(priority).ifEmpty { keys }
            return StickerId(kind, candidates[date.toEpochDay().mod(candidates.size)])
        }
        fun unowned(kind: StickerKind) = { key: String -> StickerId(kind, key) !in owned }
        return listOfNotNull(
            pick(StickerKind.ZHUYIN, pool.zhuyin) { unowned(StickerKind.ZHUYIN)(it) || it in again },
            pick(StickerKind.ABC, pool.abc, unowned(StickerKind.ABC)),
            pick(StickerKind.YOGA, pool.yoga, unowned(StickerKind.YOGA)),
        )
    }

    /**
     * 今天的旅程：存的是今天、而且每站都還在 [pool] 裡，就照存的接著走；
     * 否則（沒存過、跨日、素材換過）排新的，從第 1 站開始。
     */
    fun today(saved: JourneyDay?, date: LocalDate, pool: JourneyPool, owned: Set<StickerId>, again: Set<String>): JourneyDay {
        if (saved != null && saved.date == date && saved.stations.isNotEmpty() && saved.stations.all { inPool(it, pool) }) return saved
        return JourneyDay(date, plan(date, pool, owned, again))
    }

    /** 走完第 [index] 站（0 起算）。只有目前這站算數，重複呼叫不會多算。 */
    fun complete(day: JourneyDay, index: Int): JourneyDay =
        if (index == day.done && !day.finished) day.copy(done = day.done + 1) else day

    /** 存檔用：各站的 [StickerId.encode] 用逗號接起來。 */
    fun encodeStations(stations: List<StickerId>): String = stations.joinToString(STATION_SEPARATOR) { it.encode() }

    fun decodeStations(text: String): List<StickerId> =
        text.split(STATION_SEPARATOR).filter { it.isNotEmpty() }.mapNotNull { StickerId.decode(it) }

    private fun inPool(id: StickerId, pool: JourneyPool): Boolean = when (id.kind) {
        StickerKind.ZHUYIN -> id.key in pool.zhuyin
        StickerKind.ABC -> id.key in pool.abc
        StickerKind.YOGA -> id.key in pool.yoga
    }

    private const val STATION_SEPARATOR = ","
}
