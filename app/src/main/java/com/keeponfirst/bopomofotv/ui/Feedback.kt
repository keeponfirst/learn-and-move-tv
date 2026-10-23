package com.keeponfirst.bopomofotv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * 答對回饋（大人陪按「答對了」）。總長 [Motion.FEEDBACK_MS]＝0.8 秒，照素材包各主題時間軸等比壓縮。
 * 不閃爍：每個元件只淡入一次、淡出一次；沒有反覆明暗或全螢幕快速變化。
 * 所有元件畫在同一個 Canvas，座標用設計稿的 dp（畫面 960×540），再依實際畫面大小縮放。
 */

/** 0..1：[ms] 在 [start, end] 之間走到哪裡。 */
internal fun seg(ms: Float, start: Float, end: Float): Float = ((ms - start) / (end - start)).coerceIn(0f, 1f)

private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)
private fun easeIn(t: Float) = t * t
private fun easeInOut(t: Float) = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2) * (-2 * t + 2) / 2

/** 整體淡出：最後 150 ms。 */
private fun fadeOutAll(ms: Float) = 1f - seg(ms, 650f, 800f)

/**
 * 回饋動畫。[trigger] 每次答對加 1（0＝還沒答對過，不播）。[symbol] 是 A 印章上的「ㄆ 答對了」。
 * [starTarget] 是星星列目前那格的畫面座標（px）；沒有時用右上角。
 */
@Composable
fun FeedbackOverlay(trigger: Int, symbol: String, starTarget: Offset?) {
    val theme = LocalAppTheme.current
    val t = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        t.snapTo(0f)
        t.animateTo(1f, tween(Motion.FEEDBACK_MS, easing = LinearEasing))
    }
    val images = FeedbackImages.load(theme.id)
    val measurer = rememberTextMeasurer()
    val stampText = remember(symbol) { "$symbol 答對了" }
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val ms = p * Motion.FEEDBACK_MS
        val u = size.width / 960f // 1 設計 dp 換成 px
        val target = starTarget ?: Offset(780 * u, 44 * u)
        when (theme.id) {
            ThemeId.A -> drawStoryStamp(ms, u, images, theme, measurer, stampText)
            ThemeId.B -> drawPlayground(ms, u, images, target, trigger)
            ThemeId.C -> drawStarCatch(ms, u, images, theme, target)
        }
    }
}

/** 各主題回饋用到的圖。 */
internal class FeedbackImages(val map: Map<String, ImageBitmap?>) {
    operator fun get(name: String) = map[name]

    companion object {
        private val names = mapOf(
            ThemeId.A to listOf("feedback/stamp_good", "feedback/paper_star_yellow", "feedback/paper_star_green", "feedback/paper_star_pink", "feedback/paper_star_blue"),
            ThemeId.B to listOf(
                "feedback/glow_overlay", "feedback/rays_hot", "feedback/title_correct", "feedback/plus1_star", "guide/guide_girl",
                "feedback/confetti_red", "feedback/confetti_yellow", "feedback/confetti_green", "feedback/confetti_blue",
                "feedback/confetti_purple", "feedback/confetti_white",
            ),
            ThemeId.C to listOf("feedback/star_glow", "feedback/star_trail", "feedback/title_got_star", "feedback/sparkle", "guide/guide_girl"),
        )

        private fun dir(id: ThemeId) = when (id) {
            ThemeId.A -> "a"
            ThemeId.B -> "b"
            ThemeId.C -> "c"
        }

        /** 這套主題回饋用到的所有圖（assets 路徑）。測試用來確認檔案都在。 */
        fun paths(id: ThemeId): List<String> = names.getValue(id).map { "theme/${dir(id)}/$it.png" }

        @Composable
        fun load(id: ThemeId): FeedbackImages =
            FeedbackImages(names.getValue(id).zip(paths(id)).associate { (name, path) -> name to rememberThemeImage(path) })
    }
}

