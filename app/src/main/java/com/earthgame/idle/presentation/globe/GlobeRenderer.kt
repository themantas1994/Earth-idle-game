package com.earthgame.idle.presentation.globe

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.earthgame.idle.domain.climate.windAt
import com.earthgame.idle.domain.model.GraphicsQuality
import com.earthgame.idle.domain.storms.DeterministicRandom
import com.earthgame.idle.domain.storms.wrapLongitude
import com.earthgame.idle.presentation.visualization.EnvironmentOverlay
import com.earthgame.idle.presentation.visualization.EnvironmentalVisualizationState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * What the renderer is asked to draw, handed over from the UI thread.
 *
 * Immutable and replaced wholesale rather than mutated, so the GL thread
 * always reads a consistent snapshot without a lock.
 */
data class GlobeScene(
    val environment: EnvironmentalVisualizationState = EnvironmentalVisualizationState.EMPTY,
    val overlay: EnvironmentOverlay = EnvironmentOverlay.ATMOSPHERE,
    val quality: GraphicsQuality = GraphicsQuality.HIGH,
    /** Suppresses every animation, honouring the game's reduced-motion setting. */
    val reducedMotion: Boolean = false,
    /** Civilisation progress, 0..1, which is how bright the night side gets. */
    val nightLights: Float = 0f,
    /** A storm the player has selected, which the camera eases toward. */
    val focusStormId: String? = null,
)

/**
 * The 3D Earth.
 *
 * ## Why this is written against OpenGL ES directly
 *
 * SceneView and Filament were both evaluated first, as the obvious
 * Compose-friendly options. Both were rejected on the same two grounds, and
 * the reasoning is recorded in `docs/wiki/Environmental-Visualization.md`:
 *
 * 1. **Size and surface area.** SceneView pulls in Filament, glTF loading and
 *    an HTTP client — roughly 24 MB of artifacts, about 13 MB of it native
 *    code — plus a `uses-feature glEsVersion=0x30000 required="true"` that
 *    would merge into this app's manifest and become a Play install filter for
 *    a game that is otherwise entirely 2D.
 * 2. **Custom materials.** Every overlay this feature needs is a custom
 *    shader, and Filament materials are compiled by `matc`, which is not
 *    published to Maven. Using it would mean a build-time native toolchain and
 *    checked-in binary blobs — in a repository whose asset policy requires
 *    documented provenance for every shipped byte.
 *
 * What is left is one textured sphere, two shells and a point-sprite pass:
 * small enough to read in one sitting, and it adds no dependency at all.
 *
 * ## Threading
 *
 * Everything here runs on the `GLSurfaceView` render thread. The simulation
 * never does: the UI thread hands over an immutable [GlobeScene] and this
 * class only reads it. No simulation value is computed here and none flows
 * back — if the renderer stops, the game is unaffected.
 *
 * ## Failure
 *
 * Every GL step that can fail is checked, and the first failure calls
 * [onFailure] once and leaves the surface blank, which is the signal for
 * `GlobeSurface` to swap in the 2D fallback. A shader that will not compile
 * costs the player the globe, never the game.
 */
