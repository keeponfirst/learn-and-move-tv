package com.keeponfirst.bopomofotv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 三套主題都要能完整畫出每一頁：少一個顏色或少一張圖，切到那套時畫面就會缺東西。
 * 單元測試的工作目錄是 app/。
 */
class ThemeTokensTest {
    private val assets = File("src/main/assets")

    @Test
    fun threeThemesWithBAsDefault() {
        assertEquals(listOf(ThemeId.A, ThemeId.B, ThemeId.C), Themes.all.map { it.id })
        assertEquals(ThemeId.B, ThemeId.DEFAULT)
        assertEquals(3, Themes.all.map { it.name }.toSet().size)
    }

    @Test
    fun everyThemeHasOneValuePerEntryAndButton() {
        Themes.all.forEach { t ->
            assertEquals("${t.name} 入口圖", HomeEntry.entries.size, t.entryArt.size)
            assertEquals("${t.name} 入口色", HomeEntry.entries.size, t.entryColors.size)
            assertEquals("${t.name} 入口厚度色", HomeEntry.entries.size, t.entryShade.size)
            assertEquals("${t.name} 按鈕色", ParentButton.entries.size, t.buttonColors.size)
            assertEquals("${t.name} 按鈕厚度色", ParentButton.entries.size, t.buttonShade.size)
            assertTrue("${t.name} 焦點要放大", t.focusScale > 1f)
            assertTrue("${t.name} 焦點標籤", t.focusTag.isNotBlank())
        }
    }

    @Test
    fun everyThemeImageExists() {
        Themes.all.forEach { t ->
            val paths = listOf(t.bgHome, t.bgCard, t.bgYoga, t.preview) + t.entryArt + FeedbackImages.paths(t.id)
            paths.forEach { assertTrue("${t.name} 少了 $it", File(assets, it).isFile) }
        }
    }

    /** 畫面程式裡直接寫的素材路徑（紙膠帶、閃光、星星列等）也要都在。 */
    @Test
    fun everyThemePathInSourceExists() {
        val pattern = Regex("\"(theme/[abc]/[^\"$]+\\.(png|jpg))\"")
        val found = File("src/main/java").walk().filter { it.extension == "kt" }
            .flatMap { file -> pattern.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
        assertTrue("應該找得到畫面寫的素材路徑", found.size > 10)
        found.forEach { assertTrue("少了 $it", File(assets, it).isFile) }
    }

    @Test
    fun unknownStoredKeyFallsBackToDefault() {
        assertEquals(ThemeId.B, ThemeId.fromKey(null))
        assertEquals(ThemeId.B, ThemeId.fromKey("night"))
        ThemeId.entries.forEach { assertEquals(it, ThemeId.fromKey(it.key)) }
    }
}
