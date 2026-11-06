"""Shared aircraft detail: the cockpit seen from the pilot's seat (tub, ejection seat, instrument panel with lit
displays, glare shield, HUD combiner, side consoles, a stick and a throttle that move), and the small exterior parts
that make an aircraft read as real (pitot tube, blade antennas, formation light strips, IRST ball, static wicks).

Sizes are given in metres and converted to the model's units (u metres per unit: 0.375 for the fighters, 1 for the B-2).
The cockpit is built round the eye point E; the stick and the throttle are separate parts turning about their pivots."""
import math

import numpy as np

import meshkit as K
from meshkit import Mesh, v3


def inward(m):
    """turn every face of a mesh round (to be seen from inside)"""
    for p in m.polys:
        p["pts"].reverse()
        if p["n"] is not None:
            p["n"] = [-n for n in reversed(p["n"])]
    return m


def reskin(mesh):
    """painted panels become aircraft skin with panel lines and rivets"""
    for p in mesh.polys:
        if p["mat"] == "paint":
            p["mat"] = "skin"
        elif p["mat"] == "paint_dark":
            p["mat"] = "skin_dark"
    return mesh


def cockpit(eye, u, side_stick=True, displays=3, wide_panel=False):
    """parts 'cockpit', 'stick', 'throttle' and the pivots of the two levers (model units)"""
    ex, ey, ez = eye

    def P(x, y, z):
        return (ex + x / u, ey + y / u, ez + z / u)

    pit = Mesh()
    # the tub round the pilot, seen from inside: side walls, floor, the bulkhead behind the seat
    tub = Mesh()
    K.box(P(-0.46, -1.05, -1.3), P(0.46, -0.3, 0.6), "pit", m=tub, faces="xXyZ")
    pit.extend(inward(tub))
    # ejection seat: pan, back, headrest, the striped handle between the knees
    K.rbox(P(-0.24, -0.9, -0.38), P(0.24, -0.8, 0.12), 0.02 / u, "cushion", seg=1, m=pit)
    K.rbox(P(-0.26, -0.85, 0.12), P(0.26, 0.12, 0.3), 0.03 / u, "cushion", seg=1, m=pit)
    K.rbox(P(-0.2, 0.0, 0.18), P(0.2, 0.32, 0.4), 0.03 / u, "pit", seg=1, m=pit)
    K.box(P(-0.07, -0.86, -0.42), P(0.07, -0.82, -0.36), "warning", m=pit)
    for s in (-1, 1):
        K.box(P(s * 0.3 - 0.03, -0.85, 0.05), P(s * 0.3 + 0.03, 0.05, 0.35), "pit_light", m=pit)
    # instrument panel leaning back, glare shield over it, the displays and the up-front controls
    w = 0.44 if wide_panel else 0.38
    b0, b1 = -0.62, -0.2
    K.plate([v3(*P(-w, b0, -0.8)), v3(*P(w, b0, -0.8)), v3(*P(w, b1, -0.92)), v3(*P(-w, b1, -0.92))], 0.04 / u, "pit", m=pit)
    K.rbox(P(-w - 0.02, b1 - 0.02, -1.02), P(w + 0.02, b1 + 0.06, -0.78), 0.02 / u, "pit", seg=1, m=pit)
    if wide_panel:
        # one wide touch screen across the panel (F-35)
        K.plate([v3(*P(-w + 0.04, b0 + 0.06, -0.795)), v3(*P(w - 0.04, b0 + 0.06, -0.795)), v3(*P(w - 0.04, b1 - 0.1, -0.885)),
                 v3(*P(-w + 0.04, b1 - 0.1, -0.885))], 0.01 / u, "screen", m=pit)
    else:
        xs = [-0.22, 0.0, 0.22][:displays] if displays == 3 else [-0.15, 0.15]
        for x in xs:
            y0, y1 = b0 + 0.08, b0 + 0.27
            z0, z1 = -0.8 - (y0 - b0) * 0.28 - 0.01, -0.8 - (y1 - b0) * 0.28 - 0.01
            K.plate([v3(*P(x - 0.085, y0, z0)), v3(*P(x + 0.085, y0, z0)), v3(*P(x + 0.085, y1, z1)), v3(*P(x - 0.085, y1, z1))], 0.012 / u,
                    "screen" if x != 0.0 else "screen_amber", m=pit)
        # a row of small instruments and switches under the displays
        for k in range(7):
            x = -0.3 + k * 0.1
            K.cylinder("z", (ex + x / u, ey + (b0 + 0.04) / u), 0.022 / u, ez - 0.81 / u, ez - 0.8 / u, "light_grey", n=8, m=pit)
    K.rbox(P(-0.09, b1 - 0.02, -0.9), P(0.09, b1 + 0.08, -0.82), 0.01 / u, "pit_light", seg=1, m=pit)
    K.box(P(-0.07, b1 + 0.01, -0.822), P(0.07, b1 + 0.05, -0.818), "screen", m=pit, faces="Z")
    # HUD: the combiner glass on its frame in front of the eye
    K.plate([v3(*P(-0.1, -0.12, -0.82)), v3(*P(0.1, -0.12, -0.82)), v3(*P(0.1, 0.04, -0.87)), v3(*P(-0.1, 0.04, -0.87))], 0.006 / u, "hud_glass", m=pit)
    for s in (-1, 1):
        K.tube([P(s * 0.1, -0.16, -0.84), P(s * 0.1, 0.05, -0.88)], 0.008 / u, "pit", n=4, m=pit)
    # side consoles with their panels
    for s in (-1, 1):
        K.rbox(P(s * 0.3 - 0.08, -0.7, -0.55), P(s * 0.3 + 0.08, -0.56, 0.3), 0.01 / u, "pit", seg=1, m=pit)
        for k in range(4):
            z = -0.45 + k * 0.18
            K.box(P(s * 0.3 - 0.06, -0.559, z), P(s * 0.3 + 0.06, -0.55, z + 0.12), "pit_light", m=pit)
    # rear-view mirrors on the canopy bow
    for s in (-1, 1):
        K.box(P(s * 0.22 - 0.06, 0.1, -0.62), P(s * 0.22 + 0.06, 0.15, -0.6), "bright", m=pit)

    # the stick: in the middle or on the right console
    stick = Mesh()
    if side_stick:
        piv = P(0.3, -0.56, -0.2)
        top = P(0.3, -0.42, -0.23)
    else:
        piv = P(0.0, -0.85, -0.38)
        top = P(0.0, -0.45, -0.42)
    K.cylinder("y", (piv[0], piv[2]), 0.04 / u, piv[1] - 0.01 / u, piv[1] + 0.02 / u, "rubber", n=10, m=stick)
    K.tube([piv, top], 0.014 / u, "pit_light", n=6, m=stick)
    gx, gy, gz = top
    K.rbox((gx - 0.025 / u, gy - 0.02 / u, gz - 0.03 / u), (gx + 0.025 / u, gy + 0.09 / u, gz + 0.03 / u), 0.01 / u, "pit", seg=1, m=stick)
    K.box((gx - 0.01 / u, gy + 0.09 / u, gz - 0.02 / u), (gx + 0.01 / u, gy + 0.1 / u, gz), "red", m=stick)
    # the throttle on the left console
    thr = Mesh()
    tpiv = P(-0.3, -0.56, -0.15)
    ttop = P(-0.3, -0.44, -0.19)
    K.tube([tpiv, ttop], 0.012 / u, "pit_light", n=6, m=thr)
    tx, ty, tz = ttop
    K.rbox((tx - 0.03 / u, ty - 0.01 / u, tz - 0.035 / u), (tx + 0.03 / u, ty + 0.05 / u, tz + 0.035 / u), 0.01 / u, "pit", seg=1, m=thr)
    return {"cockpit": pit, "stick": stick, "throttle": thr}, piv, tpiv


