package com.earthgame.idle.presentation.globe

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Canvas
import com.earthgame.idle.presentation.theme.gameColors
import com.earthgame.idle.presentation.visualization.EnvironmentOverlay
import com.earthgame.idle.presentation.visualization.EnvironmentalVisualizationState
import com.earthgame.idle.presentation.visualization.StormVisual
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The globe, drawn in Compose, for when 3D is not available.
 *
 * A device without OpenGL ES 2.0, or a renderer that failed to start, must not
 * cost the player anything they need — so this draws the same
 * [EnvironmentalVisualizationState] the 3D globe would have: the same
 * temperature ramp, the same atmospheric halo, the same storms at the same
 * latitudes and longitudes, tappable in the same way.
 *
 * It is deliberately simple. It is a fallback, it is never the main event, and
 * the one thing it must do is keep working.
 */
@Composable
fun FlatGlobe(
    environment: EnvironmentalVisualizationState,
    overlay: EnvironmentOverlay,
    reducedMotion: Boolean,
    focusStormId: String?,
    onStormSelected: (String?) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = gameColors

    // The globe's own turn comes from the simulation clock; this is only the
    // extra drift that makes a still image feel alive, and it stops dead when
    // the player has asked for reduced motion.
    val drift by if (reducedMotion) {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "globe-drift").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(90_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "globe-drift",
        )
    }

    val rotation = environment.rotationPhase * 360f + drift
    val surface = surfaceColour(environment, overlay)
    val haloColor = haloColour(environment)

    Box(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(environment.storms, rotation) {
                detectTapGestures { offset ->
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) * 0.36f
                    val hit = environment.storms
                        .mapNotNull { storm ->
                            val position = projectOrthographic(storm, rotation, centre, radius)
                                ?: return@mapNotNull null
                            storm to position
                        }
                        .minByOrNull { hypot(it.second.x - offset.x, it.second.y - offset.y) }
                        ?.takeIf { hypot(it.second.x - offset.x, it.second.y - offset.y) <= radius * 0.22f }
                    onStormSelected(hit?.first?.id)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) * 0.36f

            // The atmospheric halo, thickening with radiative forcing.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        haloColor.copy(alpha = 0f),
                        haloColor.copy(alpha = 0.10f + 0.35f * environment.atmosphericOpacity),
                        haloColor.copy(alpha = 0f),
                    ),
                    center = centre,
                    radius = radius * 1.55f,
                ),
                radius = radius * 1.55f,
                center = centre,
            )

            // The planet, lit from the upper right so it reads as a sphere.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(surface, surface.copy(alpha = 0.92f), darken(surface, 0.45f)),
                    center = Offset(centre.x + radius * 0.35f, centre.y - radius * 0.35f),
                    radius = radius * 1.7f,
                ),
                radius = radius,
                center = centre,
            )

            // Latitude bands, which is what makes it read as a globe rather
            // than a disc and where the overlay's gradient shows.
            for (band in -4..4) {
                val fraction = band / 5f
                val bandY = centre.y + fraction * radius
                val halfWidth = radius * kotlin.math.sqrt((1f - fraction * fraction).coerceAtLeast(0f))
                drawLine(
                    color = bandColour(environment, overlay, fraction).copy(alpha = 0.30f),
                    start = Offset(centre.x - halfWidth, bandY),
                    end = Offset(centre.x + halfWidth, bandY),
                    strokeWidth = radius * 0.09f,
                )
            }

            // The night side.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    center = Offset(centre.x + radius * 0.45f, centre.y - radius * 0.45f),
                    radius = radius * 2.0f,
                ),
                radius = radius,
                center = centre,
            )

            drawCircle(
                color = colors.border.copy(alpha = 0.7f),
                radius = radius,
                center = centre,
                style = Stroke(width = 1.5f),
            )

            if (overlay.showsEvents) {
                for (event in environment.events) {
                    val position = projectOrthographic(
                        event.latitudeDeg, event.longitudeDeg, rotation, centre, radius,
                    ) ?: continue
                    drawCircle(
                        color = if (event.isNegative) colors.danger else colors.good,
                        radius = radius * 0.05f,
                        center = position,
                    )
                }
            }

            if (overlay.showsStorms) {
                for (storm in environment.storms) {
                    val position = projectOrthographic(storm, rotation, centre, radius) ?: continue
                    drawStorm(
                        centre = position,
                        radius = radius * (0.06f + 0.10f * storm.intensity),
                        intensity = storm.intensity,
                        selected = storm.id == focusStormId,
                        accent = colors.accent,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawStorm(
    centre: Offset,
    radius: Float,
    intensity: Float,
    selected: Boolean,
    accent: Color,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.30f + 0.35f * intensity),
                Color.White.copy(alpha = 0f),
            ),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
    rotate(degrees = intensity * 180f, pivot = centre) {
        drawArc(
            color = Color.White.copy(alpha = 0.55f + 0.35f * intensity),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(centre.x - radius * 0.7f, centre.y - radius * 0.7f),
            size = Size(radius * 1.4f, radius * 1.4f),
            style = Stroke(width = radius * 0.20f),
        )
    }
    if (selected) {
        drawCircle(
            color = accent,
            radius = radius * 1.25f,
            center = centre,
            style = Stroke(width = 2f),
        )
    }
}

/**
 * Orthographic projection of a latitude/longitude onto the visible face.
 *
 * Returns null for anything on the far side, so a marker never shows through
 * the planet — the flat globe's equivalent of the 3D renderer's facing fade.
 */
private fun projectOrthographic(
    latitudeDeg: Float,
    longitudeDeg: Float,
    rotationDeg: Float,
    centre: Offset,
    radius: Float,
): Offset? {
    val latitude = Math.toRadians(latitudeDeg.toDouble())
    val longitude = Math.toRadians((longitudeDeg + rotationDeg).toDouble())
    val x = cos(latitude) * sin(longitude)
    val y = sin(latitude)
    val z = cos(latitude) * cos(longitude)
    if (z <= 0.08) return null
    return Offset(
        centre.x + (x * radius).toFloat(),
        centre.y - (y * radius).toFloat(),
    )
}

private fun projectOrthographic(
    storm: StormVisual,
    rotationDeg: Float,
    centre: Offset,
    radius: Float,
): Offset? = projectOrthographic(storm.latitudeDeg, storm.longitudeDeg, rotationDeg, centre, radius)

/** The planet's base colour under the selected overlay. */
private fun surfaceColour(
    environment: EnvironmentalVisualizationState,
    overlay: EnvironmentOverlay,
): Color = when (overlay) {
    EnvironmentOverlay.TEMPERATURE -> temperatureRamp(environment.temperatureScale)
    EnvironmentOverlay.HUMIDITY -> lerp(
        Color(0xFF3D6E86), Color(0xFFD9ECFA), environment.humidity,
    )
    EnvironmentOverlay.WIND -> lerp(
        Color(0xFF23506E), Color(0xFF7FC6E8), environment.windStrength,
    )
    else -> lerp(
        Color(0xFF1D6E9C), Color(0xFF9C5A22), environment.atmosphericOpacity,
    )
}

private fun bandColour(
    environment: EnvironmentalVisualizationState,
    overlay: EnvironmentOverlay,
    latitudeFraction: Float,
): Color = when (overlay) {
    EnvironmentOverlay.TEMPERATURE ->
        temperatureRamp(environment.temperatureScale * (1f - abs(latitudeFraction) * 0.7f) * 1.4f)
    EnvironmentOverlay.HUMIDITY ->
        lerp(Color(0xFF2C5A72), Color.White, environment.humidity * (1f - abs(latitudeFraction) * 0.5f))
    else -> lerp(Color(0xFF12455F), Color(0xFF2E86A8), 1f - abs(latitudeFraction))
}

private fun haloColour(environment: EnvironmentalVisualizationState): Color =
    lerp(Color(0xFF59A8FF), Color(0xFFFF8C38), environment.atmosphericOpacity)

private fun temperatureRamp(scale: Float): Color {
    val heat = scale.coerceIn(0f, 1f)
    return when {
        heat < 0.34f -> lerp(Color(0xFF3378DB), Color(0xFF4CBD93), heat / 0.34f)
        heat < 0.67f -> lerp(Color(0xFF4CBD93), Color(0xFFF7B528), (heat - 0.34f) / 0.33f)
        else -> lerp(Color(0xFFF7B528), Color(0xFFED331D), (heat - 0.67f) / 0.33f)
    }
}

private fun lerp(from: Color, to: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * k,
        green = from.green + (to.green - from.green) * k,
        blue = from.blue + (to.blue - from.blue) * k,
        alpha = 1f,
    )
}

private fun darken(color: Color, amount: Float): Color =
    Color(color.red * (1 - amount), color.green * (1 - amount), color.blue * (1 - amount), color.alpha)
