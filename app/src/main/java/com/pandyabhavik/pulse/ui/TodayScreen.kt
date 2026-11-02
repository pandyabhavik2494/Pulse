package com.pandyabhavik.pulse.ui

import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.rounded.ExpandMore
import java.util.Locale
import java.time.LocalDate
import com.pandyabhavik.pulse.core.DayLog
import com.pandyabhavik.pulse.core.TwoWeeks
import com.pandyabhavik.pulse.core.Budget
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.Circle
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
    var open by rememberSaveable { mutableStateOf<String?>(null) }
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
        // Bhavik: the dashboard is the page; a tile opens its section in a panel.
        Dashboard(s, onOpen = { open = it })
    }
    open?.let { key -> SectionSheet(s, key, openSheet) { open = null } }
}

/** A section opened from a tile on the cover screen: slides up, already expanded. */
@Composable
private fun SectionSheet(s: HomeState, key: String, openSheet: () -> Unit, onDismiss: () -> Unit) {
    PulseSheet("section_sheet", onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconBtn(Icons.Rounded.Close, "Close", Modifier.testTag("section_close")) { onDismiss() }
            }
            CompositionLocalProvider(LocalPinned provides true) { SectionFor(key, s, openSheet) }
        }
    }
}

/** The section behind a tile: "calories", "activity", "body", "heart" or "sleep". */
@Composable
private fun SectionFor(key: String, s: HomeState, openSheet: () -> Unit) {
    when (key) {
        "calories" -> CaloriesSection(s, openSheet)
        "activity" -> ActivitySection(s)
        "body" -> BodySection(s)
        "heart" -> HeartSection(s)
        else -> SleepSection(s)
    }
}

private val SECTION_TABS = listOf(
    Triple("calories", "Calories", Icons.Rounded.LocalFireDepartment),
    Triple("activity", "Activity", Icons.AutoMirrored.Rounded.DirectionsWalk),
    Triple("body", "Body", Icons.Rounded.MonitorWeight),
    Triple("heart", "Heart", Icons.Rounded.Favorite),
    Triple("sleep", "Sleep", Icons.Rounded.Bedtime),
)

/** Inner screen: pick the section shown on the right (tiles pick it too). */
@Composable
private fun SectionTabs(selected: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SECTION_TABS.forEach { (key, label, icon) ->
            val on = key == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) C.s3 else C.s1)
                    .border(1.dp, if (on) C.soft.copy(alpha = 0.5f) else C.line, RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Show $label") { onPick(key) }
                    .semantics { this.selected = on }
                    .testTag("section_tab_$key"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, label, tint = if (on) C.ink else C.mute, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ---- Inner -------------------------------------------------------------------------------------

@Composable
private fun TodayInner(s: HomeState, openSheet: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf("calories") }
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
                // Bhavik: on the inner screen the overview sits on the left, the sections on the right.
                HealthWarning(s)
                s.recap?.let { RecapCard(it, s.recapCommitted) }
                Dashboard(s, onOpen = { selected = it }, selected = selected)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The section for the tile tapped on the left, already expanded.
                SectionTabs(selected) { selected = it }
                CompositionLocalProvider(LocalPinned provides true) { SectionFor(selected, s, openSheet) }
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
    val color = if (over) C.grow else C.under
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
                    F.balanceWords(over),
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
    val today = s.now.toLocalDate()
    // The last weigh-in from the scale, as the scale showed it. The trend is the line under it.
    val last = s.history.firstOrNull { it.weightKg != null }
    Tile(
        "Weight", Icons.Rounded.MonitorWeight, C.wt,
        last?.weightKg?.let { F.kg(it) } ?: "—",
        when {
            last == null -> "no weigh-in yet"
            else -> "kg ${if (last.date == today) "today" else F.date(last.date, today)}" +
                (s.trendKg?.let { " · trend ${F.kg(it)}" } ?: "")
        },
        modifier,
        valueTag = "weight_value",
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
    tag: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Collapsed by default: just the big number. A tap opens the details (and closes them again).
    val pinned = LocalPinned.current
    var openState by rememberSaveable(tag) { mutableStateOf(false) }
    val open = pinned || openState
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "chevron-$tag")
    PCard(
        modifier
            .testTag(tag)
            .animateContentSize()
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
        onClick = if (pinned) null else ({ openState = !openState }),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(title, style = T.h3.copy(fontSize = 17.sp), maxLines = 1, modifier = Modifier.weight(1f))
            if (trailing != null) trailing()
            if (!pinned) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.ExpandMore,
                    if (open) "Hide details" else "Show details",
                    tint = C.mute,
                    modifier = Modifier.size(24.dp).rotate(turn),
                )
            }
        }
        CompositionLocalProvider(LocalOpen provides open) { content() }
    }
}

