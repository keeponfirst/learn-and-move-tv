package com.keeponfirst.bopomofotv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Text
import com.keeponfirst.bopomofotv.audio.AudioPlayer
import com.keeponfirst.bopomofotv.data.YogaChoice
import com.keeponfirst.bopomofotv.data.YogaPose
import com.keeponfirst.bopomofotv.session.YogaPhase
import com.keeponfirst.bopomofotv.session.YogaRules
import kotlinx.coroutines.delay

/** 選一套頁的按鍵提示（給大人看）。 */
private const val YOGA_PICK_HINT = "← → 換一套　OK 開始　返回 回首頁"

/**
 * 選一套：一次一張大卡（第一個動作的圖＋套名大字），左右鍵換、確定鍵開始。照 [ParentStartScreen] 的做法。
 * 圖不在就只放套名。到頭不繞回。
 * [minutes]：每套（id）約幾分鐘，畫在圖的右下角（時鐘＋「約 N 分鐘」）；還沒算好或沒有就不畫。
 */
@Composable
fun YogaPickScreen(choices: List<YogaChoice>, onStart: (YogaChoice) -> Unit, onExit: () -> Unit, minutes: Map<String, Int> = emptyMap()) {
    var index by remember { mutableIntStateOf(0) }
    val choice = choices[index]
    // 封面用第一個不是「高山」的動作：每套都從高山暖身，用第一個會每套封面都一樣。
    val image = rememberAssetImage((choice.program.poses.firstOrNull { it.id != "mountain" } ?: choice.program.poses.first()).image)
    BackHandler { onExit() }

    RemoteKeyBox(
        backdrop = LocalAppTheme.current.bgYoga,
        onKey = { key ->
            when (key) {
                RemoteKey.LEFT -> index = (index - 1).coerceAtLeast(0)
                RemoteKey.RIGHT -> index = (index + 1).coerceAtMost(choices.lastIndex)
                RemoteKey.OK -> onStart(choice)
                else -> Unit
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(Spacing.L)) {
            val imageW = minOf(maxWidth * 0.5f, maxHeight * 0.55f * 16f / 9f)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                val length = minutes[choice.id]
                if (image != null) {
                    Box {
                        PoseStage(image, choice.nameZh, imageW)
                        length?.let { DurationPill(it, Modifier.align(Alignment.BottomEnd).padding(bottom = 40.dp, end = 24.dp)) }
                    }
                    Spacer(Modifier.height(Spacing.XS))
                }
                val left = if (index > 0) "‹  " else "    "
                val right = if (index < choices.lastIndex) "  ›" else "    "
                Text("$left${choice.nameZh}$right", fontSize = if (image != null) TextSize.Display else TextSize.Hero, color = SymbolColor)
                if (image == null) length?.let { DurationPill(it, Modifier) }
                Text("${index + 1} / ${choices.size}", fontSize = TextSize.Info, color = SubtleColor)
            }
            KeyHint(YOGA_PICK_HINT, Modifier.align(Alignment.BottomEnd))
        }
    }
}

/** 套的長度標示：小時鐘＋「約 N 分鐘」。B 深色膠囊白字；A、C 白底（C 深藍底）深框。 */
@Composable
private fun DurationPill(minutes: Int, modifier: Modifier) {
    val theme = LocalAppTheme.current
    val (bg, fg) = when (theme.id) {
        ThemeId.B -> theme.hud to theme.hudText
        ThemeId.C -> Color(0xE61A1B4A) to Color.White
        ThemeId.A -> Color.White to theme.textPrimary
    }
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .background(bg, shape)
            .border(2.dp, if (theme.id == ThemeId.C) Color(0xFFFFE27A) else theme.outline.copy(alpha = 1f), shape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = 2.2.dp.toPx()
            drawCircle(fg, radius = size.minDimension / 2 - w / 2, style = Stroke(w))
            drawLine(fg, center, Offset(center.x, size.height * 0.24f), w, StrokeCap.Round)
            drawLine(fg, center, Offset(size.width * 0.72f, center.y), w, StrokeCap.Round)
        }
        Spacer(Modifier.width(6.dp))
        Text("約 $minutes 分鐘", fontSize = 20.sp, color = fg, fontWeight = FontWeight.Black)
    }
}

/** 瑜伽頁右下角的按鍵提示（給大人看）。有簡單版的動作前面多一段「↓ 簡單版」或「↑ 一般版」。 */
private const val YOGA_KEY_HINT = "OK 暫停／繼續　→ 下一個　返回 回首頁"

