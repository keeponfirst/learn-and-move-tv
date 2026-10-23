package com.keeponfirst.bopomofotv

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.keeponfirst.bopomofotv.audio.AudioPlayer
import com.keeponfirst.bopomofotv.audio.AudioResolver
import com.keeponfirst.bopomofotv.audio.Phrase
import com.keeponfirst.bopomofotv.data.AbcRepository
import com.keeponfirst.bopomofotv.data.AbcWordCard
import com.keeponfirst.bopomofotv.data.Card
import com.keeponfirst.bopomofotv.data.CardRepository
import com.keeponfirst.bopomofotv.data.Decks
import com.keeponfirst.bopomofotv.data.StickerCatalog
import com.keeponfirst.bopomofotv.data.WordsRepository
import com.keeponfirst.bopomofotv.data.YogaChoice
import com.keeponfirst.bopomofotv.data.YogaProgram
import com.keeponfirst.bopomofotv.data.YogaRepository
import com.keeponfirst.bopomofotv.data.ZhuyinWord
import com.keeponfirst.bopomofotv.record.PracticeStore
import com.keeponfirst.bopomofotv.record.PracticeStore.Deck
import com.keeponfirst.bopomofotv.record.StickerId
import com.keeponfirst.bopomofotv.record.StickerKind
import com.keeponfirst.bopomofotv.record.ThemeStore
import com.keeponfirst.bopomofotv.session.ParentProgress
import com.keeponfirst.bopomofotv.session.JourneyDay
import com.keeponfirst.bopomofotv.session.JourneyRules
import com.keeponfirst.bopomofotv.ui.AbcCardScreen
import com.keeponfirst.bopomofotv.ui.BopomofoTheme
import com.keeponfirst.bopomofotv.ui.ParentCardScreen
import com.keeponfirst.bopomofotv.ui.ParentStartScreen
import com.keeponfirst.bopomofotv.ui.DoneScreen
import com.keeponfirst.bopomofotv.ui.HomeEntry
import com.keeponfirst.bopomofotv.ui.HomeScreen
import com.keeponfirst.bopomofotv.ui.JourneyScreen
import com.keeponfirst.bopomofotv.ui.SelfPlayCardScreen
import com.keeponfirst.bopomofotv.ui.SettingsScreen
import com.keeponfirst.bopomofotv.ui.StationCelebration
import com.keeponfirst.bopomofotv.ui.StickerBookScreen
import com.keeponfirst.bopomofotv.ui.StickerGain
import com.keeponfirst.bopomofotv.ui.StickerGainOverlay
import com.keeponfirst.bopomofotv.ui.ThemeId
import com.keeponfirst.bopomofotv.ui.Themes
import com.keeponfirst.bopomofotv.ui.YogaPickScreen
import com.keeponfirst.bopomofotv.ui.YogaScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

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
        val themeStore = ThemeStore(this)
        setContent {
            // 主題設定從 DataStore 讀，讀到之前先畫空白，避免先閃一下預設主題再換。
            var themeId by remember { mutableStateOf<ThemeId?>(null) }
            LaunchedEffect(Unit) { themeId = themeStore.theme() }
            val scope = rememberCoroutineScope()
            themeId?.let { id ->
                BopomofoTheme(Themes.of(id)) {
                    BopomofoApp(cards, words, abcDeck, yoga, resolver, player, store, themeId = id) { picked ->
                        themeId = picked
                        scope.launch { themeStore.save(picked) }
                    }
                }
            }
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
    /** 小旅程地圖。[startedAt] 是這次從首頁進旅程的時間，10 分鐘上限從這裡算。 */
    data class Journey(val day: JourneyDay, val startedAt: Long, val celebrate: StationCelebration? = null) : Screen
    /** 小旅程的第 [index] 站。 */
    data class Station(val day: JourneyDay, val index: Int, val startedAt: Long) : Screen
    data object StickerBook : Screen
    data class ParentStart(val progress: ParentProgress) : Screen
    data class Parent(val progress: ParentProgress) : Screen
    data class SelfPlay(val start: Int) : Screen
    data class Abc(val start: Int) : Screen
    data object YogaPick : Screen
    data class Yoga(val choice: YogaChoice) : Screen
    data class Done(val locked: Boolean, val phrase: Phrase) : Screen
    data class YogaDone(val choice: YogaChoice) : Screen
    data object Settings : Screen
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
    themeId: ThemeId,
    onTheme: (ThemeId) -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    // 首頁最後聚焦的入口。回首頁時焦點回到這顆，不跳回第一顆。第一次打開在小旅程。
    var homeFocus by rememberSaveable { mutableStateOf(HomeEntry.JOURNEY) }
    val scope = rememberCoroutineScope()
    val selfDeck = remember(cards, words) { Decks.zhuyin(cards, words) }
    val catalog = remember(selfDeck, abcDeck, yoga) { StickerCatalog.build(selfDeck, abcDeck, yoga) }
    val poses = remember(yoga) { yoga.flatMap { it.program.poses }.associateBy { it.id } }
    // 拿到貼紙的動畫蓋在目前畫面上。[gainSeq] 每次加 1，同一張再拿也會重播。
    var gain by remember { mutableStateOf<StickerGain?>(null) }
    var gainSeq by remember { mutableIntStateOf(0) }
    val goHome = {
        player.stop()
        screen = Screen.Home
    }
    val timeUp = { screen = Screen.Done(locked = true, phrase = Phrase.DONE) }

    fun showGain(id: StickerId, isNew: Boolean) {
        catalog[id]?.let { gain = StickerGain(it, isNew, ++gainSeq) }
    }

    // 自己玩、ABC、瑜伽給的貼紙：同一項只給一次，第一次拿到才播動畫。
    // 用 App 層的 scope：卡片頁的 scope 會隨換頁取消。
    fun award(id: StickerId) {
        scope.launch { if (store.award(id)) showGain(id, isNew = true) }
    }

    // 走完小旅程的一站：給這站的貼紙、存進度，回地圖播慶祝（慶祝播完才播拿到貼紙）。
    fun completeStation(s: Screen.Station) {
        scope.launch {
            val isNew = store.award(s.day.stations[s.index])
            val day = JourneyRules.complete(s.day, s.index)
            store.saveJourney(day)
            player.stop()
            screen = Screen.Journey(day, s.startedAt, StationCelebration(s.index, isNew))
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = screen) {
            Screen.Home -> {
                var journeyDone by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) { journeyDone = store.journey()?.takeIf { it.date == LocalDate.now() }?.done ?: 0 }
                HomeScreen(
                    focus = homeFocus,
                    onFocus = { homeFocus = it },
                    onJourney = {
                        scope.launch { screen = Screen.Journey(store.journeyToday(catalog.journeyPool()), SystemClock.elapsedRealtime()) }
                    },
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
                    onStickerBook = { screen = Screen.StickerBook },
                    onSettings = { screen = Screen.Settings },
                    journeyDone = journeyDone,
                )
            }
            is Screen.Journey -> key(s) {
                JourneyScreen(
                    day = s.day,
                    catalog = catalog,
                    celebrate = s.celebrate,
                    startedAt = s.startedAt,
                    onStart = { screen = Screen.Station(s.day, it, s.startedAt) },
                    onBook = { screen = Screen.StickerBook },
                    onCelebrated = { showGain(s.day.stations[it.index], it.isNew) },
                    onExit = goHome,
                    onTimeUp = timeUp,
                )
            }
            // 每站重用原本的畫面，只放這一項：注音、字母各看完它的例詞卡，瑜伽做一個動作。返回鍵回首頁，這站下次重來。
            is Screen.Station -> key(s) {
                val id = s.day.stations[s.index]
                val mode = "小旅程・第 ${s.index + 1} 站"
                when (id.kind) {
                    StickerKind.ZHUYIN -> SelfPlayCardScreen(
                        deck = remember { selfDeck.filter { it.card.symbol == id.key } },
                        start = 0,
                        resolver = resolver,
                        player = player,
                        onPosition = {},
                        onExit = goHome,
                        onTimeUp = timeUp,
                        mode = mode,
                        startedAt = s.startedAt,
                        onDeckEnd = { completeStation(s) },
                    )
                    StickerKind.ABC -> AbcCardScreen(
                        deck = remember { abcDeck.filter { it.card.upper == id.key } },
                        start = 0,
                        resolver = resolver,
                        player = player,
                        onPosition = {},
                        onExit = goHome,
                        onTimeUp = timeUp,
                        mode = mode,
                        startedAt = s.startedAt,
                        onDeckEnd = { completeStation(s) },
                    )
                    StickerKind.YOGA -> {
                        val first = yoga.first().program
                        val choice = remember { YogaChoice("journey", mode, YogaProgram(first.intro, listOf(poses.getValue(id.key)), first.done), 1) }
                        YogaScreen(choice = choice, player = player, onExit = goHome, onDone = { completeStation(s) }, skipIntro = true)
                    }
                }
            }
            Screen.StickerBook -> {
                val owned by produceState<Set<StickerId>?>(null) { value = store.stickers() }
                owned?.let { StickerBookScreen(catalog = catalog, owned = it, onExit = goHome) }
            }
            Screen.Settings -> SettingsScreen(current = themeId, onApply = onTheme, onExit = goHome)
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
                onTimeUp = timeUp,
                onSymbolDone = { award(StickerId(StickerKind.ZHUYIN, it.card.symbol)) },
            )
            is Screen.Abc -> AbcCardScreen(
                deck = abcDeck,
                start = s.start,
                resolver = resolver,
                player = player,
                onPosition = { scope.launch { store.savePosition(Deck.ABC, Decks.position(it)) } },
                onExit = goHome,
                onTimeUp = timeUp,
                onLetterDone = { award(StickerId(StickerKind.ABC, it.card.upper)) },
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
                onPoseDone = { award(StickerId(StickerKind.YOGA, it.id)) },
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
        gain?.let { g ->
            key(g.seq) { StickerGainOverlay(g) { if (gain?.seq == g.seq) gain = null } }
        }
    }
}
