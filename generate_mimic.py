"""
Generates the Mimic's GeckoLib assets and Frieren's chest-dive animations.

The Mimic is drawn in two passes (see MimicRenderer):
  1. the vanilla chest texture (minecraft:textures/entity/chest/normal.png) on the chest body,
     so a hidden Mimic looks exactly like a real chest, resource packs included;
  2. mimic_mouth.png on the teeth, tongue, maw and gums.
Every cube's UVs land on pixels that are transparent in the other pass's texture: the vanilla
chest texture only uses rows 0-42 and columns 0-55, so the mouth parts all live at y >= 43.

Writes:
  assets/frierenmod/geo/entity/mimic.geo.json
  assets/frierenmod/animations/entity/mimic.animation.json
  assets/frierenmod/textures/entity/mimic_mouth.png
  assets/frierenmod/animations/entity/frieren.animation.json (adds the chest_* animations)
"""
import json
import os
import random

from PIL import Image

random.seed(7)

ROOT = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "assets", "frierenmod")

# Lid rotation sign: GeckoLib opens a back-hinged lid with negative X.
S = -1


def face(u, v, w, h):
    return {"uv": [u, v], "uv_size": [w, h]}


# ---------------------------------------------------------------- geometry
def chest_box(u0, v0, w, h, d):
    """Per-face UVs matching vanilla's chest layout. The block entity model isn't Y-flipped like
    entity models, so its "up" texture is the second square and every side is stored upside down."""
    return {
        "up": face(u0 + d + w, v0, w, d),     # lid: outer top, base: inner floor
        "down": face(u0 + d, v0, w, d),
        "north": face(u0 + 2 * d + w, v0 + d + h, w, -h),  # front: the strip with the latch notch
        "south": face(u0 + d, v0 + d + h, w, -h),
        "east": face(u0, v0 + d + h, d, -h),
        "west": face(u0 + d + w, v0 + d + h, d, -h),
    }


TOOTH_UV = [24, 43]   # box uv for 1 x 1.5 x 1 teeth
FANG_UV = [28, 43]    # box uv for 1 x 3 x 1 fangs
TONGUE_UV = [0, 55]   # box uv for 4 x 1 x 7
TIP_UV = [32, 43]     # box uv for 3 x 1 x 3
MAW_UV = [0, 43]      # 12 x 12, up face only
GUMS_UV = [12, 43]    # 12 x 12, down face only

lower_teeth = []
for x in (-5.5, -3.5, -1.5, 0.5, 2.5, 4.5):
    lower_teeth.append({"origin": [x, 10, -6], "size": [1, 1.5, 1], "uv": TOOTH_UV})
for z in (-3.5, -1.5, 0.5, 2.5):
    lower_teeth.append({"origin": [-6, 10, z], "size": [1, 1.5, 1], "uv": TOOTH_UV})
    lower_teeth.append({"origin": [5, 10, z], "size": [1, 1.5, 1], "uv": TOOTH_UV})

upper_teeth = []
for x in (-4.5, -2.5, -0.5, 1.5, 3.5):
    upper_teeth.append({"origin": [x, 7.5, -6], "size": [1, 1.5, 1], "uv": TOOTH_UV})
for z in (-2.5, -0.5, 1.5, 3.5):
    upper_teeth.append({"origin": [-6, 7.5, z], "size": [1, 1.5, 1], "uv": TOOTH_UV})
    upper_teeth.append({"origin": [5, 7.5, z], "size": [1, 1.5, 1], "uv": TOOTH_UV})
for x in (-6, 5):
    upper_teeth.append({"origin": [x, 6, -6], "size": [1, 3, 1], "uv": FANG_UV})

