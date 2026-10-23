package com.keeponfirst.bopomofotv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.tv.material3.Text

/** CC BY 4.0 要求的標示（教育部手冊開放部件 license.txt）。 */
const val MOE_ATTRIBUTION = "符號音檔：2017 © 教育部，國語注音符號手冊-開放部件，CC BY 4.0"

/** 第三方授權全文（Apache 2.0 等）放在 APK 的 assets/licenses/。 */
const val LICENSES_HINT = "完整授權見 assets/licenses"

/** CC BY 4.0 要附授權網址。電視上點不開 APK 裡的檔，所以網址直接寫在首頁。 */
const val CC_BY_URL = "https://creativecommons.org/licenses/by/4.0/deed.zh_TW"

/** 首頁的六個入口，順序同畫面由左到右。 */
enum class HomeEntry { JOURNEY, PARENT, SELF, ABC, YOGA, LISTEN }

/** 首頁入口的大字與小字，順序同 [HomeEntry]。 */
private val ENTRY_LABELS = listOf(
    "小旅程" to "3 站・約 5 分鐘",
    "大人陪" to "陪孩子學注音",
    "自己玩" to "自己翻注音卡",
    "ABC" to "英文字母",
    "瑜伽" to "跟著做動作",
    "聽聽看" to "聽音找符號",
)

/**
 * 入口卡尺寸。第一張「小旅程」寬 [firstWidth]，其他 5 張寬 [width]。整排置中。
 * 180 ＋ 5 × 120 ＋ 5 × 10 ＝ 830 dp，置中後兩側各 65 dp（安全區內再各留 17 dp）。
 * B 原本左邊的導遊角色放不下，拿掉，改在小旅程卡上方放對話框。
 * 聚焦放大、傾斜後外框仍在安全區內（SafeAreaTest 驗）。
 * 加第 6 張「聽聽看」之後：卡從 140 縮到 120 dp，卡名從 36 縮到 30 sp（3 個字 90 dp，放得進 96 dp 的內寬）。
 */
internal data class CardSize(val firstWidth: Dp, val width: Dp, val height: Dp, val gap: Dp) {
    fun widthOf(entry: HomeEntry) = if (entry == HomeEntry.JOURNEY) firstWidth else width
}

internal fun cardSize(theme: AppTheme) = when (theme.id) {
    ThemeId.B -> CardSize(180.dp, 120.dp, 250.dp, 10.dp)
    else -> CardSize(180.dp, 120.dp, 268.dp, 10.dp)
}

/**
 * 首頁「接著玩」：[entry] 那張卡上加「接著玩」標籤，小字換成 [detail]（例「上次到 ㄆ」）。
 * 進首頁時焦點放在這張。
 */
data class ResumeBadge(val entry: HomeEntry, val detail: String)

/** 焦點卡下方標籤的高度。 */
private val TAG_HEIGHT = 40.dp

/**
 * 首頁：主題字樣的標題＋單排 5 張大卡（最左是小旅程）。進來時焦點放在 [focus]；焦點移動時呼叫 [onFocus]。
 * App 層記住最後一次的入口，從任何模式回來焦點都回到同一顆。
 * 右上角兩顆圓鈕：貼紙冊（OK 進入 [onStickerBook]）與齒輪（大人設定）。只有從卡片按「上」才到得了，
 * 左右鍵不會從卡片經過它們；上鍵先到貼紙冊，右鍵到齒輪。齒輪要長按 OK 2 秒才呼叫 [onSettings]。
 * [journeyDone]／[journeyTotal]：今天的小旅程走了幾站，畫在小旅程卡上。
 * [resume] 不是 null：那張卡加「接著玩」標籤，焦點先放在它（不管 [focus]）。
 */
