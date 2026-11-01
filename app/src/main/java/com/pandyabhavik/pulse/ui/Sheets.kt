package com.pandyabhavik.pulse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.pandyabhavik.pulse.data.HomeState

/** A dark Pulse sheet with the mock's grab handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseSheet(tag: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = C.sheet,
        contentColor = C.ink,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetMaxWidth = 640.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF333948)),
            )
        },
        modifier = Modifier.testTag(tag),
    ) {
        LightSheetBars()
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 22.dp),
        ) { content() }
    }
}

/** The sheet's dialog window takes bar colours from the system theme; Pulse is always dark. */
@Composable
private fun LightSheetBars() {
    val view = LocalView.current
    SideEffect {
        val window = (view as? DialogWindowProvider)?.window
            ?: (view.parent as? DialogWindowProvider)?.window
            ?: return@SideEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}

/** Tap the ring: burned split into resting and active, meals, protein. */
@Composable
fun BreakdownSheet(s: HomeState, onDismiss: () -> Unit) {
    PulseSheet("breakdown_sheet", onDismiss) {
        Lbl("Burned so far", Icons.Rounded.LocalFireDepartment, C.under)
        val burned = s.burned
        if (burned == null) {
            Text("Waiting for your Fitbit to sync today's burn.", style = T.body, modifier = Modifier.padding(top = 8.dp))
        } else {
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
        Spacer(Modifier.height(20.dp))
        Lbl("Eaten · from MyFitnessPal", Icons.Rounded.Restaurant, C.food)
        val meals = F.meals(s.today)
        if (meals.isEmpty()) {
            Text(
                if (s.eaten == null) "Nothing logged yet today." else "${F.n(s.eaten)} kcal logged, no meal types.",
                style = T.body,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Spacer(Modifier.height(4.dp))
            meals.forEachIndexed { i, (name, kcal) -> FieldRow(name, F.n(kcal), divider = i < meals.lastIndex) }
        }
        s.today.proteinG?.let {
            Spacer(Modifier.height(4.dp))
            FieldRow("Protein", "$it g", divider = false)
        }
        s.balance?.let { b ->
            Spacer(Modifier.height(4.dp))
            FieldRow("Balance", F.balanceShort(b), valueColor = if (b >= 0) C.under else C.over, divider = false)
        }
        Spacer(Modifier.height(8.dp))
        LineNote(Icons.Rounded.Sync, C.mute) {
            Text(
                "Resting calories keep counting until midnight, so the deficit grows a little overnight.",
                style = T.body.copy(fontSize = 13.sp),
            )
        }
    }
}
