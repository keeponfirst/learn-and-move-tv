package com.keeponfirst.bopomofotv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 筆順動畫。每張注音卡按下鍵都要有筆順可播；缺一個符號，孩子按了就沒反應（會照舊換卡）。
 * 檔案壞掉不能讓 App 當掉，只是那個符號沒有筆順。
 */
class StrokesTest {
    private val assets = File("src/main/assets")
    private val dir = File(assets, StrokeRepository.ASSET_DIR)

    private fun bundled(): Map<String, StrokeSymbol> =
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { StrokeParser.parse(it.readText(Charsets.UTF_8)) }
            .associateBy { it.symbol }

    @Test
    fun parsesSymbolStrokesInOrderWithWidth() {
        val s = StrokeParser.parse(
            """{"symbol":"ㄒ","viewBox":"0 0 1024 1024","strokes":[{"d":"M200 300 L820 300","width":80},{"d":"M512 300 L512 860","width":80}],"extra":1}""",
        )
        assertNotNull(s)
        assertEquals("ㄒ", s!!.symbol)
        assertEquals(1024f, s.size)
        // 陣列順序＝筆順：先橫後豎。
        assertEquals(listOf("M200 300 L820 300", "M512 300 L512 860"), s.strokes.map { it.d })
        assertEquals(80f, s.strokes.first().width)
    }

    @Test
    fun brokenOrEmptyFilesAreSkipped() {
        listOf(
            "",
            "{bad",
            """{"symbol":"ㄅ","strokes":[]}""",
            """{"symbol":"ㄅ","strokes":[{"d":"","width":80}]}""",
            """{"symbol":"","strokes":[{"d":"M0 0 L1 1","width":80}]}""",
        ).forEach { assertNull(it, StrokeParser.parse(it)) }
    }

    @Test
    fun all37BundledSymbolsHaveStrokes() {
        val strokes = bundled()
        assertEquals("37 個注音符號", 37, strokes.size)
        strokes.values.forEach { s ->
            assertTrue("${s.symbol} 沒有筆畫", s.strokes.isNotEmpty())
            assertEquals("${s.symbol} viewBox", 1024f, s.size)
        }
    }

    @Test
    fun everyZhuyinCardHasStrokes() {
        val cards = CardParser.parse(File(assets, CardRepository.ASSET_PATH).readText(Charsets.UTF_8))
        val strokes = bundled()
        val missing = cards.map { it.symbol }.filter { it !in strokes }
        assertTrue("這些卡沒有筆順：$missing", missing.isEmpty())
    }

    @Test
    fun eachStrokeTakesBetween600And900Millis() {
        assertEquals(StrokeTiming.MIN_MS, StrokeTiming.strokeMillis(0f, 1024f))
        assertEquals(StrokeTiming.MAX_MS, StrokeTiming.strokeMillis(5000f, 1024f))
        val mid = StrokeTiming.strokeMillis(600f, 1024f)
        assertTrue("長度中等的筆畫在中間：$mid", mid in 601..899)
        assertTrue("長的筆畫比短的久", StrokeTiming.strokeMillis(900f, 1024f) > StrokeTiming.strokeMillis(300f, 1024f))
    }
}
