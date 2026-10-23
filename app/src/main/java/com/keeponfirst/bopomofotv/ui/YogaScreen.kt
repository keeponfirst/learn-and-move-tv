package com.keeponfirst.bopomofotv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
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
 */
@Composable
fun YogaPickScreen(choices: List<YogaChoice>, onStart: (YogaChoice) -> Unit, onExit: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val choice = choices[index]
    // 封面用第一個不是「高山」的動作：每套都從高山暖身，用第一個會每套封面都一樣。
    val image = rememberAssetImage((choice.program.poses.firstOrNull { it.id != "mountain" } ?: choice.program.poses.first()).image)
    BackHandler { onExit() }

    RemoteKeyBox(
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
                if (image != null) {
                    Image(image, choice.nameZh, Modifier.size(imageW, imageW * 9f / 16f), contentScale = ContentScale.Fit)
                    Spacer(Modifier.height(Spacing.S))
                }
                val left = if (index > 0) "‹  " else "    "
                val right = if (index < choices.lastIndex) "  ›" else "    "
                Text("$left${choice.nameZh}$right", fontSize = if (image != null) TextSize.Display else TextSize.Hero, color = SymbolColor)
                Text("${index + 1} / ${choices.size}", fontSize = TextSize.Info, color = SubtleColor)
            }
            Text(YOGA_PICK_HINT, Modifier.align(Alignment.BottomEnd), fontSize = TextSize.KeyHint, color = SubtleColor)
        }
    }
}

/** 瑜伽頁右下角的按鍵提示（給大人看）。 */
private const val YOGA_KEY_HINT = "OK 暫停／繼續　→ 下一個　返回 回首頁"

/**
 * 瑜伽（跟著做）：開場 → 每個姿勢「口令 → 倒數」→ 做完進 [onDone]。規則在 [YogaRules]。
 * 確定鍵暫停／繼續，右鍵跳下一個，返回鍵回首頁。其他方向鍵不理會。不偵測動作、不計分。
 * 暫停會停掉語音；繼續時，口令段重播口令，倒數段接著倒數。離開 App 自動暫停。
 * 缺音：AudioPlayer 打不開檔會直接呼叫 onDone，等於口令立即播完，照樣倒數。
 */
@Composable
fun YogaScreen(
    choice: YogaChoice,
    player: AudioPlayer,
    onExit: () -> Unit,
    onDone: () -> Unit,
) {
    val program = choice.program
    var session by remember { mutableStateOf(YogaRules.start(program.poses.map { it.seconds }, choice.rounds)) }
    val pose = program.poses[session.poseIndex]

    // 語音：開場與口令段播音，播完才換段。暫停就停音。
    LaunchedEffect(session.phase, session.step, session.paused) {
        val audio = when (session.phase) {
            YogaPhase.INTRO -> program.intro.cueAudio
            YogaPhase.CUE -> pose.cueAudio
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
            session = YogaRules.tick(session)
        }
    }
    // 離開 App 時 MainActivity.onStop 會停音，口令的 onDone 不會來。先暫停，回來按確定鍵重播口令。
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!session.paused && session.phase != YogaPhase.DONE) session = YogaRules.togglePause(session)
    }
    BackHandler { onExit() }

    RemoteKeyBox(
        onKey = { key ->
            when (key) {
                RemoteKey.OK -> session = YogaRules.togglePause(session)
                RemoteKey.RIGHT -> {
                    player.stop()
                    session = YogaRules.next(session)
                }
                else -> Unit
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (session.phase == YogaPhase.INTRO) {
                YogaIntro(choice.nameZh)
            } else {
                YogaPoseFace(pose)
            }
            // 角落文字都放在安全區內。
            Box(Modifier.fillMaxSize().safeArea()) {
                if (session.phase != YogaPhase.INTRO) {
                    val count = "${session.poseIndex + 1} / ${program.poses.size}"
                    val info = if (choice.rounds > 1) "第 ${session.round} 輪　$count" else count
                    Text(info, Modifier.align(Alignment.TopStart), fontSize = TextSize.Info, color = SubtleColor)
                }
                if (session.phase == YogaPhase.COUNTDOWN) {
                    // 最後 3 秒換黃色當提示（目前沒有提示音素材）。
                    val color = if (session.remaining <= 3) SymbolColor else TextColor
                    Text("${session.remaining}", Modifier.align(Alignment.TopEnd), fontSize = TextSize.Hero, color = color)
                }
                // 底部兩行給大人看：安全提醒、按鍵提示。
                Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (session.phase != YogaPhase.INTRO && pose.safety.isNotEmpty()) {
                        Text(pose.safety, fontSize = TextSize.Note, color = SubtleColor)
                    }
                    Text(YOGA_KEY_HINT, fontSize = TextSize.KeyHint, color = SubtleColor)
                }
            }
            PauseOverlay(visible = session.paused)
        }
    }
}

/** 暫停：半透明遮罩蓋住整個畫面，中間大字「暫停」與「按 OK 繼續」。淡入淡出。 */
@Composable
private fun PauseOverlay(visible: Boolean) {
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

/**
 * 姿勢：左邊 16:9 大圖（畫面寬 50%），右邊 nameZh 大字＋nameEn。
 * 圖不在或解不開：不留圖位，nameZh 更大字置中，下面 nameEn。
 */
@Composable
private fun YogaPoseFace(pose: YogaPose) {
    val image = rememberAssetImage(pose.image)
    BoxWithConstraints(Modifier.fillMaxSize().padding(Spacing.L)) {
        // 示範圖是 16:9：寬取畫面 50%，高不超過畫面 80%。正方形小框在電視上太小，孩子看不清。
        val imageW = minOf(maxWidth * 0.5f, maxHeight * 0.8f * 16f / 9f)
        if (image != null) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.L, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(image, pose.nameZh, Modifier.size(imageW, imageW * 9f / 16f), contentScale = ContentScale.Fit)
                // 52 sp：最長「大樹（左腳站）」7 字約 364 dp，加圖 432 dp、間距 48 dp，放得進 864 dp 寬。
                PoseName(pose, zhSize = TextSize.PoseName)
            }
        } else {
            val zhSize = with(LocalDensity.current) { (maxHeight * 0.2f).toSp() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PoseName(pose, zhSize) }
        }
    }
}

@Composable
private fun PoseName(pose: YogaPose, zhSize: TextUnit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(pose.nameZh, fontSize = zhSize, color = SymbolColor, textAlign = TextAlign.Center)
        Text(pose.nameEn, fontSize = TextSize.Caption, lineHeight = TextSize.CaptionLine, color = TextColor, textAlign = TextAlign.Center)
    }
}
