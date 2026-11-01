package com.pandyabhavik.pulse.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pandyabhavik.pulse.MainActivity
import com.pandyabhavik.pulse.core.RingStyle
import com.pandyabhavik.pulse.data.HomeState
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// The widget layouts. Each widget is drawn at its real size (SizeMode.Exact) and scaled by k, the ratio of
// that size to the mock's design size. Every text is one line: its size is also capped so the estimated
// width fits the space it has (see [fit]), and sizes are given in dp-equivalent sp (the user's font scale is
// divided out) because a widget is a fixed box: a 1.15 font scale on the cover screen would otherwise push
// "kcal under" into the ring or cut "8,412 steps". Rings and bars are bitmaps: nested boxes don't size
// reliably in RemoteViews.

internal const val TAB_TODAY = "today"
internal const val TAB_JOURNEY = "journey"

// Colours from CONTRACT.md (ui/C), kept here so the widgets don't depend on the UI module's types.
internal object WC {
    val card = Color(0xFF0E1016)
    val line = Color(0xFF232836)
    val s3 = Color(0xFF1C202A)
    val ringTrack = Color(0xFF161A23)
    val ink = Color(0xFFF3F4F8)
    val mute = Color(0xFF8D93A6)
    val dim = Color(0xFF5A6072)
    val under = Color(0xFF2EF2A8)
    val over = Color(0xFFFF4D6D)
    val steps = Color(0xFF4DA8FF)
    val wt = Color(0xFFB48CFF)
    val wt2 = Color(0xFFFF7AD9)
    val food = Color(0xFFFFB86B)
    val hot = Color(0xFFFF8A5C)
    val eatenLight = Color(0xFFE8EAF2)
    val eatenDark = Color(0xFF9AA1B5)
}

/** The colours of the "still to go" arc for each unlocked ring style. MINT is the plain default. */
internal fun ringColors(style: RingStyle): List<Color> = when (style) {
    RingStyle.MINT -> listOf(WC.under)
    RingStyle.AURORA -> listOf(WC.under, WC.steps, WC.wt)
    RingStyle.SUNRISE -> listOf(WC.food, WC.wt2)
    RingStyle.OCEAN -> listOf(WC.steps, WC.under)
    RingStyle.NEON -> listOf(WC.wt, WC.wt2)
    RingStyle.EMBER -> listOf(WC.hot, WC.food)
    RingStyle.GLACIER -> listOf(Color(0xFFBFE9FF), WC.steps)
    RingStyle.PRISM -> listOf(WC.under, WC.steps, WC.wt, WC.wt2)
}

// ------------------------------------------------------------------------------------------ helpers

/** The widget's size relative to its design size, clamped so text stays readable and inside the card. */
@Composable
internal fun scale(designW: Float, designH: Float): Float {
    val dims = LocalSize.current
    if (dims.width.value <= 0f || dims.height.value <= 0f) return 1f
    return minOf(dims.width.value / designW, dims.height.value / designH).coerceIn(0.6f, 1.7f)
}

@Composable
private fun widthDp(): Float = LocalSize.current.width.value.let { if (it > 0f) it else 150f }

@Composable
private fun heightDp(): Float = LocalSize.current.height.value.let { if (it > 0f) it else 150f }

/** Rough width of [text] in em, a little generous so the estimate errs towards fitting. */
internal fun ems(text: String, bold: Boolean): Float {
    var w = 0f
    for (ch in text) {
        w += when {
            ch.isDigit() -> 0.58f
            ch == ',' || ch == '.' || ch == ':' -> 0.27f
            ch == ' ' -> 0.26f
            ch == '·' -> 0.3f
            ch == '—' -> 1.0f
            ch == 'm' || ch == 'w' -> 0.86f
            ch == 'M' || ch == 'W' -> 0.92f
            ch == 'i' || ch == 'l' || ch == 'j' || ch == 'I' -> 0.26f
            ch == 'f' || ch == 't' || ch == 'r' -> 0.38f
            ch.isLowerCase() -> 0.55f
            ch.isUpperCase() -> 0.7f
            else -> 0.62f
        }
    }
    return if (bold) w * 1.07f else w
}