/** 以 (cx, cy) 為中心畫圖，寬高 w×h（px），可縮放、旋轉、透明。 */
private fun DrawScope.sprite(img: ImageBitmap?, cx: Float, cy: Float, w: Float, h: Float, alpha: Float = 1f, scale: Float = 1f, rotation: Float = 0f) {
    if (img == null || alpha <= 0f || scale <= 0f) return
    withTransform({
        translate(cx, cy)
        rotate(rotation, Offset.Zero)
        scale(scale, scale, Offset.Zero)
    }) {
        drawImage(
            img,
            dstOffset = IntOffset((-w / 2).toInt(), (-h / 2).toInt()),
            dstSize = IntSize(w.toInt(), h.toInt()),
            alpha = alpha.coerceIn(0f, 1f),
        )
    }
}

/**
 * A 繪本小屋（原 1.6 秒壓成 0.8 秒）：米色遮罩 → 「好棒！」印章 1.4→1 倍蓋下並回彈 → 5 顆紙星星彈出再飄落。
 * 原時間軸的「符號大字晃一下」沒做：符號在卡片裡，回饋層碰不到。
 */
private fun DrawScope.drawStoryStamp(
    ms: Float, u: Float, img: FeedbackImages, theme: AppTheme,
    measurer: androidx.compose.ui.text.TextMeasurer, text: String,
) {
    val all = fadeOutAll(ms)
    drawRect(theme.feedbackScrim, alpha = seg(ms, 0f, 80f) * all)
    val cx = 480 * u
    val cy = 280 * u
    // 印章：0–150 縮放 1.4→1、淡入；150–200 回彈 1→0.96→1。
    val inP = easeOut(seg(ms, 0f, 150f))
    val bounce = seg(ms, 150f, 200f)
    val stampScale = (1.4f - 0.4f * inP) * (1f - 0.04f * sin(PI.toFloat() * bounce))
    val stampAlpha = inP * all
    sprite(img["feedback/stamp_good"], cx, cy, 380 * u, 380 * u, stampAlpha, stampScale)
    // 印章第二行（例「ㄆ 答對了」）：中心在印章圖 (400,478)／760，跟印章一起縮放，傾斜 -12°。
    if (stampAlpha > 0f) {
        val layout = measurer.measure(text, TextStyle(fontSize = 28.sp * (u / density), fontFamily = NotoSansTc, fontWeight = FontWeight.Black, color = theme.symbol))
        withTransform({
            translate(cx, cy)
            scale(stampScale, stampScale, Offset.Zero)
            translate((400f / 760f - 0.5f) * 380 * u, (478f / 760f - 0.5f) * 380 * u)
            rotate(-12f, Offset.Zero)
        }) {
            drawText(layout, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f), alpha = stampAlpha)
        }
    }
    // 紙星星：150–400 從印章中心彈到終點；400–750 往下飄 30 dp 並淡出。
    val stars = listOf(
        Triple("feedback/paper_star_yellow", Offset(300f, 120f), 75f),
        Triple("feedback/paper_star_green", Offset(680f, 110f), 65f),
        Triple("feedback/paper_star_yellow", Offset(735f, 425f), 85f),
        Triple("feedback/paper_star_pink", Offset(265f, 420f), 60f),
        Triple("feedback/paper_star_blue", Offset(485f, 70f), 50f),
    )
    val out = easeOut(seg(ms, 150f, 400f))
    val drift = easeIn(seg(ms, 400f, 750f))
    stars.forEachIndexed { k, (name, end, sizeDp) ->
        if (out <= 0f) return@forEachIndexed
        val x = cx + (end.x * u - cx) * out
        val y = cy + (end.y * u - cy) * out + 30 * u * drift
        val s = 0.3f + 0.7f * out
        val rot = (if (k % 2 == 0) 20f else -20f) * out
        sprite(img[name], x, y, sizeDp * u, sizeDp * u, (1f - drift) * all, s, rot)
    }
}

