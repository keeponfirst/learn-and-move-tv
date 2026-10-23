package com.keeponfirst.bopomofotv.audio

import com.keeponfirst.bopomofotv.data.AbcCard
import com.keeponfirst.bopomofotv.data.AbcWord
import com.keeponfirst.bopomofotv.data.ZhuyinWord
import com.keeponfirst.bopomofotv.data.Card
import com.keeponfirst.bopomofotv.data.CardAudio
import com.keeponfirst.bopomofotv.data.CardParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudioResolverTest {
    private val b = Card(1, 1, "ㄅ", "", "爸爸", "ㄅㄚˋ ˙ㄅㄚ", "", audio = CardAudio("s_01_b.m4a", "k_01_baba.m4a"))

    @Test
    fun moeFilesFollowStandardOrderBySymbolNotCardOrder() {
        assertEquals("F1.WAV", AudioResolver.moeFileFor("ㄅ"))
        // ㄓ 在卡片是 order 18，在標準序是第 15 個
        assertEquals("F15.WAV", AudioResolver.moeFileFor("ㄓ"))
        assertEquals("F21.WAV", AudioResolver.moeFileFor("ㄙ"))
        assertEquals("F22.WAV", AudioResolver.moeFileFor("ㄚ"))
        assertEquals("F34.WAV", AudioResolver.moeFileFor("ㄦ"))
        assertEquals("F35.WAV", AudioResolver.moeFileFor("ㄧ"))
        assertEquals("F37.WAV", AudioResolver.moeFileFor("ㄩ"))
        assertNull(AudioResolver.moeFileFor("A"))
    }

    @Test
    fun everyCardMapsToADistinctBundledMoeFile() {
        val cards = CardParser.parse(File("src/main/assets/cards.json").readText(Charsets.UTF_8))
        val files = cards.map { AudioResolver.moeFileFor(it.symbol) }
        assertTrue(files.all { it != null })
        assertEquals(37, files.toSet().size)
        files.forEach { assertTrue("$it 沒打包進 assets", File("src/main/assets/audio/moe/$it").exists()) }
    }

    @Test
    fun customSymbolRecordingWinsOverMoe() {
        val resolver = AudioResolver(setOf("audio/s_01_b.m4a", "audio/moe/F1.WAV"))
        assertEquals(listOf("audio/s_01_b.m4a"), resolver.symbolPaths(b))
    }

    @Test
    fun customFolderIsFound() {
        val resolver = AudioResolver(setOf("audio/custom/s_01_b.m4a", "audio/custom/p_done.m4a"))
        assertEquals(listOf("audio/custom/s_01_b.m4a"), resolver.symbolPaths(b))
        assertEquals(listOf("audio/custom/p_done.m4a"), resolver.phrasePaths(Phrase.DONE))
    }

    @Test
    fun fallsBackToMoeWhenCustomMissing() {
        val resolver = AudioResolver(setOf("audio/moe/F1.WAV"))
        assertEquals(listOf("audio/moe/F1.WAV"), resolver.cardPaths(b))
    }

    @Test
    fun silentWhenNothingExists() {
        val resolver = AudioResolver(emptySet())
        assertEquals(emptyList<String>(), resolver.cardPaths(b))
        assertEquals(emptyList<String>(), resolver.phrasePaths(Phrase.CORRECT))
    }

    @Test
    fun customFolderWinsOverBundledVoice() {
        // 內建配音在 audio/；家長自錄音檔放 audio/custom/，同名時要優先。
        val resolver = AudioResolver(setOf("audio/k_01_baba.m4a", "audio/custom/k_01_baba.m4a"))
        assertEquals(listOf("audio/custom/k_01_baba.m4a"), resolver.keywordPaths(b))
    }

    @Test
    fun abcPlaysLetterThenWordAndSkipsMissing() {
        val a = AbcCard(1, "A", "a", "apple", "蘋果", "abc/img/a_apple.png", "abc/audio/l_a.m4a", "abc/audio/w_apple.m4a")
        val both = AudioResolver(setOf("abc/audio/w_apple.m4a", "abc/audio/l_a.m4a"))
        assertEquals(listOf("abc/audio/l_a.m4a"), both.letterPaths(a))
        assertEquals(listOf("abc/audio/l_a.m4a", "abc/audio/w_apple.m4a"), both.letterWordPaths(a))

        val wordOnly = AudioResolver(setOf("abc/audio/w_apple.m4a"))
        assertEquals(emptyList<String>(), wordOnly.letterPaths(a))
        assertEquals(listOf("abc/audio/w_apple.m4a"), wordOnly.letterWordPaths(a))
    }

    @Test
    fun extraWordPlaysSymbolThenItsOwnAudio() {
        val beizi = ZhuyinWord("杯子", "ㄅㄟ ˙ㄗ", audio = "zhuyin/audio/beizi.m4a")
        val resolver = AudioResolver(setOf("audio/moe/F1.WAV", "audio/k_01_baba.m4a", "zhuyin/audio/beizi.m4a"))
        assertEquals(listOf("audio/moe/F1.WAV", "zhuyin/audio/beizi.m4a"), resolver.cardPaths(b, beizi))
        // 其他例詞缺音：只播符號音，不會誤播關鍵字「爸爸」。
        assertEquals(listOf("audio/moe/F1.WAV"), AudioResolver(setOf("audio/moe/F1.WAV", "audio/k_01_baba.m4a")).cardPaths(b, beizi))
    }

    @Test
    fun keywordWordPrefersCustomThenWordFileAudio() {
        val baba = ZhuyinWord("爸爸", "ㄅㄚˋ ˙ㄅㄚ", audio = "zhuyin/audio/baba.m4a")
        assertEquals(listOf("audio/k_01_baba.m4a"), AudioResolver(setOf("audio/k_01_baba.m4a", "zhuyin/audio/baba.m4a")).keywordPaths(b, baba))
        assertEquals(listOf("zhuyin/audio/baba.m4a"), AudioResolver(setOf("zhuyin/audio/baba.m4a")).keywordPaths(b, baba))
    }

    @Test
    fun abcExtraWordUsesItsOwnAudio() {
        val a = AbcCard(1, "A", "a", "apple", "蘋果", "abc/img/a_apple.png", "abc/audio/l_a.m4a", "abc/audio/w_apple.m4a")
        val resolver = AudioResolver(setOf("abc/audio/l_a.m4a", "abc/audio/w_apple.m4a", "abc/audio/w_ant.m4a"))
        assertEquals(listOf("abc/audio/l_a.m4a", "abc/audio/w_ant.m4a"), resolver.letterWordPaths(a, AbcWord("ant", "螞蟻", audio = "abc/audio/w_ant.m4a")))
        assertEquals(listOf("abc/audio/l_a.m4a"), resolver.letterWordPaths(a, AbcWord("axe", "斧頭", audio = "abc/audio/w_axe.m4a")))
    }

    @Test
    fun keywordHasNoMoeFallback() {
        val resolver = AudioResolver(setOf("audio/moe/F1.WAV"))
        assertEquals(emptyList<String>(), resolver.keywordPaths(b))
    }
}