geo = {
    "format_version": "1.12.0",
    "minecraft:geometry": [{
        "description": {
            "identifier": "geometry.mimic",
            "texture_width": 64,
            "texture_height": 64,
            "visible_bounds_width": 2,
            "visible_bounds_height": 2,
            "visible_bounds_offset": [0, 0.5, 0],
        },
        "bones": [
            {"name": "root", "pivot": [0, 0, 0]},
            {"name": "base", "parent": "root", "pivot": [0, 0, 0], "cubes": [
                {"origin": [-7, 0, -7], "size": [14, 10, 14], "uv": chest_box(0, 19, 14, 10, 14)},
                {"origin": [-6, 10.02, -6], "size": [12, 0, 12], "uv": {"up": face(*MAW_UV, 12, 12)}},
            ]},
            {"name": "lowerTeeth", "parent": "base", "pivot": [0, 10, 0], "cubes": lower_teeth},
            {"name": "tongue", "parent": "base", "pivot": [0, 10.5, 4], "cubes": [
                {"origin": [-2, 10.05, -3], "size": [4, 1, 7], "uv": TONGUE_UV},
            ]},
            {"name": "tongueTip", "parent": "tongue", "pivot": [0, 10.5, -3], "cubes": [
                {"origin": [-1.5, 10.05, -6], "size": [3, 1, 3], "uv": TIP_UV},
            ]},
            {"name": "lid", "parent": "root", "pivot": [0, 9, 7], "cubes": [
                {"origin": [-7, 9, -7], "size": [14, 5, 14], "uv": chest_box(0, 0, 14, 5, 14)},
                {"origin": [-1, 7, -8], "size": [2, 4, 1], "uv": [0, 0]},
                {"origin": [-6, 8.98, -6], "size": [12, 0, 12], "uv": {"down": face(*GUMS_UV, 12, 12)}},
            ]},
            {"name": "upperTeeth", "parent": "lid", "pivot": [0, 9, 0], "cubes": upper_teeth},
        ],
    }],
}


# ---------------------------------------------------------------- animations
def kf(pairs):
    return {f"{t:.4g}" if t != int(t) else f"{t:.1f}": v for t, v in pairs}


def rot(x=0.0, y=0.0, z=0.0):
    return [x, y, z]


TONGUE_OUT = {"tongue": {"rotation": rot(S * 22)}, "tongueTip": {"rotation": rot(-S * 50)}}