/**
 * 瑜伽（跟著做）：開場 → 每個姿勢「口令 → 倒數」→ 做完進 [onDone]。規則在 [YogaRules]。
 * 確定鍵暫停／繼續，右鍵跳下一個，返回鍵回首頁。左鍵不理會。不偵測動作、不計分。
 * 暫停會停掉語音；繼續時，口令段重播口令，倒數段接著倒數。離開 App 自動暫停。
 * 缺音：AudioPlayer 打不開檔會直接呼叫 onDone，等於口令立即播完，照樣倒數。
 * 一個動作倒數到 0（不是按右鍵跳過）呼叫 [onPoseDone]（給貼紙）。
 * 小旅程的動一動站：[choice] 只放一個動作，[skipIntro]＝不播開場，直接進口令。
 *
 * 簡單版：有簡單版的動作，下鍵換簡單版的圖與口令，上鍵換回一般版。
 * 口令段切換會重播那一版的口令；倒數段只換圖和字，倒數不重來。
 * [easyPoses] 是孩子上次選簡單版的動作 id，每切一次呼叫 [onEasy] 記住。
 * 從上次繼續：[startStep] 不是 null 就不播開場，直接到那個動作。每換一個動作呼叫 [onStep]。
 */
@Composable
fun YogaScreen(
    choice: YogaChoice,
    player: AudioPlayer,
    onExit: () -> Unit,
    onDone: () -> Unit,
    skipIntro: Boolean = false,
    onPoseDone: (YogaPose) -> Unit = {},
    easyPoses: Set<String> = emptySet(),
    onEasy: (YogaPose, Boolean) -> Unit = { _, _ -> },
    startStep: Int? = null,
    onStep: (Int) -> Unit = {},
) {
    val program = choice.program
    var session by remember {
        val start = YogaRules.start(program.poses.map { it.seconds }, choice.rounds)
        mutableStateOf(
            when {
                startStep != null -> YogaRules.resumeAt(start, startStep)
                skipIntro -> YogaRules.cueFinished(start)
                else -> start
            },
        )
    }
    var easy by remember { mutableStateOf(easyPoses) }
    val pose = program.poses[session.poseIndex]
    val useEasy = pose.hasEasy && pose.id in easy

    LaunchedEffect(session.step, session.phase == YogaPhase.INTRO) {
        if (session.phase != YogaPhase.INTRO && session.phase != YogaPhase.DONE) onStep(session.step)
    }
    // 語音：開場與口令段播音，播完才換段。暫停就停音。口令段換版本（useEasy）會重播那一版。
    LaunchedEffect(session.phase, session.step, session.paused, useEasy) {
        val audio = when (session.phase) {
            YogaPhase.INTRO -> program.intro.cueAudio
            YogaPhase.CUE -> if (useEasy) pose.cueAudioEasy ?: pose.cueAudio else pose.cueAudio
            else -> null
        }
        when {
            session.phase == YogaPhase.DONE -> onDone()
            session.paused -> player.stop()
            audio != null -> player.play(listOf(audio)) { session = YogaRules.cueFinished(session) }
        }
    }
    // 倒數：每秒一次。換段、換姿勢或暫停都會取消重來。
    LaunchedEffect(session.phase, session.step, session.paused) {
        if (session.phase != YogaPhase.COUNTDOWN || session.paused) return@LaunchedEffect
        while (true) {
            delay(1000)
            val before = session
            session = YogaRules.tick(before)
            // 倒數剩 1 秒再過一秒＝這個動作做完了。
            if (before.phase == YogaPhase.COUNTDOWN && !before.paused && before.remaining == 1) onPoseDone(program.poses[before.poseIndex])
        }
    }
    // 離開 App 時 MainActivity.onStop 會停音，口令的 onDone 不會來。先暫停，回來按確定鍵重播口令。
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!session.paused && session.phase != YogaPhase.DONE) session = YogaRules.togglePause(session)
    }
    BackHandler { onExit() }

    RemoteKeyBox(
        backdrop = LocalAppTheme.current.bgYoga,
        onKey = { key ->
            when (key) {
                RemoteKey.OK -> session = YogaRules.togglePause(session)
                RemoteKey.RIGHT -> {
                    player.stop()
                    session = YogaRules.next(session)
                }
                RemoteKey.DOWN, RemoteKey.UP -> {
                    val want = key == RemoteKey.DOWN
                    if (session.phase != YogaPhase.INTRO && pose.hasEasy && want != useEasy) {
                        easy = if (want) easy + pose.id else easy - pose.id
                        onEasy(pose, want)
                    }
                }
                else -> Unit
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (session.phase == YogaPhase.INTRO) {
                YogaIntro(choice.nameZh)
            } else {
                YogaPoseFace(pose, session.phase == YogaPhase.COUNTDOWN, session.remaining, useEasy)
            }
            // 角落文字都放在安全區內。
            Box(Modifier.fillMaxSize().safeArea()) {
                if (session.phase != YogaPhase.INTRO) {
                    Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                        val count = "第 ${session.poseIndex + 1} 個／共 ${program.poses.size} 個"
                        InfoPill { YogaPillText(if (choice.rounds > 1) "第 ${session.round} 輪　$count" else count) }
                        Spacer(Modifier.width(Spacing.S))
                        PoseDots(session.poseIndex, program.poses.size, Modifier.weight(1f))
                    }
                }
                // 底部兩行給大人看：安全提醒、按鍵提示。
                Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                    val safety = pose.safetyFor(useEasy)
                    if (session.phase != YogaPhase.INTRO && safety.isNotEmpty()) {
                        Text(safety, fontSize = TextSize.Note, color = TextColor)
                    }
                    val easyHint = when {
                        session.phase == YogaPhase.INTRO || !pose.hasEasy -> ""
                        useEasy -> "↑ 一般版　"
                        else -> "↓ 簡單版　"
                    }
                    KeyHint(easyHint + YOGA_KEY_HINT)
                }
            }
            PauseOverlay(visible = session.paused)
        }
    }
}

