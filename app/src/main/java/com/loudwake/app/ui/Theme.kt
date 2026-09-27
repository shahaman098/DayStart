package com.loudwake.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loudwake.app.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

val OutfitFamily = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold)
)

/**
 * Forum-inspired palette (not Reddit trademarks):
 * cool feed canvas, white posts, ember accent for CTAs only.
 */
private val Ember = Color(0xFFFF5C1A)
private val Ink = Color(0xFF1A1A1B)
private val Feed = Color(0xFFDAE0E6)
private val Post = Color(0xFFFFFFFF)
private val Muted = Color(0xFF787C7E)
private val Hairline = Color(0xFFCCCBCB)

private val LightColors = lightColorScheme(
    primary = Ember,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE2D6),
    onPrimaryContainer = Color(0xFF3B1200),
    secondary = Color(0xFF2A2A2B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E9EA),
    onSecondaryContainer = Color(0xFF1A1A1B),
    tertiary = Color(0xFF0079D3),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6ECFF),
    onTertiaryContainer = Color(0xFF001D33),
    error = Color(0xFFEA0027),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Feed,
    onBackground = Ink,
    surface = Post,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF2F4F5),
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Post,
    surfaceContainerLow = Post,
    surfaceContainer = Color(0xFFF6F7F8),
    surfaceContainerHigh = Color(0xFFEEF0F2),
    surfaceContainerHighest = Color(0xFFE5E8EB),
    outline = Hairline,
    outlineVariant = Color(0xFFEDEFF1)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A45),
    onPrimary = Color(0xFF3B1200),
    primaryContainer = Color(0xFF8A2E0A),
    onPrimaryContainer = Color(0xFFFFE2D6),
    secondary = Color(0xFFD7DADC),
    onSecondary = Color(0xFF1A1A1B),
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFE9E9EA),
    tertiary = Color(0xFF72B7F0),
    onTertiary = Color(0xFF001D33),
    tertiaryContainer = Color(0xFF004B7A),
    onTertiaryContainer = Color(0xFFD6ECFF),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF030303),
    onBackground = Color(0xFFD7DADC),
    surface = Color(0xFF1A1A1B),
    onSurface = Color(0xFFD7DADC),
    surfaceVariant = Color(0xFF272729),
    onSurfaceVariant = Color(0xFFA5A8AA),
    surfaceContainerLowest = Color(0xFF121213),
    surfaceContainerLow = Color(0xFF1A1A1B),
    surfaceContainer = Color(0xFF222223),
    surfaceContainerHigh = Color(0xFF2A2A2B),
    surfaceContainerHighest = Color(0xFF333334),
    outline = Color(0xFF343536),
    outlineVariant = Color(0xFF272729)
)

/** Wake / Relapse surfaces — charcoal + ember, not cartoon red. */
private val MissionColors = darkColorScheme(
    primary = Color(0xFFFF7A45),
    onPrimary = Color(0xFF3B1200),
    primaryContainer = Color(0xFF8A2E0A),
    onPrimaryContainer = Color(0xFFFFE2D6),
    secondary = Color(0xFFD7DADC),
    onSecondary = Color(0xFF1A1A1B),
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFE9E9EA),
    tertiary = Color(0xFFFFB690),
    onTertiary = Color(0xFF3B1200),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B0B0C),
    onBackground = Color(0xFFF2F4F5),
    surface = Color(0xFF1A1A1B),
    onSurface = Color(0xFFF2F4F5),
    surfaceVariant = Color(0xFF272729),
    onSurfaceVariant = Color(0xFFB0B3B5),
    // Explicit charcoal ramp (matches DarkColors) — without these, M3 falls back to its
    // default neutral dark containers, which read as faintly violet against ember/charcoal.
    surfaceContainerLowest = Color(0xFF121213),
    surfaceContainerLow = Color(0xFF1A1A1B),
    surfaceContainer = Color(0xFF222223),
    surfaceContainerHigh = Color(0xFF2A2A2B),
    surfaceContainerHighest = Color(0xFF333334),
    outline = Color(0xFF3A3A3C),
    outlineVariant = Color(0xFF2C2C2E)
)

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun typeStyle(
    weight: FontWeight,
    size: Int,
    line: Int,
    letter: Float = 0f
) = TextStyle(
    fontFamily = OutfitFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = letter.sp,
    lineHeightStyle = lineHeightStyle
)

/**
 * Above [Typography.displayLarge] — M3's `Typography` has no slot for this, so it's a
 * standalone token. `RelapseActivity`/`AlarmActivity` were each hand-overriding
 * `displayLarge.copy(fontSize = ...)` with a different ad-hoc size; this formalizes one
 * hero tier instead of three inconsistent overrides.
 */
val DisplayHeroStyle = typeStyle(FontWeight.ExtraBold, 64, 68, (-1.6f))

