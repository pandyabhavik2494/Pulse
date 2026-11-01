package com.pandyabhavik.pulse.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pandyabhavik.pulse.core.Profile
import com.pandyabhavik.pulse.data.HealthStatus
import com.pandyabhavik.pulse.data.HomeState
import java.util.Locale
import kotlin.math.roundToInt

/** Two steps, once: who you are, then connect Health Connect, live steps and nudges. */
@Composable
fun Onboarding(
    vm: MainViewModel,
    s: HomeState,
    askHealth: () -> Unit,
    askActivity: () -> Unit,
    askNotifications: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("Bhavik") }
    var metric by rememberSaveable { mutableStateOf(true) }
    var cm by rememberSaveable { mutableStateOf("") }
    var ft by rememberSaveable { mutableStateOf("") }
    var inch by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var male by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var start by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }

    val heightCm = if (metric) num(cm) else {
        val f = num(ft)
        val i = num(inch) ?: 0.0
        f?.let { it * 30.48 + i * 2.54 }
    }
    val ageN = age.trim().toIntOrNull()
    val startKg = num(start)
    val goalKg = num(goal)
    val valid = name.isNotBlank() && heightCm != null && heightCm in 120.0..230.0 &&
        ageN != null && ageN in 14..100 && male != null &&
        startKg != null && startKg in 35.0..350.0 && goalKg != null && goalKg in 35.0..350.0 && goalKg < startKg

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(C.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .testTag("onboarding_screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        val minH = maxHeight
        AnimatedContent(
            step,
            transitionSpec = { (fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 6 }) togetherWith fadeOut(tween(200)) },
            label = "onboarding",
        ) { page ->
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minH)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (page == 0) {
                    Text("STEP 1 OF 2", style = T.caps.copy(letterSpacing = 1.6.sp))
                    Text("Hi! Let's set up your journey", style = T.big.copy(fontSize = 30.sp, lineHeight = 33.sp))
                    Text("Pulse uses these to estimate your resting burn and to date your milestones.", style = T.body)
                    Spacer(Modifier.height(2.dp))
                    PField(name, { name = it.take(30) }, "Your name", "onboarding_name", KeyboardType.Text)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Height", style = T.label, modifier = Modifier.weight(1f))
                        Segmented(listOf("cm", "ft · in"), if (metric) 0 else 1) { metric = it == 0 }
                    }
                    if (metric) {
                        PField(cm, { cm = digits(it, 5) }, "Height (cm)", "onboarding_height")
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PField(ft, { ft = digits(it, 1) }, "Feet", "onboarding_height", modifier = Modifier.weight(1f))
                            PField(inch, { inch = digits(it, 4) }, "Inches", "onboarding_height_in", modifier = Modifier.weight(1f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PField(age, { age = it.filter(Char::isDigit).take(3) }, "Age", "onboarding_age", KeyboardType.Number, Modifier.weight(1f))
                        Segmented(
                            listOf("Male", "Female"),
                            if (male == null) -1 else if (male == true) 0 else 1,
                            tags = listOf("onboarding_male", "onboarding_female"),
                        ) { male = it == 0 }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PField(start, { start = digits(it, 5) }, "Start weight (kg)", "onboarding_weight", modifier = Modifier.weight(1f))
                        PField(goal, { goal = digits(it, 5) }, "Goal weight (kg)", "onboarding_goal", modifier = Modifier.weight(1f))
                    }
                    if (startKg != null && goalKg != null && goalKg < startKg) {
                        PCard(background = androidx.compose.ui.graphics.Brush.linearGradient(listOf(C.wt.copy(alpha = 0.14f), Color.Transparent))) {
                            Text(
                                buildAnnotatedString {
                                    append("${F.kgShort(startKg)} kg → ")
                                    withStyle(SpanStyle(color = C.wt2, fontWeight = FontWeight(800))) { append("${F.kgShort(goalKg)} kg") }
                                    append(" · ${F.kgShort(startKg - goalKg)} kg to go, a win every kilo")
                                },
                                style = T.label.copy(fontWeight = FontWeight(600)),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    PButton("Next", Modifier.testTag("onboarding_next"), enabled = valid) { step = 1 }
                } else {
                    ConnectStep(
                        s = s,
                        summary = summary(metric, heightCm, ft, inch, ageN, startKg, goalKg),
                        askHealth = askHealth,
                        askActivity = askActivity,
                        askNotifications = askNotifications,
                        back = { step = 0 },
                    ) {
                        if (heightCm != null && ageN != null && male != null && startKg != null && goalKg != null) {
                            vm.saveProfile(
                                Profile(
                                    name = name.trim(),
                                    heightCm = (heightCm * 10).roundToInt() / 10.0,
                                    age = ageN,
                                    male = male == true,
                                    startKg = startKg,
                                    goalKg = goalKg,
                                    startDate = s.now.toLocalDate(),
                                ),
                            )
                        } else {
                            step = 0
                        }
                    }
                }
            }
        }
    }
}

private fun summary(metric: Boolean, cm: Double?, ft: String, inch: String, age: Int?, start: Double?, goal: Double?): String {
    val h = if (metric) cm?.let { "${it.roundToInt()} cm" } else "${ft.ifBlank { "?" }} ft ${inch.ifBlank { "0" }} in"
    return listOfNotNull(h, age?.toString(), start?.let { "${F.kgShort(it)} kg" }).joinToString(" · ") +
        (goal?.let { " → ${F.kgShort(it)} kg" } ?: "")
}

@Composable
private fun ColumnScope.ConnectStep(
    s: HomeState,
    summary: String,
    askHealth: () -> Unit,
    askActivity: () -> Unit,
    askNotifications: () -> Unit,
    back: () -> Unit,
    startJourney: () -> Unit,
) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val activityOk = remember(tick) { hasPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) }
    val notifyOk = remember(tick) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val hcOk = s.health == HealthStatus.AVAILABLE
    fun allowed(vararg kinds: String) = hcOk && s.missingPermissions.none { p -> kinds.any { p.endsWith(it) } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(C.s2)
                .clickable { back() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = C.mute, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(10.dp))
        Text("STEP 2 OF 2", style = T.caps.copy(letterSpacing = 1.6.sp))
    }
    Text("Connect your data", style = T.big.copy(fontSize = 30.sp, lineHeight = 33.sp))
    Text("Pulse reads from Health Connect. Nothing leaves your phone.", style = T.body)
    Column {
        PermRow("Steps, distance, floors, exercise · Fitbit", allowed("READ_STEPS", "READ_DISTANCE", "READ_FLOORS_CLIMBED", "READ_EXERCISE"))
        PermRow("Calories burned · Fitbit", allowed("READ_TOTAL_CALORIES_BURNED", "READ_ACTIVE_CALORIES_BURNED"))
        PermRow("Food and water · MyFitnessPal", allowed("READ_NUTRITION", "READ_HYDRATION"))
        PermRow("Weight and body fat · scale", allowed("READ_WEIGHT", "READ_BODY_FAT", "READ_LEAN_BODY_MASS"))
        PermRow("Sleep · Fitbit", allowed("READ_SLEEP"))
        PermRow("Heart rate and vitals · Fitbit", allowed("READ_HEART_RATE", "READ_RESTING_HEART_RATE", "READ_HEART_RATE_VARIABILITY", "READ_OXYGEN_SATURATION", "READ_RESPIRATORY_RATE", "READ_VO2_MAX"))
        PermRow("Read in background", allowed("READ_HEALTH_DATA_IN_BACKGROUND"), divider = false)
    }
    val allHealth = hcOk && s.missingPermissions.isEmpty()
    PButton(
        when {
            s.health == HealthStatus.NOT_INSTALLED -> "Get Health Connect"
            s.health == HealthStatus.NEEDS_UPDATE -> "Update Health Connect"
            allHealth -> "Health Connect connected"
            else -> "Allow Health Connect"
        },
        Modifier.testTag("onboarding_connect"),
        brush = if (allHealth) androidx.compose.ui.graphics.Brush.linearGradient(listOf(C.s2, C.s2)) else C.goBrush,
        textColor = if (allHealth) C.under else Color(0xFF04110B),
        icon = Icons.Rounded.Favorite,
        height = 48.dp,
    ) { askHealth() }
    Spacer(Modifier.height(2.dp))
    ExtraRow(
        Icons.AutoMirrored.Rounded.DirectionsWalk, C.steps, "Live steps",
        "Physical activity lets the step count tick live between Fitbit syncs.", activityOk, "onboarding_activity",
    ) { askActivity() }
    ExtraRow(
        Icons.Rounded.Notifications, C.wt, "Two nudges a day",
        "Morning after your weigh-in, evening at 8 PM with what's left.", notifyOk, "onboarding_notifications",
    ) { askNotifications() }
    PCard(padding = 14.dp) {
        Lbl("You")
        Text(summary, style = T.label.copy(fontWeight = FontWeight(600)), modifier = Modifier.padding(top = 4.dp))
    }
    Spacer(Modifier.weight(1f))
    PButton("Start my journey", Modifier.testTag("onboarding_start")) { startJourney() }
}

