package com.keeponfirst.bopomofotv.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.tv.material3.Text
import com.keeponfirst.bopomofotv.audio.AudioPlayer
import com.keeponfirst.bopomofotv.audio.AudioResolver
import com.keeponfirst.bopomofotv.audio.Phrase
import com.keeponfirst.bopomofotv.data.AbcWordCard
import com.keeponfirst.bopomofotv.data.Card
import com.keeponfirst.bopomofotv.data.Decks
import com.keeponfirst.bopomofotv.data.ZhuyinCard
import com.keeponfirst.bopomofotv.data.ZhuyinWord
import com.keeponfirst.bopomofotv.record.PracticeResult
import com.keeponfirst.bopomofotv.record.PracticeStore
import com.keeponfirst.bopomofotv.session.Cue
import com.keeponfirst.bopomofotv.session.PARENT_MILLIS
import com.keeponfirst.bopomofotv.session.ParentProgress
import com.keeponfirst.bopomofotv.session.ParentRules
import com.keeponfirst.bopomofotv.session.Phase
import com.keeponfirst.bopomofotv.session.Step
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 自己玩單次上限 10 分鐘。 */
const val SELF_PLAY_MILLIS = 10 * 60 * 1000L

/** 大人陪換卡的提示，放在三顆提示按鈕旁。 */
private const val PARENT_KEY_HINT = "← → 換卡"

/** 大人陪卡片頁要畫的一張卡。[phase]、[pos] 一變就算換卡，播滑入轉場。 */
private data class ParentFace(val phase: Phase, val pos: Int, val card: Card, val word: ZhuyinWord)

/**
 * 大人陪：一次一個單元，新卡段 → 複習段（規則在 [ParentRules]）。
 * 左右換卡、確定播音、上鍵記答對、下鍵記再練。下方畫出三顆對應的提示，按到的那顆會亮。
 * 右上角顯示段落、第幾張與星星列；按答對播回饋動畫（[FeedbackOverlay]）。
 *
 * [onEnd] 帶下次要存的進度與結束頁要播的語句：
 * 做完 → `p_done`；10 分鐘到 → `p_rest`；返回鍵 → `p_done`。後兩者記住沒做完的卡。
 */