def place_seat(eye, u):
    """the seat point the game sits the pilot on (the eye is 0.85 m above it)"""
    return (eye[0], eye[1] - 0.85 / u, eye[2] - 0.05 / u)


# ------------------------------------------------------------------------------------------------ exterior details
def pitot(m, tip, length, r):
    x, y, z = tip
    K.cylinder("z", (x, y), r, z, z + length, "bright", n=8, r1=r * 1.6, m=m)
    K.cylinder("z", (x, y), r * 0.5, z - length * 0.15, z, "steel", n=6, m=m)


def blade(m, x, y, z, h, chord, up=True, mat="skin_dark"):
    s = 1 if up else -1
    K.plate([v3(x, y, z), v3(x, y, z + chord), v3(x, y + s * h, z + chord * 0.75), v3(x, y + s * h, z + chord * 0.45)], 0.06, mat, m=m)


def light_strip(m, a, b, w=0.12):
    """a formation light strip (pale glowing band) on the skin between two points"""
    a, b = np.asarray(a, float), np.asarray(b, float)
    d = b - a
    side = np.cross(d, np.array([0.0, 1.0, 0.0]))
    if np.linalg.norm(side) < 1e-6:
        side = np.array([1.0, 0.0, 0.0])
    side = side / np.linalg.norm(side) * w / 2
    K.plate([a - side, b - side, b + side, a + side], 0.03, "glow", m=m)


def irst(m, x, y, z, r=0.45):
    """infrared search and track ball in front of the canopy"""
    K.sphere((x, y, z), r, r, r, "lens", n=12, rows=6, top_only=True, m=m)
    K.cylinder("y", (x, z), r * 1.1, y - 0.05, y + 0.02, "skin_dark", n=12, m=m)


