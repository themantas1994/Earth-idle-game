# Environmental visualization

[← Documentation home](Home.md)

How the 3D Earth on [Home](Home-Screen.md) is rendered, where every value it
draws comes from, and why it is built the way it is.

> **The weather and storm system is a gameplay simulation, not a scientific
> weather forecast.** Nothing on this page models the atmosphere. Humidity is a
> reading of the climate model's water-vapour term, wind is a fixed three-cell
> pattern whose strength tracks warming, and the visuals are tuned against
> *gameplay-relevant* ranges rather than physical ones — a planet at +8 °C is
> meant to look alarming here, which a real one seen from orbit would not.

---

## The rule the whole feature is built on

The renderer is **always downstream** of the simulation.

```
simulation tick
  → GameState  (authoritative)
    → DerivedState
      → EnvironmentalVisualizationState   (immutable snapshot, pure, testable)
        → GlobeRenderer                   (draws it)
```

Never:

```
renderer → random storm → gameplay        ✗
```

There are no rendering objects in `GameState`, no `GameState` in the renderer,
and no path by which the renderer can change anything. A storm's position on the
globe is the position the *simulation* gave it. If the renderer never runs, the
simulation is unaffected — which is exactly what the fallback below relies on.

---

## `EnvironmentalVisualizationState`

`presentation/visualization/EnvironmentalVisualizationState.kt`. An immutable
snapshot holding only what a shader or a marker needs, built by the pure
function `environmentalVisualizationOf(state, derived, nowMs)`.

| Field | Range | Source in the simulation |
| :-- | :-- | :-- |
| `temperatureAnomalyC` | raw °C | `GameState.temperatureAnomalyC` |
| `temperatureScale` | 0..1 | the above / `TEMPERATURE_VISUAL_CEILING_C` (20 °C) |
| `humidity` | 0..1 | `relativeHumidity(atmosphere[H2O])` |
| `windStrength` | 0..1 | `windStrength(temperature, forcing)` |
| `atmosphericOpacity` | 0..1 | `forcing.total` / `FORCING_VISUAL_CEILING_WM2` (12 W/m²) |
| `cloudCoverage` | 0..1 | derived from `humidity` |
| `habitability` | 0..1 | `habitability.fraction` |
| `stormRisk` | 0..1 | `stormFormationPressure` |
| `rotationPhase` | 0..1 | `gameAgeSeconds` |
| `gasContributions` | shares | `forcing.perGas`, strongest first |
| `storms` | list | `GameState.storms.storms` |
| `events` | list | `GameState.activeEvents` |

Both visual ceilings are **gameplay** figures. A run spends most of its length
between 0 and about 20 °C of anomaly and the endgame runs to six figures, so
past the ceiling the visualization stops getting redder while the number beside
it keeps climbing. The same is true of the atmosphere.

`EnvironmentalVisualizationTest` asserts every one of these against the state it
was built from — without a GPU, because none of it needs one.

---

## Why OpenGL ES directly, and not SceneView or Filament

Both were evaluated first, as the obvious Compose-friendly options. The facts
below were read off the published artifacts, not recalled.

**SceneView 4.35.0** (`io.github.sceneview:sceneview`) declares `minSdk 24`,
which matches this app. What it brings with it does not:

| Artifact | Size |
| :-- | --: |
| `sceneview` | 4.0 MB |
| `filament-android` | 12.2 MB (12.5 MB of `.so` across four ABIs) |
| `gltfio-android` | 5.8 MB |
| `filament-utils-android` | 2.4 MB |
| `com.github.kittinunf.fuel` ×3 | an HTTP client |

That is roughly **24 MB of artifacts, about 13 MB of it native code**, for a game
whose entire release APK is a fraction of that and which is distributed as a
direct download. It also declares

```xml
<uses-feature android:glEsVersion="0x00030000" android:required="true" />
```

