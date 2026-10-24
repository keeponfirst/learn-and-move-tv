package com.keeponfirst.bopomofotv.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.keeponfirst.bopomofotv.audio.AudioPlayer
import com.keeponfirst.bopomofotv.record.StickerKind
import com.keeponfirst.bopomofotv.session.ListenQuestion
import com.keeponfirst.bopomofotv.session.ListenRules
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 聽音找符號的一張卡。[key] 是符號或大寫字母（貼紙的項目）；[label] 畫在卡上；
 * [audio] 是題目要播的音（注音＝符號音，ABC＝字母音）；答對時卡翻成 [image]，播 [reveal]（例詞音）。
 */
data class ListenItem(val key: String, val label: String, val image: String?, val audio: List<String>, val reveal: List<String>)

/** 選「注音」或「ABC」頁的按鍵提示。 */
private const val LISTEN_PICK_HINT = "← → 選　OK 開始　返回 回首頁"

/** 聽音找符號：先選注音或 ABC。左右鍵換、OK 開始、返回鍵回首頁。[kinds] 是有題目可出的類別。 */
@Composable
fun ListenPickScreen(kinds: List<StickerKind>, onStart: (StickerKind) -> Unit, onExit: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    BackHandler { onExit() }
    RemoteKeyBox(
        onKey = { key ->
            when (key) {
                RemoteKey.LEFT -> index = (index - 1).coerceAtLeast(0)
                RemoteKey.RIGHT -> index = (index + 1).coerceAtMost(kinds.lastIndex)
                RemoteKey.OK -> onStart(kinds[index])
                else -> Unit
            }
        },
    ) {
        Column(Modifier.fillMaxSize().safeArea(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Speaker(Modifier.size(56.dp), active = false)
                Spacer(Modifier.width(Spacing.S))
                StickerTitle("聽音找符號", 48.sp)
            }
            Spacer(Modifier.height(Spacing.M))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.L)) {
                kinds.forEachIndexed { i, kind ->
                    val (big, small) = if (kind == StickerKind.ZHUYIN) "ㄅㄆㄇ" to "注音" else "ABC" to "英文字母"
                    ChoiceCard(focused = i == index, dimmed = false, width = 260.dp, height = 200.dp) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            SymbolText(big, 72.sp)
                            Spacer(Modifier.height(Spacing.S))
                            Text(small, fontSize = TextSize.Body, color = TextColor, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().safeArea()) { KeyHint(LISTEN_PICK_HINT, Modifier.align(Alignment.BottomEnd)) }
    }
}

/** 遊戲頁的按鍵提示（給大人看）。 */
private const val LISTEN_KEY_HINT = "← → 選　OK 確定　↑ 再聽一次　返回 回首頁"

/**
 * 答對回饋播完、給了貼紙之後，再停多久換下一題（卡翻面、例詞音）。
 * 等「拿到新貼紙」動畫整段播完（原本 2 秒，下一題的題目列會和飛走中的貼紙疊在一起）。
 */
private const val NEXT_QUESTION_MS = STICKER_GAIN_MS.toLong()

/**
 * 聽音找符號：播一個音，從 3 張卡選對的那張。
 * - 左右鍵選卡、OK 作答、上鍵再聽一次、返回鍵回首頁。
 * - 答對：卡翻成例詞圖、播例詞音、主題答對回饋（[FeedbackOverlay]）；回饋播完呼叫 [onCorrect]（給那一項的貼紙，同一項只給一次）。
 *   再停 [NEXT_QUESTION_MS] 換下一題。答對到下一題之間，上方題目列隱藏。
 * - 答錯：那張卡輕輕搖一下、變淡（不打叉、不用紅色），播「再聽一次」＋題目音。變淡的卡跳過、不能再選。
 * - 題目和其他卡一樣眼熟，規則在 [ListenRules]。不記分，右上角只寫答對幾題。
 * - 新卡題（約每 5 題 1 題）：先只放那張新卡（翻面看例詞圖），播符號音＋例詞音＋符號音，播完才放 3 張卡出題。
 *   示範時按鍵不動作（返回鍵除外）。新卡從這題起算看過的，之後也會當其他卡。
 * - 10 分鐘上限同自己玩，到了呼叫 [onTimeUp]。
 * [listenAgain]、[correct] 是固定語句「再聽一次」「答對了」的音檔（沒有就空清單）。
 */
@Composable
fun ListenScreen(
    kind: StickerKind,
    items: List<ListenItem>,
    seen: Set<String>,
    player: AudioPlayer,
    listenAgain: List<String>,
    correct: List<String>,
    onCorrect: (ListenItem) -> Unit,
    onExit: () -> Unit,
    onTimeUp: () -> Unit,
) {
    val byKey = remember(items) { items.associateBy { it.key } }
    val random = remember { Random(SystemClock.elapsedRealtime()) }
    val startedAt = rememberSaveable { SystemClock.elapsedRealtime() }
    // 看過的：進來時的 [seen]，加上這次示範過的新卡。[asked]＝已出到第幾題（從 0 起算）。[intros]＝這次出過幾題新卡題。
    var known by remember { mutableStateOf(seen) }
    var asked by remember { mutableIntStateOf(0) }
    var intros by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf(ListenRules.next(items.map { it.key }, seen, null, random)) }
    var demo by remember { mutableStateOf(false) }
    var focus by remember { mutableIntStateOf(0) }
    val wrong = remember { mutableStateListOf<Int>() }
    var solved by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var celebrate by remember { mutableIntStateOf(0) }
    var replay by remember { mutableIntStateOf(0) }
    var scoreTarget by remember { mutableStateOf<Offset?>(null) }
    val shakes = remember { List(ListenRules.OPTIONS) { Animatable(0f) } }
    var shake by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val q = question
    val target = q?.let { byKey[it.target] }

    // 每出一題：清掉答錯的卡，焦點回中間，播題目音。新卡題先示範，播完才出題。
    LaunchedEffect(q) {
        if (q == null) return@LaunchedEffect
        wrong.clear()
        solved = false
        focus = q.options.size / 2
        val audio = target?.audio.orEmpty()
        if (q.intro) {
            known = known + q.target
            demo = true
            player.play(audio + target?.reveal.orEmpty() + audio) {
                demo = false
                player.play(audio)
            }
        } else {
            player.play(audio)
        }
    }
    // 答對：先播完答對回饋，再給貼紙（兩個動畫不要疊在一起），再停一下換下一題。
    LaunchedEffect(solved) {
        if (!solved || q == null) return@LaunchedEffect
        delay(Motion.FEEDBACK_MS.toLong())
        target?.let(onCorrect)
        delay(NEXT_QUESTION_MS)
        // 換題時同一次就重設：等 LaunchedEffect(q) 才清，新題第一格會沿用上一題的變淡與隱藏。
        asked++
        val next = ListenRules.next(items.map { it.key }, known, q.target, random, asked, intros)
        if (next?.intro == true) intros++
        wrong.clear()
        focus = (next?.options?.size ?: 0) / 2
        demo = next?.intro == true // 新卡題第一格就是示範畫面，不先閃一下 3 張卡。
        question = next
        solved = false // 最後才設：這會取消本 LaunchedEffect。
    }
    LaunchedEffect(shake) {
        val (i, _) = shake ?: return@LaunchedEffect
        val a = shakes[i]
        listOf(-8f, 8f, -6f, 6f, -3f, 0f).forEach { a.animateTo(it, tween(55)) }
    }
    LaunchedEffect(Unit) {
        delay((startedAt + SELF_PLAY_MILLIS - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        onTimeUp()
    }
    BackHandler { onExit() }

    fun move(dir: Int) {
        val n = q?.options?.size ?: return
        var i = focus + dir
        while (i in 0 until n && i in wrong) i += dir
        if (i in 0 until n) focus = i
    }

    RemoteKeyBox(
        onKey = { key ->
            if (q == null || target == null || solved || demo) return@RemoteKeyBox
            when (key) {
                RemoteKey.LEFT -> move(-1)
                RemoteKey.RIGHT -> move(1)
                RemoteKey.UP -> {
                    replay++
                    player.play(target.audio)
                }
                RemoteKey.OK -> {
                    if (focus in wrong) return@RemoteKeyBox
                    if (q.options[focus] == q.target) {
                        solved = true
                        score++
                        celebrate++
                        player.play(correct + target.reveal)
                    } else {
                        wrong += focus
                        shake = focus to (shake?.second ?: 0) + 1
                        replay++
                        player.play(listenAgain + target.audio)
                        // 焦點移到還能選的卡。
                        val before = focus
                        move(1)
                        if (focus == before) move(-1)
                    }
                }
                RemoteKey.DOWN -> Unit
            }
        },
    ) {
        Box(Modifier.fillMaxSize().safeArea()) {
            InfoPill(Modifier.align(Alignment.TopStart)) { ListenHud(if (kind == StickerKind.ZHUYIN) "聽音找符號" else "聽音找字母") }
            InfoPill(Modifier.align(Alignment.TopEnd).onGloballyPositioned { scoreTarget = it.boundsInRoot().center }) { ListenHud("答對 $score 題") }
            Column(Modifier.fillMaxSize().padding(top = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // 答對後（回饋、拿到貼紙）題目列淡出，不和大字疊在一起；下一題出現時再淡入。
                val questionAlpha by animateFloatAsState(if (solved) 0f else 1f, if (solved) snap() else tween(Motion.SHORT_MS), label = "question")
                Row(Modifier.graphicsLayer { alpha = questionAlpha }, verticalAlignment = Alignment.CenterVertically) {
                    Speaker(Modifier.size(52.dp), active = replay > 0, pulse = replay)
                    Spacer(Modifier.width(Spacing.S))
                    StickerTitle(if (demo) "新朋友！聽好囉" else "聽聽看，是哪一個？", 34.sp)
                }
                Spacer(Modifier.height(Spacing.M))
                if (q != null && demo) {
                    ChoiceCard(focused = true, dimmed = false, width = 260.dp, height = 284.dp) {
                        target?.let { ListenCardFace(it, revealed = true) }
                    }
                } else if (q != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.L)) {
                        q.options.forEachIndexed { i, key ->
                            val item = byKey[key]
                            val isAnswer = solved && key == q.target
                            ChoiceCard(
                                focused = i == focus && !solved || isAnswer,
                                dimmed = i in wrong || (solved && !isAnswer),
                                // 高 230：卡底約 390 dp，左下角的導遊（頂約 423 dp）不會壓到第一張卡。
                                width = 210.dp,
                                height = 230.dp,
                                modifier = Modifier.graphicsLayer { translationX = shakes[i].value.dp.toPx() },
                            ) {
                                if (item != null) ListenCardFace(item, isAnswer)
                            }
                        }
                    }
                } else {
                    Text("還沒有可以出的題目", fontSize = TextSize.Heading, color = TextColor)
                }
            }
            KeyHint(LISTEN_KEY_HINT, Modifier.align(Alignment.BottomEnd))
        }
        ListenGuide()
        FeedbackOverlay(celebrate, target?.label.orEmpty(), scoreTarget)
    }
}

@Composable
private fun ListenHud(text: String) {
    val theme = LocalAppTheme.current
    Text(text, fontSize = TextSize.Info, color = if (theme.id == ThemeId.B) theme.hudText else theme.textSecondary, fontWeight = FontWeight.Black)
}

/** 卡面：平常是大字符號；答對翻成例詞圖＋小字符號（沒有圖就只放大字）。 */
@Composable
private fun ListenCardFace(item: ListenItem, revealed: Boolean) {
    val image = if (revealed) item.image?.let { rememberAssetImage(it) } else null
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (image != null) {
            CardImage(image, item.label, 140.dp)
            Spacer(Modifier.height(Spacing.XS))
            SymbolText(item.label, 44.sp)
        } else {
            Box(Modifier.height(150.dp), contentAlignment = Alignment.Center) { SymbolText(item.label, 130.sp) }
        }
    }
}

