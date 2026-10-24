package com.keeponfirst.bopomofotv.session

/**
 * 巨人小螞蟻的口令。[line] 是台詞 id，也是音檔檔名（giant/audio/<id>.m4a）和 lines.tsv 第一欄。
 * [fake]＝假口令：不是巨人也不是小螞蟻，孩子要停住不動。
 */
enum class GiantCall(val line: String, val fake: Boolean) {
    GIANT("ga_giant", false),
    ANT("ga_ant", false),
    ELEPHANT("ga_fake_elephant", true),
    BIRD("ga_fake_bird", true),
    WATERMELON("ga_fake_watermelon", true),
    FISH("ga_fake_fish", true),
}

/** 台詞 id（lines.tsv）。 */
object GiantLine {
    val INTRO = listOf("ga_intro_1", "ga_intro_2", "ga_intro_3")
    /** 空間檢查：兩手打開碰不到東西、碰不到別人。每一輪都播。 */
    const val SPACE = "ga_intro_3"
    /** 安全提醒：慢慢蹲、慢慢站，腳不離開地板。每一輪都播。 */
    const val SAFE = "ga_safe_slow"
    const val RULE_GIANT = "ga_rule_giant"
    const val RULE_ANT = "ga_rule_ant"
    const val RULE_EASY = "ga_rule_easy"
    const val RULE_FAKE = "ga_rule_fake"
    const val PRACTICE = "ga_practice"
    const val GO = "ga_go"
    const val FAKE_AFTER = "ga_fake_after"
    const val FASTER = "ga_faster"
    const val FASTEST = "ga_fastest"
    const val END_HIGHFIVE = "ga_end_1"
    const val END_BREATHE = "ga_end_2"
    const val END_ASK = "ga_end_3"
    const val REST = "ga_rest"
}

/**
 * 3 組固定順序，依輪數輪流，不用亂數。每組 10 個：慢 1～3、中 4～7、快 8～10。
 * 排序規則見 GiantRulesTest。
 */
val GIANT_SETS: List<List<GiantCall>> = run {
    val g = GiantCall.GIANT
    val a = GiantCall.ANT
    listOf(
        listOf(g, a, g, a, g, GiantCall.ELEPHANT, a, g, GiantCall.FISH, a),
        listOf(a, g, a, g, GiantCall.BIRD, a, g, GiantCall.WATERMELON, a, g),
        listOf(g, a, g, a, g, a, GiantCall.WATERMELON, g, GiantCall.BIRD, a),
    )
}

/** 正式一輪的口令數。 */
const val GIANT_CALLS = 10

/** 第 [i] 個口令（0 起算）的節奏：慢段 6 秒、中段 5 秒、快段 4 秒（最快就是 4 秒）。 */
fun giantBeatMillis(i: Int): Long = when {
    i < 3 -> 6_000L
    i < 7 -> 5_000L
    else -> 4_000L
}

/** 練習時每個口令 6 秒。 */
const val GIANT_PRACTICE_BEAT = 6_000L

/** 一句話播完到下一句開始，至少空這麼久。 */
const val GIANT_GAP = 500L

/** 收操呼吸那句（ga_end_2）至少停這麼久，讓孩子真的吸一口、吐一口。 */
const val GIANT_BREATHE_MILLIS = 9_000L

/** 畫面要顯示什麼。每段台詞對一個畫面。 */
enum class GiantScene {
    /** 開場與每輪的空間、安全提醒：示範小孩站好。 */
    INTRO,
    RULE_GIANT,
    RULE_ANT,
    RULE_EASY,
    RULE_FAKE,
    PRACTICE,
    GO,
    /** 口令：變巨人、變小螞蟻、假口令。 */
    CALL,
    FAKE_AFTER,
    FASTER,
    END_HIGHFIVE,
    END_BREATHE,
    END_ASK,
    REST,
}

/**
 * 時間表裡的一段。[atMillis] 從這一輪開始算。
 * [call]／[callIndex]：口令段才有；[callIndex] 是正式口令的位置（0～9），練習是 -1。
 * [slotMillis]：這一段到下一段開始有多久（口令段＝倒數圓環的秒數）。
 */
data class GiantCue(
    val atMillis: Long,
    val lines: List<String>,
    val scene: GiantScene,
    val slotMillis: Long,
    val call: GiantCall? = null,
    val callIndex: Int = -1,
)

/** 一輪的種類：進來第一輪有完整開場、規則與練習；之後每輪只重播空間檢查與安全提醒。 */
enum class GiantRound { FIRST, AGAIN }

/** 巨人小螞蟻的時間表。全部是純函式，畫面只負責計時、播音與呼叫。 */
object GiantRules {
    /** 同一次進入連玩幾輪就休息。 */
    const val ROUNDS_BEFORE_REST = 3

    /** 第 [round] 輪（0 起算，這次進來的第幾個正式輪）用哪一組。3 組依序輪流。 */
    fun setFor(round: Int): List<GiantCall> = GIANT_SETS[round % GIANT_SETS.size]

    /** 這次進來已經玩完 [roundsDone] 輪：每滿 3 輪就休息（結尾改播 ga_rest，停在休息頁）。 */
    fun needsRest(roundsDone: Int): Boolean = roundsDone > 0 && roundsDone % ROUNDS_BEFORE_REST == 0

