"""Jet cockpits as the pilot sees them (dimensions in metres from the eye, converted to model units by `u`, the metres
per model unit): the tub and the sills, the padded canopy rails, the glare shield with the HUD projector and its
combiner glass, the instrument panel with lit displays, round gauges, the up-front keypad and the caution lights, the
centre pedestal and the side consoles with their switch panels, the canopy bow and its mirrors, the canopy glass seen
from inside, the ejection seat, a centre stick and the throttle. Placed so that with Minecraft's 70 degree view the
glare shield sits just under the horizon, the displays below it and the stick grip at the bottom of the screen.

Also makes 'body_fp': the airframe with the skin over the cockpit opening cut away (from inside it would hide the
panel) and, for an enclosed flight deck (the B-2), the inside of the hull lined so it does not vanish."""
import math

import numpy as np

import meshkit as K
from meshkit import Mesh, v3

GLASS = ("glass", "glass_dark", "glass_clear")


def _flipped(p, mat, inset):
    pts = [np.asarray(q, dtype=float) for q in p["pts"]]
    n = K.poly_normal(pts)
    return {"pts": [q - n * inset for q in reversed(pts)], "mat": mat, "n": None, "smooth": p.get("smooth", True)}


def _inside(c, box):
    (x0, y0, z0), (x1, y1, z1) = box
    return x0 <= c[0] <= x1 and y0 <= c[1] <= y1 and z0 <= c[2] <= z1


def split_body(body, cut_box, glass_box, line_box=None, inset=0.02):
    """body_fp and the inside lining: polys of the skin inside cut_box are dropped (the cockpit opening), the glass inside
    glass_box gets an inward copy, and (enclosed decks) the skin inside line_box an inward dark lining"""
    fp = Mesh()
    lining = Mesh()
    for p in body.polys:
        pts = [np.asarray(q, dtype=float) for q in p["pts"]]
        c = sum(pts) / len(pts)
        glass = p["mat"] in GLASS
        if glass and _inside(c, glass_box):
            lining.polys.append(_flipped(p, "glass_in", inset))
        elif line_box is not None and _inside(c, line_box):
            lining.polys.append(_flipped(p, "pit", inset))
        if not glass and _inside(c, cut_box):
            continue
        fp.polys.append(p)
    return fp, lining


def _in_poly(poly, x, z):
    inside = False
    n = len(poly)
    for i in range(n):
        (x0, z0), (x1, z1) = poly[i], poly[(i + 1) % n]
        if (z0 > z) != (z1 > z) and x < x0 + (x1 - x0) * (z - z0) / (z1 - z0):
            inside = not inside
    return inside


