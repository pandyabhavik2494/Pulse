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
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Bedtime

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
        HealthWarning(s)
        CaloriesSection(s, openSheet)
        StepsSection(s)
        WeightSection(s)
        HeartSection(s)
        SleepSection(s)
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
                HealthWarning(s)
                CaloriesSection(s, openSheet)
                StepsSection(s)
                HeartSection(s)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                s.recap?.let { RecapCard(it, s.recapCommitted) }
                WeightSection(s)
                SleepSection(s)
            }
        }
    }
}

// ---- Flex mode ---------------------------------------------------------------------------------

/** Top half in flex mode: today's balance, steps and weight as three big numbers. */
@Composable
fun FlexStatus(s: HomeState) {
    var sheet by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BalanceCard(s, { sheet = true }, Modifier.weight(1.3f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepsTile(s, Modifier.fillMaxWidth())
            WeightTile(s, Modifier.fillMaxWidth())
        }
    }
    if (sheet) BreakdownSheet(s) { sheet = false }
}

/** Bottom half on Today in flex mode: burned, eaten and the next milestone, then the nav (drawn by AppRoot). */
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
            BurnedTile(s, Modifier.weight(1f))
            EatenTile(s, Modifier.weight(1f))
        }
        HeartSection(s)
        SleepSection(s)
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
            onClick = { ui.vm.sync(announce = true) },
        )
    }
}

/** The balance ring with its centre text. Tapping opens the breakdown. */
/**
 * Today's one big number: burned minus eaten, in plain words. Below it the sum it comes from. Tapping
 * opens the breakdown. Before any food is logged it says so instead of showing a number.
 */
