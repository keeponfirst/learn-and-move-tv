package com.keeponfirst.bopomofotv.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Text
import com.keeponfirst.bopomofotv.audio.AudioPlayer
import com.keeponfirst.bopomofotv.audio.BrushVoice
import com.keeponfirst.bopomofotv.data.GiantLines
import com.keeponfirst.bopomofotv.session.GIANT_CALLS
import com.keeponfirst.bopomofotv.session.GiantCall
import com.keeponfirst.bopomofotv.session.GiantCue
import com.keeponfirst.bopomofotv.session.GiantRound
import com.keeponfirst.bopomofotv.session.GiantRules
import com.keeponfirst.bopomofotv.session.GiantScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

private const val TAG = "GiantScreen"

/** 巨人頁的按鍵提示（給大人看）。 */
private const val GIANT_HINT = "OK 暫停／繼續　返回 回首頁"
private const val GIANT_HINT_END = "OK 再玩一輪　返回 回首頁"
private const val GIANT_HINT_REST = "休息好了按 OK　返回 回首頁"

/** 換畫面的淡入淡出與巨人放大、螞蟻拉近的時間（0.4 秒以上，不閃爍）。 */
private const val SCENE_MS = 450

/**
 * 巨人小螞蟻：進來就自動開始，孩子不用按鍵。開場與安全提醒 → 規則 → 練習一次 → 正式 10 個口令 → 結尾。
 * 結尾停住，大人按 OK 再玩一輪（換下一組順序，開場只重播空間檢查與安全提醒）。連玩滿 3 輪改播 ga_rest，停在休息頁。
 * OK 暫停／繼續（繼續時從這一段的開頭重播），返回鍵回首頁。離開 App 自動暫停。不計分、不判對錯、不給貼紙。
 * 時間表在 [GiantRules]：每段等台詞播完才接下一段。語音排隊不疊播（[BrushVoice]）。
 * 動畫只有淡入淡出與 0.45 秒的縮放，倒數圓環連續變短，沒有閃爍。
 */
@Composable
fun GiantScreen(lines: Map<String, String>, player: AudioPlayer, onExit: () -> Unit) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    val art = remember(theme.id) { GiantArt(theme) }
    // 先把這套的圖都解碼進快取，換畫面時不會卡一下。
    art.all.forEach { rememberThemeImage(it) }
    // 每句多長：讀音檔長度；沒有音檔（改用系統語音）用估計。
    val speech by produceState<Map<String, Long>?>(null) {
        value = withContext(Dispatchers.IO) {
            lines.mapValues { (id, text) -> AudioPlayer.durationMs(context.assets, "${BrushVoice.GIANT_DIR}/$id.m4a") ?: GiantLines.estimateMillis(text) }
        }
    }
    BackHandler { onExit() }
    val durations = speech
    if (durations == null) {
        ThemeBackdrop(art.bg)
        return
    }
    GiantPlay(art, lines, durations, player)
}

