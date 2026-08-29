"""Generate the original, vanilla-style siege-zombie texture variants.

The entity uses Minecraft 1.21.1's classic ZombieModel UV layout. The model
mirrors the legacy right-arm/right-leg islands for the left side, so only the
head (0, 0), body (16, 16), arm (40, 16), and leg (0, 16) islands are painted.

The face keeps vanilla's feature scale: each eye is a 2x1 horizontal pixel
block at x=9..10 / x=13..14. They sit at y=11, one pixel above the previous
design. The emissive texture contains only those four pixels, with no bloom or
oversized socket.
"""
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/dreamingfishcore/textures/entity/siege_zombie"
OUT.mkdir(parents=True, exist_ok=True)
PREVIEW_OUT = ROOT / "design_previews"
PREVIEW_OUT.mkdir(parents=True, exist_ok=True)


# Muted variants of the familiar Zombie/Drowned/Husk families. The palette is
# original, but deliberately stays in Minecraft's small, readable color range.
SKIN_DARK = (57, 91, 48, 255)
SKIN_DULL = (72, 108, 59, 255)
SKIN = (94, 132, 75, 255)
SKIN_LIGHT = (119, 151, 91, 255)
SKIN_PALE = (132, 160, 101, 255)
EYE_SOCKET = (30, 38, 29, 255)
EYE_GLOW = (214, 242, 202, 255)

SHIRT_DARK = (25, 91, 99, 255)
SHIRT = (35, 126, 130, 255)
SHIRT_LIGHT = (50, 150, 149, 255)
PANTS_DARK = (53, 45, 89, 255)
PANTS = (70, 58, 116, 255)
PANTS_LIGHT = (83, 70, 132, 255)
BOOT_DARK = (65, 63, 62, 255)
BOOT = (91, 88, 84, 255)

# A tiny outer-layer head patch gives the silhouette some detail without
# becoming a hood or helmet. Most of the expanded head island remains empty.
WRAP_DARK = (103, 96, 75, 232)
WRAP = (149, 139, 107, 232)
WRAP_MOSS = (74, 105, 59, 232)

# The second appearance is a palette sibling, not a separate monster design.
# Recoloring the completed light texture guarantees identical UV placement,
# eye alignment and decoration geometry on both variants.
DARK_RECOLOR = {
    SKIN_DARK: (29, 48, 39, 255),
    SKIN_DULL: (40, 65, 51, 255),
    SKIN: (54, 82, 63, 255),
    SKIN_LIGHT: (71, 100, 75, 255),
    SKIN_PALE: (83, 111, 82, 255),
    EYE_SOCKET: (18, 26, 22, 255),
    SHIRT_DARK: (22, 30, 44, 255),
    SHIRT: (33, 45, 64, 255),
    SHIRT_LIGHT: (48, 61, 82, 255),
    PANTS_DARK: (36, 29, 43, 255),
    PANTS: (53, 41, 58, 255),
    PANTS_LIGHT: (70, 54, 74, 255),
    BOOT_DARK: (48, 50, 49, 255),
    BOOT: (72, 74, 70, 255),
    WRAP_DARK: (57, 51, 59, 232),
    WRAP: (88, 78, 89, 232),
    WRAP_MOSS: (57, 76, 44, 232),
}


def fill(draw: ImageDraw.ImageDraw, box, color):
    draw.rectangle(box, fill=color)


def grid(draw: ImageDraw.ImageDraw, x0: int, y0: int, rows, palette):
    """Paint one literal pixel per grid character."""
    width = len(rows[0])
    if any(len(row) != width for row in rows):
        raise ValueError("pixel grid rows must have equal width")
    for y, row in enumerate(rows):
        for x, token in enumerate(row):
            draw.point((x0 + x, y0 + y), fill=palette[token])


def front_projection(skin: Image.Image, emissive_eyes: Image.Image) -> Image.Image:
    """Flatten the model's front UV faces into a 16x32 inspection sprite."""
    sprite = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    face = skin.crop((8, 8, 16, 16))
    face = Image.alpha_composite(face, skin.crop((40, 8, 48, 16)))
    face = Image.alpha_composite(face, emissive_eyes.crop((8, 8, 16, 16)))
    sprite.alpha_composite(face, (4, 0))
    sprite.alpha_composite(skin.crop((20, 20, 28, 32)), (4, 8))
    arm = skin.crop((44, 20, 48, 32))
    sprite.alpha_composite(arm, (0, 8))
    sprite.alpha_composite(arm, (12, 8))
    leg = skin.crop((4, 20, 8, 32))
    sprite.alpha_composite(leg, (4, 20))
    sprite.alpha_composite(leg, (8, 20))
    return sprite


base = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
draw = ImageDraw.Draw(base)

# ---------------------------------------------------------------------------
# Head: simple moss-green faces. No outer hood, black face, wounds or teeth.
# ---------------------------------------------------------------------------
fill(draw, (8, 0, 15, 7), SKIN_DARK)       # top
fill(draw, (16, 0, 23, 7), SKIN_DULL)      # underside
fill(draw, (0, 8, 7, 15), SKIN_DULL)       # left
fill(draw, (16, 8, 23, 15), SKIN)          # right
fill(draw, (24, 8, 31, 15), SKIN_DARK)     # back

# Sparse square mottling on non-front faces.
fill(draw, (10, 1, 12, 2), SKIN_DULL)
fill(draw, (13, 4, 14, 5), SKIN)
fill(draw, (1, 9, 2, 11), SKIN)
fill(draw, (5, 13, 6, 14), SKIN_DARK)
fill(draw, (18, 9, 19, 10), SKIN_LIGHT)
fill(draw, (21, 12, 22, 13), SKIN_DULL)
fill(draw, (26, 10, 28, 11), SKIN_DULL)
fill(draw, (29, 13, 30, 14), SKIN)

# Front face. The eyes keep the vanilla width/spacing and move up one row.
grid(draw, 8, 8, [
    "ddmmmmmd",
    "dmmllmmd",
    "mmlllmmm",
    "meesseem",
    "mssssmmm",
    "mssddssm",
    "dmdmmdmd",
    "dddddddd",
], {
    "d": SKIN_DARK,
    "m": SKIN_DULL,
    "s": SKIN,
    "l": SKIN_LIGHT,
    "e": EYE_SOCKET,
})

# ---------------------------------------------------------------------------
# Body: a plain weathered teal shirt. The previous diagonal strap is removed.
# ---------------------------------------------------------------------------
fill(draw, (20, 16, 27, 19), SHIRT_LIGHT)   # top
fill(draw, (28, 16, 35, 19), SHIRT_DARK)    # underside
fill(draw, (16, 20, 19, 31), SHIRT_DARK)    # left side
fill(draw, (28, 20, 31, 31), SHIRT)         # right side
fill(draw, (32, 20, 39, 31), SHIRT_DARK)    # back

grid(draw, 20, 20, [
    "abbggbba",
    "bbaggbbb",
    "bbcbcbba",
    "bcbabbcb",
    "bbcbcabb",
    "cbbcbcbc",
    "bbcbcbba",
    "bcbabbcb",
    "abbcbbba",
    "abbggbba",
    "ppppppbb",
    "pppppppq",
], {
    "a": SHIRT_DARK,
    "b": SHIRT,
    "c": SHIRT_LIGHT,
    "g": SKIN_DULL,
    "p": PANTS,
    "q": PANTS_DARK,
})

# Small square wear marks on side/back faces; no diagonal or horror details.
fill(draw, (17, 27, 18, 29), SHIRT)
fill(draw, (29, 21, 30, 22), SHIRT_LIGHT)
fill(draw, (29, 28, 30, 30), SKIN_DULL)
fill(draw, (34, 23, 35, 24), SHIRT)
fill(draw, (37, 28, 38, 29), SHIRT)

# ---------------------------------------------------------------------------
# Arm: short teal sleeve, green forearm. All four side faces use the same
# vertical division so mirroring never makes one arm look mismatched.
# ---------------------------------------------------------------------------
fill(draw, (44, 16, 47, 19), SHIRT_LIGHT)
fill(draw, (48, 16, 51, 19), SHIRT_DARK)
for face_x in (40, 44, 48, 52):
    fill(draw, (face_x, 20, face_x + 3, 23), SHIRT)
    fill(draw, (face_x, 24, face_x + 3, 31), SKIN)

