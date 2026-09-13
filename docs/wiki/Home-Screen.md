# Home screen

[← Documentation home](Home.md)

The Home tab is a live 3D visualization of the planet the simulation is running,
with the numbers that explain it directly beneath.

Everything on it is a **view**. The globe computes no game state, owns none, and
feeds nothing back; if it fails to start, or the device has no 3D at all, the
screen below it is unchanged and the game is unaffected. See
[Environmental visualization](Environmental-Visualization.md) for the renderer
itself and [Storm system](Storm-System.md) for the weather it draws.

---

## Layout

Portrait-first and one-handed, like the rest of the game. Top to bottom:

| Section | What it is |
| :-- | :-- |
| **Header** | `GameHeader` — which Earth this is, its age, its temperature, its habitability, and the resource balances |
| **The globe** | `GlobeCard` → `GlobeSurface` — a 300 dp interactive Earth |
| **Overlay picker** | A scrolling pill row: Atmosphere, Temperature, Humidity, Wind, Events, Storms |
| **Legend** | The selected overlay's scale in words, plus what it currently reads |
| **Active phenomena** | Live storms, what they are costing, and any running world events |
| **Storm card** | Appears when a storm is selected; closes without leaving the screen |
| **Environment** | Temperature, humidity, wind, atmosphere, storms, habitability |
| **Objective** | The next thing to buy, and which tab sells it |
| **Planetary status** | Earth age, radiative forcing, habitability |
| **Economy / Momentum / Gases / News** | The compact status the screen has always shown |
| **Reset** | Available once the planet is uninhabitable |

The globe is the visual focus. It is deliberately **not** the whole screen: at
300 dp it leaves room for the phenomena strip and the first status card on a
360 × 640 phone, and nothing that was on Home before this feature has been
removed to make space for it.

The resource balances moved into the header on Home. They used to be hidden
there — Home was a list of numbers and the balances were among them — and now
that Home opens on a globe they belong in the header on every tab.

---

## Interacting with the globe

| Gesture | Effect |
| :-- | :-- |
| Drag | Rotates the planet; pitch is clamped to ±78° so it never tumbles |
| Pinch | Zooms between 0.85× and 2.2× |
| Tap a storm | Opens its card and eases the camera round to face it |
| Tap elsewhere | Clears the selection and hands the camera back |
| Idle | The planet turns on its own, on the simulated clock |

Auto-rotation stops while a finger is down and resumes when it lifts. On the
**Minimal** graphics setting the globe holds still entirely.

Storm taps are hit-tested against screen positions the *renderer* publishes each
frame (`GlobeRenderer.stormHitTargets`), not against a second copy of the camera
maths in Compose — so a tap lands on the storm the player can actually see, at
whatever rotation and zoom the camera has eased to.

---

## Overlays

Exactly one surface overlay is active at a time. That is a limit rather than a
simplification: each of these is a full-globe treatment, and two at once produces
a planet that is colourful and says nothing.

| Overlay | Shows | Driven by |
| :-- | :-- | :-- |
| **Atmosphere** | The greenhouse blanket — the rim halo thickens and reddens | `forcing.total` |
| **Temperature** | A cool → neutral → warm → extreme ramp, hottest at the equator | `temperatureAnomalyC` |
| **Humidity** | A vapour wash banded by latitude | `atmosphere[H2O]` |
| **Wind** | Moving streamline particles | The wind field (see below) |
| **Events** | Pins where running world events are being felt | `activeEvents` |
| **Storms** | Every live storm, spiralling at its own intensity | `storms` |

Storm markers stay visible under every overlay **except** Wind, where hundreds
of particles and six rotating spirals fight each other. Event pins show on the
Events and Storms overlays.

---

## Accessibility

The 3D globe is never the only way to understand the game. Everything it draws
is also available as text:

- The globe carries a spoken description — overlay, temperature, humidity, wind,
  habitability, storm count, running events, and how to interact with it.
- Every overlay states its scale **in words** (`Cool` → `Extreme`) as well as in
  colour, and prints its current reading underneath.
- Every storm has a spoken summary naming its type, severity, position and
  current production penalty, reachable from the storm chips without touching
  the globe.
- Storm severity is a **word** (`Forming`, `Moderate`, `Severe`, `Extreme`), not
  only a colour or a size.
- Storm effects are printed as percentages on the storm card and in the Active
  Phenomena strip. Colour never carries a gameplay effect on its own.
- The `GLSurfaceView` itself is excluded from the semantics tree, so a screen
  reader gets the description once rather than landing on an opaque surface.

See [Accessibility](Accessibility.md) for the project-wide rules.

---

## Graphics quality

Settings → Globe Quality, persisted in the save as `settings.graphicsQuality`.

| Setting | Clouds | Wind particles | Storm animation | Auto-rotation |
| :-- | :-- | --: | :-- | :-- |
| **Full** | Yes | 220 | Yes | Yes |
| **Reduced** | No | 90 | Yes | Yes |
| **Minimal** | No | 0 | No — static markers | No |

Every setting keeps the Earth, the overlays, the storm markers and all the
gameplay information. **No setting changes the simulation**: the same storms
form, move and cost the same production at every quality level. The reduced-motion
setting under Appearance is honoured independently and suppresses animation
whatever the quality is.

---

## When 3D is not available

Two things can take the globe away:

1. The device reports less than OpenGL ES 2.0 (checked before the surface is
   created).
2. The renderer fails — a shader that will not compile, a texture that will not
   allocate, a driver that throws.

Either swaps in `FlatGlobe`, a Compose `Canvas` drawing of the **same**
`EnvironmentalVisualizationState`: the same temperature ramp, the same
atmospheric halo, the same storms at the same coordinates, tappable in the same
way. The player loses polish and nothing else.

The app declares no `uses-feature` requiring OpenGL, deliberately, so a device
that cannot render the globe can still install and play the game.

---

**Next:** [Environmental visualization](Environmental-Visualization.md) · [Storm system](Storm-System.md) · [UI architecture](UI-Architecture.md)
