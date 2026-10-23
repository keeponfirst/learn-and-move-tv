package com.keeponfirst.bopomofotv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.darkColorScheme
import com.keeponfirst.bopomofotv.R

/**
 * Noto Sans TC 子集（SIL OFL 1.1）：Bold 700、Black 900。
 * 子集只收 App 用到的字。加新字串後要重產字型，否則缺的字會退回系統字型。
 */
val NotoSansTc = FontFamily(
    Font(R.font.notosanstc_bold, FontWeight.Bold),
    Font(R.font.notosanstc_black, FontWeight.Black),
)

/** 顏色、字級等 token 在 Tokens.kt；三套主題的數值在 AppTheme.kt。全 App 預設用 Bold。 */
@Composable
fun BopomofoTheme(theme: AppTheme, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        CompositionLocalProvider(LocalAppTheme provides theme) {
            ProvideTextStyle(LocalTextStyle.current.merge(TextStyle(fontFamily = NotoSansTc, fontWeight = FontWeight.Bold)), content)
        }
    }
}
