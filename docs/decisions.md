# 技術決定

這份文件記錄主要的技術選擇和理由，給想改程式的人參考。

## 版本

| 項目 | 版本 | 理由 |
|---|---|---|
| AGP | 8.13.2 | 8.x 的最後一條線，支援到 API 36。 |
| Gradle wrapper | 8.14.3 | AGP 8.13 最低要 Gradle 8.13。 |
| Kotlin | 2.2.21 | tv-material 1.1.0 需要 Kotlin 2.1 以上。Compose Compiler 外掛與 Kotlin 同版。 |
| Compose BOM | 2026.06.01（Compose 1.11.x） | Compose 1.12 要 AGP 9 與 compileSdk 37，暫不升。 |
| tv-material / tv-foundation | 1.1.0 / 1.0.0 | 穩定版。 |
| compileSdk / minSdk / targetSdk | 36 / 26 / 35 | minSdk 26 讓 `java.time.LocalDate` 不需 desugaring。 |
| datastore-preferences | 1.1.7 | 見「紀錄」。 |
| kotlinx-serialization-json | 1.9.0 | 解析 JSON。Android 的 `org.json` 在本機單元測試是 stub，不能用。 |

## 音檔：MediaPlayer

- 用系統內建的 `MediaPlayer`，不用 ExoPlayer（Media3）。
- 只播 assets 裡的短檔，一次一檔、依序播。不需要串流或播放清單 UI，也不想讓 APK 變大。
- 依序播放用 `OnCompletionListener` 接下一檔。檔案打不開或播放錯誤就記 log 跳下一檔，不丟例外。
- 整個 Activity 共用一個 MediaPlayer，換檔用 `reset()`，`onDestroy` 才 `release()`。
  - 每檔 release 一次，logcat 會出現 `mediaplayer went away with unhandled events`。
- MediaPlayer 用 `AssetManager.openFd()` 讀檔，檔案不能被壓縮。`app/build.gradle.kts` 的 `noCompress` 列了 wav/WAV/m4a/mp3。

## 音檔對應

- 符號音：家長自錄音檔（`audio/custom/` 或 `audio/`）→ 教育部手冊 `audio/moe/F{n}.WAV` → 靜音。
- 關鍵字音、固定語句：家長自錄音檔（`audio/custom/`）→ 內建配音（`audio/`）→ 靜音。
- F 編號用**符號**對標準序（ㄅ…ㄙ、ㄚ…ㄦ、ㄧㄨㄩ），不用 cards.json 的 order。卡片順序和標準序在部分單元不同。
- 6 段固定語句的檔名寫死在 `audio/AudioResolver.kt` 的 `Phrase`。
- 不使用 cards.json 的 `handbookAudioUrl`。App 不連網。

## 紀錄：Preferences DataStore

- 選 DataStore，不用 Room。
- 資料只有「符號 × 日期 × 答對／再練」的計數，最多幾千筆鍵值。Room 要 KSP 與 schema 管理，對這個量太重。
- key 格式 `2026-10-12|ㄅ|correct`，值是當天次數。
- 另存大人陪進度：`parent_unit`、`parent_attempts`、`parent_streak`、`parent_resume_phase`、`parent_resume`。
- 另存貼紙與小旅程，見「今天的小旅程與貼紙冊」。
- 只存電視本機。App 沒有 INTERNET 權限，`allowBackup=false`。

## 畫面與遙控器

- 不用 Navigation 函式庫。畫面用一個 `Screen` 狀態切換，畫面少，夠用。
- 按鍵只認 KeyDown 且 `repeatCount == 0`。長按不會連續換卡（小孩常按住不放）。
- 確定鍵認 `DPAD_CENTER`、`ENTER`、`NUMPAD_ENTER`、`BUTTON_A`、`BUTTON_SELECT`。
- 返回鍵交給 `BackHandler`，按鍵處理器不吃返回鍵。
- 返回鍵路徑：大人陪 → 結束頁 → 首頁 → 離開 App。自己玩 → 首頁。連按返回最終回到電視主畫面。
- 卡片版面：符號、關鍵字、關鍵字注音三行置中，上下各留 48 dp。
  - 符號字級＝畫面高度的 50%。
  - 中文字型上下自帶留白，符號文字框約 1.5 倍字級高，`lineHeight` 壓不掉。
  - 所以符號放在一個字級高的 Box 裡，Text 用 `wrapContentHeight(unbounded = true)`，留白畫到框外。
- 大人陪下方畫出「再聽一次／答對了／再練練」三顆提示，對應確定鍵、上鍵、下鍵。按鍵行為不變，按到的那顆會亮。自己玩不顯示。
- 字級、形狀、間距、動畫時長集中在 `ui/Tokens.kt`；三套主題的顏色、素材路徑在 `ui/AppTheme.kt`。
- 安全邊距四邊各 5%（左右 48 dp、上下 27 dp）。角落文字用 `Modifier.safeArea()`。首頁按鈕聚焦放大後也要在安全區內，有單元測試。
- 首頁記住最後聚焦的入口。從任何模式回首頁，焦點回到同一顆。
- 瑜伽暫停時蓋 60% 黑色遮罩，中間顯示「暫停」和「按 OK 繼續」。
- 10 分鐘計時用 `elapsedRealtime` 起算，不受改系統時間影響。