fill(draw, (41, 21, 42, 22), SHIRT_LIGHT)
fill(draw, (45, 20, 46, 21), SHIRT_LIGHT)
fill(draw, (49, 22, 50, 23), SHIRT_DARK)
fill(draw, (53, 21, 54, 22), SHIRT_DARK)
fill(draw, (41, 26, 42, 27), SKIN_LIGHT)
fill(draw, (45, 29, 46, 30), SKIN_DULL)
fill(draw, (49, 25, 50, 26), SKIN_LIGHT)
fill(draw, (53, 28, 54, 29), SKIN_DARK)

# ---------------------------------------------------------------------------
# Leg: muted blue-purple trousers and a two-pixel gray boot cuff.
# ---------------------------------------------------------------------------
fill(draw, (4, 16, 7, 19), PANTS_LIGHT)
fill(draw, (8, 16, 11, 19), PANTS_DARK)
for face_x in (0, 4, 8, 12):
    fill(draw, (face_x, 20, face_x + 3, 29), PANTS)
    fill(draw, (face_x, 30, face_x + 3, 31), BOOT_DARK)

fill(draw, (1, 22, 2, 23), PANTS_LIGHT)
fill(draw, (5, 25, 6, 26), PANTS_DARK)
fill(draw, (9, 21, 10, 22), PANTS_LIGHT)
fill(draw, (13, 27, 14, 28), PANTS_DARK)
fill(draw, (1, 30, 2, 31), BOOT)
fill(draw, (5, 30, 6, 31), BOOT)
fill(draw, (9, 30, 10, 31), BOOT)
fill(draw, (13, 30, 14, 31), BOOT)

# Sparse cloth-and-moss fragment on the expanded head layer. The top pixels
# connect to the upper-front pixels, while the rest of the island is left
# transparent so this reads as decoration rather than a full hood.
fill(draw, (42, 2, 43, 2), WRAP_DARK)
fill(draw, (42, 3, 44, 3), WRAP)
draw.point((45, 3), fill=WRAP_MOSS)
draw.point((44, 4), fill=WRAP_MOSS)
fill(draw, (41, 8, 43, 8), WRAP)
draw.point((44, 8), fill=WRAP_MOSS)
fill(draw, (42, 9, 43, 9), WRAP_DARK)
draw.point((44, 9), fill=WRAP_MOSS)

# Full-bright overlay: two symmetric 2x1 eyes, four opaque pixels total.
eyes = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
eyes_draw = ImageDraw.Draw(eyes)
fill(eyes_draw, (9, 11, 10, 11), EYE_GLOW)
fill(eyes_draw, (13, 11, 14, 11), EYE_GLOW)

dark = Image.new("RGBA", base.size)
dark.putdata([DARK_RECOLOR.get(pixel, pixel) for pixel in base.getdata()])

base.save(OUT / "siege_zombie.png")
dark.save(OUT / "siege_zombie_dark.png")
eyes.save(OUT / "siege_zombie_eyes.png")

# Side-by-side nearest-neighbour preview for quick visual review. This is a
# design aid only and is not packaged as a game resource.
scale = 14
projected_light = front_projection(base, eyes).resize(
    (16 * scale, 32 * scale), Image.Resampling.NEAREST)
projected_dark = front_projection(dark, eyes).resize(
    (16 * scale, 32 * scale), Image.Resampling.NEAREST)
preview = Image.new("RGBA", (16 * scale * 2 + 72, 32 * scale + 56), (24, 25, 27, 255))
preview_draw = ImageDraw.Draw(preview)
preview_draw.text((24, 18), "MOSS VARIANT", fill=(225, 225, 220, 255))
preview_draw.text((16 * scale + 48, 18), "DARK VARIANT", fill=(225, 225, 220, 255))
preview.alpha_composite(projected_light, (24, 48))
preview.alpha_composite(projected_dark, (16 * scale + 48, 48))
preview.save(PREVIEW_OUT / "siege_zombie_v4_variants_front.png")

print("wrote", OUT / "siege_zombie.png")
print("wrote", OUT / "siege_zombie_dark.png")
print("wrote", OUT / "siege_zombie_eyes.png")
print("wrote", PREVIEW_OUT / "siege_zombie_v4_variants_front.png")