class GlobeRenderer(
    private val onFailure: () -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile
    var scene: GlobeScene = GlobeScene()

    /**
     * Where each storm ended up on screen this frame, for hit-testing taps.
     *
     * Published by the renderer rather than recomputed in Compose because the
     * renderer is the only thing that knows the live camera — the eased yaw,
     * the pitch, the zoom. A tap therefore lands on the storm the player can
     * actually see, not on where an independent copy of the camera maths
     * thinks it should be.
     */
    @Volatile
    var stormHitTargets: List<StormHitTarget> = emptyList()

    // --- camera, written from the UI thread by the gesture handlers ---

    @Volatile
    private var dragYawDegrees = 0f

    @Volatile
    private var pitchDegrees = 12f

    @Volatile
    private var zoom = 1f

    @Volatile
    private var interacting = false

    /** Set once something has gone wrong; every later frame is then a no-op. */
    @Volatile
    private var failed = false

    private var notifiedFailure = false

    // --- GL objects ---

    private var mesh: SphereMesh? = null
    private var planetProgram = 0
    private var cloudProgram = 0
    private var atmosphereProgram = 0
    private var markerProgram = 0
    private var earthTexture = 0
    private var cloudTexture = 0

    private var earthBitmap: Bitmap? = null
    private var cloudBitmap: Bitmap? = null

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val modelView = FloatArray(16)
    private val mvp = FloatArray(16)
    private val normalMatrix = FloatArray(9)
    private val scratch = FloatArray(16)
    private val shellMvp = FloatArray(16)
    private val point = FloatArray(3)

    // Scratch for the screen-space projection. Hoisted out of the frame so the
    // render loop allocates nothing at all — six storms a frame at 60 Hz is a
    // few hundred short-lived arrays a second otherwise.
    private val projectionScratch = FloatArray(4)
    private val projectionInput = FloatArray(4)
    private val projectedNormal = FloatArray(3)

    private var viewportWidth = 1
    private var viewportHeight = 1
    private var maxPointSize = 64f

    private var lastFrameNanos = 0L
    private var elapsedSeconds = 0f

    /** Smoothed camera targets, so a tap on a storm glides rather than snaps. */
    private var smoothedYaw = 0f
    private var smoothedPitch = 12f
    private var smoothedZoom = 1f

    private val wind = WindParticles()
    private var markerBuffer: FloatBuffer = allocateMarkerBuffer(MAX_MARKERS)

    // ------------------------------------------------------- UI thread API --

    fun applyDrag(deltaXDp: Float, deltaYDp: Float) {
        dragYawDegrees -= deltaXDp * DRAG_DEGREES_PER_DP
        pitchDegrees = (pitchDegrees + deltaYDp * DRAG_DEGREES_PER_DP).coerceIn(-78f, 78f)
    }

    fun applyZoom(factor: Float) {
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun setInteracting(value: Boolean) {
        interacting = value
    }

    // ------------------------------------------------------- GL lifecycle --

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        try {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            GLES20.glCullFace(GLES20.GL_BACK)

            val range = FloatArray(2)
            GLES20.glGetFloatv(GLES20.GL_ALIASED_POINT_SIZE_RANGE, range, 0)
            if (range[1] > 1f) maxPointSize = range[1]

            mesh = SphereMesh(segments = 48, rings = 32)

            planetProgram = buildProgram(GlobeShaders.SHELL_VERTEX, GlobeShaders.PLANET_FRAGMENT)
            cloudProgram = buildProgram(GlobeShaders.SHELL_VERTEX, GlobeShaders.CLOUD_FRAGMENT)
            atmosphereProgram = buildProgram(GlobeShaders.SHELL_VERTEX, GlobeShaders.ATMOSPHERE_FRAGMENT)
            markerProgram = buildProgram(GlobeShaders.MARKER_VERTEX, GlobeShaders.MARKER_FRAGMENT)

            // Generating the surface is tens of milliseconds and happens once.
            // It runs here, on the GL thread, rather than the main thread: the
            // first frame is a moment late, and no frame the player is looking
            // at is ever dropped for it.
            earthBitmap = EarthSurface.createEarthBitmap()
            cloudBitmap = EarthSurface.createCloudBitmap()
            earthTexture = uploadTexture(earthBitmap!!)
            cloudTexture = uploadTexture(cloudBitmap!!)
        } catch (error: Exception) {
            fail("globe initialisation failed", error)
        } catch (error: OutOfMemoryError) {
            fail("not enough memory for the globe's textures", error)
        }
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        viewportWidth = max(1, width)
        viewportHeight = max(1, height)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)

        val aspect = viewportWidth.toFloat() / viewportHeight
        Matrix.perspectiveM(projection, 0, FIELD_OF_VIEW_DEGREES, aspect, 0.5f, 12f)
    }

    override fun onDrawFrame(unused: GL10?) {
        if (failed) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            return
        }
        try {
            drawFrame()
        } catch (error: Exception) {
            fail("globe frame failed", error)
        }
    }

    private fun drawFrame() {
        val currentMesh = mesh ?: return
        val current = scene
        val environment = current.environment
        val animate = if (current.reducedMotion || current.quality == GraphicsQuality.LOW) 0f else 1f

        val now = System.nanoTime()
        val deltaSeconds = if (lastFrameNanos == 0L) {
            0f
        } else {
            ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
        }
        lastFrameNanos = now
        elapsedSeconds += deltaSeconds * animate

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // --- camera ---------------------------------------------------------
        //
        // The planet's own turn comes from the simulated clock, so the day/night
        // line is where the *simulation* says it is; the player's drag is an
        // offset on top of it. Auto-rotation stops while a finger is down and
        // while the quality setting says the globe should hold still.
        val simulationYaw = if (current.quality.autoRotate && !interacting) {
            environment.rotationPhase * 360f
        } else {
            autoRotationHold
        }
        if (current.quality.autoRotate && !interacting) autoRotationHold = simulationYaw

        var targetYaw = simulationYaw + dragYawDegrees
        var targetPitch = pitchDegrees
        var targetZoom = zoom

        // A selected storm pulls the camera gently round to face it, rather
        // than the camera being snatched every time the weather changes.
        current.focusStormId?.let { id ->
            environment.storms.firstOrNull { it.id == id }?.let { storm ->
                if (!interacting) {
                    targetYaw = -storm.longitudeDeg
                    targetPitch = storm.latitudeDeg.coerceIn(-60f, 60f)
                    targetZoom = max(targetZoom, 1.25f)
                }
            }
        }

        val ease = if (animate == 0f) 1f else min(1f, deltaSeconds * CAMERA_EASE_PER_SECOND)
        smoothedYaw += shortestAngle(smoothedYaw, targetYaw) * ease
        smoothedPitch += (targetPitch - smoothedPitch) * ease
        smoothedZoom += (targetZoom - smoothedZoom) * ease

        val distance = CAMERA_DISTANCE / smoothedZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        Matrix.setLookAtM(view, 0, 0f, 0f, distance, 0f, 0f, 0f, 0f, 1f, 0f)

        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, smoothedPitch, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, smoothedYaw, 0f, 1f, 0f)

        Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)
        extractNormalMatrix(model, normalMatrix)

        // --- the planet ------------------------------------------------------
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        GLES20.glUseProgram(planetProgram)
        bindShellAttributes(planetProgram, currentMesh)
        setMatrices(planetProgram, mvp, normalMatrix)
        GLES20.glUniform1f(uniform(planetProgram, "uTextureOffset"), 0f)
        GLES20.glUniform3f(uniform(planetProgram, "uLightDirection"), LIGHT[0], LIGHT[1], LIGHT[2])
        GLES20.glUniform1f(uniform(planetProgram, "uTemperatureScale"), environment.temperatureScale)
        GLES20.glUniform1f(uniform(planetProgram, "uHumidity"), environment.humidity)
        GLES20.glUniform1f(uniform(planetProgram, "uAtmosphericOpacity"), environment.atmosphericOpacity)
        GLES20.glUniform1f(uniform(planetProgram, "uHabitability"), environment.habitability)
        GLES20.glUniform1f(uniform(planetProgram, "uNightLights"), current.nightLights)
        GLES20.glUniform1f(uniform(planetProgram, "uCollapsed"), if (environment.collapsed) 1f else 0f)
        GLES20.glUniform1f(
            uniform(planetProgram, "uOverlayTemperature"),
            if (current.overlay == EnvironmentOverlay.TEMPERATURE) 1f else 0f,
        )
        GLES20.glUniform1f(
            uniform(planetProgram, "uOverlayHumidity"),
            if (current.overlay == EnvironmentOverlay.HUMIDITY) 1f else 0f,
        )
        GLES20.glUniform1f(
            uniform(planetProgram, "uOverlayAtmosphere"),
            if (current.overlay == EnvironmentOverlay.ATMOSPHERE) 1f else 0f,
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, earthTexture)
        GLES20.glUniform1i(uniform(planetProgram, "uTexture"), 0)
        drawMesh(currentMesh)

        // --- the cloud shell -------------------------------------------------
        if (current.quality.cloudsEnabled && environment.cloudCoverage > 0.01f) {
            Matrix.setIdentityM(scratch, 0)
            Matrix.scaleM(scratch, 0, CLOUD_SCALE, CLOUD_SCALE, CLOUD_SCALE)
            Matrix.multiplyMM(scratch, 0, model, 0, scratch, 0)
            Matrix.multiplyMM(modelView, 0, view, 0, scratch, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)

            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glDepthMask(false)

            GLES20.glUseProgram(cloudProgram)
            bindShellAttributes(cloudProgram, currentMesh)
            setMatrices(cloudProgram, mvp, normalMatrix)
            // Clouds drift relative to the surface, at a rate set by the
            // simulated wind rather than by a magic number.
            GLES20.glUniform1f(
                uniform(cloudProgram, "uTextureOffset"),
                (elapsedSeconds * 0.006f * (0.3f + environment.windStrength)) % 1f,
            )
            GLES20.glUniform3f(uniform(cloudProgram, "uLightDirection"), LIGHT[0], LIGHT[1], LIGHT[2])
            GLES20.glUniform1f(uniform(cloudProgram, "uCoverage"), environment.cloudCoverage)
            GLES20.glUniform1f(uniform(cloudProgram, "uTint"), environment.atmosphericOpacity)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cloudTexture)
            GLES20.glUniform1i(uniform(cloudProgram, "uTexture"), 0)
            drawMesh(currentMesh)

            // Restore the planet's matrices for the shells that follow.
            Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)
        }

        // --- the atmosphere ---------------------------------------------------
        run {
            Matrix.setIdentityM(scratch, 0)
            Matrix.scaleM(scratch, 0, ATMOSPHERE_SCALE, ATMOSPHERE_SCALE, ATMOSPHERE_SCALE)
            Matrix.multiplyMM(scratch, 0, model, 0, scratch, 0)
            Matrix.multiplyMM(modelView, 0, view, 0, scratch, 0)
            Matrix.multiplyMM(shellMvp, 0, projection, 0, modelView, 0)

            GLES20.glEnable(GLES20.GL_BLEND)
            // Additive: a glow adds light, it does not paint over what is behind.
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
            GLES20.glDepthMask(false)

            GLES20.glUseProgram(atmosphereProgram)
            bindShellAttributes(atmosphereProgram, currentMesh)
            setMatrices(atmosphereProgram, shellMvp, normalMatrix)
            GLES20.glUniform1f(uniform(atmosphereProgram, "uTextureOffset"), 0f)
            GLES20.glUniform3f(
                uniform(atmosphereProgram, "uLightDirection"),
                LIGHT[0], LIGHT[1], LIGHT[2],
            )
            GLES20.glUniform3f(uniform(atmosphereProgram, "uViewDirection"), 0f, 0f, 1f)
            // The overlay that is about the atmosphere makes the atmosphere
            // louder; the rest of the time it is a rim light.
            val emphasis = if (current.overlay == EnvironmentOverlay.ATMOSPHERE) 1.55f else 1f
            GLES20.glUniform1f(
                uniform(atmosphereProgram, "uStrength"),
                (0.42f + 0.95f * environment.atmosphericOpacity) * emphasis,
            )
            GLES20.glUniform1f(uniform(atmosphereProgram, "uOpacity"), environment.atmosphericOpacity)
            drawMesh(currentMesh)

            Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)
        }

        // --- markers and particles --------------------------------------------
        drawMarkers(current, deltaSeconds * animate)

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private var autoRotationHold = 0f

    /**
     * Storms, event markers and wind streamlines, in a single point-sprite
     * draw call.
     *
     * The buffer is rebuilt each frame rather than kept in GPU memory: it is a
     * couple of hundred points, the contents change every frame anyway, and
     * one `glBufferSubData` of this size costs less than the bookkeeping to
     * avoid it.
     */
    private fun drawMarkers(current: GlobeScene, deltaSeconds: Float) {
        val environment = current.environment
        val showStorms = current.overlay.showsStorms
        val showEvents = current.overlay.showsEvents
        val showWind = current.overlay == EnvironmentOverlay.WIND

        val budget = if (showWind) current.quality.windParticleBudget else 0
        wind.update(budget, environment.windStrength, deltaSeconds)

        markerBuffer.clear()
        var count = 0

        if (showWind) {
            val alpha = 0.25f + 0.55f * environment.windStrength
            for (index in 0 until wind.activeCount) {
                if (count >= MAX_MARKERS) break
                latLonToUnitSphere(wind.latitude[index], wind.longitude[index], point)
                putMarker(
                    x = point[0] * MARKER_RADIUS,
                    y = point[1] * MARKER_RADIUS,
                    z = point[2] * MARKER_RADIUS,
                    r = 0.72f, g = 0.88f, b = 1f,
                    a = alpha * wind.fade(index),
                    size = 3.5f + 4f * environment.windStrength,
                    kind = KIND_WIND,
                )
                count++
            }
        }

        if (showEvents) {
            for (event in environment.events) {
                if (count >= MAX_MARKERS) break
                latLonToUnitSphere(event.latitudeDeg, event.longitudeDeg, point)
                val warm = if (event.isNegative) 1f else 0.36f
                putMarker(
                    x = point[0] * MARKER_RADIUS,
                    y = point[1] * MARKER_RADIUS,
                    z = point[2] * MARKER_RADIUS,
                    r = warm, g = if (event.isNegative) 0.42f else 0.86f, b = if (event.isNegative) 0.30f else 0.62f,
                    a = 1f,
                    size = 26f,
                    kind = KIND_EVENT,
                )
                count++
            }
        }

        val hits = if (showStorms) ArrayList<StormHitTarget>(environment.storms.size) else null

        if (showStorms) {
            for (storm in environment.storms) {
                if (count >= MAX_MARKERS) break
                latLonToUnitSphere(storm.latitudeDeg, storm.longitudeDeg, point)
                val selected = storm.id == current.focusStormId
                // Angular radius to screen pixels, through the same projection
                // the planet is drawn with, so a storm's marker is the size of
                // the footprint the simulation gave it.
                val sizePixels = angularRadiusToPixels(storm.radiusDeg) * if (selected) 1.18f else 1f
                putMarker(
                    x = point[0] * MARKER_RADIUS,
                    y = point[1] * MARKER_RADIUS,
                    z = point[2] * MARKER_RADIUS,
                    r = 0.93f, g = 0.95f, b = 1f,
                    // The alpha channel carries intensity into the shader,
                    // which uses it for brightness, spin rate and lightning.
                    a = storm.intensity.coerceIn(0.05f, 1f),
                    size = sizePixels,
                    kind = KIND_STORM,
                )
                count++

                hits?.add(
                    projectToScreen(
                        id = storm.id,
                        x = point[0] * MARKER_RADIUS,
                        y = point[1] * MARKER_RADIUS,
                        z = point[2] * MARKER_RADIUS,
                        // A generous target: the sprite is a spiral with a lot
                        // of empty space in it, and a thumb is not precise.
                        radiusPixels = max(sizePixels * 0.6f, MIN_TAP_RADIUS_PX),
                    ),
                )
            }
        }

        stormHitTargets = hits.orEmpty()

        if (count == 0) return
        markerBuffer.position(0)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        GLES20.glUseProgram(markerProgram)
        val stride = MARKER_FLOATS * 4

        val positionHandle = GLES20.glGetAttribLocation(markerProgram, "aPosition")
        val colorHandle = GLES20.glGetAttribLocation(markerProgram, "aColor")
        val sizeHandle = GLES20.glGetAttribLocation(markerProgram, "aSize")
        val kindHandle = GLES20.glGetAttribLocation(markerProgram, "aKind")

        markerBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, stride, markerBuffer)
        markerBuffer.position(3)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, stride, markerBuffer)
        markerBuffer.position(7)
        GLES20.glEnableVertexAttribArray(sizeHandle)
        GLES20.glVertexAttribPointer(sizeHandle, 1, GLES20.GL_FLOAT, false, stride, markerBuffer)
        markerBuffer.position(8)
        GLES20.glEnableVertexAttribArray(kindHandle)
        GLES20.glVertexAttribPointer(kindHandle, 1, GLES20.GL_FLOAT, false, stride, markerBuffer)

        GLES20.glUniformMatrix4fv(uniform(markerProgram, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniformMatrix3fv(uniform(markerProgram, "uNormalMatrix"), 1, false, normalMatrix, 0)
        GLES20.glUniform3f(uniform(markerProgram, "uViewDirection"), 0f, 0f, 1f)
        GLES20.glUniform1f(uniform(markerProgram, "uPointScale"), 1f)
        GLES20.glUniform1f(uniform(markerProgram, "uTime"), elapsedSeconds)
        GLES20.glUniform1f(
            uniform(markerProgram, "uAnimate"),
            if (current.quality.animatedStorms && !current.reducedMotion) 1f else 0f,
        )

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
        GLES20.glDisableVertexAttribArray(sizeHandle)
        GLES20.glDisableVertexAttribArray(kindHandle)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    /**
     * A point on the unit sphere, as a viewport pixel position.
     *
     * `visible` is false once the point has gone round the back of the globe,
     * so a tap can never select a storm the player cannot see.
     */
    private fun projectToScreen(
        id: String,
        x: Float,
        y: Float,
        z: Float,
        radiusPixels: Float,
    ): StormHitTarget {
        projectionInput[0] = x
        projectionInput[1] = y
        projectionInput[2] = z
        projectionInput[3] = 1f
        Matrix.multiplyMV(projectionScratch, 0, mvp, 0, projectionInput, 0)
        val w = if (abs(projectionScratch[3]) < 1e-6f) 1e-6f else projectionScratch[3]
        val ndcX = projectionScratch[0] / w
        val ndcY = projectionScratch[1] / w

        projectedNormal[0] = normalMatrix[0] * x + normalMatrix[3] * y + normalMatrix[6] * z
        projectedNormal[1] = normalMatrix[1] * x + normalMatrix[4] * y + normalMatrix[7] * z
        projectedNormal[2] = normalMatrix[2] * x + normalMatrix[5] * y + normalMatrix[8] * z

        return StormHitTarget(
            id = id,
            // GL's y climbs upward and Compose's downward, hence the flip.
            x = (ndcX * 0.5f + 0.5f) * viewportWidth,
            y = (0.5f - ndcY * 0.5f) * viewportHeight,
            radiusPixels = radiusPixels,
            visible = projectedNormal[2] > 0f,
        )
    }

    /** A storm's angular footprint in degrees, as a point-sprite size in pixels. */
    private fun angularRadiusToPixels(radiusDegrees: Float): Float {
        val halfFov = Math.toRadians(FIELD_OF_VIEW_DEGREES / 2.0)
        val distance = CAMERA_DISTANCE / smoothedZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val worldHalfHeight = kotlin.math.tan(halfFov) * distance
        val worldRadius = sin(Math.toRadians(radiusDegrees.toDouble())).toFloat()
        val pixels = worldRadius / worldHalfHeight.toFloat() * viewportHeight
        // Doubled because a point sprite's size is its full width, not a radius.
        return (pixels * 2f).coerceIn(10f, maxPointSize)
    }

    private fun putMarker(
        x: Float, y: Float, z: Float,
        r: Float, g: Float, b: Float, a: Float,
        size: Float,
        kind: Float,
    ) {
        markerBuffer.put(x); markerBuffer.put(y); markerBuffer.put(z)
        markerBuffer.put(r); markerBuffer.put(g); markerBuffer.put(b); markerBuffer.put(a)
        markerBuffer.put(size)
        markerBuffer.put(kind)
    }

    // ------------------------------------------------------------ plumbing --

    private fun bindShellAttributes(program: Int, currentMesh: SphereMesh) {
        val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        val normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        val texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")

        currentMesh.vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle, 3, GLES20.GL_FLOAT, false,
            SphereMesh.STRIDE_BYTES, currentMesh.vertexBuffer,
        )

        if (normalHandle >= 0) {
            currentMesh.vertexBuffer.position(3)
            GLES20.glEnableVertexAttribArray(normalHandle)
            GLES20.glVertexAttribPointer(
                normalHandle, 3, GLES20.GL_FLOAT, false,
                SphereMesh.STRIDE_BYTES, currentMesh.vertexBuffer,
            )
        }

        if (texHandle >= 0) {
            currentMesh.vertexBuffer.position(6)
            GLES20.glEnableVertexAttribArray(texHandle)
            GLES20.glVertexAttribPointer(
                texHandle, 2, GLES20.GL_FLOAT, false,
                SphereMesh.STRIDE_BYTES, currentMesh.vertexBuffer,
            )
        }
        currentMesh.vertexBuffer.position(0)
    }

    private fun drawMesh(currentMesh: SphereMesh) {
        currentMesh.indexBuffer.position(0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            currentMesh.indexCount,
            GLES20.GL_UNSIGNED_SHORT,
            currentMesh.indexBuffer,
        )
    }

    private fun setMatrices(program: Int, matrix: FloatArray, normals: FloatArray) {
        GLES20.glUniformMatrix4fv(uniform(program, "uMvp"), 1, false, matrix, 0)
        GLES20.glUniformMatrix3fv(uniform(program, "uNormalMatrix"), 1, false, normals, 0)
    }

    private val uniformCache = HashMap<Long, Int>()

    private fun uniform(program: Int, name: String): Int {
        val key = program.toLong() shl 32 or name.hashCode().toLong().and(0xFFFFFFFFL)
        return uniformCache.getOrPut(key) { GLES20.glGetUniformLocation(program, name) }
    }

    private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        check(program != 0) { "could not create a GL program" }
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)

        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { "shader link failed: ${GLES20.glGetProgramInfoLog(program)}" }

        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "could not create a GL shader" }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("shader compile failed: $log")
        }
        return shader
    }

    private fun uploadTexture(bitmap: Bitmap): Int {
        val handles = IntArray(1)
        GLES20.glGenTextures(1, handles, 0)
        check(handles[0] != 0) { "could not allocate a GL texture" }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, handles[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        // Wrapping in longitude, clamping in latitude: the texture is a
        // cylinder, not a torus.
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return handles[0]
    }

    private fun fail(message: String, error: Throwable) {
        if (failed) return
        failed = true
        Log.w(TAG, message, error)
        if (!notifiedFailure) {
            notifiedFailure = true
            onFailure()
        }
    }

    /** The upper-left 3×3 of a pure rotation, which is its own normal matrix. */
    private fun extractNormalMatrix(source: FloatArray, out: FloatArray) {
        out[0] = source[0]; out[1] = source[1]; out[2] = source[2]
        out[3] = source[4]; out[4] = source[5]; out[5] = source[6]
        out[6] = source[8]; out[7] = source[9]; out[8] = source[10]
    }

    /** The shorter way round from [from] to [to], so 350° to 10° is +20, not −340. */
    private fun shortestAngle(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    private companion object {
        const val TAG = "GlobeRenderer"

        const val FIELD_OF_VIEW_DEGREES = 42f
        const val CAMERA_DISTANCE = 3.1f
        const val MIN_ZOOM = 0.85f
        const val MAX_ZOOM = 2.2f
        const val DRAG_DEGREES_PER_DP = 0.32f
        const val CAMERA_EASE_PER_SECOND = 6f

        const val CLOUD_SCALE = 1.018f
        const val ATMOSPHERE_SCALE = 1.13f
        const val MARKER_RADIUS = 1.012f

        /** Wind budget plus storms plus events, with room to spare. */
        const val MAX_MARKERS = 320
        const val MARKER_FLOATS = 9

        /** Nothing smaller than this is a fair tap target on a phone. */
        const val MIN_TAP_RADIUS_PX = 40f

        const val KIND_WIND = 0f
        const val KIND_EVENT = 1f
        const val KIND_STORM = 2f

        /** A fixed sun, in world space, so turning the planet moves the terminator. */
        val LIGHT = floatArrayOf(0.62f, 0.30f, 0.72f)

        fun allocateMarkerBuffer(markers: Int): FloatBuffer = ByteBuffer
            .allocateDirect(markers * MARKER_FLOATS * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }
}

/** Where one storm is on screen, so a tap can find it. */
data class StormHitTarget(
    val id: String,
    val x: Float,
    val y: Float,
    val radiusPixels: Float,
    val visible: Boolean,
)

/**
 * The wind streamlines.
 *
 * A bounded pool of particles — never more than the quality setting's budget —
 * advected by [windAt], the **same** zonal wind field the storm simulation
 * steers storms with. The visualization is therefore a reading of the
 * environment model rather than an animation that happens to look windy: if
 * the planet's wind strengthens, these speed up because the model says so.
 *
 * Particles are seeded from a fixed deterministic generator rather than an
 * unseeded `Random`, so no randomness the player can see is ever unreproducible
 * — the same rule the simulation follows.
 */
private class WindParticles {

    val latitude = FloatArray(MAX)
    val longitude = FloatArray(MAX)
    private val age = FloatArray(MAX)
    private val lifetime = FloatArray(MAX)

    var activeCount = 0
        private set

    private val random = DeterministicRandom(SEED)

    fun fade(index: Int): Float {
        val life = lifetime[index]
        if (life <= 0f) return 0f
        val t = (age[index] / life).coerceIn(0f, 1f)
        // Fade in and out so particles appear and vanish rather than popping.
        return min(1f, min(t * 6f, (1f - t) * 4f))
    }

    fun update(budget: Int, strength: Float, deltaSeconds: Float) {
        val target = budget.coerceIn(0, MAX)
        while (activeCount < target) {
            respawn(activeCount)
            // Stagger the first generation's ages so they do not all die at once.
            age[activeCount] = (random.nextDouble() * lifetime[activeCount]).toFloat()
            activeCount++
        }
        if (activeCount > target) activeCount = target
        if (deltaSeconds <= 0f || strength <= 0f) return

        for (index in 0 until activeCount) {
            age[index] += deltaSeconds
            if (age[index] >= lifetime[index]) {
                respawn(index)
                continue
            }
            val sample = windAt(latitude[index].toDouble(), strength.toDouble())
            val scale = DEGREES_PER_SECOND * deltaSeconds
            val latitudeNext = latitude[index] + (sample.northward * scale).toFloat()
            // Longitude closes up toward the poles; the floor keeps the
            // correction finite where it would otherwise diverge.
            val convergence = 1f / max(0.25f, cos(Math.toRadians(latitude[index].toDouble())).toFloat())
            latitude[index] = latitudeNext.coerceIn(-88f, 88f)
            longitude[index] = wrapLongitude(
                (longitude[index] + (sample.eastward * scale).toFloat() * convergence).toDouble(),
            ).toFloat()
            if (abs(latitude[index]) >= 87f) respawn(index)
        }
    }

    private fun respawn(index: Int) {
        // Weighted away from the poles, where the projection crowds them
        // together and they read as a smear rather than a flow.
        latitude[index] = (random.nextDouble(-1.0, 1.0).let { it * it * it * 82.0 + it * 8.0 })
            .coerceIn(-85.0, 85.0).toFloat()
        longitude[index] = random.nextDouble(-180.0, 180.0).toFloat()
        age[index] = 0f
        lifetime[index] = random.nextDouble(4.0, 11.0).toFloat()
    }

    private companion object {
        const val MAX = 256
        const val DEGREES_PER_SECOND = 14f
        const val SEED = 0x57494E44L
    }
}