@Composable
private fun YogaPillText(text: String) {
    val theme = LocalAppTheme.current
    Text(text, fontSize = TextSize.Info, color = if (theme.id == ThemeId.B) theme.hudText else theme.textSecondary, fontWeight = FontWeight.Black)
}

/**
 * 進度點：做完的打勾、目前這個放大並寫編號、還沒做的空心。點之間用線連起來。
 * 點的大小隨個數縮，11 個時約 24 dp；一排放得下 [count] 個。
 */
@Composable
private fun PoseDots(index: Int, count: Int, modifier: Modifier) {
    val theme = LocalAppTheme.current
    BoxWithConstraints(modifier) {
        val dot = minOf(24.dp, maxWidth / (count * 1.6f))
        val big = dot * 1.5f
        val line = ((maxWidth - big - dot * (count - 1)) / (count - 1).coerceAtLeast(1)).coerceAtLeast(0.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(count) { k ->
                if (k > 0) Box(Modifier.size(line, 3.dp).background(if (k <= index) theme.progressDone else theme.outline.copy(alpha = 0.5f)))
                when {
                    k < index -> Canvas(Modifier.size(dot)) {
                        drawCircle(theme.progressDone)
                        drawCircle(theme.outline, style = Stroke(1.5.dp.toPx()))
                        val path = Path().apply {
                            moveTo(size.width * 0.28f, size.height * 0.52f)
                            lineTo(size.width * 0.45f, size.height * 0.68f)
                            lineTo(size.width * 0.74f, size.height * 0.36f)
                        }
                        drawPath(path, Color.White, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    k == index -> Box(
                        Modifier.size(big).background(theme.progressCurrent, CircleShape).border(2.dp, theme.outline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${k + 1}", fontSize = with(LocalDensity.current) { (big * 0.5f).toSp() }, color = theme.outline.copy(alpha = 1f), fontWeight = FontWeight.Black)
                    }
                    else -> Box(Modifier.size(dot).background(theme.progressTodo, CircleShape).border(1.5.dp, theme.outline, CircleShape))
                }
            }
        }
    }
}

/** 暫停：半透明遮罩蓋住整個畫面，中間大字「暫停」與「按 OK 繼續」。淡入淡出。刷牙頁也用。 */
@Composable
internal fun PauseOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.SHORT_MS)),
        exit = fadeOut(tween(Motion.SHORT_MS)),
    ) {
        Column(
            Modifier.fillMaxSize().background(ScrimColor),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("暫停", fontSize = TextSize.Hero, color = SymbolColor)
            Spacer(Modifier.height(Spacing.S))
            Text("按 OK 繼續", fontSize = TextSize.Heading, color = TextColor)
        }
    }
}

