package com.keeponfirst.bopomofotv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

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
    fun focusedHomeCardStaysInsideSafeAreaInEveryTheme() {
        Themes.all.forEach { theme ->
            val size = cardSize(theme)
            val n = HomeEntry.entries.size
            val row = HomeEntry.entries.sumOf { size.widthOf(it).value.toDouble() }.toFloat() + size.gap.value * (n - 1)
            // 整排在安全區內置中。
            val left = (screenW - row) / 2
            val right = left + row
            // 聚焦時往外多長出 (倍率 − 1) / 2 個卡寬；傾斜再讓角往外約 半高 × sin(角度)。最左是較寬的小旅程卡。
            fun grow(w: Float) = w * (theme.focusScale - 1) / 2 + size.height.value / 2 * abs(sin(Math.toRadians(theme.focusRotateDeg.toDouble()))).toFloat()
            val leftGrow = grow(size.widthOf(HomeEntry.entries.first()).value)
            val rightGrow = grow(size.widthOf(HomeEntry.entries.last()).value)
            assertTrue("${theme.name} 左緣 ${left - leftGrow} dp", left - leftGrow >= SafeArea.Horizontal.value)
            assertTrue("${theme.name} 右緣 ${right + rightGrow} dp", right + rightGrow <= screenW - SafeArea.Horizontal.value)
        }
    }
}
