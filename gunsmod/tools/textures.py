"""Particles, GUI art and soldier skins."""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod")
TX = os.path.join(ROOT, "textures")


def save(img, *path):
    p = os.path.join(TX, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def rgba(a):
    return Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8), "RGBA")


def radial(size):
    y, x = np.mgrid[0:size, 0:size]
    c = (size - 1) / 2
    return np.hypot(x - c, y - c) / c, np.arctan2(y - c, x - c)


# ------------------------------------------------------------------------------------ particles
def particles():
    rng = np.random.default_rng(3)
    defs = {}
    # muzzle flash: 4 star bursts
    names = []
    for k in range(4):
        s = 32
        r, a = radial(s)
        spikes = 0.55 + 0.45 * np.abs(np.cos(a * (3 + k % 2) + k)) ** 6
        core = np.clip(1 - r / (0.35 + 0.65 * spikes), 0, 1) ** 1.5
        col = np.stack([np.ones_like(core), 0.75 + 0.25 * core, 0.35 + 0.65 * core ** 2, core], -1)
        save(rgba(col), "particle", f"flash_{k}.png")
        names.append(f"gunsmod:flash_{k}")
    defs["muzzle_flash"] = names
    # tracer: horizontal glow bar
    w, h = 32, 8
    y = np.abs(np.arange(h) - (h - 1) / 2)[:, None] / (h / 2)
    x = np.linspace(0, 1, w)[None, :]
    a = np.clip(1 - y ** 2, 0, 1) * (0.25 + 0.75 * x)
    one = np.ones((h, w))
    col = np.stack([one, (0.85 + 0.15 * (1 - y)) * one, (0.45 + 0.4 * (1 - y)) * one, a], -1)
    save(rgba(col), "particle", "tracer.png")
    defs["tracer"] = ["gunsmod:tracer"]
    # smoke: 8 soft puffs
    names = []
    for k in range(8):
        s = 32
        r, a = radial(s)
        n = np.asarray(Image.fromarray((rng.random((8, 8)) * 255).astype(np.uint8)).resize((s, s), Image.BICUBIC)) / 255
        alpha = np.clip(1 - r * (1.05 + 0.25 * (n - 0.5)), 0, 1) ** 1.6 * (0.55 + 0.45 * n)
        g = 0.75 + 0.25 * n
        col = np.stack([g, g, g, alpha], -1)
        save(rgba(col), "particle", f"smoke_{k}.png")
        names.append(f"gunsmod:smoke_{k}")
    defs["smoke"] = names
    defs["missile_smoke"] = names
    # flame: 8 frames from bright to dark
    names = []
    for k in range(8):
        s = 32
        r, a = radial(s)
        n = np.asarray(Image.fromarray((rng.random((6, 6)) * 255).astype(np.uint8)).resize((s, s), Image.BICUBIC)) / 255
        f = k / 7
        alpha = np.clip(1 - r * (1.0 + 0.5 * (n - 0.5)), 0, 1) ** 1.2 * (1 - f * 0.4)
        heat = np.clip(1 - r * 1.3 + n * 0.3 - f * 0.6, 0, 1)
        col = np.stack([np.ones_like(heat) * (1 - f * 0.3), 0.25 + 0.7 * heat, 0.05 + 0.6 * heat ** 2, alpha], -1)
        save(rgba(col), "particle", f"flame_{k}.png")
        names.append(f"gunsmod:flame_{k}")
    defs["flame"] = names
    # spark
    s = 8
    r, a = radial(s)
    col = np.stack([np.ones_like(r), np.clip(1.1 - r * 0.5, 0, 1), np.clip(0.7 - r * 0.6, 0, 1), np.clip(1 - r, 0, 1) ** 0.7], -1)
    save(rgba(col), "particle", "spark.png")
    defs["spark"] = ["gunsmod:spark"]
    # shell casing
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([1, 3, 6, 4], fill=(214, 168, 66, 255))
    d.rectangle([1, 3, 6, 3], fill=(244, 206, 110, 255))
    d.rectangle([0, 3, 0, 4], fill=(150, 110, 40, 255))
    save(img, "particle", "shell.png")
    defs["shell"] = ["gunsmod:shell"]
    # hologram ring for spawners (also used as generic glow)
    s = 32
    r, a = radial(s)
    ring = np.exp(-((r - 0.8) ** 2) / 0.006) + np.exp(-((r - 0.45) ** 2) / 0.004) * 0.6
    col = np.stack([0.3 * np.ones_like(r), 0.85 * np.ones_like(r), np.ones_like(r), np.clip(ring, 0, 1)], -1)
    save(rgba(col), "particle", "holo.png")
    defs["holo"] = ["gunsmod:holo"]
    os.makedirs(os.path.join(ROOT, "particles"), exist_ok=True)
    for name, tex in defs.items():
        with open(os.path.join(ROOT, "particles", name + ".json"), "w") as fh:
            json.dump({"textures": tex}, fh)
    return list(defs)