@Composable
private fun YogaIntro(name: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, fontSize = TextSize.Hero, color = SymbolColor)
        Spacer(Modifier.height(Spacing.M))
        Text("請大人陪在旁邊", fontSize = TextSize.Headline, color = TextColor)
    }
}

/** 倒數圓環直徑。 */
private val RING_SIZE = 160.dp

/**
 * 姿勢：左邊 16:9 大圖站在主題的舞台上（B 黃色圓台、C 紫色圓台、A 白框），右邊大圓環倒數＋動作名＋口令。
 * [counting]＝倒數中；口令段圓環是滿的，中間寫「聽口令」。
 * 圖不在或解不開：不留圖位，圓環與名字置中。
 */
@Composable
private fun YogaPoseFace(pose: YogaPose, counting: Boolean, remaining: Int, easy: Boolean) {
    val image = rememberAssetImage(if (easy) pose.imageEasy ?: pose.image else pose.image)
    val cue = if (easy) pose.cueTextEasy ?: pose.cueText else pose.cueText
    // 上面留給進度列，下面留給安全提醒與按鍵提示兩行。
    Box(Modifier.fillMaxSize().safeArea().padding(top = 44.dp, bottom = 64.dp)) {
        if (image != null) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.M, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    PoseStage(image, pose.nameZh, 480.dp)
                    if (pose.hasEasy) EasyToggle(easy, Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-14).dp))
                    if (easy) GoodJobBadge(Modifier.align(Alignment.BottomStart).offset(x = (-10).dp, y = (-30).dp))
                }
                CountdownColumn(pose, counting, remaining, cue)
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CountdownColumn(pose, counting, remaining, cue) }
        }
    }
}

/** 示範圖右上角的切換標籤「一般｜簡單」：目前那格亮（主題焦點色），另一格淡（畫面設計稿）。 */
@Composable
private fun EasyToggle(easy: Boolean, modifier: Modifier) {
    val theme = LocalAppTheme.current
    val on = when (theme.id) {
        ThemeId.A -> theme.focusFrame to Color.White
        ThemeId.B -> Color(0xFFFFE14D) to theme.outline
        ThemeId.C -> theme.focusFrame to Color(0xFF2A1F55)
    }
    val off = (if (theme.id == ThemeId.C) Color(0xCC1A1B4A) else Color.White) to theme.textSecondary
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .background(off.first, shape)
            .border(2.dp, theme.outline.copy(alpha = 1f), shape)
            .padding(3.dp),
    ) {
        listOf("一般" to !easy, "簡單" to easy).forEach { (label, selected) ->
            val (bg, fg) = if (selected) on else Color.Transparent to off.second
            Box(Modifier.background(bg, shape).padding(horizontal = 12.dp, vertical = 2.dp)) {
                Text(label, fontSize = 18.sp, color = fg, fontWeight = FontWeight.Black)
            }
        }
    }
}

