package com.keeponfirst.bopomofotv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.keeponfirst.bopomofotv.audio.AudioPlayer
import com.keeponfirst.bopomofotv.audio.AudioResolver
import com.keeponfirst.bopomofotv.audio.Phrase
import com.keeponfirst.bopomofotv.data.AbcRepository
import com.keeponfirst.bopomofotv.data.AbcWordCard
import com.keeponfirst.bopomofotv.data.Card
import com.keeponfirst.bopomofotv.data.CardRepository
import com.keeponfirst.bopomofotv.data.Decks
import com.keeponfirst.bopomofotv.data.WordsRepository
import com.keeponfirst.bopomofotv.data.YogaChoice
import com.keeponfirst.bopomofotv.data.YogaRepository
import com.keeponfirst.bopomofotv.data.ZhuyinWord
import com.keeponfirst.bopomofotv.record.PracticeStore
import com.keeponfirst.bopomofotv.record.PracticeStore.Deck
import com.keeponfirst.bopomofotv.session.ParentProgress
import com.keeponfirst.bopomofotv.ui.AbcCardScreen
import com.keeponfirst.bopomofotv.ui.BopomofoTheme
import com.keeponfirst.bopomofotv.ui.ParentCardScreen
import com.keeponfirst.bopomofotv.ui.ParentStartScreen
import com.keeponfirst.bopomofotv.ui.DoneScreen
import com.keeponfirst.bopomofotv.ui.HomeScreen
import com.keeponfirst.bopomofotv.ui.SelfPlayCardScreen
import com.keeponfirst.bopomofotv.ui.YogaPickScreen
import com.keeponfirst.bopomofotv.ui.YogaScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var player: AudioPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cards = CardRepository.load(this)
        val words = WordsRepository.loadZhuyin(this)
        val abcDeck = Decks.abc(AbcRepository.load(this), WordsRepository.loadAbc(this))
        val yoga = YogaRepository.load(this)
        val resolver = AudioResolver(AudioPlayer.listAvailable(assets))
        player = AudioPlayer(assets)
        val store = PracticeStore(this)
        setContent {
            BopomofoTheme { BopomofoApp(cards, words, abcDeck, yoga, resolver, player, store) }
        }
    }

    override fun onStop() {
        super.onStop()
        player.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class ParentStart(val progress: ParentProgress) : Screen
    data class Parent(val progress: ParentProgress) : Screen
    data class SelfPlay(val start: Int) : Screen
    data class Abc(val start: Int) : Screen
    data object YogaPick : Screen
    data class Yoga(val choice: YogaChoice) : Screen
    data class Done(val locked: Boolean, val phrase: Phrase) : Screen
    data class YogaDone(val choice: YogaChoice) : Screen
}

@Composable
private fun BopomofoApp(
    cards: List<Card>,
    words: Map<String, List<ZhuyinWord>>,
    abcDeck: List<AbcWordCard>,
    yoga: List<YogaChoice>,
    resolver: AudioResolver,
    player: AudioPlayer,
    store: PracticeStore,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val scope = rememberCoroutineScope()
    val selfDeck = remember(cards, words) { Decks.zhuyin(cards, words) }
    val goHome = {
        player.stop()
        screen = Screen.Home
    }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            onParent = { scope.launch { screen = Screen.ParentStart(store.parentProgress()) } },
            onSelf = {
                scope.launch {
                    screen = Screen.SelfPlay(Decks.resumeIndex(selfDeck, store.position(Deck.SELF_PLAY)) { Decks.position(it) })
                }
            },
            onAbc = {
                if (abcDeck.isNotEmpty()) {
                    scope.launch { screen = Screen.Abc(Decks.resumeIndex(abcDeck, store.position(Deck.ABC)) { Decks.position(it) }) }
                }
            },
            onYoga = { if (yoga.isNotEmpty()) screen = Screen.YogaPick },
        )
        is Screen.ParentStart -> ParentStartScreen(
            cards = cards,
            progress = s.progress,
            onStart = { screen = Screen.Parent(it) },
            onExit = goHome,
        )
        is Screen.Parent -> ParentCardScreen(
            cards = cards,
            words = words,
            progress = s.progress,
            resolver = resolver,
            player = player,
            store = store,
            onEnd = { progress, phrase ->
                // 用 App 層的 scope 存檔：卡片頁的 scope 會隨換頁取消。
                scope.launch { store.saveParentProgress(progress) }
                screen = Screen.Done(locked = false, phrase = phrase)
            },
        )
        // 位置用 App 層的 scope 存：卡片頁的 scope 會隨換頁取消。
        is Screen.SelfPlay -> SelfPlayCardScreen(
            deck = selfDeck,
            start = s.start,
            resolver = resolver,
            player = player,
            onPosition = { scope.launch { store.savePosition(Deck.SELF_PLAY, Decks.position(it)) } },
            onExit = goHome,
            onTimeUp = { screen = Screen.Done(locked = true, phrase = Phrase.DONE) },
        )
        is Screen.Abc -> AbcCardScreen(
            deck = abcDeck,
            start = s.start,
            resolver = resolver,
            player = player,
            onPosition = { scope.launch { store.savePosition(Deck.ABC, Decks.position(it)) } },
            onExit = goHome,
            onTimeUp = { screen = Screen.Done(locked = true, phrase = Phrase.DONE) },
        )
        Screen.YogaPick -> YogaPickScreen(
            choices = yoga,
            onStart = { screen = Screen.Yoga(it) },
            onExit = goHome,
        )
        is Screen.Yoga -> YogaScreen(
            choice = s.choice,
            player = player,
            onExit = goHome,
            onDone = { screen = Screen.YogaDone(s.choice) },
        )
        is Screen.Done -> DoneScreen(
            locked = s.locked,
            onEnter = { player.play(resolver.phrasePaths(s.phrase)) },
            onHome = goHome,
        )
        // 瑜伽結束頁沿用「今天練完了」，改播這套的 done 語音。和自己玩一樣不放按鈕，返回鍵回首頁。
        is Screen.YogaDone -> DoneScreen(
            locked = true,
            onEnter = { player.play(listOf(s.choice.program.done.cueAudio)) },
            onHome = goHome,
        )
    }
}
