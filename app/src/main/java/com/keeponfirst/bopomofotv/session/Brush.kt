package com.keeponfirst.bopomofotv.session

/** 刷牙分 6 區，順序：上左、上前、上右、下左、下前、下右。代號同 zones.json 與音檔名。 */
val BRUSH_ZONE_CODES = listOf("ul", "uf", "ur", "ll", "lf", "lr")
val BRUSH_ZONES = BRUSH_ZONE_CODES.size

/** 每區 20 秒，6 區共 2 分鐘。模擬器截圖可用 debug 參數縮短（見 MainActivity），正式就是這個值。 */
const val BRUSH_ZONE_MILLIS = 20_000L

/** 6 區的名字，順序同 [BRUSH_ZONE_CODES]。畫面的進度格用。 */
val BRUSH_ZONE_NAMES = listOf("上左", "上前", "上右", "下左", "下前", "下右")

/**
 * 刷牙台詞的 id，也是音檔檔名（brush/audio/<id>.m4a）和 lines.tsv 第一欄。
 * 鼓勵句號從 1 起算。
 */
object BrushLine {
    /** 開場畫面播的兩句。 */
    val INTRO = listOf("br_intro_1", "br_intro_2")
    /** 按 OK 後「準備好了嗎？開始刷囉！」。 */
    const val GO = "br_intro_3"
    const val NEXT = "br_next"
    const val DONE = "br_done"
    const val ADULT = "br_adult"
    const val RINSE = "br_rinse"
    const val CHEER_PREFIX = "br_cheer_"
    fun zone(z: Int) = "br_zone_${BRUSH_ZONE_CODES[z]}"
    fun cheer(k: Int) = "$CHEER_PREFIX${k + 1}"
}

/**
 * 時間到 [atMillis]（從按 OK 開始算）要依序播的台詞。
 * [optional]＝別句還在播就跳過這次（鼓勵句）；其他的排在後面播，不疊播。
 */
data class BrushEvent(val atMillis: Long, val lines: List<String>, val optional: Boolean = false)

/**
 * 某一刻的畫面狀態。[zone] 是目前這區（0 起算），做完是 [BRUSH_ZONES]。
 * [zoneElapsed] 是這區過了多久；[remainingSeconds] 是總倒數（無條件進位，剛開始是 120）。
 */
data class BrushState(val zone: Int, val zoneElapsed: Long, val remainingSeconds: Int, val done: Boolean)

/** 刷牙時間表。全部是純函式，畫面只負責計時、播音與呼叫。 */
object BrushRules {
    fun totalMillis(zoneMillis: Long = BRUSH_ZONE_MILLIS) = zoneMillis * BRUSH_ZONES

    /**
     * 整段時間表，依時間排好：
     * - 0 秒播「開始刷囉」＋第 1 區口令。
     * - 第 2 區起，每區開頭先播換區提示（上一區的小怪剛被打跑），再播那區口令。
     * - 每區中段播一句鼓勵，[cheers] 句輪流用；別句還在播就跳過。沒有鼓勵句就不播。
     * - 最後播完成、請大人再刷一遍、吐掉泡泡。
     */
    fun schedule(cheers: Int, zoneMillis: Long = BRUSH_ZONE_MILLIS): List<BrushEvent> {
        require(zoneMillis > 0)
        return buildList {
            for (z in 0 until BRUSH_ZONES) {
                val start = z * zoneMillis
                add(BrushEvent(start, listOf(if (z == 0) BrushLine.GO else BrushLine.NEXT, BrushLine.zone(z))))
                if (cheers > 0) add(BrushEvent(start + zoneMillis / 2, listOf(BrushLine.cheer(z % cheers)), optional = true))
            }
            add(BrushEvent(totalMillis(zoneMillis), listOf(BrushLine.DONE, BrushLine.ADULT, BrushLine.RINSE)))
        }
    }

    /**
     * 從第 [next] 個事件起，到 [elapsed] 為止該播的事件。回傳（要播的事件, 下一次從第幾個查）。
     * 一次跳過好幾個（例如畫面卡住）只播最後一個，不讓語音疊成一串。沒有到時間的回 null。
     */
    fun due(schedule: List<BrushEvent>, next: Int, elapsed: Long): Pair<BrushEvent?, Int> {
        var i = next
        while (i < schedule.size && schedule[i].atMillis <= elapsed) i++
        return (if (i > next) schedule[i - 1] else null) to i
    }

    fun state(elapsed: Long, zoneMillis: Long = BRUSH_ZONE_MILLIS): BrushState {
        val total = totalMillis(zoneMillis)
        val e = elapsed.coerceIn(0, total)
        val done = e >= total
        val zone = if (done) BRUSH_ZONES else (e / zoneMillis).toInt()
        val remaining = ((total - e + 999) / 1000).toInt()
        return BrushState(zone, if (done) 0 else e - zone * zoneMillis, remaining, done)
    }
}
