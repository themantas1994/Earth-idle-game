package com.earthgame.idle.presentation.globe

/**
 * Every shader the globe uses.
 *
 * All of them are OpenGL ES 2.0 / GLSL ES 1.00 — the version guaranteed on
 * every device the app supports (`minSdk 24`), and deliberately not ES 3.0,
 * which would add an install filter for a feature the rest of the game does
 * not need. See `docs/wiki/Environmental-Visualization.md` for why the globe
 * is drawn directly rather than through a scene-graph library.
 *
 * The overlays live here rather than in geometry: switching from the
 * temperature view to the humidity view changes a handful of uniform floats,
 * not the scene. That is what lets the Home screen rebuild its render state
 * four times a second without ever rebuilding the scene.
 */
internal object GlobeShaders {

    /** Shared by the planet, the cloud shell and the atmosphere shell. */
    const val SHELL_VERTEX = """
        uniform mat4 uMvp;
        uniform mat3 uNormalMatrix;
        uniform float uTextureOffset;

        attribute vec3 aPosition;
        attribute vec3 aNormal;
        attribute vec2 aTexCoord;

        varying vec2 vUv;
        varying vec3 vNormal;
        varying vec3 vModelNormal;

        void main() {
            vUv = vec2(aTexCoord.x + uTextureOffset, aTexCoord.y);
            vNormal = normalize(uNormalMatrix * aNormal);
            vModelNormal = aNormal;
            gl_Position = uMvp * vec4(aPosition, 1.0);
        }
    """

    /**
     * The planet.
     *
     * Reads the generated surface texture, lights it against a fixed sun, and
     * then applies whichever environmental overlay the player has selected.
     * Every overlay weight arrives as a uniform in 0..1, and every one of them
     * is a **simulation value** mapped onto a gameplay range — nothing here
     * invents a number to draw.
     */
    const val PLANET_FRAGMENT = """
        precision mediump float;

        varying vec2 vUv;
        varying vec3 vNormal;
        varying vec3 vModelNormal;

        uniform sampler2D uTexture;
        uniform vec3 uLightDirection;

        uniform float uTemperatureScale;
        uniform float uHumidity;
        uniform float uAtmosphericOpacity;
        uniform float uHabitability;
        uniform float uNightLights;
        uniform float uCollapsed;

        uniform float uOverlayTemperature;
        uniform float uOverlayHumidity;
        uniform float uOverlayAtmosphere;

        void main() {
            vec4 surface = texture2D(uTexture, vUv);
            vec3 color = surface.rgb;

            // Land reads as "greener than it is blue"; ocean and ice do not.
            // Used for the night-side city lights and for keeping the humidity
            // overlay off the sea, where it would say nothing.
            float land = clamp((surface.g - surface.b) * 6.0, 0.0, 1.0);

            // The pole-to-equator position, straight off the model normal.
            float absLatitude = abs(vModelNormal.y);

            // --- Temperature overlay -------------------------------------
            // Cool -> neutral -> warm -> extreme, hotter toward the equator,
            // driven by the simulation's own temperature anomaly.
            if (uOverlayTemperature > 0.0) {
                float equatorBias = 1.0 - absLatitude * 0.7;
                float heat = clamp(uTemperatureScale * equatorBias * 1.4, 0.0, 1.0);
                vec3 cool = vec3(0.20, 0.47, 0.86);
                vec3 neutral = vec3(0.30, 0.74, 0.58);
                vec3 warm = vec3(0.97, 0.71, 0.16);
                vec3 extreme = vec3(0.93, 0.20, 0.11);
                vec3 ramp;
                if (heat < 0.34) {
                    ramp = mix(cool, neutral, heat / 0.34);
                } else if (heat < 0.67) {
                    ramp = mix(neutral, warm, (heat - 0.34) / 0.33);
                } else {
                    ramp = mix(warm, extreme, (heat - 0.67) / 0.33);
                }
                color = mix(color, ramp, uOverlayTemperature * 0.72);
            }

            // --- Humidity overlay ----------------------------------------
            // A pale vapour wash, banded to match the circulation the wind
            // model uses, and strongest where the simulated water vapour is.
            if (uOverlayHumidity > 0.0) {
                float band = 0.45 + 0.55 * pow(1.0 - absLatitude, 1.5);
                float wet = clamp((uHumidity - 0.45) / 0.45, 0.0, 1.0) * band;
                vec3 vapour = mix(vec3(0.35, 0.55, 0.72), vec3(0.86, 0.94, 1.0), wet);
                color = mix(color, vapour, uOverlayHumidity * (0.30 + 0.45 * wet));
            }

            // --- Atmosphere overlay --------------------------------------
            // The greenhouse blanket seen from inside: a warm haze that
            // thickens with total radiative forcing.
            if (uOverlayAtmosphere > 0.0) {
                vec3 haze = mix(vec3(0.55, 0.72, 0.85), vec3(0.85, 0.55, 0.28), uAtmosphericOpacity);
                color = mix(color, haze, uOverlayAtmosphere * (0.12 + 0.45 * uAtmosphericOpacity));
            }

            // --- Lighting -------------------------------------------------
            float incidence = dot(normalize(vNormal), normalize(uLightDirection));
            float daylight = smoothstep(-0.12, 0.32, incidence);

            vec3 lit = color * (0.28 + 0.88 * max(incidence, 0.0));
            // A warm band right at the terminator, which is what makes the
            // day/night line read as a sunrise rather than a seam.
            lit += vec3(0.36, 0.18, 0.05) * (1.0 - abs(incidence * 3.0)) * step(abs(incidence), 0.34);

            vec3 night = color * 0.11 + vec3(1.0, 0.80, 0.42) * land * uNightLights * 0.40;
            color = mix(night, lit, daylight);

            // A dying planet loses its colour before it loses its player.
            float bleach = uCollapsed * 0.65 + (1.0 - uHabitability) * 0.25;
            float grey = dot(color, vec3(0.299, 0.587, 0.114));
            color = mix(color, mix(vec3(grey), vec3(0.42, 0.13, 0.08), uCollapsed * 0.5), clamp(bleach, 0.0, 1.0));

            gl_FragColor = vec4(color, 1.0);
        }
    """

