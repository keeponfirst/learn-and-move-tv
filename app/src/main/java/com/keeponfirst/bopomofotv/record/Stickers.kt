package com.keeponfirst.bopomofotv.record

/** 貼紙的三類，順序同貼紙冊的書籤。[key] 存進 DataStore，改了舊紀錄會讀不到，不要改。 */
enum class StickerKind(val key: String, val label: String) {
    ZHUYIN("z", "注音"),
    ABC("a", "ABC"),
    YOGA("y", "瑜伽"),
}

/** 一張貼紙＝一類＋一項：注音符號、大寫字母或瑜伽動作 id。存成 `類|項`，例 `z|ㄅ`、`y|tree_left`。 */
data class StickerId(val kind: StickerKind, val key: String) {
    fun encode(): String = "${kind.key}$SEPARATOR$key"

    companion object {
        private const val SEPARATOR = "|"

        /** 認不得的字串回 null，不丟例外。 */
        fun decode(text: String): StickerId? {
            val parts = text.split(SEPARATOR, limit = 2)
            if (parts.size != 2 || parts[1].isEmpty()) return null
            val kind = StickerKind.entries.firstOrNull { it.key == parts[0] } ?: return null
            return StickerId(kind, parts[1])
        }
    }
}

object StickerRules {
    /** 同一項只給一次：[owned]（存檔字串）已經有 [id] 回 null；第一次拿到回加進去之後的集合。 */
    fun award(owned: Set<String>, id: StickerId): Set<String>? =
        id.encode().takeIf { it !in owned }?.let { owned + it }
}

/**
 * 自己玩／ABC 的「看完一項」判定：同一個符號（或字母）的每張卡都出現過，就算看完，給那一項的貼紙。
 * 只算這一次進來看到的卡。同一項只回報一次。
 */
class SeenTracker {
    private val seen = mutableMapOf<String, MutableSet<Int>>()

    /** 看到 [key] 的第 [wordIndex] 張（共 [total] 張）。這一張剛好讓它看完時回 true。 */
    fun see(key: String, wordIndex: Int, total: Int): Boolean {
        val set = seen.getOrPut(key) { mutableSetOf() }
        val before = set.size >= total
        set += wordIndex
        return !before && set.size >= total
    }
}
