package com.keeponfirst.bopomofotv.data

import android.content.Context

/** 從 assets/cards.json 載入卡片。 */
object CardRepository {
    const val ASSET_PATH = "cards.json"

    fun load(context: Context): List<Card> =
        context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { CardParser.parse(it.readText()) }
}