/** 簡單版示範圖旁的小貼紙「這樣也很棒！」，讓孩子不覺得是降級。顏色用貼紙的瑜伽色。 */
@Composable
private fun GoodJobBadge(modifier: Modifier) {
    val theme = LocalAppTheme.current
    val style = stickerStyle(theme)
    Box(
        modifier
            .graphicsLayer { rotationZ = -6f }
            .background(style.stationColors[2], RoundedCornerShape(14.dp))
            .border(2.5.dp, if (theme.id == ThemeId.C) Color.White else theme.outline.copy(alpha = 1f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text("這樣也很棒！", fontSize = 20.sp, color = style.pillText, fontWeight = FontWeight.Black)
    }
}

/** 示範圖＋舞台。示範圖是 16:9，寬 480 dp（畫面一半）；正方形小框在電視上太小，孩子看不清。 */
@Composable
private fun PoseStage(image: ImageBitmap, description: String, width: Dp) {
    val theme = LocalAppTheme.current
    val height = width * 9f / 16f
    Box(Modifier.size(width, height + 24.dp)) {
        when (theme.id) {
            ThemeId.A -> Box(
                Modifier
                    .size(width, height)
                    .graphicsLayer { rotationZ = -1.2f }
                    .background(Color.White, RoundedCornerShape(11.dp))
                    .border(theme.borderNormal, theme.outline, RoundedCornerShape(11.dp))
                    .padding(8.dp),
            ) { Image(image, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            // 示範圖是米白底的方圖：放進圓角框，框底下露出舞台（B 黃色圓台、C 紫色圓台）。
            else -> {
                val (stage, shade) = if (theme.id == ThemeId.B) Color(0xFFFFB61E) to Color(0xFFC98400) else Color(0xFF3C3D8A) to Color(0xFF26275E)
                val frame = if (theme.id == ThemeId.B) theme.outline else Color(0xFF5CE1E6)
                Canvas(Modifier.align(Alignment.BottomCenter).size(width * 0.86f, 56.dp)) {
                    val h = size.height - 8.dp.toPx()
                    drawOval(shade, topLeft = Offset(0f, 8.dp.toPx()), size = Size(size.width, h))
                    drawOval(stage, size = Size(size.width, h))
                    drawOval(theme.outline.copy(alpha = 1f), size = Size(size.width, h), style = Stroke(2.5.dp.toPx()))
                }
                val shape = RoundedCornerShape(theme.cardRadius)
                Image(
                    image,
                    description,
                    Modifier
                        .size(width * 0.94f, height * 0.94f)
                        .align(Alignment.TopCenter)
                        .clip(shape)
                        .border(3.dp, frame, shape),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/** 右欄：倒數圓環（剩餘秒數在中間）、動作名、口令文字。最後 3 秒數字換強調色（沒有提示音素材）。 */
@Composable
private fun CountdownColumn(pose: YogaPose, counting: Boolean, remaining: Int, cueText: String) {
    val theme = LocalAppTheme.current
    val fraction = if (counting && pose.seconds > 0) remaining.toFloat() / pose.seconds else 1f
    // 每秒跳一次的數值用 1 秒線性動畫接起來，圓環連續縮短。
    val sweep by animateFloatAsState(fraction, tween(if (counting) 1000 else Motion.SHORT_MS, easing = LinearEasing), label = "ring")
    val ringColor = when (theme.id) {
        ThemeId.A -> theme.progressDone
        ThemeId.B -> Color(0xFFFF9F1C)
        ThemeId.C -> Color(0xFF5CE1E6)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(330.dp)) {
        Box(Modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 18.dp.toPx()
                val arc = Size(size.width - w, size.height - w)
                val tl = Offset(w / 2, w / 2)
                drawArc(theme.outline.copy(alpha = 0.18f), 0f, 360f, false, tl, arc, style = Stroke(w))
                drawArc(ringColor, -90f, 360f * sweep, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (counting) {
                    val color = if (remaining <= 3) theme.symbol else theme.textPrimary
                    DecoratedText(
                        "$remaining",
                        fontSize = 64.sp,
                        color = color,
                        textModifier = Modifier.wrapContentHeight(unbounded = true),
                        stroke = if (theme.id == ThemeId.B) theme.outline else null,
                        strokeWidth = 3.dp,
                        modifier = Modifier.height(70.dp),
                    )
                    Text("秒", fontSize = TextSize.Info, color = theme.textPrimary, fontWeight = FontWeight.Black)
                } else {
                    Text("聽口令", fontSize = TextSize.Heading, color = theme.textPrimary, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(Spacing.XS))
        PoseLabel(pose.nameZh)
        Text(pose.nameEn, fontSize = 20.sp, color = SubtleColor, textAlign = TextAlign.Center)
        if (cueText.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.XS))
            InfoPill {
                Text(
                    cueText,
                    fontSize = 16.sp,
                    color = if (theme.id == ThemeId.B) theme.hudText else theme.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 動作名：B 紅色貼紙白字、C 金字發光、A 深棕字。最長「大樹（左腳站）」7 字，40 sp 約 280 dp。 */
@Composable
private fun PoseLabel(name: String) {
    val theme = LocalAppTheme.current
    when (theme.id) {
        ThemeId.B -> Box(
            Modifier
                .graphicsLayer { rotationZ = -2f }
                .background(Color(0xFFFF6B5B), RoundedCornerShape(12.dp))
                .border(3.dp, theme.outline, RoundedCornerShape(12.dp))
                .padding(horizontal = Spacing.S),
        ) { Text(name, fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Black) }
        ThemeId.C -> DecoratedText(name, fontSize = 40.sp, color = theme.symbol, glow = Color(0xB2FFD65A), glowRadius = 3.dp)
        ThemeId.A -> DecoratedText(name, fontSize = 40.sp, color = theme.textPrimary)
    }
}