/** Whether the section around it is expanded. */
private val LocalOpen = compositionLocalOf { false }

/** True inside the section panel / inner right column: sections there are always open, with no chevron. */
private val LocalPinned = compositionLocalOf { false }

/** The part of a section that shows only once it's tapped open. */
@Composable
private fun Details(content: @Composable ColumnScope.() -> Unit) {
    if (LocalOpen.current) Column(Modifier.fillMaxWidth(), content = content)
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
    Details { Text(detail, style = T.small.copy(fontSize = 13.sp), modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) }
}

/** Mean of the non-null values, rounded, or null when there are none. */
private fun List<Int?>.avgOrNull(): Int? = filterNotNull().takeIf { it.isNotEmpty() }?.average()?.roundToInt()

/** The newest value of [pick] in the last week (today first), with its date; null if none. */
private fun <T> latest(s: HomeState, pick: (DayLog) -> T?): Pair<T, LocalDate>? =
    s.history.take(7).firstNotNullOfOrNull { d -> pick(d)?.let { it to d.date } }

/** "today", or the date it's from, for a value that may be a few days old. */
private fun whenText(date: LocalDate, today: LocalDate): String? = if (date == today) null else "(${F.date(date, today)})"

private fun one(v: Double): String = String.format(Locale.US, "%.1f", v)

/** A row shown only when there's a value from the last week; older-than-today values say their date. */
@Composable
private fun <T> RecentRow(s: HomeState, label: String, pick: (DayLog) -> T?, divider: Boolean = true, tag: String? = null, show: (T) -> String) {
    val v = latest(s, pick) ?: return
    FieldRow(
        label,
        show(v.first),
        if (tag != null) Modifier.testTag(tag) else Modifier,
        note = whenText(v.second, s.now.toLocalDate()),
        divider = divider,
    )
}

