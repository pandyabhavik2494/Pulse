package com.pandyabhavik.pulse.ui

import android.graphics.BlurMaskFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandyabhavik.pulse.R
import com.pandyabhavik.pulse.core.Forecaster
import com.pandyabhavik.pulse.core.Milestone
import com.pandyabhavik.pulse.core.RateSource
import com.pandyabhavik.pulse.data.HomeState
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val RANGES = listOf("1M" to 30, "3M" to 90, "1Y" to 365, "All" to null)

@Composable
fun JourneyScreen(s: HomeState) {
    val ui = LocalUi.current
    val syncing by ui.vm.syncing.collectAsStateWithLifecycle()
    PullBox(
        refreshing = syncing,
        onRefresh = { ui.vm.sync() },
        modifier = Modifier
            .fillMaxSize()
            .testTag("journey_screen"),
    ) {
        if (ui.wide) JourneyInner(s) else JourneyCover(s, showSettings = !ui.flex)
    }
}

private fun stalled(s: HomeState) = s.forecast?.source == RateSource.STALLED

private fun goalWhen(s: HomeState): String? {
    val today = s.now.toLocalDate()
    return s.forecast?.goal?.projected?.let { "around ${F.date(it, today)}" }
}

// ---- Cover -------------------------------------------------------------------------------------

@Composable
private fun JourneyCover(s: HomeState, showSettings: Boolean) {
    val ui = LocalUi.current
    val p = s.profile ?: return
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header("Journey", "${F.kgShort(p.startKg)} → ${F.kgShort(p.goalKg)} kg") {
            if (showSettings) IconBtn(Icons.Rounded.Settings, "Settings", Modifier.testTag("nav_settings")) { ui.openSettings() }
        }
        GoalCard(s)
        WeightChart(s, rangeDays = 30, compact = true, height = 130.dp)
        Row(Modifier.fillMaxWidth()) {
            Text(F.date(p.startDate, s.now.toLocalDate()), style = T.small, modifier = Modifier.weight(1f), maxLines = 1)
            Text(F.pace(s), style = T.small, maxLines = 2, textAlign = TextAlign.End, modifier = Modifier.weight(3f))
        }
        MilestoneList(s)
        WhatIfCard(s)
    }
}

@Composable
private fun GoalCard(s: HomeState, showLine: Boolean = true) {
    val p = s.profile ?: return
    PCard(
        background = Brush.linearGradient(listOf(C.wt.copy(alpha = 0.16f), C.wt2.copy(alpha = 0.06f), Color.Transparent)),
    ) {
        Lbl("Goal", Icons.Rounded.Flag, C.wt2)
        Metric(
            "${F.kgShort(p.goalKg)} kg",
            null,
            valueStyle = T.metric.copy(fontSize = 34.sp, lineHeight = 36.sp),
            brush = C.weightBrush,
        ) {
            val w = goalWhen(s)
            Text(
                buildAnnotatedString {
                    when {
                        stalled(s) -> append("dates paused")
                        w != null -> {
                            append("around ")
                            withStyle(SpanStyle(color = C.ink, fontWeight = FontWeight(700))) { append(w.removePrefix("around ")) }
                        }
                        s.forecast?.next == null && s.forecast != null -> append("reached")
                        else -> append("dates after a few weigh-ins")
                    }
                },
                style = T.small.copy(fontSize = 13.sp),
                maxLines = 2,
            )
        }
        if (showLine) JourneyLine(s, includeAverage = false)
    }
}

/** One sentence of news. Shows the stalled message when no dates can be projected. */
@Composable
private fun JourneyLine(s: HomeState, includeAverage: Boolean) {
    val text = when {
        stalled(s) -> "Get back under to put dates on the calendar."
        s.journeyLine != null -> s.journeyLine + (
            if (includeAverage) s.forecast?.avgDeficit?.takeIf { it > 0 }?.let { " You're averaging ${F.n(it)} kcal under a day." } ?: "" else ""
            )
        s.trendKg == null -> "Weigh in a few mornings and your dates appear here."
        else -> "Every day under nudges your dates a little closer."
    }
    LineNote(Icons.AutoMirrored.Rounded.TrendingDown, if (stalled(s)) C.food else C.under) {
        Text(text, style = T.body.copy(fontSize = 13.5.sp), modifier = Modifier.testTag("journey_line"))
    }
}

// ---- Inner -------------------------------------------------------------------------------------

