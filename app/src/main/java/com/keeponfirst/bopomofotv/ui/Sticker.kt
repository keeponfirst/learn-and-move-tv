package com.keeponfirst.bopomofotv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.keeponfirst.bopomofotv.data.StickerItem
import com.keeponfirst.bopomofotv.record.StickerKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/*
 * 貼紙：照美術規格在執行時疊 4 層，不另外產圖。
 * 畫布 512（設計 px），由下到上：sticker_base → 插圖（窗口內、圓角）→ sticker_top → 符號章／名稱牌。
 * 還沒收集：sticker_empty ＋ 淡色符號（注音、ABC）或灰階 28% 的動作影子（瑜伽）。
 */

/** 一套主題的貼紙與小旅程數值（美術規格）。座標單位是貼紙畫布 512。 */
internal class StickerStyle(
    val windowAt: Float,
    val windowSize: Float,
    val windowRadius: Float,
    val badgeBorder: Color,
    val badgeText: Color,
    val emptySymbol: Color,
    /** 站點色：注音、ABC、瑜伽。符號章、名稱牌、站牌圖示都用這組。 */
    val stationColors: List<Color>,
    val pillText: Color,
)

internal fun stickerStyle(theme: AppTheme) = when (theme.id) {
    ThemeId.A -> StickerStyle(
        65f, 382f, 31f, Color(0xFF4A3B35), Color.White, Color(0x294A3B35),
        listOf(Color(0xFFD9583B), Color(0xFFFFC531), Color(0xFF9C86D8)), Color.White,
    )
    ThemeId.B -> StickerStyle(
        73f, 366f, 31f, Color(0xFF2D2250), Color.White, Color(0x292D2250),
        listOf(Color(0xFFFF6B5B), Color(0xFFFFB61E), Color(0xFF8A5CF6)), Color.White,
    )
    ThemeId.C -> StickerStyle(
        56f, 400f, 48f, Color.White, Color(0xFF2A1F55), Color(0x33C9C6FF),
        listOf(Color(0xFFFF8A7A), Color(0xFFFFD66B), Color(0xFFB79CFF)), Color(0xFF2A1F55),
    )
}

/** 主題資料夾名（theme/a、theme/b、theme/c）。 */
internal fun themeDir(theme: AppTheme) = when (theme.id) {
    ThemeId.A -> "a"
    ThemeId.B -> "b"
    ThemeId.C -> "c"
}

/**
 * 貼紙用的插圖：裁成正方形並縮到 [SIZE] px，放快取。
 * - 注音、ABC（768×768）：固定裁 (32,32)–(736,736)，貼紙裡東西的比例跟卡片一樣。
 * - 瑜伽（1280×720）：找出跟底色 #FFF8EC 差超過 24 的範圍，邊長＝較長邊＋96，限制在 560～720，以內容為中心裁。
 */
internal object StickerArt {
    private const val SIZE = 240
    private val cache = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun cached(path: String): ImageBitmap? = cache.get(path)

    fun load(context: Context, path: String, kind: StickerKind): ImageBitmap? {
        cache.get(path)?.let { return it }
        val src = runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }
            .onFailure { Log.i("StickerArt", "沒有圖 $path") }
            .getOrNull() ?: return null
        val (x, y, side) = if (kind == StickerKind.YOGA) contentSquare(src) else fixedSquare(src)
        val square = Bitmap.createBitmap(src, x, y, side, side)
        val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        return scaled.asImageBitmap().also { cache.put(path, it) }
    }

    private fun fixedSquare(src: Bitmap): Triple<Int, Int, Int> {
        val side0 = minOf(src.width, src.height)
        val inset = side0 * 32 / 768
        return Triple((src.width - side0) / 2 + inset, (src.height - side0) / 2 + inset, side0 - inset * 2)
    }

    private fun contentSquare(src: Bitmap): Triple<Int, Int, Int> {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (j in 0 until h) for (i in 0 until w) {
            val c = px[j * w + i]
            val d = max(abs((c shr 16 and 0xFF) - 0xFF), max(abs((c shr 8 and 0xFF) - 0xF8), abs((c and 0xFF) - 0xEC)))
            if (d > 24) {
                if (i < minX) minX = i
                if (i > maxX) maxX = i
                if (j < minY) minY = j
                if (j > maxY) maxY = j
            }
        }
        val k = h / 720f
        if (maxX < 0) return Triple((w - h) / 2, 0, h)
        val side = (max(maxX - minX, maxY - minY) + (96 * k).toInt()).coerceIn((560 * k).toInt(), minOf((720 * k).toInt(), w, h))
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        return Triple((cx - side / 2).coerceIn(0, w - side), (cy - side / 2).coerceIn(0, h - side), side)
    }
}

