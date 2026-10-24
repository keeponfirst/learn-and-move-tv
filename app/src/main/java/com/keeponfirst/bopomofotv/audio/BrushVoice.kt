package com.keeponfirst.bopomofotv.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * 刷牙的語音。每句先找 brush/audio/<id>.m4a（Google Cloud Text-to-Speech 預先合成）。
 * 巨人小螞蟻也用這個佇列，[dir] 傳 giant/audio。
 * 一組台詞裡只要有一句沒有音檔，整組改用電視的系統語音念 [lines] 的文字，避免同一組兩種聲音。
 * 系統語音沒有中文（台灣）也不能用中文時就靜音，不當機。
 *
 * 不疊播：[say] 在別組還在播時排到後面，依序播。[sayIfIdle] 在別組還在播時直接略過（鼓勵句用）。
 * 只在主執行緒呼叫。畫面離開時呼叫 [shutdown]。
 */
class BrushVoice(context: Context, private val player: AudioPlayer, private val lines: Map<String, String>, private val dir: String = DIR) {
    private val available: Set<String> =
        (runCatching { context.assets.list(dir) }.getOrNull() ?: emptyArray()).map { "$dir/$it" }.toSet()
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val queue = ArrayDeque<List<String>>()
    /** 正在播的一組；null＝閒著。 */
    private var playing: List<String>? = null

    init {
        // 全部都有音檔就不用開系統語音。
        if (lines.keys.any { path(it) !in available }) {
            tts = TextToSpeech(context.applicationContext) { status ->
                val result = if (status == TextToSpeech.SUCCESS) tts?.setLanguage(Locale.TAIWAN) ?: TextToSpeech.LANG_NOT_SUPPORTED else TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = result >= TextToSpeech.LANG_AVAILABLE
                Log.i(TAG, if (ttsReady) "沒有音檔的台詞用系統語音" else "系統語音不能說中文（$status/$result），沒有音檔的台詞靜音")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) { if (utteranceId == LAST) main.post { finished() } }
                    @Deprecated("API 21 以前的版本") override fun onError(utteranceId: String?) { main.post { finished() } }
                })
                // 系統語音還沒準備好時排著的（通常是開場），現在開始播。
                if (playing == null) playNext()
            }
        }
    }

    /** 排進佇列，前面播完才播。 */
    fun say(ids: List<String>) {
        queue.addLast(ids)
        if (playing == null) playNext()
    }

    /** 閒著才播，不然略過（鼓勵句）。 */
    fun sayIfIdle(ids: List<String>) {
        if (playing == null && queue.isEmpty()) say(ids) else Log.i(TAG, "略過（別句還在播）$ids")
    }

    private fun playNext() {
        val ids = queue.firstOrNull() ?: return
        if (ids.all { path(it) in available }) {
            queue.removeFirst()
            playing = ids
            player.play(ids.map(::path)) { finished() }
            return
        }
        val engine = tts
        if (engine == null) {
            queue.removeFirst()
            Log.i(TAG, "靜音 $ids")
            playNext()
            return
        }
        if (!ttsReady) return // 等初始化回呼再播
        queue.removeFirst()
        playing = ids
        val texts = ids.mapNotNull { lines[it] }
        texts.forEachIndexed { i, text ->
            engine.speak(text, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == texts.lastIndex) LAST else "$i")
        }
        if (texts.isEmpty()) finished()
        Log.i(TAG, "系統語音 $ids")
    }

    private fun finished() {
        playing = null
        playNext()
    }

    /** 停掉正在播的，清空佇列（暫停、離開）。 */
    fun stop() {
        queue.clear()
        playing = null
        player.stop()
        tts?.stop()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private fun path(id: String) = "$dir/$id.m4a"

    companion object {
        private const val TAG = "BrushVoice"
        private const val LAST = "brush-last"
        const val DIR = "brush/audio"
        const val GIANT_DIR = "giant/audio"
    }
}
