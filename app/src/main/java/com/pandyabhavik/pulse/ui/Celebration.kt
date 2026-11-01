package com.pandyabhavik.pulse.ui

import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandyabhavik.pulse.core.Milestone
import com.pandyabhavik.pulse.core.Motivation
import com.pandyabhavik.pulse.core.RingStyle
import com.pandyabhavik.pulse.data.HomeState
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.sin

/** Full-screen milestone celebration: big glowing kg, one tangible line, what's next, the unlocked ring. */
@Composable
fun Celebration(s: HomeState, m: Milestone) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val p = s.profile ?: return
    val today = s.now.toLocalDate()
    val reached = m.reachedOn ?: today
    val days = ChronoUnit.DAYS.between(p.startDate, reached).coerceAtLeast(1)
    val line = remember(m, days) { runCatching { Motivation.celebrationLine(p, m, days) }.getOrDefault("") }
    val next = s.forecast?.next?.takeIf { it.kg < m.kg }
    val trend = s.trendKg ?: m.kg

    Box(
        Modifier
            .fillMaxSize()
            .background(C.bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(C.wt.copy(alpha = 0.30f), C.wt2.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.34f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
            }
            // Swallow taps so nothing underneath reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .testTag("celebration"),
    ) {
        Sparkles()
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 48.dp, bottom = 20.dp)
                .align(Alignment.TopCenter)
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Milestone reached · ${F.dayShort(reached)}".uppercase(),
                style = T.caps.copy(color = C.wt2, letterSpacing = 1.6.sp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                F.kgShort(m.kg),
                style = T.hero.copy(fontSize = 96.sp, lineHeight = 98.sp, letterSpacing = (-3).sp).glow(C.wt, 60f),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.gradient(C.weightBrush),
            )
            Text("kg on trend", style = T.label.copy(fontSize = 18.sp))
            Text(m.label, style = T.small.copy(fontSize = 13.sp, color = C.wt), modifier = Modifier.padding(top = 2.dp))
            if (line.isNotBlank()) {
                Text(
                    line,
                    style = T.body.copy(fontSize = 15.sp, lineHeight = 22.sp, color = Color(0xFFD6D9E3)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp, start = 6.dp, end = 6.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            if (next != null) {
                PCard {
                    Lbl("Up next", Icons.Rounded.Flag, C.wt)
                    val away = (trend - next.kg).coerceAtLeast(0.0)
                    Metric(
                        "${F.kgShort(next.kg)} kg",
                        "${F.kg(away)} kg away" + (next.projected?.let { " · around ${F.date(it, today)}" } ?: ""),
                    )
                    val span = (p.startKg - next.kg).coerceAtLeast(0.1)
                    val frac = ((p.startKg - trend) / span).coerceIn(0.0, 1.0)
                    GlowBar(frac.toFloat(), listOf(C.wt, C.wt2))
                    Row(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                        Text("You're already ${(frac * 100).roundToInt()}% there", style = T.small, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${s.winsDone} of ${s.winsTotal} wins", style = T.small, maxLines = 1)
                    }
                }
            } else {
                PCard {
                    Lbl("Goal", Icons.Rounded.Flag, C.wt2)
                    Text("You made it. ${F.kgShort(p.goalKg)} kg, on trend.", style = T.h3, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (s.ringStyle != RingStyle.MINT) {
                Spacer(Modifier.height(12.dp))
                PCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BalanceRing(burned = 100, eaten = 0, style = s.ringStyle, size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Unlocked: ${s.ringStyle.label} ring", style = T.label)
                            Text("A new ring colour for Today. Every milestone unlocks a surprise.", style = T.small.copy(fontSize = 12.5.sp))
                        }
                    }
                }
            }
            Spacer(Modifier.heightIn(min = 24.dp).height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PButton(
                    "Share",
                    Modifier.weight(1f),
                    brush = Brush.linearGradient(listOf(C.s2, C.s2)),
                    textColor = C.ink,
                    icon = Icons.Rounded.Share,
                ) {
                    val text = "${F.kgShort(m.kg)} kg on trend with Pulse. ${m.label}! $line".trim()
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                null,
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                PButton(
                    "Keep going",
                    Modifier.weight(1.4f).testTag("celebration_continue"),
                    brush = C.weightBrush,
                    textColor = Color(0xFF12061A),
                ) { ui.vm.celebrated(m.kg) }
            }
        }
    }
}

/** Twinkling coloured sparks over the glow. */
@Composable
private fun Sparkles() {
    val t = rememberInfiniteTransition(label = "sparks")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "spark-phase")
    val sparks = remember {
        listOf(
            Triple(0.20f, 0.18f, C.wt2), Triple(0.78f, 0.14f, C.wt), Triple(0.86f, 0.32f, C.under),
            Triple(0.12f, 0.40f, C.steps), Triple(0.64f, 0.08f, Color.White), Triple(0.36f, 0.30f, C.wt2),
            Triple(0.90f, 0.48f, C.wt), Triple(0.08f, 0.12f, C.under), Triple(0.52f, 0.22f, C.steps),
            Triple(0.28f, 0.52f, C.wt), Triple(0.72f, 0.56f, C.wt2),
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        sparks.forEachIndexed { i, (x, y, c) ->
            val wobble = sin((phase * 2f + i * 0.37f) * Math.PI.toFloat())
            val a = 0.35f + 0.65f * (0.5f + 0.5f * wobble)
            val r = (if (i % 2 == 0) 3.dp else 2.dp).toPx()
            val center = Offset(size.width * x, size.height * y - phase * 6.dp.toPx())
            drawCircle(c.copy(alpha = a * 0.35f), r * 3.2f, center)
            drawCircle(c.copy(alpha = a), r, center)
        }
    }
}