## 大人陪流程

規則全在 `session/ParentSession.kt` 的 `ParentRules`，都是純函式，有單元測試。畫面只負責呼叫與播音。

- 一次一個單元。左右鍵只在本輪內移動，不跨單元。
- 新卡段照 cards.json 順序。最後一張再按右鍵 → 播「再練一次」，卡打亂進複習段。
- 複習段最後一張再按右鍵 → 本次做完，進結束頁。
- 下鍵：第 1 次把這張卡排到本輪最後，停在原卡重播。第 2 次直接換下一張。
- 10 分鐘到或按返回鍵：存「目前這張起剩下的卡」，下次接著做。
- 進下一單元：連續 2 次每張卡第一次就答對，或同一單元做完 4 次。最後一個單元之後回到第 1 單元。
- 存檔用 App 層的 coroutine scope。卡片頁的 scope 會隨換頁取消，存檔可能做一半。

## 自己玩與 ABC

- 任一方向鍵或確定鍵都換下一張並播音。幼兒還分不清方向鍵，任何鍵都有反應比較不挫折。
- ABC 的確定鍵不換卡，播「字母音 → 單字音」。
- 10 分鐘到停在結束頁，不顯示按鈕，只能按返回鍵回首頁。
- 缺圖：圖位改放大字。缺音：靜音。都不會當機。

## 瑜伽

- 開場 → 每個姿勢「口令 → 倒數」→ 結束。規則在 `session/YogaSession.kt`。
- 口令播完才開始倒數。確定鍵暫停／繼續，右鍵跳下一個，離開 App 自動暫停。
- 不偵測動作、不計分。原本那套做 2 輪，主題套做 1 輪。

## 主題與大人設定

- 三套主題：A 繪本小屋、B 活力遊樂場（預設）、C 星空探險。選擇存在本機 DataStore（`settings`），重開 App 記住。
- 大人設定入口是首頁右上角的齒輪。只有從卡片按「上」才到得了，左右鍵不會經過它。
- 長按 OK 2 秒才進得去；短按只顯示「請大人長按 OK」。小孩亂按多半是短按或連按。判定邏輯在 `HoldGate`，有單元測試。
- 計時用系統時鐘，不用按鍵事件的時間戳：遙控器重複送 KeyDown 時，時間戳不一定可靠。
- 字型：Noto Sans TC 子集（Bold、Black，SIL OFL 1.1），只收 App 用到的字。加新字串前先確認字在子集裡，缺的字會退回系統字型。
- 答對回饋約 0.8 秒。每個元件只淡入、淡出一次，不閃爍；B 的背景光芒不旋轉。
- 換卡有 200 ms 滑入轉場；首頁焦點的放大與框線有 180 ms 過渡。

## 今天的小旅程與貼紙冊

- 規則都是純函式，有單元測試：排站與同日接續在 `session/Journey.kt`，貼紙只給一次與「看完一項」在 `record/Stickers.kt`。
- 排站：每類先挑優先清單（注音＝沒有貼紙或按過再練；字母、瑜伽＝沒有貼紙），清單空了就用全部。用日期輪流取，同一天結果固定。某類沒有內容就少那一站。
- 存在同一個 `practice` DataStore：`stickers`（字串集合，例 `z|ㄅ`、`a|A`、`y|tree_left`）、`journey_date`、`journey_stations`、`journey_done`。
- 每站重用原本的畫面：注音站＝自己玩卡片頁只放那個符號的卡，看完最後一張再按就算完成；字母站同理；動一動站＝瑜伽頁只放一個動作，不播開場。站內按返回回首頁，這站下次重來。
- 站內看完一項的所有例詞卡才算完成。這和「自己玩看完一個符號給貼紙」是同一條規則。
- 過場後要按 OK 才進下一站，不自動進站：瑜伽站需要大人在旁邊。
- 貼紙不另外畫：執行時把例詞圖或動作圖裁成正方形，疊上主題的底層、反光、符號章或名稱牌。瑜伽圖依內容範圍置中裁切。
- 地圖與貼紙冊用 960×540 dp 的設計框等比縮放（`DesignFrame`），站牌、格子才會對準背景圖。
- 首頁 5 張卡：小旅程 200 dp、其他 140 dp、間距 14 dp，整排置中。B 主題左邊的導遊放不下，改成小旅程卡上方的對話框。
- 不做連續天數：斷掉時孩子會失落，也會變成鼓勵每天看螢幕。收集冊不會斷。

## 沒做

- TTS 引擎（配音是預先生成的檔）、麥克風、網路、筆順動畫。
