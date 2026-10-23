package com.keeponfirst.bopomofotv.data

import android.content.Context
import com.keeponfirst.bopomofotv.audio.AudioResolver
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 注音例詞。欄位照 words-zhuyin.json；image、audio 相對 assets/。 */
@Serializable
data class ZhuyinWord(val word: String, val zhuyin: String, val image: String? = null, val audio: String? = null)

@Serializable
private data class ZhuyinWordSet(val symbol: String, val words: List<ZhuyinWord> = emptyList())

/** ABC 例詞。欄位照 words-abc.json。 */
@Serializable
data class AbcWord(val word: String, val zh: String, val image: String? = null, val audio: String? = null)

@Serializable
private data class AbcWordSet(val upper: String, val words: List<AbcWord> = emptyList())

object WordsParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** 符號 → 例詞（照檔案裡的順序）。 */
    fun parseZhuyin(text: String): Map<String, List<ZhuyinWord>> =
        json.decodeFromString<List<ZhuyinWordSet>>(text).associate { it.symbol to it.words }

    /** 大寫字母 → 例詞。 */
    fun parseAbc(text: String): Map<String, List<AbcWord>> =
        json.decodeFromString<List<AbcWordSet>>(text).associate { it.upper to it.words }
}

/** 從 assets 載入例詞。檔案不在或壞掉回空表，每個符號／字母就只用 cards.json 的 1 個詞。 */
object WordsRepository {
    const val ZHUYIN_PATH = "words-zhuyin.json"
    const val ABC_PATH = "words-abc.json"

    fun loadZhuyin(context: Context): Map<String, List<ZhuyinWord>> =
        read(context, ZHUYIN_PATH)?.let { runCatching { WordsParser.parseZhuyin(it) }.getOrNull() }.orEmpty()

    fun loadAbc(context: Context): Map<String, List<AbcWord>> =
        read(context, ABC_PATH)?.let { runCatching { WordsParser.parseAbc(it) }.getOrNull() }.orEmpty()

    private fun read(context: Context, path: String): String? =
        runCatching { context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
}

/** 一張注音卡＝一個符號＋它的第 [wordIndex] 個例詞。 */
data class ZhuyinCard(val card: Card, val word: ZhuyinWord, val wordIndex: Int)

/** 一張 ABC 卡＝一個字母＋它的第 [wordIndex] 個例詞。 */
data class AbcWordCard(val card: AbcCard, val word: AbcWord, val wordIndex: Int)

/** 自己玩／ABC 上次停的那張：符號（或大寫字母）＋第幾個詞。 */
data class DeckPosition(val key: String, val wordIndex: Int)

/** 把卡片和例詞排成一疊：每個符號／字母的詞連續出完，再換下一個。 */
object Decks {
    /** 這個符號的例詞。例詞檔沒有它，就用 cards.json 的關鍵字（沒有圖）。 */
    fun zhuyinWords(card: Card, words: Map<String, List<ZhuyinWord>>): List<ZhuyinWord> =
        words[card.symbol].orEmpty().ifEmpty { listOf(ZhuyinWord(card.keyword, card.keywordZhuyin)) }

    /** 這個字母的例詞。例詞檔沒有它，就用 abc/cards.json 的單字與圖。 */
    fun abcWords(card: AbcCard, words: Map<String, List<AbcWord>>): List<AbcWord> =
        words[card.upper].orEmpty().ifEmpty { listOf(AbcWord(card.word, card.zh, card.image, card.audioWord)) }

    /** 大人陪：第 [n] 次出現用第 n 個詞，用完從頭輪。 */
    fun <T> rotate(words: List<T>, n: Int): T = words[n.mod(words.size)]

    /** 37 個符號照教育部順序 ㄅ→ㄩ（不看 cards.json 的 order）。 */
    fun zhuyin(cards: List<Card>, words: Map<String, List<ZhuyinWord>>): List<ZhuyinCard> =
        cards.sortedBy { moeIndex(it.symbol) }
            .flatMap { card -> zhuyinWords(card, words).mapIndexed { i, w -> ZhuyinCard(card, w, i) } }

    /** 26 個字母照 abc/cards.json 的 order。 */
    fun abc(cards: List<AbcCard>, words: Map<String, List<AbcWord>>): List<AbcWordCard> =
        cards.flatMap { card -> abcWords(card, words).mapIndexed { i, w -> AbcWordCard(card, w, i) } }

    /**
     * 接著上次的位置。找不到同一個詞（例詞數變了）就從同一個符號的第 1 個詞開始；
     * 符號也找不到或沒存過，從頭開始。
     */
    fun <T> resumeIndex(deck: List<T>, saved: DeckPosition?, positionOf: (T) -> DeckPosition): Int {
        if (saved == null) return 0
        val positions = deck.map(positionOf)
        return positions.indexOf(saved).takeIf { it >= 0 }
            ?: positions.indexOfFirst { it.key == saved.key }.takeIf { it >= 0 }
            ?: 0
    }

    fun position(c: ZhuyinCard) = DeckPosition(c.card.symbol, c.wordIndex)

    fun position(c: AbcWordCard) = DeckPosition(c.card.upper, c.wordIndex)

    private fun moeIndex(symbol: String): Int =
        AudioResolver.MOE_ORDER.indexOf(symbol).let { if (it < 0) Int.MAX_VALUE else it }
}