/** Three numbers side by side (carbs, fat, protein), each with its label under it. */
@Composable
private fun TripleStat(vararg items: Triple<String, String, Color>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label, color) in items) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(C.s2)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(value, style = T.metric.copy(fontSize = 19.sp, lineHeight = 22.sp, letterSpacing = 0.sp, color = color), maxLines = 1, softWrap = false)
                Text(label, style = T.small, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CaloriesSection(s: HomeState, onClick: () -> Unit) {
    val balance = s.balance
    val color = if (s.isOver) C.grow else C.under
    val shown by animateIntAsState(abs(balance ?: 0), tween(700), label = "balance")
    val d = s.today
    Section("Calories", Icons.Rounded.LocalFireDepartment, C.under, "balance_ring") {
        if (balance != null) {
            BigValue(F.n(shown), F.balanceWords(s.isOver), color, "balance_value", "balance_label")
            Details {
            // Eaten as a share of burned: the empty part is the deficit; full and red means over.
            val burned = s.burned ?: 0
            val frac = if (burned > 0) (s.eaten ?: 0).toFloat() / burned else 0f
            GlowBar(frac, listOf(if (s.isOver) C.grow else C.food), height = 8.dp, glow = false)
            if (s.isOver) Text(
                "You've eaten ${F.n(-balance)} kcal more than you burned so far",
                style = T.small,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
            )
            }
        } else {
            EmptyLine(
                if (s.burned == null) "Waiting for Fitbit" else "No food logged yet",
                if (s.burned == null) "Burned calories appear when your Fitbit syncs." else "Your balance shows once MyFitnessPal logs a meal.",
                titleTag = "balance_label",
            )
        }
        Details {
        BudgetBlock(s)
        FieldRow(
            "Burned",
            s.burned?.let { "${F.n(it)} kcal" } ?: "—",
            note = if (s.resting != null && s.active != null) "(${F.n(s.active)} active)" else null,
        )
        val meals = F.meals(d).size
        FieldRow(
            "Eaten",
            s.eaten?.let { "${F.n(it)} kcal" } ?: "nothing yet",
            valueColor = if (s.eaten == null) C.mute else C.ink,
            note = if (s.eaten != null && meals > 0) "($meals meal${if (meals == 1) "" else "s"})" else null,
            divider = d.carbsG == null && d.fatG == null && d.proteinG == null,
        )
        if (d.carbsG != null || d.fatG != null || d.proteinG != null) {
            TripleStat(
                Triple(d.carbsG?.let { "$it g" } ?: "—", "Carbs", C.food),
                Triple(d.fatG?.let { "$it g" } ?: "—", "Fat", C.wt2),
                Triple(d.proteinG?.let { "$it g" } ?: "—", "Protein", C.steps),
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
        }
        d.fiberG?.let { FieldRow("Fibre", "$it g") }
        d.sugarG?.let { FieldRow("Sugar", "$it g") }
        d.sodiumMg?.let { FieldRow("Sodium", "${F.n(it)} mg") }
        d.waterMl?.let { FieldRow("Water", if (it >= 1000) "${one(it / 1000.0)} L" else "$it ml", valueColor = C.steps) }
        val avg = s.avg7
        FieldRow(
            "7-day average",
            avg?.let { "${F.balanceShort(it)} a day" } ?: "—",
            valueColor = when {
                avg == null -> C.mute
                avg >= 0 -> C.under
                else -> C.grow
            },
        )
        FieldRow(
            "Burned more than eaten",
            when (s.streak) {
                0 -> "Start a streak today"
                1 -> "1 day"
                else -> "${s.streak} days in a row"
            },
            Modifier.testTag("streak_chip"),
            valueColor = if (s.streak > 0) C.hot else C.mute,
        )
        Text(
            "Meal breakdown",
            style = T.label.copy(color = C.steps),
            modifier = Modifier
                .padding(top = 12.dp, bottom = 2.dp)
                .clickable(role = Role.Button) { onClick() }
                .testTag("meal_breakdown"),
        )
        }
    }
}

@Composable
private fun ActivitySection(s: HomeState) {
    val goal = s.profile?.stepGoal ?: 10_000
    val steps by animateIntAsState(s.liveSteps, tween(600), label = "steps")
    val d = s.today
    Section(
        "Activity", Icons.AutoMirrored.Rounded.DirectionsWalk, C.steps, "section_activity",
        trailing = { if (s.liveActive) LivePill() },
    ) {
        BigValue(F.n(steps), "steps of ${F.n(goal)}", C.ink, "steps_value")
        Details {
        GlowBar(s.liveSteps.toFloat() / goal.coerceAtLeast(1), listOf(C.steps), height = 8.dp, glow = false)
        Spacer(Modifier.height(4.dp))
        val left = goal - s.liveSteps
        FieldRow("To go", if (left > 0) "${F.n(left)} steps" else "Goal done", valueColor = if (left > 0) C.ink else C.under)
        FieldRow(
            "Distance",
            d.distanceM?.let { String.format(Locale.US, "%.2f mi", it / 1609.344) } ?: "—",
            Modifier.testTag("distance_value"),
            valueColor = if (d.distanceM == null) C.mute else C.ink,
            note = d.distanceM?.let { String.format(Locale.US, "(%.1f km)", it / 1000.0) },
        )
        d.floors?.let { FieldRow("Floors climbed", "$it") }
        d.exerciseMin?.let { FieldRow("Exercise", "${it / 60}h ${it % 60}m".removePrefix("0h ")) }
        s.active?.let { FieldRow("Active calories", "${F.n(it)} kcal") }
        val avg = s.history.drop(1).take(7).map { it.steps }.avgOrNull()
        FieldRow("7-day average", avg?.let { "${F.n(it)} steps" } ?: "—", valueColor = if (avg == null) C.mute else C.ink, divider = false)
        }
    }
}

@Composable
private fun BodySection(s: HomeState) {
    val p = s.profile ?: return
    val today = s.now.toLocalDate()
    val lastWeighIn = s.history.firstOrNull { it.weightKg != null }
    // The big number is the last weigh-in from the VeSync scale; the smoothed trend sits under it.
    val kg = lastWeighIn?.weightKg
    Section("Body", Icons.Rounded.MonitorWeight, C.wt, "section_body") {
        if (kg == null) {
            EmptyLine("No weigh-in yet", "Step on your VeSync scale and your weight shows here.")
        } else {
            BigValue(F.kg(kg), "kg ${if (lastWeighIn!!.date == today) "today" else F.date(lastWeighIn.date, today)}", C.wt, "weight_value")
            s.trendKg?.let { t ->
                Text(
                    "Trend ${F.kg(t)} kg",
                    style = T.small.copy(fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp).testTag("weight_trend_value"),
                )
            }
            Details {
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
        }
        Details {
        TwoWeekBlock(s)
        val bmiKg = kg
        if (bmiKg != null && p.heightCm > 0) {
            val m = p.heightCm / 100.0
            FieldRow("BMI", one(bmiKg / (m * m)))
        }
        RecentRow(s, "Body fat", { it.bodyFatPct }) { "${one(it)}%" }
        RecentRow(s, "Lean mass", { it.leanKg }) { "${one(it)} kg" }
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
}

/**
 * Where the last 7 days of eating take the weight in two weeks (gain or loss), then what the scale trend
 * actually did in each of the last few two-week stretches. Always shows something: with little data it's
 * an early estimate and says what it's based on.
 */
@Composable
private fun TwoWeekBlock(s: HomeState) {
    val p = s.profile ?: return
    val today = s.now.toLocalDate()
    val o = TwoWeeks.outlook(p, s.history, today)
    val gain = o.changeKg > 0.05
    val color = if (gain) C.grow else C.under
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(C.s2)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("two_week_outlook"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR WEIGHT IN 2 WEEKS", style = T.caps, modifier = Modifier.weight(1f))
            if (o.early) Text("EARLY ESTIMATE", style = T.caps.copy(color = C.food), modifier = Modifier.testTag("two_week_early"))
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                "${F.kg(o.toKg)} kg",
                style = T.metric.copy(fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = 0.sp, color = color),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("two_week_kg"),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    o.changeKg <= -0.05 -> "${F.kg(-o.changeKg)} kg lighter"
                    gain -> "${F.kg(o.changeKg)} kg heavier"
                    else -> "about the same"
                },
                style = T.body.copy(fontSize = 15.sp, color = color),
                maxLines = 1,
                modifier = Modifier.padding(bottom = 3.dp).testTag("two_week_change"),
            )
        }
        val avg = o.avgBalance
        val why = when (o.basis) {
            TwoWeeks.Basis.FOOD_AND_SCALE, TwoWeeks.Basis.FOOD -> {
                val eating = if (avg != null && avg >= 0) "burning ${F.n(avg)} kcal a day more than you eat" else "eating ${F.n(-(avg ?: 0))} kcal a day more than you burn"
                val days = if (o.foodDays == 1) "yesterday" else "the last ${o.foodDays} days"
                "If you keep $eating, like $days." +
                    if (o.basis == TwoWeeks.Basis.FOOD_AND_SCALE) " Checked against your scale." else " Gets sharper with each day of food logged."
            }
            TwoWeeks.Basis.SCALE -> "From your weigh-ins so far. Log food in MyFitnessPal and it follows what you eat."
            TwoWeeks.Basis.TODAY -> "From today so far. Tomorrow it uses a full day of food."
            TwoWeeks.Basis.STARTING_GUESS -> "A starting estimate at the plan's pace (0.75 kg a week). It switches to your real eating after a full day of food."
        }
        Text(why, style = T.small.copy(fontSize = 13.sp), modifier = Modifier.padding(top = 2.dp))
    }
    val periods = TwoWeeks.periods(s.history, today)
    if (periods.isEmpty()) {
        FieldRow("Last 2 weeks", "after 2 weigh-ins", Modifier.testTag("two_week_last"), valueColor = C.mute)
    }
    periods.forEachIndexed { i, per ->
        val label = when {
            i == 0 && per.days < TwoWeeks.DAYS -> "Last ${per.days} day${if (per.days == 1) "" else "s"}"
            i == 0 -> "Last 2 weeks"
            else -> "${F.date(per.start, today)} to ${F.date(per.end, today)}"
        }
        FieldRow(
            label,
            when {
                per.changeKg <= -0.05 -> "${F.kg(-per.changeKg)} kg lost"
                per.changeKg >= 0.05 -> "${F.kg(per.changeKg)} kg gained"
                else -> "steady"
            },
            if (i == 0) Modifier.testTag("two_week_last") else Modifier,
            valueColor = if (per.changeKg >= 0.05) C.grow else C.under,
        )
    }
}

/**
 * How much more can be eaten today and still lose at the plan's pace: the usual daily burn minus 825.
 */
@Composable
private fun BudgetBlock(s: HomeState) {
    val p = s.profile ?: return
    val b = Budget.today(p, s.history, s.now.toLocalDate(), s.trendKg)
    val past = b.left < 0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(C.s2)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("food_budget"),
    ) {
        Text("LEFT TO EAT TODAY", style = T.caps)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                F.n(abs(b.left)),
                style = T.metric.copy(fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = 0.sp, color = if (past) C.grow else C.under),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("budget_left"),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (past) "kcal past today's budget" else "kcal to stay on pace",
                style = T.body.copy(fontSize = 15.sp, color = if (past) C.grow else C.soft),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        val burnFrom = if (b.burnDays > 0) "you usually burn ${F.n(b.burn)} kcal a day" else "you burn about ${F.n(b.burn)} kcal a day (estimated until Fitbit has a full day)"
        Text(
            "Budget ${F.n(b.budget)} kcal: $burnFrom, minus ${F.n(Budget.PACE_KCAL)} to lose 0.75 kg a week. Eaten ${F.n(b.eaten)}.",
            style = T.small.copy(fontSize = 13.sp),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun HeartSection(s: HomeState) {
    val today = s.now.toLocalDate()
    // Fitbit works out resting heart rate after a night's data, so the latest one may be yesterday's.
    val resting = s.history.take(3).firstOrNull { it.restingHr != null }
    val d = s.today
    val anyVitals = latest(s) { it.hrvMs } != null || latest(s) { it.spo2Pct } != null ||
        latest(s) { it.breathingRate } != null || latest(s) { it.vo2Max } != null
    Section("Heart and vitals", Icons.Rounded.Favorite, C.over, "section_heart") {
        if (resting == null && d.hrAvg == null && d.hrLatest == null && !anyVitals) {
            EmptyLine("No heart data yet", "It appears here when your Fitbit syncs to Health Connect.")
            return@Section
        }
        // The big number: the newest reading Fitbit has synced today, else resting, else the day's average.
        when {
            d.hrLatest != null -> BigValue("${d.hrLatest}", "bpm at ${d.hrLatestAt ?: "—"}", C.ink, "heart_latest")
            resting != null -> BigValue("${resting.restingHr}", "bpm resting", C.ink, "heart_resting")
            d.hrAvg != null -> BigValue("${d.hrAvg}", "bpm average", C.ink, "heart_resting")
            else -> EmptyLine("No heart rate today yet", "It appears when your Fitbit syncs to Health Connect.")
        }
        Details {
        Spacer(Modifier.height(4.dp))
        if (d.hrLatest != null || resting == null) FieldRow(
            "Resting",
            resting?.let { "${it.restingHr} bpm" } ?: "—",
            valueColor = if (resting == null) C.mute else C.ink,
            note = resting?.takeIf { it.date != today }?.let { "(${F.date(it.date, today)})" },
        ) else if (resting.date != today) {
            Text("from ${F.date(resting.date, today)}", style = T.small, modifier = Modifier.padding(bottom = 4.dp))
        }
        FieldRow("Average today", d.hrAvg?.let { "$it bpm" } ?: "—", Modifier.testTag("heart_rows"), valueColor = if (d.hrAvg == null) C.mute else C.ink)
        FieldRow(
            "Range today",
            if (d.hrMin != null && d.hrMax != null) "${d.hrMin}–${d.hrMax} bpm" else "—",
            valueColor = if (d.hrMin == null) C.mute else C.ink,
        )
        RecentRow(s, "HRV", { it.hrvMs }) { "$it ms" }
        RecentRow(s, "Blood oxygen", { it.spo2Pct }) { "${one(it)}%" }
        RecentRow(s, "Breathing rate", { it.breathingRate }) { "${one(it)} /min" }
        RecentRow(s, "VO2 max", { it.vo2Max }, divider = false) { one(it) }
        }
    }
}

@Composable
private fun SleepSection(s: HomeState) {
    val d = s.today
    val night = d.sleepMinutes
    val avg = s.history.take(7).map { it.sleepMinutes }.avgOrNull()
    Section("Sleep", Icons.Rounded.Bedtime, C.sleep, "section_sleep") {
        if (night == null) {
            EmptyLine("No sleep recorded last night", "Wear your Fitbit to bed and it shows here in the morning.")
        } else {
            BigValue("${night / 60}h ${night % 60}m", "last night", C.ink, "sleep_value")
            Details {
            if (d.bedTime != null && d.wakeTime != null) {
                Text("${d.bedTime} to ${d.wakeTime}", style = T.small.copy(fontSize = 13.sp), modifier = Modifier.padding(bottom = 8.dp))
            }
            val deep = d.sleepDeepMin ?: 0
            val rem = d.sleepRemMin ?: 0
            val light = d.sleepLightMin ?: 0
            val awake = d.sleepAwakeMin ?: 0
            if (deep + rem + light > 0) {
                StageBar(listOf(deep to C.wt, rem to C.steps, light to C.sleep, awake to C.dim))
                Spacer(Modifier.height(4.dp))
                StageRow("Deep", deep, C.wt)
                StageRow("REM", rem, C.steps)
                StageRow("Light", light, C.sleep)
                StageRow("Awake", awake, C.dim)
            } else {
                GlowBar(night / 480f, listOf(C.sleep), height = 8.dp, glow = false)
                Spacer(Modifier.height(4.dp))
            }
            }
        }
        Details {
        FieldRow("7-night average", avg?.let { F.sleep(it) } ?: "—", Modifier.testTag("sleep_rows"), valueColor = if (avg == null) C.mute else C.ink, divider = false)
        }
    }
}

/** Sleep stages as one bar split into coloured parts, in proportion. */
@Composable
private fun StageBar(parts: List<Pair<Int, Color>>) {
    val total = parts.sumOf { it.first }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((mins, color) in parts) {
            if (mins <= 0) continue
            Box(Modifier.weight(mins.toFloat() / total).fillMaxHeight().background(color))
        }
    }
}

@Composable
private fun StageRow(label: String, minutes: Int, color: Color) {
    FieldRow(label, F.sleep(minutes), leading = Icons.Rounded.Circle, leadingColor = color)
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
            RecapStat("${r.daysUnder}/7", "days burned more", C.under, Modifier.weight(1f))
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
        Text("Beat last week: ${r.focusDaysUnder} days burning more than you ate.", style = T.h3.copy(fontSize = 17.sp), modifier = Modifier.padding(top = 6.dp))
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
