package com.keeponfirst.bopomofotv.audio

import com.keeponfirst.bopomofotv.data.AbcCard
import com.keeponfirst.bopomofotv.data.AbcWord
import com.keeponfirst.bopomofotv.data.Card
import com.keeponfirst.bopomofotv.data.ZhuyinWord

/** 6 段固定語句。檔名固定，家長自錄時用同一個檔名覆蓋。 */
enum class Phrase(val fileName: String) {
    CORRECT("p_correct.m4a"),
    LISTEN_AGAIN("p_listen_again.m4a"),
    NEXT("p_next.m4a"),
    PRACTICE_AGAIN("p_practice_again.m4a"), // 大人陪：新卡做完，進複習段
    REST("p_rest.m4a"), // 大人陪：10 分鐘到，卡還沒做完
    DONE("p_done.m4a"),
}

/**
 * 決定每段聲音要播哪個 asset 檔。
 *
 * - 符號音：家長自錄音檔 → 教育部手冊開放部件 → 靜音。
 * - 關鍵字音、固定語句：家長自錄音檔 → 內建配音（`audio/`）→ 靜音。
 * - ABC：cards.json 寫的路徑，不在就靜音。
 *
 * 家長自錄音檔放在 `audio/custom/`，檔名和內建配音相同，先找它。
 * [available] 是 assets 裡實際存在的路徑，例如 `audio/s_01_b.m4a`。
 */
class AudioResolver(private val available: Set<String>) {

    fun symbolPaths(card: Card): List<String> =
        listOfNotNull(custom(card.audio.symbol) ?: moe(card.symbol))

    /**
     * 例詞音。[word] 是 cards.json 的關鍵字（或 null）時，先找自錄／內建配音，再找例詞檔的 audio；
     * 其他例詞只用例詞檔的 audio。都不在就靜音。
     */
    fun keywordPaths(card: Card, word: ZhuyinWord? = null): List<String> {
        val keyword = if (word == null || word.word == card.keyword) custom(card.audio.keyword) else null
        return listOfNotNull(keyword ?: word?.audio?.takeIf { it in available })
    }

    /** 符號音（教育部）接著例詞音。 */
    fun cardPaths(card: Card, word: ZhuyinWord? = null): List<String> = symbolPaths(card) + keywordPaths(card, word)

    fun phrasePaths(phrase: Phrase): List<String> = listOfNotNull(custom(phrase.fileName))

    /** ABC 進卡：只播字母音。 */
    fun letterPaths(card: AbcCard): List<String> = listOf(card.audioLetter).filter { it in available }

    /** ABC 確定鍵：字母音接著單字音。例詞檔的 audio 不在、而且是 cards.json 的單字時，用 cards.json 的音。 */
    fun letterWordPaths(card: AbcCard, word: AbcWord = AbcWord(card.word, card.zh, card.image, card.audioWord)): List<String> {
        val wordAudio = listOfNotNull(word.audio, card.audioWord.takeIf { word.word == card.word })
            .firstOrNull { it in available }
        return letterPaths(card) + listOfNotNull(wordAudio)
    }

    private fun custom(fileName: String): String? =
        CUSTOM_AUDIO_DIRS.map { "$it/$fileName" }.firstOrNull { it in available }

    private fun moe(symbol: String): String? = moeFileFor(symbol)?.let { "$MOE_DIR/$it" }?.takeIf { it in available }

    companion object {
        const val AUDIO_DIR = "audio"
        const val MOE_DIR = "audio/moe"
        const val ABC_AUDIO_DIR = "abc/audio"
        const val ZHUYIN_AUDIO_DIR = "zhuyin/audio" // 注音第 2、3 個例詞的音
        val CUSTOM_AUDIO_DIRS = listOf("audio/custom", AUDIO_DIR)

        /**
         * 教育部手冊開放部件 F1.WAV…F37.WAV 對應的符號，照標準序 ㄅ…ㄙ、ㄚ…ㄦ、ㄧㄨㄩ。
         * 用 symbol 對應，不用 cards.json 的 order。
         */
        const val MOE_ORDER = "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦㄧㄨㄩ"

        fun moeFileFor(symbol: String): String? {
            if (symbol.length != 1) return null
            val index = MOE_ORDER.indexOf(symbol[0])
            return if (index < 0) null else "F${index + 1}.WAV"
        }
    }
}