@Composable
private fun PermRow(label: String, ok: Boolean, divider: Boolean = true) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = T.body.copy(color = C.ink, fontSize = 14.5.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (ok) C.under else C.dim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (ok) "Allowed" else "Not yet", style = T.label.copy(fontSize = 13.sp, color = if (ok) C.under else C.dim))
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
    }
}

@Composable
private fun ExtraRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    title: String,
    text: String,
    ok: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    PCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = T.label)
                Text(text, style = T.small.copy(fontSize = 12.5.sp))
            }
            Spacer(Modifier.width(8.dp))
            if (ok) {
                Icon(Icons.Rounded.CheckCircle, "Allowed", tint = C.under, modifier = Modifier.size(22.dp))
            } else {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(color.copy(alpha = 0.16f))
                        .clickable { onClick() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag(tag),
                ) { Text("Allow", style = T.label.copy(color = color, fontSize = 13.sp)) }
            }
        }
    }
}

// ---- Shared form bits (also used by Settings) ---------------------------------------------------

/** Parses "82.5" or "82,5". */
fun num(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** Keeps digits and one decimal separator, at most [max] characters. */
fun digits(text: String, max: Int): String {
    var seenDot = false
    return text.filter {
        when {
            it.isDigit() -> true
            (it == '.' || it == ',') && !seenDot -> { seenDot = true; true }
            else -> false
        }
    }.take(max)
}

fun fmtNum(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun PField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    tag: String,
    type: KeyboardType = KeyboardType.Decimal,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        textStyle = T.label.copy(fontSize = 16.sp, fontWeight = FontWeight(600)),
        keyboardOptions = KeyboardOptions(
            keyboardType = type,
            capitalization = if (type == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None,
        ),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.under,
            unfocusedBorderColor = C.line,
            focusedLabelColor = C.under,
            unfocusedLabelColor = C.mute,
            cursorColor = C.under,
            focusedContainerColor = C.s1,
            unfocusedContainerColor = C.s1,
            focusedTextColor = C.ink,
            unfocusedTextColor = C.ink,
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}
