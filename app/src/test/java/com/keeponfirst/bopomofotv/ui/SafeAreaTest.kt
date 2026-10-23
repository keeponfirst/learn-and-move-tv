package com.keeponfirst.bopomofotv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 安全邊距照電視版面規範：四邊各 5%。內容不能超出，舊電視邊緣可能被裁掉。 */
class SafeAreaTest {
    // 1080p 電視的 dp 尺寸（xhdpi，1 dp ＝ 2 px）。
    private val screenW = 960f
    private val screenH = 540f

    @Test
    fun safeAreaIsFivePercentOfEachEdge() {
        assertEquals(screenW * 0.05f, SafeArea.Horizontal.value, 0.5f)
        assertEquals(screenH * 0.05f, SafeArea.Vertical.value, 0.5f)
    }

    @Test
    fun focusedHomeButtonStaysInsideSafeArea() {
        val w = HOME_BUTTON_WIDTH.value
        val row = w * HomeEntry.entries.size + HOME_BUTTON_GAP.value * (HomeEntry.entries.size - 1)
        // 最外側那顆聚焦時，往外多長出 (倍率 − 1) / 2 個按鈕寬。
        val outerEdge = (screenW - row) / 2 - w * (HOME_FOCUS_SCALE - 1) / 2
        assertTrue("聚焦按鈕外緣離邊 $outerEdge dp", outerEdge >= SafeArea.Horizontal.value)
    }
}
