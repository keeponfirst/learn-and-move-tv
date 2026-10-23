# 用 AI 助手安裝動動學學

這份檔案是給 AI 助手看的安裝說明。AI 助手例如 Claude Code，或 ChatGPT 的電腦代理。

家長的用法：把下面「中文版」或「English」整段複製，貼給 AI 助手。AI 助手會一步一步帶你把 App 裝進電視。

---

## 中文版

請你幫我把「動動學學」（Learn & Move）這個 Android TV App 裝進我家的電視。我不是工程師。請照下面的規則和步驟做。

### 規則（請你一定遵守）

- 每一步先用白話告訴我：要在電視的哪個選單、按哪裡。等我說「好了」再做下一步。
- **配對碼由我自己在電腦上輸入。** 你不要猜配對碼，也不要幫我填。
- 不要改電視的其他系統設定。
- 不要停用或解除安裝任何 App。
- 不要更換電視的主畫面（launcher）。
- 不要安裝本專案以外的任何東西。唯一的例外是下面「前提」裡的 Android platform-tools。
- 出錯就停下來，用白話說明發生什麼事。不要一直換方法硬試。

### 前提

1. 電腦和電視連在同一個 Wi-Fi。請先問我確認。
2. 電腦要有 Android platform-tools（裡面有 `adb` 指令）。
   - 先執行 `adb version` 檢查。
   - 沒有的話，只從官方網址下載安裝：https://developer.android.com/tools/releases/platform-tools
   - 安裝前先告訴我要裝什麼、裝在哪裡，等我同意。

### 步驟一：在電視開開發人員選項

告訴我在電視上做：

1. 設定 > 系統 > 關於。
2. 在「Android TV OS 版本號」上連按確定鍵約 7 次。
3. 畫面出現「你現在是開發人員」就成功了。

選單名稱依電視品牌不同，可能略有差異。

### 步驟二：無線偵錯配對

1. 告訴我在電視上做：設定 > 系統 > 開發人員選項 > 開「無線偵錯」（Wireless debugging）。這個功能需要 Android TV 13 以上。
2. 告訴我在無線偵錯裡選「使用配對碼配對裝置」。電視會顯示一組「IP:port」和 6 位數配對碼。
3. 請我念出畫面上的 IP 和 port（**不用念配對碼**）。
4. 在電腦執行下面的指令。指令會要求輸入配對碼，這時請我自己在電腦上輸入：
   ```
   adb pair <電視IP>:<配對port>
   ```
   如果你無法讓我在終端機直接輸入，就把整行指令給我，請我自己貼到終端機執行。

### 步驟三：連線

1. 請我念出無線偵錯畫面上「IP 位址和通訊埠」那一組。這組的 port 和配對用的不同。
2. 執行：
   ```
   adb connect <電視IP>:<連線port>
   adb devices
   ```
3. `adb devices` 列出電視、狀態是 `device`，就成功了。

**連不上時的備用法（5555 port）：** 有些電視配對成功後，連線一直 `failed to connect`。這時改連傳統的 adb port：

```
adb connect <電視IP>:5555
```

電視會跳出「允許偵錯？」的視窗。告訴我選「允許」。不需要勾「一律允許」。

5555 這種連線沒有加密，同一個網路裡的人都可能連上。只在我家網路短暫使用，裝完一定要關掉（見步驟五）。

### 步驟四：下載並安裝 APK

1. 從 GitHub Releases 下載最新版 APK。Releases 頁面在：
   ```
   https://github.com/keeponfirst/learn-and-move-tv/releases/latest
   ```
   APK 的直接下載網址：
   ```
   https://github.com/keeponfirst/learn-and-move-tv/releases/latest/download/learn-and-move-tv.apk
   ```
   只從這個網址下載，不要從其他網站找。
2. 安裝：
   ```
   adb install -r learn-and-move-tv.apk
   ```