@Composable
fun HomeScreen(
    focus: HomeEntry,
    onFocus: (HomeEntry) -> Unit,
    onJourney: () -> Unit,
    onParent: () -> Unit,
    onSelf: () -> Unit,
    onAbc: () -> Unit,
    onYoga: () -> Unit,
    onListen: () -> Unit,
    onStickerBook: () -> Unit,
    onSettings: () -> Unit,
    journeyDone: Int = 0,
    journeyTotal: Int = 3,
    resume: ResumeBadge? = null,
) {
    val theme = LocalAppTheme.current
    val requesters = remember { HomeEntry.entries.associateWith { FocusRequester() } }
    val book = remember { FocusRequester() }
    val gear = remember { FocusRequester() }
    val start = resume?.entry ?: focus
    var last by remember { mutableStateOf(start) }
    LaunchedEffect(start) { requesters.getValue(start).requestFocus() }
    val actions = listOf(onJourney, onParent, onSelf, onAbc, onYoga, onListen)
    val size = cardSize(theme)
    val cardsTop = when (theme.id) {
        ThemeId.A -> 104.dp
        ThemeId.B -> 126.dp
        ThemeId.C -> 114.dp
    }

    Box(Modifier.fillMaxSize()) {
        ThemeBackdrop(theme.bgHome)
        if (theme.id == ThemeId.C) RocketGirl()
        Box(Modifier.fillMaxSize().safeArea()) {
            HomeTitle(Modifier.align(Alignment.TopCenter))
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = cardsTop),
                horizontalArrangement = Arrangement.spacedBy(size.gap),
            ) {
                HomeEntry.entries.forEach { entry ->
                    EntryCard(
                        entry = entry,
                        size = size,
                        onClick = actions[entry.ordinal],
                        journey = journeyDone to journeyTotal,
                        resume = resume?.takeIf { it.entry == entry },
                        modifier = Modifier
                            .focusRequester(requesters.getValue(entry))
                            .focusProperties {
                                up = book
                                down = FocusRequester.Cancel
                                if (entry == HomeEntry.entries.first()) left = FocusRequester.Cancel
                                if (entry == HomeEntry.entries.last()) right = FocusRequester.Cancel
                            },
                        onFocused = {
                            last = entry
                            onFocus(entry)
                        },
                    )
                }
            }
            val others = HomeEntry.entries.size - 1
            if (theme.id == ThemeId.B && resume?.entry != HomeEntry.JOURNEY) {
                JourneyBubble(Modifier.padding(top = cardsTop - 46.dp, start = (864.dp - size.firstWidth - size.width * others - size.gap * others) / 2))
            }
            Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                StickerBookButton(
                    onOpen = onStickerBook,
                    modifier = Modifier
                        .focusRequester(book)
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = gear
                            up = FocusRequester.Cancel
                            down = requesters.getValue(last)
                        },
                )
                AdultGear(
                    onOpen = onSettings,
                    modifier = Modifier
                        .focusRequester(gear)
                        .focusProperties {
                            left = book
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = requesters.getValue(last)
                        },
                )
            }
            // 授權標示：左下兩行（出處與授權、授權網址），右下「完整授權見…」放在按鍵提示上面。
            // 三段都寫在左邊會太寬，和按鍵提示疊在一起；按鍵提示再往上會壓到最後一張卡的焦點標籤（模擬器看過）。
            // A 背景左下角的葉子會壓到字，墊一層頁面色。
            val legalBg = if (theme.id == ThemeId.A) theme.background.copy(alpha = 0.85f) else Color.Transparent
            Column(Modifier.align(Alignment.BottomStart).background(legalBg, RoundedCornerShape(6.dp)).padding(horizontal = 4.dp)) {
                Text(MOE_ATTRIBUTION, fontSize = TextSize.Legal, color = TextColor)
                Text("授權條款：$CC_BY_URL", fontSize = TextSize.Legal, color = TextColor)
            }
            Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End) {
                Text(LICENSES_HINT, Modifier.background(legalBg, RoundedCornerShape(6.dp)).padding(horizontal = 4.dp), fontSize = TextSize.Legal, color = TextColor)
                KeyHint("← → 選擇　↑ 貼紙冊／大人　OK 進入")
            }
        }
    }
}

