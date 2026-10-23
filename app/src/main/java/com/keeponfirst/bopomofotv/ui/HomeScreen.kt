package com.keeponfirst.bopomofotv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text

/** CC BY 4.0 要求的標示（教育部手冊開放部件 license.txt）。 */
const val MOE_ATTRIBUTION = "符號音檔：2017 © 教育部，國語注音符號手冊-開放部件，CC BY 4.0"

/** 第三方授權全文（Apache 2.0 等）放在 APK 的 assets/licenses/。 */
const val LICENSES_HINT = "完整授權見 assets/licenses"

/** 首頁的四個入口，順序同畫面由左到右。 */
enum class HomeEntry { PARENT, SELF, ABC, YOGA }

/**
 * 首頁按鈕尺寸。四顆 184 dp ＋ 三個 32 dp 間距 ＝ 832 dp。
 * 聚焦放大 1.1 倍後最外緣離畫面邊約 55 dp，仍在 48 dp 安全邊距內。
 * 48 sp「大人陪」3 字約 144 dp，扣掉按鈕左右內距 16 dp 仍放得進 184 dp。
 */
internal val HOME_BUTTON_WIDTH = 184.dp
internal val HOME_BUTTON_HEIGHT = 150.dp
internal val HOME_BUTTON_GAP = Spacing.M

/** tv-material Button 的預設聚焦放大倍率。 */
internal const val HOME_FOCUS_SCALE = 1.1f

/**
 * 首頁。進來時焦點放在 [focus]；焦點移動時呼叫 [onFocus]。
 * App 層記住最後一次的入口，從任何模式回來焦點都回到同一顆。
 */
@Composable
fun HomeScreen(
    focus: HomeEntry,
    onFocus: (HomeEntry) -> Unit,
    onParent: () -> Unit,
    onSelf: () -> Unit,
    onAbc: () -> Unit,
    onYoga: () -> Unit,
) {
    val requesters = remember { HomeEntry.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { requesters.getValue(focus).requestFocus() }

    @Composable
    fun Entry(entry: HomeEntry, label: String, onClick: () -> Unit) {
        BigButton(
            label,
            onClick,
            Modifier
                .focusRequester(requesters.getValue(entry))
                .onFocusChanged { if (it.isFocused) onFocus(entry) },
        )
    }

    Box(Modifier.fillMaxSize().background(Background).safeArea()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("動動學學", fontSize = TextSize.Title, color = TextColor)
            Spacer(Modifier.height(Spacing.L))
            Row(horizontalArrangement = Arrangement.spacedBy(HOME_BUTTON_GAP)) {
                Entry(HomeEntry.PARENT, "大人陪", onParent)
                Entry(HomeEntry.SELF, "自己玩", onSelf)
                Entry(HomeEntry.ABC, "ABC", onAbc)
                Entry(HomeEntry.YOGA, "瑜伽", onYoga)
            }
        }
        Text(
            "$MOE_ATTRIBUTION　｜　$LICENSES_HINT",
            modifier = Modifier.align(Alignment.BottomCenter),
            fontSize = TextSize.Legal,
            color = SubtleColor,
        )
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.size(width = HOME_BUTTON_WIDTH, height = HOME_BUTTON_HEIGHT),
        shape = ButtonDefaults.shape(ButtonShape),
        scale = ButtonDefaults.scale(focusedScale = HOME_FOCUS_SCALE),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = TextSize.Title)
        }
    }
}
