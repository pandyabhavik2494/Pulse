package com.pandyabhavik.pulse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandyabhavik.pulse.core.DayLog
import com.pandyabhavik.pulse.data.HomeState
import com.pandyabhavik.pulse.data.Settings
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** What a dashboard tile shows: the number and the line under it. */
data class DashValue(val value: String, val unit: String, val color: Color? = null)

/** One number the dashboard can show. [read] gives null when there's no data for it. */
class DashTile(val id: String, val label: String, val group: String, val color: Color, val read: (HomeState) -> DashValue?)

private fun d1(v: Double) = String.format(Locale.US, "%.1f", v)

/** Newest value of [pick] in the last week, with "today" or the date it's from. */
private fun <T> recent(s: HomeState, pick: (DayLog) -> T?): Pair<T, String>? {
    val today = s.now.toLocalDate()
    return s.history.take(7).firstNotNullOfOrNull { d -> pick(d)?.let { it to d.date } }
        ?.let { (v, date: LocalDate) -> v to (if (date == today) "" else " · ${F.date(date, today)}") }
}

private fun hm(min: Int) = "${min / 60}h ${min % 60}m"

/** Every number Pulse can put on the dashboard, grouped like the sections below it. */
val DASH_TILES: List<DashTile> = listOf(
    DashTile("calories", "Calories", "Calories", C.under) { s ->
        val b = s.balance ?: return@DashTile null
        DashValue(F.n(abs(b)), if (b < 0) "kcal over" else "kcal under", if (b < 0) C.over else C.under)
    },
    DashTile("burned", "Burned", "Calories", C.food) { s -> s.burned?.let { DashValue(F.n(it), "kcal so far") } },
    DashTile("eaten", "Eaten", "Calories", C.food) { s -> s.eaten?.let { DashValue(F.n(it), "kcal logged") } },
    DashTile("active", "Active", "Calories", C.hot) { s -> s.active?.let { DashValue(F.n(it), "active kcal") } },
    DashTile("avg7", "7-day balance", "Calories", C.under) { s ->
        val a = s.avg7 ?: return@DashTile null
        DashValue(F.n(abs(a)), if (a < 0) "kcal/day over" else "kcal/day under", if (a < 0) C.over else C.under)
    },
    DashTile("protein", "Protein", "Calories", C.food) { s -> s.today.proteinG?.let { DashValue("$it", "g protein") } },
    DashTile("carbs", "Carbs", "Calories", C.food) { s -> s.today.carbsG?.let { DashValue("$it", "g carbs") } },
    DashTile("fat", "Fat", "Calories", C.food) { s -> s.today.fatG?.let { DashValue("$it", "g fat") } },
    DashTile("water", "Water", "Calories", C.steps) { s -> s.today.waterMl?.let { DashValue(d1(it / 1000.0), "litres") } },
    DashTile("streak", "Streak", "Calories", C.under) { s -> DashValue("${s.streak}", if (s.streak == 1) "day under" else "days under") },

    DashTile("steps", "Steps", "Activity", C.steps) { s -> DashValue(F.n(s.liveSteps), "of ${F.n(s.profile?.stepGoal ?: 10_000)}") },
    DashTile("distance", "Distance", "Activity", C.steps) { s ->
        s.today.distanceM?.let { DashValue(String.format(Locale.US, "%.2f", it / 1609.344), "mi · ${d1(it / 1000.0)} km") }
    },
    DashTile("floors", "Floors", "Activity", C.steps) { s -> s.today.floors?.let { DashValue("$it", "floors climbed") } },
    DashTile("exercise", "Exercise", "Activity", C.steps) { s -> s.today.exerciseMin?.let { DashValue("$it", "min exercise") } },

    DashTile("weight", "Weight", "Weight", C.wt) { s -> s.trendKg?.let { DashValue(F.kg(it), "kg trend") } },
    DashTile("lost", "Lost", "Weight", C.wt) { s ->
        val p = s.profile ?: return@DashTile null
        val kg = s.trendKg ?: return@DashTile null
        val lost = p.startKg - kg
        DashValue(F.kg(abs(lost)), if (lost >= 0) "kg down" else "kg up", if (lost >= 0) C.under else C.over)
    },
    DashTile("to_goal", "To goal", "Weight", C.wt) { s ->
        val p = s.profile ?: return@DashTile null
        val kg = s.trendKg ?: return@DashTile null
        DashValue(F.kg((kg - p.goalKg).coerceAtLeast(0.0)), "kg to ${F.kgShort(p.goalKg)}")
    },
    DashTile("goal_date", "Goal date", "Weight", C.wt2) { s ->
        s.forecast?.goal?.projected?.let { DashValue(F.date(it, s.now.toLocalDate()), "at this pace") }
    },
    DashTile("bmi", "BMI", "Weight", C.wt) { s ->
        val p = s.profile ?: return@DashTile null
        val kg = s.trendKg ?: return@DashTile null
        val m = p.heightCm / 100.0
        if (m <= 0) null else DashValue(d1(kg / (m * m)), "BMI")
    },
    DashTile("body_fat", "Body fat", "Weight", C.wt) { s -> recent(s) { it.bodyFatPct }?.let { (v, w) -> DashValue(d1(v), "%$w") } },
    DashTile("lean", "Lean mass", "Weight", C.wt) { s -> recent(s) { it.leanKg }?.let { (v, w) -> DashValue(d1(v), "kg lean$w") } },

    DashTile("resting_hr", "Heart", "Heart", C.over) { s ->
        recent(s) { it.restingHr }?.let { (v, w) -> DashValue("$v", "bpm resting$w") }
            ?: s.today.hrAvg?.let { DashValue("$it", "bpm average") }
    },
    DashTile("avg_hr", "Avg heart", "Heart", C.over) { s -> s.today.hrAvg?.let { DashValue("$it", "bpm average") } },
    DashTile("hrv", "HRV", "Heart", C.over) { s -> recent(s) { it.hrvMs }?.let { (v, w) -> DashValue("$v", "ms$w") } },
    DashTile("spo2", "Blood oxygen", "Heart", C.over) { s -> recent(s) { it.spo2Pct }?.let { (v, w) -> DashValue("${v.roundToInt()}", "%$w") } },
    DashTile("breathing", "Breathing", "Heart", C.over) { s -> recent(s) { it.breathingRate }?.let { (v, w) -> DashValue(d1(v), "breaths/min$w") } },
    DashTile("vo2", "VO2 max", "Heart", C.over) { s -> recent(s) { it.vo2Max }?.let { (v, w) -> DashValue(d1(v), "ml/kg/min$w") } },

    DashTile("sleep", "Sleep", "Sleep", C.sleep) { s -> s.today.sleepMinutes?.let { DashValue(hm(it), "last night") } },
    DashTile("deep_sleep", "Deep sleep", "Sleep", C.sleep) { s -> s.today.sleepDeepMin?.let { DashValue(hm(it), "deep") } },
    DashTile("rem_sleep", "REM sleep", "Sleep", C.sleep) { s -> s.today.sleepRemMin?.let { DashValue(hm(it), "REM") } },
    DashTile("bedtime", "Bedtime", "Sleep", C.sleep) { s -> s.today.bedTime?.let { DashValue(it, "wake ${s.today.wakeTime ?: "—"}") } },
)