/**
 * 一張可選的卡（選注音／ABC、遊戲的 3 張卡）。焦點：放大、主題焦點框、光暈（同首頁入口卡）。
 * [dimmed]＝答錯或不是答案，變淡。
 */
@Composable
private fun ChoiceCard(focused: Boolean, dimmed: Boolean, width: Dp, height: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val theme = LocalAppTheme.current
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(Motion.FOCUS_MS), label = "choice")
    val a by animateFloatAsState(if (dimmed) 0.35f else 1f, tween(Motion.SHORT_MS), label = "dim")
    val shape = RoundedCornerShape(theme.cardRadius)
    val fill = if (theme.id == ThemeId.C) Color(0x33FFFFFF) else theme.surface
    Box(
        modifier
            .size(width, height)
            .graphicsLayer {
                val s = 1f + (theme.focusScale - 1f) * f
                scaleX = s
                scaleY = s
                alpha = a
            }
            .drawBehind { if (f > 0f) drawGlow(theme.cardRadius.toPx(), 2.dp.toPx(), 24.dp.toPx(), theme.focusGlow, 0.9f * f) }
            .background(fill, shape)
            .border(androidx.compose.ui.unit.lerp(theme.borderNormal, theme.borderFocused, f), androidx.compose.ui.graphics.lerp(theme.outline, theme.focusFrame, f), shape)
            .padding(Spacing.XS),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 大喇叭：主題符號色的喇叭＋聲波。[pulse] 每次再聽一次加 1，聲波亮一下。 */
@Composable
internal fun Speaker(modifier: Modifier, active: Boolean, pulse: Int = 0) {
    val theme = LocalAppTheme.current
    val glow = remember { Animatable(0f) }
    LaunchedEffect(pulse) {
        if (pulse == 0) return@LaunchedEffect
        glow.snapTo(1f)
        glow.animateTo(0f, tween(600))
    }
    val body = if (theme.id == ThemeId.C) Color(0xFFFFD66B) else theme.symbol
    val wave = androidx.compose.ui.graphics.lerp(if (theme.id == ThemeId.C) Color.White else theme.outline.copy(alpha = 1f), theme.focusFrame, if (active) glow.value else 0f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.08f, h * 0.36f)
            lineTo(w * 0.28f, h * 0.36f)
            lineTo(w * 0.52f, h * 0.14f)
            lineTo(w * 0.52f, h * 0.86f)
            lineTo(w * 0.28f, h * 0.64f)
            lineTo(w * 0.08f, h * 0.64f)
            close()
        }
        drawPath(path, body)
        drawPath(path, if (theme.id == ThemeId.C) Color.White else theme.outline.copy(alpha = 1f), style = Stroke(2.5.dp.toPx()))
        val stroke = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round)
        listOf(0.16f, 0.30f).forEach { r ->
            drawArc(wave, -50f, 100f, false, Offset(w * 0.52f - w * r, h / 2 - w * r), androidx.compose.ui.geometry.Size(w * r * 2, w * r * 2), style = stroke)
        }
    }
}

/** 導遊站在左下角。B、C 用主題的小女孩；A 用頭像。 */
@Composable
private fun ListenGuide() {
    val theme = LocalAppTheme.current
    Box(Modifier.fillMaxSize().safeArea()) {
        when (theme.id) {
            ThemeId.A -> rememberThemeImage("theme/a/guide/avatar_head.png")?.let { Image(it, null, Modifier.align(Alignment.BottomStart).offset(y = (-8).dp).size(69.dp, 63.dp)) }
            ThemeId.B -> rememberThemeImage("theme/b/guide/guide_girl.png")?.let { Image(it, null, Modifier.align(Alignment.BottomStart).size(74.dp, 90.dp)) }
            ThemeId.C -> rememberThemeImage("theme/c/guide/guide_girl.png")?.let { Image(it, null, Modifier.align(Alignment.BottomStart).size(74.dp, 90.dp)) }
        }
    }
}
