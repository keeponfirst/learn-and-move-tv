package com.keeponfirst.bopomofotv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Background = Color(0xFF1B2F38)
val SymbolColor = Color(0xFFFFD666)
val TextColor = Color(0xFFFFFFFF)
val SubtleColor = Color(0xFFB0BEC5)

@Composable
fun BopomofoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
