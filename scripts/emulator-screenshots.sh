#!/usr/bin/env bash
# 在 Android TV 模擬器上截四張圖，並用 logcat 確認播音有找到 MOE 檔。
#
# 用法：scripts/emulator-screenshots.sh [輸出資料夾]
#   預設輸出：build/screenshots/
#   QUICK=1：跳過 04-done（要等自己玩 10 分鐘）。
#   AVDMANAGER：avdmanager 路徑，預設 $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager。
#
# 步驟：建 AVD tv34（沒有才建）→ 無頭啟動 → 等開機 → 裝 debug APK → 清資料
#       → 首頁 01 → 大人陪 02 → 返回兩次回首頁 → 自己玩 03 → 返回 → ABC 05
#       → 等 10 分鐘 04（ABC 的結束頁）→ 關模擬器。
# 結束時一定關模擬器（trap）。任一步失敗就停下並印出原因。
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
AVDMANAGER="${AVDMANAGER:-$SDK/cmdline-tools/latest/bin/avdmanager}"
EMULATOR="$SDK/emulator/emulator"
ADB="$SDK/platform-tools/adb"
AVD=tv34
IMAGE="system-images;android-34;android-tv;arm64-v8a"
PKG=com.keeponfirst.bopomofotv
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$ROOT/build/screenshots}"
TMP="$ROOT/build/emulator"   # 截圖與 log 先放這裡（git 忽略），失敗時留著查
rm -rf "$TMP"
mkdir -p "$TMP"

fail() { echo "失敗：$*（log 在 $TMP）" >&2; exit 1; }

trap '"$ADB" emu kill >/dev/null 2>&1 || true' EXIT

if ! "$EMULATOR" -list-avds | grep -qx "$AVD"; then
  echo "建 AVD $AVD"
  echo no | "$AVDMANAGER" create avd -n "$AVD" -k "$IMAGE" -d tv_1080p || fail "avdmanager 建 AVD"
fi

echo "啟動模擬器（無頭）"
"$EMULATOR" -avd "$AVD" -no-window -no-audio -no-snapshot -gpu swiftshader_indirect >"$TMP/emulator.log" 2>&1 &
"$ADB" wait-for-device
for _ in $(seq 1 180); do
  [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 2
done
[ "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" = "1" ] || fail "6 分鐘內沒開機完成"

echo "建置並安裝"
(cd "$ROOT" && ./gradlew -q assembleDebug) || fail "assembleDebug"
"$ADB" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" || fail "adb install"
"$ADB" shell pm clear "$PKG" >/dev/null
"$ADB" logcat -c

key() { "$ADB" shell input keyevent "$1"; sleep "${2:-2}"; }
shot() { "$ADB" exec-out screencap -p >"$TMP/$1" || fail "screencap $1"; echo "截圖 $1"; }

"$ADB" shell am start -W -n "$PKG/.MainActivity" >/dev/null || fail "am start"
sleep 3
shot 01-home.png

key KEYCODE_DPAD_CENTER 3   # 大人陪（首頁預設焦點）
key KEYCODE_DPAD_CENTER 3   # 確定鍵：播符號音（ㄅ → audio/moe/F1.WAV）
shot 02-card-parent.png

key KEYCODE_BACK            # 大人陪 → 結束頁
key KEYCODE_BACK            # 結束頁 → 首頁
key KEYCODE_DPAD_RIGHT 1    # 焦點移到「自己玩」
key KEYCODE_DPAD_CENTER 3   # 自己玩：第一張自動播音
shot 03-card-solo.png

key KEYCODE_BACK            # 自己玩 → 首頁（焦點回到「大人陪」）
key KEYCODE_DPAD_RIGHT 1
key KEYCODE_DPAD_RIGHT 1    # 焦點移到「ABC」
key KEYCODE_DPAD_CENTER 3   # ABC：第一張自動播字母音
shot 05-card-abc.png

if [ "${QUICK:-0}" != "1" ]; then
  echo "等自己玩 10 分鐘到（約 605 秒）"
  sleep 605
  shot 04-done.png
fi

echo "logcat（AudioPlayer／MediaPlayer）"
"$ADB" logcat -d -s AudioPlayer:V MediaPlayer:V MediaPlayerNative:V >"$TMP/audio.log" || true
cat "$TMP/audio.log"
grep -q "播放 audio/moe/F1.WAV" "$TMP/audio.log" || fail "logcat 沒看到播放 audio/moe/F1.WAV"
if grep -q "無法播放\|播放失敗" "$TMP/audio.log"; then fail "AudioPlayer 報錯，見上方 log"; fi

mkdir -p "$OUT"
for f in "$TMP"/*.png; do cp "$f" "$OUT/"; done
echo "完成：截圖在 $OUT"