which merges into this app's manifest and becomes a **Play install filter** — a
game that is otherwise entirely 2D would stop being installable on devices that
can run every other screen perfectly. And `fuel` is a networking dependency added
to an app whose [Data Safety declaration](../GOOGLE_PLAY_DATA_SAFETY.md) and
[privacy policy](../PRIVACY_POLICY.md) both turn on the app making no network
requests of its own.

**Filament directly** avoids the HTTP client and the glTF loader but keeps the
12.5 MB of native code, and hits a second problem: every overlay this feature
needs is a custom shader, and Filament materials are compiled by `matc`, which is
**not published to Maven** — it ships in Filament's release tarballs. Using it
would mean either a build-time native toolchain in a CI job that currently needs
none, or checked-in binary blobs, in a repository whose
[asset policy](../ASSETS.md) requires documented provenance for every shipped
byte.

Neither buys anything this feature actually needs. There is no glTF model to
load — the globe is a generated sphere — no PBR pipeline, no scene graph, and no
AR.

**What was built instead:** one textured sphere, two shells and a point-sprite
pass, against OpenGL ES 2.0. It adds **no dependency at all**, nothing to the
licence audit, and nothing to the manifest. GLES 2.0 is available on every device
at `minSdk 24`, so there is no install filter and no device excluded.

Measured, by building the release APK on the commit before this feature and on
the commit after it:

| | Release APK |
| :-- | --: |
| Before | 3,742,304 bytes |
| After | 3,807,840 bytes |
| **Difference** | **+64 KB (+1.8%)** |

Against roughly +20 MB for the SceneView route, for a feature that would have
used almost none of what that 20 MB contains.

Vulkan was not considered. Nothing here is close to being limited by the API.

---

## The renderer

`presentation/globe/`, about 1,100 lines across five files.

| File | What it is |
| :-- | :-- |
| `EarthTexture.kt` | Generates the surface and cloud textures |
| `SphereMesh.kt` | The UV sphere, and the lat/long → sphere mapping |
| `GlobeShaders.kt` | Every shader, as GLSL ES 1.00 strings |
| `GlobeRenderer.kt` | The `GLSurfaceView.Renderer`, the camera, the wind particles |
| `GlobeSurface.kt` | The composable, the gestures, the fallback decision |
| `FlatGlobe.kt` | The Compose fallback |

### Passes

1. **The planet.** A unit sphere with the generated texture, lit by a sun fixed
   in world space, with the selected overlay applied in the fragment shader.
2. **The cloud shell** (Full quality only). The same sphere at 1.018×, with the
   generated cloud alpha map, drifting at a rate set by the simulated wind, its
   opacity the simulated cloud coverage.
3. **The atmosphere.** The same sphere at 1.13×, drawn additively with a
   fresnel-style rim alpha — so it contributes nothing over the planet's face and
   piles into a halo at the silhouette. Its strength and colour come from
   radiative forcing: a thin blue line on a pristine Earth, a thick orange corona
   on a cooked one.
4. **Markers and particles.** Storms, event pins and wind streamlines in a
   **single** `GL_POINTS` draw call, with `aKind` selecting what the fragment
   shader paints and `aFade` hiding anything round the back.

### Day and night

The sun is fixed in world space and the planet turns under it, so the terminator
sweeps the surface and the night side darkens, keeping only a warm band at the
terminator and civilisation's lights on the land.

The rotation is driven by `GameState.gameAgeSeconds` — the simulation's own
clock — and **never** by the device clock. A paused game produces a still globe
and an absence jumps the planet forward correctly.

One rotation is `VISUAL_ROTATION_SIMULATED_DAYS` = 90 simulated days rather than
one simulated day, because [a simulated day passes every real
second](Atmospheric-Half-Life.md) and a planet spinning once a second is a
strobe, not a world. It is still the simulated clock; it is just read at a rate
a person can watch.

### The wind field

There is no wind in the climate model, so `domain/climate/Environment.kt` defines
one: `-cos(3·|lat|)`, which puts easterlies over the tropics, reverses to
westerlies through the mid-latitudes and falls away toward the poles, with a small
meridional term. Overall strength rises with warming and forcing and is capped.

