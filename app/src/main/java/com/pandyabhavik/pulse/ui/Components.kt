package com.pandyabhavik.pulse.ui

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandyabhavik.pulse.core.RingStyle
import kotlin.math.min

// ---- Surfaces --------------------------------------------------------------------------------------

/** The mock's .card: s1, a hairline border and 22dp corners. */
@Composable
fun PCard(
    modifier: Modifier = Modifier,
    background: Brush? = null,
    borderColor: Color = C.line,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (background != null) Modifier.background(C.s1).background(background) else Modifier.background(C.s1))
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = padding, vertical = padding - 2.dp),
        content = content,
    )
}

/** Uppercase card label with an optional coloured icon and a trailing slot (the LIVE pill). */
@Composable
fun Lbl(
    text: String,
    icon: ImageVector? = null,
    iconColor: Color = C.mute,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text.uppercase(),
            style = T.caps,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** Big number + muted unit on one baseline (wrapping the unit if it must). */
@Composable
fun Metric(
    value: String,
    of: String?,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = T.metric,
    valueModifier: Modifier = Modifier,
    brush: Brush? = null,
    ofContent: (@Composable () -> Unit)? = null,
) {
    Row(modifier.padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = valueStyle,
            maxLines = 1,
            softWrap = false,
            modifier = valueModifier.then(if (brush != null) Modifier.gradient(brush) else Modifier),
        )
        Spacer(Modifier.width(7.dp))
        Box(Modifier.weight(1f, fill = false).padding(bottom = 3.dp)) {
            if (ofContent != null) ofContent() else if (of != null) {
                Text(of, style = T.small.copy(fontSize = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Paints the content (text) with [brush]: gradient text, keeping any glow shadow. */
fun Modifier.gradient(brush: Brush): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(brush, blendMode = BlendMode.SrcIn)
    }

// ---- Chips, pills, buttons ---------------------------------------------------------------------

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = C.mute,
    background: Color = C.s2,
    dot: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            GlowDot(dot, 7.dp)
            Spacer(Modifier.width(6.dp))
        }
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = T.small.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A small dot with a halo. */
@Composable
fun GlowDot(color: Color, size: Dp, alpha: Float = 1f) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawIntoCanvas { c ->
            paint.color = color.copy(alpha = 0.8f * alpha).toArgb()
            paint.maskFilter = BlurMaskFilter(r * 1.4f, BlurMaskFilter.Blur.NORMAL)
            c.nativeCanvas.drawCircle(center.x, center.y, r * 1.3f, paint)
        }
        drawCircle(color.copy(alpha = alpha), r)
    }
}

/** The pulsing LIVE pill on the steps card. */
@Composable
fun LivePill(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "live-a")
    Row(
        modifier
            .clip(CircleShape)
            .background(C.steps.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowDot(C.steps, 6.dp, a)
        Spacer(Modifier.width(5.dp))
        Text("LIVE", style = T.caps.copy(color = C.steps, fontWeight = androidx.compose.ui.text.font.FontWeight(700), letterSpacing = 0.6.sp))
    }
}

@Composable
fun IconBtn(icon: ImageVector, description: String, modifier: Modifier = Modifier, tint: Color = C.mute, onClick: () -> Unit) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(C.s2)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(21.dp))
    }
}

/** The mock's pill button: mint-to-blue gradient by default. */
@Composable
fun PButton(
    text: String,
    modifier: Modifier = Modifier,
    brush: Brush = C.goBrush,
    textColor: Color = Color(0xFF04110B),
    height: Dp = 52.dp,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .then(if (enabled) Modifier.background(brush) else Modifier.background(C.s2))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) textColor else C.dim, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = T.label.copy(fontSize = 15.sp, color = if (enabled) textColor else C.dim),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One calm line of commentary with a leading icon. */
