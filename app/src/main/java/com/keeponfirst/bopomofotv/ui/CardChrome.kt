package com.keeponfirst.bopomofotv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * 大人陪的三顆提示，對應原本的按鍵：確定鍵＝再聽一次、上鍵＝答對了、下鍵＝再練練。
 * 只是畫出來給大人看，按鍵行為不變，按鈕本身不能聚焦。
 */
enum class ParentButton(val key: String, val label: String) {
    LISTEN("OK", "再聽一次"),
    CORRECT("↑", "答對了"),
    AGAIN("↓", "再練練"),
}

/** 星星列：本輪第 [index] 張（0 起算）／共 [total] 張；[currentDone]＝目前這張已按答對。 */
data class StarRow(val index: Int, val total: Int, val currentDone: Boolean)

/** 星星列最多畫幾格。超過（再練的卡排到後面讓本輪變長）就只顯示文字。 */
private const val MAX_STARS = 12

/** 「第 N 張／共 M 張」。[index] 0 起算。 */
fun cardCounter(index: Int, total: Int) = "第 ${index + 1} 張／共 $total 張"

/**
 * 卡片頁的外框：左上模式、右上進度（大人陪多一排星星）、中間大卡、下方三顆提示（只有大人陪）。
 * [celebrate] 每次答對加 1，星星列等回饋動畫飛到時才點亮那格；[onStarTarget] 回報目前那格在畫面上的位置。
 */
@Composable
fun CardPage(
    mode: String,
    counter: String,
    stars: StarRow? = null,
    celebrate: Int = 0,
    onStarTarget: (Offset) -> Unit = {},
    buttons: ParentButton? = null,
    showButtons: Boolean = false,
    panel: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().safeArea()) {
        InfoPill(Modifier.align(Alignment.TopStart)) { PillText(mode) }
        InfoPill(Modifier.align(Alignment.TopEnd)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                if (stars != null && stars.total <= MAX_STARS) Stars(stars, celebrate, onStarTarget)
                PillText(counter)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 52.dp, bottom = if (showButtons) 78.dp else 4.dp, start = Spacing.S, end = Spacing.S),
        ) {
            CardPanel(panel)
        }
        if (showButtons) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                ParentButton.entries.forEach { HintButton(it, active = it == buttons) }
            }
        }
    }
}

@Composable
private fun PillText(text: String) {
    val theme = LocalAppTheme.current
    Text(text, fontSize = TextSize.Info, color = if (theme.id == ThemeId.B) theme.hudText else theme.textSecondary, fontWeight = FontWeight.Black)
}

/** 大卡：B 白卡＋深框＋底下厚度；A 米白紙卡＋細框＋淡投影；C 不畫卡，內容直接放在夜空上。 */
@Composable
private fun CardPanel(content: @Composable BoxScope.() -> Unit) {
    val theme = LocalAppTheme.current
    val shape = RoundedCornerShape(theme.cardRadius)
    val frame = when (theme.id) {
        ThemeId.C -> Modifier
        else -> Modifier
            .drawBehind {
                val dy = (if (theme.id == ThemeId.B) 6 else 5).dp.toPx()
                val shade = if (theme.id == ThemeId.B) Color(0xFF2B7FD0) else Color(0x1F785A3C)
                drawRoundRect(shade, topLeft = Offset(0f, dy), size = size, cornerRadius = CornerRadius(theme.cardRadius.toPx()))
            }
            .background(theme.surface, shape)
            .border(theme.borderNormal, theme.outline, shape)
    }
    Box(Modifier.fillMaxSize().then(frame).padding(Spacing.S), content = content)
}

/**
 * 換卡時新卡從右邊滑入（往回換從左邊），約 [Motion.CARD_SLIDE_MS]。
 * [target] 一變就換；[content] 一定要用參數畫，不要用外面的變數，否則滑出去的舊卡會變成新卡。
 */