/** 標題「動動學學」：B 四色描邊字、A 深棕字＋頭像＋手繪底線、C 白字發光＋副標。 */
@Composable
private fun HomeTitle(modifier: Modifier) {
    val theme = LocalAppTheme.current
    when (theme.id) {
        ThemeId.B -> Row(modifier.padding(top = 2.dp)) {
            "動動學學".forEachIndexed { i, ch ->
                DecoratedText(
                    ch.toString(),
                    fontSize = 54.sp,
                    // 跳過小旅程的顏色，四個字沿用原本四張卡的顏色。
                    color = theme.entryColors[i + 1],
                    stroke = theme.outline,
                    strokeWidth = 4.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = if (i % 2 == 0) -4f else 3f },
                )
            }
        }
        ThemeId.A -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                rememberThemeImage("theme/a/guide/avatar_head.png")?.let { Image(it, null, Modifier.size(46.dp, 42.dp)) }
                Spacer(Modifier.width(Spacing.S))
                DecoratedText("動動學學", fontSize = 50.sp, color = theme.textPrimary)
            }
            rememberThemeImage("theme/a/guide/title_underline.png")?.let {
                Image(it, null, Modifier.size(170.dp, 8.dp).offset(x = 30.dp))
            }
        }
        ThemeId.C -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            DecoratedText("動動學學", fontSize = 46.sp, color = theme.textPrimary, glow = Color(0xA6FFE27A), glowRadius = 2.dp)
            Text("選一顆星球出發！", fontSize = TextSize.Hint, color = theme.textSecondary)
        }
    }
}

/** B：小旅程卡上方的對話框「今天的小旅程準備好了！」（首頁設計稿）。 */
@Composable
private fun JourneyBubble(modifier: Modifier) {
    val theme = LocalAppTheme.current
    Box(
        modifier
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(2.5.dp, theme.outline, RoundedCornerShape(16.dp))
            .padding(horizontal = Spacing.S, vertical = 4.dp),
    ) {
        Text("今天的小旅程準備好了！", fontSize = 17.sp, color = theme.textPrimary, fontWeight = FontWeight.Black)
    }
}

/**
 * 首頁右上角的貼紙冊圓鈕：圓底＋打開的書。焦點時放大變亮（同齒輪）。短按 OK 就進（給小孩用，不用長按）。
 * 放開才進入，同 [EntryCard]。
 */
@Composable
private fun StickerBookButton(onOpen: () -> Unit, modifier: Modifier) {
    val theme = LocalAppTheme.current
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(Motion.FOCUS_MS), label = "book")
    val color = if (focused) theme.focusFrame else theme.textSecondary
    val accent = stickerStyle(theme).stationColors[0]
    Box(
        modifier
            .size(44.dp)
            .graphicsLayer {
                val s = 1f + 0.2f * f
                scaleX = s
                scaleY = s
                alpha = 0.55f + 0.45f * f
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.remoteKey() != RemoteKey.OK) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> pressed = true
                    KeyEventType.KeyUp -> if (pressed) {
                        pressed = false
                        onOpen()
                    }
                }
                true
            }
            .focusable(),
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(theme.outline.copy(alpha = 0.35f), radius = r)
            // 打開的書：左右兩頁＋中間書脊，左頁上一顆圓點代表貼紙。
            val w = r * 0.62f
            val h = r * 0.9f
            val top = center.y - h / 2
            drawRoundRect(color, topLeft = Offset(center.x - w - r * 0.04f, top), size = Size(w, h), cornerRadius = CornerRadius(r * 0.1f))
            drawRoundRect(color, topLeft = Offset(center.x + r * 0.04f, top), size = Size(w, h), cornerRadius = CornerRadius(r * 0.1f))
            drawCircle(accent, radius = r * 0.17f, center = Offset(center.x - w / 2 - r * 0.04f, center.y - h * 0.08f))
            drawCircle(theme.outline.copy(alpha = 1f), radius = r * 0.08f, center = Offset(center.x + w / 2 + r * 0.04f, center.y - h * 0.08f))
        }
    }
}