It is a **gameplay wind field**. There is no pressure field, no Coriolis term and
no advection anywhere in it. It exists so the wind overlay has something honest to
draw and so storms drift somewhere sensible — and the storm simulation steers
storms with the *same* function, so the particles and the storms agree.

Particles are a bounded pool (220 / 90 / 0 by quality setting), advected by
`windAt`, seeded from the domain's own `DeterministicRandom` rather than an
unseeded `Random`.

### Humidity

`relativeHumidity(waterVaporPpm)` reads the climate model's existing `GasId.H2O`
feedback term — the one `computeWaterVaporFeedbackConcentration` has always
driven — and maps it onto 0..1 with a saturating curve. **No humidity state was
invented**: the number the globe draws and the number storms form out of is the
simulated water vapour.

---

## The Earth texture

Generated in code, at first frame, on the GL thread. See
[Assets](../ASSETS.md#the-earth-surface-texture) for the provenance record.

- `EarthSurface.CONTINENTS` — coarse continent outlines as `(longitude, latitude)`
  rings, **hand-authored for this project**. Recognisable at a glance and no more
  precise than that.
- Rasterised at 288 × 144, blurred with longitude wrapping, then roughened with
  value noise so the authored edges read as coastline.
- Painted at 1024 × 512: biome bands by latitude, relief from a second noise
  field, polar ice, ocean depth by distance from land.
- The cloud map is a separate 512 × 256 alpha texture, banded to match the wind
  model's three cells.

Generated rather than shipped for two reasons: an Earth texture is exactly the
kind of asset whose licensing quietly goes unrecorded (the repository already
carries [one such problem](../ASSETS.md)), and it costs a few kilobytes of Kotlin
instead of a few megabytes of PNG. The value noise is integer-only and depends on
no platform RNG, so the same planet is generated on every device.

---

## Performance

| Technique | Why |
| :-- | :-- |
| The scene is never rebuilt | `AndroidView`'s `update` writes one immutable `GlobeScene` onto the renderer. A 250 ms tick costs a field write, not a scene rebuild. |
| Overlays are uniforms | Switching from Temperature to Humidity changes a few floats. No geometry, no textures, no reallocation. |
| One mesh, three shells | The cloud and atmosphere shells are the same sphere at a different scale. |
| One draw call for markers | Storms, events and hundreds of wind particles are one `GL_POINTS` call. |
| Bounded particles | Never more than the quality budget. No allocation per frame. |
| Textures generated once | On the GL thread at startup, never on the main thread, never regenerated. |
| No simulation on the render thread | The renderer only reads a snapshot. See [UI architecture](UI-Architecture.md) for where the tick actually runs. |
| Paused with the lifecycle | `GLSurfaceView.onPause()` on `ON_PAUSE`, so the GL thread does not run behind a locked screen. |

The target is 30 fps on lower-end supported devices and 60 where the hardware
allows. The renderer runs `RENDERMODE_CONTINUOUSLY` and does not force a frame
rate; the quality setting is the battery control, and Minimal draws a still globe
with no particles and no animation.

**Measured frame rates are not claimed.** No physical device or emulator was
available in the environment this was built in — see
[Testing](Testing.md) and the release checklist.

---

## Failure and fallback

| Failure | What happens |
| :-- | :-- |
| Device reports < GLES 2.0 | Checked before the surface is created; `FlatGlobe` is used |
| Shader will not compile | `buildProgram` checks the status, reports, `FlatGlobe` is used |
| Texture will not allocate | Caught, including `OutOfMemoryError`; `FlatGlobe` is used |
| Any exception in a frame | Caught, the surface goes blank once, `FlatGlobe` takes over |

`FlatGlobe` draws the same `EnvironmentalVisualizationState` with a Compose
`Canvas`: the same temperature ramp, the same halo, the same storms at the same
coordinates, tappable the same way, with the same spoken description.

A graphics failure can never corrupt the simulation, because the renderer has no
write path into it.

---

**Next:** [Home screen](Home-Screen.md) · [Storm system](Storm-System.md) · [Performance](Performance.md) · [Assets](../ASSETS.md)