mimic_anims = {
    "hidden": {"loop": True, "animation_length": 1, "bones": {
        "lid": {"rotation": rot()}, "root": {"position": [0, 0, 0], "rotation": rot()},
    }},
    "chest_open": {"loop": True, "animation_length": 1, "bones": {
        "lid": {"rotation": rot(S * 85)}, "root": {"position": [0, 0, 0], "rotation": rot()},
    }},
    "reveal": {"loop": False, "animation_length": 1.0, "bones": {
        "lid": {"rotation": kf([(0, rot()), (0.08, rot(S * 12)), (0.16, rot()), (0.26, rot(S * 15)),
                                (0.36, rot()), (0.6, rot(S * 75)), (1.0, rot(S * 45))])},
        "root": {"position": kf([(0, [0, 0, 0]), (0.08, [0, 1, 0]), (0.16, [0, 0, 0]), (0.26, [0, 1.5, 0]),
                                 (0.36, [0, 0, 0]), (0.5, [0, 3, 0]), (0.7, [0, 0, 0])])},
        "tongue": {"rotation": kf([(0.36, rot()), (0.7, rot(S * 30)), (1.0, rot(S * 22))])},
        "tongueTip": {"rotation": kf([(0.36, rot()), (0.7, rot(-S * 30)), (1.0, rot(-S * 50))])},
    }},
    "idle": {"loop": True, "animation_length": 2.0, "bones": {
        "lid": {"rotation": kf([(0, rot(S * 40)), (1.0, rot(S * 48)), (2.0, rot(S * 40))])},
        "tongue": {"rotation": kf([(0, rot(S * 20, -8)), (1.0, rot(S * 26, 8)), (2.0, rot(S * 20, -8))])},
        "tongueTip": {"rotation": kf([(0, rot(-S * 45)), (1.0, rot(-S * 55)), (2.0, rot(-S * 45))])},
        "root": {"position": [0, 0, 0], "rotation": rot()},
    }},
    "hop": {"loop": True, "animation_length": 0.6, "bones": {
        "root": {"position": kf([(0, [0, 0, 0]), (0.3, [0, 4, 0]), (0.6, [0, 0, 0])]),
                 "rotation": kf([(0, rot()), (0.15, rot(-6)), (0.45, rot(4)), (0.6, rot())])},
        "lid": {"rotation": kf([(0, rot(S * 30)), (0.3, rot(S * 58)), (0.6, rot(S * 30))])},
        "tongue": {"rotation": rot(S * 15)},
        "tongueTip": {"rotation": kf([(0, rot(-S * 30)), (0.3, rot(-S * 60)), (0.6, rot(-S * 30))])},
    }},
    "bite": {"loop": False, "animation_length": 0.45, "bones": {
        "lid": {"rotation": kf([(0, rot(S * 40)), (0.15, rot(S * 95)), (0.28, rot()), (0.45, rot(S * 40))])},
        "root": {"position": kf([(0, [0, 0, 0]), (0.15, [0, 2, 0]), (0.28, [0, 0, 0])])},
        "tongue": {"rotation": kf([(0, rot(S * 20)), (0.15, rot(S * 40)), (0.28, rot()), (0.45, rot(S * 20))])},
        "tongueTip": {"rotation": kf([(0, rot(-S * 50)), (0.15, rot(-S * 10)), (0.28, rot()), (0.45, rot(-S * 50))])},
    }},
    "chew": {"loop": True, "animation_length": 0.8, "bones": {
        "lid": {"rotation": kf([(0, rot(S * 24)), (0.4, rot(S * 12)), (0.8, rot(S * 24))])},
        "root": {"rotation": kf([(0, rot(0, 0, -3)), (0.4, rot(0, 0, 3)), (0.8, rot(0, 0, -3))]),
                 "position": kf([(0, [0, 0, 0]), (0.4, [0, 1, 0]), (0.8, [0, 0, 0])])},
    }},
    "gag": {"loop": False, "animation_length": 1.5, "bones": {
        "root": {"rotation": kf([(0, rot()), (0.15, rot(8, 0, -6)), (0.3, rot(-8, 0, 6)), (0.45, rot(8, 0, -6)),
                                 (0.6, rot(-8, 0, 6)), (0.75, rot(6, 0, -4)), (0.9, rot(-6, 0, 4)),
                                 (1.05, rot())]),
                 "position": kf([(0, [0, 0, 0]), (1.05, [0, 0, 0]), (1.2, [0, 3, 0]), (1.35, [0, 0, 0])])},
        "lid": {"rotation": kf([(0, rot(S * 20)), (0.9, rot(S * 10)), (1.15, rot(S * 105)), (1.5, rot(S * 55))])},
        "tongue": {"rotation": kf([(0, rot()), (1.05, rot()), (1.15, rot(S * 35)), (1.5, rot(S * 22))])},
        "tongueTip": {"rotation": kf([(0, rot()), (1.05, rot()), (1.15, rot(-S * 20)), (1.5, rot(-S * 50))])},
    }},
}

# Frieren head-first in a chest: the whole model flips around her feet, then rises so her waist
# sits at the anchor (the chest's inner floor). Everything below it is hidden by the chest itself.
# Her slim arms are exactly as wide as a chest (x = +-7), so flipped they'd show through its sides:
# they shrink away while she's inside (the staff is parented to the right arm and goes with it).
ARMS_IN = {"rightArm": {"scale": [0, 0, 0]}, "leftArm": {"scale": [0, 0, 0]}}

frieren_anims = {
    "chest_search": {"loop": True, "animation_length": 1.6, "bones": {
        "root": {"rotation": kf([(0, rot(180, 0, -3)), (0.8, rot(180, 0, 3)), (1.6, rot(180, 0, -3))]),
                 "position": [0, 12, 0]},
        "body": {"rotation": rot()},
        "rightLeg": {"rotation": kf([(0, rot(-15)), (0.8, rot(15)), (1.6, rot(-15))])},
        "leftLeg": {"rotation": kf([(0, rot(15)), (0.8, rot(-15)), (1.6, rot(15))])},
    }},
    "chest_panic": {"loop": True, "animation_length": 0.5, "bones": {
        "root": {"rotation": kf([(0, rot(180, 0, -6)), (0.25, rot(180, 0, 6)), (0.5, rot(180, 0, -6))]),
                 "position": [0, 13, 0]},
        "body": {"rotation": rot()},
        "rightLeg": {"rotation": kf([(0, rot(-45, 0, 8)), (0.125, rot(40, 0, -4)), (0.25, rot(-45, 0, 8)),
                                     (0.375, rot(40, 0, -4)), (0.5, rot(-45, 0, 8))])},
        "leftLeg": {"rotation": kf([(0, rot(40, 0, 4)), (0.125, rot(-45, 0, -8)), (0.25, rot(40, 0, 4)),
                                    (0.375, rot(-45, 0, -8)), (0.5, rot(40, 0, 4))])},
    }},
    "chest_deeper": {"loop": True, "animation_length": 0.4, "bones": {
        "root": {"rotation": rot(180), "position": [0, 9, 0]},
        "body": {"rotation": rot()},
        "rightLeg": {"rotation": kf([(0, rot(-10)), (0.2, rot(10)), (0.4, rot(-10))])},
        "leftLeg": {"rotation": kf([(0, rot(10)), (0.2, rot(-10)), (0.4, rot(10))])},
    }},
}


