package com.keeponfirst.bopomofotv.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 一筆：[d] 是 SVG 路徑的中心線，方向＝書寫方向；[width] 是筆寬（viewBox 單位）。 */
@Serializable
data class Stroke(val d: String, val width: Float = 80f)

/**
 * 一個注音符號的筆順（本專案原創，MIT；筆順參照教育部公告標準）。[strokes] 的順序就是筆順。
 * [viewBox] 是正方形，例如 `0 0 1024 1024`。
 */
@Serializable
data class StrokeSymbol(
    val symbol: String,
    val viewBox: String = "0 0 1024 1024",
    val strokes: List<Stroke>,
) {
    /** 畫布邊長（viewBox 的寬）。讀不到用 1024。 */
    val size: Float get() = viewBox.trim().split(Regex("\\s+")).getOrNull(2)?.toFloatOrNull() ?: 1024f
}

object StrokeParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** 壞掉、沒有筆畫或有空路徑回 null。 */
    fun parse(text: String): StrokeSymbol? =
        runCatching { json.decodeFromString<StrokeSymbol>(text) }.getOrNull()
            ?.takeIf { s -> s.symbol.isNotBlank() && s.strokes.isNotEmpty() && s.strokes.all { it.d.isNotBlank() && it.width > 0f } }
}

/** 筆順動畫的時間（毫秒）：每筆 0.6～0.9 秒、筆間停 0.2 秒。 */
object StrokeTiming {
    const val MIN_MS = 600
    const val MAX_MS = 900
    const val GAP_MS = 200L

    /** 一筆畫多久：越長越久。長度 ≥ 畫布邊長 1.2 倍就是 [MAX_MS]，最短 [MIN_MS]。 */
    fun strokeMillis(length: Float, canvas: Float): Int =
        (MIN_MS + (MAX_MS - MIN_MS) * (length / canvas / 1.2f)).toInt().coerceIn(MIN_MS, MAX_MS)
}

/** 從 assets/zhuyin/strokes/ 讀全部筆順，key 是符號。資料夾不在回空表，畫面就不提供筆順。 */
object StrokeRepository {
    const val ASSET_DIR = "zhuyin/strokes"

    fun load(context: Context): Map<String, StrokeSymbol> {
        val names = runCatching { context.assets.list(ASSET_DIR) }.getOrNull().orEmpty().filter { it.endsWith(".json") }
        return names.mapNotNull { name ->
            runCatching { context.assets.open("$ASSET_DIR/$name").bufferedReader(Charsets.UTF_8).use { StrokeParser.parse(it.readText()) } }
                .getOrNull()
                .also { if (it == null) Log.i("StrokeRepository", "讀不到筆順 $name") }
        }.associateBy { it.symbol }
    }
}
