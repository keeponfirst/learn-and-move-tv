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
import androidx.compose.ui.platform.LocalContext
import com.keeponfirst.bopomofotv.audio.AudioPlayer
import com.keeponfirst.bopomofotv.audio.AudioResolver
import com.keeponfirst.bopomofotv.audio.Phrase
import com.keeponfirst.bopomofotv.data.AbcRepository
import com.keeponfirst.bopomofotv.data.AbcWordCard
import com.keeponfirst.bopomofotv.data.Card
import com.keeponfirst.bopomofotv.data.CardRepository
import com.keeponfirst.bopomofotv.data.DeckPosition
import com.keeponfirst.bopomofotv.data.Decks
import com.keeponfirst.bopomofotv.data.StickerCatalog
import com.keeponfirst.bopomofotv.data.StrokeRepository
import com.keeponfirst.bopomofotv.data.StrokeSymbol
import com.keeponfirst.bopomofotv.data.WordsRepository
import com.keeponfirst.bopomofotv.data.YogaChoice
import com.keeponfirst.bopomofotv.data.YogaParser
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
import com.keeponfirst.bopomofotv.session.ListenRules
import com.keeponfirst.bopomofotv.session.ResumeMode
import com.keeponfirst.bopomofotv.session.ResumePoint
import com.keeponfirst.bopomofotv.session.ResumeRules
import com.keeponfirst.bopomofotv.ui.AbcCardScreen
import com.keeponfirst.bopomofotv.ui.BopomofoTheme
import com.keeponfirst.bopomofotv.ui.ParentCardScreen
import com.keeponfirst.bopomofotv.ui.ParentStartScreen
import com.keeponfirst.bopomofotv.ui.DoneScreen
import com.keeponfirst.bopomofotv.ui.HomeEntry
import com.keeponfirst.bopomofotv.ui.HomeScreen
import com.keeponfirst.bopomofotv.ui.JourneyScreen
import com.keeponfirst.bopomofotv.ui.ListenItem
import com.keeponfirst.bopomofotv.ui.ListenPickScreen
import com.keeponfirst.bopomofotv.ui.ListenScreen
import com.keeponfirst.bopomofotv.ui.ResumeBadge
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private lateinit var player: AudioPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cards = CardRepository.load(this)
        val words = WordsRepository.loadZhuyin(this)
        val abcDeck = Decks.abc(AbcRepository.load(this), WordsRepository.loadAbc(this))
        val yoga = YogaRepository.load(this)
        val strokes = StrokeRepository.load(this)
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
                    BopomofoApp(cards, words, abcDeck, yoga, strokes, resolver, player, store, themeId = id) { picked ->
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

/**
 * 首頁「接著玩」要掛在哪張卡、小字寫什麼。上次的位置已經不能用（素材換過、旅程跨日或走完）就回 null，不顯示。
 * 自己玩、ABC 寫上次停的符號或字母；小旅程寫接著第幾站；瑜伽寫這一輪的第幾個動作。
 */
private fun resumeBadge(p: ResumePoint, today: JourneyDay?, yoga: List<YogaChoice>, self: DeckPosition?, abc: DeckPosition?): ResumeBadge? =
    when (p.mode) {
        ResumeMode.SELF -> self?.let { ResumeBadge(HomeEntry.SELF, "上次到 ${it.key}") }
        ResumeMode.ABC -> abc?.let { ResumeBadge(HomeEntry.ABC, "上次到 ${it.key}") }
        ResumeMode.JOURNEY -> today?.takeIf { !it.finished && it.stations.isNotEmpty() }?.let { ResumeBadge(HomeEntry.JOURNEY, "接著第 ${it.done + 1} 站") }
        ResumeMode.YOGA -> yoga.firstOrNull { it.id == p.yogaSet }
            ?.takeIf { p.yogaStep in 0 until it.program.poses.size * it.rounds }
            ?.let { ResumeBadge(HomeEntry.YOGA, "上次第 ${p.yogaStep % it.program.poses.size + 1} 個") }
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
    /** [startStep] 不是 null＝從上次繼續，直接到那個動作。 */
    data class Yoga(val choice: YogaChoice, val startStep: Int? = null) : Screen
    data object ListenPick : Screen
    /** 聽音找符號。[seen] 是孩子看過的符號或字母，題目優先出這些。 */
    data class Listen(val kind: StickerKind, val seen: Set<String>) : Screen
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
    strokes: Map<String, StrokeSymbol>,
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
    val context = LocalContext.current
    // 瑜伽選套卡的「約 N 分鐘」：在背景讀每個口令音檔的長度再算。
    val yogaMinutes by produceState(emptyMap<String, Int>(), yoga) {
        value = withContext(Dispatchers.IO) {
            val ms = mutableMapOf<String, Long?>()
            yoga.associate { c -> c.id to YogaParser.minutes(c) { p -> ms.getOrPut(p) { AudioPlayer.durationMs(context.assets, p) } } }
        }
    }
    // 孩子選了簡單版的動作（每個動作分開記）。
    var yogaEasy by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) { yogaEasy = store.yogaEasy() }
    // 聽音找符號的題目：有音檔的符號、字母才能出題。
    val listenItems = remember(selfDeck, abcDeck) {
        mapOf(
            StickerKind.ZHUYIN to selfDeck.groupBy { it.card.symbol }.map { (symbol, cs) ->
                val first = cs.first()
                ListenItem(symbol, symbol, cs.firstNotNullOfOrNull { it.word.image }, resolver.symbolPaths(first.card), resolver.keywordPaths(first.card, first.word))
            },
            StickerKind.ABC to abcDeck.groupBy { it.card.upper }.map { (upper, cs) ->
                val c = cs.first().card
                ListenItem(upper, "$upper${c.lower}", c.image, resolver.letterPaths(c), resolver.letterWordPaths(c, cs.first().word))
            },
        ).mapValues { (_, items) -> items.filter { it.audio.isNotEmpty() } }.filterValues { it.size >= 2 }
    }

    // 從上次繼續：只在打開 App 後第一次到首頁時看一次；進任何模式就收起來。
    var resumeChecked by rememberSaveable { mutableStateOf(false) }
    var resume by remember { mutableStateOf<Pair<ResumeBadge, ResumePoint>?>(null) }
    fun saveResume(point: ResumePoint?) {
        resume = null
        scope.launch { store.saveResume(point) }
    }
    fun resumePoint(mode: ResumeMode, set: String? = null, step: Int = 0) = ResumePoint(mode, System.currentTimeMillis(), set, step)

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
            // 走完了就不用「接著玩」；還沒走完，下次接著這一天。
            store.saveResume(if (day.finished) null else resumePoint(ResumeMode.JOURNEY))
            player.stop()
            screen = Screen.Journey(day, s.startedAt, StationCelebration(s.index, isNew))
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = screen) {
            Screen.Home -> {
                var journeyDone by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    val today = store.journey()?.takeIf { it.date == LocalDate.now() }
                    journeyDone = today?.done ?: 0
                    if (!resumeChecked) {
                        resumeChecked = true
                        resume = ResumeRules.shown(store.resume(), System.currentTimeMillis())?.let { p ->
                            resumeBadge(p, today, yoga, store.position(Deck.SELF_PLAY), store.position(Deck.ABC))?.let { it to p }
                        }
                    }
                }
                HomeScreen(
                    focus = homeFocus,
                    onFocus = { homeFocus = it },
                    onJourney = {
                        saveResume(resumePoint(ResumeMode.JOURNEY))
                        scope.launch { screen = Screen.Journey(store.journeyToday(catalog.journeyPool()), SystemClock.elapsedRealtime()) }
                    },
                    onParent = {
                        saveResume(null)
                        scope.launch { screen = Screen.ParentStart(store.parentProgress()) }
                    },
                    onSelf = {
                        saveResume(resumePoint(ResumeMode.SELF))
                        scope.launch {
                            screen = Screen.SelfPlay(Decks.resumeIndex(selfDeck, store.position(Deck.SELF_PLAY)) { Decks.position(it) })
                        }
                    },
                    onAbc = {
                        if (abcDeck.isNotEmpty()) {
                            saveResume(resumePoint(ResumeMode.ABC))
                            scope.launch { screen = Screen.Abc(Decks.resumeIndex(abcDeck, store.position(Deck.ABC)) { Decks.position(it) }) }
                        }
                    },
                    onYoga = {
                        // 接著玩：直接回到那一套的那個動作，不經過選套。
                        val p = resume?.second?.takeIf { it.mode == ResumeMode.YOGA }
                        val choice = p?.let { yoga.firstOrNull { c -> c.id == it.yogaSet } }
                        resume = null
                        when {
                            choice != null -> screen = Screen.Yoga(choice, p.yogaStep)
                            yoga.isNotEmpty() -> screen = Screen.YogaPick
                        }
                    },
                    onListen = {
                        if (listenItems.isNotEmpty()) {
                            saveResume(null)
                            screen = Screen.ListenPick
                        }
                    },
                    onStickerBook = {
                        resume = null
                        screen = Screen.StickerBook
                    },
                    onSettings = {
                        resume = null
                        screen = Screen.Settings
                    },
                    journeyDone = journeyDone,
                    resume = resume?.first,
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
                        strokes = strokes,
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
                strokes = strokes,
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
                strokes = strokes,
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
                minutes = yogaMinutes,
            )
            is Screen.Yoga -> key(s) {
                YogaScreen(
                    choice = s.choice,
                    player = player,
                    onExit = goHome,
                    onDone = {
                        saveResume(null)
                        screen = Screen.YogaDone(s.choice)
                    },
                    onPoseDone = { award(StickerId(StickerKind.YOGA, it.id)) },
                    easyPoses = yogaEasy,
                    onEasy = { pose, easy ->
                        yogaEasy = if (easy) yogaEasy + pose.id else yogaEasy - pose.id
                        scope.launch { store.saveYogaEasy(pose.id, easy) }
                    },
                    startStep = s.startStep,
                    onStep = { saveResume(resumePoint(ResumeMode.YOGA, s.choice.id, it)) },
                )
            }
            Screen.ListenPick -> ListenPickScreen(
                kinds = listenItems.keys.toList(),
                onStart = { kind ->
                    scope.launch {
                        val owned = store.stickers().filter { it.kind == kind }.map { it.key }.toSet()
                        val seen = if (kind == StickerKind.ZHUYIN) {
                            ListenRules.seen(selfDeck.map { it.card.symbol }.distinct(), store.position(Deck.SELF_PLAY)?.key, owned, store.practicedSymbols())
                        } else {
                            ListenRules.seen(abcDeck.map { it.card.upper }.distinct(), store.position(Deck.ABC)?.key, owned)
                        }
                        screen = Screen.Listen(kind, seen)
                    }
                },
                onExit = goHome,
            )
            is Screen.Listen -> key(s) {
                ListenScreen(
                    kind = s.kind,
                    items = listenItems.getValue(s.kind),
                    seen = s.seen,
                    player = player,
                    listenAgain = resolver.phrasePaths(Phrase.LISTEN_AGAIN),
                    correct = resolver.phrasePaths(Phrase.CORRECT),
                    onCorrect = { award(StickerId(s.kind, it.key)) },
                    onExit = goHome,
                    onTimeUp = timeUp,
                )
            }
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