    /**
     * The cloud shell: the same sphere a little larger, with the generated
     * cloud alpha map on it, turning at its own rate.
     *
     * Its opacity is the simulated cloud coverage, which is the simulated
     * humidity, which is the simulated water vapour — so a drying planet
     * visibly clears and a humid one closes over.
     */
    const val CLOUD_FRAGMENT = """
        precision mediump float;

        varying vec2 vUv;
        varying vec3 vNormal;

        uniform sampler2D uTexture;
        uniform vec3 uLightDirection;
        uniform float uCoverage;
        uniform float uTint;

        void main() {
            float density = texture2D(uTexture, vUv).a * uCoverage;
            if (density <= 0.004) discard;

            float incidence = dot(normalize(vNormal), normalize(uLightDirection));
            float daylight = smoothstep(-0.15, 0.35, incidence);
            vec3 cloud = mix(vec3(0.30, 0.33, 0.40), vec3(1.0, 0.99, 0.97), daylight);
            // Storm-laden skies bruise toward the atmosphere overlay's colour.
            cloud = mix(cloud, vec3(0.92, 0.74, 0.55), uTint * 0.5);

            gl_FragColor = vec4(cloud, density * 0.85);
        }
    """

    /**
     * The atmospheric rim.
     *
     * A slightly larger sphere drawn additively, with alpha driven by how
     * edge-on the surface is — so it contributes almost nothing over the
     * planet's face and piles up into a halo at the silhouette. Its strength
     * and colour come from the simulated greenhouse forcing: a pristine Earth
     * gets a thin blue line, a cooked one a thick orange corona.
     */
    const val ATMOSPHERE_FRAGMENT = """
        precision mediump float;

        varying vec3 vNormal;

        uniform vec3 uLightDirection;
        uniform float uStrength;
        uniform float uOpacity;
        uniform vec3 uViewDirection;

        void main() {
            vec3 normal = normalize(vNormal);
            float facing = abs(dot(normal, normalize(uViewDirection)));
            float rim = pow(1.0 - facing, 2.6);

            float incidence = dot(normal, normalize(uLightDirection));
            float daylight = 0.32 + 0.68 * smoothstep(-0.4, 0.4, incidence);

            vec3 clean = vec3(0.35, 0.62, 1.0);
            vec3 choked = vec3(1.0, 0.55, 0.22);
            vec3 color = mix(clean, choked, uOpacity);

            gl_FragColor = vec4(color * rim * daylight * uStrength, 1.0);
        }
    """

