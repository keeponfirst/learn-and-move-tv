package com.keeponfirst.bopomofotv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.keeponfirst.bopomofotv.data.Card
import com.keeponfirst.bopomofotv.data.ZhuyinWord

/**
 * 卡片內容（放在 [CardPage] 的大卡裡）：符號大字、例詞、例詞注音。
 * 例詞有圖：左圖右字，圖是大卡高度 82% 的方框（框的樣子照主題）。沒圖：三行整組置中。
 * 符號字級＝大卡高度的 55%。大人陪大卡約 320 dp 高：符號框 176 dp＋例詞 48 sp（約 70 dp）＋注音（約 38 dp）＝284 dp。
 */
@Composable
fun CardFace(card: Card, word: ZhuyinWord = ZhuyinWord(card.keyword, card.keywordZhuyin)) {
    val image = word.image?.let { rememberAssetImage(it) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val symbolBox = maxHeight * 0.55f
        val imageBox = minOf(maxHeight * 0.82f, maxWidth * 0.42f)
        if (image != null) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardImage(image, word.word, imageBox)
                SymbolAndWord(card, word, symbolBox)
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SymbolAndWord(card, word, symbolBox)
            }
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
            SymbolText(card.symbol, symbolSize)
        }
        Text(word.word, fontSize = 48.sp, color = TextColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
        Text(word.zhuyin, fontSize = 26.sp, color = SubtleColor)
    }
}

/** 符號大字：B 紅字深框、C 金字發光、A 磚紅字。 */
@Composable
internal fun SymbolText(text: String, size: androidx.compose.ui.unit.TextUnit) {
    val theme = LocalAppTheme.current
    val unbounded = Modifier.wrapContentHeight(unbounded = true)
    when (theme.id) {
        ThemeId.B -> DecoratedText(text, size, theme.symbol, textModifier = unbounded, stroke = theme.outline, strokeWidth = 4.dp)
        ThemeId.C -> DecoratedText(text, size, theme.symbol, textModifier = unbounded, glow = androidx.compose.ui.graphics.Color(0xB2FFD65A), glowRadius = 3.dp)
        ThemeId.A -> DecoratedText(text, size, theme.symbol, textModifier = unbounded)
    }
}