/** Font size (in dp) for one line of [text]: [wantDp], or smaller so it fits in [availDp]. */
internal fun fitDp(text: String, wantDp: Float, availDp: Float, bold: Boolean = true): Float {
    val em = ems(text, bold)
    if (em <= 0f || availDp <= 0f) return wantDp
    return minOf(wantDp, availDp * 0.94f / em).coerceAtLeast(6f)
}

/** dp to sp with the user's font scale divided out, so text keeps the size the layout was measured for. */
internal fun Context.dpToSp(dp: Float): TextUnit {
    val fs = resources.configuration.fontScale.takeIf { it > 0f } ?: 1f
    return (dp / fs).sp
}

private fun Context.ts(color: Color, dp: Float, weight: FontWeight = FontWeight.Bold, align: TextAlign = TextAlign.Start) =
    TextStyle(color = ColorProvider(color), fontSize = dpToSp(dp), fontWeight = weight, textAlign = align)

/** One line of text at [dp] (already fitted). */
@Composable
private fun Line(
    context: Context,
    text: String,
    color: Color,
    dp: Float,
    weight: FontWeight = FontWeight.Bold,
    align: TextAlign = TextAlign.Start,
    modifier: GlanceModifier = GlanceModifier,
) {
    Text(text, modifier = modifier, style = context.ts(color, dp, weight, align), maxLines = 1)
}

/** Opens Pulse on [tab] ("today" or "journey"). */
internal fun openTab(context: Context, tab: String): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(MainActivity.EXTRA_TAB, tab),
)

/** The dark rounded card: a 1dp line-coloured border around the surface. Rounded on Android 12+. */
@Composable
private fun Card(onClick: Action, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(WC.line)
            .cornerRadius(26.dp)
            .padding(1.dp)
            .clickable(onClick),
    ) {
        Box(modifier = GlanceModifier.fillMaxSize().background(WC.card).cornerRadius(25.dp)) {
            content()
        }
    }
}

// ------------------------------------------------------------------------------------------ text from state

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

internal fun grouped(n: Int): String = String.format(Locale.US, "%,d", n)

/** 115.0 -> "115", 112.5 -> "112.5". */
internal fun kg(v: Double): String =
    if (abs(v - v.roundToInt()) < 0.05) v.roundToInt().toString() else String.format(Locale.US, "%.1f", v)

internal fun trendText(s: HomeState): String = s.trendKg?.let { String.format(Locale.US, "%.1f", it) } ?: "—"

/** The big balance number (always positive; the label says under or over). */
internal fun balanceText(s: HomeState): String = s.balance?.let { grouped(abs(it)) } ?: "—"

internal fun balanceColor(s: HomeState): Color = when {
    s.balance == null -> WC.mute
    s.isOver -> WC.over
    else -> WC.under
}

internal fun balanceLabel(s: HomeState, short: Boolean): String = when {
    s.balance == null -> "no food yet"
    s.isOver -> if (short) "over" else "kcal over"
    else -> if (short) "under" else "kcal under"
}

internal fun stepsText(s: HomeState): String = "${grouped(s.liveSteps)} steps"

/** "115 kg · 17 Dec", "115 kg" (no date while stalled), "Goal reached", or null without a forecast. */
internal fun milestoneText(s: HomeState): String? {
    val f = s.forecast ?: return null
    val next = f.next ?: return "Goal reached"
    val date = next.projected
    return if (date != null) "${kg(next.kg)} kg · ${date.format(DAY)}" else "${kg(next.kg)} kg"
}

/** "Next 115 · 17 Dec" in three parts so the kg can be bold. */
internal fun nextParts(s: HomeState): Triple<String, String, String> {
    val f = s.forecast ?: return Triple("Next ", "—", "")
    val next = f.next ?: return Triple("", "Goal reached", "")
    val date = next.projected
    return Triple("Next ", kg(next.kg), if (date != null) " · ${date.format(DAY)}" else " kg")
}