@Composable
private fun BalanceCard(s: HomeState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val balance = s.balance
    val over = s.isOver
    val color = if (over) C.over else C.under
    val shown by animateIntAsState(abs(balance ?: 0), tween(700), label = "balance")
    PCard(
        modifier.testTag("balance_ring"),
        borderColor = if (balance == null) C.line else color.copy(alpha = 0.28f),
        background = if (balance == null) null else Brush.verticalGradient(listOf(color.copy(alpha = 0.10f), Color.Transparent)),
        onClick = onClick,
    ) {
        Lbl("Today's balance", Icons.Rounded.LocalFireDepartment, if (balance == null) C.mute else color) {
            Text("Details", style = T.small.copy(color = C.mute), maxLines = 1)
        }
        if (balance != null) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    F.n(shown),
                    style = T.hero.copy(fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = 0.sp, color = color),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag("balance_value"),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (over) "kcal over" else "kcal under",
                    style = T.label.copy(fontSize = 17.sp, color = color),
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 7.dp).testTag("balance_label"),
                )
            }
        } else {
            Text(
                if (s.burned == null) "Waiting for Fitbit" else "No food logged yet",
                style = T.title.copy(color = C.ink),
                maxLines = 1,
                modifier = Modifier.padding(top = 8.dp).testTag("balance_label"),
            )
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = C.ink, fontWeight = FontWeight(700))) { append(s.burned?.let { F.n(it) } ?: "—") }
                append(" burned  −  ")
                withStyle(SpanStyle(color = C.ink, fontWeight = FontWeight(700))) { append(s.eaten?.let { F.n(it) } ?: "0") }
                append(" eaten")
            },
            style = T.small.copy(fontSize = 13.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** One number in a small card: label, value, a short line under it. */
@Composable
private fun Tile(
    label: String,
    icon: ImageVector,
    color: Color,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    valueTag: String? = null,
    valueColor: Color = C.ink,
    bar: Float? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    PCard(modifier, padding = 14.dp) {
        Lbl(label, icon, color, trailing = trailing)
        Text(
            value,
            style = T.metric.copy(fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = 0.sp, color = valueColor),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 6.dp).then(if (valueTag != null) Modifier.testTag(valueTag) else Modifier),
        )
        Text(sub, style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        if (bar != null) {
            Spacer(Modifier.height(8.dp))
            GlowBar(bar, listOf(color), height = 6.dp, glow = false)
        }
    }
}

@Composable
private fun BurnedTile(s: HomeState, modifier: Modifier = Modifier) {
    Tile(
        "Burned", Icons.Rounded.LocalFireDepartment, C.under,
        s.burned?.let { F.n(it) } ?: "—",
        when {
            s.burned == null -> "waiting for Fitbit"
            s.active != null -> "kcal · ${F.n(s.active)} active"
            else -> "kcal so far"
        },
        modifier,
    )
}

@Composable
private fun EatenTile(s: HomeState, modifier: Modifier = Modifier) {
    val meals = F.meals(s.today).size
    Tile(
        "Eaten", Icons.Rounded.Restaurant, C.food,
        s.eaten?.let { F.n(it) } ?: "—",
        when {
            s.eaten == null -> "nothing logged"
            meals > 0 -> "kcal · $meals meal${if (meals == 1) "" else "s"}"
            else -> "kcal"
        },
        modifier,
    )
}

@Composable
private fun StepsTile(s: HomeState, modifier: Modifier = Modifier) {
    val goal = s.profile?.stepGoal ?: 10_000
    val steps by animateIntAsState(s.liveSteps, tween(600), label = "steps")
    Tile(
        "Steps", Icons.AutoMirrored.Rounded.DirectionsWalk, C.steps,
        F.n(steps),
        "of ${F.n(goal)}",
        modifier,
        valueTag = "steps_value",
        bar = s.liveSteps.toFloat() / goal.coerceAtLeast(1),
        trailing = { if (s.liveActive) LivePill() },
    )
}

@Composable
private fun WeightTile(s: HomeState, modifier: Modifier = Modifier) {
    val p = s.profile
    val kg = s.trendKg
    val today = s.now.toLocalDate()
    Tile(
        "Weight", Icons.Rounded.MonitorWeight, C.wt,
        kg?.let { F.kg(it) } ?: "—",
        when {
            kg == null -> "no weigh-in yet"
            p == null -> "kg"
            else -> "kg · ${F.signedKg(kg - p.startKg)} since ${F.date(p.startDate, today)}"
        },
        modifier,
        valueTag = "weight_trend_value",
    )
}

// ---- Sections ----------------------------------------------------------------------------------
// Today is five plain sections: Calories, Steps, Weight, Heart rate, Sleep. Each has one big number,
// at most one simple bar, and a few labelled rows. Nothing else.

/** A section card: icon + title (+ something on the right), then its content. */
@Composable
private fun Section(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(title, style = T.h3.copy(fontSize = 17.sp), maxLines = 1, modifier = Modifier.weight(1f))
            if (trailing != null) trailing()
        }
        content()
    }
}

/** The section's main number with its unit beside it. */
@Composable
private fun BigValue(value: String, unit: String?, color: Color = C.ink, valueTag: String? = null, unitTag: String? = null) {
    Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = T.hero.copy(fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = 0.sp, color = color),
            maxLines = 1,
            softWrap = false,
            modifier = if (valueTag != null) Modifier.testTag(valueTag) else Modifier,
        )
        if (unit != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                unit,
                style = T.label.copy(fontSize = 16.sp, color = if (color == C.ink) C.mute else color),
                maxLines = 1,
                modifier = Modifier.padding(bottom = 5.dp).then(if (unitTag != null) Modifier.testTag(unitTag) else Modifier),
            )
        }
    }
}

