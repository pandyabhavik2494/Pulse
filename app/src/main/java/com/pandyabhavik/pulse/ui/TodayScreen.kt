package com.pandyabhavik.pulse.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandyabhavik.pulse.core.Motivation
import com.pandyabhavik.pulse.core.WeeklyRecap
import com.pandyabhavik.pulse.data.HealthStatus
import com.pandyabhavik.pulse.data.HomeState
import kotlin.math.abs

@Composable
fun TodayScreen(s: HomeState) {
    val ui = LocalUi.current
    val syncing by ui.vm.syncing.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(false) }
    val openSheet = { sheet = true }
    PullBox(
        refreshing = syncing,
        onRefresh = { ui.vm.sync() },
        modifier = Modifier
            .fillMaxSize()
            .testTag("today_screen"),
    ) {
        when {
            ui.flex -> TodayFlexDetails(s)
            ui.wide -> TodayInner(s, openSheet)
            else -> TodayCover(s, openSheet)
        }
    }
    if (sheet) BreakdownSheet(s) { sheet = false }
}

// ---- Cover -------------------------------------------------------------------------------------

@Composable
private fun TodayCover(s: HomeState, openSheet: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TodayHeader(s, showSettings = true)
        s.recap?.let { RecapCard(it, s.recapCommitted) }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val ring = minOf(maxWidth * 0.68f, 280.dp)
            Box(Modifier.padding(vertical = 14.dp)) {
                RingBlock(s, ring, showHint = true, onClick = openSheet)
            }
        }
        SplitLine(s)
        if (s.isOver) SpillCard(s)
        MotivationChips(s)
        StepsCard(s)
        WeightCard(s, compactNext = false)
        HealthWarning(s)
    }
}

// ---- Inner -------------------------------------------------------------------------------------

@Composable
private fun TodayInner(s: HomeState, openSheet: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        TodayHeader(s, showSettings = false)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val ring = minOf(maxWidth * 0.8f, 290.dp)
                    Box(Modifier.padding(vertical = 14.dp)) { RingBlock(s, ring, showHint = false, onClick = openSheet) }
                }
                SplitLine(s)
                if (s.isOver) SpillCard(s)
                MotivationChips(s)
                StepsCard(s)
                WeightCard(s, compactNext = true)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                s.recap?.let { RecapCard(it, s.recapCommitted) }
                BurnedCard(s)
                EatenCard(s)
                WeekBalanceCard(s)
                HealthWarning(s)
            }
        }
    }
}

// ---- Flex mode ---------------------------------------------------------------------------------

/** Top half in flex mode: a big, readable status display. */
@Composable
fun FlexStatus(s: HomeState) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val ring = minOf(maxHeight * 0.8f, maxWidth * 0.42f, 300.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            var sheet by rememberSaveable { mutableStateOf(false) }
            RingBlock(s, ring, showHint = false, showKicker = false, onClick = { sheet = true })
            if (sheet) BreakdownSheet(s) { sheet = false }
            Column {
                Text(F.dayShort(s.now.toLocalDate()).uppercase(), style = T.caps)
                Spacer(Modifier.height(8.dp))
                val steps by animateIntAsState(s.liveSteps, tween(700), label = "flex-steps")
                Text(F.n(steps), style = T.hero.copy(fontSize = 44.sp, lineHeight = 46.sp, color = C.steps).glow(C.steps, 28f), maxLines = 1, modifier = Modifier.testTag("steps_value"))
                Text("steps", style = T.small.copy(fontSize = 13.sp))
                Spacer(Modifier.height(12.dp))
                val kg = s.trendKg
                if (kg != null) {
                    Text(F.kg(kg), style = T.hero.copy(fontSize = 44.sp, lineHeight = 46.sp), maxLines = 1, modifier = Modifier.gradient(C.weightBrush).testTag("weight_trend_value"))
                    val next = s.forecast?.next
                    Text(
                        if (next != null) "kg · ${F.kgShort(next.kg)} by ${next.projected?.let { F.date(it, s.now.toLocalDate()) } ?: "—"}" else "kg on trend",
                        style = T.small.copy(fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("next_milestone"),
                    )
                } else {
                    Text("Weigh in to start your trend", style = T.small.copy(fontSize = 13.sp), maxLines = 2)
                }
            }
        }
    }
}

