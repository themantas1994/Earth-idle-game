"""Generates EARTH's launcher icons and splash screens.

The mark states the game's premise in one image: a planet whose northern half
is still the ocean-blue and green of an opening run, cooking through to the
scorched red of a collapsed one toward the south, inside a thin atmospheric
haze. Everything is rendered per-pixel at 4x and downsampled, so there is no
dependency on any drawing library's antialiasing.
"""
from PIL import Image
import math, os

RES = 'android/app/src/main/res'
SS = 4

BG_DEEP  = (11, 22, 34)
OCEAN    = (30, 92, 140)
OCEAN_LIT= (86, 178, 224)
LAND     = (46, 124, 84)
LAND_LIT = (108, 194, 132)
SCORCH   = (140, 44, 28)
SCORCH_LIT=(236, 116, 58)
HAZE     = (94, 214, 200)


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)


# Landmasses in the northern hemisphere, as unit-sphere-space blobs
# (x, y, radius-x, radius-y) with y negative = north.
CONTINENTS = [
    (-0.34, -0.50, 0.30, 0.19),
    (-0.10, -0.34, 0.26, 0.16),
    ( 0.30, -0.44, 0.26, 0.18),
    (-0.50, -0.12, 0.22, 0.15),
    ( 0.38, -0.06, 0.22, 0.14),
    ( 0.02, -0.62, 0.20, 0.12),
]


def land_amount(dx, dy):
    """Soft coverage in [0,1] of the continent blobs at a point on the disc."""
    best = 0.0
    for (ox, oy, rx, ry) in CONTINENTS:
        q = ((dx - ox) / rx) ** 2 + ((dy - oy) / ry) ** 2
        if q < 1.0:
            best = max(best, min(1.0, (1.0 - q) * 3.0))
    return best


def render(size, planet_frac=0.78, opaque_bg=True, bg=BG_DEEP):
    S = size * SS
    px = bytearray(S * S * 4)
    cx = cy = (S - 1) / 2.0
    r = S * planet_frac / 2.0
    halo_r = r * 1.20          # outer edge of the atmospheric haze

    for y in range(S):
        dyp = (y - cy) / r
        row = y * S * 4
        for x in range(S):
            dxp = (x - cx) / r
            dist = math.hypot(dxp, dyp)
            i = row + x * 4

            if opaque_bg:
                col = list(bg)
                alpha = 255
            else:
                col = [0.0, 0.0, 0.0]
                alpha = 0

            if dist <= 1.0:
                # --- planet surface ---
                nz = math.sqrt(max(0.0, 1.0 - dist * dist))
                # Latitude drives how far the climate has run away.
                heat = max(0.0, min(1.0, (dyp + 0.30) / 1.10)) ** 1.35
                ocean = lerp(OCEAN, SCORCH, heat)
                ocean_lit = lerp(OCEAN_LIT, SCORCH_LIT, heat)
                land = lerp(LAND, lerp(LAND_LIT, SCORCH, heat), 0.30)
                land_lit = lerp(LAND_LIT, SCORCH_LIT, heat)

                g = land_amount(dxp, dyp)
                base = lerp(ocean, land, g)
                lit = lerp(ocean_lit, land_lit, g)

                # Lambert term, light from the upper left.
                shade = (-dxp * 0.50 - dyp * 0.50 + nz * 0.80) / 1.30
                shade = max(0.0, min(1.0, shade))
                surf = lerp((base[0] * 0.22, base[1] * 0.22, base[2] * 0.24), base, 0.30 + 0.70 * shade)
                surf = lerp(surf, lit, shade ** 2.2 * 0.75)

                # Bright rim on the sunlit limb.
                rim = max(0.0, (dist - 0.86) / 0.14) ** 1.6 * max(0.0, -dxp * 0.5 - dyp * 0.5)
                surf = lerp(surf, HAZE, min(0.55, rim))

                edge = min(1.0, (1.0 - dist) * r * 1.2)  # feather the silhouette
                col = list(lerp(col if opaque_bg else (surf if True else surf), surf, edge)) if opaque_bg else list(surf)
                alpha = 255 if opaque_bg else int(255 * edge)
            elif dist < halo_r / r:
                # --- atmospheric haze: one smooth falloff, not stacked fills ---
                t = (dist - 1.0) / (halo_r / r - 1.0)
                a = (1.0 - t) ** 2.0 * 0.42
                if opaque_bg:
                    col = list(lerp(bg, HAZE, a))
                    alpha = 255
                else:
                    col = list(HAZE)
                    alpha = int(255 * a)

            px[i] = int(max(0, min(255, col[0])))
            px[i + 1] = int(max(0, min(255, col[1])))
            px[i + 2] = int(max(0, min(255, col[2])))
            px[i + 3] = alpha

    img = Image.frombytes('RGBA', (S, S), bytes(px))
    return img.resize((size, size), Image.LANCZOS)


def write(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def round_mask(size):
    S = size * SS
    m = bytearray(S * S)
    c = (S - 1) / 2.0
    for y in range(S):
        for x in range(S):
            m[y * S + x] = 255 if math.hypot(x - c, y - c) <= c else 0
    return Image.frombytes('L', (S, S), bytes(m)).resize((size, size), Image.LANCZOS)


DENSITIES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}

for name, size in DENSITIES.items():
    write(f'{RES}/mipmap-{name}/ic_launcher.png', render(size, planet_frac=0.74))

    rnd = render(size, planet_frac=0.82)
    rnd.putalpha(round_mask(size))
    write(f'{RES}/mipmap-{name}/ic_launcher_round.png', rnd)

    # Adaptive foreground: transparent, art inside the 66/108 safe zone.
    write(f'{RES}/mipmap-{name}/ic_launcher_foreground.png', render(size, planet_frac=0.55, opaque_bg=False))

SPLASH = {
    'port': {'mdpi': (320, 480), 'hdpi': (480, 800), 'xhdpi': (720, 1280), 'xxhdpi': (960, 1600), 'xxxhdpi': (1280, 1920)},
    'land': {'mdpi': (480, 320), 'hdpi': (800, 480), 'xhdpi': (1280, 720), 'xxhdpi': (1600, 960), 'xxxhdpi': (1920, 1280)},
}
for orient, sizes in SPLASH.items():
    for name, (w, h) in sizes.items():
        canvas = Image.new('RGBA', (w, h), (*BG_DEEP, 255))
        mark = render(int(min(w, h) * 0.46), planet_frac=0.78, opaque_bg=False)
        canvas.alpha_composite(mark, ((w - mark.width) // 2, (h - mark.height) // 2))
        write(f'{RES}/drawable-{orient}-{name}/splash.png', canvas)

canvas = Image.new('RGBA', (480, 800), (*BG_DEEP, 255))
mark = render(220, planet_frac=0.78, opaque_bg=False)
canvas.alpha_composite(mark, ((480 - mark.width) // 2, (800 - mark.height) // 2))
write(f'{RES}/drawable/splash.png', canvas)

write('public/icon-192.png', render(192, planet_frac=0.80))
write('public/icon-512.png', render(512, planet_frac=0.80))
write('public/favicon.png', render(64, planet_frac=0.86))

print('icons + splashes generated')