private val AppTypography = Typography(
    displayLarge = typeStyle(FontWeight.Bold, 52, 58, (-1.2f)),
    displayMedium = typeStyle(FontWeight.Bold, 40, 46, (-0.8f)),
    displaySmall = typeStyle(FontWeight.SemiBold, 32, 38, (-0.4f)),
    headlineLarge = typeStyle(FontWeight.SemiBold, 28, 34),
    headlineMedium = typeStyle(FontWeight.SemiBold, 24, 30),
    headlineSmall = typeStyle(FontWeight.SemiBold, 20, 26),
    titleLarge = typeStyle(FontWeight.SemiBold, 18, 24),
    titleMedium = typeStyle(FontWeight.SemiBold, 15, 20, 0.1f),
    titleSmall = typeStyle(FontWeight.Medium, 13, 18, 0.1f),
    bodyLarge = typeStyle(FontWeight.Normal, 15, 22, 0.15f),
    bodyMedium = typeStyle(FontWeight.Normal, 13, 18, 0.15f),
    bodySmall = typeStyle(FontWeight.Normal, 12, 16, 0.2f),
    labelLarge = typeStyle(FontWeight.SemiBold, 13, 18, 0.2f),
    labelMedium = typeStyle(FontWeight.Medium, 11, 14, 0.3f),
    labelSmall = typeStyle(FontWeight.Medium, 10, 14, 0.4f)
)

/** Tighter radii — closer to feed posts than bubbly cards. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

/**
 * Atmosphere tokens that [Shapes]/[ColorScheme] can't express (Brush-based gradients,
 * a hero card shape used only by frosted surfaces). Kept separate from [ColorScheme]
 * rather than bolted on, since Material3's color scheme has no gradient slot.
 */
data class DsAtmosphere(
    val backgroundGradient: Brush,
    val glassTint: Color,
    val glassBorderTop: Brush,
    val heroCardShape: RoundedCornerShape = RoundedCornerShape(20.dp),
    /** Sun-disc fill for [CoachSol] — warm center to ember edge. */
    val solDiscGradient: Brush,
    /** Sol's ray color — usually the theme's ember primary. */
    val solRayColor: Color,
    /** Sol's brow/eye/mouth stroke color — needs to read against a bright disc in both themes. */
    val solFaceColor: Color = Ink
)

private val LocalDsAtmosphere = staticCompositionLocalOf<DsAtmosphere> {
    error("No DsAtmosphere provided — wrap content in DayStartTheme or MissionTheme")
}

val MaterialTheme.dsAtmosphere: DsAtmosphere
    @Composable get() = LocalDsAtmosphere.current

private fun glassBorderTop(tint: Color) = Brush.verticalGradient(
    listOf(tint.copy(alpha = 0.35f), tint.copy(alpha = 0f))
)

/** Fades to [flat] by ~28% down the screen instead of spreading linearly across it. */
private fun topCast(top: Color, flat: Color) = Brush.verticalGradient(
    0f to top,
    0.28f to flat,
    1f to flat
)

private fun solDisc(center: Color, edge: Color) = Brush.radialGradient(listOf(center, edge))

// Top warm cast, resolving to the flat page background below — never painted
// directly under body text, only behind cards/atmosphere.
private val HomeLightAtmosphere = DsAtmosphere(
    backgroundGradient = topCast(Color(0xFFFFF1E8), Feed),
    glassTint = Color.White.copy(alpha = 0.55f),
    glassBorderTop = glassBorderTop(Color.White),
    solDiscGradient = solDisc(Color(0xFFFFE7B8), Ember),
    solRayColor = Ember
)

private val HomeDarkAtmosphere = DsAtmosphere(
    backgroundGradient = topCast(Color(0xFF241207), Color(0xFF030303)),
    glassTint = Color(0xFF2A2A2B).copy(alpha = 0.45f),
    glassBorderTop = glassBorderTop(Color(0xFFFF7A45)),
    solDiscGradient = solDisc(Color(0xFFFFD9A0), Color(0xFFFF7A45)),
    solRayColor = Color(0xFFFF7A45)
)

// Deliberately low luminance-delta — Wake Gate must stay glanceable at 6am, not moody.
private val MissionAtmosphere = DsAtmosphere(
    backgroundGradient = topCast(Color(0xFF12100E), Color(0xFF0B0B0C)),
    glassTint = Color(0xFF2A2A2B).copy(alpha = 0.5f),
    glassBorderTop = glassBorderTop(Color(0xFFFF7A45)),
    solDiscGradient = solDisc(Color(0xFFFFCB8A), Color(0xFFFF7A45)),
    solRayColor = Color(0xFFFF7A45)
)

@Composable
fun DayStartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Brand palette for store screenshots and consistency. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val atmosphere = if (darkTheme) HomeDarkAtmosphere else HomeLightAtmosphere
    CompositionLocalProvider(LocalDsAtmosphere provides atmosphere) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

@Composable
fun MissionTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDsAtmosphere provides MissionAtmosphere) {
        MaterialTheme(
            colorScheme = MissionColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

@Composable
fun LoudWakeTheme(content: @Composable () -> Unit) = DayStartTheme(content = content)

/**
 * Shared Haze blur source for the current screen's [GlassCard]/[MissionCard] instances.
 * Null when the screen isn't hosted inside an atmosphere [SurfaceBackground] — consumers
 * fall back to a flat surface in that case rather than crashing.
 */
internal val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Page-level canvas. Paints the atmosphere gradient behind content when [useAtmosphere]
 * is true (and makes that content available as a Haze blur source for glass cards placed
 * inside it); otherwise falls back to the flat theme background (unchanged default).
 */
@Composable
fun SurfaceBackground(
    useAtmosphere: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    if (useAtmosphere) {
        val hazeState = rememberHazeState()
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.dsAtmosphere.backgroundGradient)
                    .hazeSource(state = hazeState),
                content = content
            )
        }
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            content = content
        )
    }
}
