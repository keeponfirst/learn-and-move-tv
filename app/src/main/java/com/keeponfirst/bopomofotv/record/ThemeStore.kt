package com.keeponfirst.bopomofotv.record

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.keeponfirst.bopomofotv.ui.ThemeId
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 大人設定選的主題。存在電視本機，重開 App 記住。沒存過或值認不得時用預設主題。 */
class ThemeStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    suspend fun theme(): ThemeId = ThemeId.fromKey(dataStore.data.first()[THEME])

    suspend fun save(id: ThemeId) {
        dataStore.edit { it[THEME] = id.key }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
    }
}