/**
 * 在背景執行緒讀貼紙插圖；讀好之前先畫空的窗口。
 * 翻頁時同一格會換成別的貼紙：produceState 換 key 後會留著上一張的值，所以每次都先換成這張的快取（或 null）。
 */
@Composable
internal fun rememberStickerArt(item: StickerItem): ImageBitmap? {
    val context = LocalContext.current
    val path = item.art ?: return null
    val art by produceState(StickerArt.cached(path), path) {
        value = StickerArt.cached(path) ?: withContext(Dispatchers.IO) { StickerArt.load(context, path, item.id.kind) }
    }
    return art
}

/**
 * 設計稿座標的畫框：裡面一律當 960×540 dp 排版，再等比縮放到畫面大小、置中。
 * 小旅程地圖與貼紙冊的站牌、格子要對準背景圖，畫面比例不同（例如模擬器有狀態列）也不會跑掉。
 */
@Composable
internal fun DesignFrame(background: String? = null, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val u = minOf(maxWidth / 960.dp, maxHeight / 540.dp)
        Box(
            Modifier
                .requiredSize(960.dp, 540.dp)
                .graphicsLayer {
                    scaleX = u
                    scaleY = u
                },
        ) {
            background?.let { path -> rememberThemeImage(path)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) } }
            content()
        }
    }
}

private val GRAYSCALE = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * 一張貼紙，畫布邊長 [size]（含白邊與陰影的外圍）。[owned]＝已收集；沒收集就畫灰影。
 * 放進格子時，畫布比格子大 217/190 倍、往左上多出 13.6/190（美術規格）。
 */
