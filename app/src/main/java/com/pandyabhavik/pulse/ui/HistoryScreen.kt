package com.pandyabhavik.pulse.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandyabhavik.pulse.core.DayLog
import com.pandyabhavik.pulse.data.HomeState
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.min

@Composable
fun HistoryScreen(s: HomeState) {
    val ui = LocalUi.current
    val syncing by ui.vm.syncing.collectAsStateWithLifecycle()
    val today = s.now.toLocalDate()
    var monthBack by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var sheetDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val month = YearMonth.from(today).minusMonths(monthBack.toLong())
    PullBox(
        refreshing = syncing,
        onRefresh = { ui.vm.sync() },
        modifier = Modifier
            .fillMaxSize()
            .testTag("history_screen"),
    ) {
        if (ui.wide) {
            HistoryInner(s, month, monthBack, { monthBack = it }, LocalDate.ofEpochDay(selected)) { selected = it.toEpochDay() }
        } else {
            HistoryCover(s, month, monthBack, { monthBack = it }, showSettings = !ui.flex) { sheetDay = it.toEpochDay() }
        }
    }
    sheetDay?.let { day ->
        PulseSheet("day_sheet", onDismiss = { sheetDay = null }) {
            DayDetail(s, LocalDate.ofEpochDay(day), inCard = false)
        }
    }
}

// ---- Cover -------------------------------------------------------------------------------------

@Composable
private fun HistoryCover(
    s: HomeState,
    month: YearMonth,
    monthBack: Int,
    setMonthBack: (Int) -> Unit,
    showSettings: Boolean,
    openDay: (LocalDate) -> Unit,
) {
    val ui = LocalUi.current
    var calendar by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header("History", if (calendar) F.monthYear.format(month) else "Last 7 days") {
            IconBtn(if (calendar) Icons.AutoMirrored.Rounded.List else Icons.Rounded.DateRange, if (calendar) "Show list" else "Show calendar", Modifier.testTag("history_toggle")) {
                calendar = !calendar
            }
            if (showSettings) IconBtn(Icons.Rounded.Settings, "Settings", Modifier.testTag("nav_settings")) { ui.openSettings() }
        }
        AnimatedContent(calendar, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "history-mode") { cal ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (cal) {
                    MonthNav(month, monthBack, setMonthBack)
                    Calendar(s, month, selected = null, onDay = openDay)
                    MonthSummary(s, month)
                } else {
                    WeekSummary(s)
                    WeekRows(s, openDay)
                }
            }
        }
    }
}