def wicks(m, points, length=0.9):
    """static discharge wicks along trailing edges"""
    for x, y, z in points:
        K.cylinder("z", (x, y), 0.03, z, z + length, "dark", n=4, m=m)


def lights(m, left, right, tail=None):
    """navigation lights: red on the left wing tip, green on the right, white on the tail"""
    K.sphere(left, 0.12, 0.12, 0.12, "red_light", n=6, rows=3, m=m)
    K.sphere(right, 0.12, 0.12, 0.12, "glow", n=6, rows=3, m=m)
    if tail:
        K.sphere(tail, 0.1, 0.1, 0.1, "glow", n=6, rows=3, m=m)


def intake_lip(m, cx, cy, z, hw, hh, e=3.0):
    """a raised metal lip round an intake mouth"""
    outer = [v3(cx + x, cy + y, z) for x, y in K.superellipse(hw * 1.06, hh * 1.06, e, 18)]
    inner = [v3(cx + x, cy + y, z + 0.4) for x, y in K.superellipse(hw * 0.94, hh * 0.94, e, 18)]
    K.loft([outer, inner], "skin_metal", caps=(False, False), m=m)


def _upper(c, t, f):
    """height of the upper surface of a NACA-like section of chord c and thickness ratio t at chord fraction f"""
    x = f
    return 5 * t * (0.2969 * math.sqrt(x) - 0.126 * x - 0.3516 * x * x + 0.2843 * x ** 3 - 0.1036 * x ** 4) * c


def hinge_lines(m, le_root, le_tip, chord_root, chord_tip, thick, fracs=(0.12, 0.74), span=(0.08, 0.92)):
    """the seams of the slats and the flaps / ailerons drawn as thin dark strips on the upper skin"""
    a, b = np.asarray(le_root, float), np.asarray(le_tip, float)
    for f in fracs:
        pts = []
        for s in span:
            p = a + (b - a) * s
            c = chord_root + (chord_tip - chord_root) * s
            pts.append(np.array([p[0], p[1] + _upper(c, thick, f) + 0.02, p[2] + f * c]))
        d = pts[1] - pts[0]
        w = np.array([0.0, 0.0, 0.07])
        K.plate([pts[0] - w, pts[1] - w, pts[1] + w, pts[0] + w], 0.02, "dark", m=m)
    # the flap / aileron split half way along the span
    s = 0.5
    p = a + (b - a) * s
    c = chord_root + (chord_tip - chord_root) * s
    y = p[1] + _upper(c, thick, 0.85) + 0.02
    K.plate([v3(p[0] - 0.05, y, p[2] + 0.74 * c), v3(p[0] + 0.05, y, p[2] + 0.74 * c), v3(p[0] + 0.05, y, p[2] + c), v3(p[0] - 0.05, y, p[2] + c)],
            0.02, "dark", m=m)


# ------------------------------------------------------------------------------------------------ v2.3.1 exterior detail
def petal_nozzle(m, x, y, z0, z1, r0, r1, petals=16):
    """a convergent nozzle of overlapping petals from radius r0 at z0 to r1 at z1 (pointing +z), the sync ring round
    it, and inside the liner, the flame holder rings and the turbine face"""
    for k in range(petals):
        a0 = 2 * math.pi * (k - 0.55) / petals
        a1 = 2 * math.pi * (k + 0.55) / petals
        lift = 0.03 if k % 2 else 0.0
        mat = "gunmetal" if k % 2 else "burnt_metal"
        tip = 0.18 if k % 2 == 0 else 0.0
        pts = [v3(x + math.cos(a0) * (r0 + lift), y + math.sin(a0) * (r0 + lift), z0),
               v3(x + math.cos(a1) * (r0 + lift), y + math.sin(a1) * (r0 + lift), z0),
               v3(x + math.cos(a1) * (r1 + lift), y + math.sin(a1) * (r1 + lift), z1),
               v3(x + math.cos((a0 + a1) / 2) * (r1 + lift), y + math.sin((a0 + a1) / 2) * (r1 + lift), z1 + tip),
               v3(x + math.cos(a0) * (r1 + lift), y + math.sin(a0) * (r1 + lift), z1)]
        K.plate(pts, 0.05, mat, m=m)
    K.torus("z", (x, y), z0 + 0.25, r0 + 0.06, 0.07, "steel", n=petals * 2, k=4, m=m)
    K.torus("z", (x, y), z1 - 0.05, r1 + 0.02, 0.05, "dark", n=petals * 2, k=4, m=m)
    # the liner seen from behind (inward faces), the flame holder and the turbine
    liner = Mesh()
    K.cylinder("z", (x, y), r1 * 0.96, z1 - 1.8, z1 - 0.02, "burnt_metal", n=petals, caps=(False, False), m=liner)
    m.extend(inward(liner))
    K.torus("z", (x, y), z1 - 1.0, r1 * 0.55, 0.06, "dark", n=16, k=4, m=m)
    K.torus("z", (x, y), z1 - 1.0, r1 * 0.3, 0.05, "dark", n=12, k=4, m=m)
    for k in range(6):
        a = 2 * math.pi * k / 6
        K.tube([(x + math.cos(a) * r1 * 0.3, y + math.sin(a) * r1 * 0.3, z1 - 1.0), (x + math.cos(a) * r1 * 0.9, y + math.sin(a) * r1 * 0.9, z1 - 1.0)],
               0.04, "dark", n=4, m=m)
    K.cylinder("z", (x, y), r1 * 0.96, z1 - 1.8, z1 - 1.75, "black", n=petals, caps=(False, True), m=m)
    K.cylinder("z", (x, y), r1 * 0.22, z1 - 1.75, z1 - 1.2, "dark", n=10, r1=0.05, caps=(False, False), m=m)


