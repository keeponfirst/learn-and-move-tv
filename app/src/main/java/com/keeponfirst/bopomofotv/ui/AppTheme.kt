package com.keeponfirst.bopomofotv.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 三套可切換的視覺主題。[key] 存進 DataStore，改名會讓舊設定讀不到，不要改。
 * 預設 [B]。
 */
enum class ThemeId(val key: String) {
    A("storybook"),
    B("playground"),
    C("starry"),
    ;

    companion object {
        val DEFAULT = B

        /** 讀不到或認不得的值都回預設，不丟例外。 */
        fun fromKey(key: String?): ThemeId = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * 一套主題的 token 與素材路徑。顏色、尺寸照素材包的 tokens.json（單位 dp）。
 * 素材路徑都相對 assets/。各主題特有的裝飾（紙膠帶、彩帶、星座）由畫面依 [id] 決定要不要畫。
 */
data class AppTheme(
    val id: ThemeId,
    /** 設定頁顯示的名稱。 */
    val name: String,
    val background: Color,
    val bgHome: String,
    val bgCard: String,
    val bgYoga: String,
    /** 設定頁的預覽縮圖（首頁）。 */
    val preview: String,
    /** 首頁 4 張入口插圖，順序同 [HomeEntry]。 */
    val entryArt: List<String>,
    /** 入口卡顏色 4 個，順序同 [HomeEntry]。A 是插圖底板色，B 是卡片底色，C 是卡內星球色。 */
    val entryColors: List<Color>,
    /** 入口卡底下的立體厚度色。A 沒有，用投影色代替。 */
    val entryShade: List<Color>,
    /** 卡片頁的大卡底色。 */
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** 入口卡標題字色。 */
    val textOnEntry: Color,
    val symbol: Color,
    /** 外框色（卡片、按鈕）。 */
    val outline: Color,
    val focusFrame: Color,
    val focusGlow: Color,
    /** 頂部資訊膠囊的底色。 */
    val hud: Color,
    val hudText: Color,
    /** 大人陪三顆按鈕：再聽一次、答對了、再練練。 */
    val buttonColors: List<Color>,
    val buttonShade: List<Color>,
    val buttonText: Color,
    val buttonTextActive: Color,
    /** 按鈕被按到時的底色（A、C 用；B 用原色加框）。 */
    val buttonActive: Color,
    /** 進度：已完成、目前、未完成。 */
    val progressDone: Color,
    val progressCurrent: Color,
    val progressTodo: Color,
    /** 答對回饋的半透明遮罩（A、C）。B 用光暈圖，不用遮罩。 */
    val feedbackScrim: Color,
    val cardRadius: Dp,
    val buttonRadius: Dp,
    val borderNormal: Dp,
    val borderFocused: Dp,
    val focusScale: Float,
    val focusRotateDeg: Float,
    val focusTranslateY: Dp,
    /** 焦點卡下方的小標籤文字。 */
    val focusTag: String,
)

/** 三套主題的實際數值。 */
object Themes {
    private fun entries(dir: String) = listOf("parent", "self", "abc", "yoga").map { "theme/$dir/entry/entry_$it.png" }

    val A = AppTheme(
        id = ThemeId.A,
        name = "繪本小屋",
        background = Color(0xFFFBF1DE),
        bgHome = "theme/a/bg/bg_home.jpg",
        bgCard = "theme/a/bg/bg_page.jpg",
        bgYoga = "theme/a/bg/bg_page.jpg",
        preview = "theme/a/preview.jpg",
        entryArt = entries("a"),
        entryColors = listOf(Color(0xFFFFD9C2), Color(0xFFD5EEDF), Color(0xFFFFEAB0), Color(0xFFE5DBF8)),
        entryShade = List(4) { Color(0x1F785A3C) },
        surface = Color(0xFFFFFCF5),
        textPrimary = Color(0xFF4A3B35),
        textSecondary = Color(0xFF8A6E5A),
        textOnEntry = Color(0xFF4A3B35),
        symbol = Color(0xFFD9583B),
        outline = Color(0xFF4A3B35),
        focusFrame = Color(0xFFE8743B),
        focusGlow = Color(0x4C78461E),
        hud = Color(0x00000000),
        hudText = Color(0xFF4A3B35),
        buttonColors = List(3) { Color(0xFFFFFCF5) },
        buttonShade = List(3) { Color(0x24785A3C) },
        buttonText = Color(0xFF4A3B35),
        buttonTextActive = Color(0xFFE8743B),
        buttonActive = Color(0xFFFFE6C4),
        progressDone = Color(0xFF8CC084),
        progressCurrent = Color(0xFFE8743B),
        progressTodo = Color(0xFFEADCC4),
        feedbackScrim = Color(0x8CFBF1DE),
        cardRadius = 15.dp,
        buttonRadius = 13.dp,
        borderNormal = 3.dp,
        borderFocused = 6.dp,
        focusScale = 1.1f,
        focusRotateDeg = -1.5f,
        focusTranslateY = (-11).dp,
        focusTag = "按 OK 開始 ▶",
    )

    val B = AppTheme(
        id = ThemeId.B,
        name = "活力遊樂場",
        background = Color(0xFF26B0F2),
        bgHome = "theme/b/bg/bg_all.jpg",
        bgCard = "theme/b/bg/bg_all.jpg",
        bgYoga = "theme/b/bg/bg_all.jpg",
        preview = "theme/b/preview.jpg",
        entryArt = entries("b"),
        entryColors = listOf(Color(0xFFFF6B5B), Color(0xFFFFB61E), Color(0xFF2FC46B), Color(0xFF8A5CF6)),
        entryShade = listOf(Color(0xFFC9382A), Color(0xFFC98400), Color(0xFF178A45), Color(0xFF5A33B8)),
        surface = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF2D2250),
        textSecondary = Color(0xFF8A5CF6),
        textOnEntry = Color(0xFFFFFFFF),
        symbol = Color(0xFFFF5A4E),
        outline = Color(0xFF2D2250),
        focusFrame = Color(0xFFFFFFFF),
        focusGlow = Color(0xF2FFF596),
        hud = Color(0xEB2D2250),
        hudText = Color(0xFFFFFFFF),
        buttonColors = listOf(Color(0xFF3D8BFF), Color(0xFF2FC46B), Color(0xFFFF9F1C)),
        buttonShade = listOf(Color(0xFF1F5BC0), Color(0xFF178A45), Color(0xFFC06A00)),
        buttonText = Color(0xFFFFFFFF),
        buttonTextActive = Color(0xFFFFFFFF),
        buttonActive = Color(0xFFFFFFFF),
        progressDone = Color(0xFF2FC46B),
        progressCurrent = Color(0xFFFFD43B),
        progressTodo = Color(0xFFFFFFFF),
        feedbackScrim = Color(0x00000000),
        cardRadius = 24.dp,
        buttonRadius = 32.dp,
        borderNormal = 3.5.dp,
        borderFocused = 6.dp,
        focusScale = 1.08f,
        focusRotateDeg = -1.5f,
        focusTranslateY = 0.dp,
        focusTag = "OK 出發！",
    )

    val C = AppTheme(
        id = ThemeId.C,
        name = "星空探險",
        background = Color(0xFF1A1B4A),
        bgHome = "theme/c/bg/bg_home.jpg",
        bgCard = "theme/c/bg/bg_card.jpg",
        bgYoga = "theme/c/bg/bg_yoga.jpg",
        preview = "theme/c/preview.jpg",
        entryArt = entries("c"),
        entryColors = listOf(Color(0xFFFF8A7A), Color(0xFF5CE1E6), Color(0xFFFFD66B), Color(0xFFB79CFF)),
        entryShade = listOf(Color(0xFFE2546A), Color(0xFF2A9DB8), Color(0xFFE59A2C), Color(0xFF7A5BE0)),
        surface = Color(0x17FFFFFF),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFC9C6FF),
        textOnEntry = Color(0xFFFFFFFF),
        symbol = Color(0xFFFFE27A),
        outline = Color(0x38FFFFFF),
        focusFrame = Color(0xFFFFE27A),
        focusGlow = Color(0x8CFFD250),
        // 卡片頁背景右上有月亮，資訊字放在半透明深色膠囊上才讀得清楚。
        hud = Color(0x990A0C28),
        hudText = Color(0xFFFFFFFF),
        buttonColors = List(3) { Color(0x14FFFFFF) },
        buttonShade = List(3) { Color(0x00000000) },
        buttonText = Color(0xFFE8E6FF),
        buttonTextActive = Color(0xFF2A1F55),
        buttonActive = Color(0xFFFFE27A),
        progressDone = Color(0xFFFFE27A),
        progressCurrent = Color(0xFFFFE27A),
        progressTodo = Color(0x4CFFFFFF),
        feedbackScrim = Color(0x730A0C28),
        cardRadius = 20.dp,
        buttonRadius = 31.dp,
        borderNormal = 1.5.dp,
        borderFocused = 4.5.dp,
        focusScale = 1.1f,
        focusRotateDeg = 0f,
        focusTranslateY = (-9).dp,
        focusTag = "OK 出發 ▶",
    )

    val all = listOf(A, B, C)

    fun of(id: ThemeId): AppTheme = when (id) {
        ThemeId.A -> A
        ThemeId.B -> B
        ThemeId.C -> C
    }
}

/** 目前的主題。由 [BopomofoTheme] 提供。 */
val LocalAppTheme = staticCompositionLocalOf { Themes.of(ThemeId.DEFAULT) }