# ------------------------------------------------------------------------------------ GUI
def nine_slice(path, w, h, border, draw):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw(img)
    save(img, "gui", "sprites", path + ".png")
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": border}}}
    with open(os.path.join(TX, "gui", "sprites", path + ".png.mcmeta"), "w") as fh:
        json.dump(meta, fh)


def noise_img(w, h, base, var, seed):
    rng = np.random.default_rng(seed)
    n = rng.normal(0, var, (h, w, 1))
    arr = np.clip(np.array(base, float)[None, None, :] / 255 + n, 0, 1)
    return arr


def panel_draw(base=(48, 52, 46), edge=(110, 118, 98), seed=1, rivets=True):
    def d(img):
        w, h = img.size
        arr = noise_img(w, h, base, 0.018, seed)
        a = np.ones((h, w, 1))
        out = np.concatenate([arr, a], -1)
        im = rgba(out)
        dr = ImageDraw.Draw(im)
        dr.rectangle([0, 0, w - 1, h - 1], outline=(12, 14, 12, 255))
        dr.rectangle([1, 1, w - 2, h - 2], outline=edge + (255,))
        dr.line([(2, h - 3), (w - 3, h - 3)], fill=(20, 22, 20, 255))
        dr.line([(w - 3, 2), (w - 3, h - 3)], fill=(20, 22, 20, 255))
        dr.line([(2, 2), (w - 4, 2)], fill=tuple(min(255, c + 30) for c in edge) + (255,))
        if rivets:
            for (x, y) in ((4, 4), (w - 6, 4), (4, h - 6), (w - 6, h - 6)):
                dr.rectangle([x, y, x + 1, y + 1], fill=(150, 156, 140, 255))
                dr.point((x + 1, y + 1), fill=(40, 42, 38, 255))
        img.paste(im)
    return d


