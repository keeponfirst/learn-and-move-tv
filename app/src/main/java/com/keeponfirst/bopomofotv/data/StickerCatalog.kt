package com.keeponfirst.bopomofotv.data

import com.keeponfirst.bopomofotv.record.StickerId
import com.keeponfirst.bopomofotv.record.StickerKind
import com.keeponfirst.bopomofotv.session.JourneyPool

/** 貼紙冊的一格。[label] 是符號、大寫字母或動作中文名；[art] 是插圖（assets 路徑），沒有圖就只畫字。 */
data class StickerItem(val id: StickerId, val label: String, val art: String?)

/**
 * 全部貼紙，照類分開。貼紙不另外畫：沿用卡片的例詞圖與瑜伽示範圖（美術規格）。
 * - 注音：37 個符號照教育部順序，圖用第一個有圖的例詞。
 * - ABC：照 abc/cards.json 的順序，圖用字母卡的圖。
 * - 瑜伽：所有套裡出現過的動作，照第一次出現的順序，同一個 id 只列一次。
 */
class StickerCatalog(val byKind: Map<StickerKind, List<StickerItem>>) {
    private val byId = byKind.values.flatten().associateBy { it.id }

    operator fun get(id: StickerId): StickerItem? = byId[id]

    fun items(kind: StickerKind): List<StickerItem> = byKind[kind].orEmpty()

    /** 小旅程可以排的項目。 */
    fun journeyPool() = JourneyPool(
        zhuyin = items(StickerKind.ZHUYIN).map { it.id.key },
        abc = items(StickerKind.ABC).map { it.id.key },
        yoga = items(StickerKind.YOGA).map { it.id.key },
    )

    companion object {
        fun build(zhuyin: List<ZhuyinCard>, abc: List<AbcWordCard>, yoga: List<YogaChoice>): StickerCatalog {
            val z = zhuyin.groupBy { it.card.symbol }.map { (symbol, cards) ->
                StickerItem(StickerId(StickerKind.ZHUYIN, symbol), symbol, cards.firstNotNullOfOrNull { it.word.image })
            }
            val a = abc.groupBy { it.card.upper }.map { (upper, cards) ->
                StickerItem(StickerId(StickerKind.ABC, upper), upper, cards.first().card.image)
            }
            val y = yoga.flatMap { it.program.poses }.distinctBy { it.id }.map {
                StickerItem(StickerId(StickerKind.YOGA, it.id), it.nameZh, it.image)
            }
            return StickerCatalog(mapOf(StickerKind.ZHUYIN to z, StickerKind.ABC to a, StickerKind.YOGA to y))
        }
    }
}
