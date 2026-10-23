package com.keeponfirst.bopomofotv.session

/** 原本 poses.json 那套做 2 輪；sets.json 的主題套做 1 輪。9 個姿勢倒數合計 145 秒，2 輪 290 秒，加口令約 8～9 分鐘。 */
const val YOGA_ROUNDS = 2

/**
 * 瑜伽的段落。
 * - [INTRO]：播開場語音，播完進第一個姿勢的口令。
 * - [CUE]：播姿勢口令，播完才開始倒數。
 * - [COUNTDOWN]：倒數 [YogaSession.remaining] 秒，到 0 換下一個姿勢。
 * - [DONE]：全部做完。
 */
enum class YogaPhase { INTRO, CUE, COUNTDOWN, DONE }

/**
 * 一次瑜伽的狀態。[step] 是第幾個姿勢（跨輪攤平，0 起算），
 * 第 [step] 個姿勢是 poses[step % poseSeconds.size]。
 */
data class YogaSession(
    val poseSeconds: List<Int>,
    val rounds: Int,
    val phase: YogaPhase = YogaPhase.INTRO,
    val step: Int = 0,
    val remaining: Int = 0,
    val paused: Boolean = false,
) {
    val totalSteps: Int get() = poseSeconds.size * rounds
    val poseIndex: Int get() = step % poseSeconds.size
    val round: Int get() = step / poseSeconds.size + 1
}

/** 瑜伽流程規則。全部是純函式，畫面只負責播音、計時與呼叫。 */
object YogaRules {
    fun start(poseSeconds: List<Int>, rounds: Int = YOGA_ROUNDS): YogaSession {
        require(poseSeconds.isNotEmpty() && rounds > 0)
        return YogaSession(poseSeconds, rounds)
    }

    /** 開場或口令播完（缺音時立即呼叫）。暫停中不理會：暫停會停掉語音，繼續時重播。 */
    fun cueFinished(s: YogaSession): YogaSession = when {
        s.paused -> s
        s.phase == YogaPhase.INTRO -> s.copy(phase = YogaPhase.CUE, step = 0)
        s.phase == YogaPhase.CUE -> s.copy(phase = YogaPhase.COUNTDOWN, remaining = s.poseSeconds[s.poseIndex])
        else -> s
    }

    /** 倒數過了一秒。到 0 換下一個姿勢。只有倒數中且沒暫停才算。 */
    fun tick(s: YogaSession): YogaSession {
        if (s.phase != YogaPhase.COUNTDOWN || s.paused) return s
        val left = s.remaining - 1
        return if (left > 0) s.copy(remaining = left) else next(s)
    }

    /** 跳到下一個姿勢的口令（右鍵或倒數到 0）。最後一個之後進 [YogaPhase.DONE]。跳過會解除暫停。 */
    fun next(s: YogaSession): YogaSession {
        if (s.phase == YogaPhase.DONE) return s
        val nextStep = if (s.phase == YogaPhase.INTRO) 0 else s.step + 1
        return if (nextStep >= s.totalSteps) {
            s.copy(phase = YogaPhase.DONE, remaining = 0, paused = false)
        } else {
            s.copy(phase = YogaPhase.CUE, step = nextStep, remaining = 0, paused = false)
        }
    }

    /** 確定鍵：暫停／繼續。做完後不理會。 */
    fun togglePause(s: YogaSession): YogaSession =
        if (s.phase == YogaPhase.DONE) s else s.copy(paused = !s.paused)
}