@Composable
private fun JourneyInner(s: HomeState) {
    val p = s.profile ?: return
    var range by rememberSaveable { mutableIntStateOf(1) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 18.dp),
    ) {
        val w = goalWhen(s)
        Header("Journey", "${F.kgShort(p.startKg)} → ${F.kgShort(p.goalKg)} kg" + (w?.let { " · $it" } ?: "")) {
            Segmented(RANGES.map { it.first }, range, tags = RANGES.map { "range_${it.first}" }) { range = it }
        }
        Spacer(Modifier.height(8.dp))
        WeightChart(s, rangeDays = RANGES[range].second, compact = false, height = 300.dp)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(F.pace(s), style = T.small, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        JourneyLine(s, includeAverage = true)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) { MilestoneList(s) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WhatIfCard(s)
                GoalCard(s, showLine = false)
            }
        }
    }
}

// ---- Milestones --------------------------------------------------------------------------------

@Composable
private fun MilestoneList(s: HomeState) {
    val f = s.forecast
    val today = s.now.toLocalDate()
    Column(Modifier.fillMaxWidth().testTag("milestone_list")) {
        if (f == null || f.milestones.isEmpty()) {
            EmptyNote(Icons.Rounded.Flag, "Milestones are waiting", "Your first weigh-in sets the line, then every 5 kg gets a date.", C.wt)
            return@Column
        }
        val next = f.next
        f.milestones.forEachIndexed { i, m ->
            MilestoneRow(m, isNext = m == next, last = i == f.milestones.lastIndex, shift = if (m == next) s.shiftDays else null, stalled = stalled(s), today = today)
        }
    }
}

