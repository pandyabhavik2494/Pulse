package com.pandyabhavik.pulse.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.pandyabhavik.pulse.core.Milestone
import com.pandyabhavik.pulse.data.HomeState

enum class Tab(val key: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Rounded.MonitorHeart),
    JOURNEY("journey", "Journey", Icons.Rounded.Insights),
    HISTORY("history", "History", Icons.Rounded.History),
}

/** What any screen can reach: the view model, layout mode and app-level actions. */
class UiActions(
    val vm: MainViewModel,
    /** Inner screen (>= 600dp): two panes, rail navigation. */
    val wide: Boolean,
    /** Half-opened with a horizontal hinge: screens render only their lower-half details. */
    val flex: Boolean,
    val openSettings: () -> Unit,
    val closeSettings: () -> Unit,
    val askHealth: () -> Unit,
    val askActivity: () -> Unit,
    val askNotifications: () -> Unit,
)

val LocalUi = staticCompositionLocalOf<UiActions> { error("No UiActions") }

@Composable
fun AppRoot(
    vm: MainViewModel,
    requestedTab: MutableState<String?>,
    askHealth: () -> Unit,
    askActivity: () -> Unit,
    askNotifications: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state
    Box(
        Modifier
            .fillMaxSize()
            .background(C.bg),
    ) {
        when {
            s == null -> Loading()
            !s.onboarded -> Onboarding(vm, s, askHealth, askActivity, askNotifications)
            else -> Main(vm, s, requestedTab, askHealth, askActivity, askNotifications)
        }
    }
}

/** A breathing ring while the first state is computed (a moment after launch). */
@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BalanceRing(burned = 2000, eaten = 1300, style = com.pandyabhavik.pulse.core.RingStyle.MINT, size = 120.dp)
    }
}

@Composable
private fun Main(
    vm: MainViewModel,
    s: HomeState,
    requestedTab: MutableState<String?>,
    askHealth: () -> Unit,
    askActivity: () -> Unit,
    askNotifications: () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var tabKey by rememberSaveable { mutableStateOf(Tab.TODAY.key) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val tab = Tab.entries.firstOrNull { it.key == tabKey } ?: Tab.TODAY

    LaunchedEffect(requestedTab.value) {
        val r = requestedTab.value ?: return@LaunchedEffect
        if (r == "settings") settingsOpen = true
        else Tab.entries.firstOrNull { it.key == r }?.let { tabKey = it.key; settingsOpen = false }
        requestedTab.value = null
    }

    val fold = rememberFlexFold()
    // Root's top in window pixels, to place the hinge relative to this layout.
    var rootTop by remember { mutableFloatStateOf(0f) }
    val flex = fold != null

    val actions = UiActions(
        vm = vm,
        wide = wide,
        flex = flex,
        openSettings = { settingsOpen = true },
        closeSettings = { settingsOpen = false },
        askHealth = askHealth,
        askActivity = askActivity,
        askNotifications = askNotifications,
    )

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = !settingsOpen && tab != Tab.TODAY) { tabKey = Tab.TODAY.key }

    val select: (Tab?) -> Unit = { t -> if (t == null) settingsOpen = true else { tabKey = t.key; settingsOpen = false } }

    CompositionLocalProvider(LocalUi provides actions) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootTop = it.positionInWindow().y },
        ) {
            when {
                fold != null -> {
                    val density = LocalDensity.current
                    val topH = with(density) { (fold.bounds.top - rootTop).coerceAtLeast(0f).toDp() }
                    val hingeH = with(density) { fold.bounds.height().toFloat().toDp() }
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(topH)
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                        ) { FlexStatus(s) }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(maxOf(hingeH, 2.dp))
                                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF2A2F3D), Color(0xFF2A2F3D), Color.Transparent))),
                        )
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                        ) {
                            Box(Modifier.weight(1f)) { Screen(tab, settingsOpen, s) }
                            NavRow(tab, settingsOpen, showSettings = true, select)
                        }
                    }
                }
                wide -> {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                    ) {
                        Rail(tab, settingsOpen, select)
                        Box(
                            Modifier
                                .weight(1f)
                                .statusBarsPadding(),
                        ) { Screen(tab, settingsOpen, s) }
                    }
                }
                else -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .statusBarsPadding(),
                        ) { Screen(tab, settingsOpen, s) }
                        if (!settingsOpen) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(C.s1.copy(alpha = 0.92f))
                                    .navigationBarsPadding(),
                            ) { NavRow(tab, settingsOpen, showSettings = false, select) }
                        } else {
                            Spacer(Modifier.navigationBarsPadding())
                        }
                    }
                }
            }

            // The milestone celebration sits over everything until "Keep going".
            val celebrate = s.celebrate
            // Keep the last milestone so the overlay has something to draw while it fades out.
            var shown by remember { mutableStateOf<Milestone?>(null) }
            SideEffect { if (celebrate != null) shown = celebrate }
            AnimatedVisibility(
                visible = celebrate != null,
                enter = fadeIn(tween(500)) + scaleIn(tween(600), initialScale = 0.96f),
                exit = fadeOut(tween(400)),
            ) {
                (celebrate ?: shown)?.let { Celebration(s, it) }
            }
        }
    }
}

@Composable
private fun Screen(tab: Tab, settingsOpen: Boolean, s: HomeState) {
    AnimatedContent(
        targetState = if (settingsOpen) "settings" else tab.key,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
        label = "screen",
    ) { key ->
        when (key) {
            "settings" -> SettingsScreen(s)
            "journey" -> JourneyScreen(s)
            "history" -> HistoryScreen(s)
            else -> TodayScreen(s)
        }
    }
}

/** Bottom navigation (cover) and the flex-mode nav row. */
@Composable
private fun NavRow(tab: Tab, settingsOpen: Boolean, showSettings: Boolean, onTab: (Tab?) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            NavItem(t.icon, t.label, t == tab && !settingsOpen, "nav_${t.key}", Modifier.weight(1f)) { onTab(t) }
        }
        if (showSettings) {
            NavItem(Icons.Rounded.Settings, "Settings", settingsOpen, "nav_settings", Modifier.weight(1f)) { onTab(null) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, on: Boolean, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (on) C.s3 else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (on) C.ink else C.dim, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = T.small.copy(color = if (on) C.ink else C.dim, fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Inner-screen rail: three tabs at the top, Settings at the bottom. */
@Composable
private fun Rail(tab: Tab, settingsOpen: Boolean, onTab: (Tab?) -> Unit) {
    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier
                .width(80.dp)
                .fillMaxHeight()
                .background(C.s1)
                .statusBarsPadding()
                .padding(top = 40.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Tab.entries.forEach { t ->
                NavItem(t.icon, t.label, t == tab && !settingsOpen, "nav_${t.key}") { onTab(t) }
            }
            Spacer(Modifier.weight(1f))
            NavItem(Icons.Rounded.Settings, "Settings", settingsOpen, "nav_settings") { onTab(null) }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.line))
    }
}

/** The horizontal half-opened fold (flex mode), or null. */
@Composable
private fun rememberFlexFold(): FoldingFeature? {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val info by produceState<WindowLayoutInfo?>(initialValue = null, activity) {
        val a = activity ?: return@produceState
        WindowInfoTracker.getOrCreate(a).windowLayoutInfo(a).collect { value = it }
    }
    return info?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull { it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
