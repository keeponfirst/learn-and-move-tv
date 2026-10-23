package com.keeponfirst.bopomofotv.data

import android.content.Context
import com.keeponfirst.bopomofotv.session.YOGA_ROUNDS
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 開場或結束的一段語音。欄位照 yoga/poses.json；路徑都相對 assets/。 */
@Serializable
data class YogaCue(val cueAudio: String, val cueText: String = "")

/**
 * 一個瑜伽姿勢。[seconds] 是口令播完後的倒數秒數。
 * 簡單版四欄（[imageEasy]、[cueTextEasy]、[cueAudioEasy]、[safetyEasy]）不在 sets.json 裡，由 [YogaParser.withEasy] 從 easy.json 補上；
 * [imageEasy] 是 null＝這個動作沒有簡單版。
 */
@Serializable
data class YogaPose(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val seconds: Int,
    val image: String,
    val cueAudio: String,
    val cueText: String = "",
    val safety: String = "",
    val imageEasy: String? = null,
    val cueTextEasy: String? = null,
    val cueAudioEasy: String? = null,
    val safetyEasy: String? = null,
) {
    val hasEasy: Boolean get() = imageEasy != null

    /** 畫面下方的安全提醒：簡單版用簡單版提醒（姿勢不同，原版提醒會講錯），沒有就退回一般版。 */
    fun safetyFor(easy: Boolean): String = if (easy) safetyEasy?.takeIf { it.isNotBlank() } ?: safety else safety
}

/** easy.json 的一筆（[safety] 是簡單版安全提醒）。needEasy=false 的動作沿用原版。 */
@Serializable
data class YogaEasyDef(
    val id: String,
    val needEasy: Boolean = false,
    val image: String? = null,
    val cueTextEasy: String? = null,
    val cueAudioEasy: String? = null,
    val safety: String? = null,
)

@Serializable
data class YogaEasyFile(val poses: List<YogaEasyDef>)

@Serializable
data class YogaProgram(val intro: YogaCue, val poses: List<YogaPose>, val done: YogaCue)

/** sets.json 裡的一套：照 [poseIds] 的順序從 [YogaSetsFile.poses] 取動作。 */
@Serializable
data class YogaSetDef(
    val id: String,
    val nameZh: String,
    val intro: YogaCue,
    val done: YogaCue,
    val poseIds: List<String>,
)

/** yoga/sets.json：所有動作（含原本那套的 9 個）＋幾套主題。 */
@Serializable
data class YogaSetsFile(val poses: List<YogaPose>, val sets: List<YogaSetDef>)

/** 「選一套」頁的一張卡。[rounds] 是這套要做幾輪。 */
data class YogaChoice(val id: String, val nameZh: String, val program: YogaProgram, val rounds: Int)

/** 主題套做 1 輪（每套本身 5～7 分鐘）。原本那套照 [YOGA_ROUNDS]。 */
const val YOGA_SET_ROUNDS = 1

object YogaParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): YogaProgram = json.decodeFromString(text)

    fun parseSets(text: String): YogaSetsFile = json.decodeFromString(text)

    fun parseEasy(text: String): YogaEasyFile = json.decodeFromString(text)

    /**
     * 組出「選一套」的卡片。兩個檔都可以是 null（不在）或壞掉。
     * - sets.json 可用：每套一張，最後加「原本那套」（poses.json）。
     * - sets.json 不在或壞掉：只有一張「全部動作」（poses.json）。
     * 套裡找不到的 poseId 跳過；沒有動作的套不列。兩個都不能用回空清單。
     */
    fun choices(setsText: String?, posesText: String?): List<YogaChoice> {
        val classic = posesText?.let { runCatching { parse(it) }.getOrNull() }?.takeIf { it.poses.isNotEmpty() }
        val sets = setsText?.let { runCatching { parseSets(it) }.getOrNull() }
        val byId = sets?.poses.orEmpty().associateBy { it.id }
        val themed = sets?.sets.orEmpty().mapNotNull { set ->
            val poses = set.poseIds.mapNotNull { byId[it] }
            if (poses.isEmpty()) null else YogaChoice(set.id, set.nameZh, YogaProgram(set.intro, poses, set.done), YOGA_SET_ROUNDS)
        }
        val classicName = if (themed.isEmpty()) "全部動作" else "原本那套"
        return themed + listOfNotNull(classic?.let { YogaChoice("classic", classicName, it, YOGA_ROUNDS) })
    }

    /**
     * 把 easy.json 的簡單版補進每個動作。[easyText] 是 null 或壞掉就原樣回傳。
     * 只有 needEasy=true、而且圖真的在 assets（[exists]）的動作才有簡單版：缺圖就不顯示切換。
     * 簡單版口令音不在就用原版口令音（口令字仍用簡單版）。
     */
    fun withEasy(choices: List<YogaChoice>, easyText: String?, exists: (String) -> Boolean): List<YogaChoice> {
        val defs = easyText?.let { runCatching { parseEasy(it) }.getOrNull() }
            ?.poses.orEmpty()
            .filter { it.needEasy && it.image != null && exists(it.image) }
            .associateBy { it.id }
        if (defs.isEmpty()) return choices
        fun easy(p: YogaPose): YogaPose {
            val d = defs[p.id] ?: return p
            return p.copy(imageEasy = d.image, cueTextEasy = d.cueTextEasy, cueAudioEasy = d.cueAudioEasy?.takeIf(exists), safetyEasy = d.safety)
        }
        return choices.map { c -> c.copy(program = c.program.copy(poses = c.program.poses.map(::easy))) }
    }

    /**
     * 選套卡上的「約 N 分鐘」：(開場＋每輪的口令與倒數＋結束) 加起來，四捨五入到分鐘，至少 1 分鐘。
     * [audioMs] 回音檔長度（毫秒），讀不到回 null 當 0。用一般版口令算。
     */
    fun minutes(choice: YogaChoice, audioMs: (String) -> Long?): Int {
        val p = choice.program
        val perRound = p.poses.sumOf { it.seconds * 1000L + (audioMs(it.cueAudio) ?: 0L) }
        val total = (audioMs(p.intro.cueAudio) ?: 0L) + perRound * choice.rounds + (audioMs(p.done.cueAudio) ?: 0L)
        return Math.round(total / 60_000.0).toInt().coerceAtLeast(1)
    }
}

/**
 * 從 assets/yoga/sets.json 與 poses.json 載入，再補上 easy.json 的簡單版。
 * 都不能用回空清單，首頁瑜伽鍵就不進瑜伽頁。
 */
object YogaRepository {
    const val ASSET_PATH = "yoga/poses.json"
    const val SETS_ASSET_PATH = "yoga/sets.json"
    const val EASY_ASSET_PATH = "yoga/easy.json"

    fun load(context: Context): List<YogaChoice> {
        fun read(path: String) = runCatching {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
        fun exists(path: String) = runCatching { context.assets.open(path).close() }.isSuccess
        return YogaParser.withEasy(YogaParser.choices(read(SETS_ASSET_PATH), read(ASSET_PATH)), read(EASY_ASSET_PATH), ::exists)
    }
}
