package com.zsecure.com.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Consistent spacing scale used across all screens (no more ad-hoc dp soup). */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    /** Standard horizontal screen padding. */
    val screen = 16.dp
}

/** Rounded, friendly-but-serious shape scale for a security product. */
val ZSecureShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandCyanDark,
    onPrimary = BrandOnCyanDark,
    primaryContainer = BrandCyanContainerDark,
    onPrimaryContainer = BrandOnCyanContainerDark,
    secondary = BrandBlueDark,
    onSecondary = BrandOnBlueDark,
    secondaryContainer = BrandBlueContainerDark,
    onSecondaryContainer = BrandOnBlueContainerDark,
    tertiary = BrandVioletDark,
    onTertiary = BrandOnVioletDark,
    tertiaryContainer = BrandVioletContainerDark,
    onTertiaryContainer = BrandOnVioletContainerDark,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary
)

private val LightColorScheme = lightColorScheme(
    primary = BrandCyanLight,
    onPrimary = BrandOnCyanLight,
    primaryContainer = BrandCyanContainerLight,
    onPrimaryContainer = BrandOnCyanContainerLight,
    secondary = BrandBlueLight,
    onSecondary = BrandOnBlueLight,
    secondaryContainer = BrandBlueContainerLight,
    onSecondaryContainer = BrandOnBlueContainerLight,
    tertiary = BrandVioletLight,
    onTertiary = BrandOnVioletLight,
    tertiaryContainer = BrandVioletContainerLight,
    onTertiaryContainer = BrandOnVioletContainerLight,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary
)

/**
 * The single theme entry point for the whole app. Follows the system
 * dark/light setting; both schemes are branded and first-class.
 */
@Composable
fun ZSecureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = ZSecureTypography,
        shapes = ZSecureShapes,
        content = content
    )
}
