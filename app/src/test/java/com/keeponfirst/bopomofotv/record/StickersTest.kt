package com.keeponfirst.bopomofotv.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 貼紙：同一項只給一次；看完一個符號的所有卡才給。 */
class StickersTest {
    private val p = StickerId(StickerKind.ZHUYIN, "ㄆ")

    @Test
    fun sameStickerIsGivenOnlyOnce() {
        val once = StickerRules.award(emptySet(), p)
        assertEquals(setOf("z|ㄆ"), once)
        assertNull("第二次不再給", StickerRules.award(once!!, p))
    }

    /** 不同類的同名項目是不同貼紙（例：注音和瑜伽各自計）。 */
    @Test
    fun kindsAreCountedSeparately() {
        val owned = StickerRules.award(emptySet(), StickerId(StickerKind.ABC, "A"))!!
        assertEquals(2, StickerRules.award(owned, StickerId(StickerKind.YOGA, "A"))!!.size)
    }

    @Test
    fun idSurvivesSaveAndLoad() {
        listOf(p, StickerId(StickerKind.ABC, "Q"), StickerId(StickerKind.YOGA, "tree_left")).forEach {
            assertEquals(it, StickerId.decode(it.encode()))
        }
        assertNull(StickerId.decode("x|ㄅ"))
        assertNull(StickerId.decode("z|"))
        assertNull(StickerId.decode("garbage"))
    }

    /** 自己玩看完 ㄆ 的 3 張卡才給 ㄆ；只看 2 張不給；之後再看不重複回報。 */
    @Test
    fun symbolIsDoneAfterAllItsCardsWereSeen() {
        val seen = SeenTracker()
        assertFalse(seen.see("ㄆ", 0, 3))
        assertFalse(seen.see("ㄆ", 1, 3))
        assertFalse("同一張看兩次不算兩張", seen.see("ㄆ", 1, 3))
        assertTrue(seen.see("ㄆ", 2, 3))
        assertFalse(seen.see("ㄆ", 0, 3))
        assertFalse(seen.see("ㄆ", 2, 3))
    }

    @Test
    fun eachSymbolIsTrackedOnItsOwn() {
        val seen = SeenTracker()
        seen.see("ㄅ", 0, 2)
        assertFalse(seen.see("ㄆ", 1, 2))
        assertTrue(seen.see("ㄅ", 1, 2))
        // 只有 1 個例詞的符號，看一張就算看完。
        assertTrue(seen.see("ㄦ", 0, 1))
    }
}
