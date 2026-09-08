package com.earthgame.idle.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthgame.idle.domain.model.GasId
import com.earthgame.idle.domain.model.ThemePreference

/**
 * The game's palette, carried over from the original build's CSS tokens so the
 * port looks like the same game.
 *
 * Material 3's own colour scheme covers buttons, surfaces and the ripple, but
 * the game needs a handful of semantic colours it does not have an opinion
 * about — the six gas colours, "good/warning/danger" for habitability, and the
 * dim/faint text steps the dense stat rows are built on. Those live here, in
 * one [GameColors] object provided down the tree, rather than being scattered
 * as literals at the call sites.
 */
@Immutable
data class GameColors(
    val background: Color,
    val surfaceElevated: Color,
    val card: Color,
    val border: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val accentStrong: Color,
    val danger: Color,
    val warning: Color,
    val good: Color,
    val co2: Color,
    val ch4: Color,
    val n2o: Color,
    val h2o: Color,
    val o3: Color,
    val fluorinated: Color,
) {
    fun forGas(gas: GasId): Color = when (gas) {
        GasId.CO2 -> co2
        GasId.CH4 -> ch4
        GasId.N2O -> n2o
        GasId.H2O -> h2o
        GasId.O3 -> o3
        GasId.FLUORINATED -> fluorinated
    }

    /** Green while the planet is fine, amber when strained, red when it is going. */
    fun forHabitability(fraction: Double): Color = when {
        fraction > 0.5 -> good
        fraction > 0.15 -> warning
        else -> danger
    }
}

private val DarkGameColors = GameColors(
    background = Color(0xFF0D1420),
    surfaceElevated = Color(0xFF161F2E),
    card = Color(0xFF1C2635),
    border = Color(0xFF2A3648),
    text = Color(0xFFEEF3F8),
    textDim = Color(0xFF9FB0C3),
    textFaint = Color(0xFF6B7C91),
    accent = Color(0xFF4FD1C5),
    accentStrong = Color(0xFF38B2AC),
    danger = Color(0xFFF5654C),
    warning = Color(0xFFF0B429),
    good = Color(0xFF48C774),
    co2 = Color(0xFF7D8A9A),
    ch4 = Color(0xFFD98C3C),
    n2o = Color(0xFFB06AD9),
    h2o = Color(0xFF4A9FD6),
    o3 = Color(0xFF5CC9C0),
    fluorinated = Color(0xFFE05C8A),
)

private val LightGameColors = DarkGameColors.copy(
    background = Color(0xFFF2F5F8),
    surfaceElevated = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    border = Color(0xFFDBE2EA),
    text = Color(0xFF101820),
    textDim = Color(0xFF4A5A6B),
    textFaint = Color(0xFF8496A8),
    // The dark accent is legible on a dark card but washes out on white.
    accent = Color(0xFF177F76),
    accentStrong = Color(0xFF0F615A),
    good = Color(0xFF1E8E4A),
    warning = Color(0xFFA6720A),
    danger = Color(0xFFC03A24),
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkGameColors.accent,
    onPrimary = Color(0xFF06231F),
    primaryContainer = DarkGameColors.accentStrong,
    onPrimaryContainer = Color(0xFFE7FFFC),
    secondary = DarkGameColors.warning,
    onSecondary = Color(0xFF241900),
    background = DarkGameColors.background,
    onBackground = DarkGameColors.text,
    surface = DarkGameColors.surfaceElevated,
    onSurface = DarkGameColors.text,
    surfaceVariant = DarkGameColors.card,
    onSurfaceVariant = DarkGameColors.textDim,
    outline = DarkGameColors.border,
    outlineVariant = DarkGameColors.border,
    error = DarkGameColors.danger,
    onError = Color(0xFF2B0703),
)

private val LightColorScheme = lightColorScheme(
    primary = LightGameColors.accent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFF2EE),
    onPrimaryContainer = Color(0xFF04302C),
    secondary = LightGameColors.warning,
    onSecondary = Color(0xFFFFFFFF),
    background = LightGameColors.background,
    onBackground = LightGameColors.text,
    surface = LightGameColors.surfaceElevated,
    onSurface = LightGameColors.text,
    surfaceVariant = LightGameColors.card,
    onSurfaceVariant = LightGameColors.textDim,
    outline = LightGameColors.border,
    outlineVariant = LightGameColors.border,
    error = LightGameColors.danger,
    onError = Color(0xFFFFFFFF),
)

/**
 * The planet's visual era, driven by global temperature. A run reads as a slow
 * slide from green through industrial haze to a burning red without the player
 * having to watch a number.
 */
enum class VisualEra(val tint: Color) {
    PRISTINE(Color(0x1F48C774)),
    INDUSTRIAL(Color(0x24F0B429)),
    HOT(Color(0x2ED98C3C)),
    EXTREME(Color(0x38F5654C)),
    COLLAPSED(Color(0x528C281E));

    companion object {
        fun of(temperatureAnomalyC: Double, collapsed: Boolean): VisualEra = when {
            collapsed -> COLLAPSED
            temperatureAnomalyC < 0.5 -> PRISTINE
            temperatureAnomalyC < 3 -> INDUSTRIAL
            temperatureAnomalyC < 15 -> HOT
            else -> EXTREME
        }
    }
}

val LocalGameColors: ProvidableCompositionLocal<GameColors> =
    staticCompositionLocalOf { DarkGameColors }

/**
 * Whether the player has asked for reduced motion. Read by every animated
 * surface so the setting is honoured everywhere rather than in the two places
 * someone remembered.
 */
val LocalReducedAnimations: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

private val GameTypography = Typography().let { base ->
    // Numbers are the whole game, so the readouts get tabular-friendly weight
    // and tighter line heights than Material's defaults, which are tuned for
    // prose.
    base.copy(
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp),
    )
}

/** The style every live number uses: monospaced digits, so values stop jittering as they tick. */
val NumericTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
)

@Composable
fun EarthTheme(
    themePreference: ThemePreference,
    reducedAnimations: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val gameColors = if (dark) DarkGameColors else LightGameColors

    CompositionLocalProvider(
        LocalGameColors provides gameColors,
        LocalReducedAnimations provides reducedAnimations,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColorScheme else LightColorScheme,
            typography = GameTypography,
            content = content,
        )
    }
}

/** Shorthand for the game palette at a call site. */
val gameColors: GameColors
    @Composable @ReadOnlyComposable
    get() = LocalGameColors.current

object Dimens {
    /** Material's minimum touch target, and the floor for every button in the game. */
    val MinTouchTarget = 48.dp
    val ScreenPadding = 12.dp
    val CardPadding = 12.dp
    val CardSpacing = 10.dp
    val CardCorner = 12.dp
    /** Nine destinations have to fit across a 360 dp phone. */
    val NavBarHeight = 60.dp
}