/** Where the bar toward the next milestone starts: the last milestone reached, else the start weight. */
internal fun milestoneFrom(s: HomeState): Double? {
    val p = s.profile ?: return null
    val f = s.forecast ?: return p.startKg
    val next = f.next ?: return p.startKg
    val reached = f.milestones.filter { it.reachedOn != null && it.kg > next.kg }.minByOrNull { it.kg }
    return reached?.kg ?: maxOf(p.startKg, next.kg)
}

internal fun milestoneProgress(s: HomeState): Float {
    val f = s.forecast ?: return 0f
    val next = f.next ?: return 1f
    val from = milestoneFrom(s) ?: return 0f
    val cur = s.trendKg ?: return 0f
    val span = from - next.kg
    if (span <= 0.0) return 1f
    return ((from - cur) / span).toFloat().coerceIn(0f, 1f)
}

internal fun stepsProgress(s: HomeState): Float {
    val goal = s.profile?.stepGoal ?: 10_000
    return if (goal <= 0) 0f else (s.liveSteps.toFloat() / goal).coerceIn(0f, 1f)
}

/** The ring's two inputs: eaten as a fraction of burned (under), or the overflow fraction (over). */
private data class RingValue(val eatenFrac: Float, val overFrac: Float, val empty: Boolean)

private fun ringValue(s: HomeState): RingValue {
    val burned = s.burned
    if (burned == null || burned <= 0) return RingValue(0f, 0f, empty = true)
    val eaten = (s.eaten ?: 0).coerceAtLeast(0)
    return if (eaten <= burned) {
        RingValue(eaten.toFloat() / burned, 0f, empty = false)
    } else {
        RingValue(1f, ((eaten - burned).toFloat() / burned).coerceIn(0f, 1f), empty = false)
    }
}

@Composable
private fun RingImage(s: HomeState, ringDp: Float) {
    val v = ringValue(s)
    Image(
        provider = ImageProvider(Art.ring(v.eatenFrac, v.overFrac, v.empty, s.ringStyle, ringDp)),
        contentDescription = "${balanceText(s)} ${balanceLabel(s, short = false)}",
        contentScale = ContentScale.Fit,
        modifier = GlanceModifier.fillMaxSize(),
    )
}

// ------------------------------------------------------------------------------------------ empty

/** Shown before onboarding, or if the data can't be read. */
@Composable
internal fun EmptyWidget(context: Context, tab: String) {
    val w = widthDp()
    val h = heightDp()
    val avail = w - 2f - 24f
    val text = "Open Pulse to start"
    val titleDp = fitDp("Pulse", minOf(20f, h * 0.24f), avail)
    val lineDp = fitDp(text, minOf(14f, h * 0.18f), avail, bold = false)
    Card(openTab(context, tab)) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Line(context, "Pulse", WC.wt, titleDp, align = TextAlign.Center)
            Spacer(GlanceModifier.height((titleDp * 0.2f).dp))
            Line(context, text, WC.mute, lineDp, FontWeight.Medium, TextAlign.Center)
        }
    }
}

// ------------------------------------------------------------------------------------------ 1. Today (2x2)

/** The numbers inside a ring of [ringDp]: balance, its label, and optionally steps. */
@Composable
private fun RingCenter(context: Context, s: HomeState, ringDp: Float, numberDp: Float, labelDp: Float, short: Boolean, steps: Boolean) {
    // The hole is about 0.75 of the ring; the text lines sit off-centre where the hole is narrower.
    val hole = ringDp * 0.75f
    val number = balanceText(s)
    val label = balanceLabel(s, short)
    val numDp = fitDp(number, numberDp, hole * 0.9f)
    val lblDp = fitDp(label, labelDp, hole * 0.72f)
    val color = balanceColor(s)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Line(context, number, color, numDp, align = TextAlign.Center)
        Line(context, label, color, lblDp, align = TextAlign.Center)
        if (steps) {
            val st = stepsText(s)
            Spacer(GlanceModifier.height((labelDp * 0.4f).dp))
            Line(context, st, WC.steps, fitDp(st, labelDp, hole * 0.72f), align = TextAlign.Center)
        }
    }
}

