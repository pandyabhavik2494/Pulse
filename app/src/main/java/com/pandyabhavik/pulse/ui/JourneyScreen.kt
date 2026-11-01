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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.clickable
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
        val pace = paceOf(s)
        var kcal by rememberSaveable(pace) { mutableIntStateOf(pace?.coerceIn(0, 1500) ?: 500) }
        val ps = PaceState(kcal, pace) { kcal = it }
        if (ui.wide) JourneyInner(s, ps) else JourneyCover(s, ps, showSettings = !ui.flex)
    }
}

private fun stalled(s: HomeState) = s.forecast?.source == RateSource.STALLED


// ---- Pace (shared by every card on the page) ---------------------------------------------------

/**
 * The page's one question: "what if I burned [kcal] more than I eat a day?". Starts at the 7-day average
 * ([pace]); the hero, the timeline and the chart all follow it. Only answers; the plan never changes.
 */
private class PaceState(val kcal: Int, val pace: Int?, val set: (Int) -> Unit) {
    /** Moved off the 7-day average (with no average, every value is a what-if). */
    val changed: Boolean get() = pace == null || kcal != pace
    val faster: Boolean get() = pace != null && kcal > pace
    val color: Color get() = when {
        !changed -> C.wt
        pace == null -> C.under
        faster -> C.under
        else -> C.hot
    }
}

private fun paceOf(s: HomeState): Int? = s.forecast?.avgDeficit?.takeIf { it > 0 && !stalled(s) }

/** Each unreached milestone: (milestone, date at the chosen pace, date at the 7-day pace). */
private data class MRow(val m: Milestone, val at: LocalDate?, val now: LocalDate?)

private fun rowsFor(s: HomeState, ps: PaceState): List<MRow> {
    val f = s.forecast ?: return emptyList()
    val p = s.profile ?: return emptyList()
    val today = s.now.toLocalDate()
    val trend = s.trendKg ?: p.startKg
    val at = Forecaster.whatIfAll(f, trend, today, ps.kcal)
    val now = ps.pace?.let { Forecaster.whatIfAll(f, trend, today, it) }
    return at.mapIndexed { i, (m, d) -> MRow(m, d, now?.getOrNull(i)?.second) }
}

private fun diffWords(at: LocalDate, now: LocalDate): String? {
    val d = ChronoUnit.DAYS.between(at, now)
    if (d == 0L) return null
    return F.sooner(at, now)
}

// ---- Cover -------------------------------------------------------------------------------------

@Composable
private fun JourneyCover(s: HomeState, ps: PaceState, showSettings: Boolean) {
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
        Header("${F.kgShort(p.startKg)} → ${F.kgShort(p.goalKg)} kg", "Journey") {
            ProgressBadge(s)
            if (showSettings) IconBtn(Icons.Rounded.Settings, "Settings", Modifier.testTag("nav_settings")) { ui.openSettings() }
        }
        GoalHero(s, ps)
        PaceCard(s, ps)
        Timeline(s, ps)
        // Bhavik: on the cover screen the trajectory sits at the bottom.
        TrajectoryCard(s, ps, rangeDays = null, height = 200.dp)
    }
}

// ---- Inner -------------------------------------------------------------------------------------

@Composable
private fun JourneyInner(s: HomeState, ps: PaceState) {
    val p = s.profile ?: return
    var range by rememberSaveable { mutableIntStateOf(3) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Header("${F.kgShort(p.startKg)} → ${F.kgShort(p.goalKg)} kg", "Journey") {
            ProgressBadge(s)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GoalHero(s, ps)
                TrajectoryCard(s, ps, rangeDays = RANGES[range].second, height = 230.dp) {
                    Segmented(RANGES.map { it.first }, range, tags = RANGES.map { "range_${it.first}" }) { range = it }
                }
                PaceCard(s, ps)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp),
            ) {
                Timeline(s, ps)
            }
        }
    }
}

