package com.keeponfirst.bopomofotv.record

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.keeponfirst.bopomofotv.data.DeckPosition
import com.keeponfirst.bopomofotv.session.ParentProgress
import com.keeponfirst.bopomofotv.session.Phase
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val Context.practiceDataStore: DataStore<Preferences> by preferencesDataStore(name = "practice")

/** 紀錄只存在電視本機（Preferences DataStore），不上傳。 */
class PracticeStore(context: Context) {
    private val dataStore = context.applicationContext.practiceDataStore

    suspend fun record(symbol: String, result: PracticeResult, date: LocalDate = LocalDate.now()) {
        val key = intPreferencesKey(PracticeRecords.key(date, symbol, result))
        dataStore.edit { it[key] = (it[key] ?: 0) + 1 }
    }

    suspend fun entries(): List<PracticeEntry> =
        dataStore.data.first().asMap().mapNotNull { (key, value) ->
            (value as? Int)?.let { PracticeRecords.parse(key.name, it) }
        }

    /** 大人陪的單元進度與沒做完的卡。 */
    suspend fun parentProgress(): ParentProgress {
        val prefs = dataStore.data.first()
        return ParentProgress(
            unit = prefs[PARENT_UNIT] ?: 1,
            attempts = prefs[PARENT_ATTEMPTS] ?: 0,
            passStreak = prefs[PARENT_STREAK] ?: 0,
            resumePhase = Phase.entries.firstOrNull { it.name == prefs[PARENT_RESUME_PHASE] } ?: Phase.NEW,
            resumeQueue = prefs[PARENT_RESUME]?.split(QUEUE_SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList(),
        )
    }

    suspend fun saveParentProgress(progress: ParentProgress) {
        dataStore.edit {
            it[PARENT_UNIT] = progress.unit
            it[PARENT_ATTEMPTS] = progress.attempts
            it[PARENT_STREAK] = progress.passStreak
            it[PARENT_RESUME_PHASE] = progress.resumePhase.name
            it[PARENT_RESUME] = progress.resumeQueue.joinToString(QUEUE_SEPARATOR)
        }
    }

    /** 自己玩／ABC 上次停的那張。存成 `符號|第幾個詞`，例 `ㄅ|1`。 */
    suspend fun position(deck: Deck): DeckPosition? {
        val parts = dataStore.data.first()[deck.key]?.split(POSITION_SEPARATOR) ?: return null
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return DeckPosition(parts[0], index)
    }

    suspend fun savePosition(deck: Deck, position: DeckPosition) {
        dataStore.edit { it[deck.key] = "${position.key}$POSITION_SEPARATOR${position.wordIndex}" }
    }

    enum class Deck(val key: Preferences.Key<String>) {
        SELF_PLAY(stringPreferencesKey("self_position")),
        ABC(stringPreferencesKey("abc_position")),
    }

    private companion object {
        const val POSITION_SEPARATOR = "|"
        const val QUEUE_SEPARATOR = ","
        val PARENT_UNIT = intPreferencesKey("parent_unit")
        val PARENT_ATTEMPTS = intPreferencesKey("parent_attempts")
        val PARENT_STREAK = intPreferencesKey("parent_streak")
        val PARENT_RESUME_PHASE = stringPreferencesKey("parent_resume_phase")
        val PARENT_RESUME = stringPreferencesKey("parent_resume")
    }
}
