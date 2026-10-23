package com.keeponfirst.bopomofotv.ui

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/**
 * 主題素材的快取。背景圖每頁都用，每次換頁都重新解碼會卡一下。
 * 只快取 theme/ 底下的圖；換主題時清掉，不會同時留三套。
 */
internal object ThemeImages {
    private val cache = HashMap<String, ImageBitmap>()
    private var themeDir: String? = null

    fun get(context: android.content.Context, path: String): ImageBitmap? {
        val dir = path.split('/').take(2).joinToString("/")
        if (dir != themeDir) {
            cache.clear()
            themeDir = dir
        }
        cache[path]?.let { return it }
        return runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }
            .onFailure { Log.i("ThemeImages", "沒有圖 $path") }
            .getOrNull()
            ?.also { cache[path] = it }
    }
}

/** 讀主題素材圖（有快取）。檔案不在回 null，畫面照樣能用。 */
@Composable
internal fun rememberThemeImage(path: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(path) { ThemeImages.get(context, path) }
}

/** 整頁背景：主題底色＋背景圖（裁切填滿）。 */
@Composable
fun ThemeBackdrop(path: String, modifier: Modifier = Modifier) {
    val image = rememberThemeImage(path)
    Box(modifier.fillMaxSize().background(Background)) {
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/**
 * 有外框或發光的字。B 的入口標題、符號大字有深色描邊；C 的標題有黃色光暈。
 * 描邊做法：同一段字先畫一次粗線條（顏色＝[stroke]），再畫填色蓋上去。
 */
@Composable
fun DecoratedText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    stroke: Color? = null,
    strokeWidth: Dp = 0.dp,
    glow: Color? = null,
    glowRadius: Dp = 0.dp,
    weight: FontWeight = FontWeight.Black,
    textAlign: TextAlign? = null,
) {
    val density = LocalDensity.current
    val base = TextStyle(fontSize = fontSize, fontFamily = NotoSansTc, fontWeight = weight, textAlign = textAlign ?: TextAlign.Unspecified)
    Box(modifier, contentAlignment = Alignment.Center) {
        // 發光：用幾層半透明的粗線條疊出柔邊。文字陰影（Shadow）的模糊會被字框裁成方塊，不能用。
        if (glow != null && glowRadius > 0.dp) {
            listOf(1f to 0.18f, 0.6f to 0.28f, 0.3f to 0.4f).forEach { (k, a) ->
                val px = with(density) { (glowRadius * 2 * k).toPx() }
                Text(text, textModifier, style = base.copy(color = glow.copy(alpha = glow.alpha * a), drawStyle = Stroke(width = px, join = StrokeJoin.Round)))
            }
        }
        if (stroke != null && strokeWidth > 0.dp) {
            // 線條一半在字外、一半被填色蓋住，所以寬度乘 2。
            val px = with(density) { (strokeWidth * 2).toPx() }
            Text(text, textModifier, style = base.copy(color = stroke, drawStyle = Stroke(width = px, join = StrokeJoin.Round)))
        }
        Text(text, textModifier, style = base.copy(color = color))
    }
}

/** 頁面頂部的資訊膠囊（模式、進度）。B 是深色膠囊；A、C 不畫底，只放字。 */
@Composable
fun InfoPill(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val theme = LocalAppTheme.current
    Box(
        modifier
            .background(theme.hud, RoundedCornerShape(50))
            .padding(horizontal = if (theme.hud.alpha > 0f) Spacing.S else 0.dp, vertical = Spacing.XS / 2),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** 給大人看的按鍵提示（各頁右下或底部）。B 用深色膠囊白字，A、C 用次要字色。 */
@Composable
fun KeyHint(text: String, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    InfoPill(modifier) { Text(text, fontSize = TextSize.KeyHint, color = if (theme.id == ThemeId.B) theme.hudText else SubtleColor) }
}
