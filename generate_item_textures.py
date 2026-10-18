"""Generates simple 16x16 item textures for the Frieren mod's custom items."""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__),
                   "src", "main", "resources", "assets", "frierenmod", "textures", "item")
os.makedirs(OUT, exist_ok=True)

T = (0, 0, 0, 0)


def save(name, grid, palette):
    img = Image.new("RGBA", (16, 16), T)
    px = img.load()
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch != " ":
                px[x, y] = palette[ch]
    img.save(os.path.join(OUT, name + ".png"))
    print("wrote", name)


def book(name, cover, pages=(238, 230, 210, 255)):
    pal = {"c": cover, "d": tuple(max(0, v - 30) if i < 3 else v for i, v in enumerate(cover)),
           "p": pages, "b": (60, 45, 30, 255)}
    grid = [
        "                ",
        "    cccccccccc   ",
        "  cddcccccccdc   ",
        "  cdppppppppdc   ",
        "  cdppppppppdc   ",
        "  cdppppppppdc   ",
        "  cdppppppppdc   ",
        "  cdppppppppdc   ",
        "  cdppppppppdc   ",
        "  cdppppppppdc   ",
        "  cdppppppppdc   ",
        "  cdppppppppdc   ",
        "  cddcccccccdc   ",
        "   cccccccccc   ",
        "                ",
        "                ",
    ]
    save(name, grid, pal)


book("grimoire_basic_magic", (70, 90, 200, 255))
book("grimoire_spirit_lore", (90, 170, 150, 255))
book("grimoire_ancient_spells", (150, 90, 190, 255))

# Promise ring
ring_pal = {"g": (232, 200, 96, 255), "d": (190, 160, 70, 255), "s": (150, 220, 240, 255)}
save("promise_ring", [
    "                ",
    "                ",
    "       s        ",
    "      sss       ",
    "       s        ",
    "     ggggg      ",
    "    gd   dg     ",
    "    g     g     ",
    "    g     g     ",
    "    gd   dg     ",
    "     ggggg      ",
    "                ",
    "                ",
    "                ",
    "                ",
    "                ",
], ring_pal)

# Mana herb
herb_pal = {"l": (80, 175, 95, 255), "d": (55, 130, 70, 255), "s": (110, 90, 60, 255),
            "b": (120, 150, 230, 255)}
save("mana_herb", [
    "                ",
    "      l         ",
    "     ldl   l    ",
    "     ldl  ldl   ",
    "      ldl ld    ",
    "    l  ldlll    ",
    "   ldl  ld      ",
    "    lddldl b    ",
    "      sdl bb    ",
    "      s ld      ",
    "      s         ",
    "     sss        ",
    "                ",
    "                ",
    "                ",
    "                ",
], herb_pal)

# Zoltraak scroll
scr_pal = {"p": (232, 224, 196, 255), "d": (198, 186, 150, 255), "r": (120, 80, 50, 255),
           "m": (150, 90, 220, 255)}
save("zoltraak_scroll", [
    "                ",
    "   rr      rr   ",
    "   rppppppppr   ",
    "   rpddddddpr   ",
    "   rp  mm  pr   ",
    "   rp m  m pr   ",
    "   rp mmmm pr   ",
    "   rp m  m pr   ",
    "   rp m  m pr   ",
    "   rpddddddpr   ",
    "   rppppppppr   ",
    "   rr      rr   ",
    "                ",
    "                ",
    "                ",
    "                ",
], scr_pal)

print("done")
