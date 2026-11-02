package com.pandyabhavik.pulse.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pandyabhavik.pulse.R
import com.pandyabhavik.pulse.core.RingStyle

/** The approved mock's palette: OLED black with glowing colour. */
object C {
    val bg = Color(0xFF050608)
    val s1 = Color(0xFF0C0E13)
    val s2 = Color(0xFF14171F)
    val s3 = Color(0xFF1C202A)
    val line = Color(0xFF232836)
    val ink = Color(0xFFF3F4F8)
    val mute = Color(0xFF8D93A6)
    val dim = Color(0xFF5A6072)
    val under = Color(0xFF2EF2A8)
    val over = Color(0xFFFF4D6D)
    val steps = Color(0xFF4DA8FF)
    val wt = Color(0xFFB48CFF)
    val wt2 = Color(0xFFFF7AD9)
    val sleep = Color(0xFF7C8CFF)
    val food = Color(0xFFFFB86B)
    val hot = Color(0xFFFF8A5C)

    /** Body copy a step brighter than [mute], as in the mock's notes. */
    val soft = Color(0xFFC9CDD9)
    val track = Color(0xFF161A23)
    val sheet = Color(0xFF10131A)
    val restingBar = Color(0xFF3A4256)

    /** Calm status colours (Bhavik: encouraging, never scary). On track uses [under]. */
    val steady = Color(0xFF8FB8FF)
    val grow = Color(0xFFFFA3C7)

    val weightBrush: Brush get() = Brush.horizontalGradient(listOf(wt, wt2))
    val goBrush: Brush get() = Brush.horizontalGradient(listOf(under, steps))
    val heroBrush: Brush get() = Brush.horizontalGradient(listOf(under, steps, wt, wt2))
}

/** Colours of the glowing "deficit gap" arc of the balance ring, per unlocked style. MINT is the design default. */
object RingColors {
    fun of(style: RingStyle): List<Color> = when (style) {
        RingStyle.MINT -> listOf(C.under, Color(0xFF7BFFD3), C.under)
        RingStyle.AURORA -> listOf(C.under, C.steps, C.wt, C.wt2, C.under)
        RingStyle.SUNRISE -> listOf(Color(0xFFFFD36B), C.food, C.hot, C.wt2, Color(0xFFFFD36B))
        RingStyle.OCEAN -> listOf(Color(0xFF2EF2E0), C.steps, Color(0xFF3D6BFF), Color(0xFF2EF2E0))
        RingStyle.NEON -> listOf(Color(0xFF00F5FF), Color(0xFFFF2BD6), Color(0xFF00F5FF))
        RingStyle.EMBER -> listOf(Color(0xFFFFD36B), C.hot, Color(0xFFFF5FA2), Color(0xFFFFD36B))
        RingStyle.GLACIER -> listOf(Color(0xFFE6FBFF), Color(0xFF7FD8FF), Color(0xFFB4C8FF), Color(0xFFE6FBFF))
        RingStyle.PRISM -> listOf(C.under, C.steps, C.wt, C.wt2, C.food, C.under)
    }

    /** The main colour of a style, for its glow and thumbnails. */
    fun main(style: RingStyle): Color = of(style)[1]
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun display(weight: Int) = Font(
    R.font.bricolage,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun body(weight: Int) = Font(
    R.font.figtree,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Display = FontFamily(display(400), display(600), display(700), display(800))
val Body = FontFamily(body(400), body(500), body(600), body(700), body(800))

/** Type scale from the mock. Sizes are chosen to fit the cover screen at font scale 1.15. */
object T {
    val hero = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 56.sp, lineHeight = 58.sp, letterSpacing = (-1.2).sp, color = C.ink)
    val big = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp, color = C.ink)
    val title = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 21.sp, lineHeight = 25.sp, letterSpacing = (-0.1).sp, color = C.ink)
    val h3 = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 16.sp, lineHeight = 20.sp, color = C.ink)
    val label = TextStyle(fontFamily = Body, fontWeight = FontWeight(700), fontSize = 14.sp, lineHeight = 19.sp, color = C.ink)
    val body = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 14.sp, lineHeight = 20.sp, color = C.soft)
    val small = TextStyle(fontFamily = Body, fontWeight = FontWeight(600), fontSize = 12.sp, lineHeight = 16.sp, color = C.mute)
    val caps = TextStyle(fontFamily = Body, fontWeight = FontWeight(600), fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.1.sp, color = C.mute)
    val metric = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp, color = C.ink)
    val num = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 15.sp, lineHeight = 19.sp, color = C.ink)
}

/** A soft coloured halo behind text, like the mock's text-shadow glow. */
fun TextStyle.glow(color: Color, radius: Float = 36f): TextStyle =
    copy(color = color, shadow = Shadow(color = color.copy(alpha = 0.55f), offset = Offset.Zero, blurRadius = radius))

@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = C.bg,
            surface = C.s1,
            surfaceVariant = C.s2,
            surfaceContainer = C.s1,
            surfaceContainerHigh = C.s2,
            surfaceContainerLow = C.s1,
            primary = C.under,
            onPrimary = Color(0xFF04110B),
            secondary = C.wt,
            onSecondary = Color(0xFF12061A),
            tertiary = C.steps,
            onBackground = C.ink,
            onSurface = C.ink,
            onSurfaceVariant = C.mute,
            outline = C.line,
            outlineVariant = C.line,
            error = C.over,
        ),
        content = content,
    )
}
