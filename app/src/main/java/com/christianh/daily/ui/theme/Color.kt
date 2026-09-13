package com.christianh.daily.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The brass palette (DESIGN §6). Warm throughout, and deliberately free of pure
 * white and pure black — both fight everything else here.
 *
 * Replaces the generated Material purple, which was never a choice anyone made.
 */

// Paper — light
val Paper = Color(0xFFF2EADD)          // background
val PaperSurface = Color(0xFFEDE3D4)   // raised surface
val Ink = Color(0xFF2B241E)            // body text
val InkSoft = Color(0xFF6E5A45)        // walnut — secondary text
val Brass = Color(0xFF9A6F3F)          // aged brass — accent

// Gear metal, lit from the upper left
val BrassHi = Color(0xFFE8D5B0)
val BrassMid = Color(0xFFC7A474)
val BrassLo = Color(0xFF8A6A42)

// Lamplight — dark
val Night = Color(0xFF14100D)          // background
val NightSurface = Color(0xFF1C1713)   // raised surface
val Lamp = Color(0xFFE8DCC9)           // body text
val LampSoft = Color(0xFF8C7358)       // secondary text
val BrassLit = Color(0xFFC69A5E)       // accent

// Gear metal in the dark — dimmer, same light direction
val BrassHiDark = Color(0xFF7A6244)
val BrassMidDark = Color(0xFF52412D)
val BrassLoDark = Color(0xFF2E241A)