@Composable
private fun GiantPlay(art: GiantArt, lines: Map<String, String>, speech: Map<String, Long>, player: AudioPlayer) {
    val context = LocalContext.current
    val voice = remember { BrushVoice(context, player, lines, BrushVoice.GIANT_DIR) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }
    // 這次進來第幾輪（0 起算），也是之前玩完幾輪。
    var round by remember { mutableIntStateOf(0) }
    val schedule = remember(round) {
        GiantRules.schedule(if (round == 0) GiantRound.FIRST else GiantRound.AGAIN, GiantRules.setFor(round), round) { speech[it] ?: GiantLines.estimateMillis(lines[it].orEmpty()) }
            .also { s -> Log.i(TAG, "第 ${round + 1} 輪時間表 ${s.size} 段、共 ${GiantRules.totalMillis(s)} ms：" + s.joinToString { "${it.atMillis}+${it.slotMillis} ${it.lines}" }) }
    }
    val total = GiantRules.totalMillis(schedule)
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var next by remember { mutableIntStateOf(0) }
    val finished = elapsed >= total

    // 計時：用畫面的格時間累加，暫停或離開 App 就不加。到時間的段照 [GiantRules.due] 播。
    LaunchedEffect(round, paused) {
        if (paused) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (elapsed < total) {
            val now = withFrameMillis { it }
            elapsed = minOf(total, elapsed + now - last)
            last = now
            val (cue, i) = GiantRules.due(schedule, next, elapsed)
            next = i
            cue?.let {
                Log.i(TAG, "第 ${round + 1} 輪 ${it.atMillis} ms ${it.scene} ${it.lines}")
                voice.say(it.lines)
            }
        }
    }
    LaunchedEffect(paused) { if (paused) voice.stop() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!finished) paused = true
    }

    val index = GiantRules.cueAt(schedule, elapsed).coerceAtLeast(0)
    val cue = schedule[index]
    // 假口令之後那句「你有停住嗎？」，泡泡裡還是剛才那個東西。
    val lastCall = schedule.take(index + 1).lastOrNull { it.call != null }?.call
    RemoteKeyBox(
        backdrop = art.bg,
        onKey = { key ->
            if (key == RemoteKey.OK) {
                when {
                    finished -> {
                        Log.i(TAG, "按 OK 再玩一輪（第 ${round + 2} 輪）")
                        elapsed = 0L
                        next = 0
                        round++
                    }
                    // 暫停會清掉播到一半的句子，繼續時回到這一段的開頭重播。
                    paused -> {
                        val (e, i) = GiantRules.resume(schedule, elapsed)
                        elapsed = e
                        next = i
                        paused = false
                    }
                    else -> paused = true
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            GiantStage(art, cue, lastCall, lines)
            Box(Modifier.fillMaxSize().safeArea()) {
                if (cue.call != null) {
                    BeatRing(remaining = cue.atMillis + cue.slotMillis - elapsed, slot = cue.slotMillis, art = art, modifier = Modifier.align(Alignment.TopEnd))
                }
                if (cue.scene != GiantScene.REST) {
                    Footprints(GiantRules.footprints(schedule, elapsed), art, Modifier.align(Alignment.BottomEnd))
                }
                val hint = when {
                    finished && cue.scene == GiantScene.REST -> GIANT_HINT_REST
                    finished -> GIANT_HINT_END
                    else -> GIANT_HINT
                }
                // A 的提示沒有底色，壓在草叢、巨人鞋子上看不清楚；墊一層米白膠囊（同示意畫面）。
                val hintBg = if (LocalAppTheme.current.id == ThemeId.A) Color(0xEBFFFCF5) else Color.Transparent
                KeyHint(hint, Modifier.align(Alignment.BottomCenter).background(hintBg, RoundedCornerShape(50)).padding(horizontal = 12.dp))
            }
            PauseOverlay(visible = paused)
        }
    }
}

/**
 * 巨人小螞蟻的插圖（theme/<a|b|c>/giant/，擺位照示意畫面）。三套主題各一套。
 * 位置用示意畫面（1920×1080 px）÷ 2 換成 dp。
 */
internal class GiantArt(theme: AppTheme) {
    val dir = "theme/${themeDir(theme)}/giant"
    val bg = "$dir/ga_bg.png"
    val giantIdle = "$dir/ga_giant_idle.png"
    val giantReach = "$dir/ga_giant_reach.png"
    val giantCrouch = "$dir/ga_giant_crouch.png"
    val antIdle = "$dir/ga_ant_idle.png"
    val antCloseup = "$dir/ga_ant_closeup.png"
    val antHighfive = "$dir/ga_ant_highfive.png"
    val antGiggle = "$dir/ga_ant_giggle.png"
    fun kid(pose: String) = "$dir/ga_kid_$pose.png"
    val bubble = "$dir/ga_fake_bubble.png"
    fun fakeIcon(call: GiantCall) = "$dir/ga_fake_${call.name.lowercase()}.png"
    val footOn = "$dir/ga_footprint_on.png"
    val footOff = "$dir/ga_footprint_off.png"
    val rest = "$dir/ga_rest.png"

    val all = listOf(bg, giantIdle, giantReach, giantCrouch, antIdle, antCloseup, antHighfive, antGiggle, bubble, footOn, footOff, rest) +
        KID_POSES.map(::kid) + GiantCall.entries.filter { it.fake }.map(::fakeIcon)

    /** 標題字：A 深棕、B 黃字深框、C 金字發光（同示意畫面）。 */
    val titleColor = when (theme.id) {
        ThemeId.A -> theme.textPrimary
        ThemeId.B -> Color(0xFFFFE14D)
        ThemeId.C -> Color(0xFFFFE27A)
    }
    val titleStroke = if (theme.id == ThemeId.B) theme.outline else null
    val titleGlow = if (theme.id == ThemeId.C) Color(0xB2FFE27A) else null

    /** 倒數圓環：亮色、底圈、數字。 */
    val ringLit = when (theme.id) {
        ThemeId.A -> theme.focusFrame
        ThemeId.B -> Color(0xFFFFE14D)
        ThemeId.C -> Color(0xFFFFE27A)
    }
    val ringTrack = when (theme.id) {
        ThemeId.A -> Color(0xFFEADFCC)
        ThemeId.B -> Color(0x80FFFFFF)
        ThemeId.C -> Color(0x33FFFFFF)
    }
    val ringText = if (theme.id == ThemeId.A) theme.textPrimary else Color.White
    /** 圓環裡的底：B 背景右上有白雲，白字會看不見（模擬器看過），墊主題的深色資訊底。 */
    val ringFill = if (theme.id == ThemeId.A) Color(0xE6FFFCF5) else theme.hud

    companion object {
        val KID_POSES = listOf("ready", "tiptoe", "tall_easy", "squat", "freeze")
    }
}

/** 左半邊放哪個大角色。 */
private enum class Big { GIANT_IDLE, GIANT_REACH, ANT_CLOSEUP, BUBBLE, HIGHFIVE, REST }

/**
 * 一段的畫面：左邊大角色（巨人、大螞蟻、問號泡泡、擊掌），右邊示範小孩，上方口令大字或台詞字幕。
 * 練習的口令加「練習」小牌子。
 */
@Composable
private fun GiantStage(art: GiantArt, cue: GiantCue, lastCall: GiantCall?, lines: Map<String, String>) {
    val call = cue.call
    val big = when (cue.scene) {
        GiantScene.RULE_GIANT, GiantScene.RULE_EASY -> Big.GIANT_REACH
        GiantScene.RULE_ANT -> Big.ANT_CLOSEUP
        GiantScene.RULE_FAKE, GiantScene.FAKE_AFTER -> Big.BUBBLE
        GiantScene.CALL -> when {
            call == GiantCall.GIANT -> Big.GIANT_REACH
            call == GiantCall.ANT -> Big.ANT_CLOSEUP
            else -> Big.BUBBLE
        }
        GiantScene.END_HIGHFIVE, GiantScene.END_BREATHE, GiantScene.END_ASK -> Big.HIGHFIVE
        GiantScene.REST -> Big.REST
        else -> Big.GIANT_IDLE
    }
    val kid = when (cue.scene) {
        GiantScene.RULE_GIANT -> "tiptoe"
        GiantScene.RULE_EASY -> "tall_easy"
        GiantScene.RULE_ANT -> "squat"
        GiantScene.RULE_FAKE, GiantScene.FAKE_AFTER -> "freeze"
        GiantScene.CALL -> when (call) {
            GiantCall.GIANT -> "tiptoe"
            GiantCall.ANT -> "squat"
            else -> "freeze"
        }
        GiantScene.END_HIGHFIVE, GiantScene.END_BREATHE, GiantScene.END_ASK, GiantScene.REST -> null
        else -> "ready"
    }
    // 泡泡裡的小圖：假口令與「你有停住嗎？」放那個東西；規則說明只放問號泡泡。
    val icon = when (cue.scene) {
        GiantScene.CALL -> call?.takeIf { it.fake }
        GiantScene.FAKE_AFTER -> lastCall?.takeIf { it.fake }
        else -> null
    }
    // 小螞蟻站在草地上：開場、規則、假口令時揮手；「你有停住嗎？」抱肚子笑。
    val smallAnt = when (big) {
        Big.GIANT_IDLE, Big.BUBBLE -> if (cue.scene == GiantScene.FAKE_AFTER) art.antGiggle else art.antIdle
        else -> null
    }

    Box(Modifier.fillMaxSize()) {
        Crossfade(big to icon, animationSpec = tween(SCENE_MS), label = "big") { (b, ic) ->
            Box(Modifier.fillMaxSize()) {
                when (b) {
                    // 巨人頭頂超出畫面上緣（照插圖設計）；變巨人時從腳底往上放大。
                    Big.GIANT_IDLE -> Placed(art.giantIdle, 30f, 0f, 400f)
                    Big.GIANT_REACH -> Placed(art.giantReach, 30f, 0f, 400f, grow = 0.9f, origin = TransformOrigin(0.5f, 1f))
                    // 變小螞蟻：草叢裡的大螞蟻從遠處拉近。
                    Big.ANT_CLOSEUP -> Placed(art.antCloseup, 60f, 75f, 450f, grow = 0.8f, origin = TransformOrigin(0.5f, 1f))
                    Big.BUBBLE -> {
                        Placed(art.bubble, 130f, 105f, 360f, grow = 0.85f)
                        // 泡泡 480×400 縮成 360 dp（0.75 倍），小圖中心在泡泡的 (240, 186)。
                        ic?.let { Placed(art.fakeIcon(it), 130f + 240 * 0.75f - 90f, 105f + 186 * 0.75f - 90f, 180f, grow = 0.85f) }
                    }
                    // 擊掌：巨人手掌 (906,694)，螞蟻放大後舉起的手掌碰到它（示意畫面的擺法）。
                    Big.HIGHFIVE -> {
                        Placed(art.giantCrouch, 60f, 0f, 520f)
                        Placed(art.antHighfive, 489.5f, 247f, 234f)
                    }
                    Big.REST -> Placed(art.rest, 180f, 125f, 600f)
                }
            }
        }
        Crossfade(smallAnt, animationSpec = tween(SCENE_MS), label = "ant") { a ->
            // 螞蟻 360×460，腳底 y=430；寬 150 dp 時腳底和示範小孩一樣在 y≈455 dp。
            a?.let { Placed(it, 440f, 276f, 150f) }
        }
        Crossfade(kid, animationSpec = tween(SCENE_MS), label = "kid") { k ->
            // 示範小孩 5 張腳底都在 y=680，換圖不會上下跳。
            k?.let { Placed(art.kid(it), 590f, 115f, 235f) }
        }
        Crossfade(cue, animationSpec = tween(SCENE_MS), label = "title") { c -> GiantTitle(c, art, lines) }
    }
}

/** 上方的字：口令段是口令大字（練習加小牌子）；結尾與休息是一句短標題；其他段是台詞字幕。 */
@Composable
private fun GiantTitle(cue: GiantCue, art: GiantArt, lines: Map<String, String>) {
    val theme = LocalAppTheme.current
    val big = when (cue.scene) {
        GiantScene.CALL, GiantScene.FASTER, GiantScene.GO -> lines[cue.lines.single()].orEmpty()
        GiantScene.END_HIGHFIVE -> "擊掌！"
        GiantScene.END_BREATHE -> "慢慢吸氣、吐氣"
        GiantScene.END_ASK -> "你聽得好仔細！"
        GiantScene.REST -> "休息一下，喝口水"
        else -> null
    }
    // 字放在巨人右邊（示意畫面 x≈900 px）、倒數圓環左邊，不蓋到示範小孩舉高的手。
    Box(Modifier.fillMaxSize().safeArea()) {
        Column(Modifier.padding(start = 380.dp, top = 12.dp).widthIn(max = 400.dp)) {
            if (big != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cue.call != null && cue.callIndex < 0) {
                        Box(Modifier.background(theme.focusFrame, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 2.dp)) {
                            Text("練習", fontSize = 22.sp, color = if (theme.id == ThemeId.A) Color.White else Color(0xFF2A1F55), fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    DecoratedText(
                        big,
                        // 「準備好了嗎？開始！」9 個字：40 sp 才放得進 400 dp 一行。
                        fontSize = when {
                            cue.scene == GiantScene.CALL -> 60.sp
                            big.length > 8 -> 40.sp
                            else -> 46.sp
                        },
                        color = art.titleColor,
                        stroke = art.titleStroke,
                        strokeWidth = 6.dp,
                        glow = art.titleGlow,
                        glowRadius = 3.dp,
                    )
                }
            }
            // 開場、規則、「你有停住嗎？」、結尾：台詞字幕（給大人看，孩子聽語音）。
            if (cue.scene !in setOf(GiantScene.CALL, GiantScene.FASTER, GiantScene.GO)) {
                // 結尾那句開頭就是標題「你聽得好仔細！」，字幕不重複。
                GiantCaption(cue.lines.joinToString("") { lines[it].orEmpty() }.removePrefix(big.orEmpty()))
            }
        }
    }
}

/** 台詞字幕：B 深色膠囊白字，A 白底深字，C 深藍底白字（同刷牙）。開場 3 句約 46 字，20 sp 一行約 18 字，最多 4 行。 */
@Composable
private fun GiantCaption(text: String) {
    val theme = LocalAppTheme.current
    val (bg, fg) = when (theme.id) {
        ThemeId.B -> theme.hud to theme.hudText
        ThemeId.C -> Color(0xE61A1B4A) to Color.White
        ThemeId.A -> Color(0xF2FFFCF5) to theme.textPrimary
    }
    Box(
        Modifier
            .padding(top = 8.dp)
            .background(bg, RoundedCornerShape(18.dp))
            .border(2.dp, if (theme.id == ThemeId.C) Color(0xFFFFE27A) else theme.outline.copy(alpha = 1f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 20.sp, color = fg, fontWeight = FontWeight.Black, maxLines = 4, lineHeight = 27.sp)
    }
}

/**
 * 一張插圖，左上角 ([x], [y]) dp、寬 [w] dp，高照原圖比例。
 * [grow] < 1：出現時從這個倍率放大到 1（[SCENE_MS]），以 [origin] 為中心。
 */
@Composable
private fun Placed(path: String, x: Float, y: Float, w: Float, grow: Float = 1f, origin: TransformOrigin = TransformOrigin.Center) {
    val img = rememberThemeImage(path) ?: return
    val scale = remember { Animatable(grow) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(SCENE_MS)) }
    Image(
        img,
        null,
        Modifier
            .offset(x.dp, y.dp)
            .width(w.dp)
            .aspectRatio(img.width.toFloat() / img.height)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                transformOrigin = origin
            },
        contentScale = ContentScale.Fit,
    )
}

/** 右上倒數圓環：這個口令還剩幾秒。圓環連續變短，數字每秒換一次。 */
@Composable
private fun BeatRing(remaining: Long, slot: Long, art: GiantArt, modifier: Modifier) {
    val left = remaining.coerceIn(0, slot)
    Box(modifier.size(75.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 8.dp.toPx()
            val inset = w / 2
            val arcSize = Size(size.width - w, size.height - w)
            drawCircle(art.ringFill, size.minDimension / 2 - w / 2)
            drawArc(art.ringTrack, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(art.ringLit, -90f, 360f * left / slot, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(ceil(left / 1000.0).toInt().toString(), fontSize = 30.sp, color = art.ringText, fontWeight = FontWeight.Black)
    }
}

/** 右下 10 個小腳印：正式口令開始一個就亮一個。練習不亮。 */
@Composable
private fun Footprints(lit: Int, art: GiantArt, modifier: Modifier) {
    val on = rememberThemeImage(art.footOn)
    val off = rememberThemeImage(art.footOff)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(GIANT_CALLS) { i ->
            (if (i < lit) on else off)?.let { Image(it, null, Modifier.size(26.dp)) }
        }
    }
}
