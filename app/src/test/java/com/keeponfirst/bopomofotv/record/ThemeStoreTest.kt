package com.keeponfirst.bopomofotv.record

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.keeponfirst.bopomofotv.ui.ThemeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 大人選的主題要存住：重開 App（新的 DataStore 讀同一個檔）還是同一套。 */
class ThemeStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** 開一個 DataStore，用完關掉（同一個檔同時只能有一個 DataStore）。 */
    private fun <T> withStore(file: File, block: suspend (ThemeStore) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
            return runBlocking { block(ThemeStore(dataStore)) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun firstLaunchUsesDefaultB() {
        val file = File(folder.root, "settings.preferences_pb")
        assertEquals(ThemeId.B, withStore(file) { it.theme() })
    }

    @Test
    fun choiceSurvivesRestart() {
        val file = File(folder.root, "settings.preferences_pb")
        withStore(file) { it.save(ThemeId.C) }
        assertEquals(ThemeId.C, withStore(file) { it.theme() })
        withStore(file) { it.save(ThemeId.A) }
        assertEquals(ThemeId.A, withStore(file) { it.theme() })
    }

    @Test
    fun unknownStoredValueFallsBackToDefault() {
        val file = File(folder.root, "settings.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
            runBlocking { dataStore.edit { it[stringPreferencesKey("theme")] = "removed-theme" } }
        } finally {
            scope.cancel()
        }
        assertEquals(ThemeId.B, withStore(file) { it.theme() })
    }
}
