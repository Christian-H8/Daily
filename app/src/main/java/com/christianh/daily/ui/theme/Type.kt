package com.christianh.daily.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * DESIGN §6. Body text is a serif, because it is the thing actually looked at for
 * four minutes and Roboto is cold enough to undercut everything else.
 *
 * FontFamily.Serif rather than a bundled face — it is Noto Serif on essentially
 * every device, needs no file, no dependency and no network. Dropping a .ttf into
 * res/font later is a strict upgrade and touches only this file.
 *
 * Labels stay sans: they are signposts, and small letterspaced serif caps turn to
 * mush at 11sp.
 */
private val Body = FontFamily.Serif
private val Label = FontFamily.SansSerif

val Typography = Typography(
    // Entry titles
    headlineMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp
    ),
    // Taglines
    titleSmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    // The body of every entry. Slightly larger and looser than Material's default —
    // this is read at arm's length on a bus, not scanned.
    bodyLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    // Section headings, nav, tags — quiet signposts
    labelMedium = TextStyle(
        fontFamily = Label,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Label,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.8.sp
    )
)
