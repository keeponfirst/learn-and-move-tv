package com.keeponfirst.bopomofotv.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 一張注音卡。欄位照 cards.json；handbookAudioUrl 不用，解析時忽略。 */
@Serializable
data class Card(
    val unit: Int,
    val order: Int,
    val symbol: String,
    val name: String,
    val keyword: String,
    val keywordZhuyin: String,
    val parentPrompt: String,
    val confusableWith: List<String> = emptyList(),
    val audio: CardAudio,
)

@Serializable
data class CardAudio(
    val symbol: String,
    val keyword: String,
)

object CardParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** 解析 cards.json，依 order 排序。 */
    fun parse(text: String): List<Card> =
        json.decodeFromString<List<Card>>(text).sortedBy { it.order }
}
