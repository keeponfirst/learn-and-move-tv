package com.keeponfirst.bopomofotv.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.keeponfirst.bopomofotv.data.StickerCatalog
import com.keeponfirst.bopomofotv.data.StickerItem
import com.keeponfirst.bopomofotv.record.StickerKind
import com.keeponfirst.bopomofotv.session.JourneyDay
import kotlinx.coroutines.delay

/*
 * 今天的小旅程：地圖畫面。座標照美術規格（設計稿 1920×1080 px，÷2 = dp）。
 * 地圖背景只有路、旗子、關著的禮物；站牌、導遊、字都由這裡疊。
 */

/** 站牌錨點（柱子底部），依站的順序：注音、字母、瑜伽。 */
private val STATION_ANCHOR = listOf(DpOffset(300.dp, 325.dp), DpOffset(540.dp, 395.dp), DpOffset(765.dp, 280.dp))

/** 站牌圖 260×190 dp；錨點在圖的 (130, 150)。 */
private val SIGN_W = 260.dp
private val SIGN_H = 190.dp
private val SIGN_ANCHOR = DpOffset(130.dp, 150.dp)

/** 導遊左上角：出發、第 1→2 站之間、第 2→3 站之間、終點。高 140 dp，都避開站牌。 */
private val GIRL_AT = listOf(DpOffset(100.dp, 300.dp), DpOffset(330.dp, 310.dp), DpOffset(635.dp, 280.dp), DpOffset(720.dp, 320.dp))
private val GIRL_H = 140.dp
private val GIRL_W = GIRL_H * 470f / 570f

/** 走完一站的慶祝：第 [index] 站（0 起算）；[isNew]＝這站的貼紙是第一次拿到。 */
data class StationCelebration(val index: Int, val isNew: Boolean)

/** 站牌上的字：「ㄆ 站」「A 站」「動一動站」。 */
private fun stationLabel(item: StickerItem?, kind: StickerKind): String = when (kind) {
    StickerKind.YOGA -> "動一動站"
    else -> "${item?.label.orEmpty()} 站"
}

/** 這一段時間軸：打勾與標題 0.4 秒 → 導遊走到下一站 1.2 秒 → 標題留到 2 秒。 */
private const val CHECK_MS = 400
private const val WALK_MS = 1200
private const val TITLE_HOLD_MS = 400

/**
 * 旅程地圖。OK 進入目前這站（[onStart] 帶站的索引）；旅程走完時 OK 打開貼紙冊（[onBook]）；返回鍵回首頁。
 * [celebrate] 不是 null：剛走完那站，先播慶祝（打勾、「第 N 站 完成！」、導遊走到下一站），再呼叫 [onCelebrated]
 * 讓 App 播拿到貼紙的動畫。慶祝中不接受 OK。
 * 10 分鐘上限：從進旅程（[startedAt]）算起，到了呼叫 [onTimeUp]。
 */
