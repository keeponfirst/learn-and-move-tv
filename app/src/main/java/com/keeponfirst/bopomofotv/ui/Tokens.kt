package com.keeponfirst.bopomofotv.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 設計 token：顏色、字級、形狀、間距、動畫時長集中在這個檔。
 * 換視覺風格時先改這裡，畫面檔不寫死數值。
 */

// 顏色
val Background = Color(0xFF1B2F38)
val SymbolColor = Color(0xFFFFD666)
val TextColor = Color(0xFFFFFFFF)
val SubtleColor = Color(0xFFB0BEC5)

/** 暫停遮罩：60% 黑。 */
val ScrimColor = Color(0x99000000)

/** 字級。依用途命名，同一用途全 App 用同一個值。 */
object TextSize {
    val Hero = 120.sp // 結束頁、暫停、倒數、瑜伽開場套名
    val Display = 72.sp // 選單元、選一套的主字
    val Headline = 64.sp // 瑜伽開場副標
    val Word = 56.sp // 例詞、單元符號列
    val PoseName = 52.sp // 瑜伽動作中文名（有圖時）
    val Title = 48.sp // 首頁標題、首頁按鈕
    val Heading = 40.sp // 頁面提問、暫停說明
    val Body = 32.sp // 一般按鈕字、ABC 中文
    val Caption = 30.sp // 瑜伽動作英文名
    val CaptionLine = 38.sp
    val Zhuyin = 28.sp // 例詞注音
    val Info = 24.sp // 進度、狀態
    val Hint = 20.sp // 卡片四角的大人提示
    val Note = 18.sp // 安全提醒、鎖定頁提示
    val KeyHint = 16.sp // 瑜伽按鍵提示
    val Legal = 14.sp // 授權標示
}

/** 按鈕形狀。現況是 tv-material 預設的膠囊形。 */
val ButtonShape = CircleShape

/** 間距。 */
object Spacing {
    val XS = 8.dp
    val S = 16.dp
    val M = 32.dp
    val L = 48.dp
    val XL = 64.dp
}

/**
 * 安全邊距：四邊各 5%。1080p 電視是 960×540 dp，所以左右 48 dp、上下 27 dp。
 * 所有文字與可聚焦元件都要在這個範圍內。
 */
object SafeArea {
    val Horizontal = 48.dp
    val Vertical = 27.dp
}

/** 把內容縮進安全區。 */
fun Modifier.safeArea(): Modifier = padding(horizontal = SafeArea.Horizontal, vertical = SafeArea.Vertical)

/** 動畫時長（毫秒）。 */
object Motion {
    const val SHORT_MS = 200
}
