package com.keeponfirst.bopomofotv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.keeponfirst.bopomofotv.data.StrokeSymbol
import com.keeponfirst.bopomofotv.data.StrokeTiming
import kotlinx.coroutines.delay

/** 還沒畫到的筆畫用淡色底稿，讓孩子先看到整個字的樣子。 */
private const val GHOST_ALPHA = 0.18f

/**
 * 筆順字和字型大字對齊：筆順路徑在 1024 方框裡留了邊、筆寬也比 Black 字重細，
 * 按下去時字會「縮一下」。不改路徑 JSON，改在畫布上放大、加粗、往下移。
 * 數值是 37 個符號和 notosanstc_black 比對的中位數（字框約大 22%、粗 1.5 倍、字型中心偏下約 6% 字級），
 * 再以模擬器截圖的字框確認。
 */
private const val STROKE_ZOOM = 1.2f
private const val STROKE_WEIGHT = 1.45f
private const val STROKE_DROP = 0.0625f

/** C 主題符號的金色光暈（同 [SymbolText]）。 */
private val GLOW = Color(0xB2FFD65A)

/**
 * 一次筆順播放的進度。[index] 正在畫第幾筆（0 起算），[fraction] 這筆畫了多少（0～1）。
 * 全部畫完 [done]＝true，停在完整的字。
 */
@Stable
class StrokePlayback internal constructor(val symbol: StrokeSymbol) {
    internal val paths: List<Path> = symbol.strokes.map { PathParser().parsePathString(it.d).toPath() }
    private val measures: List<PathMeasure> = paths.map { p -> PathMeasure().apply { setPath(p, false) } }
    internal val lengths: List<Float> = measures.map { it.length }
    private val segment = Path()

    /** 底稿：全部筆畫合成一條路徑一次畫完，交叉處才不會因為半透明疊兩次而變深。 */
    internal val ghost: Path = Path().apply { paths.forEach { addPath(it) } }
    internal val ghostWidth: Float = symbol.strokes.maxOf { it.width }

    var index by mutableIntStateOf(0)
        internal set
    internal val fraction = Animatable(0f)
    var done by mutableStateOf(false)
        internal set

    val total: Int get() = paths.size

    /** 第 [i] 筆畫到 [f] 的那一段。沿路徑方向從起筆取到 f×長度。 */
    internal fun partial(i: Int, f: Float): Path {
        segment.reset()
        measures[i].getSegment(0f, lengths[i] * f, segment, true)
        return segment
    }
}

/**
 * 筆順播放。[play] 是按了幾次：0＝還沒按，回 null（畫面照舊顯示符號大字）；每加 1 從第一筆重畫。
 * 每筆 [StrokeTiming.strokeMillis]，筆間停 [StrokeTiming.GAP_MS]。
 */
@Composable
fun rememberStrokePlayback(symbol: StrokeSymbol?, play: Int): StrokePlayback? {
    if (symbol == null || play == 0) return null
    val playback = remember(symbol, play) { StrokePlayback(symbol) }
    LaunchedEffect(playback) {
        for (i in 0 until playback.total) {
            playback.index = i
            playback.fraction.snapTo(0f)
            playback.fraction.animateTo(1f, tween(StrokeTiming.strokeMillis(playback.lengths[i], symbol.size), easing = LinearEasing))
            if (i < playback.total - 1) delay(StrokeTiming.GAP_MS)
        }
        playback.done = true
    }
    return playback
}

/**
 * 筆順畫布，取代符號大字，邊長 [size]（和符號字級同大）。
 * 顏色跟主題：筆畫用符號色；B 加深色外框，C 加金色光暈，A 不加（同 [SymbolText]）。
 */
@Composable
fun StrokeCanvas(playback: StrokePlayback, size: Dp) {
    val theme = LocalAppTheme.current
    Canvas(Modifier.size(size)) {
        val side = this.size.minDimension
        // 以畫布中心放大 [STROKE_ZOOM]，再下移 [STROKE_DROP]；畫到框外沒關係（Canvas 不裁切）。
        val scale = side / playback.symbol.size * STROKE_ZOOM
        // 外框、光暈的寬度用 dp 算，換成 viewBox 單位（畫布有縮放）。
        val outline = 4.dp.toPx() / scale
        val glow = 3.dp.toPx() / scale
        withTransform({
            translate(side * (1f - STROKE_ZOOM) / 2f, side * ((1f - STROKE_ZOOM) / 2f + STROKE_DROP))
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            line(playback.ghost, theme.symbol.copy(alpha = GHOST_ALPHA), playback.ghostWidth * STROKE_WEIGHT)
            val f = playback.fraction.value
            for (i in 0..playback.index) {
                val p = if (i < playback.index) playback.paths[i] else playback.partial(i, f)
                val w = playback.symbol.strokes[i].width * STROKE_WEIGHT
                when (theme.id) {
                    ThemeId.B -> line(p, theme.outline, w + outline * 2)
                    // 和 DecoratedText 的發光同一組層次。
                    ThemeId.C -> listOf(1f to 0.18f, 0.6f to 0.28f, 0.3f to 0.4f).forEach { (k, a) ->
                        line(p, GLOW.copy(alpha = GLOW.alpha * a), w + glow * 2 * k)
                    }
                    ThemeId.A -> Unit
                }
                line(p, theme.symbol, w)
            }
        }
    }
}

private fun DrawScope.line(path: Path, color: Color, width: Float) =
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))

/** 畫的時候顯示「第 N 筆」，畫完顯示「共 N 筆」。 */
fun strokeLabel(playback: StrokePlayback): String =
    if (playback.done) "共 ${playback.total} 筆" else "第 ${playback.index + 1} 筆"