def gui():
    nine_slice("panel", 64, 64, 7, panel_draw())
    nine_slice("panel_dark", 32, 32, 4, panel_draw((24, 27, 25), (60, 66, 58), 2, rivets=False))
    nine_slice("button", 32, 16, 3, panel_draw((76, 86, 56), (128, 140, 96), 3, rivets=False))
    nine_slice("button_hover", 32, 16, 3, panel_draw((104, 118, 74), (170, 186, 120), 4, rivets=False))
    nine_slice("button_off", 32, 16, 3, panel_draw((50, 50, 50), (80, 80, 80), 5, rivets=False))
    nine_slice("button_red", 32, 16, 3, panel_draw((120, 34, 30), (190, 70, 60), 6, rivets=False))

    def slot(img):
        dr = ImageDraw.Draw(img)
        dr.rectangle([0, 0, 17, 17], fill=(30, 33, 30, 255))
        dr.line([(0, 0), (16, 0)], fill=(12, 12, 12, 255))
        dr.line([(0, 0), (0, 16)], fill=(12, 12, 12, 255))
        dr.line([(1, 17), (17, 17)], fill=(96, 104, 88, 255))
        dr.line([(17, 1), (17, 17)], fill=(96, 104, 88, 255))
    img = Image.new("RGBA", (18, 18))
    slot(img)
    save(img, "gui", "sprites", "slot.png")

    # hazard strip
    w, h = 64, 8
    img = Image.new("RGBA", (w, h))
    dr = ImageDraw.Draw(img)
    for i in range(-2, 12):
        x = i * 8
        dr.polygon([(x, h), (x + 4, h), (x + 8, 0), (x + 4, 0)], fill=(230, 180, 20, 255))
    arr = np.asarray(img).copy()
    arr[arr[..., 3] == 0] = (25, 25, 25, 255)
    Image.fromarray(arr).save(os.path.join(TX, "gui", "sprites", "hazard.png")) if os.path.isdir(os.path.join(TX, "gui", "sprites")) else None

    # speedometer dial 128x128
    S = 128
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    c = S / 2
    dr.ellipse([2, 2, S - 3, S - 3], fill=(18, 20, 18, 235), outline=(120, 128, 110, 255), width=3)
    dr.ellipse([8, 8, S - 9, S - 9], outline=(60, 64, 56, 255), width=1)
    # 0..60 km/h over 240 degrees, start at 150deg
    for v in range(0, 61, 2):
        ang = math.radians(150 + v * 4)
        major = v % 10 == 0
        r0 = c - (16 if major else 11)
        r1 = c - 7
        col = (230, 230, 210, 255) if v <= 40 else (220, 60, 50, 255)
        dr.line([(c + math.cos(ang) * r0, c + math.sin(ang) * r0), (c + math.cos(ang) * r1, c + math.sin(ang) * r1)],
                fill=col, width=2 if major else 1)
    # red zone arc
    dr.arc([10, 10, S - 11, S - 11], 150 + 40 * 4, 150 + 60 * 4, fill=(200, 40, 30, 255), width=3)
    font = ImageFont.load_default()
    for v in range(0, 61, 10):
        ang = math.radians(150 + v * 4)
        r = c - 26
        txt = str(v)
        tw = dr.textlength(txt, font=font)
        dr.text((c + math.cos(ang) * r - tw / 2, c + math.sin(ang) * r - 5), txt, fill=(220, 220, 200, 255), font=font)
    dr.text((c - dr.textlength("km/h", font=font) / 2, c + 22), "km/h", fill=(160, 170, 150, 255), font=font)
    save(img, "gui", "speedometer.png")

    # sniper scope overlay 256x256 (centre circle clear)
    S = 256
    y, x = np.mgrid[0:S, 0:S]
    r = np.hypot(x - S / 2 + 0.5, y - S / 2 + 0.5) / (S / 2)
    alpha = np.clip((r - 0.96) * 30, 0, 1)
    arr = np.zeros((S, S, 4))
    arr[..., 3] = alpha
    # vignette ring
    arr[..., 3] = np.maximum(arr[..., 3], np.clip((r - 0.8) * 3, 0, 0.6))
    # reticle lines (thick outer posts, thin centre)
    cx = S // 2
    for (x0, x1, y0, y1) in ((cx - 1, cx + 1, 0, cx - 40), (cx - 1, cx + 1, cx + 40, S), (0, cx - 40, cx - 1, cx + 1), (cx + 40, S, cx - 1, cx + 1)):
        arr[y0:y1, x0:x1] = (0, 0, 0, 1)
    arr[cx - 40:cx + 40, cx:cx + 1] = (0, 0, 0, 0.9)
    arr[cx:cx + 1, cx - 40:cx + 40] = (0, 0, 0, 0.9)
    for k in range(1, 5):  # mil dots
        for s in (-1, 1):
            arr[cx + s * k * 9 - 1:cx + s * k * 9 + 1, cx - 1:cx + 2] = (0, 0, 0, 1)
            arr[cx - 1:cx + 2, cx + s * k * 9 - 1:cx + s * k * 9 + 1] = (0, 0, 0, 1)
    arr[cx - 1:cx + 1, cx - 1:cx + 1] = (1, 0.1, 0.1, 1)
    save(rgba(arr), "gui", "scope.png")

    # launch cover (hinged plastic cover with hazard border) 64x64
    S = 64
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    dr.rounded_rectangle([0, 0, S - 1, S - 1], 6, fill=(200, 30, 25, 150), outline=(255, 210, 40, 255), width=3)
    for i in range(-8, 16):
        x0 = i * 8
        dr.line([(x0, S - 1), (x0 + 12, S - 13)], fill=(20, 20, 20, 200), width=3)
    dr.rectangle([4, S - 12, S - 5, S - 5], outline=(255, 210, 40, 220))
    dr.text((10, 18), "SAFETY", fill=(255, 240, 220, 255), font=font)
    dr.line([(6, 6), (S - 12, 6)], fill=(255, 255, 255, 120), width=2)
    save(img, "gui", "launch_cover.png")
    # big red button 48x48 + pressed
    for name, off, glow in (("launch_button", 0, 1.0), ("launch_button_pressed", 2, 0.6)):
        S = 48
        r, a = radial(S)
        base = np.stack([np.full_like(r, 0.15), np.full_like(r, 0.15), np.full_like(r, 0.15), (r <= 1.0).astype(float)], -1)
        rr = np.hypot(*(np.mgrid[0:S, 0:S] - np.array([S / 2 - 0.5 - off * 0.3, S / 2 - 0.5 + off])[:, None, None])) / (S / 2)
        btn = rr < 0.78
        light = np.clip(1 - rr / 0.78, 0, 1)
        col = np.stack([0.55 + 0.45 * light * glow, 0.05 + 0.25 * light ** 3 * glow, 0.05 + 0.2 * light ** 3 * glow], -1)
        base[btn, :3] = col[btn]
        base[(r > 0.86) & (r <= 1.0), :3] = (0.75, 0.62, 0.15)
        save(rgba(base), "gui", name + ".png")


