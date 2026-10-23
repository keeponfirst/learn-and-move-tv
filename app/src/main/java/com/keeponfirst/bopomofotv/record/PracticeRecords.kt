package com.keeponfirst.bopomofotv.record

import java.time.LocalDate

/** 大人陪模式的兩種紀錄。 */
enum class PracticeResult(val key: String) {
    CORRECT("correct"), // 上鍵：答對
    AGAIN("again"), // 下鍵：再練
}

/** 一個符號在某一天的紀錄。 */
data class PracticeEntry(val date: LocalDate, val symbol: String, val result: PracticeResult, val count: Int)

/**
 * 紀錄的 key 格式：`日期|符號|結果`，值是當天次數。
 * 例：`2026-10-12|ㄅ|correct` = 3。這樣同時保有每張卡的次數與日期。
 */
object PracticeRecords {
    private const val SEPARATOR = "|"

    fun key(date: LocalDate, symbol: String, result: PracticeResult): String =
        listOf(date.toString(), symbol, result.key).joinToString(SEPARATOR)

    fun parse(key: String, count: Int): PracticeEntry? {
        val parts = key.split(SEPARATOR)
        if (parts.size != 3) return null
        val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
        val result = PracticeResult.entries.firstOrNull { it.key == parts[2] } ?: return null
        return PracticeEntry(date, parts[1], result, count)
    }
}
