package com.keeponfirst.bopomofotv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** App 會處理的遙控器按鍵。返回鍵另由 BackHandler 處理；Home 鍵 App 收不到。 */
enum class RemoteKey { LEFT, RIGHT, UP, DOWN, OK }

/**
 * 方向鍵與確定鍵對應到 [RemoteKey]；其他鍵（返回、音量、媒體鍵）回傳 null，交給系統。
 * 確定鍵的 keycode 照官方 TV controllers 文件列出的五種。
 */
fun KeyEvent.remoteKey(): RemoteKey? = when (key) {
    Key.DirectionLeft -> RemoteKey.LEFT
    Key.DirectionRight -> RemoteKey.RIGHT
    Key.DirectionUp -> RemoteKey.UP
    Key.DirectionDown -> RemoteKey.DOWN
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA, Key.ButtonSelect -> RemoteKey.OK
    else -> null
}

/** 只有第一次按下算數。KeyUp 與長按重複都不觸發動作（小孩常按住不放）。 */
fun KeyEvent.isFirstPress(): Boolean = type == KeyEventType.KeyDown && nativeKeyEvent.repeatCount == 0
