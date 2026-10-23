package com.keeponfirst.bopomofotv.ui

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import com.keeponfirst.bopomofotv.data.AbcCard
import com.keeponfirst.bopomofotv.data.AbcWord

/**
 * ABC 卡內容（放在 [CardPage] 的大卡裡）：左邊插圖，右邊大寫＋小寫大字、英文字、中文小字。
 * 插圖是大卡高度 82% 的方框。圖不在或解不開，圖位改放大寫字母（字級同框高 60%）。
 */
@Composable
fun AbcCardFace(card: AbcCard, word: AbcWord = AbcWord(card.word, card.zh, card.image, card.audioWord)) {
    val image = word.image?.let { rememberAssetImage(it) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val imageBox = minOf(maxHeight * 0.82f, maxWidth * 0.42f)
        val letterSize = with(LocalDensity.current) { (maxHeight * 0.38f).toSp() }
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (image != null) {
                CardImage(image, word.word, imageBox)
            } else {
                Box(Modifier.size(imageBox), contentAlignment = Alignment.Center) {
                    SymbolText(card.upper, with(LocalDensity.current) { (imageBox * 0.6f).toSp() })
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SymbolText("${card.upper} ${card.lower}", letterSize)
                Text(word.word, fontSize = TextSize.Word, color = TextColor, fontWeight = FontWeight.Black)
                Text(word.zh, fontSize = TextSize.Body, color = SubtleColor)
            }
        }
    }
}

/** 讀 assets 裡的圖。檔案不在或壞掉回 null，不丟例外。瑜伽頁也用。 */
@Composable
internal fun rememberAssetImage(path: String): ImageBitmap? {
    val assets = LocalContext.current.assets
    return remember(path) {
        runCatching { assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }
            .onFailure { Log.i("AbcCardFace", "沒有圖 $path") }
            .getOrNull()
    }
}
