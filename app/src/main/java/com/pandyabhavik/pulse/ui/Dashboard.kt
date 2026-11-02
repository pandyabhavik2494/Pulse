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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn as hIn
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
import com.pandyabhavik.pulse.core.Mood
import com.pandyabhavik.pulse.core.Moods
import com.pandyabhavik.pulse.core.Status
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
        DashValue(F.n(abs(b)), F.balanceWords(b < 0))
    },
    DashTile("left_to_eat", "Left to eat", "Calories", C.under) { s ->
        val p = s.profile ?: return@DashTile null
        val b = com.pandyabhavik.pulse.core.Budget.today(p, s.history, s.now.toLocalDate(), s.trendKg)
        if (b.left < 0) DashValue(F.n(-b.left), "kcal above plan") else DashValue(F.n(b.left), "kcal left to eat")
    },
    DashTile("burned", "Burned", "Calories", C.food) { s -> s.burned?.let { DashValue(F.n(it), "kcal so far") } },
    DashTile("eaten", "Eaten", "Calories", C.food) { s -> s.eaten?.let { DashValue(F.n(it), "kcal logged") } },
    DashTile("active", "Active", "Calories", C.hot) { s -> s.active?.let { DashValue(F.n(it), "active kcal") } },
    DashTile("avg7", "7-day balance", "Calories", C.under) { s ->
        val a = s.avg7 ?: return@DashTile null
        DashValue(F.n(abs(a)), if (a < 0) "a day more eaten" else "a day more burned")
    },
    DashTile("protein", "Protein", "Calories", C.food) { s -> s.today.proteinG?.let { DashValue("$it", "g protein") } },
    DashTile("carbs", "Carbs", "Calories", C.food) { s -> s.today.carbsG?.let { DashValue("$it", "g carbs") } },
    DashTile("fat", "Fat", "Calories", C.food) { s -> s.today.fatG?.let { DashValue("$it", "g fat") } },
    DashTile("water", "Water", "Calories", C.steps) { s -> s.today.waterMl?.let { DashValue(d1(it / 1000.0), "litres") } },
    DashTile("streak", "Streak", "Calories", C.under) { s -> DashValue("${s.streak}", if (s.streak == 1) "day burned more" else "days burned more") },

    DashTile("steps", "Steps", "Activity", C.steps) { s -> DashValue(F.n(s.liveSteps), "of ${F.n(s.profile?.stepGoal ?: 10_000)}") },
    DashTile("distance", "Distance", "Activity", C.steps) { s ->
        s.today.distanceM?.let { DashValue(String.format(Locale.US, "%.2f", it / 1609.344), "mi · ${d1(it / 1000.0)} km") }
    },
    DashTile("floors", "Floors", "Activity", C.steps) { s -> s.today.floors?.let { DashValue("$it", "floors climbed") } },
    DashTile("exercise", "Exercise", "Activity", C.steps) { s -> s.today.exerciseMin?.let { DashValue("$it", "min exercise") } },

    DashTile("weight", "Weight", "Weight", C.wt) { s ->
        val today = s.now.toLocalDate()
        s.history.firstOrNull { it.weightKg != null }?.let { d ->
            DashValue(F.kg(d.weightKg!!), "kg ${if (d.date == today) "today" else F.date(d.date, today)}")
        }
    },
    DashTile("weight_trend", "Weight trend", "Weight", C.wt) { s -> s.trendKg?.let { DashValue(F.kg(it), "kg trend") } },
    DashTile("lost", "Lost", "Weight", C.wt) { s ->
        val p = s.profile ?: return@DashTile null
        val kg = s.trendKg ?: return@DashTile null
        val lost = p.startKg - kg
        DashValue(F.kg(abs(lost)), if (lost >= 0) "kg down" else "kg up")
    },
    DashTile("to_goal", "To goal", "Weight", C.wt) { s ->
        val p = s.profile ?: return@DashTile null
        val kg = s.trendKg ?: return@DashTile null
        DashValue(F.kg((kg - p.goalKg).coerceAtLeast(0.0)), "kg to ${F.kgShort(p.goalKg)}")
    },
    DashTile("in_2_weeks", "In 2 weeks", "Weight", C.wt2) { s ->
        val p = s.profile ?: return@DashTile null
        val o = com.pandyabhavik.pulse.core.TwoWeeks.outlook(p, s.history, s.now.toLocalDate())
        DashValue(F.kg(o.toKg), "kg · ${F.signedKg(o.changeKg)}${if (o.early) " · early" else ""}")
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
        s.today.hrLatest?.let { DashValue("$it", "bpm at ${s.today.hrLatestAt ?: "—"}") }
            ?: recent(s) { it.restingHr }?.let { (v, w) -> DashValue("$v", "bpm resting$w") }
            ?: s.today.hrAvg?.let { DashValue("$it", "bpm average") }
    },
    DashTile("resting_only", "Resting heart", "Heart", C.over) { s -> recent(s) { it.restingHr }?.let { (v, w) -> DashValue("$v", "bpm resting$w") } },
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