@Composable
internal fun TodayView(context: Context, s: HomeState) {
    val w = widthDp()
    val h = heightDp()
    val k = scale(156f, 156f)
    val pad = 6f * k
    val ring = (minOf(w, h) - 2f - 2 * pad).coerceAtLeast(56f)
    // Mock: a 136dp ring holds a 32dp number and 11.5dp lines.
    val r = ring / 136f
    Card(openTab(context, TAB_TODAY)) {
        Box(modifier = GlanceModifier.fillMaxSize().padding(pad.dp), contentAlignment = Alignment.Center) {
            Box(modifier = GlanceModifier.size(ring.dp), contentAlignment = Alignment.Center) {
                RingImage(s, ring)
                RingCenter(context, s, ring, 32f * r, 11.5f * r, short = false, steps = true)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ 2. Journey (2x2)

@Composable
internal fun JourneyView(context: Context, s: HomeState) {
    val w = widthDp()
    val k = scale(156f, 156f)
    val pad = 14f * k
    val inner = w - 2f - 2 * pad
    val trend = trendText(s)
    val (pre, kgPart, post) = nextParts(s)
    val nextDp = fitDp(pre + kgPart + post, 12f * k, inner)
    Card(openTab(context, TAB_JOURNEY)) {
        Column(modifier = GlanceModifier.fillMaxSize().padding(pad.dp)) {
            Line(context, "JOURNEY", WC.mute, fitDp("JOURNEY", 10.5f * k, inner))
            Spacer(GlanceModifier.defaultWeight())
            Line(context, trend, WC.wt, fitDp(trend, 34f * k, inner))
            Line(context, "kg trend", WC.mute, fitDp("kg trend", 12f * k, inner, bold = false), FontWeight.Medium)
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pre.isNotEmpty()) Line(context, pre, WC.ink, nextDp, FontWeight.Medium)
                Line(context, kgPart, WC.ink, nextDp, FontWeight.Bold)
                if (post.isNotEmpty()) Line(context, post, WC.ink, nextDp, FontWeight.Medium)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ 3. Next milestone (4x1)

@Composable
internal fun MilestoneView(context: Context, s: HomeState) {
    val w = widthDp()
    val k = scale(328f, 72f)
    val padH = 16f * k
    val padV = 8f * k
    val inner = w - 2f - 2 * padH
    val trend = trendText(s)
    val numDp = fitDp(trend, 26f * k, inner * 0.34f)
    val numW = ems(trend, true) * numDp
    val gap = 12f * k
    val col = (inner - numW - gap).coerceAtLeast(40f)
    val from = milestoneFrom(s)?.let { kg(it) } ?: ""
    val to = milestoneText(s) ?: s.profile?.let { "Goal ${kg(it.goalKg)} kg" } ?: ""
    val fromW = ems(from, true) * 12f * k
    val toDp = fitDp(to, 12f * k, col - fromW - 8f * k)
    val fromDp = minOf(12f * k, toDp)
    Card(openTab(context, TAB_JOURNEY)) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = padH.dp, vertical = padV.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Line(context, trend, WC.wt, numDp)
            Spacer(GlanceModifier.width(gap.dp))
            Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(Art.bar(listOf(WC.wt, WC.wt2), WC.s3, milestoneProgress(s), col, 8f * k)),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().height((8f * k).dp),
                )
                Spacer(GlanceModifier.height((5f * k).dp))
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Line(context, from, WC.mute, fromDp, modifier = GlanceModifier.defaultWeight())
                    Line(context, to, WC.ink, toDp, align = TextAlign.End)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ 4. Today, full (4x2)

@Composable
internal fun TodayFullView(context: Context, s: HomeState) {
    val w = widthDp()
    val h = heightDp()
    val k = scale(328f, 156f)
    val padH = 18f * k
    val padV = 12f * k
    val ring = minOf(h - 2f - 2 * padV, (w - 2f) * 0.4f).coerceAtLeast(56f)
    val r = ring / 112f
    val gap = 16f * k
    val col = (w - 2f - 2 * padH - ring - gap).coerceAtLeast(40f)

    val steps = grouped(s.liveSteps)
    val trend = trendText(s)
    val next = milestoneText(s) ?: s.profile?.let { "Goal ${kg(it.goalKg)} kg" } ?: ""
    val lblDp0 = fitDp("WEIGHT", 11f * k, col)
    val stepsDp0 = fitDp(steps, 22f * k, col)
    // "117.6 kg": the number and a smaller unit share the column.
    val unitRatio = 13f / 22f
    val trendDp0 = minOf(22f * k, col * 0.94f / (ems(trend, true) + ems(" kg", true) * unitRatio))
    val nextDp0 = fitDp(next, 12f * k, col)
    // The column holds five text lines plus the bar; on a short 4x2 (the cover screen's 156dp) they didn't
    // all fit and the last line was cut. Text lines take about 1.4x their size (font padding included),
    // so shrink everything together until the column fits the card's height.
    val gaps = (4f + 5f + 6f) * k
    val need = (lblDp0 * 2 + stepsDp0 + trendDp0 + nextDp0) * 1.4f + gaps
    val fit = ((h - 2f - 2 * padV) / need).coerceIn(0.6f, 1f)
    val lblDp = lblDp0 * fit
    val stepsDp = stepsDp0 * fit
    val trendDp = trendDp0 * fit
    val nextDp = nextDp0 * fit

    Card(openTab(context, TAB_TODAY)) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = padH.dp, vertical = padV.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = GlanceModifier.size(ring.dp), contentAlignment = Alignment.Center) {
                RingImage(s, ring)
                RingCenter(context, s, ring, 26f * r, 12f * r, short = true, steps = false)
            }
            Spacer(GlanceModifier.width(gap.dp))
            Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Line(context, "STEPS", WC.mute, lblDp)
                Line(context, steps, WC.steps, stepsDp)
                Spacer(GlanceModifier.height((4f * k).dp))
                Image(
                    provider = ImageProvider(Art.bar(listOf(WC.steps), WC.s3, stepsProgress(s), col, 5f * k)),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().height((5f * k).dp),
                )
                Spacer(GlanceModifier.height((6f * k).dp))
                Line(context, "WEIGHT", WC.mute, lblDp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Line(context, trend, WC.wt, trendDp)
                    Line(context, " kg", WC.wt, trendDp * unitRatio)
                }
                Line(context, next, WC.mute, nextDp, FontWeight.Medium)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ bitmaps

/**
 * Bitmaps for the widgets. They travel inside the RemoteViews in one binder transaction (about 1 MB for
 * everything in flight), and SizeMode.Exact sends one layout per size (portrait and landscape). So pixel
 * sizes are rounded to a few buckets and kept in a small cache: both layouts then hold the same Bitmap
 * object, which the RemoteViews bitmap cache sends once.
 */
internal object Art {
    private const val PX_PER_DP = 2f
    private const val RING_MAX = 240

    private val cache = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 24
    }

    @Synchronized
    private fun cached(key: String, make: () -> Bitmap): Bitmap = cache.getOrPut(key, make)

    private fun bucket(dp: Float, step: Int, min: Int, max: Int): Int {
        val raw = (dp * PX_PER_DP).roundToInt()
        return (((raw + step - 1) / step) * step).coerceIn(min, max)
    }

    private fun q(v: Float) = (v.coerceIn(0f, 1f) * 200).roundToInt()

    private fun argb(c: Color, alpha: Float = c.alpha): Int = android.graphics.Color.argb(
        (alpha * 255).roundToInt().coerceIn(0, 255),
        (c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt(),
    )

    /**
     * The calorie ring, as in the mock (viewBox 270, radius 112, stroke 20): eaten fills clockwise from
     * the top in grey-white; the gap still to go glows in the ring style's colours. Over: a full grey
     * ring with the overflow wrapped on top in glowing red. [empty]: just the track (no burn data yet).
     */
    fun ring(eatenFrac: Float, overFrac: Float, empty: Boolean, style: RingStyle, dp: Float): Bitmap {
        val px = bucket(dp, 40, 120, RING_MAX)
        val key = "ring:$px:${q(eatenFrac)}:${q(overFrac)}:$empty:${style.name}"
        return cached(key) {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val u = px / 270f
            val cx = px / 2f
            val stroke = 20f * u
            val r = 112f * u
            val oval = RectF(cx - r, cx - r, cx + r, cx + r)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.BUTT
            }
            p.color = argb(WC.ringTrack)
            c.drawOval(oval, p)
            if (empty) return@cached bmp

            val blur = BlurMaskFilter(14f * u, BlurMaskFilter.Blur.NORMAL)
            val eatenShader = LinearGradient(
                cx - r, cx - r, cx + r, cx + r,
                argb(WC.eatenLight), argb(WC.eatenDark), Shader.TileMode.CLAMP,
            )

            fun arc(from: Float, frac: Float, paint: Paint) {
                if (frac <= 0.002f) return
                c.drawArc(oval, -90f + 360f * from, 360f * frac.coerceAtMost(1f), false, paint)
            }

            if (overFrac <= 0f) {
                val fe = eatenFrac.coerceIn(0f, 1f)
                // A closed donut: eaten from 12 o'clock, the rest glowing round to meet it. No gaps, flat ends.
                val gapStart = fe
                val gapLen = 1f - fe
                val colors = ringColors(style)
                val glow = Paint(p).apply {
                    maskFilter = blur
                    alpha = 204
                }
                val solid = Paint(p)
                if (colors.size == 1) {
                    glow.color = argb(colors[0], 0.8f)
                    solid.color = argb(colors[0])
                } else {
                    // A closed ring (nothing eaten yet) wraps its colours round to the first, so no seam shows.
                    val wrapped = colors + colors.first()
                    val sweep = SweepGradient(cx, cx, wrapped.map { argb(it) }.toIntArray(), null)
                    sweep.setLocalMatrix(Matrix().apply { setRotate(-90f, cx, cx) })
                    glow.shader = sweep
                    solid.shader = sweep
                }
                arc(gapStart, gapLen, glow)
                arc(gapStart, gapLen, solid)
                arc(0f, fe, Paint(p).apply { shader = eatenShader })
            } else {
                c.drawOval(oval, Paint(p).apply { shader = eatenShader })
                arc(0f, overFrac, Paint(p).apply { color = argb(WC.over, 0.8f); maskFilter = blur })
                arc(0f, overFrac, Paint(p).apply { color = argb(WC.over) })
            }
            bmp
        }
    }

    /** A rounded bar: [track] behind, [colors] (one colour, or a left-to-right gradient) up to [value]. */
    fun bar(colors: List<Color>, track: Color, value: Float, trackDp: Float, thickDp: Float): Bitmap {
        val w = bucket(trackDp, 16, 32, 480)
        val h = (thickDp * PX_PER_DP).roundToInt().coerceIn(4, 24)
        val key = "bar:$w:$h:${colors.joinToString(",") { argb(it).toString() }}:${argb(track)}:${q(value)}"
        return cached(key) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = h / 2f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = argb(track)
            c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, p)
            val v = value.coerceIn(0f, 1f)
            if (v > 0f) {
                val end = (w * v).coerceAtLeast(h.toFloat())
                if (colors.size == 1) {
                    p.color = argb(colors[0])
                } else {
                    p.shader = LinearGradient(
                        0f, 0f, end, 0f, colors.map { argb(it) }.toIntArray(), null, Shader.TileMode.CLAMP,
                    )
                }
                c.drawRoundRect(RectF(0f, 0f, end, h.toFloat()), r, r, p)
            }
            bmp
        }
    }
}
