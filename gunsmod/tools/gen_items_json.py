"""Gun / jeep item models (builtin/entity + display transforms) and the muzzle flash fx model."""
import json, os
import numpy as np
from PIL import Image
from display import fit

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod")
meta = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "gun_meta.json")))


def write(p, o):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(o, open(p, "w"), indent=1)


for g, m in meta.items():
    b = m["bounds"]
    disp = {
        "gui": fit(b, (12, -32, 0), 1.0),
        "ground": fit(b, (0, 0, 0), 0.55),
        "fixed": fit(b, (0, 0, 0), 0.95),
        "head": fit(b, (0, 90, 0), 0.9),
    }
    write(os.path.join(ROOT, "models", "item", g + ".json"),
          {"parent": "builtin/entity", "gui_light": "side", "textures": {"particle": f"gunsmod:item/gun/{g}"}, "display": disp})

for j in ("green_jeep", "red_jeep"):
    write(os.path.join(ROOT, "models", "item", j + ".json"),
          {"parent": "builtin/entity", "gui_light": "side", "textures": {"particle": "gunsmod:item/vehicle/jeep"},
           "display": {"gui": {"rotation": [25, -135, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                       "ground": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                       "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                       "thirdperson_righthand": {"rotation": [70, 45, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
                       "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]}}})

for j, tex in (("green_tank", "tank"), ("red_tank", "tank"), ("green_helicopter", "heli"), ("red_helicopter", "heli")):
    write(os.path.join(ROOT, "models", "item", j + ".json"),
          {"parent": "builtin/entity", "gui_light": "side", "textures": {"particle": f"gunsmod:item/vehicle/{tex}"},
           "display": {"gui": {"rotation": [25, -135, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                       "ground": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                       "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                       "thirdperson_righthand": {"rotation": [70, 45, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
                       "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]}}})

# rotor blur: translucent disc with faint blade streaks and darker tip ring
n = 256
yy, xx = np.mgrid[0:n, 0:n]
cc = (n - 1) / 2
rr = np.hypot(xx - cc, yy - cc) / cc
ang = np.arctan2(yy - cc, xx - cc)
streak = 0.55 + 0.45 * np.cos(ang * 4) ** 8
alpha = np.where(rr <= 1.0, (0.25 + 0.55 * rr ** 1.5) * (0.7 + 0.3 * streak), 0.0)
alpha[(rr > 0.93) & (rr <= 1.0)] = 0.85
alpha[rr < 0.09] = 0.0
rgb = np.ones((n, n, 3)) * 0.13
rgb[(rr > 0.93) & (rr <= 1.0)] = [0.75, 0.62, 0.12]
blur = np.concatenate([rgb, alpha[..., None]], -1)
os.makedirs(os.path.join(ROOT, "textures", "entity"), exist_ok=True)
Image.fromarray((np.clip(blur, 0, 1) * 255).astype(np.uint8), "RGBA").save(os.path.join(ROOT, "textures", "entity", "rotor_blur.png"))

# muzzle flash: a star facing +X plus two long crossed planes, centred on (0, 8, 8) and reaching forward along +X
s = 64
y, x = np.mgrid[0:s, 0:s]
c = (s - 1) / 2
r = np.hypot(x - c, y - c) / c
a = np.arctan2(y - c, x - c)
spikes = 0.5 + 0.5 * np.abs(np.cos(a * 3.0)) ** 5
core = np.clip(1 - r / (0.3 + 0.7 * spikes), 0, 1) ** 1.3
star = np.stack([np.ones_like(core), 0.78 + 0.22 * core, 0.35 + 0.65 * core ** 2, core], -1)
# side flame: long tapered cone along x
yy = np.abs(y - c) / c
xx = x / (s - 1)
cone = np.clip(1 - yy / (0.15 + 0.85 * (1 - xx) * (0.6 + 0.4 * np.sin(xx * 9) ** 2)), 0, 1) * np.clip(1.2 - xx, 0, 1)
side = np.stack([np.ones_like(cone), 0.7 + 0.3 * cone, 0.25 + 0.6 * cone ** 2, cone ** 1.2], -1)
img = np.zeros((s, s * 2, 4))
img[:, :s] = star
img[:, s:] = side
os.makedirs(os.path.join(ROOT, "textures", "item", "fx"), exist_ok=True)
Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGBA").save(os.path.join(ROOT, "textures", "item", "fx", "muzzle_flash.png"))
T = "#0"
flash = {
    "textures": {"0": "gunsmod:item/fx/muzzle_flash", "particle": "gunsmod:item/fx/muzzle_flash"},
    "elements": [
        {"from": [0.2, 4, 4], "to": [0.2, 12, 12], "shade": False,
         "faces": {"east": {"uv": [0, 0, 8, 16], "texture": T}, "west": {"uv": [0, 0, 8, 16], "texture": T}}},
        {"from": [0, 4, 8], "to": [9, 12, 8], "shade": False,
         "faces": {"north": {"uv": [16, 0, 8, 16], "texture": T}, "south": {"uv": [8, 0, 16, 16], "texture": T}}},
        {"from": [0, 8, 4], "to": [9, 8, 12], "shade": False,
         "faces": {"up": {"uv": [8, 0, 16, 16], "texture": T, "rotation": 90}, "down": {"uv": [8, 0, 16, 16], "texture": T, "rotation": 90}}},
    ],
}
write(os.path.join(ROOT, "models", "fx", "muzzle_flash.json"), flash)
print("ok", len(meta))