/** "5.4 kg down · 29.6 to go" in the header. */
@Composable
private fun ProgressBadge(s: HomeState) {
    val p = s.profile ?: return
    val t = s.trendKg ?: return
    val down = (p.startKg - t).coerceAtLeast(0.0)
    val left = (t - p.goalKg).coerceAtLeast(0.0)
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.testTag("journey_progress")) {
        Text("${F.kgShort(down)} kg", style = T.h3.copy(fontSize = 20.sp, color = C.wt), maxLines = 1)
        Text("down · ${F.kgShort(left)} to go", style = T.small, maxLines = 1)
    }
}

// ---- Goal hero ---------------------------------------------------------------------------------

@Composable
private fun GoalHero(s: HomeState, ps: PaceState) {
    val p = s.profile ?: return
    val today = s.now.toLocalDate()
    val rows = rowsFor(s, ps)
    val goal = rows.lastOrNull { it.m.isGoal }
    val reached = s.forecast?.goal?.reachedOn
    PCard(background = Brush.linearGradient(listOf(C.wt.copy(alpha = 0.14f), C.wt2.copy(alpha = 0.05f), Color.Transparent))) {
        Lbl(if (ps.changed && ps.pace != null) "What if" else "At your current pace", Icons.Rounded.Flag, C.wt2)
        Text("Goal ${F.kgShort(p.goalKg)} kg on", style = T.label.copy(color = C.soft, fontWeight = FontWeight(600)), modifier = Modifier.padding(top = 8.dp))
        val big = when {
            reached != null -> "Reached ${F.date(reached, today)}"
            s.forecast == null -> "After your first weigh-in"
            goal?.at == null -> "Pick a number above 0 to see dates"
            else -> F.date(goal.at!!, today)
        }
        Text(
            big,
            style = T.metric.copy(fontSize = if (goal?.at != null) 36.sp else 20.sp, lineHeight = 40.sp),
            maxLines = 2,
            modifier = Modifier
                .padding(top = 2.dp)
                .then(if (goal?.at != null || reached != null) Modifier.gradient(C.weightBrush) else Modifier)
                .testTag("whatif_result"),
        )
        val goalAt = goal?.at
        val goalNow = goal?.now
        val diff = if (goalAt != null && goalNow != null) diffWords(goalAt, goalNow)?.let { it to goalNow } else null
        if (diff != null && goalAt != null) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    F.date(diff.second, today),
                    style = T.small.copy(fontSize = 13.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                DeltaPill(diff.first, sooner = goalAt.isBefore(diff.second), tag = "goal_delta")
            }
        }
        Text(heroLine(s, ps), style = T.small.copy(fontSize = 13.sp, lineHeight = 18.sp), modifier = Modifier.padding(top = 8.dp).testTag("journey_line"))
    }
}

private fun heroLine(s: HomeState, ps: PaceState): String {
    val week = { k: Int -> String.format(java.util.Locale.US, "%.2f", k / com.pandyabhavik.pulse.core.Energy.KCAL_PER_KG * 7) }
    return when {
        s.trendKg == null && s.forecast == null -> "Weigh in a few mornings and your dates appear here."
        stalled(s) && !ps.changed -> "Burn more than you eat for a few days to put dates on the calendar."
        stalled(s) -> "Burn more than you eat for a few days to put dates on the calendar. Meanwhile: ${F.n(ps.kcal)} kcal a day more burned is ${week(ps.kcal)} kg a week."
        ps.pace == null -> "No full day of food yet. ${F.n(ps.kcal)} kcal a day more burned than eaten is ${week(ps.kcal)} kg a week."
        ps.changed -> "Burning ${F.n(ps.kcal)} kcal a day more than you eat is ${week(ps.kcal)} kg a week. Your plan doesn't change."
        else -> "Your last 7 days: ${F.n(ps.pace)} kcal a day burned more than eaten, about ${week(ps.pace)} kg a week."
    }
}