@Composable
fun ParentCardScreen(
    cards: List<Card>,
    words: Map<String, List<ZhuyinWord>>,
    progress: ParentProgress,
    resolver: AudioResolver,
    player: AudioPlayer,
    store: PracticeStore,
    onEnd: (ParentProgress, Phrase) -> Unit,
) {
    val bySymbol = remember(cards) { cards.associateBy { it.symbol } }
    var session by remember { mutableStateOf(ParentRules.start(cards, progress)) }
    var lastMark by remember { mutableStateOf<PracticeResult?>(null) }
    val startedAt = rememberSaveable { SystemClock.elapsedRealtime() }
    val scope = rememberCoroutineScope()
    val card = bySymbol.getValue(session.current)
    // 每個符號每出現一次換下一個例詞。起點加上練過的次數，每次大人陪不會都從第 1 個詞開始。
    val seen = remember { mutableMapOf<String, Int>() }
    val appearance = remember(session.phase, session.pos) {
        val n = seen[session.current] ?: 0
        seen[session.current] = n + 1
        n
    }
    val word = Decks.rotate(Decks.zhuyinWords(card, words), appearance + progress.attempts)
    var forward by remember { mutableStateOf(true) }
    var celebrate by remember { mutableIntStateOf(0) }
    var listenFlash by remember { mutableIntStateOf(0) }
    var listenActive by remember { mutableStateOf(false) }
    var starTarget by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(session.phase, session.pos) { lastMark = null }
    // 「再聽一次」沒有要記錄的結果，按了只亮一下。
    LaunchedEffect(listenFlash) {
        if (listenFlash == 0) return@LaunchedEffect
        listenActive = true
        delay(400)
        listenActive = false
    }
    LaunchedEffect(Unit) {
        delay((startedAt + PARENT_MILLIS - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        onEnd(ParentRules.paused(progress, session), Phrase.REST)
    }
    BackHandler { onEnd(ParentRules.paused(progress, session), Phrase.DONE) }

    fun apply(step: Step) {
        session = step.session
        when (step.cue) {
            Cue.NEXT -> player.play(resolver.phrasePaths(Phrase.NEXT))
            Cue.PRACTICE_AGAIN -> player.play(resolver.phrasePaths(Phrase.PRACTICE_AGAIN))
            Cue.DONE -> onEnd(ParentRules.completed(progress, session, cards.map { it.unit }), Phrase.DONE)
            null -> player.play(resolver.phrasePaths(Phrase.LISTEN_AGAIN) + resolver.cardPaths(card, word))
        }
    }

    val phaseLabel = if (session.phase == Phase.NEW) "新卡" else "複習"
    // 按到的提示：已記錄的結果一直亮到換卡；再聽一次只亮一下。
    val active = when {
        listenActive -> ParentButton.LISTEN
        lastMark == PracticeResult.CORRECT -> ParentButton.CORRECT
        lastMark == PracticeResult.AGAIN -> ParentButton.AGAIN
        else -> null
    }

    RemoteKeyBox(
        onKey = { key ->
            when (key) {
                RemoteKey.LEFT -> {
                    player.stop()
                    forward = false
                    session = ParentRules.previous(session)
                }
                RemoteKey.RIGHT -> {
                    forward = true
                    apply(ParentRules.next(session))
                }
                RemoteKey.OK -> {
                    listenFlash++
                    player.play(resolver.cardPaths(card, word))
                }
                RemoteKey.UP -> {
                    scope.launch { store.record(card.symbol, PracticeResult.CORRECT) }
                    session = ParentRules.markCorrect(session)
                    lastMark = PracticeResult.CORRECT
                    celebrate++
                    player.play(resolver.phrasePaths(Phrase.CORRECT))
                }
                RemoteKey.DOWN -> {
                    scope.launch { store.record(card.symbol, PracticeResult.AGAIN) }
                    forward = true
                    apply(ParentRules.markAgain(session))
                    lastMark = PracticeResult.AGAIN
                }
            }
        },
    ) {
        CardPage(
            mode = "大人陪・第 ${session.unit} 單元",
            counter = "$phaseLabel　${cardCounter(session.pos, session.queue.size)}",
            stars = StarRow(session.pos, session.queue.size, currentDone = lastMark == PracticeResult.CORRECT),
            celebrate = celebrate,
            onStarTarget = { starTarget = it },
            buttons = active,
            showButtons = true,
        ) {
            CardSlide(ParentFace(session.phase, session.pos, card, word), forward) { CardFace(it.card, it.word) }
        }
        Box(Modifier.fillMaxSize().safeArea()) {
            KeyHint(PARENT_KEY_HINT, Modifier.align(Alignment.BottomEnd).padding(bottom = 18.dp))
        }
        FeedbackOverlay(celebrate, card.symbol, starTarget)
    }
}

/** 大人陪選單元頁的按鍵提示。 */
private const val PARENT_START_HINT = "← → 選單元　OK 開始"

/**
 * 大人陪開頭：選「從第幾單元開始」。預設是上次的單元。左右選、確定開始、返回回首頁。
 * 選的和上次同一個單元，接著上次的進度；選別的單元，從那個單元的第一張開始。
 */
@Composable
fun ParentStartScreen(
    cards: List<Card>,
    progress: ParentProgress,
    onStart: (ParentProgress) -> Unit,
    onExit: () -> Unit,
) {
    val units = remember(cards) { cards.map { it.unit }.distinct().sorted() }
    var unit by remember { mutableIntStateOf(progress.unit.takeIf { it in units } ?: units.first()) }
    val symbols = cards.filter { it.unit == unit }.joinToString("　") { it.symbol }
    val resume = unit == progress.unit && progress.resumeQueue.isNotEmpty()
    BackHandler { onExit() }

    RemoteKeyBox(
        onKey = { key ->
            val i = units.indexOf(unit)
            when (key) {
                RemoteKey.LEFT -> unit = units[(i - 1).coerceAtLeast(0)]
                RemoteKey.RIGHT -> unit = units[(i + 1).coerceAtMost(units.lastIndex)]
                RemoteKey.OK -> onStart(if (unit == progress.unit) progress else ParentProgress(unit = unit))
                else -> Unit
            }
        },
    ) {
        Column(
            Modifier.fillMaxSize().padding(Spacing.L),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("從第幾單元開始？", fontSize = TextSize.Heading, color = TextColor)
            Text("‹  第 $unit 單元  ›", fontSize = TextSize.Display, color = SymbolColor)
            Text(symbols, fontSize = TextSize.Word, color = TextColor)
            Text(if (resume) "接著上次沒做完的卡" else " ", fontSize = TextSize.Info, color = SubtleColor)
        }
        Box(Modifier.fillMaxSize().safeArea()) {
            KeyHint(PARENT_START_HINT, Modifier.align(Alignment.BottomEnd))
        }
    }
}

/**
 * 自己玩：任一方向鍵或確定鍵都換下一張並播音。幼兒還分不清方向鍵，任何鍵都有反應比較不挫折（Hourcade 2004）。
 * 37 個符號照教育部順序，每個符號的例詞連續出完再換下一個；最後一張之後回到 ㄅ。
 * 從 [start] 開始。每換一張呼叫 [onPosition]，下次從這張接著。
 * 不記分。10 分鐘到呼叫 [onTimeUp]。返回鍵回首頁。
 */
@Composable
fun SelfPlayCardScreen(
    deck: List<ZhuyinCard>,
    start: Int,
    resolver: AudioResolver,
    player: AudioPlayer,
    onPosition: (ZhuyinCard) -> Unit,
    onExit: () -> Unit,
    onTimeUp: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(start) }
    val startedAt = rememberSaveable { SystemClock.elapsedRealtime() }
    val item = deck[step % deck.size]

    LaunchedEffect(step) {
        onPosition(item)
        player.play(resolver.cardPaths(item.card, item.word))
    }
    LaunchedEffect(Unit) {
        delay((startedAt + SELF_PLAY_MILLIS - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        onTimeUp()
    }
    BackHandler { onExit() }

    RemoteKeyBox(onKey = { step++ }) {
        CardPage(mode = "自己玩", counter = cardCounter(step % deck.size, deck.size)) {
            CardSlide(item, forward = true) { CardFace(it.card, it.word) }
        }
    }
}

/**
 * ABC：玩法照自己玩。任一方向鍵換下一張，進卡自動播字母音。
 * 每個字母的例詞連續出完再換下一個字母。從 [start] 開始，每換一張呼叫 [onPosition]。
 * 確定鍵播「字母音 → 單字音」。不記分。10 分鐘到呼叫 [onTimeUp]。返回鍵回首頁。
 */
@Composable
fun AbcCardScreen(
    deck: List<AbcWordCard>,
    start: Int,
    resolver: AudioResolver,
    player: AudioPlayer,
    onPosition: (AbcWordCard) -> Unit,
    onExit: () -> Unit,
    onTimeUp: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(start) }
    val startedAt = rememberSaveable { SystemClock.elapsedRealtime() }
    val item = deck[step % deck.size]

    LaunchedEffect(step) {
        onPosition(item)
        player.play(resolver.letterPaths(item.card))
    }
    LaunchedEffect(Unit) {
        delay((startedAt + SELF_PLAY_MILLIS - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        onTimeUp()
    }
    BackHandler { onExit() }

    RemoteKeyBox(
        onKey = { key ->
            if (key == RemoteKey.OK) player.play(resolver.letterWordPaths(item.card, item.word)) else step++
        },
    ) {
        CardPage(mode = "ABC", counter = cardCounter(step % deck.size, deck.size)) {
            CardSlide(item, forward = true) { AbcCardFace(it.card, it.word) }
        }
    }
}

/**
 * 取得焦點並接收遙控器按鍵的全螢幕容器。背景是主題的 [backdrop]（預設卡片頁背景）。
 * 只吃方向鍵與確定鍵；返回鍵交給 BackHandler，音量等其他鍵交給系統。
 */
@Composable
fun RemoteKeyBox(onKey: (RemoteKey) -> Unit, backdrop: String = LocalAppTheme.current.bgCard, content: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                val key = event.remoteKey() ?: return@onPreviewKeyEvent false
                if (event.isFirstPress()) onKey(key)
                true
            }
            .focusable(),
    ) {
        ThemeBackdrop(backdrop)
        content()
    }
}
