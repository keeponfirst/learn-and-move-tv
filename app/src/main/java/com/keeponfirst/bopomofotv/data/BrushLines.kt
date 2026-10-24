package com.keeponfirst.bopomofotv.data

import android.content.Context
import android.util.Log
import com.keeponfirst.bopomofotv.session.BrushLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 刷牙的台詞（畫面字幕＋系統語音的文字）。
 * assets/brush/lines.tsv 有的就用它，沒有的用 [DEFAULTS]。
 * 鼓勵句的句數照 lines.tsv：br_cheer_1、br_cheer_2…連號，斷號就停。
 */
object BrushLines {
    const val PATH = "brush/lines.tsv"

    /** lines.tsv 讀不到時用。內容同 lines.tsv（2026-10-24）。不叫孩子吞牙膏；「大人再刷一遍」在完成畫面。 */
    val DEFAULTS = linkedMapOf(
        "br_intro_1" to "細菌小怪躲在牙齒上，我們一起把它們趕走！",
        "br_intro_2" to "拿好牙刷，請大人幫你擠一點點牙膏。",
        BrushLine.GO to "準備好了嗎？開始刷囉！",
        BrushLine.zone(0) to "刷上面左邊的牙齒，把小怪趕走！",
        BrushLine.zone(1) to "刷上面的門牙，把小怪趕走！",
        BrushLine.zone(2) to "刷上面右邊的牙齒，把小怪趕走！",
        BrushLine.zone(3) to "刷下面左邊的牙齒，把小怪趕走！",
        BrushLine.zone(4) to "刷下面的門牙，把小怪趕走！",
        BrushLine.zone(5) to "刷下面右邊的牙齒，把小怪趕走！",
        BrushLine.cheer(0) to "刷刷刷，小怪快跑掉了！",
        BrushLine.cheer(1) to "好厲害，再刷一下下！",
        BrushLine.NEXT to "啵！小怪飛走了，換下一個地方。",
        BrushLine.DONE to "好乾淨！給你一張貼紙。",
        BrushLine.ADULT to "最後請大人再幫你刷一遍喔。",
        BrushLine.RINSE to "把泡泡吐掉就好，不要吞下去喔！",
    )

    /**
     * 解析 lines.tsv：每行「檔名<TAB>台詞」。檔名可帶 .m4a。空行、# 開頭、標題列（檔名不是 br_ 開頭）都略過。
     * 解析出來的蓋過 [DEFAULTS]。
     */
    fun parse(text: String): Map<String, String> {
        val parsed = text.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val cols = line.split('\t')
                val id = cols[0].trim().removeSuffix(".m4a")
                val words = cols.getOrNull(1)?.trim().orEmpty()
                if (id.startsWith("br_") && words.isNotEmpty()) id to words else null
            }
            .toMap()
        // 鼓勵句以 lines.tsv 為準：它只有 2 句就輪 2 句，不混進內建的句子。
        val base = if (parsed.keys.any { it.startsWith(BrushLine.CHEER_PREFIX) }) DEFAULTS.filterKeys { !it.startsWith(BrushLine.CHEER_PREFIX) } else DEFAULTS
        return base + parsed
    }

    /** 鼓勵句有幾句：從 br_cheer_1 起連號算。 */
    fun cheerCount(lines: Map<String, String>): Int {
        var n = 0
        while (BrushLine.cheer(n) in lines) n++
        return n
    }

    fun load(context: Context): Map<String, String> = runCatching {
        context.assets.open(PATH).bufferedReader().use { parse(it.readText()) }
    }.onFailure { Log.i("BrushLines", "沒有 $PATH，用內建台詞") }.getOrDefault(DEFAULTS)
}

/**
 * 牙齒圖（teeth_base.png）上 6 區的位置，座標是圖的像素（brush/zones.json）。
 * [box] 是 [左, 上, 右, 下]；[germs] 是小怪中心與大小倍率（小怪圖 [germCanvas] px 見方）；[arrow] 是「刷這裡」箭頭尖端。
 */
@Serializable
data class BrushZones(
    val canvas: List<Int>,
    @kotlinx.serialization.SerialName("germ_canvas") val germCanvas: Int,
    val zones: Map<String, Zone>,
) {
    @Serializable
    data class Zone(val box: List<Int>, val germs: List<Germ>, val arrow: Arrow)

    @Serializable
    data class Germ(val x: Float, val y: Float, val scale: Float = 1f)

    @Serializable
    data class Arrow(val x: Float, val y: Float, val dir: String)

    companion object {
        const val PATH = "brush/zones.json"
        private val json = Json { ignoreUnknownKeys = true }

        /** 解析失敗、或 6 區缺任何一區就回 null（畫面改用程式畫的牙齒）。 */
        fun parse(text: String, codes: List<String>): BrushZones? = runCatching { json.decodeFromString<BrushZones>(text) }
            .getOrNull()
            ?.takeIf { z -> z.canvas.size == 2 && codes.all { it in z.zones } && z.zones.values.all { it.box.size == 4 } }

        fun load(context: Context, codes: List<String>): BrushZones? = runCatching {
            context.assets.open(PATH).bufferedReader().use { parse(it.readText(), codes) }
        }.onFailure { Log.i("BrushZones", "沒有 $PATH，用程式畫的牙齒") }.getOrNull()
    }
}