@Composable
private fun DeltaPill(text: String, sooner: Boolean, tag: String? = null) {
    val c = if (sooner) C.under else C.hot
    Text(
        text,
        style = T.small.copy(fontSize = 11.5.sp, fontWeight = FontWeight(800), color = c),
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(c.copy(alpha = 0.13f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    )
}

// ---- Pace control ------------------------------------------------------------------------------

@Composable
private fun PaceCard(s: HomeState, ps: PaceState) {
    s.profile ?: return
    PCard {
        Lbl("Try a different pace", Icons.Rounded.Tune, C.mute)
        Text(
            buildAnnotatedString {
                append("If I burn ")
                withStyle(SpanStyle(color = if (ps.changed) ps.color else C.wt, fontWeight = FontWeight(800))) { append("${F.n(ps.kcal)} kcal more") }
                append(" than I eat a day")
            },
            style = T.label.copy(fontSize = 15.sp, fontWeight = FontWeight(600)),
            modifier = Modifier.padding(top = 8.dp),
        )
        Slider(
            value = ps.kcal.toFloat(),
            onValueChange = { ps.set(((it / 50f).roundToInt() * 50).coerceIn(0, 1500)) },
            valueRange = 0f..1500f,
            steps = 29,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = if (ps.changed) ps.color else C.wt,
                inactiveTrackColor = C.s3,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .testTag("whatif_slider"),
        )
        Row(Modifier.fillMaxWidth()) {
            Text("Slower", style = T.small, modifier = Modifier.weight(1f))
            Text("Faster", style = T.small)
        }
        val chips = buildList {
            add(500 to "500")
            ps.pace?.let { add(it to "My pace") }
            add(1000 to "1,000")
            add(1250 to "1,250")
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { (v, label) ->
                val on = ps.kcal == v
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) C.ink else C.s2)
                        .border(1.dp, if (on) C.ink else C.line, RoundedCornerShape(14.dp))
                        .clickable { ps.set(v) }
                        .testTag(if (label == "My pace") "whatif_reset" else "whatif_chip_$v"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = T.small.copy(color = if (on) C.bg else C.soft, fontWeight = FontWeight(800)), maxLines = 1)
                }
            }
        }
        Text(
            ps.pace?.let { "Your 7-day average: ${F.n(it)} kcal a day more burned than eaten" }
                ?: "Your 7-day average appears after a full day of food",
            style = T.small.copy(fontSize = 11.5.sp, color = C.dim),
            modifier = Modifier.padding(top = 10.dp).testTag("whatif_avg"),
        )
    }
}

// ---- Milestone timeline ------------------------------------------------------------------------

@Composable
private fun Timeline(s: HomeState, ps: PaceState) {
    val f = s.forecast
    val p = s.profile
    val today = s.now.toLocalDate()
    if (f == null || p == null || f.milestones.isEmpty()) {
        EmptyNote(Icons.Rounded.Flag, "Milestones are waiting", "Your first weigh-in sets the line, then every 5 kg gets a date.", C.wt, Modifier.testTag("milestone_list"))
        return
    }
    val ahead = rowsFor(s, ps)
    val trend = s.trendKg ?: p.startKg
    PCard(Modifier.testTag("milestone_list")) {
        Lbl("Milestones", Icons.Rounded.Insights, C.wt, trailing = {
            Text(if (ps.changed && ps.pace != null) "vs your current pace" else "at your 7-day pace", style = T.small.copy(fontSize = 11.5.sp))
        })
        Spacer(Modifier.height(10.dp))
        var aheadIndex = 0
        f.milestones.forEachIndexed { i, m ->
            val done = m.reachedOn != null
            val row = if (done) null else ahead.getOrNull(aheadIndex)
            val tag = if (done) "milestone_done_$i" else "whatif_row_${aheadIndex++}"
            val prevKg = f.milestones.getOrNull(i - 1)?.kg ?: p.startKg
            TimelineRow(
                m = m,
                row = row,
                isNext = m == f.next,
                first = i == 0,
                last = i == f.milestones.lastIndex,
                prevDone = i > 0 && f.milestones[i - 1].reachedOn != null,
                progress = if (m == f.next) ((prevKg - trend) / (prevKg - m.kg)).toFloat().coerceIn(0f, 1f) else 0f,
                toGo = (trend - m.kg).coerceAtLeast(0.0),
                shift = if (m == f.next && !ps.changed) s.shiftDays else null,
                paused = stalled(s) && !ps.changed,
                today = today,
                tag = tag,
            )
        }
    }
}

