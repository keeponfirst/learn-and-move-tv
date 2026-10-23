package com.keeponfirst.bopomofotv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AbcParserTest {
    private val cards = AbcParser.parse(File("src/main/assets/abc/cards.json").readText(Charsets.UTF_8))

    @Test
    fun bundledListHasAToZInOrder() {
        assertEquals(('A'..'Z').map { it.toString() }, cards.map { it.upper })
        assertEquals(('a'..'z').map { it.toString() }, cards.map { it.lower })
    }

    @Test
    fun assetPathsPointUnderAbcFolder() {
        // AudioPlayer.listAvailable 只列 abc/audio；路徑跑出這個資料夾，App 會當成缺音而靜音。
        cards.forEach {
            assertTrue(it.image, it.image.startsWith("abc/img/"))
            assertTrue(it.audioLetter, it.audioLetter.startsWith("abc/audio/"))
            assertTrue(it.audioWord, it.audioWord.startsWith("abc/audio/"))
        }
    }
}
