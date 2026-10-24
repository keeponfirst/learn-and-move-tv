package com.keeponfirst.bopomofotv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Text
import com.keeponfirst.bopomofotv.audio.AudioPlayer
import com.keeponfirst.bopomofotv.audio.BrushVoice
import com.keeponfirst.bopomofotv.data.BrushLines
import com.keeponfirst.bopomofotv.data.BrushZones
import com.keeponfirst.bopomofotv.session.BRUSH_ZONE_CODES
import com.keeponfirst.bopomofotv.session.BRUSH_ZONES
import com.keeponfirst.bopomofotv.session.BRUSH_ZONE_MILLIS
import com.keeponfirst.bopomofotv.session.BRUSH_ZONE_NAMES
import com.keeponfirst.bopomofotv.session.BrushLine
import com.keeponfirst.bopomofotv.session.BrushRules
import com.keeponfirst.bopomofotv.session.BrushState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 刷牙頁的按鍵提示（給大人看）。 */
private const val BRUSH_HINT_INTRO = "OK 開始　返回 回首頁"
private const val BRUSH_HINT = "OK 暫停／繼續　返回 回首頁"
private const val BRUSH_HINT_DONE = "返回 回首頁"

/** 一區時間到，小怪變泡泡飛走的時間（毫秒）。 */
private const val POP_MS = 1200f

/**
 * 刷牙小怪獸：開場 → 按 OK 開始 2 分鐘 → 6 區各 [zoneMillis] → 完成。時間表在 [BrushRules]。
 * 孩子不用按鍵，全程自動。確定鍵暫停／繼續，返回鍵回首頁。離開 App 自動暫停。
 * 語音見 [BrushVoice]：有音檔放音檔，沒有就用系統語音念 [lines]，再不行就靜音，流程照跑。
 * 插圖：theme/<主題>/brush/ 下有 PNG 就用（檔名見 [BrushArt]），沒有就用程式畫的。
 * 動畫只用位移、縮放、淡出；最快的擺動 1 秒 1 次，沒有閃爍。
 */