@Composable
private fun TimelineRow(
    m: Milestone,
    row: MRow?,
    isNext: Boolean,
    first: Boolean,
    last: Boolean,
    prevDone: Boolean,
    progress: Float,
    toGo: Double,
    shift: Int?,
    paused: Boolean,
    today: LocalDate,
    tag: String,
) {
    val done = m.reachedOn != null
    val big = isNext || m.isGoal
    val at = row?.at
    val now = row?.now
    val moved = at != null && now != null && at != now
    val sooner = at != null && now != null && at.isBefore(now)
    Row(Modifier.fillMaxWidth().testTag(tag)) {
        // The line and its dot.
        Box(
            Modifier
                .width(22.dp)
                .drawBehind {
                    val x = size.width / 2
                    val mid = 21.dp.toPx()
                    val w = 2.dp.toPx()
                    if (!first) drawLine(if (done || prevDone) C.under else C.line, Offset(x, 0f), Offset(x, mid), strokeWidth = w)
                    if (!last) drawLine(if (done) C.under else C.line, Offset(x, mid), Offset(x, size.height), strokeWidth = w)
                }
                .padding(top = if (big) 13.dp else 15.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                done -> {
                    GlowDot(C.under.copy(alpha = 0.6f), 22.dp)
                    Box(Modifier.size(12.dp).clip(CircleShape).background(C.under))
                }
                isNext -> {
                    GlowDot(C.wt.copy(alpha = 0.7f), 34.dp)
                    Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White).border(3.dp, C.wt.copy(alpha = 0.5f), CircleShape))
                }
                m.isGoal -> {
                    GlowDot(C.wt2.copy(alpha = 0.6f), 28.dp)
                    Box(Modifier.size(16.dp).clip(CircleShape).background(C.wt2))
                }
                else -> Box(Modifier.size(12.dp).clip(CircleShape).background(C.restingBar))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.label,
                    style = (if (big) T.h3.copy(fontSize = 17.sp) else T.h3.copy(fontSize = 15.sp, fontWeight = FontWeight(600))).copy(color = if (done) C.mute else C.ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                val (dateText, dateColor) = when {
                    done -> "Reached ${F.date(m.reachedOn!!, today)}" to C.under
                    paused || at == null -> "—" to C.dim
                    moved -> F.date(at, today) to (if (sooner) C.under else C.hot)
                    m.isGoal -> F.date(at, today) to C.wt2
                    else -> F.date(at, today) to C.soft
                }
                Text(dateText, style = T.h3.copy(fontSize = if (m.isGoal) 17.sp else 14.sp, fontWeight = FontWeight(800), color = dateColor), maxLines = 1)
            }
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${F.kgShort(m.kg)} kg", style = T.small.copy(color = C.dim), modifier = Modifier.weight(1f), maxLines = 1)
                if (moved && at != null && now != null && !paused) {
                    Text(
                        F.date(now, today),
                        style = T.small.copy(color = C.dim, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                    DeltaPill(F.sooner(at, now), sooner)
                }
            }
            if (isNext && !done) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(C.track),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceAtLeast(0.03f))
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(C.weightBrush),
                    )
                }
                Text(
                    "Next up · ${F.kgShort(toGo)} kg to go" + (shift?.takeIf { it != 0 }?.let { " · ${F.shift(it)}" } ?: ""),
                    style = T.small.copy(color = C.soft),
                    modifier = Modifier.padding(top = 5.dp).testTag("next_progress"),
                    maxLines = 2,
                )
            }
        }
    }
}