def deck_lining(body, box, inset=0.02, step=0.3):
    """the inside of a hull over an enclosed flight deck: a grid following the skin from below, open where the
    windows are, with side, front and rear walls down to the floor"""
    (x0, y0, z0), (x1, y1, z1) = box
    tops, wins = [], []
    for p in body.polys:
        pts = [np.asarray(q, dtype=float) for q in p["pts"]]
        n = K.poly_normal(pts)
        a = np.array(pts)
        if a[:, 0].max() < x0 or a[:, 0].min() > x1 or a[:, 2].max() < z0 or a[:, 2].min() > z1 or a[:, 1].max() < y0:
            continue
        if p["mat"] in GLASS:
            if n[1] > 0.5:
                wins.append([(q[0], q[2]) for q in pts])
        elif n[1] > 0.05:
            tops.append((pts, n))

    def height(x, z):
        best = None
        for pts, n in tops:
            if not _in_poly([(q[0], q[2]) for q in pts], x, z):
                continue
            q0 = pts[0]
            y = q0[1] - (n[0] * (x - q0[0]) + n[2] * (z - q0[2])) / n[1]
            if y1 >= y >= y0 and (best is None or y > best):
                best = y
        return best

    xs = set(np.round(np.arange(x0, x1 + 1e-6, step), 3)) | {round(x0, 3), round(x1, 3)}
    zs = set(np.round(np.arange(z0, z1 + 1e-6, step), 3)) | {round(z0, 3), round(z1, 3)}
    x0, x1, z0, z1 = round(x0, 3), round(x1, 3), round(z0, 3), round(z1, 3)
    for w in wins:
        xs.update(round(q[0], 3) for q in w)
        zs.update(round(q[1], 3) for q in w)
    xs = sorted(x for x in xs if x0 <= x <= x1)
    zs = sorted(z for z in zs if z0 <= z <= z1)
    H = {(x, z): height(x, z) for x in xs for z in zs}
    # where no skin was found (between polygons, past an edge) take the neighbours' height, else the box top
    for _ in range(6):
        for i, x in enumerate(xs):
            for j, z in enumerate(zs):
                if H[(x, z)] is not None:
                    continue
                nb = [H[(xs[a], zs[b])] for a, b in ((i - 1, j), (i + 1, j), (i, j - 1), (i, j + 1)) if 0 <= a < len(xs) and 0 <= b < len(zs)]
                nb = [h for h in nb if h is not None]
                if nb:
                    H[(x, z)] = max(nb)
    for k in H:
        if H[k] is None:
            H[k] = y1
    m = Mesh()
    for i in range(len(xs) - 1):
        for j in range(len(zs) - 1):
            xa, xb, za, zb = xs[i], xs[i + 1], zs[j], zs[j + 1]
            xm, zm = (xa + xb) / 2, (za + zb) / 2
            if any(_in_poly(w, xm, zm) for w in wins):
                continue
            hs = [H[(xa, za)], H[(xb, za)], H[(xb, zb)], H[(xa, zb)]]
            if any(h is None for h in hs):
                continue
            pts = [v3(xa, hs[0] - inset, za), v3(xb, hs[1] - inset, za), v3(xb, hs[2] - inset, zb), v3(xa, hs[3] - inset, zb)]
            m.add_oriented(pts, "pit", v3(0, -1, 0))
    # walls round the deck, up to the skin
    for x, out in ((x0, -1), (x1, 1)):
        for j in range(len(zs) - 1):
            ha, hb = H[(x, zs[j])], H[(x, zs[j + 1])]
            if ha is None or hb is None:
                continue
            m.add_oriented([v3(x, y0, zs[j]), v3(x, y0, zs[j + 1]), v3(x, hb - inset, zs[j + 1]), v3(x, ha - inset, zs[j])], "pit", v3(-out, 0, 0))
    for z, out in ((z0, -1), (z1, 1)):
        for i in range(len(xs) - 1):
            ha, hb = H[(xs[i], z)], H[(xs[i + 1], z)]
            if ha is None or hb is None:
                continue
            m.add_oriented([v3(xs[i], y0, z), v3(xs[i + 1], y0, z), v3(xs[i + 1], hb - inset, z), v3(xs[i], ha - inset, z)], "pit", v3(0, 0, -out))
    m.add_oriented([v3(x0, y0, z0), v3(x1, y0, z0), v3(x1, y0, z1), v3(x0, y0, z1)], "pit", v3(0, 1, 0))
    return m


def _arc(cx, cy, z, rx, ry, a0=0, a1=180, n=14):
    return [(cx + rx * math.cos(math.radians(a0 + (a1 - a0) * k / n)), cy + ry * math.sin(math.radians(a0 + (a1 - a0) * k / n)), z) for k in range(n + 1)]