@Composable
fun JourneyScreen(
    day: JourneyDay,
    catalog: StickerCatalog,
    celebrate: StationCelebration?,
    startedAt: Long,
    onStart: (Int) -> Unit,
    onBook: () -> Unit,
    onCelebrated: (StationCelebration) -> Unit,
    onExit: () -> Unit,
    onTimeUp: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val dir = themeDir(theme)
    val walk = remember { Animatable(if (celebrate != null) 0f else 1f) }
    val check = remember { Animatable(if (celebrate != null) 0f else 1f) }
    var title by remember { mutableStateOf(celebrate != null) }
    var busy by remember { mutableStateOf(celebrate != null) }

    LaunchedEffect(Unit) {
        if (celebrate == null) return@LaunchedEffect
        check.animateTo(1f, tween(CHECK_MS))
        walk.animateTo(1f, tween(WALK_MS, easing = FastOutSlowInEasing))
        delay(TITLE_HOLD_MS.toLong())
        title = false
        busy = false
        onCelebrated(celebrate)
    }
    LaunchedEffect(Unit) {
        delay((startedAt + SELF_PLAY_MILLIS - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        onTimeUp()
    }
    BackHandler { onExit() }

    val total = day.stations.size
    // 慶祝中那站還沒「到」：路、導遊從上一站開始動。慶祝播完就照 day 畫。
    val celebrating = if (busy) celebrate else null
    val shownDone = celebrating?.index ?: day.done

    RemoteKeyBox(
        backdrop = "theme/$dir/journey/journey_map_bg.jpg",
        onKey = { key ->
            if (key != RemoteKey.OK || busy) return@RemoteKeyBox
            if (day.finished) onBook() else onStart(day.done)
        },
    ) {
        DesignFrame(background = "theme/$dir/journey/journey_map_bg.jpg") {
            // 走過的路：完成第 N 站疊第 N 張；剛完成的那張淡入。
            if (shownDone >= 1) MapLayer("theme/$dir/journey/road_done_$shownDone.png")
            if (celebrating != null) MapLayer("theme/$dir/journey/road_done_${celebrating.index + 1}.png", alpha = walk.value)

            day.stations.forEachIndexed { i, id ->
                val item = catalog[id]
                val state = when {
                    i < shownDone -> SignState.DONE
                    celebrating != null && i == celebrating.index -> SignState.JUST_DONE
                    i == day.done && !day.finished -> SignState.CURRENT
                    else -> SignState.TODO
                }
                StationSign(i, item, id.kind, state, check.value)
            }
            val finishedView = day.finished && celebrating == null
            if (finishedView) {
                rememberThemeImage("theme/$dir/journey/gift_open.png")?.let { Image(it, null, Modifier.offset(830.dp, 200.dp).size(125.dp)) }
            }

            val girlFrom = GIRL_AT[(celebrating?.index ?: day.done).coerceIn(0, GIRL_AT.lastIndex)]
            val girlTo = GIRL_AT[day.done.coerceIn(0, GIRL_AT.lastIndex)]
            val girl = DpOffset(lerp(girlFrom.x, girlTo.x, walk.value), lerp(girlFrom.y, girlTo.y, walk.value))
            Guide(girl, hop = if (celebrating != null) walk.value else 0f)
            // 對話框放在 HUD 下方正中間：放在導遊頭上會擋到第 1 站站牌；再往下一點避開 C 的月亮（模擬器看過）。
            if (!title && !day.finished) {
                val next = day.stations.getOrNull(day.done)
                val text = if (day.done == 0) "今天要去 $total 個地方喔！" else "下一站是 ${next?.let { stationLabel(catalog[it], it.kind) }.orEmpty()}，出發！"
                SpeechBubble(text, Modifier.align(Alignment.TopCenter).padding(top = 116.dp))
            }

            Box(Modifier.fillMaxSize().safeArea()) {
                InfoPill(Modifier.align(Alignment.TopStart)) { HudText("今天的小旅程") }
                InfoPill(Modifier.align(Alignment.TopEnd)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        StationDots(shownDone, total)
                        HudText(if (finishedView) "$total 站都完成了" else "第 ${(shownDone + 1).coerceAtMost(total)} 站／共 $total 站")
                    }
                }
                if (!busy) KeyHint(if (day.finished) "OK 看貼紙冊　返回 回首頁" else "OK 出發　返回 回首頁（進度保留到今天結束）", Modifier.align(Alignment.BottomEnd))
            }

            if (celebrating != null && title) {
                Box(Modifier.fillMaxSize().padding(top = 70.dp).graphicsLayer { alpha = check.value }, contentAlignment = Alignment.TopCenter) {
                    StickerTitle("第 ${celebrating.index + 1} 站 完成！", 72.sp, Modifier.graphicsLayer {
                        val s = 0.6f + 0.4f * check.value
                        scaleX = s
                        scaleY = s
                    })
                }
            }
            if (finishedView) JourneyDone(day, catalog)
        }
    }
}

private enum class SignState { DONE, JUST_DONE, CURRENT, TODO }

@Composable
private fun MapLayer(path: String, alpha: Float = 1f) {
    rememberThemeImage(path)?.let { Image(it, null, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, contentScale = ContentScale.Crop) }
}

/** 站牌：底圖依狀態換（一般／目前／還沒到）＋左邊圖示＋右邊字；完成的右上角蓋打勾章。 */
@Composable
private fun StationSign(index: Int, item: StickerItem?, kind: StickerKind, state: SignState, check: Float) {
    val theme = LocalAppTheme.current
    val dir = themeDir(theme)
    val anchor = STATION_ANCHOR.getOrElse(index) { STATION_ANCHOR.last() }
    val sign = when (state) {
        SignState.DONE, SignState.JUST_DONE -> "station_sign.png"
        SignState.CURRENT -> "station_sign_current.png"
        SignState.TODO -> "station_sign_todo.png"
    }
    val contentAlpha = if (state == SignState.TODO) 0.55f else 1f
    Box(Modifier.offset(anchor.x - SIGN_ANCHOR.x, anchor.y - SIGN_ANCHOR.y).size(SIGN_W, SIGN_H)) {
        rememberThemeImage("theme/$dir/journey/$sign")?.let { Image(it, null, Modifier.fillMaxSize()) }
        Box(Modifier.offset(50.dp, 47.dp).size(50.dp).graphicsLayer { alpha = contentAlpha }) { StationIcon(item, kind) }
        Box(
            Modifier.offset(106.dp, 35.dp).size(106.dp, 75.dp).graphicsLayer { alpha = contentAlpha },
            contentAlignment = Alignment.Center,
        ) {
            Text(stationLabel(item, kind), fontSize = 23.sp, color = if (theme.id == ThemeId.C) Color.White else theme.outline.copy(alpha = 1f), fontWeight = FontWeight.Black, maxLines = 1)
        }
        val badge = when (state) {
            SignState.DONE -> 1f
            SignState.JUST_DONE -> check
            else -> 0f
        }
        if (badge > 0f) {
            rememberThemeImage("theme/$dir/journey/station_done_badge.png")?.let {
                // 打勾章中心在牌面右上角 (220, 35)；剛完成時 0 → 1.2 → 1 彈出來。
                val pop = if (badge < 0.7f) badge / 0.7f * 1.2f else 1.2f - 0.2f * (badge - 0.7f) / 0.3f
                Image(it, null, Modifier.offset(190.dp, 5.dp).size(60.dp).graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                })
            }
        }
    }
}

