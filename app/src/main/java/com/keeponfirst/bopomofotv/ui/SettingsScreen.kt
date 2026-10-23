package com.keeponfirst.bopomofotv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/** 設定頁的按鍵提示。 */
private const val SETTINGS_HINT = "← → 選擇　OK 套用　返回 離開"

/**
 * 大人設定：選主題。三張預覽卡（各主題的首頁縮圖）左右選，確定鍵套用（[onApply] 會存起來並立刻換主題），返回鍵離開。
 * 進來時選在目前用的主題上。
 */
@Composable
fun SettingsScreen(current: ThemeId, onApply: (ThemeId) -> Unit, onExit: () -> Unit) {
    val themes = Themes.all
    var index by remember { mutableIntStateOf(themes.indexOfFirst { it.id == current }.coerceAtLeast(0)) }
    BackHandler { onExit() }

    RemoteKeyBox(
        backdrop = LocalAppTheme.current.bgHome,
        onKey = { key ->
            when (key) {
                RemoteKey.LEFT -> index = (index - 1).coerceAtLeast(0)
                RemoteKey.RIGHT -> index = (index + 1).coerceAtMost(themes.lastIndex)
                RemoteKey.OK -> onApply(themes[index].id)
                else -> Unit
            }
        },
    ) {
        Box(Modifier.fillMaxSize().safeArea()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                InfoPill { Text("大人設定・選主題", fontSize = TextSize.Heading, color = pillColor(), fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(Spacing.M))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    themes.forEachIndexed { i, theme -> PreviewCard(theme, selected = i == index, inUse = theme.id == current) }
                }
            }
            KeyHint(SETTINGS_HINT, Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun pillColor() = LocalAppTheme.current.let { if (it.id == ThemeId.B) it.hudText else it.textPrimary }

/** 一張預覽卡：縮圖＋名稱，使用中的加標籤。選到的放大並加粗框（框色用目前主題的焦點色）。 */
@Composable
private fun PreviewCard(theme: AppTheme, selected: Boolean, inUse: Boolean) {
    val ui = LocalAppTheme.current
    val f by animateFloatAsState(if (selected) 1f else 0f, tween(Motion.FOCUS_MS), label = "preview")
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.graphicsLayer {
            val s = 1f + 0.08f * f
            scaleX = s
            scaleY = s
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(256.dp, 144.dp)
                .border(if (selected) 6.dp else 2.dp, if (selected) ui.focusFrame else ui.outline.copy(alpha = 0.5f), shape)
                .padding(if (selected) 6.dp else 2.dp),
        ) {
            rememberAssetImage(theme.preview)?.let { Image(it, theme.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.height(Spacing.S))
        InfoPill { Text(theme.name, fontSize = 26.sp, color = pillColor(), fontWeight = FontWeight.Black) }
        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            if (inUse) {
                Text(
                    // 字型子集沒有「使」，用子集裡有的字。
                    "目前用這套",
                    Modifier.background(ui.focusFrame, RoundedCornerShape(50)).padding(horizontal = Spacing.S),
                    fontSize = TextSize.Info,
                    color = when (ui.id) {
                        ThemeId.A -> androidx.compose.ui.graphics.Color.White
                        ThemeId.B -> ui.outline
                        ThemeId.C -> androidx.compose.ui.graphics.Color(0xFF2A1F55)
                    },
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}