def cockpit(body, eye, u, canopy=None, displays=3, wide=False, bow_z=None, sill=-0.5, tub_w=0.40, enclosed=None, station_only=False):
    """returns (parts, stick pivot, throttle pivot); canopy = ((cx, cy, cz), rx, ry, rz) in model units; enclosed =
    (cut box, line box) for a flight deck inside the hull"""
    ex, ey, ez = eye

    def P(x, y, z):
        return (ex + x / u, ey + y / u, ez + z / u)

    def V(x, y, z):
        return v3(*P(x, y, z))

    r = 1.0 / u
    pit = Mesh()
    # ---------------------------------------------------------------- the airframe round it
    body_fp = None
    if station_only:
        pass
    elif enclosed is None:
        (cx, cy, cz), rx, ry, rz = canopy
        hx = rx * 0.985
        cut = ((cx - hx, ey + (sill - 0.75) * r, ez - 0.85 * r), (cx + hx, ey + (sill + 0.1) * r, ez + 0.8 * r))
        gbox = ((cx - rx * 1.1, cy - 0.3, cz - rz * 1.1), (cx + rx * 1.1, cy + ry * 1.2, cz + rz * 1.1))
        body_fp, lining = split_body(body, cut, gbox)
        pit.extend(lining)
    else:
        cut, line = enclosed
        body_fp, lining = split_body(body, ((0, 0, 0), (0, 0, 0)), line)
        pit.extend(lining)
        pit.extend(deck_lining(body, line))

    # ---------------------------------------------------------------- tub, sills, rails
    tub = Mesh()
    K.box(P(-tub_w, -1.05, -0.95), P(tub_w, sill, 0.6), "pit", m=tub, faces="xXyZz" if enclosed is None else "y")
    for p in tub.polys:
        p["pts"].reverse()
        if p.get("n"):
            p["n"] = [-q for q in reversed(p["n"])]
    pit.extend(tub)
    if enclosed is None:
        out = rx * u + 0.04
        for s in (-1, 1):
            xa, xb = sorted((s * tub_w, s * out))
            K.box(P(xa, sill - 0.03, -0.9), P(xb, sill, 0.75), "pit", m=pit, faces="Y")
            # the padded coaming along the inner edge of the sill, the canopy rail outside it
            K.tube([P(s * (tub_w + 0.02), sill + 0.02, -0.75), P(s * (tub_w + 0.02), sill + 0.02, 0.6)], 0.035 * r, "cushion", n=6, m=pit)
            K.tube([P(s * (out - 0.05), sill + 0.015, -0.9), P(s * (out - 0.05), sill + 0.015, 0.7)], 0.02 * r, "frame", n=5, m=pit)
            # canopy actuator and the handholds by the bow
            K.box(P(s * (tub_w + 0.06) - 0.02, sill, 0.15), P(s * (tub_w + 0.06) + 0.02, sill + 0.05, 0.45), "warning", m=pit)

    # ---------------------------------------------------------------- glare shield and HUD
    top = -0.115
    rings = []
    for z, w, dy in ((-0.62, 0.27, 0.0), (-0.78, 0.29, -0.012), (-0.98, 0.31, -0.03)):
        upper = [(x, top + dy - 0.09 * (x / w) ** 2) for x in np.linspace(-w, w, 9)]
        lower = [(x, y - 0.035) for x, y in reversed(upper)]
        rings.append([V(x, y, z) for x, y in upper + lower])
    K.loft(rings, "frame", caps=(True, True), m=pit)
    K.tube([V(x, top + 0.004 - 0.09 * (x / 0.27) ** 2, -0.618) for x in np.linspace(-0.27, 0.27, 9)], 0.012 * r, "rubber", n=4, m=pit)
    # HUD projector, the two posts and the combiner glass
    K.rbox(P(-0.075, top - 0.01, -0.81), P(0.075, top + 0.04, -0.655), 0.01 * r, "frame", seg=1, m=pit)
    K.box(P(-0.06, top + 0.035, -0.79), P(0.06, top + 0.041, -0.68), "glass_dark", m=pit, faces="Y")
    for s in (-1, 1):
        K.tube([P(s * 0.072, top + 0.035, -0.70), P(s * 0.072, 0.045, -0.675)], 0.008 * r, "frame", n=4, m=pit)
    K.tube([P(-0.072, 0.045, -0.675), P(0.072, 0.045, -0.675)], 0.007 * r, "frame", n=4, m=pit)
    K.plate([V(-0.068, top + 0.045, -0.695), V(0.068, top + 0.045, -0.695), V(0.068, 0.04, -0.672), V(-0.068, 0.04, -0.672)], 0.003 * r, "hud_glass", m=pit)

    # ---------------------------------------------------------------- the instrument panel
    pw = 0.26
    p_top, p_bot = (-0.135, -0.70), (-0.55, -0.64)

    def panel_pt(x, y, lift=0.0):
        t = (y - p_top[0]) / (p_bot[0] - p_top[0])
        return V(x, y, p_top[1] + (p_bot[1] - p_top[1]) * t + lift)

    K.plate([panel_pt(-pw, p_bot[0]), panel_pt(pw, p_bot[0]), panel_pt(pw, p_top[0]), panel_pt(-pw, p_top[0])], 0.03 * r, "pit", m=pit)

    def face(x0, x1, y0, y1, mat, lift=0.018):
        K.plate([panel_pt(x0, y0, lift), panel_pt(x1, y0, lift), panel_pt(x1, y1, lift), panel_pt(x0, y1, lift)], 0.012 * r, mat, m=pit)

    def bezel(x0, x1, y0, y1):
        K.plate([panel_pt(x0 - 0.008, y0 - 0.008, 0.012), panel_pt(x1 + 0.008, y0 - 0.008, 0.012), panel_pt(x1 + 0.008, y1 + 0.008, 0.012),
                 panel_pt(x0 - 0.008, y1 + 0.008, 0.012)], 0.012 * r, "frame", m=pit)

    ys = (-0.285, -0.15)
    if wide:
        bezel(-0.24, 0.24, -0.30, -0.15)
        face(-0.235, 0.0, -0.295, -0.155, "mfd_a")
        face(0.0, 0.235, -0.295, -0.155, "mfd_b")
    elif displays == 3:
        for x, mat in ((-0.165, "mfd_a"), (0.0, "mfd_b"), (0.165, "mfd_c")):
            bezel(x - 0.067, x + 0.067, *ys)
            face(x - 0.067, x + 0.067, ys[0], ys[1], mat)
    else:
        for x, mat in ((-0.16, "mfd_a"), (0.16, "mfd_c")):
            bezel(x - 0.07, x + 0.07, *ys)
            face(x - 0.07, x + 0.07, ys[0], ys[1], mat)
        bezel(-0.055, 0.055, -0.27, -0.15)
        face(-0.055, 0.055, -0.27, -0.15, "keypad")
    # the gauges under the displays, the caution panel, switch strips
    gy = -0.355
    for x, mat in ((-0.115, "dial_a"), (0.0, "dial_b"), (0.115, "dial_c")):
        c = panel_pt(x, gy, 0.02)
        nrm = np.array(K.poly_normal([panel_pt(-1, -0.2), panel_pt(1, -0.2), panel_pt(1, -0.1)]))
        if nrm[2] < 0:
            nrm = -nrm
        ring = [c + (np.array([math.cos(a), math.sin(a), 0]) * 0.048 * r) for a in np.linspace(0, 2 * math.pi, 16, endpoint=False)]
        K.plate([q - nrm * 0.004 * r for q in ring], 0.014 * r, "frame", m=pit)
        disc = [c + (np.array([math.cos(a), math.sin(a), 0]) * 0.04 * r) for a in np.linspace(0, 2 * math.pi, 16, endpoint=False)]
        K.plate([q + nrm * 0.006 * r for q in disc], 0.004 * r, mat, m=pit)
    face(0.17, 0.245, -0.39, -0.32, "caution")
    face(-0.245, -0.17, -0.39, -0.32, "switches")
    face(-0.245, 0.245, -0.53, -0.43, "switches", 0.01)
    # centre pedestal down to the floor
    K.box(P(-0.1, -0.95, -0.70), P(0.1, -0.55, -0.6), "pit", m=pit)
    K.box(P(-0.085, -0.75, -0.601), P(0.085, -0.56, -0.598), "switches", m=pit, faces="Z")

    # ---------------------------------------------------------------- side consoles
    for s in (-1, 1):
        xa, xb = sorted((s * 0.2, s * tub_w))
        K.rbox(P(xa, -0.95, -0.62), P(xb, -0.64, 0.3), 0.01 * r, "pit", seg=1, m=pit)
        for k in range(3):
            z = -0.56 + k * 0.28
            K.box(P(xa + 0.012, -0.64, z), P(xb - 0.012, -0.632, z + 0.25), "switches", m=pit, faces="Y")

    # ---------------------------------------------------------------- the canopy bow, its mirrors
    if enclosed is None and not station_only:
        bz = bow_z if bow_z is not None else cz - rz * 0.68
        k = math.sqrt(max(0.05, 1 - ((bz - cz) / rz) ** 2))
        bow = _arc(cx, cy, bz, rx * k * 0.97, ry * k * 0.97, 0, 180, 18)
        K.tube(bow, 0.025 * r, "frame", n=6, m=pit)
        topy = cy + ry * k * 0.97
        for x, w in ((0.0, 0.07), (-0.17, 0.05), (0.17, 0.05)):
            mx = cx + x * r
            my = cy + ry * 0.97 * math.sqrt(max(0.0, k * k - ((mx - cx) / (rx * 0.97)) ** 2)) - 0.035 * r
            K.rbox((mx - w * r, my - 0.025 * r, bz + 0.01 * r), (mx + w * r, my + 0.012 * r, bz + 0.045 * r), 0.006 * r, "frame", seg=1, m=pit)
            K.box((mx - (w - 0.006) * r, my - 0.019 * r, bz + 0.046 * r), (mx + (w - 0.006) * r, my + 0.006 * r, bz + 0.048 * r), "bright", m=pit, faces="Z")
        # the rear bow behind the seat
        rzb = cz + rz * 0.55
        k2 = math.sqrt(max(0.05, 1 - ((rzb - cz) / rz) ** 2))
        K.tube(_arc(cx, cy, rzb, rx * k2 * 0.97, ry * k2 * 0.97, 0, 180, 14), 0.03 * r, "frame", n=6, m=pit)

    # ---------------------------------------------------------------- the ejection seat
    K.rbox(P(-0.24, -0.92, -0.32), P(0.24, -0.82, 0.12), 0.02 * r, "cushion", seg=1, m=pit)
    K.rbox(P(-0.26, -0.86, 0.1), P(0.26, 0.1, 0.3), 0.03 * r, "cushion", seg=1, m=pit)
    K.rbox(P(-0.2, 0.0, 0.16), P(0.2, 0.34, 0.4), 0.03 * r, "pit", seg=1, m=pit)
    K.box(P(-0.07, -0.88, -0.36), P(0.07, -0.84, -0.3), "warning", m=pit)

    # ---------------------------------------------------------------- centre stick and throttle
    stick = Mesh()
    piv = P(0.0, -0.95, -0.46)
    k0 = P(0.0, -0.44, -0.52)
    K.cylinder("y", (piv[0], piv[2]), 0.06 * r, piv[1], piv[1] + 0.05 * r, "rubber", n=12, r1=0.03 * r, m=stick)
    K.tube([piv, k0], 0.016 * r, "frame", n=6, m=stick)
    gx, gy0, gz = k0
    # the grip: contoured, a trigger in front, a hat and the red pickle button on top
    K.rbox((gx - 0.022 * r, gy0, gz - 0.028 * r), (gx + 0.022 * r, gy0 + 0.12 * r, gz + 0.03 * r), 0.012 * r, "rubber", seg=1, m=stick)
    K.rbox((gx - 0.018 * r, gy0 + 0.1 * r, gz - 0.034 * r), (gx + 0.018 * r, gy0 + 0.14 * r, gz + 0.022 * r), 0.01 * r, "frame", seg=1, m=stick)
    K.box((gx - 0.006 * r, gy0 + 0.05 * r, gz - 0.04 * r), (gx + 0.006 * r, gy0 + 0.08 * r, gz - 0.027 * r), "pit_light", m=stick)
    K.cylinder("y", (gx - 0.008 * r, gz - 0.012 * r), 0.007 * r, gy0 + 0.14 * r, gy0 + 0.15 * r, "red", n=6, m=stick)
    K.cylinder("y", (gx + 0.008 * r, gz - 0.004 * r), 0.006 * r, gy0 + 0.14 * r, gy0 + 0.148 * r, "pit_light", n=6, m=stick)
    thr = Mesh()
    tpiv = P(-0.31, -0.64, -0.12)
    ttop = P(-0.31, -0.53, -0.16)
    K.tube([tpiv, ttop], 0.012 * r, "pit_light", n=6, m=thr)
    tx, ty, tz = ttop
    K.rbox((tx - 0.03 * r, ty - 0.01 * r, tz - 0.04 * r), (tx + 0.03 * r, ty + 0.05 * r, tz + 0.04 * r), 0.012 * r, "rubber", seg=1, m=thr)
    K.box((tx - 0.034 * r, ty + 0.01 * r, tz - 0.01 * r), (tx - 0.03 * r, ty + 0.03 * r, tz + 0.01 * r), "red", m=thr)
    return {"cockpit": pit, "stick": stick, "throttle": thr, "body_fp": body_fp}, piv, tpiv


def place_seat(eye, u):
    """where the pilot's hips sit (the passenger attachment): the eye less 0.8 m"""
    return (eye[0], eye[1] - 0.8 / u, eye[2])