/** 一片彩帶的隨機參數。 */
private class Confetti(val color: Int, val angle: Float, val dist: Float, val scale: Float, val spin: Float, val fall: Float)

private val CONFETTI = listOf("red", "yellow", "green", "blue", "purple", "white")

/**
 * B 活力遊樂場（原 1.8 秒壓成 0.8 秒）：光暈與光芒淡入 → 彩帶爆開 → 「答對了！」彈出 → 小女孩跳一下 → 「+1 ★」飛進星星列。
 * 光芒不旋轉：0.8 秒內轉不到幾度，省掉全螢幕的轉動。彩帶用 [seed] 固定亂數，同一次動畫每格畫面一致。
 */
private fun DrawScope.drawPlayground(ms: Float, u: Float, img: FeedbackImages, target: Offset, seed: Int) {
    val all = fadeOutAll(ms)
    val glow = seg(ms, 0f, 100f) * all
    sprite(img["feedback/glow_overlay"], size.width / 2, size.height / 2, size.width, size.height, glow)
    sprite(img["feedback/rays_hot"], 480 * u, 270 * u, 800 * u, 800 * u, glow * 0.8f)

    // 彩帶 40 片：50–400 從中心爆開；400–800 受重力往下，600–800 淡出。
    val rnd = Random(seed)
    val pieces = List(40) {
        Confetti(rnd.nextInt(CONFETTI.size), rnd.nextFloat() * 360f, 150f + rnd.nextFloat() * 300f, 0.55f + rnd.nextFloat() * 0.45f, 180f + rnd.nextFloat() * 540f, 60f + rnd.nextFloat() * 60f)
    }
    val burst = easeOut(seg(ms, 50f, 400f))
    val gravity = easeIn(seg(ms, 400f, 800f))
    val confettiAlpha = 1f - seg(ms, 600f, 800f)
    if (burst > 0f) {
        pieces.forEach { c ->
            val rad = Math.toRadians(c.angle.toDouble())
            val x = 480 * u + (cos(rad) * c.dist * burst).toFloat() * u
            val y = 270 * u + (sin(rad) * c.dist * burst).toFloat() * u + c.fall * gravity * u
            sprite(img["feedback/confetti_${CONFETTI[c.color]}"], x, y, 17 * u, 7 * u, confettiAlpha, c.scale, c.spin * seg(ms, 50f, 800f))
        }
    }

    // 小女孩：100–400 跳一下（上移 40 dp、縮放 0.9→1.05→1），50–150 淡入。高 310 dp，底部中心 (470,475)。
    val jump = seg(ms, 100f, 400f)
    val girlH = 310 * u
    val girlW = girlH * 470f / 570f
    val lift = -40 * u * sin(PI.toFloat() * jump)
    val girlScale = if (jump < 0.5f) 0.9f + 0.15f * (jump * 2) else 1.05f - 0.05f * ((jump - 0.5f) * 2)
    sprite(img["guide/guide_girl"], 470 * u, 475 * u - girlH / 2 + lift, girlW, girlH, seg(ms, 50f, 150f) * all, girlScale)

    // 「答對了！」：70–180 縮放 0.5→1.15、淡入；180–250 回到 1。中心 (480,105)。
    val titleScale = if (ms < 180f) 0.5f + 0.65f * easeOut(seg(ms, 70f, 180f)) else 1.15f - 0.15f * seg(ms, 180f, 250f)
    sprite(img["feedback/title_correct"], 480 * u, 105 * u, 380 * u, 120 * u, seg(ms, 70f, 180f) * all, titleScale)

    // 「+1 ★」：250–330 從 0 彈到 1；330–600 飛到星星列目前那格，縮到 0.4 並淡出。
    val pop = seg(ms, 250f, 330f)
    val fly = easeIn(seg(ms, 330f, 600f))
    if (pop > 0f && fly < 1f) {
        val start = Offset(690 * u, 300 * u)
        val pos = start + (target - start) * fly
        val s = if (fly > 0f) 1f - 0.6f * fly else easeOut(pop) * 1.1f - 0.1f * seg(ms, 300f, 330f)
        sprite(img["feedback/plus1_star"], pos.x, pos.y, 163 * u, 101 * u, 1f - fly, s)
    }
}