@Composable
fun <T> CardSlide(target: T, forward: Boolean, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(Motion.CARD_SLIDE_MS)) { dir * it / 3 } + fadeIn(tween(Motion.CARD_SLIDE_MS)))
                .togetherWith(slideOutHorizontally(tween(Motion.CARD_SLIDE_MS)) { -dir * it / 3 } + fadeOut(tween(Motion.CARD_SLIDE_MS)))
        },
        label = "card",
    ) { content(it) }
}

/** 卡片插圖的框：B 淡紫圓、A 白邊方框＋紙膠帶、C 發光圓。 */
@Composable
fun CardImage(image: ImageBitmap, description: String, size: Dp) {
    val theme = LocalAppTheme.current
    when (theme.id) {
        // 例詞圖是白底方圖：圓框內填白，圖裁成圓，才不會露出方角。
        ThemeId.B -> Box(
            Modifier.size(size).background(Color(0xFFEDE4FF), CircleShape).border(3.dp, theme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Image(image, description, Modifier.size(size * 0.8f).clip(CircleShape).background(Color.White), contentScale = ContentScale.Fit) }
        ThemeId.A -> Box(Modifier.size(size)) {
            val shape = RoundedCornerShape(11.dp)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = -1.5f }
                    .background(Color.White, shape)
                    .border(theme.borderNormal, theme.outline, shape)
                    .padding(10.dp)
                    .background(theme.entryColors[HomeEntry.YOGA.ordinal], RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) { Image(image, description, Modifier.fillMaxSize(0.88f), contentScale = ContentScale.Fit) }
            rememberThemeImage("theme/a/guide/tape.png")?.let {
                Image(it, null, Modifier.offset(x = (-10).dp, y = (-6).dp).size(70.dp, 20.dp).graphicsLayer { rotationZ = -8f })
            }
        }
        ThemeId.C -> Box(
            Modifier
                .size(size)
                .drawBehind {
                    for (k in 5 downTo 1) drawCircle(Color(0xFF5CE1E6).copy(alpha = 0.07f), radius = this.size.minDimension / 2 + k * 5.dp.toPx())
                }
                .background(Color.White, CircleShape)
                .border(4.dp, Color(0xFF5CE1E6), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Image(image, description, Modifier.size(size * 0.86f).clip(CircleShape), contentScale = ContentScale.Fit) }
    }
}

/** 一顆提示按鈕。[active]＝剛按過或已記錄：B 加白圈深框並放大，A 換淺橘底橘字，C 換金底深字。 */
@Composable
private fun HintButton(button: ParentButton, active: Boolean) {
    val theme = LocalAppTheme.current
    val f by animateFloatAsState(if (active) 1f else 0f, tween(Motion.FOCUS_MS), label = "button")
    val i = button.ordinal
    val shape = RoundedCornerShape(theme.buttonRadius)
    val scale = 1f + (if (theme.id == ThemeId.B) 0.12f else 0.08f) * f
    val fill = when (theme.id) {
        ThemeId.B -> theme.buttonColors[i]
        else -> androidx.compose.ui.graphics.lerp(theme.buttonColors[i], theme.buttonActive, f)
    }
    val text = androidx.compose.ui.graphics.lerp(theme.buttonText, theme.buttonTextActive, f)
    val border = when (theme.id) {
        ThemeId.A -> androidx.compose.ui.graphics.lerp(theme.outline, theme.focusFrame, f)
        ThemeId.B -> theme.outline
        ThemeId.C -> androidx.compose.ui.graphics.lerp(Color(0x4CFFFFFF), Color.White, f)
    }
    Box(
        Modifier
            .size(176.dp, 54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val r = CornerRadius(theme.buttonRadius.toPx())
                fun ring(spread: Float, color: Color, dy: Float = 0f) = drawRoundRect(
                    color,
                    topLeft = Offset(-spread, -spread + dy),
                    size = Size(size.width + spread * 2, size.height + spread * 2),
                    cornerRadius = CornerRadius(r.x + spread),
                )
                when (theme.id) {
                    ThemeId.B -> {
                        if (f > 0f) {
                            drawGlow(r.x, 8.5.dp.toPx(), 22.dp.toPx(), theme.focusGlow, 0.55f * f)
                            ring(8.5.dp.toPx(), theme.outline.copy(alpha = f))
                            ring(5.dp.toPx(), Color.White.copy(alpha = f))
                        }
                        ring(0f, theme.buttonShade[i], 6.dp.toPx())
                    }
                    ThemeId.A -> ring(0f, theme.buttonShade[i], 4.dp.toPx())
                    ThemeId.C -> if (f > 0f) drawGlow(r.x, 1.dp.toPx(), 22.dp.toPx(), theme.focusGlow, 0.9f * f)
                }
            }
            .background(fill, shape)
            .border(if (theme.id == ThemeId.C) 2.dp else theme.borderNormal, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(button.key, fontSize = 18.sp, color = text.copy(alpha = 0.8f), fontWeight = FontWeight.Black)
            Text("  ${button.label}", fontSize = 24.sp, color = text, fontWeight = FontWeight.Black)
        }
    }
}

/**
 * 星星列：B 星星貼紙、C 星座格、A 圓點。已完成的格子在前，目前那格比較大。
 * 答對時目前那格等回饋動畫的星星飛到才點亮並彈一下（[celebrate] 變了之後延遲 [STAR_ARRIVE_MS]）。
 */
@Composable
private fun Stars(row: StarRow, celebrate: Int, onTarget: (Offset) -> Unit) {
    val theme = LocalAppTheme.current
    var revealed by remember { mutableStateOf(true) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(celebrate) {
        if (celebrate == 0) return@LaunchedEffect
        revealed = false
        delay(starArriveMillis(theme.id))
        revealed = true
        pop.snapTo(1f)
        pop.animateTo(1.35f, tween(80))
        pop.animateTo(1f, tween(100))
    }
    LaunchedEffect(row.index) { revealed = true }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (theme.id == ThemeId.A) 6.dp else 3.dp)) {
        repeat(row.total) { k ->
            val state = when {
                k < row.index -> 0
                k == row.index -> if (row.currentDone && revealed) 0 else 1
                else -> 2
            }
            val current = k == row.index
            val mod = if (current) {
                Modifier
                    .onGloballyPositioned { onTarget(it.boundsInRoot().center) }
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
            } else {
                Modifier
            }
            StarCell(theme, state, current, mod)
        }
    }
}