/** Which Today section a tile opens: "calories", "activity", "body", "heart" or "sleep". */
fun sectionOf(tile: DashTile): String = when (tile.group) {
    "Calories" -> "calories"
    "Activity" -> "activity"
    "Weight" -> "body"
    "Heart" -> "heart"
    else -> "sleep"
}

private fun restingNow(s: HomeState): Int? = s.history.take(7).firstNotNullOfOrNull { it.restingHr }
private fun restingUsual(s: HomeState): List<Int> = s.history.mapNotNull { it.restingHr }.drop(1).take(30)

/**
 * The calm status of a tile (core/Mood.kt holds the rules), or null for numbers with no good/steady/grow
 * reading: those show plain.
 */
fun statusOf(id: String, s: HomeState): Status? {
    val p = s.profile
    val t = s.now.toLocalTime()
    val budget = p?.let { com.pandyabhavik.pulse.core.Budget.today(it, s.history, s.now.toLocalDate(), s.trendKg) }
    return when (id) {
        "steps" -> Moods.steps(s.liveSteps, p?.stepGoal ?: 10_000, t)
        "distance" -> if (s.today.distanceM == null) null else Moods.steps(s.liveSteps, p?.stepGoal ?: 10_000, t).let {
            it.copy(why = if (it.mood == Mood.WAIT) it.why else "Moves with your steps", bar = null)
        }
        "burned" -> budget?.let { Moods.burned(s.burned, it.burn, t) }
        "eaten" -> budget?.let { Moods.eaten(s.eaten, it.budget) }
        "calories" -> Moods.balance(s.balance)
        "left_to_eat" -> budget?.let { Moods.left(it.left, it.budget) }
        "weight" -> Moods.weight(s.history.firstOrNull { it.weightKg != null }?.weightKg, s.trendKg)
        "weight_trend" -> if (s.trendKg == null) null else Moods.trend(s.trend, s.now.toLocalDate())
        "resting_only" -> Moods.resting(restingNow(s), restingUsual(s))
        "resting_hr" -> if (s.today.hrLatest != null) Status(Mood.WAIT, "Latest", "Moves with what you’re doing")
        else Moods.resting(restingNow(s), restingUsual(s))
        "sleep" -> Moods.sleep(s.today.sleepMinutes)
        "deep_sleep" -> Moods.deep(s.today.sleepDeepMin)
        "streak" -> if (s.streak > 0) Status(Mood.GOOD, Moods.ON_TRACK, "Keep it going", win = "${s.streak} days in a row of burning more than you eat.") else null
        else -> null
    }
}

fun moodColor(m: Mood): Color = when (m) {
    Mood.GOOD -> C.under
    Mood.STEADY -> C.steady
    Mood.GROW -> C.grow
    Mood.WAIT -> C.mute
}

/** Dashboard groups, in order, and the tile groups each holds. */
private val GROUPS = listOf(
    "Food" to listOf("Calories"),
    "Move" to listOf("Activity"),
    "Body" to listOf("Weight"),
    "Recovery" to listOf("Heart", "Sleep"),
)

