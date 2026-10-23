package com.keeponfirst.bopomofotv.data

import android.content.Context
import com.keeponfirst.bopomofotv.session.YOGA_ROUNDS
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 開場或結束的一段語音。欄位照 yoga/poses.json；路徑都相對 assets/。 */
@Serializable
data class YogaCue(val cueAudio: String, val cueText: String = "")

/** 一個瑜伽姿勢。[seconds] 是口令播完後的倒數秒數。 */
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
)

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
}

/** 從 assets/yoga/sets.json 與 poses.json 載入。都不能用回空清單，首頁瑜伽鍵就不進瑜伽頁。 */
object YogaRepository {
    const val ASSET_PATH = "yoga/poses.json"
    const val SETS_ASSET_PATH = "yoga/sets.json"

    fun load(context: Context): List<YogaChoice> {
        fun read(path: String) = runCatching {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
        return YogaParser.choices(read(SETS_ASSET_PATH), read(ASSET_PATH))
    }
}
