"""
Generates the 128x128 GeckoLib texture for Frieren, matching the box-UV layout in
assets/frierenmod/geo/entity/frieren.geo.json.

Colors: silver/white hair, pale skin, dark charcoal/purple outfit with white trim, green eyes,
wooden staff with a gold ornament.
"""
import os
import random
from PIL import Image

random.seed(42)

W = H = 128
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Palette
SKIN = (240, 224, 214, 255)
SKIN_SH = (222, 202, 192, 255)
HAIR = (228, 228, 236, 255)
HAIR_SH = (203, 203, 214, 255)
OUTFIT = (46, 44, 60, 255)
OUTFIT_SH = (36, 34, 48, 255)
TRIM = (234, 234, 238, 255)
EYE = (78, 168, 108, 255)
EYE_DK = (44, 110, 70, 255)
WOOD = (112, 80, 52, 255)
GOLD = (228, 196, 98, 255)


def fill(x0, y0, x1, y1, color, shade=None, noise=8):
    """Fill a rectangle [x0,x1) x [y0,y1) with color + subtle noise."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            if 0 <= x < W and 0 <= y < H:
                if shade is not None and random.random() < 0.22:
                    c = shade
                else:
                    c = color
                if noise:
                    r = random.randint(-noise, noise)
                    c = (
                        max(0, min(255, c[0] + r)),
                        max(0, min(255, c[1] + r)),
                        max(0, min(255, c[2] + r)),
                        255,
                    )
                px[x, y] = c


# ---- Head (uv 0,0 net 32x16) ----
fill(0, 0, 32, 16, SKIN, SKIN_SH)
# Front face of the head is at [8,8]-[16,16]; draw the face + eyes there.
fill(8, 8, 16, 16, SKIN, SKIN_SH, noise=4)
# Eyes (two green dots)
for (ex, ey) in [(10, 12), (13, 12)]:
    px[ex, ey] = EYE
    px[ex, ey + 1] = EYE_DK
# Small mouth
px[11, 14] = SKIN_SH
px[12, 14] = SKIN_SH
# Hairline/bangs across the top of the face
fill(8, 8, 16, 10, HAIR, HAIR_SH, noise=6)

# ---- Body (uv 0,20 net 24x16) ----
fill(0, 20, 24, 36, OUTFIT, OUTFIT_SH)
# White trim: vertical center stripe on the front face (front at x 4..12, y 24..36)
fill(7, 24, 9, 36, TRIM, None, noise=4)
# Collar trim near the top
fill(4, 24, 12, 26, TRIM, None, noise=4)

# ---- Right arm (uv 0,40 net 16x16) ----
fill(0, 40, 16, 56, OUTFIT, OUTFIT_SH)
# Hand (skin) at the bottom rows of the arm net
fill(0, 52, 16, 56, SKIN, SKIN_SH, noise=4)
# Sleeve white cuff
fill(0, 50, 16, 52, TRIM, None, noise=3)

# ---- Left arm (uv 24,40 net 16x16) ----
fill(24, 40, 40, 56, OUTFIT, OUTFIT_SH)
fill(24, 52, 40, 56, SKIN, SKIN_SH, noise=4)
fill(24, 50, 40, 52, TRIM, None, noise=3)

# ---- Right leg (uv 48,40 net 16x16) ----
fill(48, 40, 64, 56, OUTFIT, OUTFIT_SH)
# Boots at the bottom
fill(48, 53, 64, 56, (30, 28, 36, 255), None, noise=4)

# ---- Left leg (uv 64,40 net 16x16) ----
fill(64, 40, 80, 56, OUTFIT, OUTFIT_SH)
fill(64, 53, 80, 56, (30, 28, 36, 255), None, noise=4)

# ---- Hair back (uv 40,0 net 22x30) ----
fill(40, 0, 62, 30, HAIR, HAIR_SH)

# ---- Hair left (uv 64,0 net 22x29) ----
fill(64, 0, 86, 29, HAIR, HAIR_SH)

# ---- Hair right (uv 86,0 net 22x29) ----
fill(86, 0, 108, 29, HAIR, HAIR_SH)

# ---- Ears (uv 0,60 and 10,60 net 10x4) ----
fill(0, 60, 10, 64, SKIN, SKIN_SH, noise=4)
fill(10, 60, 20, 64, SKIN, SKIN_SH, noise=4)

# ---- Staff rod (uv 110,0 net 4x35) ----
fill(110, 0, 114, 35, WOOD, (92, 64, 40, 255))

# ---- Staff ornament (uv 110,36 net 12x6) ----
fill(110, 36, 122, 42, GOLD, (198, 168, 80, 255))

out_dir = os.path.join(
    os.path.dirname(__file__),
    "src", "main", "resources", "assets", "frierenmod", "textures", "entity",
)
os.makedirs(out_dir, exist_ok=True)
out_path = os.path.join(out_dir, "frieren.png")
img.save(out_path)
print("Wrote", out_path, img.size)