@Composable
fun StickerView(item: StickerItem, owned: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val style = stickerStyle(theme)
    val dir = themeDir(theme)
    val art = rememberStickerArt(item)
    val u = size / 512f
    val color = style.stationColors[item.id.kind.ordinal]
    val density = LocalDensity.current
    Box(modifier.size(size)) {
        val window = Modifier
            .offset(u * style.windowAt, u * style.windowAt)
            .size(u * style.windowSize)
            .clip(RoundedCornerShape(u * style.windowRadius))
        if (owned) {
            rememberThemeImage("theme/$dir/stickerbook/sticker_base.png")?.let { Image(it, null, Modifier.fillMaxSize()) }
            Box(window.background(Color.White)) {
                if (art != null) {
                    Image(art, item.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(item.label, Modifier.align(Alignment.Center), fontSize = with(density) { (u * 200).toSp() }, color = color, fontWeight = FontWeight.Black)
                }
            }
            rememberThemeImage("theme/$dir/stickerbook/sticker_top.png")?.let { Image(it, null, Modifier.fillMaxSize()) }
            if (item.id.kind == StickerKind.YOGA) {
                YogaPill(item.label, color, style.pillText, u)
            } else {
                Box(
                    Modifier
                        .offset(u * 4, u * 4)
                        .size(u * 134)
                        .background(color, CircleShape)
                        .border(u * 6, style.badgeBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.label, fontSize = with(density) { (u * 134 * 0.58f).toSp() }, color = style.badgeText, fontWeight = FontWeight.Black)
                }
            }
        } else {
            rememberThemeImage("theme/$dir/stickerbook/sticker_empty.png")?.let { Image(it, null, Modifier.fillMaxSize()) }
            Box(window, contentAlignment = Alignment.Center) {
                if (item.id.kind == StickerKind.YOGA && art != null) {
                    Image(art, item.label, Modifier.fillMaxSize().graphicsLayer { alpha = 0.28f }, contentScale = ContentScale.Crop, colorFilter = GRAYSCALE)
                } else {
                    Text(item.label, fontSize = with(density) { (u * 230).toSp() }, color = style.emptySymbol, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/** 瑜伽貼紙下方的名稱牌：高 90、底邊離畫布底 2。字太長就縮小，一行放得下。 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.YogaPill(label: String, fill: Color, text: Color, u: Dp) {
    val density = LocalDensity.current
    val maxW = u * 440
    val font = minOf(u * 58, maxW * 0.86f / label.length.coerceAtLeast(1))
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = u * 2)
            .size(maxW, u * 90)
            .background(fill, RoundedCornerShape(50))
            .border(u * 5, Color.White, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = with(density) { font.toSp() }, color = text, fontWeight = FontWeight.Black, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** 一次「拿到貼紙」：[isNew]＝第一次拿到。[seq] 每次加 1，同一張再拿也會重播。 */
data class StickerGain(val item: StickerItem, val isNew: Boolean, val seq: Int)

/** 拿到貼紙動畫總長。 */
const val STICKER_GAIN_MS = 2600

/**
 * 拿到貼紙：光暈＋貼紙從中間放大出現 → 停 1.5 秒 → 縮小飛到右上角（首頁貼紙冊的位置）並淡出。
 * 不攔按鍵，蓋在任何畫面上都可以。播完呼叫 [onDone]。
 * 第一次拿到寫「拿到新貼紙！」（主題標題圖）；已經有的寫「這張已經有囉！」。
 */
@Composable
fun StickerGainOverlay(gain: StickerGain, onDone: () -> Unit) {
    val theme = LocalAppTheme.current
    val dir = themeDir(theme)
    val t = remember(gain.seq) { Animatable(0f) }
    LaunchedEffect(gain.seq) {
        t.animateTo(1f, tween(STICKER_GAIN_MS, easing = LinearEasing))
        onDone()
    }
    val ms = t.value * STICKER_GAIN_MS
    val appear = seg(ms, 0f, 350f)
    val settle = seg(ms, 350f, 450f)
    val fly = seg(ms, 1950f, 2500f).let { it * it }
    val scale = (if (appear < 1f) 1.15f * appear else 1.15f - 0.15f * settle) * (1f - 0.8f * fly)
    val alpha = (1f - fly) * seg(ms, 0f, 120f)
    val stickerSize = 210.dp
    Box(Modifier.fillMaxSize().background(Color(0xFF000000).copy(alpha = 0.35f * alpha)))
    DesignFrame {
        // 中心 (480, 290) dp；飛到右上角貼紙冊圓鈕附近 (850, 50)。
        val cx = 480.dp + 370.dp * fly
        val cy = 290.dp - 240.dp * fly
        rememberThemeImage("theme/$dir/stickerbook/sticker_new_glow.png")?.let {
            Image(
                it, null,
                Modifier
                    .offset(cx - 225.dp, cy - 225.dp)
                    .size(450.dp)
                    .graphicsLayer {
                        this.alpha = alpha * appear
                        scaleX = scale
                        scaleY = scale
                        rotationZ = ms / 40f
                    },
            )
        }
        StickerView(
            gain.item, owned = true, size = stickerSize,
            modifier = Modifier
                .offset(cx - stickerSize / 2, cy - stickerSize / 2)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
        )
        val titleAlpha = seg(ms, 150f, 350f) * (1f - seg(ms, 1850f, 2050f))
        // 標題在 HUD 下面（安全區頂 27 dp ＋ HUD 約 30 dp），不壓到右上角的進度。
        Box(Modifier.fillMaxSize().padding(top = 76.dp).graphicsLayer { this.alpha = titleAlpha }, contentAlignment = Alignment.TopCenter) {
            if (gain.isNew) {
                rememberThemeImage("theme/$dir/stickerbook/title_new_sticker.png")?.let { Image(it, "拿到新貼紙！", Modifier.size(400.dp, 95.dp)) }
            } else {
                StickerTitle("這張已經有囉！", 44.sp)
            }
        }
    }
}

/** 小旅程、貼紙冊的大標題字：B 黃字深框、C 金字發光、A 深棕字。 */
@Composable
internal fun StickerTitle(text: String, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    when (theme.id) {
        ThemeId.B -> DecoratedText(text, fontSize = size, color = Color(0xFFFFE14D), stroke = theme.outline, strokeWidth = 4.dp, modifier = modifier.graphicsLayer { rotationZ = -3f })
        ThemeId.C -> DecoratedText(text, fontSize = size, color = theme.symbol, glow = Color(0xB2FFD65A), glowRadius = 4.dp, modifier = modifier)
        ThemeId.A -> DecoratedText(text, fontSize = size, color = theme.textPrimary, modifier = modifier.graphicsLayer { rotationZ = -2f })
    }
}