def nozzle_2d(m, x, y0, y1, z0, z1, w):
    """a two-dimensional thrust-vectoring nozzle: side walls, the upper and lower flaps with their seams, the dark
    throat inside"""
    hw = w / 2
    for s in (-1, 1):
        K.plate([v3(x + s * hw, y0, z0), v3(x + s * hw, y1, z0), v3(x + s * hw, y1 - 0.2, z1), v3(x + s * hw, y0 + 0.2, z1)], 0.12, "gunmetal", m=m)
    ym = (y0 + y1) / 2
    for yy, d in ((y1, -1), (y0, 1)):
        K.plate([v3(x - hw, yy, z0), v3(x + hw, yy, z0), v3(x + hw, yy + d * 0.25, z1), v3(x - hw, yy + d * 0.25, z1)], 0.1, "burnt_metal", m=m)
        for k in range(1, 4):
            xx = x - hw + k * w / 4
            K.box((xx - 0.02, yy - 0.06, z0 + 0.1), (xx + 0.02, yy + 0.06, z1 - 0.05), "dark", m=m)
    inner = Mesh()
    K.box((x - hw + 0.1, y0 + 0.3, z1 - 1.6), (x + hw - 0.1, y1 - 0.3, z1 - 0.02), "burnt_metal", m=inner, faces="xXyYz")
    m.extend(inward(inner))
    K.box((x - hw + 0.1, ym - 0.04, z1 - 1.2), (x + hw - 0.1, ym + 0.04, z1 - 1.1), "dark", m=m)


def canopy_frame(m, c, rx, ry, rz, bow=-0.68):
    """the canopy's sill frame round its base and the windscreen arch"""
    x, y, z = c
    base = [(x + rx * 1.01 * math.cos(math.radians(a)), y + 0.02, z + rz * 1.01 * math.sin(math.radians(a))) for a in range(0, 361, 12)]
    K.tube(base, 0.07, "dark", n=5, caps=(False, False), m=m)
    bz = z + rz * bow
    k = math.sqrt(max(0.05, 1 - bow * bow))
    arch = [(x + rx * k * 1.01 * math.cos(math.radians(a)), y + ry * k * 1.01 * math.sin(math.radians(a)), bz) for a in range(0, 181, 12)]
    K.tube(arch, 0.07, "dark", n=5, m=m)


def beacon(m, x, y, z, r=0.16):
    """the red anti-collision light on its base"""
    K.cylinder("y", (x, z), r * 1.2, y, y + 0.06, "dark", n=8, m=m)
    K.sphere((x, y + 0.06, z), r, r * 0.7, r, "red_light", n=8, rows=4, top_only=True, m=m)


def insignia(spots, green=True):
    """the team roundel: a ring in the team colour, white, the team colour again, its symbol in white on top"""
    import vehmesh as V
    m = Mesh()
    team = "green_mark" if green else "red_mark"
    for c, n, up, s in spots:
        for rr, mat, off in ((1.0, team, 0.02), (0.74, "white_mark", 0.03), (0.52, team, 0.04)):
            V._flat(m, c, n, up, [(math.cos(a) * s * rr, math.sin(a) * s * rr) for a in np.linspace(0, 2 * math.pi, 20, endpoint=False)], mat, off)
        if green:
            V.chevron(m, np.asarray(c, dtype=float) + np.asarray(n, dtype=float) * 0.025, n, up, s * 0.36, "white_mark")
        else:
            V.triangle_mark(m, np.asarray(c, dtype=float) + np.asarray(n, dtype=float) * 0.025, n, up, s * 0.3, "white_mark")
    return m