3. 看到 `Success` 就裝好了。告訴我到電視主畫面「您的應用程式」列找「動動學學」。
4. 如果出現 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 或其他錯誤：停下來，把錯誤告訴我。**不要**自己解除安裝舊版。

### 步驟五：裝完收尾（一定要做）

告訴我在電視上做：

1. 設定 > 系統 > 開發人員選項 > 關閉「無線偵錯」。
2. 同一頁選「撤銷 USB 偵錯授權」（Revoke USB debugging authorizations），確認。這會讓電視忘記這台電腦。

然後在電腦執行 `adb disconnect`。

---

## English

Please help me install "Learn & Move" (動動學學), an Android TV app, on my TV. I am not an engineer. Follow the rules and steps below.

### Rules (you must follow them)

- Before each step, tell me in plain words which TV menu to open and what to press. Wait until I say "done".
- **I will type the pairing code on the computer myself.** Do not guess the code. Do not type it for me.
- Do not change any other TV system settings.
- Do not disable or uninstall any app.
- Do not change the TV home screen (launcher).
- Do not install anything except this project. The only exception is Android platform-tools (see "Before you start").
- If something fails, stop. Explain in plain words what happened. Do not keep trying other methods.

### Before you start

1. The computer and the TV must use the same Wi-Fi. Ask me to confirm.
2. The computer needs Android platform-tools (this gives the `adb` command).
   - Run `adb version` to check.
   - If it is missing, download it only from the official page: https://developer.android.com/tools/releases/platform-tools
   - Before you install it, tell me what and where. Wait for my OK.

### Step 1: Turn on Developer options on the TV

Tell me to do this on the TV:

1. Settings > System > About.
2. Press OK on "Android TV OS build" about 7 times.
3. When the TV says "You are now a developer", it worked.

Menu names can differ by TV brand.

### Step 2: Pair with wireless debugging

1. Tell me: Settings > System > Developer options > turn on "Wireless debugging". This needs Android TV 13 or later.
2. Tell me to choose "Pair device with pairing code". The TV shows an "IP:port" and a 6-digit code.
3. Ask me to read the IP and port to you. **Do not ask for the code.**
4. Run this command. When it asks for the code, I will type it myself:
   ```
   adb pair <TV IP>:<pairing port>
   ```
   If I cannot type into your terminal, give me the full command. I will paste and run it myself.

### Step 3: Connect

1. Ask me to read the "IP address & Port" on the wireless debugging screen. This port is not the pairing port.
2. Run:
   ```
   adb connect <TV IP>:<connect port>
   adb devices
   ```
3. If `adb devices` lists the TV as `device`, it worked.

**Fallback (port 5555):** Some TVs pair but then always show `failed to connect`. Then try the classic adb port:

```
adb connect <TV IP>:5555
```

The TV shows an "Allow debugging?" dialog. Tell me to choose "Allow". I do not need to tick "Always allow".

This connection is not encrypted. Anyone on the same network may connect. Use it only on my home network, and only for a short time. Turn it off after install (see Step 5).

### Step 4: Download and install the APK

1. Download the latest APK from GitHub Releases. The Releases page is:
   ```
   https://github.com/keeponfirst/learn-and-move-tv/releases/latest
   ```
   The direct APK link is:
   ```
   https://github.com/keeponfirst/learn-and-move-tv/releases/latest/download/learn-and-move-tv.apk
   ```
   Download only from this address. Do not search other sites.
2. Install:
   ```
   adb install -r learn-and-move-tv.apk
   ```
3. `Success` means it is installed. Tell me to look for "動動學學" in "Your apps" on the TV home screen.
4. If you see `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or any other error, stop. Tell me the error. **Do not** uninstall the old version.

### Step 5: Clean up after install (always do this)

Tell me to do this on the TV:

1. Settings > System > Developer options > turn off "Wireless debugging".
2. On the same screen, choose "Revoke USB debugging authorizations" and confirm. The TV then forgets this computer.

Then run `adb disconnect` on the computer.