/** 答對後，回饋的星星飛到星星列要多久（各主題的時間軸不同）。 */
internal fun starArriveMillis(id: ThemeId): Long = when (id) {
    ThemeId.A -> 300L
    ThemeId.B -> 600L
    ThemeId.C -> 350L
}

/** 一格。[state]：0 已完成、1 目前、2 未完成。 */
@Composable
private fun StarCell(theme: AppTheme, state: Int, current: Boolean, modifier: Modifier) {
    when (theme.id) {
        ThemeId.B -> {
            val (path, size) = when (state) {
                0 -> "theme/b/feedback/hud_star_done.png" to (if (current) 28.dp else 22.dp)
                1 -> "theme/b/feedback/hud_star_current.png" to 28.dp
                else -> "theme/b/feedback/hud_star_todo.png" to 19.dp
            }
            rememberThemeImage(path)?.let { Image(it, null, modifier.size(size)) }
        }
        ThemeId.C -> {
            val (path, size) = when (state) {
                0 -> "theme/c/feedback/slot_star_done.png" to (if (current) 26.dp else 20.dp)
                1 -> "theme/c/feedback/slot_star_current.png" to 34.dp
                else -> "theme/c/feedback/slot_star_todo.png" to 15.dp
            }
            rememberThemeImage(path)?.let { Image(it, null, modifier.size(size)) }
        }
        ThemeId.A -> {
            val color = when (state) {
                0 -> theme.progressDone
                1 -> theme.progressCurrent
                else -> theme.progressTodo
            }
            Canvas(modifier.size(if (current) 14.dp else 10.dp)) {
                drawCircle(color)
                if (state == 2) drawCircle(theme.outline.copy(alpha = 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
        }
    }
}