@Composable
fun LineNote(icon: ImageVector, iconColor: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/** A label/value row with a hairline under it (meals, fields). */
@Composable
fun FieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = C.ink,
    note: String? = null,
    divider: Boolean = true,
    leading: ImageVector? = null,
    leadingColor: Color = C.mute,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Icon(leading, null, tint = leadingColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = T.body.copy(color = C.ink, fontSize = 14.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(value, style = T.label.copy(color = valueColor), maxLines = 1, softWrap = false)
            if (note != null) {
                Spacer(Modifier.width(5.dp))
                Text(note, style = T.small, maxLines = 1, softWrap = false)
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
    }
}

// ---- Bars --------------------------------------------------------------------------------------

/** A rounded progress bar on a dark track; the fill glows in its own colour. Animates its width. */
@Composable
fun GlowBar(
    fraction: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    glow: Boolean = true,
    track: Color = C.s3,
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "bar")
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val rect = remember { RectF() }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val h = size.height
        val r = h / 2f
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        val w = size.width * f
        if (w <= 0.5f) return@Canvas
        val fillW = maxOf(w, h)
        val brush = if (colors.size > 1) Brush.horizontalGradient(colors, 0f, size.width) else Brush.linearGradient(listOf(colors[0], colors[0]))
        if (glow) {
            drawIntoCanvas { c ->
                paint.color = colors.last().copy(alpha = 0.65f).toArgb()
                paint.maskFilter = BlurMaskFilter(h * 1.2f, BlurMaskFilter.Blur.NORMAL)
                rect.set(0f, 0f, fillW, h)
                c.nativeCanvas.drawRoundRect(rect, r, r, paint)
            }
        }
        drawRoundRect(brush, size = androidx.compose.ui.geometry.Size(fillW, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
    }
}

/** Resting vs active burned split: two segments with a gap, active glowing mint. */
@Composable
fun SplitBar(resting: Int, active: Int, modifier: Modifier = Modifier) {
    val total = (resting + active).coerceAtLeast(1)
    val target = active.toFloat() / total
    val fa by animateFloatAsState(target, tween(900), label = "split")
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val rect = remember { RectF() }
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val h = size.height
        val r = h / 2f
        val gap = 3.dp.toPx()
        val restW = (size.width - gap) * (1f - fa)
        val cr = androidx.compose.ui.geometry.CornerRadius(r, r)
        drawRoundRect(C.restingBar, size = androidx.compose.ui.geometry.Size(restW.coerceAtLeast(h), h), cornerRadius = cr)
        val ax = restW + gap
        val aw = size.width - ax
        if (aw > 1f) {
            drawIntoCanvas { c ->
                paint.color = C.under.copy(alpha = 0.7f).toArgb()
                paint.maskFilter = BlurMaskFilter(h, BlurMaskFilter.Blur.NORMAL)
                rect.set(ax, 0f, ax + aw, h)
                c.nativeCanvas.drawRoundRect(rect, r, r, paint)
            }
            drawRoundRect(C.under, topLeft = androidx.compose.ui.geometry.Offset(ax, 0f), size = androidx.compose.ui.geometry.Size(aw, h), cornerRadius = cr)
        }
    }
}

// ---- The balance ring --------------------------------------------------------------------------

/**
 * The calorie ring from the mock: what's eaten fills it in silver, the gap still unspent glows in the
 * ring style's colours (mint by default) = the deficit. Eat past the burn and the ring closes, then the
 * overflow wraps round again in red. [burned]/[eaten] null draw an empty track.
 */
@Composable
fun BalanceRing(
    burned: Int?,
    eaten: Int?,
    style: RingStyle,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val b = burned ?: 0
    val e = eaten ?: 0
    val hasData = b > 0
    val eatenFrac = if (!hasData) 0f else min(1f, e.toFloat() / b)
    val overFrac = if (!hasData || e <= b) 0f else min(1f, (e - b).toFloat() / b)
    val gapTarget = if (!hasData) 0f else 1f - eatenFrac
    val fe by animateFloatAsState(eatenFrac, tween(1100), label = "ring-eaten")
    val fg by animateFloatAsState(gapTarget, tween(1100), label = "ring-gap")
    val fo by animateFloatAsState(overFrac, tween(1100, delayMillis = 200), label = "ring-over")
    val gapColors = RingColors.of(style)

    val track = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE } }
    val arc = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND } }
    val glow = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND } }
    val oval = remember { RectF() }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // Drawn on a canvas 30% bigger than the ring's box, so the glow is never cut by the bounds.
        Canvas(Modifier.requiredSize(size * 1.3f)) {
            val box = this.size.width / 1.3f
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val sw = box * 0.074f
            val r = box / 2f - sw / 2f - box * 0.02f
            oval.set(cx - r, cy - r, cx + r, cy + r)
            val circ = (2 * Math.PI * r).toFloat()
            val capFrac = (sw / 2f) / circ // round caps poke out by half a stroke: start arcs inside it
            drawIntoCanvas { cv ->
                val nc = cv.nativeCanvas
                track.color = C.track.toArgb()
                track.strokeWidth = sw
                nc.drawCircle(cx, cy, r, track)
                if (!hasData) return@drawIntoCanvas

                // The eaten part: a silver sweep from 12 o'clock.
                val silver = SweepGradient(cx, cy, intArrayOf(0xFFE8EAF2.toInt(), 0xFF9AA1B5.toInt(), 0xFFE8EAF2.toInt()), null)
                val m = android.graphics.Matrix().apply { setRotate(-90f, cx, cy) }
                silver.setLocalMatrix(m)
                arc.shader = silver
                arc.strokeWidth = sw
                arc.alpha = 255
                if (fe >= 0.999f) {
                    arc.strokeCap = Paint.Cap.BUTT
                    nc.drawArc(oval, -90f, 360f, false, arc)
                    arc.strokeCap = Paint.Cap.ROUND
                } else if (fe > 0.002f) {
                    val s = (fe - 2 * capFrac).coerceAtLeast(0.001f)
                    nc.drawArc(oval, -90f + capFrac * 360f, s * 360f, false, arc)
                }
                arc.shader = null

                // The unspent gap: the deficit, glowing.
                if (fg > 0.01f) {
                    // Nothing eaten yet: the whole budget is still open, so the ring is one closed glowing
                    // circle (no cap gap at the top, which read as a horseshoe). The colours wrap round to
                    // the first so the seam doesn't show.
                    val closed = fe <= 0.002f && fg >= 0.995f
                    val gapStartFrac = if (closed) 0f else fe + 0.012f + capFrac
                    val gapLen = if (closed) 1f else (fg - 0.024f - 2 * capFrac).coerceAtLeast(0.001f)
                    val start = -90f + gapStartFrac * 360f
                    val sweep = gapLen * 360f
                    val colours = if (closed) gapColors + gapColors.first() else gapColors
                    val shader = SweepGradient(cx, cy, colours.map { it.toArgb() }.toIntArray(), null)
                    val cap = if (closed) Paint.Cap.BUTT else Paint.Cap.ROUND
                    glow.strokeCap = cap
                    arc.strokeCap = cap
                    shader.setLocalMatrix(m)
                    glow.shader = shader
                    glow.strokeWidth = sw * 1.15f
                    glow.alpha = 205
                    glow.maskFilter = BlurMaskFilter(sw * 0.75f, BlurMaskFilter.Blur.NORMAL)
                    nc.drawArc(oval, start, sweep, false, glow)
                    arc.shader = shader
                    nc.drawArc(oval, start, sweep, false, arc)
                    arc.shader = null
                    glow.strokeCap = Paint.Cap.ROUND
                    arc.strokeCap = Paint.Cap.ROUND
                }

                // Over: the overflow wraps round in red, glowing.
                if (fo > 0.002f) {
                    val sweep = (fo * 360f).coerceAtLeast(2f)
                    glow.shader = null
                    glow.color = C.over.toArgb()
                    glow.alpha = 215
                    glow.strokeWidth = sw * 1.15f
                    glow.maskFilter = BlurMaskFilter(sw * 0.75f, BlurMaskFilter.Blur.NORMAL)
                    nc.drawArc(oval, -90f + capFrac * 360f, sweep, false, glow)
                    arc.color = C.over.toArgb()
                    nc.drawArc(oval, -90f + capFrac * 360f, sweep, false, arc)
                }
            }
        }
        content()
    }
}

