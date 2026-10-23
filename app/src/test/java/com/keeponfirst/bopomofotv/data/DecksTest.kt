package com.keeponfirst.bopomofotv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DecksTest {
    private val cards = CardParser.parse(File("src/main/assets/cards.json").readText(Charsets.UTF_8))

    private fun card(symbol: String) = cards.first { it.symbol == symbol }

    private val words = WordsParser.parseZhuyin(
        """
        [{"symbol":"ㄅ","words":[
          {"word":"爸爸","zhuyin":"ㄅㄚˋ ˙ㄅㄚ","image":"zhuyin/img/baba.png","audio":"zhuyin/audio/baba.m4a","imagePrompt":"x"},
          {"word":"杯子","zhuyin":"ㄅㄟ ˙ㄗ","image":"zhuyin/img/beizi.png","audio":"zhuyin/audio/beizi.m4a"},
          {"word":"包子","zhuyin":"ㄅㄠ ˙ㄗ","image":"zhuyin/img/baozi.png","audio":"zhuyin/audio/baozi.m4a"}]}]
        """,
    )

    @Test
    fun withoutWordFileSelfPlayWalksAll37InMoeOrder() {
        val deck = Decks.zhuyin(cards, emptyMap())
        assertEquals(37, deck.size)
        assertEquals(
            "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦㄧㄨㄩ",
            deck.joinToString("") { it.card.symbol },
        )
        // 沒有例詞檔：用 cards.json 的關鍵字，沒有圖。
        assertEquals("爸爸", deck.first().word.word)
        assertEquals(null, deck.first().word.image)
    }

    @Test
    fun eachSymbolShowsAllItsWordsBeforeTheNextSymbol() {
        val deck = Decks.zhuyin(cards, words)
        assertEquals(listOf("ㄅ" to "爸爸", "ㄅ" to "杯子", "ㄅ" to "包子", "ㄆ" to "葡萄"), deck.take(4).map { it.card.symbol to it.word.word })
        assertEquals(39, deck.size)
    }

    @Test
    fun resumeAtSavedCardOrSameSymbolOrStart() {
        val deck = Decks.zhuyin(cards, words)
        val pos = { c: ZhuyinCard -> Decks.position(c) }
        assertEquals(2, Decks.resumeIndex(deck, DeckPosition("ㄅ", 2), pos))
        assertEquals(3, Decks.resumeIndex(deck, DeckPosition("ㄆ", 0), pos))
        // 例詞檔換過、詞數變少：同一個符號從第 1 個詞開始。
        assertEquals(3, Decks.resumeIndex(deck, DeckPosition("ㄆ", 2), pos))
        assertEquals(0, Decks.resumeIndex(deck, DeckPosition("X", 0), pos))
        assertEquals(0, Decks.resumeIndex(deck, null, pos))
    }

    @Test
    fun parentModeRotatesWordsOnEachAppearance() {
        val list = Decks.zhuyinWords(card("ㄅ"), words)
        assertEquals(listOf("爸爸", "杯子", "包子", "爸爸"), (0..3).map { Decks.rotate(list, it).word })
        // 符號沒有例詞：每次都是關鍵字。
        assertEquals("葡萄", Decks.rotate(Decks.zhuyinWords(card("ㄆ"), words), 5).word)
    }

    @Test
    fun abcLettersWithoutWordsKeepCardsJsonWordAndImage() {
        val a = AbcCard(1, "A", "a", "apple", "蘋果", "abc/img/a_apple.png", "abc/audio/l_a.m4a", "abc/audio/w_apple.m4a")
        val b = AbcCard(2, "B", "b", "ball", "球", "abc/img/b_ball.png", "abc/audio/l_b.m4a", "abc/audio/w_ball.m4a")
        val abcWords = WordsParser.parseAbc(
            """[{"upper":"A","words":[{"word":"apple","zh":"蘋果","image":"abc/img/a_apple.png","audio":"abc/audio/w_apple.m4a"},
                {"word":"ant","zh":"螞蟻","image":"abc/img/a_ant.png","audio":"abc/audio/w_ant.m4a"}]}]""",
        )
        val deck = Decks.abc(listOf(a, b), abcWords)
        assertEquals(listOf("apple", "ant", "ball"), deck.map { it.word.word })
        assertEquals("abc/img/b_ball.png", deck.last().word.image)
    }

    @Test
    fun bundledWordFilesParseWhenPresent() {
        // 素材到了才檢查：每個符號／字母都有詞，第 1 個是 cards.json 的關鍵字。
        File("src/main/assets/words-zhuyin.json").takeIf { it.exists() }?.let { f ->
            val parsed = WordsParser.parseZhuyin(f.readText(Charsets.UTF_8))
            cards.forEach { c -> assertEquals(c.keyword, parsed[c.symbol]?.firstOrNull()?.word) }
        }
        File("src/main/assets/words-abc.json").takeIf { it.exists() }?.let { f ->
            assertTrue(WordsParser.parseAbc(f.readText(Charsets.UTF_8)).isNotEmpty())
        }
    }
}