    /**
     * 一輪的時間表。[speech] 回傳一句台詞要播多久（毫秒；音檔長度，沒有音檔用估計）。
     * 每一段的長度＝max(這段至少要停的時間, 這段台詞播完＋[GIANT_GAP])，所以下一段一定在上一段播完後才開始，不疊播。
     * 口令段至少停節奏秒數；長句（例 ga_fake_after 6 秒）在 4 秒的快段也照樣等它播完，後面整個往後推。
     * [roundsDone]：這一輪之前已玩完幾輪；這一輪結束後要休息就把 ga_end_3 換成 ga_rest。
     */
    fun schedule(kind: GiantRound, set: List<GiantCall>, roundsDone: Int, speech: (String) -> Long): List<GiantCue> {
        require(set.size == GIANT_CALLS)
        val cues = mutableListOf<GiantCue>()
        var at = 0L
        fun add(lines: List<String>, scene: GiantScene, hold: Long = 0L, call: GiantCall? = null, index: Int = -1) {
            val talk = lines.sumOf { speech(it) } + GIANT_GAP
            val slot = maxOf(hold, talk)
            cues += GiantCue(at, lines, scene, slot, call, index)
            at += slot
        }
        if (kind == GiantRound.FIRST) {
            // 開場三句（請大人陪、往後退、手打開碰不到）＋安全提醒＋四條規則＋練習一次。
            add(GiantLine.INTRO, GiantScene.INTRO)
            add(listOf(GiantLine.SAFE), GiantScene.INTRO)
            add(listOf(GiantLine.RULE_GIANT), GiantScene.RULE_GIANT)
            add(listOf(GiantLine.RULE_ANT), GiantScene.RULE_ANT)
            add(listOf(GiantLine.RULE_EASY), GiantScene.RULE_EASY)
            add(listOf(GiantLine.RULE_FAKE), GiantScene.RULE_FAKE)
            add(listOf(GiantLine.PRACTICE), GiantScene.PRACTICE)
            add(listOf(GiantCall.GIANT.line), GiantScene.CALL, GIANT_PRACTICE_BEAT, GiantCall.GIANT)
            add(listOf(GiantCall.ANT.line), GiantScene.CALL, GIANT_PRACTICE_BEAT, GiantCall.ANT)
        } else {
            // 再玩一輪：孩子可能換了位置，空間檢查和安全提醒再播一次。
            add(listOf(GiantLine.SPACE, GiantLine.SAFE), GiantScene.INTRO)
        }
        add(listOf(GiantLine.GO), GiantScene.GO)
        set.forEachIndexed { i, call ->
            if (i == 3) add(listOf(GiantLine.FASTER), GiantScene.FASTER)
            if (i == 7) add(listOf(GiantLine.FASTEST), GiantScene.FASTER)
            add(listOf(call.line), GiantScene.CALL, giantBeatMillis(i), call, i)
            if (call.fake) add(listOf(GiantLine.FAKE_AFTER), GiantScene.FAKE_AFTER)
        }
        add(listOf(GiantLine.END_HIGHFIVE), GiantScene.END_HIGHFIVE)
        add(listOf(GiantLine.END_BREATHE), GiantScene.END_BREATHE, GIANT_BREATHE_MILLIS)
        if (needsRest(roundsDone + 1)) add(listOf(GiantLine.REST), GiantScene.REST) else add(listOf(GiantLine.END_ASK), GiantScene.END_ASK)
        return cues
    }

    /** 整輪多長：最後一段播完。之後停在結尾或休息頁，等大人按 OK。 */
    fun totalMillis(schedule: List<GiantCue>): Long = schedule.last().let { it.atMillis + it.slotMillis }

    /** [elapsed] 時正在第幾段（還沒開始回 -1）。 */
    fun cueAt(schedule: List<GiantCue>, elapsed: Long): Int = schedule.indexOfLast { it.atMillis <= elapsed }

    /**
     * 從第 [next] 段起，到 [elapsed] 為止該開始的段。回傳（要播的段, 下一次從第幾段查）。
     * 一次跳過好幾段（例如畫面卡住）只播最後一段，不讓語音疊成一串（同刷牙）。
     */
    fun due(schedule: List<GiantCue>, next: Int, elapsed: Long): Pair<GiantCue?, Int> {
        var i = next
        while (i < schedule.size && schedule[i].atMillis <= elapsed) i++
        return (if (i > next) schedule[i - 1] else null) to i
    }

    /**
     * 暫停後繼續：回到這一段的開頭重播（暫停會清掉播到一半的句子）。回傳（新的 elapsed, 下一次從第幾段查）。
     * 從段頭重來，後面的時間表不用改，也不會疊播。
     */
    fun resume(schedule: List<GiantCue>, elapsed: Long): Pair<Long, Int> {
        val i = cueAt(schedule, elapsed).coerceAtLeast(0)
        return schedule[i].atMillis to i
    }

    /** 右下 10 個腳印亮幾個：已經開始的正式口令數（含假口令）。練習不算。 */
    fun footprints(schedule: List<GiantCue>, elapsed: Long): Int =
        schedule.count { it.callIndex >= 0 && it.atMillis <= elapsed }
}