/** Font size for the ring's big number so 4-5 digit values still fit inside the hole. */
fun ringNumberSize(text: String, base: Float): Float = when {
    text.length <= 3 -> base
    text.length == 4 -> base * 0.86f
    else -> base * 0.76f
}

// ---- Pull to refresh ---------------------------------------------------------------------------

/** Pull-to-refresh that calls [onRefresh] (vm.sync()) and shows a mint spinner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullBox(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val state = rememberPullToRefreshState()
    // The spinner is only for a sync you pulled for. Background syncs (app opening, every 15 minutes)
    // show in the header's "Syncing…" chip instead of a spinner sitting on top of the ring.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (!refreshing) pulled = false }
    val spinning = refreshing && pulled
    PullToRefreshBox(
        isRefreshing = spinning,
        onRefresh = { pulled = true; onRefresh() },
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = spinning,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Color(0xFF2A3040),
                color = C.under,
            )
        },
        content = content,
    )
}

/** Screen header: small muted line above a bold display title, with a trailing slot. */
@Composable
fun Header(eyebrow: String, title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow, style = T.small.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, style = T.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** Friendly empty state inside a card. */
@Composable
fun EmptyNote(icon: ImageVector, title: String, text: String, color: Color = C.mute, modifier: Modifier = Modifier) {
    PCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = T.label)
                Text(text, style = T.small.copy(fontSize = 13.sp), textAlign = TextAlign.Start)
            }
        }
    }
}

/** A segmented pill control (units, sex, chart ranges). */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    tags: List<String?> = emptyList(),
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(C.s2)
            .border(1.dp, C.line, CircleShape)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (on) C.s3 else Color.Transparent)
                    .clickable { onSelect(i) }
                    .then(tags.getOrNull(i)?.let { Modifier.testTag(it) } ?: Modifier)
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, style = T.small.copy(color = if (on) C.ink else C.mute, fontSize = 13.sp), maxLines = 1)
            }
        }
    }
}