/**
 * C 星空探險（原 1.6 秒壓成 0.8 秒）：深色遮罩 → 一顆星從「答對了」按鈕沿曲線飛進星座那格、拖著光尾 →
 * 「抓到一顆星！」浮現 → 小女孩從右邊滑入 → 那格周圍 5 顆小閃光。
 */
private fun DrawScope.drawStarCatch(ms: Float, u: Float, img: FeedbackImages, theme: AppTheme, target: Offset) {
    val all = fadeOutAll(ms)
    drawRect(theme.feedbackScrim, alpha = seg(ms, 0f, 100f) * all)

    // 星星：50–350 沿二次曲線從按鈕 (490,470) 經控制點 (500,260) 飛到目標，縮放 0.6→1。
    val p0 = Offset(490 * u, 470 * u)
    val c = Offset(500 * u, 260 * u)
    val fly = easeInOut(seg(ms, 50f, 350f))
    if (ms >= 50f && ms < 450f) {
        val q = 1 - fly
        val pos = p0 * (q * q) + c * (2 * q * fly) + target * (fly * fly)
        val dir = (c - p0) * (2 * q) + (target - c) * (2 * fly)
        val angle = Math.toDegrees(atan2(dir.y, dir.x).toDouble()).toFloat()
        // 光尾：亮端貼著星星，朝飛行方向；長度 0→200→0 dp。
        val len = 200 * u * sin(PI.toFloat() * seg(ms, 50f, 350f))
        val trail = img["feedback/star_trail"]
        if (trail != null && len > 1f) {
            withTransform({
                translate(pos.x, pos.y)
                rotate(angle, Offset.Zero)
            }) {
                drawImage(trail, dstOffset = IntOffset(-len.toInt(), (-12 * u).toInt()), dstSize = IntSize(len.toInt(), (24 * u).toInt()), alpha = 1f - seg(ms, 300f, 350f))
            }
        }
        sprite(img["feedback/star_glow"], pos.x, pos.y, 148 * u * 0.5f, 147 * u * 0.5f, 1f - seg(ms, 350f, 450f), 0.6f + 0.4f * fly)
    }

    // 「抓到一顆星！」：150–300 縮放 0.8→1、淡入。中心 (480,240)。
    val title = easeOut(seg(ms, 150f, 300f))
    sprite(img["feedback/title_got_star"], 480 * u, 240 * u, 455 * u, 108 * u, title * all, 0.8f + 0.2f * title)

    // 小女孩：150–350 從右邊 +60 dp 滑入。高 185 dp，左上角 (750,250)。
    val slide = easeOut(seg(ms, 150f, 350f))
    val gh = 185 * u
    val gw = gh * 470f / 570f
    sprite(img["guide/guide_girl"], 750 * u + gw / 2 + 60 * u * (1 - slide), 250 * u + gh / 2, gw, gh, slide * all)

    // 閃光：350–500，5 顆在目標周圍依序出現，每顆 0→1→0 用 100 ms。
    repeat(5) { k ->
        val sp = seg(ms, 350f + k * 20f, 450f + k * 20f)
        if (sp <= 0f || sp >= 1f) return@repeat
        val a = k * 72f + 20f
        val r = (16 + (k % 3) * 7) * u
        val rad = Math.toRadians(a.toDouble())
        sprite(img["feedback/sparkle"], target.x + (cos(rad) * r).toFloat(), target.y + (sin(rad) * r).toFloat(), 18 * u, 18 * u, 1f, sin(PI.toFloat() * sp))
    }
}
