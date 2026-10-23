package com.keeponfirst.bopomofotv.ui

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** 顏色、字級等 token 在 Tokens.kt。 */
@Composable
fun BopomofoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
