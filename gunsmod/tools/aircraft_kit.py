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
