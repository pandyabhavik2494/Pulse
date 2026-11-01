package com.pandyabhavik.pulse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandyabhavik.pulse.BuildConfig
import com.pandyabhavik.pulse.data.HealthStatus
import com.pandyabhavik.pulse.data.HomeState
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(s: HomeState) {
    val ui = LocalUi.current
    val p = s.profile ?: return
    val settings by ui.vm.repo.settings.collectAsStateWithLifecycle()

    // Profile draft: keyed on the saved profile so a save (or a sync) resets it to what's stored.
    var name by rememberSaveable(p) { mutableStateOf(p.name) }
    var goal by rememberSaveable(p) { mutableStateOf(fmtNum(p.goalKg)) }
    var start by rememberSaveable(p) { mutableStateOf(fmtNum(p.startKg)) }
    var height by rememberSaveable(p) { mutableStateOf(fmtNum(p.heightCm)) }
    var age by rememberSaveable(p) { mutableStateOf(p.age.toString()) }
    var male by rememberSaveable(p) { mutableStateOf(p.male) }
    var stepGoal by rememberSaveable(p) { mutableStateOf(p.stepGoal.toString()) }

    val goalKg = num(goal)
    val startKg = num(start)
    val heightCm = num(height)
    val ageN = age.toIntOrNull()
    val steps = stepGoal.toIntOrNull()
    val valid = name.isNotBlank() && goalKg != null && startKg != null && goalKg in 35.0..350.0 && startKg in 35.0..350.0 &&
        goalKg < startKg && heightCm != null && heightCm in 120.0..230.0 && ageN != null && ageN in 14..100 &&
        steps != null && steps in 1000..50_000
    val draft = if (valid) p.copy(
        name = name.trim(),
        goalKg = goalKg!!,
        startKg = startKg!!,
        heightCm = heightCm!!,
        age = ageN!!,
        male = male,
        stepGoal = steps!!,
    ) else null
    val changed = draft != null && draft != p

    Column(
        Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!ui.wide) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(C.s2)
                        .clickable { ui.closeSettings() }
                        .testTag("settings_back"),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = C.ink, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text("Pulse", style = T.small.copy(fontSize = 13.sp))
                Text("Settings", style = T.title)
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Profile
            PCard(background = Brush.linearGradient(listOf(C.wt.copy(alpha = 0.12f), Color.Transparent))) {
                Lbl("You and your goal", Icons.Rounded.MonitorWeight, C.wt)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PField(name, { name = it.take(30) }, "Name", "settings_name", KeyboardType.Text)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PField(start, { start = digits(it, 5) }, "Start (kg)", "settings_start", modifier = Modifier.weight(1f))
                        PField(goal, { goal = digits(it, 5) }, "Goal (kg)", "settings_goal", modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PField(height, { height = digits(it, 5) }, "Height (cm)", "settings_height", modifier = Modifier.weight(1f))
                        PField(age, { age = it.filter(Char::isDigit).take(3) }, "Age", "settings_age", KeyboardType.Number, Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sex (for resting burn)", style = T.body.copy(color = C.ink), modifier = Modifier.weight(1f))
                        Segmented(listOf("Male", "Female"), if (male) 0 else 1) { male = it == 0 }
                    }
                    PField(stepGoal, { stepGoal = it.filter(Char::isDigit).take(5) }, "Daily step goal", "settings_steps", KeyboardType.Number)
                    Text(
                        "Started ${F.dateWithYear(p.startDate)}. The step goal only fills the steps bar; it doesn't change the calorie maths.",
                        style = T.small,
                    )
                    PButton(
                        if (changed) "Save changes" else "Saved",
                        Modifier.testTag("settings_save"),
                        enabled = changed,
                        brush = C.weightBrush,
                        textColor = Color(0xFF12061A),
                        height = 46.dp,
                    ) { draft?.let { ui.vm.saveProfile(it) } }
                }
            }

            // ---- Nudges
            PCard {
                Lbl("Nudges", Icons.Rounded.Notifications, C.under)
                Spacer(Modifier.height(4.dp))
                ToggleRow("Morning, after your weigh-in syncs", settings.morningNudge, "settings_nudge_morning") {
                    ui.vm.setNudges(it, settings.eveningNudge)
                }
                ToggleRow("Evening at 8 PM, with what's left", settings.eveningNudge, "settings_nudge_evening") {
                    ui.vm.setNudges(settings.morningNudge, it)
                }
                Text("Nothing else, ever.", style = T.small, modifier = Modifier.padding(top = 4.dp))
                TextLink("Notification permission") { ui.askNotifications() }
            }

            // ---- Health Connect
            PCard {
                Lbl("Health Connect", Icons.Rounded.Favorite, C.over)
                Spacer(Modifier.height(6.dp))
                val (statusText, ok) = when {
                    s.health == HealthStatus.NOT_INSTALLED -> "Not installed" to false
                    s.health == HealthStatus.NEEDS_UPDATE -> "Needs an update" to false
                    s.missingPermissions.isEmpty() -> "Connected · all access allowed" to true
                    else -> "${s.missingPermissions.size} permission${if (s.missingPermissions.size == 1) "" else "s"} not allowed" to false
                }
                FieldRow("Status", statusText, valueColor = if (ok) C.under else C.food)
                FieldRow("Last sync", F.syncAgo(s.lastSync, s.now).removePrefix("Fitbit · "), divider = false)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PButton(
                        if (ok) "Permissions" else "Allow access",
                        Modifier.weight(1f).testTag("settings_health"),
                        height = 44.dp,
                        icon = Icons.Rounded.Favorite,
                    ) { ui.askHealth() }
                    PButton(
                        "Sync now",
                        Modifier.weight(1f).testTag("settings_sync"),
                        brush = Brush.linearGradient(listOf(C.s2, C.s2)),
                        textColor = C.ink,
                        height = 44.dp,
                        icon = Icons.Rounded.Sync,
                    ) { ui.vm.sync() }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, tint = C.steps, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Live steps use the phone's step sensor between Fitbit syncs.", style = T.small, modifier = Modifier.weight(1f))
                }
                TextLink("Physical activity permission") { ui.askActivity() }
            }

            // ---- Privacy
            PCard {
                Lbl("Privacy", Icons.Rounded.Lock, C.steps)
                Text(
                    "Pulse reads steps, calories, food, weight and sleep from Health Connect and keeps them on this phone. No account, no cloud, no ads, nothing shared.",
                    style = T.body,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Text(
                "Pulse ${BuildConfig.VERSION_NAME} · ring style ${s.ringStyle.label}",
                style = T.small.copy(color = C.dim),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            s.trendKg?.let {
                Text(
                    "Trend ${F.kg(it)} kg · ${((p.startKg - it) * 10).roundToInt() / 10.0} kg down so far",
                    style = T.small.copy(color = C.dim),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = T.body.copy(color = C.ink), modifier = Modifier.weight(1f).padding(end = 16.dp))
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked = on,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = C.under,
                    checkedBorderColor = C.under,
                    checkedIconColor = C.under,
                    uncheckedThumbColor = Color(0xFFB8BDCB),
                    uncheckedTrackColor = C.s3,
                    uncheckedBorderColor = C.mute,
                    uncheckedIconColor = C.s3,
                ),
                modifier = Modifier.testTag(tag),
            )
        }
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = T.label.copy(color = C.steps, fontSize = 13.sp),
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
    )
}
