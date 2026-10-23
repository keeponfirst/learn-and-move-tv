package com.keeponfirst.bopomofotv.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 一張 ABC 卡。欄位照 abc/cards.json；路徑都相對 assets/。 */
@Serializable
data class AbcCard(
    val order: Int,
    val upper: String,
    val lower: String,
    val word: String,
    val zh: String,
    val image: String,
    val audioLetter: String,
    val audioWord: String,
)

object AbcParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** 解析 abc/cards.json，依 order 排序。 */
    fun parse(text: String): List<AbcCard> =
        json.decodeFromString<List<AbcCard>>(text).sortedBy { it.order }
}

/** 從 assets/abc/cards.json 載入。檔案不在或壞掉回空清單，首頁 ABC 鍵就不進卡片頁。 */
object AbcRepository {
    const val ASSET_PATH = "abc/cards.json"

    fun load(context: Context): List<AbcCard> = runCatching {
        context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { AbcParser.parse(it.readText()) }
    }.getOrElse { emptyList() }
}
