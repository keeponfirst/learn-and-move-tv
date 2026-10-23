package com.keeponfirst.bopomofotv.session

/** 可以「接著玩」的模式。[key] 存進 DataStore，改了舊紀錄會讀不到，不要改。 */
enum class ResumeMode(val key: String) {
    SELF("self"),
    ABC("abc"),
    YOGA("yoga"),
    JOURNEY("journey"),
}

/**
 * 上次離開的位置。[savedAt] 是存的時間（牆上時鐘毫秒）。
 * 自己玩、ABC 的卡片位置另外存（PracticeStore.position），小旅程的站另外存（JourneyDay）；這裡只記「上次在哪個模式」。
 * 瑜伽要記是哪一套（[yogaSet]）與第幾個動作（[yogaStep]，跨輪攤平，0 起算）。
 */
data class ResumePoint(val mode: ResumeMode, val savedAt: Long, val yogaSet: String? = null, val yogaStep: Int = 0)

/** 從上次繼續的規則。全部是純函式，時間由呼叫端傳入。 */
object ResumeRules {
    /** 超過 1 天就不顯示「接著玩」。 */
    const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    /** 首頁要不要顯示：存過、沒超過 1 天。時間倒退（電視改過時鐘）也不顯示。 */
    fun shown(point: ResumePoint?, now: Long): ResumePoint? =
        point?.takeIf { now - it.savedAt in 0..MAX_AGE_MS }

    /** 存檔字串：`模式|時間|套|第幾個`，例 `yoga|1761200000000|animal|3`。 */
    fun encode(p: ResumePoint): String = listOf(p.mode.key, p.savedAt.toString(), p.yogaSet.orEmpty(), p.yogaStep.toString()).joinToString(SEPARATOR)

    /** 認不得的字串回 null，不丟例外。 */
    fun decode(text: String?): ResumePoint? {
        val parts = text?.split(SEPARATOR) ?: return null
        if (parts.size != 4) return null
        val mode = ResumeMode.entries.firstOrNull { it.key == parts[0] } ?: return null
        val at = parts[1].toLongOrNull() ?: return null
        return ResumePoint(mode, at, parts[2].ifEmpty { null }, parts[3].toIntOrNull() ?: 0)
    }

    private const val SEPARATOR = "|"
}
