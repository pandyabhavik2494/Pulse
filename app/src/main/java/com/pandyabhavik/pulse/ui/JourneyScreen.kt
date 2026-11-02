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
import androidx.compose.foundation.layout.fillMaxHeight
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
    }
}

// ---- Inner -------------------------------------------------------------------------------------

@Composable
private fun JourneyInner(s: HomeState, ps: PaceState) {
    val p = s.profile ?: return
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
                // Bhavik: the trajectory chart added nothing next to the timeline, so it's gone.
                GoalHero(s, ps)
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
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).testTag(tag)) {
        // The line and its dot: the column spans the whole row, so the segments join into one line.
        Box(
            Modifier
                .width(22.dp)
                .fillMaxHeight()
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
