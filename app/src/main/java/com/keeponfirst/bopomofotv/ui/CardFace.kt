package com.keeponfirst.bopomofotv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.keeponfirst.bopomofotv.data.Card
import com.keeponfirst.bopomofotv.data.ZhuyinWord

/**
 * 卡片畫面：符號大字、例詞、例詞注音小字。
 * 例詞有圖：左圖右字（同 ABC 卡），圖是可用高度 70% 的正方形。沒圖：三行整組置中。
 * 符號字級＝畫面高度的 50%，ㄅ 的字形實高約佔畫面 43%（字形約 0.86 個字級高）。
 * 上下各留 48 dp。1080p 電視（960×540 dp）上三行合計約 390 dp，可用高度 444 dp。
 * 有圖時寬度：圖 311 dp＋間距 64 dp，右邊剩約 489 dp，放得下 270 dp 的符號與 56 sp 的四字詞。
 */
@Composable
fun CardFace(
    card: Card,
    word: ZhuyinWord = ZhuyinWord(card.keyword, card.keywordZhuyin),
    topLeft: String? = null,
    topRight: String? = null,
    bottomRight: String? = null,
) {
    val image = word.image?.let { rememberAssetImage(it) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val symbolBox = maxHeight * 0.5f
        val imageBox = (maxHeight - 96.dp) * 0.7f
        if (image != null) {
            Row(
                Modifier.fillMaxSize().padding(48.dp),
                horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(image, word.word, Modifier.size(imageBox), contentScale = ContentScale.Fit)
                SymbolAndWord(card, word, symbolBox)
            }
        } else {
            Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                SymbolAndWord(card, word, symbolBox)
            }
        }
        Box(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
            topLeft?.let { Text(it, Modifier.align(Alignment.TopStart), fontSize = 20.sp, color = SubtleColor) }
            topRight?.let { Text(it, Modifier.align(Alignment.TopEnd), fontSize = 20.sp, color = SubtleColor) }
            bottomRight?.let { Text(it, Modifier.align(Alignment.BottomEnd), fontSize = 20.sp, color = SubtleColor) }
        }
    }
}

@Composable
private fun SymbolAndWord(card: Card, word: ZhuyinWord, symbolBox: Dp) {
    val symbolSize = with(LocalDensity.current) { symbolBox.toSp() }
    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        // 中文字型上下自帶留白，符號的文字框約 1.5 倍字級高，會把下方的字擠出畫面
        // （模擬器實測，lineHeight 改不掉）。所以版面只給一個字級高的框，
        // 字在框內置中，留白畫到框外，不佔版面。
        Box(Modifier.height(symbolBox), contentAlignment = Alignment.Center) {
            Text(
                card.symbol,
                Modifier.wrapContentHeight(unbounded = true),
                fontSize = symbolSize,
                color = SymbolColor,
            )
        }
        Text(word.word, fontSize = 56.sp, color = TextColor)
        Text(word.zhuyin, fontSize = 28.sp, color = SubtleColor)
    }
}
