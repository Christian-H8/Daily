package com.christianh.daily.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * DESIGN §6.
 *
 * Dynamic colour is gone. It derived the accent from the wallpaper, which cannot
 * coexist with a deliberate palette — and it was the Android Studio default
 * rather than a decision.
 */

private val LightScheme = lightColorScheme(
    primary = Brass,
    onPrimary = Paper,
    secondary = InkSoft,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperSurface,
    onSurfaceVariant = InkSoft,
    outline = InkSoft
)

private val DarkScheme = darkColorScheme(
    primary = BrassLit,
    onPrimary = Night,
    secondary = LampSoft,
    background = Night,
    onBackground = Lamp,
    surface = Night,
    onSurface = Lamp,
    surfaceVariant = NightSurface,
    onSurfaceVariant = LampSoft,
    outline = LampSoft
)

/**
 * Colours the Material scheme has no slot for — the gear metal.
 * Three stops so each gear can carry a gradient and read as a lit surface
 * rather than a flat shape.
 */
data class BrassColors(
    val hi: Color,
    val mid: Color,
    val lo: Color
)

private val BrassLight = BrassColors(BrassHi, BrassMid, BrassLo)
private val BrassDark = BrassColors(BrassHiDark, BrassMidDark, BrassLoDark)

val LocalBrass = staticCompositionLocalOf { BrassLight }

@Composable
fun DailyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val brass = if (darkTheme) BrassDark else BrassLight

    CompositionLocalProvider(LocalBrass provides brass) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            content = content
        )
    }
}