# Head pats: she bows a little with her hands folded in front, and tilts her head into the hand.
# The body pivots at the shoulders, so the legs lean back the other way to stay upright.
BOW = {"loop": True, "animation_length": 1.2, "bones": {
    "body": {"rotation": rot(12), "position": [0, -0.5, -2.4]},
    "rightLeg": {"rotation": rot(-12)},
    "leftLeg": {"rotation": rot(-12)},
    "head": {"rotation": kf([(0, rot(16, 0, -5)), (0.6, rot(20, 0, 6)), (1.2, rot(16, 0, -5))])},
    "rightArm": {"rotation": rot(-10, 0, 14)},
    "leftArm": {"rotation": rot(-10, 0, -14)},
}}


# ---------------------------------------------------------------- mouth texture
def paint_mouth():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()

    def jitter(c, amount=8):
        d = random.randint(-amount, amount)
        return tuple(max(0, min(255, v + d)) for v in c[:3]) + (255,)

    # Maw: dark red, black throat toward the back.
    for y in range(12):
        for x in range(12):
            dx, dy = (x - 5.5) / 6, (y - 3.5) / 8
            dark = max(0.0, 1 - (dx * dx + dy * dy))
            base = (120, 18, 28)
            c = tuple(int(v * (1 - 0.75 * dark)) for v in base)
            px[MAW_UV[0] + x, MAW_UV[1] + y] = jitter(c, 6)
    # Gums: ridged pinkish red.
    for y in range(12):
        for x in range(12):
            c = (150, 34, 48) if y % 3 else (110, 20, 34)
            px[GUMS_UV[0] + x, GUMS_UV[1] + y] = jitter(c, 6)

    # Teeth and fangs: ivory, darker toward the gum.
    def tooth_block(u, v, w, h):
        for y in range(h):
            for x in range(w):
                shade = int(238 - 12 * (y / max(1, h - 1)))
                px[u + x, v + y] = jitter((shade, shade - 8, shade - 34), 4)
    tooth_block(TOOTH_UV[0], TOOTH_UV[1], 4, 3)
    tooth_block(FANG_UV[0], FANG_UV[1], 4, 4)

    # Tongue: pink with a darker middle groove on the top face.
    def tongue_block(u, v, w, d, h):
        fw, fh = 2 * d + 2 * w, d + h
        for y in range(fh):
            for x in range(fw):
                px[u + x, v + y] = jitter((214, 92, 116), 6)
        mid = u + d + w // 2
        for y in range(d):
            px[mid, v + y] = (176, 62, 86, 255)
    tongue_block(TONGUE_UV[0], TONGUE_UV[1], 4, 7, 1)
    tongue_block(TIP_UV[0], TIP_UV[1], 3, 3, 1)
    return img


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    write_json(os.path.join(ROOT, "geo", "entity", "mimic.geo.json"), geo)
    write_json(os.path.join(ROOT, "animations", "entity", "mimic.animation.json"),
               {"format_version": "1.8.0", "animations": mimic_anims})
    paint_mouth().save(os.path.join(ROOT, "textures", "entity", "mimic_mouth.png"))

    frieren_path = os.path.join(ROOT, "animations", "entity", "frieren.animation.json")
    with open(frieren_path, encoding="utf-8") as f:
        frieren = json.load(f)
    for anim in frieren_anims.values():
        anim["bones"].update(ARMS_IN)
    frieren["animations"].update(frieren_anims)
    frieren["animations"]["bow"] = BOW
    write_json(frieren_path, frieren)
    print("mimic assets written")


if __name__ == "__main__":
    main()
