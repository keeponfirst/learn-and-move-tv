package com.keeponfirst.bopomofotv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

/**
 * 結束頁：「今天練完了」大字＋「回首頁」按鈕。
 *
 * [locked]＝自己玩 10 分鐘到：方向鍵與確定鍵全部不回應，只剩返回鍵回首頁。
 * 不顯示按鈕，只留「按返回鍵回首頁」小字。孩子自己按不到下一輪，時間到就是到了。
 */
@Composable
fun DoneScreen(locked: Boolean, onEnter: () -> Unit, onHome: () -> Unit) {
    LaunchedEffect(Unit) { onEnter() }
    BackHandler { onHome() }

    if (locked) {
        RemoteKeyBox(onKey = {}) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DoneContent(locked = true, onHome = onHome) }
        }
    } else {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            DoneContent(locked = false, onHome = onHome)
        }
    }
}

@Composable
private fun DoneContent(locked: Boolean, onHome: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("今天練完了", fontSize = 120.sp, color = SymbolColor)
        Spacer(Modifier.height(48.dp))
        if (locked) {
            Text("按返回鍵回首頁", fontSize = 18.sp, color = SubtleColor)
        } else {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Button(onClick = onHome, modifier = Modifier.focusRequester(focus)) {
                Text("回首頁", fontSize = 32.sp, modifier = Modifier.padding(8.dp))
            }
        }
    }
}
