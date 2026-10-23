package com.keeponfirst.bopomofotv.session

import com.keeponfirst.bopomofotv.data.Card
import kotlin.random.Random

/** 大人陪單次上限 10 分鐘。學齡前孩子專注時間短，到時就收。 */
const val PARENT_MILLIS = 10 * 60 * 1000L

/** 新卡段 → 複習段。 */
enum class Phase { NEW, REVIEW }

/**
 * 跨次保存的大人陪進度。
 *
 * - [attempts]：這個單元完整練完（新卡＋複習都做完）幾次。
 * - [passStreak]：連續幾次「每張卡第一次就按上鍵」。
 * - [resumePhase]／[resumeQueue]：上次沒做完（10 分鐘到或按返回鍵）時剩下的卡，下次從這裡接著做。
 */
data class ParentProgress(
    val unit: Int = 1,
    val attempts: Int = 0,
    val passStreak: Int = 0,
    val resumePhase: Phase = Phase.NEW,
    val resumeQueue: List<String> = emptyList(),
)

/**
 * 一次大人陪的狀態。[queue] 是本輪要出現的符號，[pos] 是目前那張。
 * [againCount] 與 [firstTry] 以「本次」計，跨新卡段與複習段。
 */
data class ParentSession(
    val unit: Int,
    val unitSymbols: List<String>,
    val phase: Phase,
    val queue: List<String>,
    val pos: Int = 0,
    val againCount: Map<String, Int> = emptyMap(),
    val firstTry: Map<String, Boolean> = emptyMap(),
) {
    val current: String get() = queue[pos]
}

/** 換卡後要播什麼。[DONE]＝本次做完，進結束頁。 */
enum class Cue { NEXT, PRACTICE_AGAIN, DONE }

data class Step(val session: ParentSession, val cue: Cue?)

/** 大人陪的規則。全部是純函式，畫面只負責呼叫與播音。 */
object ParentRules {
    /** 同一張卡本次最多按 2 次下鍵；第 2 次之後直接換卡，下次再練。 */
    const val MAX_AGAIN = 2

    /** 連續 2 次每張卡第一次就答對 → 下次進下一單元。 */
    const val PASS_STREAK = 2

    /** 同一單元練 4 次還沒過 → 先進下一單元。 */
    const val MAX_ATTEMPTS = 4

    fun start(cards: List<Card>, progress: ParentProgress): ParentSession {
        val unit = progress.unit.takeIf { u -> cards.any { it.unit == u } } ?: cards.minOf { it.unit }
        val symbols = cards.filter { it.unit == unit }.map { it.symbol }
        val resume = progress.resumeQueue.filter { it in symbols }
        return if (unit == progress.unit && resume.isNotEmpty()) {
            ParentSession(unit, symbols, progress.resumePhase, resume)
        } else {
            ParentSession(unit, symbols, Phase.NEW, symbols)
        }
    }

    /** 右鍵。本輪最後一張之後：新卡段 → 打亂進複習段；複習段 → 做完。 */
    fun next(s: ParentSession, random: Random = Random.Default): Step = when {
        s.pos < s.queue.lastIndex -> Step(s.copy(pos = s.pos + 1), Cue.NEXT)
        s.phase == Phase.NEW -> Step(s.copy(phase = Phase.REVIEW, queue = s.unitSymbols.shuffled(random), pos = 0), Cue.PRACTICE_AGAIN)
        else -> Step(s, Cue.DONE)
    }

    /** 左鍵。只在本輪內往回，不跨段。 */
    fun previous(s: ParentSession): ParentSession = s.copy(pos = (s.pos - 1).coerceAtLeast(0))

    /** 上鍵。 */
    fun markCorrect(s: ParentSession): ParentSession = s.copy(firstTry = s.firstTry.withFirst(s.current, true))

    /**
     * 下鍵。第 1 次：這張卡排到本輪最後，停在原卡重播（cue = null）。
     * 第 [MAX_AGAIN] 次起：不再排，直接換下一張。
     */
    fun markAgain(s: ParentSession, random: Random = Random.Default): Step {
        val count = (s.againCount[s.current] ?: 0) + 1
        val marked = s.copy(againCount = s.againCount + (s.current to count), firstTry = s.firstTry.withFirst(s.current, false))
        return if (count < MAX_AGAIN) Step(marked.copy(queue = marked.queue + s.current), null) else next(marked, random)
    }

    /** 沒做完就結束（10 分鐘到或返回鍵）：記住目前這張起剩下的卡。 */
    fun paused(progress: ParentProgress, s: ParentSession): ParentProgress =
        progress.copy(unit = s.unit, resumePhase = s.phase, resumeQueue = s.queue.drop(s.pos).distinct())

    /** 做完一次：連續 [PASS_STREAK] 次全對或練滿 [MAX_ATTEMPTS] 次，下次進下一單元。 */
    fun completed(progress: ParentProgress, s: ParentSession, units: List<Int>): ParentProgress {
        val perfect = s.unitSymbols.all { s.firstTry[it] == true }
        val attempts = (if (progress.unit == s.unit) progress.attempts else 0) + 1
        val streak = if (perfect) (if (progress.unit == s.unit) progress.passStreak else 0) + 1 else 0
        return if (streak >= PASS_STREAK || attempts >= MAX_ATTEMPTS) {
            ParentProgress(unit = nextUnit(units, s.unit))
        } else {
            ParentProgress(unit = s.unit, attempts = attempts, passStreak = streak)
        }
    }

    /** 下一個單元；最後一個單元之後回到第一個。 */
    fun nextUnit(units: List<Int>, unit: Int): Int {
        val sorted = units.distinct().sorted()
        return sorted.firstOrNull { it > unit } ?: sorted.first()
    }

    private fun Map<String, Boolean>.withFirst(symbol: String, value: Boolean) =
        if (symbol in this) this else this + (symbol to value)
}