@Composable
private fun WeekSummary(s: HomeState) {
    val week = s.history.take(7)
    val stepsDays = week.mapNotNull { it.steps }
    val avgSteps = if (stepsDays.isEmpty()) null else stepsDays.sum() / stepsDays.size
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Lbl("7-day average")
                val a = s.avg7
                if (a == null) {
                    Text("Log food for a day to see it", style = T.small, modifier = Modifier.padding(top = 6.dp))
                } else {
                    Metric(
                        F.n(abs(a)),
                        if (a >= 0) "kcal under / day" else "kcal over / day",
                        valueStyle = T.metric.glow(if (a >= 0) C.under else C.over, 24f),
                        modifier = Modifier.padding(top = 0.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("STEPS", style = T.caps)
                Text(
                    avgSteps?.let { F.n(it) } ?: "—",
                    style = T.metric.copy(fontSize = 22.sp, lineHeight = 24.sp, color = C.steps),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun WeekRows(s: HomeState, openDay: (LocalDate) -> Unit) {
    val today = s.now.toLocalDate()
    val byDate = s.history.associateBy { it.date }
    val days = (0 until 7).map { today.minusDays(it.toLong()) }
    Column(Modifier.fillMaxWidth().testTag("history_list")) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("DAY", style = T.caps.copy(color = C.dim), modifier = Modifier.width(52.dp))
            Text("BALANCE", style = T.caps.copy(color = C.dim), modifier = Modifier.weight(1f))
            Text("STEPS", style = T.caps.copy(color = C.dim), modifier = Modifier.width(66.dp))
            Text("KG", style = T.caps.copy(color = C.dim), textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
        days.forEach { date ->
            val d = byDate[date]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { openDay(date) }
                    .padding(vertical = 10.dp)
                    .testTag("history_row_$date"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(52.dp)) {
                    Text(F.weekdayShort.format(date), style = T.small, maxLines = 1)
                    Text("${date.dayOfMonth}", style = T.h3.copy(fontSize = 15.sp), maxLines = 1)
                }
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    val b = d?.balance
                    if (b == null) {
                        Text(if (date == today) "so far: —" else "no food logged", style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(F.balanceShort(b), style = T.label.copy(fontSize = 13.sp, color = if (b >= 0) C.under else C.over), maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        GlowBar(min(1f, abs(b) / 1000f), listOf(if (b >= 0) C.under else C.over), height = 5.dp, glow = abs(b) > 600)
                    }
                }
                Text(d?.steps?.let { F.n(it) } ?: "—", style = T.label.copy(fontSize = 13.sp, color = C.steps), maxLines = 1, modifier = Modifier.width(66.dp))
                Text(d?.weightKg?.let { F.kg(it) } ?: "—", style = T.label.copy(fontSize = 13.sp), maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
        }
    }
}

// ---- Inner -------------------------------------------------------------------------------------

@Composable
private fun HistoryInner(
    s: HomeState,
    month: YearMonth,
    monthBack: Int,
    setMonthBack: (Int) -> Unit,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Header("History", F.monthYear.format(month)) { MonthNav(month, monthBack, setMonthBack) }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Calendar(s, month, selected, onSelect)
                MonthSummary(s, month)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp),
            ) {
                AnimatedContent(selected, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "day") { d ->
                    DayDetail(s, d, inCard = true)
                }
            }
        }
    }
}

// ---- Month calendar ----------------------------------------------------------------------------

@Composable
private fun MonthNav(month: YearMonth, monthBack: Int, setMonthBack: (Int) -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(C.s2)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(enabled = monthBack < 12) { setMonthBack(monthBack + 1) }
                .testTag("month_prev"),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", tint = C.mute) }
        Text(
            "${F.monthShort.format(month.minusMonths(1))} · ${F.monthShort.format(month.plusMonths(1))}",
            style = T.small.copy(fontSize = 12.sp),
            maxLines = 1,
        )
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(enabled = monthBack > 0) { setMonthBack(monthBack - 1) }
                .testTag("month_next"),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", tint = if (monthBack > 0) C.mute else C.dim) }
    }
}

@Composable
private fun Calendar(s: HomeState, month: YearMonth, selected: LocalDate?, onDay: (LocalDate) -> Unit) {
    val today = s.now.toLocalDate()
    val byDate = s.history.associateBy { it.date }
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1 // Monday first
    val cells = lead + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    Column(Modifier.fillMaxWidth().testTag("history_calendar"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = T.small.copy(fontSize = 11.sp, color = C.dim), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0 until 7) {
                    val i = r * 7 + c - lead
                    if (i < 0 || i >= month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(i + 1)
                        DayCell(date, byDate[date], future = date.isAfter(today), selected = date == selected, modifier = Modifier.weight(1f)) { onDay(date) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, d: DayLog?, future: Boolean, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val b = d?.balance
    val a = if (b == null) 0f else min(1f, 0.25f + abs(b) / 1200f)
    val base = if (b == null) null else if (b >= 0) C.under else C.over
    val textColor = when {
        future -> C.dim
        b != null && a > 0.6f -> Color(0xFF05060A)
        else -> C.ink
    }
    Box(
        modifier
            .aspectRatio(1f)
            .then(if (base != null && a > 0.75f) Modifier.shadow(10.dp, shape, ambientColor = base, spotColor = base) else Modifier)
            .clip(shape)
            .background(if (base != null) base.copy(alpha = a) else C.s1)
            .then(if (base == null) Modifier.border(1.dp, C.line, shape) else Modifier)
            .then(if (selected) Modifier.border(2.dp, C.ink, shape) else Modifier)
            .clickable(enabled = !future) { onClick() }
            .testTag("day_$date"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${date.dayOfMonth}", style = T.label.copy(fontSize = 13.sp, color = textColor, lineHeight = 15.sp), maxLines = 1)
            if (b != null) {
                Text(
                    (if (b >= 0) F.MINUS.toString() else "+") + abs(b),
                    style = T.small.copy(fontSize = 9.5.sp, lineHeight = 11.sp, color = textColor.copy(alpha = 0.85f)),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun MonthSummary(s: HomeState, month: YearMonth) {
    val inMonth = s.history.filter { YearMonth.from(it.date) == month }
    val withBalance = inMonth.mapNotNull { it.balance }
    val under = withBalance.count { it >= 0 }
    val trendIn = s.trend.filter { YearMonth.from(it.date) == month }
    val lost = if (trendIn.size >= 2) trendIn.first().trend - trendIn.last().trend else null
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Lbl(if (month == YearMonth.from(s.now.toLocalDate())) "Month so far" else "That month")
                if (withBalance.isEmpty()) {
                    Text("No logged days", style = T.small, modifier = Modifier.padding(top = 6.dp))
                } else {
                    Metric("$under", "of ${withBalance.size} days under", valueStyle = T.metric.glow(C.under, 24f))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (lost != null && lost < 0) "GAINED" else "LOST", style = T.caps)
                Text(
                    lost?.let { "${F.kg(abs(it))} kg" } ?: "—",
                    style = T.metric.copy(fontSize = 24.sp, lineHeight = 26.sp),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp).gradient(C.weightBrush),
                )
            }
        }
    }
}

// ---- One day -----------------------------------------------------------------------------------

/** One day's numbers. Sleep appears only here. */
@Composable
private fun DayDetail(s: HomeState, date: LocalDate, inCard: Boolean) {
    val d = s.history.firstOrNull { it.date == date }
    val trend = s.trend.firstOrNull { it.date == date }?.trend
    val content: @Composable () -> Unit = {
        Lbl(F.dayShort(date))
        val b = d?.balance
        if (b != null) {
            Metric(F.n(abs(b)), if (b >= 0) "kcal under" else "kcal over", valueStyle = T.metric.glow(if (b >= 0) C.under else C.over, 24f))
        } else {
            Metric("—", if (d?.eaten == null) "no food logged" else "no burn synced", valueStyle = T.metric.copy(color = C.mute))
        }
        if (d == null) {
            Text("No data synced for this day.", style = T.body, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            val total = d.burnedTotal
            val active = d.burnedActive
            FieldRow(
                "Burned",
                total?.let { F.n(it) } ?: "—",
                note = if (total != null && active != null) "(${F.n((total - active).coerceAtLeast(0))} + ${F.n(active)})" else null,
            )
            FieldRow("Eaten", d.eaten?.let { F.n(it) } ?: "—")
            d.proteinG?.let { FieldRow("Protein", "$it g", valueColor = C.mute) }
            FieldRow("Steps", d.steps?.let { F.n(it) } ?: "—", valueColor = C.steps)
            FieldRow(
                "Weigh-in",
                d.weightKg?.let { "${F.kg(it)} kg" } ?: "—",
                note = trend?.let { "trend ${F.kg(it)}" },
            )
            FieldRow(
                "Sleep",
                d.sleepMinutes?.let { F.sleep(it) } ?: "—",
                valueColor = C.sleep,
                divider = false,
                leading = Icons.Rounded.Hotel,
                leadingColor = C.sleep,
            )
        }
    }
    if (inCard) {
        PCard(Modifier.testTag("day_detail")) { content() }
    } else {
        Column(Modifier.fillMaxWidth().testTag("day_detail")) { content() }
    }
}
