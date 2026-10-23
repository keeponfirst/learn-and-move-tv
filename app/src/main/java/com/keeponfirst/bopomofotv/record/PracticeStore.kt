package com.keeponfirst.bopomofotv.record

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.keeponfirst.bopomofotv.data.DeckPosition
import com.keeponfirst.bopomofotv.session.ParentProgress
import com.keeponfirst.bopomofotv.session.JourneyDay
import com.keeponfirst.bopomofotv.session.JourneyPool
import com.keeponfirst.bopomofotv.session.JourneyRules
import com.keeponfirst.bopomofotv.session.Phase
import com.keeponfirst.bopomofotv.session.ResumePoint
import com.keeponfirst.bopomofotv.session.ResumeRules
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

    /** 已收集的貼紙。 */
    suspend fun stickers(): Set<StickerId> =
        dataStore.data.first()[STICKERS].orEmpty().mapNotNull { StickerId.decode(it) }.toSet()

    /** 給一張貼紙。同一項只給一次：已經有了回 false，第一次拿到回 true。 */
    suspend fun award(id: StickerId): Boolean {
        var added = false
        dataStore.edit { prefs ->
            StickerRules.award(prefs[STICKERS].orEmpty(), id)?.let {
                prefs[STICKERS] = it
                added = true
            }
        }
        return added
    }

    /** 大人陪按過「再練」的符號（不分日期）。小旅程排注音站時優先選。 */
    suspend fun againSymbols(): Set<String> =
        entries().filter { it.result == PracticeResult.AGAIN }.map { it.symbol }.toSet()

    /** 大人陪練過的符號（不分結果、不分日期）。聽音找符號優先出這些。 */
    suspend fun practicedSymbols(): Set<String> = entries().map { it.symbol }.toSet()

    /** 存著的小旅程（可能是之前某一天的）。 */
    suspend fun journey(): JourneyDay? {
        val prefs = dataStore.data.first()
        val date = prefs[JOURNEY_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return JourneyDay(date, JourneyRules.decodeStations(prefs[JOURNEY_STATIONS].orEmpty()), prefs[JOURNEY_DONE] ?: 0)
    }

    suspend fun saveJourney(day: JourneyDay) {
        dataStore.edit {
            it[JOURNEY_DATE] = day.date.toString()
            it[JOURNEY_STATIONS] = JourneyRules.encodeStations(day.stations)
            it[JOURNEY_DONE] = day.done
        }
    }

    /** 今天的小旅程：同一天接著存的那份；沒有或跨日就排新的並存起來。 */
    suspend fun journeyToday(pool: JourneyPool, date: LocalDate = LocalDate.now()): JourneyDay {
        val saved = journey()
        val day = JourneyRules.today(saved, date, pool, stickers(), againSymbols())
        if (day != saved) saveJourney(day)
        return day
    }

    /** 上次離開的模式與位置（「接著玩」用）。 */
    suspend fun resume(): ResumePoint? = ResumeRules.decode(dataStore.data.first()[RESUME])

    /** 存上次離開的位置；null＝清掉（做完了，或去了不能接著玩的模式）。 */
    suspend fun saveResume(point: ResumePoint?) {
        dataStore.edit { if (point == null) it.remove(RESUME) else it[RESUME] = ResumeRules.encode(point) }
    }

    /** 孩子選了簡單版的瑜伽動作 id（每個動作分開記）。 */
    suspend fun yogaEasy(): Set<String> = dataStore.data.first()[YOGA_EASY].orEmpty()

    suspend fun saveYogaEasy(poseId: String, easy: Boolean) {
        dataStore.edit {
            val now = it[YOGA_EASY].orEmpty()
            it[YOGA_EASY] = if (easy) now + poseId else now - poseId
        }
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
        val STICKERS = stringSetPreferencesKey("stickers")
        val JOURNEY_DATE = stringPreferencesKey("journey_date")
        val JOURNEY_STATIONS = stringPreferencesKey("journey_stations")
        val JOURNEY_DONE = intPreferencesKey("journey_done")
        val RESUME = stringPreferencesKey("resume")
        val YOGA_EASY = stringSetPreferencesKey("yoga_easy")
    }
}