/** One number on the dashboard: label, the value (green only when it's on track), unit, a calm status and one kind line. Tap opens its section. */
@Composable
private fun DashCell(tile: DashTile, s: HomeState, st: Status?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val v = tile.read(s)
    val color = st?.let { moodColor(it.mood) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .testTag("dash_${tile.id}")
            .clip(shape)
            .background(C.s2)
            .border(1.dp, if (selected) (color ?: C.wt) else Color.Transparent, shape)
            .clickable(role = Role.Button, onClickLabel = "Open ${tile.label}") { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(tile.label.uppercase(), style = T.caps, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            v?.value ?: "—",
            style = T.metric.copy(
                fontSize = 28.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
                color = when {
                    v == null -> C.mute
                    st?.mood == Mood.GOOD -> C.under
                    else -> C.ink
                },
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 6.dp).testTag("dash_value_${tile.id}"),
        )
        Text(v?.unit ?: "no data yet", style = T.small.copy(fontSize = 13.sp, color = C.soft), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f).hIn(min = 8.dp))
        if (st != null && color != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("dash_status_${tile.id}")) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(st.word, style = T.small.copy(fontSize = 11.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(800), color = color), maxLines = 1)
            }
            st.bar?.let { f ->
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(C.s3)) {
                    Box(Modifier.fillMaxWidth(f.coerceIn(0.02f, 1f)).height(4.dp).clip(CircleShape).background(color))
                }
            }
            Text(st.why, style = T.small.copy(fontSize = 11.5.sp, lineHeight = 15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** The one-line verdict on top: a kind headline, a win, one small next step, and the counts. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Verdict(s: HomeState, shown: List<Status>) {
    if (shown.isEmpty()) return
    val moods = shown.map { it.mood }
    val judged = moods.filter { it != Mood.WAIT }
    val head = if (judged.isEmpty()) "Your day is filling in" else Moods.headline(judged, s.now.toLocalTime())
    val headColor = if (moods.any { it == Mood.GROW }) C.steady else C.under
    val win = shown.firstOrNull { it.mood == Mood.GOOD && it.win != null }?.win
    val tip = shown.firstOrNull { it.mood == Mood.GROW && it.tip != null }?.tip
        ?: shown.firstOrNull { it.mood == Mood.STEADY && it.tip != null }?.tip
    val line = listOfNotNull(win, tip?.let { "One small thing: " + it.replaceFirstChar { c -> c.lowercaseChar() } })
        .joinToString(" ")
        .ifEmpty { "Your numbers fill in as the day goes." }
    PCard(
        Modifier.testTag("verdict"),
        background = androidx.compose.ui.graphics.Brush.linearGradient(listOf(headColor.copy(alpha = 0.10f), Color.Transparent)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowDot(headColor.copy(alpha = 0.6f), 22.dp)
            Box(Modifier.offset(x = (-16).dp).size(10.dp).clip(CircleShape).background(headColor))
            Text(head, style = T.h3.copy(fontSize = 20.sp, color = headColor), maxLines = 1, modifier = Modifier.offset(x = (-8).dp).testTag("verdict_headline"))
        }
        Text(line, style = T.body.copy(fontSize = 14.sp, lineHeight = 20.sp), modifier = Modifier.padding(top = 8.dp).testTag("verdict_line"))
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                Mood.GOOD to "on track",
                Mood.STEADY to "steady",
                Mood.GROW to "room to grow",
                Mood.WAIT to "filling in",
            ).forEach { (m, word) ->
                val c = moods.count { it == m }
                if (c > 0) {
                    Row(
                        Modifier.clip(CircleShape).background(C.s2).padding(horizontal = 10.dp, vertical = 6.dp).testTag("verdict_count_${m.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(moodColor(m)))
                        Spacer(Modifier.width(6.dp))
                        Text("$c $word", style = T.small.copy(color = C.soft))
                    }
                }
            }
        }
    }
}

/**
 * Today's overview: the verdict, then the numbers Bhavik picked, grouped Food / Move / Body / Recovery, two
 * to a row. Each tile has a calm status; tapping one opens its section ([onOpen] gets the section key).
 */
@Composable
fun Dashboard(s: HomeState, onOpen: (String) -> Unit = {}, selected: String? = null) {
    val ui = LocalUi.current
    val settings by ui.vm.repo.settings.collectAsStateWithLifecycle()
    val tiles = settings.dashboard.mapNotNull { BY_ID[it] }.ifEmpty { Settings.DEFAULT_DASHBOARD.mapNotNull { BY_ID[it] } }
    val statuses = tiles.associate { it.id to statusOf(it.id, s) }
    var editing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("dashboard"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Verdict(s, tiles.mapNotNull { statuses[it.id] })
        var first = true
        GROUPS.forEach { (name, groups) ->
            val list = tiles.filter { it.group in groups }
            if (list.isEmpty()) return@forEach
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) {
                Text(name.uppercase(), style = T.caps.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(800)), modifier = Modifier.weight(1f))
                if (first) {
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
            }
            first = false
            list.chunked(2).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { t ->
                        DashCell(t, s, statuses[t.id], selected == sectionOf(t), Modifier.weight(1f).fillMaxHeight()) { onOpen(sectionOf(t)) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(start = 4.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(Mood.GOOD to "On track", Mood.STEADY to "Steady", Mood.GROW to "Room to grow", Mood.WAIT to "Filling in").forEach { (m, w) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(moodColor(m)))
                    Spacer(Modifier.width(5.dp))
                    Text(w, style = T.small.copy(fontSize = 11.sp))
                }
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