// ---- Trajectory --------------------------------------------------------------------------------

@Composable
private fun TrajectoryCard(s: HomeState, ps: PaceState, rangeDays: Int?, height: Dp, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val rows = rowsFor(s, ps)
    val showWhatIf = ps.changed && ps.pace != null && rows.any { it.at != null }
    PCard {
        val legend: @Composable RowScope.() -> Unit = {
            Legend(C.wt2, "Now")
            if (showWhatIf) {
                Spacer(Modifier.width(10.dp))
                Legend(ps.color, "What if")
            }
        }
        Lbl("Trajectory", Icons.AutoMirrored.Rounded.TrendingDown, C.wt, trailing = trailing ?: legend)
        if (trailing != null) Row(Modifier.padding(top = 6.dp), content = legend)
        Spacer(Modifier.height(8.dp))
        val whatIf = if (showWhatIf) rows.mapNotNull { r -> r.at?.let { it to r.m.kg } } else emptyList()
        WeightChart(s, rangeDays = rangeDays, compact = false, height = height, whatIf = whatIf, whatIfColor = ps.color)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(3.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = T.small.copy(fontSize = 11.sp), maxLines = 1)
    }
}

// ---- The chart ---------------------------------------------------------------------------------

/**
 * Weigh-ins as dots, the trend as a glowing gradient line with a soft area under it, the projection as
 * a dashed line through each milestone's projected date to the goal, with milestone markers.
 * Everything drawn comes from HomeState (trend + forecast); nothing is computed here.
 */
@Composable
private fun WeightChart(
    s: HomeState,
    rangeDays: Int?,
    compact: Boolean,
    height: Dp,
    whatIf: List<Pair<LocalDate, Double>> = emptyList(),
    whatIfColor: Color = C.under,
) {
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
    // The what-if line: today -> each milestone at the chosen pace.
    val wiPts: List<Pair<LocalDate, Double>> =
        if (whatIf.isEmpty() || trendNow == null) emptyList() else listOf(today to trendNow) + whatIf.sortedBy { it.first.toEpochDay() }
    val firstDate = trend.first().date
    val goalDate = listOfNotNull(projPts.lastOrNull()?.first, wiPts.lastOrNull()?.first).maxOrNull()
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
        wiPts.filter { !it.first.isAfter(end) }.forEach { add(it.second) }
        val wiAfter = wiPts.indexOfFirst { it.first.isAfter(end) }
        if (wiAfter > 0) add(interp(wiPts[wiAfter - 1], wiPts[wiAfter], end))
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

            // The what-if line, solid and glowing, with a dot at each milestone.
            if (wiPts.size >= 2) {
                drawIntoCanvas { c ->
                    val path = android.graphics.Path()
                    wiPts.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(x(d), y(v)) else path.lineTo(x(d), y(v)) }
                    glowPaint.shader = null
                    glowPaint.color = whatIfColor.toArgb()
                    glowPaint.strokeWidth = 6.dp.toPx()
                    glowPaint.alpha = 150
                    glowPaint.maskFilter = BlurMaskFilter(5.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                    c.nativeCanvas.drawPath(path, glowPaint)
                    glowPaint.maskFilter = null
                    glowPaint.alpha = 255
                    glowPaint.strokeWidth = 3.dp.toPx()
                    c.nativeCanvas.drawPath(path, glowPaint)
                }
                wiPts.drop(1).forEach { (d, v) -> drawCircle(whatIfColor, 3.5.dp.toPx(), Offset(x(d), y(v))) }
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