@Composable
private fun MilestoneRow(m: Milestone, isNext: Boolean, last: Boolean, shift: Int?, stalled: Boolean, today: LocalDate) {
    val done = m.reachedOn != null
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (!last) {
                    val x = 9.dp.toPx()
                    drawLine(if (done) C.wt.copy(alpha = 0.5f) else C.line, Offset(x, 22.dp.toPx()), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                }
            }
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 2.dp).size(18.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Box(Modifier.size(18.dp).clip(CircleShape).background(Brush.linearGradient(listOf(C.wt, C.wt2))))
                isNext -> {
                    GlowDot(C.wt.copy(alpha = 0.6f), 30.dp)
                    Box(Modifier.size(18.dp).clip(CircleShape).background(C.bg).border(2.dp, C.wt, CircleShape))
                }
                else -> Box(Modifier.size(18.dp).clip(CircleShape).background(C.bg).border(2.dp, C.dim, CircleShape))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${F.kgShort(m.kg)} kg",
                style = T.h3.copy(color = if (done || isNext) C.ink else C.soft),
                maxLines = 1,
            )
            Text(
                if (done) "Reached ${F.date(m.reachedOn!!, today)}" else m.label,
                style = T.small.copy(fontSize = 12.5.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            when {
                done -> Text("Done", style = T.small.copy(color = C.under, fontWeight = FontWeight(700)))
                stalled || m.projected == null -> Text("—", style = T.h3.copy(fontSize = 14.sp, color = C.dim))
                else -> {
                    Text(F.date(m.projected!!, today), style = T.h3.copy(fontSize = 14.sp, color = if (isNext) C.ink else C.soft), maxLines = 1)
                    if (shift != null && shift != 0) {
                        Text(F.shift(shift), style = T.small.copy(fontSize = 11.5.sp, fontWeight = FontWeight(700), color = if (shift > 0) C.under else C.food), maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---- What if -----------------------------------------------------------------------------------

@Composable
private fun WhatIfCard(s: HomeState) {
    val p = s.profile ?: return
    val today = s.now.toLocalDate()
    val start = ((s.forecast?.avgDeficit ?: 500).coerceIn(0, 1500) / 50f).roundToInt() * 50f
    var v by rememberSaveable { mutableFloatStateOf(start) }
    val kcal = v.roundToInt()
    val date = remember(kcal, s.trendKg, p) {
        if (kcal <= 0) null else runCatching { Forecaster.whatIf(p, s.trendKg ?: p.startKg, today, kcal) }.getOrNull()
    }
    PCard {
        Lbl("What if", Icons.Rounded.Tune, C.mute)
        Text(
            buildAnnotatedString {
                append("If I average ")
                withStyle(SpanStyle(color = C.under, fontWeight = FontWeight(800))) { append("${F.n(kcal)} kcal under") }
                append(" a day")
            },
            style = T.label.copy(fontWeight = FontWeight(600)),
            modifier = Modifier.padding(top = 8.dp),
        )
        Slider(
            value = v,
            onValueChange = { v = (it / 50f).roundToInt() * 50f },
            valueRange = 0f..1500f,
            steps = 29,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = C.under,
                inactiveTrackColor = C.s3,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .testTag("whatif_slider"),
        )
        Row(Modifier.fillMaxWidth()) {
            Text("0", style = T.small, modifier = Modifier.weight(1f))
            Text("1,500", style = T.small)
        }
        Spacer(Modifier.height(10.dp))
        val current = s.forecast?.goal?.projected
        val result = when {
            date == null -> "Pick a deficit above 0 to see a date"
            current != null -> "${F.date(date, today)} · ${F.sooner(date, current)}"
            else -> F.date(date, today)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${F.kgShort(p.goalKg)} kg", style = T.body.copy(color = C.ink), modifier = Modifier.weight(1f))
            Text(
                result,
                style = T.label,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .then(if (date != null) Modifier.gradient(C.weightBrush) else Modifier)
                    .testTag("whatif_result"),
            )
        }
        Text("Only answers a question. Your plan doesn't change.", style = T.small.copy(fontSize = 11.5.sp, color = C.dim), modifier = Modifier.padding(top = 8.dp))
    }
}

// ---- The chart ---------------------------------------------------------------------------------

/**
 * Weigh-ins as dots, the trend as a glowing gradient line with a soft area under it, the projection as
 * a dashed line through each milestone's projected date to the goal, with milestone markers.
 * Everything drawn comes from HomeState (trend + forecast); nothing is computed here.
 */
@Composable
private fun WeightChart(s: HomeState, rangeDays: Int?, compact: Boolean, height: Dp) {
    val context = LocalContext.current
    val today = s.now.toLocalDate()
    val trend = s.trend
    val f = s.forecast
    val p = s.profile
    if (trend.isEmpty() || p == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .background(C.s1)
                .border(1.dp, C.line, androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .testTag("journey_chart"),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                androidx.compose.material3.Icon(Icons.Rounded.Insights, null, tint = C.wt, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                Text("Your trend line starts with your first weigh-in.", style = T.body, textAlign = TextAlign.Center)
            }
        }
        return
    }

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(rangeDays, trend.size) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    val typeface = remember { runCatching { ResourcesCompat.getFont(context, R.font.figtree) }.getOrNull() ?: Typeface.DEFAULT }
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val glowPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND } }
    val dashPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND } }

    // Projection polyline: today -> each unreached milestone's projected date (in order) -> goal.
    val stalledNow = f == null || f.source == RateSource.STALLED
    val trendNow = s.trendKg
    val projPts: List<Pair<LocalDate, Double>> = buildList<Pair<LocalDate, Double>> {
        if (!stalledNow && trendNow != null) {
            add(today to trendNow)
            f?.milestones?.filter { it.reachedOn == null && it.projected != null }?.sortedBy { it.projected?.toEpochDay() }?.forEach { add(it.projected!! to it.kg) }
        }
    }
    val firstDate = trend.first().date
    val goalDate = projPts.lastOrNull()?.first
    val start = if (rangeDays == null) firstDate else later(firstDate, today.minusDays(rangeDays.toLong()))
    val end = when {
        rangeDays == null -> goalDate ?: today.plusDays(30)
        goalDate == null -> today.plusDays((rangeDays / 3).toLong().coerceAtLeast(7))
        else -> earlier(goalDate, today.plusDays(rangeDays.toLong()))
    }.let { if (!it.isAfter(start)) start.plusDays(7) else it }
    val total = ChronoUnit.DAYS.between(start, end).toFloat().coerceAtLeast(1f)

    val visible = trend.filter { !it.date.isBefore(start) }
    val milestones = f?.milestones.orEmpty().filter { it.reachedOn == null && it.projected != null && !it.projected!!.isAfter(end) }
    val nextM = f?.next
    val values = buildList<Double> {
        visible.forEach { tp ->
            add(tp.trend)
            tp.raw?.let { r -> add(r) }
        }
        projPts.filter { !it.first.isAfter(end) }.forEach { add(it.second) }
        // The projection's value where it leaves the window, by interpolating between the two points around [end].
        val after = projPts.indexOfFirst { it.first.isAfter(end) }
        if (after > 0) add(interp(projPts[after - 1], projPts[after], end))
        if (compact && nextM != null) add(nextM.kg)
    }
    val rawLo = values.minOrNull() ?: p.goalKg
    val rawHi = values.maxOrNull() ?: p.startKg
    val pad = maxOf(0.6, (rawHi - rawLo) * 0.08)
    val lo = rawLo - pad
    val hi = rawHi + pad

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .testTag("journey_chart"),
    ) {
        val left = if (compact) 4.dp.toPx() else 34.dp.toPx()
        val right = if (compact) 4.dp.toPx() else 50.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = if (compact) 6.dp.toPx() else 24.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom
        fun x(d: LocalDate) = left + ChronoUnit.DAYS.between(start, d) / total * w
        fun y(v: Double) = top + ((hi - v) / (hi - lo)).toFloat() * h

        textPaint.typeface = typeface
        textPaint.textSize = 11.sp.toPx()

        // Grid + labels.
        if (!compact) {
            val step = niceStep(hi - lo)
            var g = ceil(lo / step) * step
            while (g <= hi) {
                val gy = y(g)
                drawLine(C.s3, Offset(left, gy), Offset(size.width - right, gy), strokeWidth = 1.dp.toPx())
                drawIntoCanvas { c ->
                    textPaint.color = C.dim.toArgb()
                    textPaint.textAlign = Paint.Align.RIGHT
                    c.nativeCanvas.drawText(F.kgShort(g), left - 6.dp.toPx(), gy + 4.dp.toPx(), textPaint)
                }
                g += step
            }
            // Month labels.
            var mDate = start.withDayOfMonth(1).plusMonths(1)
            val months = ChronoUnit.MONTHS.between(start, end).coerceAtLeast(1)
            val every = when {
                months > 18 -> 4L
                months > 8 -> 2L
                else -> 1L
            }
            var k = 0L
            while (!mDate.isAfter(end)) {
                if (k % every == 0L) {
                    drawIntoCanvas { c ->
                        textPaint.color = C.dim.toArgb()
                        textPaint.textAlign = Paint.Align.CENTER
                        c.nativeCanvas.drawText(F.monthShort.format(mDate), x(mDate), size.height - 6.dp.toPx(), textPaint)
                    }
                }
                mDate = mDate.plusMonths(1)
                k++
            }
        } else if (nextM != null) {
            val ny = y(nextM.kg)
            drawLine(Color(0xFF2A2F3D), Offset(0f, ny), Offset(size.width, ny), strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            drawIntoCanvas { c ->
                textPaint.color = C.mute.toArgb()
                textPaint.textAlign = Paint.Align.RIGHT
                c.nativeCanvas.drawText(F.kgShort(nextM.kg), size.width - 2.dp.toPx(), ny - 5.dp.toPx(), textPaint)
            }
        }

        clipRect(right = left + (w + right) * reveal.value) {
            // Uncertainty band and dashed projection.
            if (projPts.size >= 2) {
                val band = Path()
                val span = ChronoUnit.DAYS.between(today, projPts.last().first).toFloat().coerceAtLeast(1f)
                val upper = projPts.map { (d, v) -> Offset(x(d), y(v + 0.4 + 3.2 * (ChronoUnit.DAYS.between(today, d) / span))) }
                val lower = projPts.map { (d, v) -> Offset(x(d), y(v - 0.4 - 3.2 * (ChronoUnit.DAYS.between(today, d) / span))) }
                band.moveTo(upper.first().x, upper.first().y)
                upper.drop(1).forEach { band.lineTo(it.x, it.y) }
                lower.reversed().forEach { band.lineTo(it.x, it.y) }
                band.close()
                drawPath(band, C.wt2.copy(alpha = 0.08f))

                drawIntoCanvas { c ->
                    val path = android.graphics.Path()
                    projPts.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(x(d), y(v)) else path.lineTo(x(d), y(v)) }
                    dashPaint.color = C.wt2.copy(alpha = 0.9f).toArgb()
                    dashPaint.strokeWidth = 2.5.dp.toPx()
                    dashPaint.pathEffect = DashPathEffect(floatArrayOf(5.dp.toPx(), 6.dp.toPx()), 0f)
                    c.nativeCanvas.drawPath(path, dashPaint)
                }
            }

            // Area under the trend.
            if (visible.size >= 2) {
                val area = Path()
                area.moveTo(x(visible.first().date), y(visible.first().trend))
                visible.drop(1).forEach { area.lineTo(x(it.date), y(it.trend)) }
                area.lineTo(x(visible.last().date), top + h)
                area.lineTo(x(visible.first().date), top + h)
                area.close()
                drawPath(area, Brush.verticalGradient(listOf(C.wt.copy(alpha = 0.22f), Color.Transparent), startY = top, endY = top + h))
            }

            // Weigh-in dots.
            visible.forEach { tp -> tp.raw?.let { drawCircle(Color(0xFF6A7086), 2.2.dp.toPx(), Offset(x(tp.date), y(it))) } }

            // The glowing trend line.
            if (visible.isNotEmpty()) {
                val line = Path()
                line.moveTo(x(visible.first().date), y(visible.first().trend))
                visible.drop(1).forEach { line.lineTo(x(it.date), y(it.trend)) }
                val x0 = x(visible.first().date)
                val x1 = maxOf(x(visible.last().date), x0 + 1f)
                drawIntoCanvas { c ->
                    val ap = android.graphics.Path()
                    ap.moveTo(x(visible.first().date), y(visible.first().trend))
                    visible.drop(1).forEach { ap.lineTo(x(it.date), y(it.trend)) }
                    glowPaint.shader = LinearGradient(x0, 0f, x1, 0f, C.wt.toArgb(), C.wt2.toArgb(), Shader.TileMode.CLAMP)
                    glowPaint.strokeWidth = 7.dp.toPx()
                    glowPaint.alpha = 190
                    glowPaint.maskFilter = BlurMaskFilter(5.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                    c.nativeCanvas.drawPath(ap, glowPaint)
                }
                drawPath(
                    line,
                    Brush.horizontalGradient(listOf(C.wt, C.wt2), startX = x0, endX = x1),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                )
            }

            // Milestone markers on the projection.
            if (!compact) {
                milestones.forEach { m ->
                    val cx = x(m.projected!!)
                    val cy = y(m.kg)
                    val isGoal = m.isGoal
                    val r = (if (isGoal) 7 else 5).dp.toPx()
                    drawCircle(C.bg, r, Offset(cx, cy))
                    drawCircle(if (m == nextM) C.wt else C.wt2, r, Offset(cx, cy), style = Stroke(2.5.dp.toPx()))
                    drawIntoCanvas { c ->
                        textPaint.color = (if (isGoal) Color.White else Color(0xFFBFC4D3)).toArgb()
                        textPaint.textAlign = Paint.Align.LEFT
                        textPaint.isFakeBoldText = true
                        textPaint.textSize = (if (isGoal) 13 else 11).sp.toPx()
                        c.nativeCanvas.drawText("${F.kgShort(m.kg)} kg", cx + 8.dp.toPx(), cy - 8.dp.toPx(), textPaint)
                        textPaint.isFakeBoldText = false
                        textPaint.textSize = 11.sp.toPx()
                    }
                }
            }
        }

        // Today.
        s.trendKg?.let { tk ->
            val tx = x(today).coerceIn(left, size.width - right)
            val ty = y(tk)
            drawCircle(C.wt.copy(alpha = 0.35f), 10.dp.toPx(), Offset(tx, ty))
            drawCircle(Color.White, (if (compact) 5 else 6).dp.toPx(), Offset(tx, ty))
            if (!compact) {
                drawIntoCanvas { c ->
                    textPaint.color = Color.White.toArgb()
                    textPaint.textAlign = Paint.Align.LEFT
                    textPaint.isFakeBoldText = true
                    c.nativeCanvas.drawText("Today ${F.kg(tk)}", tx + 12.dp.toPx(), ty + 22.dp.toPx(), textPaint)
                    textPaint.isFakeBoldText = false
                }
            }
        }
    }
}

private fun interp(a: Pair<LocalDate, Double>, b: Pair<LocalDate, Double>, at: LocalDate): Double {
    val span = ChronoUnit.DAYS.between(a.first, b.first).toDouble()
    if (span <= 0) return b.second
    val t = ChronoUnit.DAYS.between(a.first, at) / span
    return a.second + (b.second - a.second) * t
}

private fun niceStep(range: Double): Double = when {
    range > 30 -> 10.0
    range > 12 -> 5.0
    range > 5 -> 2.0
    range > 2 -> 1.0
    else -> 0.5
}

private fun later(a: LocalDate, b: LocalDate): LocalDate = if (a.isAfter(b)) a else b
private fun earlier(a: LocalDate, b: LocalDate): LocalDate = if (a.isBefore(b)) a else b
