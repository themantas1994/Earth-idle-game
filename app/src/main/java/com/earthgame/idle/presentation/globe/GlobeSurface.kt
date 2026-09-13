package com.earthgame.idle.presentation.globe

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.earthgame.idle.domain.model.GraphicsQuality
import com.earthgame.idle.presentation.visualization.EnvironmentOverlay
import com.earthgame.idle.presentation.visualization.EnvironmentalVisualizationState
import kotlin.math.hypot

/**
 * The 3D Earth, as a composable.
 *
 * ## What it does and does not own
 *
 * It owns a `GLSurfaceView` and a [GlobeRenderer], and it hands them an
 * immutable [GlobeScene] whenever the simulation produces a new one. It owns
 * no game state and computes none. The globe is a view of the world, never a
 * participant in it.
 *
 * ## Recomposition
 *
 * The `AndroidView` is created once. Its `update` block writes a new scene onto
 * the renderer and returns — it never rebuilds the surface, reallocates the
 * mesh or regenerates a texture. A tick four times a second therefore costs a
 * field write, not a scene rebuild.
 *
 * ## When 3D is not available
 *
 * Two things can take the globe away: a device that cannot offer OpenGL ES 2.0,
 * which is checked before the surface is created, and a failure inside the
 * renderer, which reports itself. Either one swaps in [FlatGlobe], a Compose
 * drawing of the same [EnvironmentalVisualizationState]. The player loses some
 * polish and nothing else: every number is still on the screen beside it, and
 * the simulation never knew the difference.
 */
@Composable
fun GlobeSurface(
    environment: EnvironmentalVisualizationState,
    overlay: EnvironmentOverlay,
    quality: GraphicsQuality,
    reducedMotion: Boolean,
    nightLights: Float,
    focusStormId: String?,
    onStormSelected: (String?) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val supportsOpenGl = remember(context) { supportsOpenGlEs2(context) }
    var rendererFailed by remember { mutableStateOf(false) }

    if (!supportsOpenGl || rendererFailed) {
        FlatGlobe(
            environment = environment,
            overlay = overlay,
            reducedMotion = reducedMotion || quality == GraphicsQuality.LOW,
            focusStormId = focusStormId,
            onStormSelected = onStormSelected,
            contentDescription = contentDescription,
            modifier = modifier,
        )
        return
    }

    val renderer = remember { GlobeRenderer(onFailure = { rendererFailed = true }) }
    val currentOnStormSelected by rememberUpdatedState(onStormSelected)
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceView by remember { mutableStateOf<GLSurfaceView?>(null) }

    // The GL thread is expensive to leave running behind a locked screen, and
    // `GLSurfaceView` only pauses itself if it is told to.
    DisposableEffect(lifecycleOwner, surfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surfaceView?.onResume()
                Lifecycle.Event.ON_PAUSE -> surfaceView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(environment, overlay, quality, reducedMotion, nightLights, focusStormId) {
        renderer.scene = GlobeScene(
            environment = environment,
            overlay = overlay,
            quality = quality,
            reducedMotion = reducedMotion,
            nightLights = nightLights,
            focusStormId = focusStormId,
        )
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                // A tap selects the storm under the finger, and a tap on empty
                // ocean clears the selection — so the camera can always be
                // handed back.
                detectTapGestures { offset ->
                    val target = renderer.stormHitTargets
                        .filter { it.visible }
                        .minByOrNull { hypot(it.x - offset.x, it.y - offset.y) }
                    val hit = target?.takeIf {
                        hypot(it.x - offset.x, it.y - offset.y) <= it.radiusPixels
                    }
                    currentOnStormSelected(hit?.id)
                }
            }
            .semantics {
                this.contentDescription = contentDescription
            },
    ) {
        AndroidView(
            factory = { viewContext -> GlobeGLSurfaceView(viewContext, renderer).also { surfaceView = it } },
            // The surface itself is decoration: the description on the Box
            // above already says what the planet is doing, and every value it
            // draws is printed as text elsewhere on the screen.
            modifier = Modifier.fillMaxSize().clearAndSetSemantics { },
        )
    }
}

/**
 * Whether this device reports OpenGL ES 2.0.
 *
 * Checked rather than assumed. Every device at `minSdk 24` should have it, and
 * the app declares no `uses-feature` requiring it precisely so a device that
 * somehow does not can still install and play — it simply gets the flat globe.
 */
internal fun supportsOpenGlEs2(context: Context): Boolean = try {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    (manager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0) >= 0x20000
} catch (_: Exception) {
    false
}

/**
 * The `GLSurfaceView` the globe is drawn into, and the gestures that turn it.
 *
 * A named class rather than an anonymous object so the lint suppression below
 * has somewhere to live, and so the touch handling reads on its own.
 *
 * **It never consumes a touch.** `onTouchEvent` returns false for every event,
 * so the gesture detector Compose put over it still sees the tap that selects a
 * storm. This view rotates and zooms; Compose selects.
 */
// `ViewConstructor`: this view is only ever constructed in code, by the
// `AndroidView` above. It is not in any layout file and cannot be inflated from
// one, so the `(Context, AttributeSet)` constructor lint asks for would be dead
// code that also made the renderer optional.
@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
private class GlobeGLSurfaceView(
    context: Context,
    private val renderer: GlobeRenderer,
) : GLSurfaceView(context) {

    private var lastX = 0f
    private var lastY = 0f
    private var pointerSpacing = 0f

    init {
        setEGLContextClientVersion(2)
        // Transparent, so the globe sits on the game's own background rather
        // than on a black rectangle.
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(false)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /**
     * Lint's `ClickableViewAccessibility` rule wants a custom view that handles
     * touch to route clicks through `performClick()`. This one deliberately does
     * not: it has no click to route. It never consumes a touch and never reports
     * one — the tap belongs to the Compose detector above it, this view is
     * removed from the semantics tree entirely, and selecting a storm is
     * reachable without the globe at all, from the storm chips on Home.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                renderer.setInteracting(true)
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_POINTER_DOWN -> pointerSpacing = spacing(event)

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val spacing = spacing(event)
                    if (pointerSpacing > 0f && spacing > 0f) {
                        renderer.applyZoom(spacing / pointerSpacing)
                    }
                    pointerSpacing = spacing
                } else {
                    val density = resources.displayMetrics.density
                    renderer.applyDrag((event.x - lastX) / density, (event.y - lastY) / density)
                    lastX = event.x
                    lastY = event.y
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pointerSpacing = 0f
                renderer.setInteracting(false)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return false
    }

    private fun spacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
    }
}
