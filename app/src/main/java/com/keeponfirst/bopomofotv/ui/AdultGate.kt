package com.keeponfirst.bopomofotv.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 大人設定要長按 OK 多久。小孩亂按多半是短按，按住 2 秒才進得去。 */
const val ADULT_HOLD_MILLIS = 2000L

/** 放開 OK 時該做什麼。 */
enum class Release {
    /** 按不夠久：顯示「請大人長按 OK」。 */
    HINT,

    /** 按夠久但計時還沒觸發（放開剛好在 2 秒那一刻）：補開設定頁。 */
    OPEN,

    /** 已經開過了，或根本沒按下：不做事。 */
    NONE,
}

/**
 * 長按判定。時間由呼叫端傳入（毫秒），方便測試。
 * 按住時呼叫 [down]（重複的 KeyDown 不會重設起點）；計時到了呼叫 [poll]；放開呼叫 [up]。
 */
class HoldGate(private val holdMillis: Long = ADULT_HOLD_MILLIS) {
    private var downAt: Long? = null
    private var fired = false

    /** 回 true＝新的一次按下；按住期間重複送來的 KeyDown 回 false。 */
    fun down(at: Long): Boolean {
        if (downAt != null) return false
        downAt = at
        fired = false
        return true
    }

    /** 按住滿 [holdMillis] 回 true，同一次按住只回一次。 */
    fun poll(now: Long): Boolean {
        val start = downAt ?: return false
        if (fired || now - start < holdMillis) return false
        fired = true
        return true
    }

    fun up(at: Long): Release {
        val start = downAt ?: return Release.NONE
        downAt = null
        return when {
            fired -> Release.NONE
            at - start >= holdMillis -> Release.OPEN
            else -> Release.HINT
        }
    }
}

/**
 * 首頁右上角的齒輪。焦點可以停在這裡，但只有從卡片按「上」才到得了（焦點規則在 HomeScreen）。
 * 長按 OK [ADULT_HOLD_MILLIS] 才呼叫 [onOpen]；按住時外圈慢慢填滿。短按只在左邊顯示提示。
 */
@Composable
fun AdultGear(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val gate = remember { HoldGate() }
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var focused by remember { mutableStateOf(false) }
    var hint by remember { mutableIntStateOf(0) }
    var hintVisible by remember { mutableStateOf(false) }
    val focus by animateFloatAsState(if (focused) 1f else 0f, tween(Motion.FOCUS_MS), label = "gear")

    LaunchedEffect(hint) {
        if (hint == 0) return@LaunchedEffect
        hintVisible = true
        delay(2000)
        hintVisible = false
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        AnimatedVisibility(hintVisible, enter = fadeIn(tween(Motion.SHORT_MS)), exit = fadeOut(tween(Motion.SHORT_MS))) {
            KeyHint("請大人長按 OK")
        }
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer {
                    val s = 1f + 0.2f * focus
                    scaleX = s
                    scaleY = s
                    alpha = 0.55f + 0.45f * focus
                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) {
                        holdJob?.cancel()
                        gate.up(SystemClock.uptimeMillis())
                        scope.launch { progress.snapTo(0f) }
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.remoteKey() != RemoteKey.OK) return@onPreviewKeyEvent false
                    // 用系統時鐘，不用事件時間：有些遙控器（和 adb 模擬的長按）重複 KeyDown 的時間戳不可靠。
                    val now = SystemClock.uptimeMillis()
                    when (event.type) {
                        KeyEventType.KeyDown -> if (gate.down(now)) {
                            holdJob?.cancel()
                            holdJob = scope.launch {
                                progress.snapTo(0f)
                                progress.animateTo(1f, tween(ADULT_HOLD_MILLIS.toInt(), easing = LinearEasing))
                                if (gate.poll(SystemClock.uptimeMillis())) onOpen()
                            }
                        }
                        KeyEventType.KeyUp -> {
                            holdJob?.cancel()
                            scope.launch { progress.snapTo(0f) }
                            when (gate.up(now)) {
                                Release.HINT -> hint++
                                Release.OPEN -> onOpen()
                                Release.NONE -> Unit
                            }
                        }
                    }
                    true
                }
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            GearIcon(color = if (focused) theme.focusFrame else theme.textSecondary, ring = theme.outline, progress = progress.value, accent = theme.progressCurrent)
        }
    }
}

/** 齒輪圖：8 個齒＋中間圓孔。字型沒有齒輪字元，用畫的。外圈是長按進度。 */
@Composable
private fun GearIcon(color: Color, ring: Color, progress: Float, accent: Color) {
    Canvas(Modifier.size(44.dp)) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(ring.copy(alpha = 0.35f), radius = r)
        val tooth = Size(r * 0.32f, r * 0.42f)
        repeat(8) { i ->
            rotate(i * 45f, c) {
                drawRoundRect(color, topLeft = Offset(c.x - tooth.width / 2, c.y - r * 0.78f), size = tooth, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f))
            }
        }
        drawCircle(color, radius = r * 0.5f, center = c)
        drawCircle(ring.copy(alpha = 1f), radius = r * 0.2f, center = c)
        if (progress > 0f) {
            val w = r * 0.18f
            drawArc(accent, -90f, 360f * progress, useCenter = false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}
