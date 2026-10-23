package com.keeponfirst.bopomofotv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text

/** CC BY 4.0 要求的標示（教育部手冊開放部件 license.txt）。 */
const val MOE_ATTRIBUTION = "符號音檔：2017 © 教育部，國語注音符號手冊-開放部件，CC BY 4.0"

/** 第三方授權全文（Apache 2.0 等）放在 APK 的 assets/licenses/。 */
const val LICENSES_HINT = "完整授權見 assets/licenses"

@Composable
fun HomeScreen(onParent: () -> Unit, onSelf: () -> Unit, onAbc: () -> Unit, onYoga: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("動動學學", fontSize = 48.sp, color = TextColor)
            Spacer(Modifier.height(48.dp))
            // 四顆 200 dp ＋ 三個 32 dp 間距 ＝ 896 dp，放得進 1080p 電視的 960 dp 寬。
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                BigButton("大人陪", onParent, Modifier.focusRequester(first))
                BigButton("自己玩", onSelf)
                BigButton("ABC", onAbc)
                BigButton("瑜伽", onYoga)
            }
        }
        Text(
            "$MOE_ATTRIBUTION　｜　$LICENSES_HINT",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            fontSize = 14.sp,
            color = SubtleColor,
        )
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 48 sp：「大人陪」3 字約 144 dp，扣掉按鈕左右內距仍放得進 200 dp。
    Button(onClick = onClick, modifier = modifier.size(width = 200.dp, height = 150.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 48.sp)
        }
    }
}