@Composable
fun BrushScreen(lines: Map<String, String>, player: AudioPlayer, onExit: () -> Unit, zoneMillis: Long = BRUSH_ZONE_MILLIS) {
    val context = LocalContext.current
    val zones = remember { BrushZones.load(context, BRUSH_ZONE_CODES) }
    val voice = remember { BrushVoice(context, player, lines) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }
    val schedule = remember(zoneMillis) { BrushRules.schedule(BrushLines.cheerCount(lines), zoneMillis) }
    var started by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var next by remember { mutableIntStateOf(0) }
    val state = BrushRules.state(elapsed, zoneMillis)

    LaunchedEffect(Unit) { voice.say(BrushLine.INTRO) }
    // 計時：用畫面的格時間累加，暫停或離開 App 就不加。到時間的台詞照 [BrushRules.due] 播。
    LaunchedEffect(started, paused) {
        if (!started || paused) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (next < schedule.size) {
            val now = withFrameMillis { it }
            elapsed += now - last
            last = now
            val (event, i) = BrushRules.due(schedule, next, elapsed)
            next = i
            event?.let { if (it.optional) voice.sayIfIdle(it.lines) else voice.say(it.lines) }
        }
    }
    LaunchedEffect(paused) { if (paused) voice.stop() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (started && !state.done) paused = true
    }
    BackHandler { onExit() }

    val theme = LocalAppTheme.current
    val art = remember(theme.id) { BrushArt(theme) }
    RemoteKeyBox(
        backdrop = theme.bgYoga,
        onKey = { key ->
            if (key == RemoteKey.OK) {
                when {
                    // 開場兩句約 10 秒，按 OK 就切掉，不然後面的口令會排在它後面、跟畫面對不上。
                    !started -> {
                        voice.stop()
                        started = true
                    }
                    state.done -> Unit
                    // 暫停會清掉還沒播的句子，繼續時重播這一區的口令。
                    paused -> {
                        paused = false
                        voice.say(listOf(BrushLine.zone(state.zone)))
                    }
                    else -> paused = true
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (state.done) {
                BrushDone(art)
            } else {
                BrushStage(art, zones, started, state, elapsed, zoneMillis, lines)
            }
            Box(Modifier.fillMaxSize().safeArea()) {
                val hint = when {
                    !started -> BRUSH_HINT_INTRO
                    state.done -> BRUSH_HINT_DONE
                    else -> BRUSH_HINT
                }
                KeyHint(hint, Modifier.align(Alignment.BottomEnd))
            }
            PauseOverlay(visible = paused)
        }
    }
}

/**
 * 刷牙小怪獸的插圖路徑（theme/<a|b|c>/brush/）。檔案不在就回 null，畫面改用程式畫的。
 * - hero_ready／hero_brush／hero_cheer.png：導遊拿牙刷，開場／刷牙中／完成（470×620）。
 * - teeth_base.png：上下兩排牙齒（1200×760）；teeth_lit_<區>.png：同尺寸的單區亮起圖層。位置看 brush/zones.json。
 * - germ_1～4.png：細菌小怪；germ_1～4_bubble.png：被泡泡包住、飛走（180×180）。
 * - done_tooth.png：完成的笑臉牙齒；sticker_art.png：完成貼紙（不透明方圖，畫面裁成圓）。
 */
internal class BrushArt(theme: AppTheme) {
    val dir = "theme/${themeDir(theme)}/brush"
    val guide = when (theme.id) {
        ThemeId.A -> "theme/a/guide/avatar_head.png"
        ThemeId.B -> "theme/b/guide/guide_girl.png"
        ThemeId.C -> "theme/c/guide/guide_girl.png"
    }
    val heroReady = "$dir/hero_ready.png"
    val heroBrush = "$dir/hero_brush.png"
    val heroCheer = "$dir/hero_cheer.png"
    val teeth = "$dir/teeth_base.png"
    fun zone(z: Int) = "$dir/teeth_lit_${BRUSH_ZONE_CODES[z]}.png"
    fun germ(kind: Int) = "$dir/germ_${kind + 1}.png"
    fun germPop(kind: Int) = "$dir/germ_${kind + 1}_bubble.png"
    val doneTooth = "$dir/done_tooth.png"
    val sticker = "$dir/sticker_art.png"

    /** 線條色：C 的 outline 是半透明白，畫牙齒要深色線。 */
    val ink = if (theme.id == ThemeId.C) Color(0xFF2A1F55) else theme.outline.copy(alpha = 1f)
    val highlight = when (theme.id) {
        ThemeId.A -> Color(0xFFFFC98A)
        ThemeId.B -> Color(0xFFFFE14D)
        ThemeId.C -> Color(0xFFFFE27A)
    }
    val done = theme.progressDone
}

/** 第 [z] 區的小怪是哪一種（4 種輪流）。 */
private fun germKind(z: Int) = z % 4

/** 4 種小怪的身體色：淡綠、淡紫輪流。 */
private val GERM_COLORS = listOf(Color(0xFFA8E6A1), Color(0xFFCDB8F7), Color(0xFF9EDFC8), Color(0xFFE2C2F2))

/** 開場與刷牙中：上面一排（標題或倒數＋6 區進度），左邊導遊，中間大牙齒，下面口令字幕。 */
@Composable
private fun BrushStage(art: BrushArt, zones: BrushZones?, started: Boolean, state: BrushState, elapsed: Long, zoneMillis: Long, lines: Map<String, String>) {
    val theme = LocalAppTheme.current
    val sway by rememberInfiniteTransition(label = "sway").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "sway",
    )
    // 版面（安全區內 864×486 dp）：上排 0～80（開場：標題＋按 OK 開始；刷牙中：大字倒數＋6 區進度），
    // 左邊導遊，右邊牙齒 y 96～438（牙齒圖 1200×760 等比），下排左邊字幕、右邊按鍵提示。
    Box(Modifier.fillMaxSize().safeArea()) {
        Row(Modifier.fillMaxWidth().height(80.dp).align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically) {
            if (started) {
                BigCountdown(state.remainingSeconds, Modifier)
                Spacer(Modifier.weight(1f))
                ZoneChecks(state.zone, art)
            } else {
                InfoPill { Text("刷牙小怪獸", fontSize = TextSize.Info, color = if (theme.id == ThemeId.B) theme.hudText else theme.textSecondary, fontWeight = FontWeight.Black) }
                Spacer(Modifier.weight(1f))
                StartBadge()
                Spacer(Modifier.weight(1f))
            }
        }
        // 右邊的區（上右、下右）導遊左右翻轉，像換手刷另一邊。
        val flip = started && state.zone < BRUSH_ZONES && BRUSH_ZONE_CODES[state.zone].endsWith("r")
        BrushGuide(art, sway, brushing = started, flip = flip, modifier = Modifier.padding(top = 110.dp).size(190.dp, 250.dp))
        Box(Modifier.align(Alignment.TopEnd).padding(top = 96.dp, end = 10.dp).size(540.dp, 342.dp)) {
            Teeth(art, zones, started, state, elapsed, zoneMillis, sway, Modifier.fillMaxSize())
        }
        val caption = if (started) lines[BrushLine.zone(state.zone)].orEmpty() else "拿好牙刷，打跑細菌怪！"
        Box(Modifier.align(Alignment.BottomStart)) { StageCaption(caption, 24.sp) }
    }
}

/** 開場上方的「按 OK 開始」。 */
@Composable
private fun StartBadge() {
    val theme = LocalAppTheme.current
    val (bg, fg) = when (theme.id) {
        ThemeId.A -> theme.focusFrame to Color.White
        ThemeId.B -> Color(0xFFFFE14D) to theme.outline
        ThemeId.C -> theme.focusFrame to Color(0xFF2A1F55)
    }
    Box(
        Modifier
            .background(bg, RoundedCornerShape(50))
            .border(if (theme.id == ThemeId.B) 3.dp else 0.dp, theme.outline, RoundedCornerShape(50))
            .padding(horizontal = Spacing.M, vertical = 4.dp),
    ) {
        Text("按 OK 開始 ▶", fontSize = 34.sp, color = fg, fontWeight = FontWeight.Black)
    }
}

/** 字幕：B 深色膠囊白字，A 白底深字，C 深藍底白字。 */
@Composable
private fun StageCaption(text: String, size: androidx.compose.ui.unit.TextUnit) {
    val theme = LocalAppTheme.current
    val (bg, fg) = when (theme.id) {
        ThemeId.B -> theme.hud to theme.hudText
        ThemeId.C -> Color(0xE61A1B4A) to Color.White
        ThemeId.A -> Color(0xF2FFFCF5) to theme.textPrimary
    }
    Box(
        Modifier
            .background(bg, RoundedCornerShape(50))
            .border(2.dp, if (theme.id == ThemeId.C) Color(0xFFFFE27A) else theme.outline.copy(alpha = 1f), RoundedCornerShape(50))
            .padding(horizontal = Spacing.S, vertical = 4.dp),
    ) {
        Text(text, fontSize = size, color = fg, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** 大字倒數「1:40」。 */
@Composable
private fun BigCountdown(seconds: Int, modifier: Modifier) {
    val theme = LocalAppTheme.current
    val text = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    DecoratedText(
        text,
        fontSize = 60.sp,
        color = if (theme.id == ThemeId.A) theme.textPrimary else Color.White,
        stroke = if (theme.id == ThemeId.C) null else theme.outline.copy(alpha = 1f),
        strokeWidth = 3.dp,
        glow = if (theme.id == ThemeId.C) Color(0xA6FFE27A) else null,
        glowRadius = 3.dp,
        modifier = modifier,
    )
}

/** 6 區進度：做完的打勾、目前這區亮起、還沒刷的淡色。字是區名。 */
@Composable
private fun ZoneChecks(current: Int, art: BrushArt) {
    val theme = LocalAppTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(BRUSH_ZONES) { z ->
            val (bg, fg) = when {
                z < current -> art.done to Color.White
                z == current -> art.highlight to art.ink
                else -> (if (theme.id == ThemeId.C) Color(0x4CFFFFFF) else Color(0xE6FFFFFF)) to theme.textSecondary
            }
            Row(
                Modifier
                    .graphicsLayer { val s = if (z == current) 1.12f else 1f; scaleX = s; scaleY = s }
                    .background(bg, RoundedCornerShape(50))
                    .border(2.dp, art.ink, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (z < current) {
                    Canvas(Modifier.size(16.dp)) { drawCheck(Color.White, 2.5.dp.toPx()) }
                    Spacer(Modifier.width(2.dp))
                }
                Text(BRUSH_ZONE_NAMES[z], fontSize = 20.sp, color = fg, fontWeight = FontWeight.Black)
            }
        }
    }
}

private fun DrawScope.drawCheck(color: Color, width: Float) {
    val path = Path().apply {
        moveTo(size.width * 0.18f, size.height * 0.52f)
        lineTo(size.width * 0.42f, size.height * 0.76f)
        lineTo(size.width * 0.84f, size.height * 0.26f)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * 導遊拿牙刷。有 hero_ready（開場）／hero_brush（刷牙中）就用；沒有就用主題導遊圖（B、C 全身、A 頭像），
 * 在舉起的手上畫一支大牙刷。[brushing]＝刷牙中：上下刷動（1 秒 1 次）；開場時不動。[flip]＝左右翻轉。
 */
@Composable
private fun BrushGuide(art: BrushArt, sway: Float, brushing: Boolean, flip: Boolean, modifier: Modifier) {
    val hero = rememberThemeImage(if (brushing) art.heroBrush else art.heroReady)
    val guide = rememberThemeImage(art.guide)
    val bob = if (brushing) sin(sway * 2 * PI * 2).toFloat() else 0f
    Box(modifier) {
        if (hero != null) {
            Image(
                hero,
                "拿牙刷的導遊",
                Modifier.fillMaxSize().graphicsLayer {
                    translationY = bob * 4.dp.toPx()
                    scaleX = if (flip) -1f else 1f
                },
                contentScale = ContentScale.Fit,
            )
            return@Box
        }
        val isHead = LocalAppTheme.current.id == ThemeId.A
        if (guide != null) {
            Image(
                guide,
                "導遊",
                if (isHead) Modifier.size(150.dp, 138.dp).align(Alignment.BottomStart).offset(y = (-40).dp) else Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        // 牙刷：從舉起的手（全身圖右上角）斜斜往上，像拿寶劍。A 只有頭像，放在頭像右邊。
        Canvas(Modifier.fillMaxSize()) {
            val hand = if (isHead) Offset(size.width * 0.78f, size.height * 0.62f) else Offset(size.width * 0.9f, size.height * 0.1f)
            translate(0f, bob * 6.dp.toPx()) {
                // 全身圖的手在上緣附近，牙刷斜 55 度往右，才不會伸出畫面上緣。
                rotate(if (isHead) 20f else 55f, hand) { drawToothbrush(art, hand, 130.dp.toPx()) }
            }
        }
    }
}

/** 一支大牙刷：握把底端在 [hand]，往上長 [length]。刷頭有刷毛和一小坨牙膏。 */
private fun DrawScope.drawToothbrush(art: BrushArt, hand: Offset, length: Float) {
    val w = length * 0.12f
    val stroke = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round)
    val handle = Rect(hand.x - w / 2, hand.y - length, hand.x + w / 2, hand.y + w * 0.6f)
    drawRoundRect(Color(0xFF4FA3FF), handle.topLeft, handle.size, CornerRadius(w / 2))
    drawRoundRect(art.ink, handle.topLeft, handle.size, CornerRadius(w / 2), style = stroke)
    // 握把上的白條紋
    drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(hand.x - w * 0.15f, hand.y - length * 0.75f), Size(w * 0.3f, length * 0.45f), CornerRadius(w * 0.15f))
    // 刷毛：在刷頭右側，一排白色小方塊
    val headTop = hand.y - length
    val bristle = Rect(hand.x + w / 2, headTop + w * 0.2f, hand.x + w * 1.5f, headTop + length * 0.3f)
    drawRoundRect(Color.White, bristle.topLeft, bristle.size, CornerRadius(w * 0.2f))
    drawRoundRect(art.ink, bristle.topLeft, bristle.size, CornerRadius(w * 0.2f), style = stroke)
    for (k in 1..3) {
        val y = bristle.top + bristle.height * k / 4
        drawLine(art.ink.copy(alpha = 0.4f), Offset(bristle.left, y), Offset(bristle.right, y), 1.5.dp.toPx())
    }
    // 牙膏：一小坨（只有一點點）
    val paste = Offset(bristle.right + w * 0.35f, bristle.top + bristle.height * 0.35f)
    drawCircle(Color(0xFFBFF0FF), w * 0.45f, paste)
    drawCircle(Color.White, w * 0.32f, paste + Offset(0f, -w * 0.25f))
    drawCircle(art.ink, w * 0.45f, paste, style = Stroke(2.dp.toPx()))
}

/**
 * 大牙齒，6 區（左、前、右 × 上下）。畫面左邊＝孩子的左邊（像照鏡子）。
 * 有 teeth_base.png 和 zones.json 就畫插圖，目前這區疊 teeth_lit_<區>.png，小怪與箭頭照 zones.json 的位置；
 * 沒有就程式畫（每排 6 顆、每 2 顆一區）。
 * 小怪：還沒刷的區站著搖；目前這區被泡泡慢慢包住；刷完的區變泡泡往上飄走（[POP_MS]），之後亮晶晶、打勾。
 */
@Composable
private fun Teeth(art: BrushArt, zones: BrushZones?, started: Boolean, state: BrushState, elapsed: Long, zoneMillis: Long, sway: Float, modifier: Modifier) {
    val teethImg = rememberThemeImage(art.teeth)
    val zoneImgs = (0 until BRUSH_ZONES).map { rememberThemeImage(art.zone(it)) }
    val germImgs = (0 until 4).map { rememberThemeImage(art.germ(it)) }
    val popImgs = (0 until 4).map { rememberThemeImage(art.germPop(it)) }
    // 新的一區開始時，那區的小怪從小變大冒出來。
    val appear = remember { Animatable(1f) }
    LaunchedEffect(state.zone, started) {
        if (started && state.zone < BRUSH_ZONES) {
            appear.snapTo(0.3f)
            appear.animateTo(1f, tween(600))
        }
    }
    Canvas(modifier) {
        val image = teethImg?.takeIf { zones != null }
        val geo = if (image != null && zones != null) ImageTeeth(size, zones) else DrawnTeeth(size)
        if (image != null) {
            drawImage(image, dstOffset = geo.imageOffset, dstSize = geo.imageSize)
        } else {
            drawTeeth(geo as DrawnTeeth, art)
        }
        val current = if (started) state.zone else -1
        // 目前這區亮起：畫在牙齒上、小怪下面。
        if (current in 0 until BRUSH_ZONES) {
            val lit = zoneImgs[current]
            if (image != null && lit != null) {
                drawImage(lit, dstOffset = geo.imageOffset, dstSize = geo.imageSize)
            } else {
                val r = geo.zoneRect(current).inflate(6.dp.toPx())
                drawRoundRect(art.highlight.copy(alpha = 0.45f), r.topLeft, r.size, CornerRadius(18.dp.toPx()))
                drawRoundRect(art.highlight, r.topLeft, r.size, CornerRadius(18.dp.toPx()), style = Stroke(6.dp.toPx()))
            }
            drawPointer(geo.arrow(current), art, sway)
        }
        for (z in 0 until BRUSH_ZONES) {
            val kind = germKind(z)
            // 每隻小怪擺動的相位錯開，不會一起動。
            val phase = (sway + z * 0.17f) % 1f
            val tilt = sin(phase * 2 * PI).toFloat() * 6f
            val done = z < current || (started && state.done)
            geo.germs(z).forEachIndexed { k, (c, r) ->
                // 同一區第 2 隻用下一種造型。
                val kk = (kind + k) % 4
                when {
                    done -> {
                        val t = ((elapsed - (z + 1) * zoneMillis) / POP_MS).coerceIn(0f, 1f)
                        if (t < 1f) drawPopped(c, r, kk, t, popImgs[kk], art)
                    }
                    z == current -> {
                        val wrap = (state.zoneElapsed.toFloat() / zoneMillis).coerceIn(0f, 1f)
                        drawGerm(c, r * appear.value, kk, tilt, germImgs[kk], art)
                        drawBubbles(c, r * 1.25f, wrap, phase)
                    }
                    else -> drawGerm(c, r * 0.8f, kk, tilt, germImgs[kk], art)
                }
            }
            if (done) drawSparkleTooth(geo.zoneRect(z), art, phase)
        }
    }
}

/** 「刷這裡」箭頭：尖端位置與方向（[down]＝往下指，畫在上排上方）。 */
private data class Arrow(val tip: Offset, val down: Boolean)

/** 牙齒版面：每區的框、小怪（中心, 半徑）、箭頭，單位是畫布 px。 */
private abstract class TeethGeometry {
    open val imageOffset = IntOffset.Zero
    open val imageSize = IntSize.Zero
    abstract fun zoneRect(z: Int): Rect
    abstract fun germs(z: Int): List<Pair<Offset, Float>>
    abstract fun arrow(z: Int): Arrow
}

/** 牙齒圖等比放進畫布正中間，zones.json 的像素座標換算到畫布。 */
private class ImageTeeth(canvas: Size, private val zones: BrushZones) : TeethGeometry() {
    private val scale = minOf(canvas.width / zones.canvas[0], canvas.height / zones.canvas[1])
    private val left = (canvas.width - zones.canvas[0] * scale) / 2
    private val top = (canvas.height - zones.canvas[1] * scale) / 2
    override val imageOffset = IntOffset(left.toInt(), top.toInt())
    override val imageSize = IntSize((zones.canvas[0] * scale).toInt(), (zones.canvas[1] * scale).toInt())
    private fun zone(z: Int) = zones.zones.getValue(BRUSH_ZONE_CODES[z])
    private fun at(x: Float, y: Float) = Offset(left + x * scale, top + y * scale)
    override fun zoneRect(z: Int) = zone(z).box.let { Rect(at(it[0].toFloat(), it[1].toFloat()), at(it[2].toFloat(), it[3].toFloat())) }
    // drawGerm 把小怪圖畫成 2.6r 見方，所以 r＝圖寬 ÷ 2.6。
    override fun germs(z: Int) = zone(z).germs.map { at(it.x, it.y) to zones.germCanvas * it.scale * scale / 2.6f }
    override fun arrow(z: Int) = zone(z).arrow.let { Arrow(at(it.x, it.y), it.dir == "down") }
}

/** 程式畫的牙齒版面。上排 y 8%～46%、下排 54%～92%，左右各留 4%。 */
private class DrawnTeeth(val size: Size) : TeethGeometry() {
    val left = size.width * 0.04f
    val right = size.width * 0.96f
    val toothW = (right - left) / 6f
    fun rowTop(row: Int) = size.height * if (row == 0) 0.08f else 0.54f
    val rowH = size.height * 0.38f
    fun tooth(row: Int, i: Int) = Rect(left + toothW * i + 3f, rowTop(row), left + toothW * (i + 1) - 3f, rowTop(row) + rowH)
    override fun zoneRect(z: Int): Rect {
        val row = z / 3
        val col = z % 3
        return Rect(left + toothW * col * 2, rowTop(row), left + toothW * (col * 2 + 2), rowTop(row) + rowH)
    }
    override fun germs(z: Int) = zoneRect(z).let { listOf(it.center to minOf(it.width, it.height) * 0.34f) }
    override fun arrow(z: Int) = zoneRect(z).let { if (z < 3) Arrow(Offset(it.center.x, it.top - 4f), true) else Arrow(Offset(it.center.x, it.bottom + 4f), false) }
}

private fun DrawScope.drawTeeth(l: DrawnTeeth, art: BrushArt) {
    val gum = Color(0xFFFFB7C5)
    val gumDark = Color(0xFFF08AA3)
    val stroke = Stroke(3.dp.toPx(), join = StrokeJoin.Round)
    // 牙齦：上下各一條圓角帶
    val upperGum = Rect(l.left - 10.dp.toPx(), 0f, l.right + 10.dp.toPx(), l.rowTop(0) + l.rowH * 0.35f)
    val lowerGum = Rect(l.left - 10.dp.toPx(), l.rowTop(1) + l.rowH * 0.65f, l.right + 10.dp.toPx(), size.height)
    listOf(upperGum, lowerGum).forEach {
        drawRoundRect(gum, it.topLeft, it.size, CornerRadius(40.dp.toPx()))
        drawRoundRect(gumDark, it.topLeft, it.size, CornerRadius(40.dp.toPx()), style = stroke)
    }
    // 牙齒：白色圓角
    for (row in 0..1) for (i in 0 until 6) {
        val t = l.tooth(row, i)
        val radius = CornerRadius(t.width * 0.32f)
        drawRoundRect(Color.White, t.topLeft, t.size, radius)
        drawRoundRect(art.ink, t.topLeft, t.size, radius, style = stroke)
        // 反光
        drawRoundRect(Color(0xFFE6F4FF), Offset(t.left + t.width * 0.18f, t.top + t.height * 0.15f), Size(t.width * 0.16f, t.height * 0.4f), CornerRadius(t.width * 0.08f))
    }
}

/** 「刷這裡！」箭頭＋字。上下輕輕晃（1 秒 1 次）。 */
private fun DrawScope.drawPointer(arrow: Arrow, art: BrushArt, sway: Float) {
    val bob = sin(sway * 2 * PI * 2).toFloat() * 5.dp.toPx()
    val dir = if (arrow.down) -1f else 1f
    val x = arrow.tip.x
    val tipY = arrow.tip.y - dir * bob
    val a = 16.dp.toPx()
    val path = Path().apply {
        moveTo(x, tipY)
        lineTo(x - a, tipY + dir * a)
        lineTo(x - a * 0.4f, tipY + dir * a)
        lineTo(x - a * 0.4f, tipY + dir * a * 1.8f)
        lineTo(x + a * 0.4f, tipY + dir * a * 1.8f)
        lineTo(x + a * 0.4f, tipY + dir * a)
        lineTo(x + a, tipY + dir * a)
        close()
    }
    drawPath(path, art.highlight)
    drawPath(path, art.ink, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
    // 字放在箭頭尾巴旁邊（右側），白底深字。
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 18.dp.toPx()
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 5.dp.toPx()
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    val ty = tipY + dir * a * 1.4f + paint.textSize * 0.35f
    val tx = x + a * 1.2f
    drawContext.canvas.nativeCanvas.drawText("刷這裡！", tx, ty, paint)
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = art.ink.toArgb()
    drawContext.canvas.nativeCanvas.drawText("刷這裡！", tx, ty, paint)
}

/**
 * 細菌小怪：圓滾滾、邊上一圈小圓凸、兩個大眼睛、笑嘴、腮紅。4 種：淡綠／淡紫 × 觸角／小耳朵。
 * 有 germ_N.png 就畫圖。
 */
private fun DrawScope.drawGerm(c: Offset, r: Float, kind: Int, tilt: Float, img: ImageBitmap?, art: BrushArt) {
    rotate(tilt, c) {
        if (img != null) {
            drawImageFit(img, Rect(c.x - r * 1.3f, c.y - r * 1.3f, c.x + r * 1.3f, c.y + r * 1.3f))
            return@rotate
        }
        val body = GERM_COLORS[kind]
        val line = art.ink
        val stroke = Stroke(r * 0.08f, join = StrokeJoin.Round)
        // 觸角或小耳朵
        if (kind % 2 == 0) {
            listOf(-1f, 1f).forEach { s ->
                val tip = c + Offset(s * r * 0.55f, -r * 1.25f)
                drawLine(line, c + Offset(s * r * 0.3f, -r * 0.8f), tip, r * 0.08f, StrokeCap.Round)
                drawCircle(body, r * 0.17f, tip)
                drawCircle(line, r * 0.17f, tip, style = stroke)
            }
        } else {
            listOf(-1f, 1f).forEach { s ->
                val ear = c + Offset(s * r * 0.62f, -r * 0.72f)
                drawCircle(body, r * 0.3f, ear)
                drawCircle(line, r * 0.3f, ear, style = stroke)
            }
        }
        // 身體：邊上 8 個小圓凸，讓輪廓軟軟的
        for (k in 0 until 8) {
            val a = k * PI / 4
            val p = c + Offset((cos(a) * r * 0.86f).toFloat(), (sin(a) * r * 0.86f).toFloat())
            drawCircle(body, r * 0.26f, p)
            drawCircle(line, r * 0.26f, p, style = stroke)
        }
        drawCircle(body, r * 0.92f, c)
        // 眼睛
        listOf(-1f, 1f).forEach { s ->
            val e = c + Offset(s * r * 0.33f, -r * 0.15f)
            drawCircle(Color.White, r * 0.24f, e)
            drawCircle(line, r * 0.24f, e, style = Stroke(r * 0.05f))
            drawCircle(line, r * 0.12f, e + Offset(r * 0.03f, r * 0.03f))
            drawCircle(Color.White, r * 0.045f, e + Offset(-r * 0.02f, -r * 0.04f))
        }
        // 笑嘴＋腮紅
        drawArc(line, 20f, 140f, false, c + Offset(-r * 0.25f, r * 0.05f), Size(r * 0.5f, r * 0.4f), style = Stroke(r * 0.08f, cap = StrokeCap.Round))
        listOf(-1f, 1f).forEach { s -> drawCircle(Color(0x66FF8FA8), r * 0.12f, c + Offset(s * r * 0.6f, r * 0.2f)) }
    }
}

/** 刷牙中慢慢包住小怪的泡泡：[wrap] 0→1，8 顆依序冒出，最後全包住。 */
private fun DrawScope.drawBubbles(c: Offset, ring: Float, wrap: Float, phase: Float) {
    val n = 8
    for (k in 0 until n) {
        val show = ((wrap * n) - k).coerceIn(0f, 1f)
        if (show <= 0f) continue
        val a = k * 2 * PI / n - PI / 2 + sin(phase * 2 * PI) * 0.08
        val p = c + Offset((cos(a) * ring).toFloat(), (sin(a) * ring).toFloat())
        val br = ring * (0.2f + 0.06f * (k % 3)) * show
        drawCircle(Color.White.copy(alpha = 0.55f), br, p)
        drawCircle(Color(0xFF7FC8F8), br, p, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, br * 0.3f, p + Offset(-br * 0.35f, -br * 0.35f))
    }
}

/** 被打跑：小怪縮小、包在大泡泡裡往上飄，淡出。[t] 0→1。有 germ_N_pop.png 就畫圖。 */
private fun DrawScope.drawPopped(c: Offset, r: Float, kind: Int, t: Float, img: ImageBitmap?, art: BrushArt) {
    val rise = c + Offset(0f, -r * 2.2f * t)
    val alpha = 1f - t
    val bubble = r * (1.1f + 0.2f * t)
    if (img != null) {
        val rect = Rect(rise.x - bubble, rise.y - bubble, rise.x + bubble, rise.y + bubble)
        drawContext.canvas.saveLayer(rect, androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha })
        drawImageFit(img, rect)
        drawContext.canvas.restore()
        return
    }
    drawCircle(Color.White.copy(alpha = 0.5f * alpha), bubble, rise)
    drawCircle(Color(0xFF7FC8F8).copy(alpha = alpha), bubble, rise, style = Stroke(3.dp.toPx()))
    drawCircle(Color.White.copy(alpha = alpha), bubble * 0.22f, rise + Offset(-bubble * 0.4f, -bubble * 0.4f))
    // 泡泡裡的小怪，縮小變淡
    val small = r * 0.55f * (1f - 0.4f * t)
    drawCircle(GERM_COLORS[kind].copy(alpha = alpha), small, rise)
    listOf(-1f, 1f).forEach { s -> drawCircle(art.ink.copy(alpha = alpha), small * 0.14f, rise + Offset(s * small * 0.35f, -small * 0.1f)) }
}

/** 刷乾淨的區：牙齒上方一顆小亮點（慢慢變大變小，2 秒一次）、右上角打勾圓章。 */
private fun DrawScope.drawSparkleTooth(rect: Rect, art: BrushArt, phase: Float) {
    val s = 0.85f + 0.15f * sin(phase * 2 * PI).toFloat()
    drawStar(rect.center + Offset(-rect.width * 0.15f, -rect.height * 0.12f), rect.height * 0.14f * s, Color(0xFFFFE27A), art.ink)
    val badge = Offset(rect.right - rect.width * 0.12f, rect.top + rect.height * 0.16f)
    val br = rect.height * 0.14f
    drawCircle(art.done, br, badge)
    drawCircle(art.ink, br, badge, style = Stroke(2.dp.toPx()))
    translate(badge.x - br * 0.7f, badge.y - br * 0.7f) {
        val path = Path().apply {
            moveTo(br * 1.4f * 0.18f, br * 1.4f * 0.52f)
            lineTo(br * 1.4f * 0.42f, br * 1.4f * 0.76f)
            lineTo(br * 1.4f * 0.84f, br * 1.4f * 0.26f)
        }
        drawPath(path, Color.White, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 四角星。 */
private fun DrawScope.drawStar(c: Offset, r: Float, fill: Color, line: Color?) {
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x + r * 0.15f, c.y - r * 0.15f, c.x + r, c.y)
        quadraticTo(c.x + r * 0.15f, c.y + r * 0.15f, c.x, c.y + r)
        quadraticTo(c.x - r * 0.15f, c.y + r * 0.15f, c.x - r, c.y)
        quadraticTo(c.x - r * 0.15f, c.y - r * 0.15f, c.x, c.y - r)
        close()
    }
    drawPath(path, fill)
    line?.let { drawPath(path, it, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round)) }
}

/** 把圖等比縮放放進 [rect] 正中間。 */
private fun DrawScope.drawImageFit(img: ImageBitmap, rect: Rect) {
    val scale = minOf(rect.width / img.width, rect.height / img.height)
    val w = img.width * scale
    val h = img.height * scale
    drawImage(
        img,
        dstOffset = IntOffset((rect.center.x - w / 2).toInt(), (rect.center.y - h / 2).toInt()),
        dstSize = IntSize(w.toInt(), h.toInt()),
    )
}

/**
 * 完成：導遊舉手（hero_cheer）、亮晶晶的笑臉牙齒（從小彈出）＋貼紙，「刷好了！」、「請大人再幫忙刷一遍」。
 * 提醒寫「把泡泡吐掉就好」，不寫漱口（兒童口腔衛教資料：刷完吐掉、不馬上漱口）。沒有分數、沒有比較。
 */
@Composable
private fun BrushDone(art: BrushArt) {
    val theme = LocalAppTheme.current
    val pop = remember { Animatable(0.4f) }
    val stick = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, tween(500))
        stick.animateTo(1f, tween(400))
    }
    val twinkle by rememberInfiniteTransition(label = "twinkle").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "twinkle",
    )
    val tooth = rememberThemeImage(art.doneTooth)
    val sticker = rememberThemeImage(art.sticker)
    val hero = rememberThemeImage(art.heroCheer)
    Box(Modifier.fillMaxSize().safeArea()) {
        Row(
            Modifier.fillMaxSize().padding(bottom = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M, Alignment.CenterHorizontally),
        ) {
            hero?.let { Image(it, "導遊", Modifier.size(170.dp, 224.dp), contentScale = ContentScale.Fit) }
            Box(Modifier.size(260.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
                if (tooth != null) {
                    Image(tooth, "笑臉牙齒", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Canvas(Modifier.fillMaxSize()) { drawHappyTooth(art, twinkle) }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 30.dp, y = 10.dp)
                        .size(120.dp)
                        .graphicsLayer {
                            scaleX = stick.value
                            scaleY = stick.value
                            alpha = stick.value
                            rotationZ = -10f
                        },
                ) {
                    if (sticker != null) {
                        // 貼紙圖是不透明方圖，裁成圓、加白框，像一張貼紙。
                        Image(
                            sticker,
                            "貼紙",
                            Modifier.fillMaxSize().clip(CircleShape).border(5.dp, Color.White, CircleShape).border(2.dp, art.ink, CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Canvas(Modifier.fillMaxSize()) { drawBrushSticker(art) }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DecoratedText(
                    "刷好了！",
                    fontSize = 72.sp,
                    color = theme.symbol,
                    stroke = if (theme.id == ThemeId.B) theme.outline else null,
                    strokeWidth = 4.dp,
                    glow = if (theme.id == ThemeId.C) Color(0xB2FFD65A) else null,
                    glowRadius = 3.dp,
                )
                Spacer(Modifier.height(Spacing.S))
                StageCaption("把泡泡吐掉就好", 28.sp)
                Spacer(Modifier.height(Spacing.S))
                AdultNote()
            }
        }
    }
}

/** 完成畫面給大人的提醒：「請大人再幫忙刷一遍」。 */
@Composable
private fun AdultNote() {
    val theme = LocalAppTheme.current
    val style = stickerStyle(theme)
    Box(
        Modifier
            .background(style.stationColors[0], RoundedCornerShape(14.dp))
            .border(2.5.dp, if (theme.id == ThemeId.C) Color.White else theme.outline.copy(alpha = 1f), RoundedCornerShape(14.dp))
            .padding(horizontal = Spacing.S, vertical = 6.dp),
    ) {
        Text("請大人再幫忙刷一遍", fontSize = 30.sp, color = style.pillText, fontWeight = FontWeight.Black)
    }
}

/** 笑臉牙齒：一顆大臼齒形狀、眼睛、笑嘴、腮紅，周圍 4 顆星慢慢變大變小（2 秒一次）。 */
private fun DrawScope.drawHappyTooth(art: BrushArt, twinkle: Float) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.22f, h * 0.2f)
        cubicTo(w * 0.32f, h * 0.08f, w * 0.42f, h * 0.14f, w * 0.5f, h * 0.18f)
        cubicTo(w * 0.58f, h * 0.14f, w * 0.68f, h * 0.08f, w * 0.78f, h * 0.2f)
        cubicTo(w * 0.9f, h * 0.34f, w * 0.84f, h * 0.55f, w * 0.78f, h * 0.68f)
        cubicTo(w * 0.74f, h * 0.8f, w * 0.72f, h * 0.92f, w * 0.64f, h * 0.92f)
        cubicTo(w * 0.56f, h * 0.92f, w * 0.56f, h * 0.74f, w * 0.5f, h * 0.74f)
        cubicTo(w * 0.44f, h * 0.74f, w * 0.44f, h * 0.92f, w * 0.36f, h * 0.92f)
        cubicTo(w * 0.28f, h * 0.92f, w * 0.26f, h * 0.8f, w * 0.22f, h * 0.68f)
        cubicTo(w * 0.16f, h * 0.55f, w * 0.1f, h * 0.34f, w * 0.22f, h * 0.2f)
        close()
    }
    drawPath(path, Color.White)
    drawPath(path, art.ink, style = Stroke(5.dp.toPx(), join = StrokeJoin.Round))
    drawRoundRect(Color(0xFFE6F4FF), Offset(w * 0.28f, h * 0.24f), Size(w * 0.07f, h * 0.2f), CornerRadius(w * 0.035f))
    listOf(-1f, 1f).forEach { s ->
        val e = Offset(w * (0.5f + s * 0.12f), h * 0.42f)
        drawCircle(art.ink, w * 0.035f, e)
        drawCircle(Color.White, w * 0.012f, e + Offset(-w * 0.01f, -w * 0.012f))
        drawCircle(Color(0x66FF8FA8), w * 0.04f, Offset(w * (0.5f + s * 0.2f), h * 0.52f))
    }
    drawArc(art.ink, 15f, 150f, false, Offset(w * 0.42f, h * 0.44f), Size(w * 0.16f, h * 0.12f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
    val stars = listOf(Offset(0.1f, 0.12f), Offset(0.9f, 0.1f), Offset(0.95f, 0.55f), Offset(0.06f, 0.6f))
    stars.forEachIndexed { k, p ->
        val s = 0.75f + 0.25f * sin((twinkle + k * 0.25f) * 2 * PI).toFloat()
        drawStar(Offset(w * p.x, h * p.y), w * 0.07f * s, Color(0xFFFFE27A), art.ink)
    }
}

/** 完成貼紙：圓形、主題色底、白框，中間一顆小笑臉牙和星星。 */
private fun DrawScope.drawBrushSticker(art: BrushArt) {
    val c = center
    val r = size.minDimension / 2
    drawCircle(Color.White, r, c)
    drawCircle(art.highlight, r * 0.86f, c)
    drawCircle(art.ink, r, c, style = Stroke(3.dp.toPx()))
    translate(c.x - r * 0.5f, c.y - r * 0.55f) {
        val w = r
        val h = r
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.2f)
            cubicTo(w * 0.35f, h * 0.05f, w * 0.45f, h * 0.15f, w * 0.5f, h * 0.18f)
            cubicTo(w * 0.55f, h * 0.15f, w * 0.65f, h * 0.05f, w * 0.8f, h * 0.2f)
            cubicTo(w * 0.92f, h * 0.4f, w * 0.8f, h * 0.7f, w * 0.72f, h * 0.95f)
            cubicTo(w * 0.62f, h * 0.95f, w * 0.58f, h * 0.75f, w * 0.5f, h * 0.75f)
            cubicTo(w * 0.42f, h * 0.75f, w * 0.38f, h * 0.95f, w * 0.28f, h * 0.95f)
            cubicTo(w * 0.2f, h * 0.7f, w * 0.08f, h * 0.4f, w * 0.2f, h * 0.2f)
            close()
        }
        drawPath(path, Color.White)
        drawPath(path, art.ink, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
        listOf(-1f, 1f).forEach { s -> drawCircle(art.ink, w * 0.05f, Offset(w * (0.5f + s * 0.13f), h * 0.42f)) }
        drawArc(art.ink, 15f, 150f, false, Offset(w * 0.4f, h * 0.45f), Size(w * 0.2f, h * 0.14f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
    drawStar(c + Offset(r * 0.55f, -r * 0.5f), r * 0.2f, Color.White, art.ink)
}