/** Bottom half on Today in flex mode: burned, eaten, one line, then the nav (drawn by AppRoot). */
@Composable
private fun TodayFlexDetails(s: HomeState) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PCard(Modifier.weight(1f)) {
                Lbl("Burned", Icons.Rounded.LocalFireDepartment, C.under)
                Metric(s.burned?.let { F.n(it) } ?: "—", if (s.resting != null && s.active != null) "${F.n(s.resting)} + ${F.n(s.active)} active" else "kcal")
            }
            PCard(Modifier.weight(1f)) {
                Lbl("Eaten", Icons.Rounded.Restaurant, C.food)
                val meals = F.meals(s.today).size
                Metric(s.eaten?.let { F.n(it) } ?: "—", if (s.eaten == null) "nothing logged yet" else "$meals meal${if (meals == 1) "" else "s"} logged")
            }
        }
        if (s.isOver) SpillCard(s) else MotivationChips(s)
        s.recap?.let { RecapCard(it, s.recapCommitted) }
    }
}

// ---- Pieces ------------------------------------------------------------------------------------

@Composable
private fun TodayHeader(s: HomeState, showSettings: Boolean) {
    val ui = LocalUi.current
    val name = s.profile?.name?.takeIf { it.isNotBlank() } ?: "there"
    Header(F.dayShort(s.now.toLocalDate()), "${F.partOfDay(s.now)}, $name") {
        SyncChip(s)
        if (showSettings) IconBtn(Icons.Rounded.Settings, "Settings", Modifier.testTag("nav_settings")) { ui.openSettings() }
    }
}

@Composable
fun SyncChip(s: HomeState) {
    val ui = LocalUi.current
    val syncing by ui.vm.syncing.collectAsStateWithLifecycle()
    val failed by ui.vm.lastSyncFailed.collectAsStateWithLifecycle()
    val needsAccess = s.health != HealthStatus.AVAILABLE || s.missingPermissions.isNotEmpty() && s.lastSync == null
    when {
        needsAccess -> Chip("Connect", color = C.food, background = C.food.copy(alpha = 0.13f), dot = C.food) { ui.askHealth() }
        syncing -> Chip("Syncing…", dot = C.steps)
        else -> Chip(
            F.syncAgo(s.lastSync, s.now),
            dot = if (failed) C.over else C.under,
            onClick = { ui.vm.sync() },
        )
    }
}