/** 站牌左邊的圖示：注音、字母是站點色方塊＋白字；瑜伽是動作圖。 */
@Composable
private fun StationIcon(item: StickerItem?, kind: StickerKind) {
    val theme = LocalAppTheme.current
    val style = stickerStyle(theme)
    val shape = RoundedCornerShape(10.dp)
    if (kind == StickerKind.YOGA) {
        val art = item?.let { rememberStickerArt(it) }
        Box(Modifier.fillMaxSize().clip(shape).background(Color.White).border(2.dp, style.badgeBorder, shape)) {
            art?.let { Image(it, item.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    } else {
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationZ = -4f }.background(style.stationColors[kind.ordinal], shape).border(2.5.dp, style.badgeBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(item?.label.orEmpty(), fontSize = 30.sp, color = style.badgeText, fontWeight = FontWeight.Black)
        }
    }
}

/** 導遊：B、C 用主題的小女孩；A 沒有全身圖，用頭像當「現在在這裡」。走路時上下跳。 */
@Composable
private fun Guide(at: DpOffset, hop: Float) {
    val theme = LocalAppTheme.current
    val lift = if (hop in 0.01f..0.99f) (-10).dp * kotlin.math.abs(kotlin.math.sin(hop * Math.PI.toFloat() * 3)) else 0.dp
    when (theme.id) {
        ThemeId.A -> rememberThemeImage("theme/a/guide/avatar_head.png")?.let {
            Image(it, null, Modifier.offset(at.x + 20.dp, at.y + 70.dp + lift).size(69.dp, 63.dp))
        }
        ThemeId.B -> rememberThemeImage("theme/b/guide/guide_girl.png")?.let {
            Image(it, null, Modifier.offset(at.x, at.y + lift).size(GIRL_W, GIRL_H))
        }
        ThemeId.C -> rememberThemeImage("theme/c/guide/guide_girl.png")?.let {
            Image(it, null, Modifier.offset(at.x, at.y + lift).size(GIRL_W, GIRL_H))
        }
    }
}

/** 導遊的對話框：白底深框圓角，字置中。 */
@Composable
private fun SpeechBubble(text: String, modifier: Modifier) {
    val theme = LocalAppTheme.current
    val (fill, fg, line) = when (theme.id) {
        ThemeId.C -> Triple(Color(0xE61A1B4A), Color.White, Color(0xFFFFE27A))
        else -> Triple(Color.White, theme.textPrimary, theme.outline)
    }
    Box(
        modifier
            .background(fill, RoundedCornerShape(18.dp))
            .border(2.5.dp, line, RoundedCornerShape(18.dp))
            .padding(horizontal = Spacing.S, vertical = Spacing.XS),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 20.sp, color = fg, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun HudText(text: String) {
    val theme = LocalAppTheme.current
    Text(text, fontSize = TextSize.Info, color = if (theme.id == ThemeId.B) theme.hudText else theme.textSecondary, fontWeight = FontWeight.Black)
}

/** 右上角的站點圓：完成的實心、目前的放大、還沒到的空心。 */
@Composable
internal fun StationDots(done: Int, total: Int, size: Dp = 18.dp) {
    val theme = LocalAppTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { k ->
            val (fill, s) = when {
                k < done -> theme.progressDone to size
                k == done -> theme.progressCurrent to size * 1.25f
                else -> Color.Transparent to size
            }
            Box(Modifier.size(s).background(fill, CircleShape).border(2.dp, if (theme.id == ThemeId.B) Color.White else theme.textSecondary, CircleShape))
        }
    }
}

/** 今天的旅程走完：「今天的旅程完成了！」＋今天拿到的貼紙。 */
@Composable
private fun JourneyDone(day: JourneyDay, catalog: StickerCatalog) {
    val theme = LocalAppTheme.current
    val dir = themeDir(theme)
    Box(Modifier.fillMaxSize().background(Color(0x59000000)))
    Column(Modifier.fillMaxSize().padding(top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rememberThemeImage("theme/$dir/journey/title_journey_done.png")?.let { Image(it, "今天的旅程完成了！", Modifier.size(480.dp, 81.dp)) }
        Spacer(Modifier.height(Spacing.S))
        Text("今天拿到的貼紙", fontSize = TextSize.Info, color = Color.White, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(Spacing.XS))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            day.stations.forEach { id -> catalog[id]?.let { StickerView(it, owned = true, size = 170.dp, modifier = Modifier.wrapContentSize()) } }
        }
    }
}
