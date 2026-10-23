package com.keeponfirst.bopomofotv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 驗 App 實際打包的 assets/cards.json，確保卡片內容完整進到 App。 */
class CardParserTest {
    private val cards = CardParser.parse(File("src/main/assets/cards.json").readText(Charsets.UTF_8))

    @Test
    fun has37Cards() {
        assertEquals(37, cards.size)
    }

    @Test
    fun has10Units() {
        assertEquals((1..10).toSet(), cards.map { it.unit }.toSet())
    }

    @Test
    fun everyCardHasSymbolAndKeyword() {
        cards.forEach {
            assertTrue("order ${it.order} 缺 symbol", it.symbol.isNotBlank())
            assertTrue("order ${it.order} 缺 keyword", it.keyword.isNotBlank())
        }
    }

    @Test
    fun symbolsAreUniqueSoAudioLookupIsUnambiguous() {
        assertEquals(37, cards.map { it.symbol }.toSet().size)
    }

    @Test
    fun cardsAreSortedByOrder() {
        assertEquals((1..37).toList(), cards.map { it.order })
    }
}