/** C：左上角的火箭小女孩。 */
@Composable
private fun RocketGirl() {
    rememberThemeImage("theme/c/guide/rocket_girl.png")?.let {
        Image(it, null, Modifier.offset(x = 150.dp, y = 24.dp).size(79.dp, 96.dp))
    }
}

/**
 * 一張入口卡＋下方焦點標籤。焦點時放大、加粗框／發光，過渡 [Motion.FOCUS_MS]。
 * 卡的樣子照主題：A 米白紙卡＋紙膠帶、B 彩色卡＋白圈深框＋閃光、C 半透明玻璃＋金框＋角落星。
 */
@Composable
private fun EntryCard(
    entry: HomeEntry,
    size: CardSize,
    onClick: () -> Unit,
    journey: Pair<Int, Int>,
    resume: ResumeBadge?,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    val theme = LocalAppTheme.current
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(Motion.FOCUS_MS), label = "focus")
    val i = entry.ordinal
    val shape = RoundedCornerShape(theme.cardRadius)
    val (label, sub0) = ENTRY_LABELS[i]
    val sub = resume?.detail ?: sub0
    val labelSize = if (entry == HomeEntry.JOURNEY) 36.sp else 30.sp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier
                .size(size.widthOf(entry), size.height)
                .graphicsLayer {
                    val s = lerp(1f, theme.focusScale, f)
                    scaleX = s
                    scaleY = s
                    rotationZ = theme.focusRotateDeg * f
                    translationY = theme.focusTranslateY.toPx() * f
                }
                .drawBehind { drawCardBack(theme, i, f) }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .onPreviewKeyEvent { event ->
                    if (event.remoteKey() != RemoteKey.OK) return@onPreviewKeyEvent false
                    // 放開才進入（同 tv-material 按鈕）。要先在這張卡上按下：上一頁按 OK 的放開不算。
                    when (event.type) {
                        KeyEventType.KeyDown -> pressed = true
                        KeyEventType.KeyUp -> if (pressed) {
                            pressed = false
                            onClick()
                        }
                    }
                    true
                }
                .focusable(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(cardFill(theme, i), shape)
                    .border(lerp(theme.borderNormal, theme.borderFocused, f), cardBorder(theme, f), shape)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (entry == HomeEntry.JOURNEY) {
                    // 小旅程卡比較寬：插圖固定 120 dp，下面多一排今天的站點圓。
                    rememberThemeImage(theme.entryArt[i])?.let { Image(it, label, Modifier.size(120.dp)) }
                    Spacer(Modifier.height(Spacing.XS))
                    EntryLabel(label, sub, labelSize)
                    Spacer(Modifier.height(4.dp))
                    StationDots(journey.first, journey.second, size = 12.dp)
                } else {
                    rememberThemeImage(theme.entryArt[i])?.let { Image(it, label, Modifier.fillMaxWidth(0.92f).aspectRatio(1f)) }
                    Spacer(Modifier.height(Spacing.XS))
                    EntryLabel(label, sub, labelSize)
                }
            }
            CardDecoration(theme, f)
            if (resume != null) ResumeRibbon(Modifier.align(Alignment.TopCenter).offset(y = (-14).dp))
        }
        Spacer(Modifier.height(Spacing.XS))
        // 標籤可以比卡寬（A「按 OK 開始 ▶」約 150 dp），但不能把這一欄撐寬：卡改成 120 dp 後，6 張會擠出畫面（模擬器看過）。
        Box(Modifier.width(size.widthOf(entry)).height(TAG_HEIGHT).graphicsLayer { alpha = f }, contentAlignment = Alignment.Center) {
            Box(Modifier.wrapContentWidth(unbounded = true)) { FocusTag(theme) }
        }
    }
}