/** The balance ring with its centre text. Tapping opens the breakdown. */
@Composable
private fun RingBlock(s: HomeState, size: Dp, showHint: Boolean, showKicker: Boolean = true, onClick: () -> Unit) {
    val balance = s.balance
    val over = s.isOver
    val shown by animateIntAsState(abs(balance ?: 0), tween(1000), label = "balance")
    val color = if (over) C.over else C.under
    BalanceRing(
        burned = s.burned,
        eaten = s.eaten,
        style = s.ringStyle,
        size = size,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Show the breakdown",
            ) { onClick() }
            .testTag("balance_ring"),
    ) {
        Column(
            Modifier.padding(horizontal = size * 0.14f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showKicker) Text("TODAY", style = T.caps.copy(letterSpacing = 1.6.sp))
            if (balance != null) {
                val text = F.n(shown)
                val base = (size.value * 0.215f).coerceAtMost(64f)
                Text(
                    text,
                    style = T.hero.copy(
                        fontSize = ringNumberSize(F.n(abs(balance)), base).sp,
                        lineHeight = (ringNumberSize(F.n(abs(balance)), base) * 1.05f).sp,
                    ).glow(color),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag("balance_value"),
                )
                Text(
                    if (over) "kcal over" else "kcal under",
                    style = T.label.copy(fontSize = 15.sp).glow(color, 24f),
                    maxLines = 1,
                    modifier = Modifier.testTag("balance_label"),
                )
            } else {
                Text("—", style = T.hero.copy(fontSize = (size.value * 0.2f).coerceAtMost(56f).sp, color = C.mute), maxLines = 1)
                Text(
                    if (s.burned == null) "Waiting for Fitbit" else "No food logged yet",
                    style = T.label.copy(color = C.mute),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
            if (showHint) {
                Spacer(Modifier.height(6.dp))
                Text("Tap for the breakdown", style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SplitLine(s: HomeState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        SplitPart(s.burned, "burned")
        Spacer(Modifier.width(20.dp))
        SplitPart(s.eaten, "eaten")
    }
}

@Composable
private fun SplitPart(v: Int?, what: String) {
    val n by animateIntAsState(v ?: 0, tween(900), label = "split-$what")
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = C.ink, fontWeight = FontWeight(700))) { append(if (v == null) "—" else F.n(n)) }
            append(" $what")
        },
        style = T.small.copy(fontSize = 13.sp),
        maxLines = 1,
    )
}

@Composable
private fun SpillCard(s: HomeState) {
    val overBy = -(s.balance ?: 0)
    PCard(borderColor = C.over.copy(alpha = 0.18f)) {
        LineNote(Icons.Rounded.Favorite, C.mute) {
            Text(
                Motivation.spillLine(overBy, s.avg7),
                style = T.body.copy(color = C.soft, fontSize = 13.5.sp),
                modifier = Modifier.testTag("spill_line"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MotivationChips(s: HomeState) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val streak = s.streak
        Chip(
            when (streak) {
                0 -> "Start a streak today"
                1 -> "1 day under"
                else -> "$streak days under in a row"
            },
            icon = Icons.Rounded.LocalFireDepartment,
            color = C.hot,
            background = C.hot.copy(alpha = 0.13f),
            modifier = Modifier.testTag("streak_chip"),
        )
        val toWin = s.toNextWin
        Chip(
            if (toWin != null) "${F.kg(toWin)} kg to your next win" else "Weigh in to unlock wins",
            icon = Icons.Rounded.Bolt,
            color = C.wt,
            background = C.wt.copy(alpha = 0.13f),
            modifier = Modifier.testTag("win_chip"),
        )
    }
}

@Composable
private fun StepsCard(s: HomeState) {
    val goal = s.profile?.stepGoal ?: 10_000
    val steps by animateIntAsState(s.liveSteps, tween(700), label = "steps")
    PCard {
        Lbl("Steps", Icons.AutoMirrored.Rounded.DirectionsWalk, C.steps) {
            if (s.liveActive) LivePill()
        }
        Metric(F.n(steps), "/ ${F.n(goal)}", valueModifier = Modifier.testTag("steps_value"))
        GlowBar(s.liveSteps.toFloat() / goal.coerceAtLeast(1), listOf(C.steps))
    }
}

@Composable
private fun WeightCard(s: HomeState, compactNext: Boolean) {
    val p = s.profile ?: return
    val kg = s.trendKg
    val today = s.now.toLocalDate()
    PCard {
        Lbl("Weight trend", Icons.Rounded.MonitorWeight, C.wt)
        if (kg == null) {
            Spacer(Modifier.height(6.dp))
            Text("Step on the scale to start your trend line.", style = T.body)
            Text("Pulse smooths weigh-ins, so one salty dinner never ruins a morning.", style = T.small, modifier = Modifier.padding(top = 4.dp))
            return@PCard
        }
        val next = s.forecast?.next
        val nextText = when {
            next == null -> "Goal reached"
            next.projected != null -> "${F.kgShort(next.kg)} kg · ${F.date(next.projected!!, today)}"
            else -> "${F.kgShort(next.kg)} kg"
        }
        val change = kg - p.startKg
        Metric(
            F.kg(kg),
            null,
            brush = C.weightBrush,
            valueModifier = Modifier.testTag("weight_trend_value"),
        ) {
            Text(
                buildAnnotatedString {
                    append("kg · ")
                    if (compactNext) {
                        append("next ")
                        withStyle(SpanStyle(color = C.ink, fontWeight = FontWeight(700))) { append(nextText) }
                    } else {
                        withStyle(SpanStyle(color = if (change <= 0) C.under else C.mute)) {
                            append("${F.signedKg(change)} since ${F.date(p.startDate, today)}")
                        }
                    }
                },
                style = T.small.copy(fontSize = 13.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (compactNext) Modifier.testTag("next_milestone") else Modifier,
            )
        }
        val target = next?.kg ?: p.goalKg
        val span = (p.startKg - target).coerceAtLeast(0.1)
        GlowBar(((p.startKg - kg) / span).toFloat(), listOf(C.wt, C.wt2))
        if (!compactNext) {
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(F.kgShort(p.startKg), style = T.small, modifier = Modifier.weight(1f))
                Text(
                    buildAnnotatedString {
                        append(if (next == null) "" else "Next: ")
                        withStyle(SpanStyle(fontWeight = FontWeight(800))) { append(nextText) }
                    },
                    style = T.small.copy(color = C.ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("next_milestone"),
                )
            }
        }
    }
}

@Composable
private fun HealthWarning(s: HomeState) {
    val ui = LocalUi.current
    if (s.health == HealthStatus.AVAILABLE && s.missingPermissions.isEmpty()) return
    PCard(borderColor = C.food.copy(alpha = 0.3f), onClick = { ui.askHealth() }) {
        LineNote(Icons.Rounded.ErrorOutline, C.food) {
            Text(
                when (s.health) {
                    HealthStatus.NOT_INSTALLED -> "Health Connect isn't installed. Tap to get it, so Pulse can read your Fitbit, food and scale."
                    HealthStatus.NEEDS_UPDATE -> "Health Connect needs an update. Tap to update it."
                    HealthStatus.AVAILABLE -> "Some Health Connect access is off (${s.missingPermissions.size}). Tap to allow it."
                },
                style = T.body.copy(fontSize = 13.5.sp),
            )
        }
    }
}

@Composable
fun BurnedCard(s: HomeState) {
    PCard {
        Lbl("Burned", Icons.Rounded.LocalFireDepartment, C.under)
        val burned = s.burned
        if (burned == null) {
            Text("Waiting for your Fitbit to sync.", style = T.body, modifier = Modifier.padding(top = 8.dp))
            return@PCard
        }
        Metric(F.n(burned), "kcal from Fitbit")
        if (s.resting != null && s.active != null) {
            SplitBar(s.resting, s.active)
            Row(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = C.ink, fontWeight = FontWeight(700))) { append(F.n(s.resting)) }
                        append(" resting")
                    },
                    style = T.small, modifier = Modifier.weight(1f), maxLines = 1,
                )
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = C.under, fontWeight = FontWeight(700))) { append(F.n(s.active)) }
                        append(" active")
                    },
                    style = T.small, maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun EatenCard(s: HomeState) {
    PCard {
        Lbl("Eaten · MyFitnessPal", Icons.Rounded.Restaurant, C.food)
        val meals = F.meals(s.today)
        if (s.eaten == null || meals.isEmpty()) {
            Text(
                if (s.eaten == null) "Nothing logged yet today. Meals appear here as MyFitnessPal syncs." else "${F.n(s.eaten)} kcal logged.",
                style = T.body,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Spacer(Modifier.height(4.dp))
            meals.forEachIndexed { i, (name, kcal) ->
                FieldRow(name, F.n(kcal), divider = i < meals.lastIndex || s.today.proteinG != null)
            }
        }
        s.today.proteinG?.let { FieldRow("Protein", "$it g", valueColor = C.mute, divider = false) }
    }
}

/** Seven days of balance bars around a zero line: mint up for under, red down for over. */
@Composable
private fun WeekBalanceCard(s: HomeState) {
    val days = s.history.take(7).reversed()
    PCard {
        Lbl("This week's balance")
        if (days.none { it.balance != null }) {
            Text("Bars appear once a few days have food logged.", style = T.body, modifier = Modifier.padding(top = 8.dp))
            return@PCard
        }
        val maxAbs = days.maxOf { abs(it.balance ?: 0) }.coerceAtLeast(600).toFloat()
        val grow by animateFloatAsState(1f, tween(900), label = "week")
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .padding(top = 10.dp),
        ) {
            val mid = size.height * 0.62f
            val up = mid - 4f
            val down = size.height - mid - 4f
            drawLine(Color(0xFF2A2F3D), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.5f)
            val slot = size.width / 7f
            val bw = minOf(slot * 0.55f, 30.dp.toPx())
            days.forEachIndexed { i, d ->
                val b = d.balance ?: return@forEachIndexed
                val x = i * slot + (slot - bw) / 2f
                val h = (abs(b) / maxAbs) * (if (b >= 0) up else down) * grow
                val c = if (b >= 0) C.under else C.over
                val alpha = if (i == days.lastIndex) 1f else 0.75f
                val top = if (b >= 0) mid - h else mid
                drawRoundRect(
                    c.copy(alpha = alpha),
                    topLeft = Offset(x, top),
                    size = Size(bw, h.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                Text(
                    F.weekdayShort.format(d.date).take(1),
                    style = T.small.copy(color = if (i == days.lastIndex) C.ink else C.dim, fontWeight = FontWeight(700)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ---- Monday recap ------------------------------------------------------------------------------

@Composable
private fun RecapCard(r: WeeklyRecap, committed: Boolean) {
    val ui = LocalUi.current
    PCard(
        Modifier.animateContentSize().testTag("recap_card"),
        background = Brush.linearGradient(listOf(C.under.copy(alpha = 0.14f), Color.Transparent)),
        borderColor = C.under.copy(alpha = 0.22f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${F.ordinalWeek(r.weekNumber)}, done".uppercase(), style = T.caps.copy(color = C.under))
                Text("Trend this week", style = T.small, modifier = Modifier.padding(top = 2.dp))
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(C.s2)
                    .clickable { ui.vm.dismissRecap() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Dismiss recap", tint = C.mute, modifier = Modifier.size(18.dp)) }
        }
        val lost = r.trendChangeKg <= 0
        Metric(
            F.signedKg(r.trendChangeKg),
            "kg",
            valueStyle = T.hero.copy(fontSize = 40.sp, lineHeight = 42.sp, color = if (lost) C.under else C.ink).let { if (lost) it.glow(C.under, 24f) else it },
        )
        LineNote(Icons.AutoMirrored.Rounded.TrendingDown, C.under) { Text(r.headline, style = T.body.copy(fontSize = 13.5.sp)) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecapStat("${r.daysUnder}/7", "days under", C.under, Modifier.weight(1f))
            RecapStat(F.compact(r.totalSteps), "steps", C.steps, Modifier.weight(1f))
            RecapStat(
                r.bestDayBalance?.let { F.n(it) } ?: "—",
                r.bestDay?.let { "best day, ${F.weekdayShort.format(it)}" } ?: "best day",
                C.ink,
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Lbl("This week's one thing", Icons.Rounded.Flag, C.wt)
        Text("Beat last week: ${r.focusDaysUnder} days under.", style = T.h3.copy(fontSize = 17.sp), modifier = Modifier.padding(top = 6.dp))
        Text(r.focusTip, style = T.small.copy(fontSize = 13.sp), modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        if (committed) {
            Chip("You're in. Pulse will check back next Monday.", color = C.under, background = C.under.copy(alpha = 0.12f), dot = C.under)
        } else {
            PButton("I'm in", Modifier.testTag("recap_commit"), height = 46.dp) { ui.vm.commitRecap() }
        }
    }
}

@Composable
private fun RecapStat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(C.s2)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = T.metric.copy(fontSize = 20.sp, lineHeight = 22.sp, color = color), maxLines = 1, softWrap = false)
        Text(label, style = T.small.copy(fontSize = 11.sp), maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}
