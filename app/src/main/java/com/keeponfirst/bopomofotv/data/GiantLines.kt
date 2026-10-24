package com.keeponfirst.bopomofotv.data

import android.content.Context
import android.util.Log

/**
 * 巨人小螞蟻的台詞（畫面字幕＋系統語音的文字）。
 * assets/giant/lines.tsv 有的就用它，沒有的用 [DEFAULTS]。
 */
object GiantLines {
    const val PATH = "giant/lines.tsv"

    /** lines.tsv 讀不到時用。內容同 lines.tsv（2026-10-24，23 句）。台詞沒有「跳」。 */
    val DEFAULTS = linkedMapOf(
        "ga_intro_1" to "我們來玩巨人小螞蟻！請大人陪在旁邊。",
        "ga_intro_2" to "站在電視前面，往後退兩大步。",
        "ga_intro_3" to "兩手打開，左邊右邊都碰不到東西，也碰不到別人。",
        "ga_safe_slow" to "蹲下、站起來都慢慢來，腳不要離開地板喔。",
        "ga_rule_giant" to "聽到「變巨人」，就踮起腳，手伸高高。",
        "ga_rule_ant" to "聽到「變小螞蟻」，就蹲下來，抱住膝蓋。",
        "ga_rule_easy" to "踮不起來也沒關係，腳踩平、手伸高高就可以。",
        "ga_rule_fake" to "如果不是巨人，也不是小螞蟻，就停住不要動！",
        "ga_practice" to "我們先練習一次。",
        "ga_go" to "準備好了嗎？開始！",
        "ga_giant" to "變巨人！",
        "ga_ant" to "變小螞蟻！",
        "ga_fake_elephant" to "變大象！",
        "ga_fake_bird" to "變小鳥！",
        "ga_fake_watermelon" to "變大西瓜！",
        "ga_fake_fish" to "變小魚！",
        "ga_fake_after" to "嘻嘻，不是巨人，也不是小螞蟻。你有停住嗎？",
        "ga_faster" to "要變快囉！",
        "ga_fastest" to "最快的來囉！",
        "ga_end_1" to "巨人蹲下來，跟小螞蟻擊掌！",
        "ga_end_2" to "慢慢站起來，手放下來。慢慢吸氣，慢慢吐氣。",
        "ga_end_3" to "你聽得好仔細！還想玩，請大人按 OK。",
        "ga_rest" to "玩了好多次，休息一下，去喝口水吧！",
    )

    /** 解析 lines.tsv：每行「檔名<TAB>台詞」。檔名可帶 .m4a。空行、# 開頭、不是 ga_ 開頭的列都略過。解析出來的蓋過 [DEFAULTS]。 */
    fun parse(text: String): Map<String, String> = DEFAULTS + text.lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val cols = line.split('\t')
            val id = cols[0].trim().removeSuffix(".m4a")
            val words = cols.getOrNull(1)?.trim().orEmpty()
            if (id.startsWith("ga_") && words.isNotEmpty()) id to words else null
        }
        .toMap()

    /**
     * 沒有音檔（改用系統語音）時估計一句要念多久：每個字 280 毫秒，加 600 毫秒起頭。
     * 音檔約每字 230～270 毫秒（例 ga_intro_3 23 字 6.6 秒），估長一點比較不會疊播。
     */
    fun estimateMillis(text: String): Long = 600L + 280L * text.count { !it.isWhitespace() }

    fun load(context: Context): Map<String, String> = runCatching {
        context.assets.open(PATH).bufferedReader().use { parse(it.readText()) }
    }.onFailure { Log.i("GiantLines", "沒有 $PATH，用內建台詞") }.getOrDefault(DEFAULTS)
}