@Composable
private fun EntryLabel(label: String, sub: String, size: androidx.compose.ui.unit.TextUnit) {
    val theme = LocalAppTheme.current
    when (theme.id) {
        ThemeId.B -> {
            DecoratedText(label, fontSize = size, color = theme.textOnEntry, stroke = theme.outline, strokeWidth = 3.dp)
            DecoratedText(sub, fontSize = 15.sp, color = theme.textOnEntry, stroke = theme.outline, strokeWidth = 1.5.dp)
        }
        ThemeId.C -> {
            DecoratedText(label, fontSize = size, color = theme.textOnEntry)
            Text(sub, fontSize = 15.sp, color = theme.textSecondary, maxLines = 1)
        }
        ThemeId.A -> {
            DecoratedText(label, fontSize = size, color = theme.textPrimary)
            Text(sub, fontSize = 15.sp, color = theme.textSecondary, maxLines = 1)
        }
    }
}

/** 「接著玩」標籤：卡片上緣中間的小牌子（主題焦點色，B 加深框、稍微傾斜）。 */
@Composable
private fun ResumeRibbon(modifier: Modifier) {
    val theme = LocalAppTheme.current
    val (bg, fg) = when (theme.id) {
        ThemeId.A -> theme.focusFrame to Color.White
        ThemeId.B -> Color(0xFFFFE14D) to theme.outline
        ThemeId.C -> theme.focusFrame to Color(0xFF2A1F55)
    }
    Box(
        modifier
            .graphicsLayer { rotationZ = if (theme.id == ThemeId.B) -4f else 0f }
            .background(bg, RoundedCornerShape(50))
            .border(if (theme.id == ThemeId.C) 0.dp else 2.5.dp, theme.outline.copy(alpha = 1f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text("接著玩 ▶", fontSize = 16.sp, color = fg, fontWeight = FontWeight.Black)
    }
}

/** 卡片底色：A 紙卡、B 該入口顏色、C 半透明玻璃。 */
private fun cardFill(theme: AppTheme, i: Int): Color = when (theme.id) {
    ThemeId.B -> theme.entryColors[i]
    else -> theme.surface
}

private fun cardBorder(theme: AppTheme, f: Float): Color = when (theme.id) {
    ThemeId.A -> lerp(theme.outline, theme.focusFrame, f)
    ThemeId.B -> theme.outline
    ThemeId.C -> lerp(theme.outline, theme.focusFrame, f)
}

/**
 * 卡片後面的厚度、投影、發光。Compose 的 shadow() 沒有 spread，所以發光用幾層漸淡的圓角框疊出來。
 * B 焦點：白圈＋深色圈＋黃色光暈（tokens 的 cardFocused）。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCardBack(theme: AppTheme, i: Int, f: Float) {
    val r = theme.cardRadius.toPx()
    fun ring(spread: Float, color: Color, dy: Float = 0f) = drawRoundRect(
        color,
        topLeft = Offset(-spread, -spread + dy),
        size = Size(size.width + spread * 2, size.height + spread * 2),
        cornerRadius = CornerRadius(r + spread),
    )
    when (theme.id) {
        ThemeId.B -> {
            if (f > 0f) {
                drawGlow(r, 9.5.dp.toPx(), 30.dp.toPx(), theme.focusGlow, 0.55f * f)
                ring(9.5.dp.toPx(), theme.entryShade[i].copy(alpha = f), 8.dp.toPx())
                ring(9.5.dp.toPx(), theme.outline.copy(alpha = f))
                ring(6.dp.toPx(), theme.focusFrame.copy(alpha = f))
            }
            ring(0f, theme.entryShade[i], 7.dp.toPx())
        }
        ThemeId.A -> {
            ring(0f, Color(0x1F785A3C), 5.dp.toPx())
            if (f > 0f) for (k in 1..4) ring(k * 3.dp.toPx(), theme.focusGlow.copy(alpha = 0.10f * f), 12.dp.toPx())
        }
        // C 的卡是半透明玻璃，光暈只能畫在卡外，不然會透進卡裡。
        ThemeId.C -> if (f > 0f) drawGlow(r, 2.dp.toPx(), 26.dp.toPx(), theme.focusGlow, 0.9f * f)
    }
}

/**
 * 卡片外的光暈：從 [inner] 往外到 [inner]＋[width]，一圈圈細框、越外越淡。只畫在卡外。
 * Compose 的 shadow() 沒有 spread，也不能指定顏色漸層，所以用細框疊。
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlow(radius: Float, inner: Float, width: Float, color: Color, alpha: Float) {
    val steps = 14
    val step = width / steps
    for (k in 0 until steps) {
        val spread = inner + step * (k + 0.5f)
        val a = alpha * (1f - k / steps.toFloat()) * (1f - k / steps.toFloat()) * 0.35f
        drawRoundRect(
            color.copy(alpha = color.alpha * a),
            topLeft = Offset(-spread, -spread),
            size = Size(size.width + spread * 2, size.height + spread * 2),
            cornerRadius = CornerRadius(radius + spread),
            style = androidx.compose.ui.graphics.drawscope.Stroke(step * 1.05f),
        )
    }
}

/** 焦點裝飾：A 兩條紙膠帶、B 三顆閃光、C 右上角星星。跟著焦點淡入。 */
@Composable
private fun CardDecoration(theme: AppTheme, f: Float) {
    if (f <= 0f) return
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = f }) {
        when (theme.id) {
            ThemeId.A -> rememberThemeImage("theme/a/guide/tape.png")?.let {
                Image(it, null, Modifier.offset(x = (-14).dp, y = (-10).dp).size(75.dp, 22.dp).graphicsLayer { rotationZ = -6f })
                Image(it, null, Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-10).dp).size(75.dp, 22.dp).graphicsLayer { rotationZ = 8f })
            }
            ThemeId.B -> {
                rememberThemeImage("theme/b/guide/sparkle_yellow.png")?.let {
                    Image(it, null, Modifier.offset(x = (-22).dp, y = (-26).dp).size(40.dp))
                    Image(it, null, Modifier.align(Alignment.TopEnd).offset(x = 22.dp, y = (-20).dp).size(34.dp))
                }
                rememberThemeImage("theme/b/guide/sparkle_white.png")?.let {
                    Image(it, null, Modifier.align(Alignment.BottomStart).offset(x = (-18).dp, y = 10.dp).size(28.dp))
                }
            }
            ThemeId.C -> rememberThemeImage("theme/c/guide/focus_star.png")?.let {
                Image(it, null, Modifier.align(Alignment.TopEnd).offset(x = 20.dp, y = (-20).dp).size(50.dp))
            }
        }
    }
}

/** 焦點卡下方標籤：B 黃色貼紙「OK 出發！」、A 橘色「按 OK 開始 ▶」、C 金色「OK 出發 ▶」。 */
@Composable
private fun FocusTag(theme: AppTheme) {
    val (bg, fg) = when (theme.id) {
        ThemeId.A -> theme.focusFrame to Color.White
        ThemeId.B -> Color(0xFFFFE14D) to theme.outline
        ThemeId.C -> theme.focusFrame to Color(0xFF2A1F55)
    }
    Box(
        Modifier
            .graphicsLayer { rotationZ = if (theme.id == ThemeId.B) -3f else 0f }
            .background(bg, RoundedCornerShape(50))
            .border(if (theme.id == ThemeId.B) 2.5.dp else 0.dp, theme.outline, RoundedCornerShape(50))
            .padding(horizontal = Spacing.S, vertical = 2.dp),
    ) {
        Text(theme.focusTag, fontSize = 18.sp, color = fg, fontWeight = FontWeight.Black)
    }
}
