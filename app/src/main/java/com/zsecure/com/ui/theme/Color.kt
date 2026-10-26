package com.zsecure.com.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Z+ Secure brand palette.
 *
 * The brand identity is a deep-navy "vault" background with an electric-cyan
 * primary and a blue secondary (carried over from the original hand-coded
 * screens, now expressed as proper Material 3 tonal roles for both light and
 * dark schemes). Semantic strength colours are shared by the password
 * generator's meter in both schemes.
 */

// ---- Dark scheme (the brand's home turf) ----
val BrandCyanDark = Color(0xFF57D9F2)          // primary
val BrandOnCyanDark = Color(0xFF003640)
val BrandCyanContainerDark = Color(0xFF004E5E)
val BrandOnCyanContainerDark = Color(0xFFB9EDFB)

val BrandBlueDark = Color(0xFF9DBBFF)           // secondary
val BrandOnBlueDark = Color(0xFF16305F)
val BrandBlueContainerDark = Color(0xFF2E4A7D)
val BrandOnBlueContainerDark = Color(0xFFD8E4FF)

val BrandVioletDark = Color(0xFFCDBDFF)         // tertiary
val BrandOnVioletDark = Color(0xFF3A2760)
val BrandVioletContainerDark = Color(0xFF51417E)
val BrandOnVioletContainerDark = Color(0xFFE9DFFF)

val DarkBackground = Color(0xFF0A0E17)
val DarkOnBackground = Color(0xFFE3E9F4)
val DarkSurface = Color(0xFF0F1522)
val DarkOnSurface = Color(0xFFE3E9F4)
val DarkSurfaceVariant = Color(0xFF1B2433)
val DarkOnSurfaceVariant = Color(0xFFA9B4C6)
val DarkSurfaceContainerLowest = Color(0xFF070A11)
val DarkSurfaceContainerLow = Color(0xFF101623)
val DarkSurfaceContainer = Color(0xFF131A26)
val DarkSurfaceContainerHigh = Color(0xFF1A2333)
val DarkSurfaceContainerHighest = Color(0xFF232F42)
val DarkOutline = Color(0xFF5B6B82)
val DarkOutlineVariant = Color(0xFF39445A)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkInverseSurface = Color(0xFFE3E9F4)
val DarkInverseOnSurface = Color(0xFF131A26)
val DarkInversePrimary = Color(0xFF00677C)

// ---- Light scheme (first-class, not an afterthought) ----
val BrandCyanLight = Color(0xFF00677C)          // primary
val BrandOnCyanLight = Color(0xFFFFFFFF)
val BrandCyanContainerLight = Color(0xFFB8ECFB)
val BrandOnCyanContainerLight = Color(0xFF004E5E)

val BrandBlueLight = Color(0xFF2E4A7D)          // secondary
val BrandOnBlueLight = Color(0xFFFFFFFF)
val BrandBlueContainerLight = Color(0xFFD8E4FF)
val BrandOnBlueContainerLight = Color(0xFF16305F)

val BrandVioletLight = Color(0xFF51417E)        // tertiary
val BrandOnVioletLight = Color(0xFFFFFFFF)
val BrandVioletContainerLight = Color(0xFFE9DFFF)
val BrandOnVioletContainerLight = Color(0xFF3A2760)

val LightBackground = Color(0xFFF6F8FC)
val LightOnBackground = Color(0xFF141B28)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF141B28)
val LightSurfaceVariant = Color(0xFFE2E9F2)
val LightOnSurfaceVariant = Color(0xFF44536A)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFEFF3F9)
val LightSurfaceContainer = Color(0xFFE9EEF5)
val LightSurfaceContainerHigh = Color(0xFFE2E8F1)
val LightSurfaceContainerHighest = Color(0xFFD8E0EC)
val LightOutline = Color(0xFF71809A)
val LightOutlineVariant = Color(0xFFC2CEDF)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF93000A)
val LightInverseSurface = Color(0xFF232B3A)
val LightInverseOnSurface = Color(0xFFEDF1F8)
val LightInversePrimary = Color(0xFF57D9F2)

// ---- Password-strength semantics (scheme-independent hues) ----
val StrengthVeryWeak = Color(0xFFD32F2F)
val StrengthWeak = Color(0xFFEF6C00)
val StrengthMedium = Color(0xFFF9A825)
val StrengthStrong = Color(0xFF43A047)
val StrengthVeryStrong = Color(0xFF1B5E20)