# ------------------------------------------------------------------------------------ soldier skins
SKIN_TONES = [(232, 190, 160), (200, 150, 110), (150, 100, 70), (95, 62, 42)]


def camo_tex(w, h, pal, seed):
    rng = np.random.default_rng(seed)
    idx = np.zeros((h, w), int)
    for k, th in ((1, 0.25), (2, 0.4), (3, 0.55)):
        n = np.asarray(Image.fromarray((rng.random((max(2, h // 3), max(2, w // 3))) * 255).astype(np.uint8)).resize((w, h), Image.NEAREST)) / 255
        idx[n > 1 - th * 0.6] = k
    return np.array(pal, np.uint8)[idx]


def fill_box(px, x, y, w, h, img):
    px[y:y + h, x:x + w, :3] = img[:h, :w]
    px[y:y + h, x:x + w, 3] = 255


def skin(team, variant):
    rng = np.random.default_rng(variant * 13 + (0 if team == "green" else 100))
    px = np.zeros((64, 64, 4), np.uint8)
    pal = [(76, 85, 52), (47, 53, 34), (107, 106, 69), (31, 34, 24)] if team == "green" else \
        [(179, 154, 108), (140, 117, 80), (209, 189, 143), (110, 90, 60)]
    camo = camo_tex(64, 64, pal, variant + (7 if team == "red" else 0))
    tone = np.array(SKIN_TONES[variant % 4], np.uint8)
    # ---- head (8x8 faces)
    head = np.tile(tone, (8, 8, 1)).astype(np.int16)
    head += rng.integers(-6, 6, (8, 8, 1))
    head = np.clip(head, 0, 255).astype(np.uint8)
    hair = np.array([(40, 30, 22), (60, 45, 30), (25, 22, 20), (70, 55, 35)][variant % 4], np.uint8)
    for (x, y) in ((8, 0), (16, 0), (0, 8), (8, 8), (16, 8), (24, 8)):
        fill_box(px, x, y, 8, 8, head)
    # short hair on top / back / sides
    px[0:8, 8:16, :3] = hair
    px[8:10, 0:8, :3] = hair
    px[8:10, 16:24, :3] = hair
    px[8:12, 24:32, :3] = hair
    px[8:9, 8:16, :3] = hair
    # face (front 8..16, 8..16)
    fx, fy = 8, 8
    if team == "red":
        # balaclava: dark cloth everywhere except the eye band
        bal = np.array([(30, 30, 32), (60, 20, 18), (40, 36, 30), (25, 25, 25)][variant % 4], np.uint8)
        for (x, y) in ((8, 0), (0, 8), (8, 8), (16, 8), (24, 8)):
            px[y:y + 8, x:x + 8, :3] = bal
        px[fy + 3:fy + 5, fx + 1:fx + 7, :3] = tone
    eye_white = (235, 235, 230)
    pupil = [(50, 80, 140), (60, 45, 30), (40, 90, 50), (30, 30, 30)][variant % 4]
    px[fy + 3, fx + 1:fx + 3, :3] = eye_white
    px[fy + 3, fx + 5:fx + 7, :3] = eye_white
    px[fy + 3, fx + 2, :3] = pupil
    px[fy + 3, fx + 5, :3] = pupil
    if team == "green":
        px[fy + 2, fx + 1:fx + 3, :3] = hair
        px[fy + 2, fx + 5:fx + 7, :3] = hair
        px[fy + 4, fx + 3:fx + 5, :3] = np.clip(tone.astype(int) - 25, 0, 255)       # nose shadow
        px[fy + 6, fx + 2:fx + 6, :3] = np.clip(tone.astype(int) - 50, 0, 255)       # mouth
        # camo face paint stripes
        px[fy + 5, fx:fx + 2, :3] = pal[1]
        px[fy + 5, fx + 6:fx + 8, :3] = pal[1]
        px[fy + 4, fx + 7, :3] = pal[0]
    # ---- body / arms / legs with camo
    def region(x, y, w, h):
        fill_box(px, x, y, w, h, camo[y:y + h, x:x + w])
    for (x, y, w, h) in ((20, 16, 8, 4), (28, 16, 8, 4), (16, 20, 4, 12), (20, 20, 8, 12), (28, 20, 4, 12), (32, 20, 8, 12),
                         (44, 16, 4, 4), (48, 16, 4, 4), (40, 20, 16, 12),
                         (36, 48, 4, 4), (40, 48, 4, 4), (32, 52, 16, 12),
                         (4, 16, 4, 4), (8, 16, 4, 4), (0, 20, 16, 12),
                         (20, 48, 4, 4), (24, 48, 4, 4), (16, 52, 16, 12)):
        region(x, y, w, h)
    # gloves (bottom 3 rows of arms + arm bottoms)
    glove = (28, 28, 28)
    px[29:32, 40:56, :3] = glove
    px[61:64, 32:48, :3] = glove
    px[16:20, 48:52, :3] = glove
    px[48:52, 40:44, :3] = glove
    # boots (bottom 4 rows of legs)
    boot = (52, 40, 30) if team == "red" else (30, 28, 26)
    px[28:32, 0:16, :3] = boot
    px[60:64, 16:32, :3] = boot
    px[16:20, 8:12, :3] = boot
    px[48:52, 24:28, :3] = boot
    # belt
    px[30:32, 16:40, :3] = (40, 36, 30)
    # rolled sleeves line + shirt neck
    px[24, 40:56, :3] = np.clip(np.array(pal[0]) + 20, 0, 255)
    # ---- overlays: knee pads, team armband
    for (x, y) in ((4, 40), (0, 40), (8, 40), (4, 56), (0, 56), (8, 56)):
        pass
    kp = (36, 36, 34)
    px[44:47, 4:8] = (*kp, 255)       # right leg overlay front knee
    px[57:60, 4:8] = (*kp, 255)
    band = (40, 160, 60) if team == "green" else (190, 40, 30)
    px[32 + 4 + 2:32 + 4 + 4, 40:56] = (*band, 255)    # right arm sleeve overlay (y 36..48)
    px[52 + 2:52 + 4, 48:64] = (*band, 255)              # left arm sleeve overlay
    return Image.fromarray(px, "RGBA")


def skins():
    for team in ("green", "red"):
        for v in range(4):
            save(skin(team, v), "entity", f"soldier_{team}_{v}.png")


if __name__ == "__main__":
    print("particles", particles())
    gui()
    skins()
    print("ok")