    /**
     * Markers and particles: storms, events, and wind streamlines, all drawn
     * as point sprites in one pass.
     *
     * One draw call for the lot. `aKind` selects what the fragment shader
     * paints, and `aFade` carries how far around the globe the point is so
     * anything on the far side fades out instead of showing through.
     */
    const val MARKER_VERTEX = """
        uniform mat4 uMvp;
        uniform mat3 uNormalMatrix;
        uniform vec3 uViewDirection;
        uniform float uPointScale;

        attribute vec3 aPosition;
        attribute vec4 aColor;
        attribute float aSize;
        attribute float aKind;

        varying vec4 vColor;
        varying float vKind;
        varying float vFade;

        void main() {
            vec3 worldNormal = normalize(uNormalMatrix * normalize(aPosition));
            // 1 on the near face, 0 once the point has gone round the back.
            vFade = smoothstep(-0.05, 0.35, dot(worldNormal, normalize(uViewDirection)));
            vColor = aColor;
            vKind = aKind;
            gl_Position = uMvp * vec4(aPosition, 1.0);
            gl_PointSize = max(aSize * uPointScale, 1.0);
        }
    """

    const val MARKER_FRAGMENT = """
        precision mediump float;

        varying vec4 vColor;
        varying float vKind;
        varying float vFade;

        uniform float uTime;
        uniform float uAnimate;

        void main() {
            if (vFade <= 0.01) discard;

            vec2 offset = gl_PointCoord * 2.0 - 1.0;
            float radius = length(offset);
            if (radius > 1.0) discard;

            float alpha;
            vec3 color = vColor.rgb;

            if (vKind > 1.5) {
                // A storm: a rotating spiral with a bright eye wall, its
                // rotation rate and brightness both set by its intensity
                // (carried in the alpha channel by the renderer).
                float angle = atan(offset.y, offset.x);
                float spin = uTime * (1.4 + 2.6 * vColor.a) * uAnimate;
                float spiral = sin(angle * 2.0 + radius * 9.0 - spin);
                float arms = smoothstep(0.05, 0.85, spiral) * (1.0 - smoothstep(0.15, 1.0, radius));
                float eye = 1.0 - smoothstep(0.0, 0.20, radius);
                float halo = (1.0 - smoothstep(0.55, 1.0, radius)) * 0.22;
                alpha = clamp(arms * 0.80 + eye * 0.95 + halo, 0.0, 1.0);
                // The strongest storms flash.
                float lightning = step(0.94, fract(uTime * 1.7 + vColor.a * 7.0)) * uAnimate;
                color = mix(color, vec3(1.0), lightning * vColor.a * 0.8 * eye);
                alpha *= 0.45 + 0.55 * vColor.a;
            } else if (vKind > 0.5) {
                // An event: a pulsing ring, so it reads differently from a storm.
                float pulse = 0.5 + 0.5 * sin(uTime * 2.6 * uAnimate);
                float ring = 1.0 - smoothstep(0.05, 0.22, abs(radius - (0.45 + 0.25 * pulse)));
                float core = 1.0 - smoothstep(0.0, 0.28, radius);
                alpha = clamp(ring * 0.85 + core * 0.9, 0.0, 1.0);
            } else {
                // A wind particle: a soft dot, faint by design — there are
                // hundreds and none of them is meant to be looked at directly.
                alpha = (1.0 - smoothstep(0.0, 1.0, radius)) * 0.75;
                alpha *= vColor.a;
            }

            gl_FragColor = vec4(color, alpha * vFade);
        }
    """
}
