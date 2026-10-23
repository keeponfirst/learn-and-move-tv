package com.keeponfirst.bopomofotv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.keeponfirst.bopomofotv.data.StickerCatalog
import com.keeponfirst.bopomofotv.record.StickerId
import com.keeponfirst.bopomofotv.record.StickerKind

/*
 * 貼紙收集冊。背景是攤開的書（書皮、左右兩頁、翻頁箭頭、3 個書籤），三類各一張，差在亮起來的書籤。
 * 格子位置照美術規格（設計稿 px ÷2 = dp）。
 */

/** 一頁 3×3 格。 */
const val STICKERS_PER_PAGE = 9

private val CELL = 95.dp
private val CELL_GAP = 12.dp
/** 左右兩頁第 1 格的左上角。 */
private val PAGE_FIRST_CELL = listOf(125.5.dp to 135.dp, 525.5.dp to 135.dp)
/** 兩頁的左緣與寬度（頁名、頁數置中或靠右用）。 */
private val PAGE_LEFT = listOf(85.dp, 485.dp)
private val PAGE_WIDTH = 390.dp

/** 這一類共幾頁、幾個跨頁（一次攤開 2 頁）。 */
internal fun pageCount(items: Int) = ((items + STICKERS_PER_PAGE - 1) / STICKERS_PER_PAGE).coerceAtLeast(1)
internal fun spreadCount(items: Int) = (pageCount(items) + 1) / 2

/**
 * 貼紙冊。左右鍵翻頁（一次一個跨頁，到頭不繞回），上下鍵切分類（注音 → ABC → 瑜伽）。返回鍵回首頁。
 * 已收集的畫彩色貼紙，還沒收集的畫灰影。
 */
@Composable
fun StickerBookScreen(catalog: StickerCatalog, owned: Set<StickerId>, onExit: () -> Unit) {
    val theme = LocalAppTheme.current
    val dir = themeDir(theme)
    var kind by remember { mutableStateOf(StickerKind.ZHUYIN) }
    var spread by remember { mutableIntStateOf(0) }
    val items = catalog.items(kind)
    val pages = pageCount(items.size)
    val spreads = spreadCount(items.size)
    BackHandler { onExit() }

    val background = "theme/$dir/stickerbook/book_bg_${kind.name.lowercase()}.jpg"
    RemoteKeyBox(
        backdrop = background,
        onKey = { key ->
            when (key) {
                RemoteKey.LEFT -> spread = (spread - 1).coerceAtLeast(0)
                RemoteKey.RIGHT -> spread = (spread + 1).coerceAtMost(spreads - 1)
                RemoteKey.UP, RemoteKey.DOWN -> {
                    val step = if (key == RemoteKey.UP) -1 else 1
                    kind = StickerKind.entries[(kind.ordinal + step).mod(StickerKind.entries.size)]
                    spread = 0
                }
                RemoteKey.OK -> Unit
            }
        },
    ) {
        DesignFrame(background) {
            repeat(2) { side ->
                val page = spread * 2 + side
                if (page < pages) {
                    val onPage = items.drop(page * STICKERS_PER_PAGE).take(STICKERS_PER_PAGE)
                    val (x0, y0) = PAGE_FIRST_CELL[side]
                    onPage.forEachIndexed { k, item ->
                        val cx = x0 + (CELL + CELL_GAP) * (k % 3)
                        val cy = y0 + (CELL + CELL_GAP) * (k / 3)
                        // 畫布比格子大 217/190 倍，往左上多出 13.6/190。
                        StickerView(item, item.id in owned, CELL * (217f / 190f), Modifier.offset(cx - CELL * (13.6f / 190f), cy - CELL * (13.6f / 190f)))
                    }
                    PageText("${kind.label} 第 ${page + 1} 頁", PAGE_LEFT[side], 96.dp, PAGE_WIDTH, TextAlign.Center, 22.sp, bookTitleColor(theme))
                    PageText("這一頁：${onPage.count { it.id in owned }}／${onPage.size}", PAGE_LEFT[side], 448.dp, PAGE_WIDTH - 28.dp, TextAlign.End, 15.sp, bookCountColor(theme))
                }
            }
            Box(Modifier.fillMaxSize().safeArea()) {
                InfoPill(Modifier.align(Alignment.TopStart)) { BookHud("貼紙冊　${kind.label} ${items.count { it.id in owned }}／${items.size}") }
                val first = spread * 2 + 1
                val shown = if (first + 1 <= pages) "第 $first・${first + 1} 頁" else "第 $first 頁"
                InfoPill(Modifier.align(Alignment.TopEnd)) { BookHud("$shown／共 $pages 頁") }
                // 提示壓在書皮下緣：A 的提示沒有底色，墊一層頁面色才讀得到（模擬器看過）。
                val hintBack = if (theme.id == ThemeId.A) theme.surface.copy(alpha = 0.9f) else androidx.compose.ui.graphics.Color.Transparent
                Box(Modifier.align(Alignment.BottomCenter).background(hintBack, RoundedCornerShape(50)).padding(horizontal = Spacing.S)) {
                    KeyHint("← → 翻頁　↑ ↓ 換注音／ABC／瑜伽　返回 回首頁")
                }
            }
        }
    }
}

@Composable
private fun PageText(text: String, left: Dp, top: Dp, width: Dp, align: TextAlign, size: androidx.compose.ui.unit.TextUnit, color: androidx.compose.ui.graphics.Color) {
    Text(text, Modifier.offset(left, top).width(width), fontSize = size, color = color, fontWeight = FontWeight.Black, textAlign = align)
}

@Composable
private fun BookHud(text: String) {
    val theme = LocalAppTheme.current
    Text(text, fontSize = TextSize.Info, color = if (theme.id == ThemeId.B) theme.hudText else theme.textSecondary, fontWeight = FontWeight.Black)
}

/** 頁名與頁數的字色（美術規格）。 */
private fun bookTitleColor(theme: AppTheme) = when (theme.id) {
    ThemeId.A -> androidx.compose.ui.graphics.Color(0xFF4A3B35)
    ThemeId.B -> androidx.compose.ui.graphics.Color(0xFF2D2250)
    ThemeId.C -> androidx.compose.ui.graphics.Color.White
}

private fun bookCountColor(theme: AppTheme) = when (theme.id) {
    ThemeId.A -> androidx.compose.ui.graphics.Color(0xFF8A6E5A)
    ThemeId.B -> androidx.compose.ui.graphics.Color(0xFF7A6E98)
    ThemeId.C -> androidx.compose.ui.graphics.Color(0xFFC9C6FF)
}