/** A short plain line in place of a number when there's no data yet. */
@Composable
private fun EmptyLine(title: String, detail: String, titleTag: String? = null) {
    Text(
        title,
        style = T.title.copy(fontSize = 19.sp),
        modifier = Modifier.padding(top = 12.dp).then(if (titleTag != null) Modifier.testTag(titleTag) else Modifier),
    )
    Text(detail, style = T.small.copy(fontSize = 13.sp), modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
}

/** Mean of the non-null values, rounded, or null when there are none. */
private fun List<Int?>.avgOrNull(): Int? = filterNotNull().takeIf { it.isNotEmpty() }?.average()?.roundToInt()

@Composable
private fun CaloriesSection(s: HomeState, onClick: () -> Unit) {
    val balance = s.balance
    val color = if (s.isOver) C.over else C.under
    val shown by animateIntAsState(abs(balance ?: 0), tween(700), label = "balance")
    Section(
        "Calories", Icons.Rounded.LocalFireDepartment, C.under,
        Modifier.testTag("balance_ring"),
        onClick = onClick,
        trailing = { Text("Details", style = T.small, maxLines = 1) },
    ) {
        if (balance != null) {
            BigValue(F.n(shown), if (s.isOver) "kcal over" else "kcal under", color, "balance_value", "balance_label")
            // Eaten as a share of burned: the empty part is the deficit; full and red means over.
            val burned = s.burned ?: 0
            val frac = if (burned > 0) (s.eaten ?: 0).toFloat() / burned else 0f
            GlowBar(frac, listOf(if (s.isOver) C.over else C.food), height = 8.dp, glow = false)
            Text(
                if (s.isOver) "Eaten more than burned" else "${F.n(balance)} kcal left before you'd be over",
                style = T.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
            )
        } else {
            EmptyLine(
                if (s.burned == null) "Waiting for Fitbit" else "No food logged yet",
                if (s.burned == null) "Burned calories appear when your Fitbit syncs." else "Your balance shows once MyFitnessPal logs a meal.",
                titleTag = "balance_label",
            )
        }
        FieldRow(
            "Burned",
            s.burned?.let { "${F.n(it)} kcal" } ?: "—",
            note = if (s.resting != null && s.active != null) "(${F.n(s.active)} active)" else null,
        )
        val meals = F.meals(s.today).size
        FieldRow(
            "Eaten",
            s.eaten?.let { "${F.n(it)} kcal" } ?: "nothing yet",
            valueColor = if (s.eaten == null) C.mute else C.ink,
            note = if (s.eaten != null && meals > 0) "($meals meal${if (meals == 1) "" else "s"})" else null,
        )
        val avg = s.avg7
        FieldRow(
            "7-day average",
            avg?.let { "${F.balanceShort(it)} a day" } ?: "—",
            valueColor = when {
                avg == null -> C.mute
                avg >= 0 -> C.under
                else -> C.over
            },
        )
        FieldRow(
            "Streak",
            when (s.streak) {
                0 -> "Start a streak today"
                1 -> "1 day under"
                else -> "${s.streak} days under in a row"
            },
            Modifier.testTag("streak_chip"),
            valueColor = if (s.streak > 0) C.hot else C.mute,
            divider = false,
        )
    }
}

@Composable
private fun StepsSection(s: HomeState) {
    val goal = s.profile?.stepGoal ?: 10_000
    val steps by animateIntAsState(s.liveSteps, tween(600), label = "steps")
    Section(
        "Steps", Icons.AutoMirrored.Rounded.DirectionsWalk, C.steps,
        trailing = { if (s.liveActive) LivePill() },
    ) {
        BigValue(F.n(steps), "of ${F.n(goal)}", C.ink, "steps_value")
        GlowBar(s.liveSteps.toFloat() / goal.coerceAtLeast(1), listOf(C.steps), height = 8.dp, glow = false)
        Spacer(Modifier.height(4.dp))
        val left = goal - s.liveSteps
        FieldRow("To go", if (left > 0) F.n(left) else "Goal done", valueColor = if (left > 0) C.ink else C.under)
        val avg = s.history.drop(1).take(7).map { it.steps }.avgOrNull()
        FieldRow("7-day average", avg?.let { F.n(it) } ?: "—", valueColor = if (avg == null) C.mute else C.ink, divider = false)
    }
}

@Composable
private fun WeightSection(s: HomeState) {
    val p = s.profile ?: return
    val kg = s.trendKg
    val today = s.now.toLocalDate()
    val lastWeighIn = s.history.firstOrNull { it.weightKg != null }
    Section("Weight", Icons.Rounded.MonitorWeight, C.wt) {
        if (kg == null) {
            EmptyLine("No weigh-in yet", "Step on your VeSync scale. Pulse uses a smoothed trend, so one heavy morning doesn't count.")
        } else {
            BigValue(F.kg(kg), "kg trend", C.wt, "weight_trend_value")
            val change = kg - p.startKg
            Text(
                "${F.signedKg(change)} kg since ${F.date(p.startDate, today)}",
                style = T.small.copy(fontSize = 13.sp, color = if (change <= 0) C.under else C.mute),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val span = (p.startKg - p.goalKg).coerceAtLeast(0.1)
            GlowBar(((p.startKg - kg) / span).toFloat(), listOf(C.wt, C.wt2), height = 8.dp, glow = false)
            Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                Text("${F.kgShort(p.startKg)} kg", style = T.small, modifier = Modifier.weight(1f))
                Text("Goal ${F.kgShort(p.goalKg)} kg", style = T.small)
            }
        }
        Spacer(Modifier.height(4.dp))
        FieldRow(
            "Last weigh-in",
            lastWeighIn?.let { "${F.kg(it.weightKg!!)} kg" } ?: "—",
            valueColor = if (lastWeighIn == null) C.mute else C.ink,
            note = lastWeighIn?.let { if (it.date == today) "today" else F.date(it.date, today) },
        )
        val f = s.forecast
        val next = f?.next
        FieldRow(
            "Next milestone",
            when {
                f == null -> "—"
                next == null -> "Goal reached"
                else -> "${F.kgShort(next.kg)} kg · ${next.projected?.let { F.date(it, today) } ?: "no date yet"}"
            },
            Modifier.testTag("next_milestone"),
            valueColor = C.wt,
        )
        FieldRow(
            "Goal ${F.kgShort(p.goalKg)} kg",
            when {
                f == null -> "—"
                next == null -> "Reached"
                else -> f.goal.projected?.let { F.date(it, today) } ?: "no date yet"
            },
            divider = false,
        )
    }
}

@Composable
private fun HeartSection(s: HomeState) {
    val today = s.now.toLocalDate()
    // Fitbit works out resting heart rate after a night's data, so the latest one may be yesterday's.
    val resting = s.history.take(3).firstOrNull { it.restingHr != null }
    val day = s.today
    Section("Heart rate", Icons.Rounded.Favorite, C.over) {
        if (resting == null && day.hrAvg == null) {
            EmptyLine("No heart rate yet", "It appears here when your Fitbit syncs to Health Connect.")
            return@Section
        }
        if (resting != null) {
            BigValue("${resting.restingHr}", "bpm resting", C.ink, "heart_resting")
        } else {
            BigValue("${day.hrAvg}", "bpm average", C.ink, "heart_resting")
        }
        if (resting != null && resting.date != today) {
            Text("from ${F.date(resting.date, today)}", style = T.small, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(4.dp))
        FieldRow("Today's average", day.hrAvg?.let { "$it bpm" } ?: "—", valueColor = if (day.hrAvg == null) C.mute else C.ink)
        FieldRow(
            "Today's range",
            if (day.hrMin != null && day.hrMax != null) "${day.hrMin}–${day.hrMax} bpm" else "—",
            valueColor = if (day.hrMin == null) C.mute else C.ink,
            divider = false,
        )
    }
}

@Composable
private fun SleepSection(s: HomeState) {
    val night = s.today.sleepMinutes
    val avg = s.history.take(7).map { it.sleepMinutes }.avgOrNull()
    Section("Sleep", Icons.Rounded.Bedtime, C.sleep) {
        if (night == null) {
            EmptyLine("No sleep recorded last night", "Wear your Fitbit to bed and it shows here in the morning.")
        } else {
            BigValue("${night / 60} h ${night % 60} m", "last night", C.ink, "sleep_value")
            GlowBar(night / 480f, listOf(C.sleep), height = 8.dp, glow = false)
            Spacer(Modifier.height(4.dp))
        }
        FieldRow("7-night average", avg?.let { F.sleep(it) } ?: "—", valueColor = if (avg == null) C.mute else C.ink, divider = false)
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
