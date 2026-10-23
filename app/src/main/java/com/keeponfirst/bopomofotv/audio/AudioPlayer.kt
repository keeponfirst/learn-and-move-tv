package com.keeponfirst.bopomofotv.audio

import android.content.res.AssetManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.util.Log

/**
 * 依序播放 assets 裡的音檔。用 MediaPlayer（理由見 docs/decisions.md）。
 *
 * - 每次 [play] 先停掉正在播的，再播新的清單。
 * - 任何一檔打不開或播放出錯，記 log 後跳下一檔。不丟例外，不當機。
 * - 只在主執行緒呼叫。
 * - 整個 Activity 共用一個 MediaPlayer，換檔用 reset()，Activity 結束才 [release]。
 *   每檔 release 一次會在 logcat 留「mediaplayer went away with unhandled events」：
 *   模擬器實測 release 後約 6 ms 就出現，不論在 OnCompletionListener 裡放或拿掉 stop() 都一樣。
 */
class AudioPlayer(private val assets: AssetManager) {
    private var player: MediaPlayer? = null
    private var queue: List<String> = emptyList()
    private var onDone: (() -> Unit)? = null

    /**
     * [onDone]：整份清單播完（或每檔都打不開）時呼叫一次。清單是空的就立刻呼叫。
     * 被 [stop] 或下一次 [play] 打斷時不呼叫。瑜伽用它在口令播完才開始倒數。
     */
    fun play(paths: List<String>, onDone: (() -> Unit)? = null) {
        stop()
        queue = paths
        this.onDone = onDone
        playNext()
    }

    /** 停止並清空清單。reset() 會丟掉還沒送出的播放事件，舊的播完不會再接下一檔。 */
    fun stop() {
        queue = emptyList()
        onDone = null
        player?.reset()
    }

    /** Activity onDestroy 呼叫。之後 [play] 會再建新的 MediaPlayer。 */
    fun release() {
        queue = emptyList()
        onDone = null
        player?.release()
        player = null
    }

    private fun playNext() {
        val path = queue.firstOrNull()
        if (path == null) {
            val done = onDone
            onDone = null
            done?.invoke()
            return
        }
        queue = queue.drop(1)
        val mp = player ?: newPlayer().also { player = it }
        try {
            mp.reset()
            assets.openFd(path).use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            mp.prepare()
            mp.start()
            Log.i(TAG, "播放 $path") // 給 scripts/emulator-screenshots.sh 用 logcat 確認找到檔
        } catch (e: Exception) {
            Log.w(TAG, "無法播放 $path", e)
            playNext()
        }
    }

    private fun newPlayer() = MediaPlayer().apply {
        setOnCompletionListener { playNext() }
        setOnErrorListener { _, what, extra ->
            Log.w(TAG, "播放失敗 what=$what extra=$extra")
            playNext()
            true
        }
    }

    companion object {
        private const val TAG = "AudioPlayer"

        /** 音檔長度（毫秒）。打不開回 null。瑜伽選套卡算「約 N 分鐘」用，要在背景執行緒呼叫。 */
        fun durationMs(assets: AssetManager, path: String): Long? = runCatching {
            val r = MediaMetadataRetriever()
            try {
                assets.openFd(path).use { fd -> r.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                r.release()
            }
        }.getOrNull()

        /** 列出 assets 裡所有可能用到的音檔路徑，給 [AudioResolver] 用。 */
        fun listAvailable(assets: AssetManager): Set<String> {
            val dirs = AudioResolver.CUSTOM_AUDIO_DIRS + AudioResolver.MOE_DIR + AudioResolver.ABC_AUDIO_DIR + AudioResolver.ZHUYIN_AUDIO_DIR
            return dirs.flatMap { dir ->
                (runCatching { assets.list(dir) }.getOrNull() ?: emptyArray()).map { "$dir/$it" }
            }.toSet()
        }
    }
}
