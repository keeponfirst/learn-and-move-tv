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
            val paths = listOf(t.bgHome, t.bgCard, t.bgYoga, t.preview) + t.entryArt.filterNotNull() + FeedbackImages.paths(t.id)
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

    /** 小旅程與貼紙冊的素材路徑用主題資料夾組出來（上一個測試抓不到），這裡逐一列。 */
    @Test
    fun everyThemeHasJourneyAndStickerBookArt() {
        val journey = listOf(
            "journey_map_bg.jpg", "road_done_1.png", "road_done_2.png", "road_done_3.png",
            "station_sign.png", "station_sign_current.png", "station_sign_todo.png", "station_done_badge.png",
            "gift_open.png", "title_journey_done.png",
        )
        val book = listOf(
            "book_bg_zhuyin.jpg", "book_bg_abc.jpg", "book_bg_yoga.jpg",
            "sticker_base.png", "sticker_top.png", "sticker_empty.png", "sticker_new_glow.png", "title_new_sticker.png",
        )
        Themes.all.forEach { t ->
            val dir = themeDir(t)
            journey.forEach { assertTrue("${t.name} 少了 journey/$it", File(assets, "theme/$dir/journey/$it").isFile) }
            book.forEach { assertTrue("${t.name} 少了 stickerbook/$it", File(assets, "theme/$dir/stickerbook/$it").isFile) }
            // 貼紙冊背景照 StickerKind 的名字組檔名。
            com.keeponfirst.bopomofotv.record.StickerKind.entries.forEach {
                assertTrue(File(assets, "theme/$dir/stickerbook/book_bg_${it.name.lowercase()}.jpg").isFile)
            }
        }
    }

    @Test
    fun unknownStoredKeyFallsBackToDefault() {
        assertEquals(ThemeId.B, ThemeId.fromKey(null))
        assertEquals(ThemeId.B, ThemeId.fromKey("night"))
        ThemeId.entries.forEach { assertEquals(it, ThemeId.fromKey(it.key)) }
    }
}