private val BY_ID = DASH_TILES.associateBy { it.id }

/** One number on the dashboard: small label, the value, its unit. */
@Composable
private fun DashCell(tile: DashTile, s: HomeState, modifier: Modifier = Modifier) {
    val v = tile.read(s)
    Column(
        modifier
            .testTag("dash_${tile.id}")
            .clip(RoundedCornerShape(16.dp))
            .background(C.s2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(tile.label.uppercase(), style = T.caps, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            v?.value ?: "—",
            style = T.metric.copy(fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = 0.sp, color = if (v == null) C.mute else v.color ?: tile.color),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(v?.unit ?: "no data", style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The overview at the top of Today: the numbers Bhavik picked, two to a row. The tune button changes them. */
@Composable
fun Dashboard(s: HomeState) {
    val ui = LocalUi.current
    val settings by ui.vm.repo.settings.collectAsStateWithLifecycle()
    val tiles = settings.dashboard.mapNotNull { BY_ID[it] }.ifEmpty { Settings.DEFAULT_DASHBOARD.mapNotNull { BY_ID[it] } }
    var editing by rememberSaveable { mutableStateOf(false) }
    PCard(Modifier.testTag("dashboard"), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
            Text("OVERVIEW", style = T.caps, modifier = Modifier.weight(1f))
            Row(
                Modifier
                    .testTag("dashboard_edit")
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { editing = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Tune, null, tint = C.mute, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Customize", style = T.small.copy(fontSize = 13.sp, color = C.soft))
            }
        }
        tiles.chunked(2).forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { DashCell(it, s, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    if (editing) {
        DashboardSheet(s, settings.dashboard, onSave = { ui.vm.setDashboard(it) }) { editing = false }
    }
}

/** Pick the dashboard's numbers: tap to add or remove; they show in the order picked. */
@Composable
private fun DashboardSheet(s: HomeState, current: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val picked = remember { mutableStateListOf<String>().apply { addAll(current.filter { it in BY_ID }) } }
    PulseSheet("dashboard_sheet", onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Customize overview", style = T.h3.copy(fontSize = 20.sp))
            Text(
                "Tap numbers to add or remove them. They show in the order you pick them.",
                style = T.small.copy(fontSize = 13.sp),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            val maxList = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
            Column(Modifier.heightIn(max = maxList).verticalScroll(rememberScrollState())) {
                DASH_TILES.groupBy { it.group }.forEach { (group, list) ->
                    Text(group.uppercase(), style = T.caps, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    list.forEach { t ->
                        val pos = picked.indexOf(t.id)
                        val on = pos >= 0
                        val v = t.read(s)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) t.color.copy(alpha = 0.12f) else C.s2)
                                .border(1.dp, if (on) t.color.copy(alpha = 0.6f) else C.line, RoundedCornerShape(14.dp))
                                .clickable { if (on) picked.remove(t.id) else picked.add(t.id) }
                                .semantics { selected = on; role = Role.Checkbox }
                                .testTag("dash_opt_${t.id}")
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(if (on) t.color else C.s3),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) Text("${pos + 1}", style = T.small.copy(fontSize = 13.sp, color = Color(0xFF05070A)))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(t.label, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(
                                v?.let { "${it.value} ${it.unit}" } ?: "no data yet",
                                style = T.small.copy(fontSize = 13.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PButton(
                    "Reset",
                    Modifier.weight(1f).testTag("dashboard_reset"),
                    brush = androidx.compose.ui.graphics.SolidColor(C.s3),
                    textColor = C.ink,
                ) {
                    picked.clear(); picked.addAll(Settings.DEFAULT_DASHBOARD)
                }
                PButton(
                    if (picked.isEmpty()) "Pick one" else "Save",
                    Modifier.weight(1f).testTag("dashboard_save"),
                    enabled = picked.isNotEmpty(),
                    icon = Icons.Rounded.Check,
                ) {
                    onSave(picked.toList()); onDismiss()
                }
            }
        }
    }
}
