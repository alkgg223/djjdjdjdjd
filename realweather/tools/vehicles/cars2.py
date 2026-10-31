"""The civilian vehicles, built as faceted realistic bodies with drawn detail (see carbody.py): family sedan,
storm-chasing pickup, full-size SUV, muscle coupe and sport sedan. Same coordinates and outputs as vehicles.py
(metres, front -Z, +X right, ground y = 0)."""
import math

import numpy as np

import carbody as C
import decals as D
import meshkit as K
import vehicles as V
from meshkit import Mesh, v3


# ------------------------------------------------------------------------------------------------ shared pieces
def wheel(r, width, rim="rim_alloy", caliper="dark", rim_r=0.68, tyre_n=24):
    """tyre with a drawn sidewall, an open rim face (drawn spokes) over a brake disc and caliper; axle along X,
    outer face toward +X"""
    m = Mesh()
    rw = width / 2
    prof = [(-rw * 0.86, r * rim_r), (-rw, r * 0.76), (-rw, r * 0.92), (-rw * 0.86, r), (rw * 0.86, r), (rw, r * 0.92), (rw, r * 0.76),
            (rw * 0.86, r * rim_r)]
    rings = [[v3(x, rad * math.sin(2 * math.pi * k / tyre_n), rad * math.cos(2 * math.pi * k / tyre_n)) for k in range(tyre_n)]
             for x, rad in prof]
    K.loft(rings, "rubber", caps=(False, False), m=m)
    # Tread blocks across the crown.
    for k in range(tyre_n * 2):
        if k % 2:
            continue
        a0 = math.pi * (k + 0.15) / tyre_n
        a1 = math.pi * (k + 0.85) / tyre_n
        for x0, x1 in ((-rw * 0.8, -rw * 0.06), (rw * 0.06, rw * 0.8)):
            q = [v3(x, (r + 0.006) * math.sin(a), (r + 0.006) * math.cos(a)) for x, a in ((x0, a0), (x1, a0), (x1, a1), (x0, a1))]
            m.add_oriented(q, "rubber", (q[0] + q[2]) / 2, smooth=False)
    # Sidewall drawing.
    m.extend(V.disc_face((rw + 0.004, 0, 0), r * 0.985, "x", 1, "tyre_side", n=tyre_n))
    # Rim barrel, brake disc and caliper behind the open spokes.
    rr = r * rim_r
    K.cylinder("x", (0, 0), rr, -rw * 0.8, rw * 0.62, "dark", n=tyre_n, caps=(True, False), m=m)
    m.extend(V.disc_face((rw * 0.05, 0, 0), rr * 0.82, "x", 1, "brake_disc", n=20))
    K.rbox((rw * 0.06, rr * 0.3, -rr * 0.32), (rw * 0.32, rr * 0.82, rr * 0.32), 0.015, caliper, seg=1, m=m)
    m.extend(V.disc_face((rw * 0.62, 0, 0), rr * 1.01, "x", 1, rim, n=tyre_n))
    K.cylinder("x", (0, 0), 0.05, rw * 0.6, rw * 0.66, "chrome" if rim in ("rim_alloy", "rim_multi") else "dark", n=10, m=m)
    return m


def door_parts(v, car, W, handle_zs, card_top, handle_mat="chrome", trim="dark"):
    """doors (hinged at their front edge) with handles, panel gaps and inner door cards; each door window rides
    on its door"""
    C.seams(car, None, car.sill(0) + car.rocker - 0.01, lin_belt(car, 0) + 0.015)
    hinges = []
    for i, (z0, z1) in enumerate(car.door_spans):
        for side in ("r", "l"):
            s = 1 if side == "r" else -1
            name = "door_" + ["f", "r"][i] + side
            d = car.doors.get(name, Mesh())
            zh = handle_zs[i]
            x, y = car.section(zh)[3]
            y -= 0.035
            xo = s * (x + 0.004)
            K.rbox((min(xo, xo + s * 0.024), y - 0.018, zh - 0.08), (max(xo, xo + s * 0.024), y + 0.018, zh + 0.08), 0.008, handle_mat,
                   seg=1, m=d)
            # Door card inside: textured panel, armrest with the window switches, chrome pull.
            xi = s * (W / 2 - 0.09)
            y0c = car.sill(0) + 0.12
            K.rbox((xi - 0.025, y0c, z0 + 0.05), (xi + 0.025, card_top, z1 - 0.05), 0.02, "dash", seg=1, m=d)
            Q(d, TRIMS[trim]["card"], (xi - s * 0.0255, (y0c + card_top) / 2, (z0 + z1) / 2), (0, 0, s * ((z1 - z0) / 2 - 0.07)),
              (0, (card_top - y0c) / 2 - 0.01, 0), flip=s < 0)
            K.rbox((xi - s * 0.05 - 0.035, card_top - 0.24, z0 + 0.18), (xi - s * 0.05 + 0.035, card_top - 0.17, z1 - 0.15), 0.02,
                   TRIMS[trim]["seat"], seg=1, m=d)
            if i == 0:
                Q(d, "switch_panel", (xi - s * 0.05, card_top - 0.168, z0 + 0.32), (0, 0, 0.065), (0.03, 0, 0))
            K.rbox((xi - s * 0.03 - 0.01, card_top - 0.14, z0 + 0.12), (xi - s * 0.03 + 0.01, card_top - 0.11, z0 + 0.2), 0.006, "chrome",
                   seg=1, m=d)
            v.part(name, d.translate((-s * W / 2, 0, -z0)))
            g = car.door_glass.get(name + "_glass")
            if g is not None:
                v.part(name + "_glass", g.translate((-s * W / 2, 0, -z0)))
        hinges.append((W / 2, 0.0, z0))
    v.geo["doors"] = hinges


def lin_belt(car, z):
    return C.lin(car.belt, z)


def wheels_geo(b, x):
    return [(-x, b.wheel_r, b.wz_f), (x, b.wheel_r, b.wz_f), (-x, b.wheel_r, b.wz_r), (x, b.wheel_r, b.wz_r)]


def fin(m, car, z, mat="paint"):
    """shark-fin antenna on the roof"""
    y = car.top_y(z, 0)
    prof = [(z - 0.1, y - 0.005), (z + 0.08, y - 0.005), (z + 0.07, y + 0.06), (z + 0.03, y + 0.07)]
    pts_l = [v3(-0.03, yy, zz) for zz, yy in prof]
    pts_r = [v3(0.03, yy, zz) for zz, yy in prof]
    m.add_oriented(pts_r, mat, v3(1, 0, 0), smooth=False)
    m.add_oriented(pts_l, mat, v3(-1, 0, 0), smooth=False)
    for i in range(len(prof)):
        a, b = i, (i + 1) % len(prof)
        q = [pts_r[a], pts_r[b], pts_l[b], pts_l[a]]
        c = (q[0] + q[2]) / 2
        m.add_oriented(q, mat, v3(0, c[1] - (y + 0.02), c[2] - z), smooth=False)


def tips(m, xs, y, z, r=0.04, length=0.16, mat="chrome"):
    out = []
    for x in xs:
        K.cylinder("z", (x, y), r, z - length, z, mat, n=12, caps=(False, False), m=m)
        K.cylinder("z", (x, y), r * 0.78, z - length, z - 0.005, "black", n=12, caps=(False, True), m=m)
        out.append((x, y, z))
    return out


def finish(v, car, body, glass, W, wiper_y, wiper_len, wheel_x, wheel_mesh, lights, exhausts, cover_roof=True):
    ws = car.windshield()
    V.wheel_wells(body, car.b, body, 0.22)
    car.underbody(body)
    v.part("body", body)
    v.part("glass", glass)
    v.part("wheel", wheel_mesh)
    roof = car.top_y((car.zw1 + car.zr1) / 2, 0)
    xr = car.section((car.zw1 + car.zr1) / 2)[8][0] - 0.03
    hood_z0 = -car.half + 0.35
    hood_z1 = car.zw0 - 0.1
    hx = C.lin(car.plan, hood_z1) - 0.12
    covers = Mesh()
    if cover_roof:
        covers.extend(V.cover([(-xr, roof + 0.0, car.zw1 + 0.08), (xr, roof + 0.0, car.zw1 + 0.08), (xr, roof + 0.0, car.zr1 - 0.08),
                               (-xr, roof + 0.0, car.zr1 - 0.08)]))
    covers.extend(V.cover([(-hx, car.top_y(hood_z0, hx), hood_z0), (hx, car.top_y(hood_z0, hx), hood_z0),
                           (hx, car.top_y(hood_z1, hx), hood_z1), (-hx, car.top_y(hood_z1, hx), hood_z1)]))
    v.part("cover", covers)
    zw0 = car.zw0
    v.geo.update(wheels=wheels_geo(car.b, wheel_x), wheel_r=car.b.wheel_r, windshield=ws,
                 wipers=[(-0.42, wiper_y, zw0 - 0.03), (0.16, wiper_y, zw0 - 0.03)], wiper_len=wiper_len,
                 size=(W, roof, car.length), lights_front=lights, exhausts=exhausts)


def front_rear(car, det, hl, tl, grille, hl_box, tl_box, grille_box, plate_f, plate_r, intake=None, fog=None, badge_f=None,
               badge_r=None, markers=True, hl_dot=0.42, tl_dot=0.42):
    """lamps, grille, plates and badges drawn onto the nose and tail"""
    sk = car.skins()
    h = car.half
    x0, x1, y0, y1 = hl_box
    C.pair(sk, det, hl, x0, x1, y0, y1, -h - 0.1, -h + 0.45, "-z", min_dot=hl_dot, offset=0.007)
    x0, x1, y0, y1 = tl_box
    C.pair(sk, det, tl, x0, x1, y0, y1, h - 0.45, h + 0.1, "+z", min_dot=tl_dot)
    gx, gy0, gy1 = grille_box
    C.project(sk, det, grille, (-gx, gy0, -h - 0.1), (gx, gy1, -h + 0.3), "-z", min_dot=0.5)
    if intake:
        ix, iy0, iy1 = intake
        C.project(sk, det, "vent_mesh", (-ix, iy0, -h - 0.1), (ix, iy1, -h + 0.3), "-z", min_dot=0.5)
    if fog:
        fx0, fx1, fy0, fy1 = fog
        C.pair(sk, det, "fog_light", fx0, fx1, fy0, fy1, -h - 0.1, -h + 0.3, "-z", min_dot=0.5, offset=0.009)
    py0, py1 = plate_f
    C.project(sk, det, "plate_front", (-0.24, py0, -h - 0.1), (0.24, py1, -h + 0.2), "-z", min_dot=0.6, offset=0.011)
    py0, py1 = plate_r
    C.project(sk, det, "plate_rear", (-0.24, py0, h - 0.2), (0.24, py1, h + 0.1), "+z", min_dot=0.6, offset=0.011)
    if badge_f:
        C.project(sk, det, "badge", (-0.07, badge_f - 0.05, -h - 0.1), (0.07, badge_f + 0.05, -h + 0.3), "-z", min_dot=0.4, offset=0.012)
    if badge_r:
        C.project(sk, det, "badge", (-0.07, badge_r - 0.05, h - 0.3), (0.07, badge_r + 0.05, h + 0.1), "+z", min_dot=0.4, offset=0.012)
    if markers:
        zf = -h + 0.22
        y = car.section(zf)[3][1]
        C.sides(sk, det, "marker_amber", y - 0.01, y + 0.025, zf - 0.07, zf + 0.03, offset=0.006)


# ------------------------------------------------------------------------------------------------ interiors
TRIMS = {
    "dark": dict(seat="seat", quilt="quilt_dark", card="door_card_dark"),
    "tan": dict(seat="seat_tan", quilt="quilt_tan", card="door_card_tan"),
    "red": dict(seat="leather_red", quilt="quilt_red", card="door_card_red"),
}
Q = V.facing_quad


def seat(m, car, x, floor_y, z, t, sport=False):
    """front seat: base and rails, cushion with side bolsters and a quilted insert, reclined backrest with bolsters,
    headrest on two posts (or a one-piece sports back)"""
    mat, quilt = t["seat"], t["quilt"]
    K.rbox((x - 0.19, floor_y, z - 0.2), (x + 0.19, floor_y + 0.1, z + 0.18), 0.02, "dark", seg=1, m=m)
    for s in (-1, 1):
        K.box((x + s * 0.15 - 0.015, floor_y, z - 0.3), (x + s * 0.15 + 0.015, floor_y + 0.03, z + 0.25), "steel", m=m)
    K.rbox((x - 0.19, floor_y + 0.1, z - 0.27), (x + 0.19, floor_y + 0.2, z + 0.2), 0.045, mat, m=m)
    bol = 0.3 if sport else 0.26
    for s in (-1, 1):
        K.rbox((x + s * 0.21 - 0.05, floor_y + 0.11, z - 0.26), (x + s * 0.21 + 0.05, floor_y + bol, z + 0.18), 0.04, mat, m=m)
    Q(m, quilt, (x, floor_y + 0.202, z - 0.04), (0.14, 0, 0), (0, 0, -0.2))
    back = Mesh()
    top = floor_y + (1.02 if sport else 0.86)
    top = min(top, car.top_y(z + 0.3, abs(x)) - 0.12 - (0 if sport else 0.2))
    K.rbox((x - 0.19, floor_y + 0.21, z + 0.18), (x + 0.19, top, z + 0.29), 0.05, mat, m=back)
    K.rbox((x - 0.2, floor_y + 0.24, z + 0.285), (x + 0.2, top - 0.02, z + 0.31), 0.015, "dark", seg=1, m=back)
    for s in (-1, 1):
        K.rbox((x + s * 0.21 - 0.05, floor_y + 0.24, z + 0.13), (x + s * 0.21 + 0.05, top - (0.05 if sport else 0.08), z + 0.3), 0.04, mat,
               m=back)
    Q(back, quilt, (x, (floor_y + 0.26 + top - 0.1) / 2, z + 0.177), (-0.14, 0, 0), (0, (top - floor_y - 0.36) / 2, 0))
    if sport:
        # One-piece back: a slot for the harness, the headrest moulded in.
        K.rbox((x - 0.13, top - 0.02, z + 0.19), (x + 0.13, top + 0.12, z + 0.29), 0.05, mat, m=back)
        K.box((x - 0.06, top - 0.04, z + 0.175), (x - 0.03, top + 0.02, z + 0.3), "black", m=back)
        K.box((x + 0.03, top - 0.04, z + 0.175), (x + 0.06, top + 0.02, z + 0.3), "black", m=back)
    else:
        hr = min(top + 0.2, car.top_y(z + 0.25, abs(x)) - 0.07)
        K.rbox((x - 0.13, hr - 0.17, z + 0.19), (x + 0.13, hr, z + 0.29), 0.045, mat, m=back)
        for s in (-1, 1):
            K.box((x + s * 0.07 - 0.008, top - 0.03, z + 0.235), (x + s * 0.07 + 0.008, hr - 0.15, z + 0.245), "chrome", m=back)
    back.rotate("x", -11, origin=(0, floor_y + 0.21, z + 0.24))
    m.extend(back)


def rear_bench(m, car, floor_y, zr, t, xr):
    mat, quilt = t["seat"], t["quilt"]
    K.rbox((-xr, floor_y, zr - 0.25), (xr, floor_y + 0.1, zr + 0.2), 0.02, "dark", seg=1, m=m)
    K.rbox((-xr, floor_y + 0.08, zr - 0.29), (xr, floor_y + 0.23, zr + 0.2), 0.05, mat, m=m)
    for x in (-0.4, 0.4):
        Q(m, quilt, (x, floor_y + 0.232, zr - 0.05), (0.15, 0, 0), (0, 0, -0.18))
    back = Mesh()
    top = min(floor_y + 0.84, min(car.top_y(zr + 0.32, x) for x in (0.0, 0.5)) - 0.16)
    K.rbox((-xr, floor_y + 0.22, zr + 0.2), (xr, top, zr + 0.33), 0.05, mat, m=back)
    for x in (-0.4, 0.4):
        Q(back, quilt, (x, (floor_y + 0.3 + top - 0.06) / 2, zr + 0.197), (-0.15, 0, 0), (0, (top - floor_y - 0.36) / 2, 0))
    for x, w in ((-0.42, 0.12), (0.0, 0.09), (0.42, 0.12)):
        hr = min(top + 0.17, car.top_y(zr + 0.28, abs(x)) - 0.08)
        if hr - top > 0.07:
            K.rbox((x - w, hr - 0.14, zr + 0.21), (x + w, hr, zr + 0.3), 0.04, mat, m=back)
            for s in (-1, 1):
                K.box((x + s * w * 0.5 - 0.007, top - 0.02, zr + 0.25), (x + s * w * 0.5 + 0.007, hr - 0.12, zr + 0.26), "chrome", m=back)
    # Centre armrest folded down, with cupholders.
    K.rbox((-0.12, floor_y + 0.23, zr - 0.08), (0.12, floor_y + 0.33, zr + 0.22), 0.04, mat, m=back)
    back.rotate("x", -9, origin=(0, floor_y + 0.22, zr + 0.26))
    m.extend(back)


def interior2(v, m, car, W, floor_y, belt, rear, trim="dark", accent="piano", sport=False, driver_x=-0.37, front_z=1.0, rear_z=1.85,
              mats=True):
    """dashboard with instrument binnacle, touch screen, vents and climate panel; console with the selector and an
    armrest; seats, rear bench, headliner, sun visors, mirror, dome light and seat belts"""
    t = TRIMS[trim]
    zw0 = car.zw0
    g = {}
    zl = zw0 + 0.44
    yb = belt

    def inner(z):
        return C.lin(car.plan, z) - 0.11

    def ws(z):
        return car.top_y(z, 0.3) - 0.035
    xi = min(inner(zw0 + 0.05), inner(zl))
    # Floor, transmission tunnel, mats.
    z_end = zw0 + (rear_z + 0.45 if rear else front_z + 0.45)
    K.box((-xi, floor_y - 0.03, zw0 - 0.1), (xi, floor_y, z_end), "carpet", m=m)
    K.rbox((-0.14, floor_y - 0.01, zw0 + 0.1), (0.14, floor_y + 0.1, z_end - 0.35), 0.03, "carpet", seg=1, m=m)
    if mats:
        for x in (-0.4, 0.4):
            Q(m, "floor_mat", (x, floor_y + 0.003, zw0 + 0.45), (0.2, 0, 0), (0, 0, -0.2))
            if rear:
                Q(m, "floor_mat", (x, floor_y + 0.003, zw0 + rear_z - 0.45), (0.2, 0, 0), (0, 0, -0.17))
    # Dashboard: an extruded profile under the windshield.
    zf = zw0 + 0.05
    prof = [(zf, min(yb, ws(zf))), (zw0 + 0.22, min(yb + 0.03, ws(zw0 + 0.22))), (zl, min(yb - 0.01, ws(zl))), (zl + 0.03, yb - 0.05),
            (zl, yb - 0.1), (zl - 0.03, yb - 0.26), (zl - 0.06, floor_y + 0.5), (zw0 + 0.1, floor_y + 0.52), (zf, floor_y + 0.5)]
    K.prism(prof, "x", -xi, xi, "dash", bevel=0.012, m=m)
    # Accent strip across the face, glovebox seam.
    K.box((-xi + 0.04, yb - 0.135, zl - 0.012), (xi - 0.04, yb - 0.112, zl + 0.008), accent, m=m)
    K.box((0.28, yb - 0.27, zl - 0.04), (0.66, yb - 0.262, zl - 0.02), "black", m=m)
    # Instrument binnacle with the cluster and the two dials.
    xd = driver_x
    K.rbox((xd - 0.23, yb - 0.17, zl - 0.12), (xd + 0.23, yb + 0.03, zl + 0.03), 0.03, "dash", m=m)
    K.rbox((xd - 0.24, yb + 0.01, zl - 0.12), (xd + 0.24, yb + 0.055, zl + 0.085), 0.02, "dash", m=m)
    Q(m, "cluster", (xd, yb - 0.07, zl + 0.033), (0.2, 0, 0), (0, 0.075, 0))
    gz = zl + 0.036
    m.extend(V.disc_face((xd - 0.1, yb - 0.07, gz), 0.06, "z", 1, "gauge"))
    m.extend(V.disc_face((xd + 0.1, yb - 0.07, gz), 0.053, "z", 1, "gauge_rpm"))
    g["speedo"] = (xd - 0.1, yb - 0.07, gz + 0.003, 0.06)
    g["tacho"] = (xd + 0.1, yb - 0.07, gz + 0.003, 0.053)
    # Touch screen on top of the dash (the radar screen on the chase vehicles).
    zs = zl - 0.05
    K.rbox((-0.175, yb - 0.01, zs - 0.03), (0.175, yb + 0.155, zs), 0.012, "piano", seg=1, m=m)
    Q(m, "infotainment", (0, yb + 0.073, zs + 0.002), (0.158, 0, 0), (0, 0.068, 0))
    g["screen"] = [(-0.158, yb + 0.005, zs + 0.002), (0.158, yb + 0.005, zs + 0.002), (0.158, yb + 0.141, zs + 0.002),
                   (-0.158, yb + 0.141, zs + 0.002)]
    # Centre stack: vents, climate panel; vents at the outer ends of the dash.
    K.rbox((-0.15, floor_y + 0.12, zl - 0.12), (0.15, yb - 0.1, zl + 0.02), 0.02, "dash", m=m)
    K.box((-0.152, yb - 0.32, zl + 0.0), (0.152, yb - 0.31, zl + 0.022), accent, m=m)
    for x in (-0.075, 0.075):
        Q(m, "vent", (x, yb - 0.16, zl + 0.023), (0.062, 0, 0), (0, 0.031, 0))
    Q(m, "climate", (0, yb - 0.25, zl + 0.023), (0.13, 0, 0), (0, 0.048, 0))
    for x in (-(xi - 0.11), xi - 0.11):
        Q(m, "vent", (x, yb - 0.175, zl - 0.004), (0.075, 0, 0), (0, 0.036, 0))
    # Steering column.
    sw = (xd, yb - 0.04, zw0 + 0.6)
    K.tube([v3(xd, yb - 0.15, zl - 0.02), v3(xd, yb - 0.085, zw0 + 0.53)], 0.04, "dash", n=10, m=m)
    g["steer"] = sw
    g["pedals"] = [(xd + 0.15, floor_y + 0.44, zw0 + 0.1), (xd - 0.02, floor_y + 0.44, zw0 + 0.1)]
    # Console with the selector panel, armrest and rear vents.
    zc1 = zw0 + front_z + 0.22
    K.rbox((-0.13, floor_y, zl - 0.06), (0.13, floor_y + 0.3, zc1), 0.03, "dash", m=m)
    Q(m, "gear_panel", (0, floor_y + 0.302, zw0 + 0.75), (0.095, 0, 0), (0, 0, -0.19))
    g["gear"] = (0.0, floor_y + 0.3, zw0 + 0.64)
    K.rbox((-0.13, floor_y + 0.3, zw0 + front_z - 0.12), (0.13, floor_y + 0.38, zc1), 0.035, t["seat"], m=m)
    if rear:
        Q(m, "vent", (0, floor_y + 0.2, zc1 + 0.002), (0.07, 0, 0), (0, 0.035, 0))
    # Seats.
    seats = [(driver_x, zw0 + front_z), (-driver_x, zw0 + front_z)]
    for x, z in seats:
        seat(m, car, x, floor_y, z, t, sport)
    g["seats"] = [(x, floor_y + 0.06, z) for x, z in seats]
    zr = zw0 + rear_z
    if rear:
        xr = min(inner(zr), inner(zr + 0.3)) - 0.02
        rear_bench(m, car, floor_y, zr, t, xr)
        g["seats"] += [(-0.4, floor_y + 0.02, zr), (0.4, floor_y + 0.02, zr)]
    # Headliner under the roof, down to the top of the side windows.
    zs_h = [car.zw1 + 0.02 + (car.zr1 - car.zw1 - 0.04) * k / 8 for k in range(9)]
    rows = []
    for z in zs_h:
        sec = car.section(z)
        x6, y6 = sec[6]
        yt = sec[-1][1]
        rows.append([v3(-x6 + 0.015, y6 + 0.01, z), v3(-x6 * 0.5, min(y6 + 0.035, yt - 0.04), z), v3(0, min(y6 + 0.045, yt - 0.035), z),
                     v3(x6 * 0.5, min(y6 + 0.035, yt - 0.04), z), v3(x6 - 0.015, y6 + 0.01, z)])
    for a in range(len(rows) - 1):
        for j in range(4):
            m.add_oriented([rows[a][j], rows[a][j + 1], rows[a + 1][j + 1], rows[a + 1][j]], "headliner", v3(0, -1, 0), smooth=False)
    yh = rows[0][2][1]
    zv = car.zw1 + 0.04
    for s in (-1, 1):
        K.rbox((s * 0.12, rows[1][1][1] - 0.035, zv), (s * 0.58, rows[1][1][1] - 0.012, zv + 0.17), 0.01, "headliner", seg=1, m=m)
    # Rear-view mirror at the top of the windshield, dome light.
    K.tube([v3(0, yh - 0.005, car.zw1 - 0.03), v3(0, yh - 0.07, car.zw1 + 0.01)], 0.012, "dark", n=6, m=m)
    K.rbox((-0.12, yh - 0.12, car.zw1 - 0.005), (0.12, yh - 0.06, car.zw1 + 0.03), 0.015, "dark", seg=1, m=m)
    Q(m, "chrome", (0, yh - 0.09, car.zw1 + 0.031), (0.11, 0, 0), (0, 0.026, 0))
    Q(m, "dome_light", (0, rows[3][2][1] - 0.003, zs_h[3]), (0.06, 0, 0), (0, 0, 0.03))
    # Seat belts from the B-pillars.
    if len(car.windows) > 1:
        zB = (car.windows[0][1] + car.windows[1][0]) / 2
    else:
        zB = car.windows[0][1] + 0.04
    for x, z in seats:
        s = 1 if x > 0 else -1
        x6, y6 = car.section(zB)[6]
        K.tube([v3(s * (x6 - 0.04), y6 - 0.06, zB), v3(s * (abs(x) + 0.23), floor_y + 0.28, z + 0.2)], 0.012, "black", n=4, m=m)
    v.geo.update(g)
    return g


# ------------------------------------------------------------------------------------------------ family sedan
def sedan():
    v = V.Vehicle("sedan")
    W = 1.84
    b = V.Body(length=4.8, width=W, sill=0.2, bumper_low=0.27, over_f=0.9, over_r=1.0, wz_f=-1.45, wz_r=1.42, wheel_r=0.34)
    h = 2.4
    plan = [(-h, 0.72), (-h + 0.1, 0.84), (-h + 0.3, 0.9), (-1.85, 0.915), (1.9, 0.92), (h - 0.22, 0.9), (h - 0.07, 0.85), (h, 0.79)]
    belt = [(-h, 0.55), (-h + 0.2, 0.74), (-1.5, 0.83), (-0.8, 0.9), (1.2, 0.98), (1.9, 1.0), (h - 0.12, 0.98), (h, 0.92)]
    top = [(-h, 0.56), (-h + 0.2, 0.75), (-1.6, 0.86), (-0.8, 0.93), (0.0, 1.41), (0.5, 1.45), (0.95, 1.42), (1.8, 1.02),
           (h - 0.15, 1.02), (h, 0.94)]
    zw0, zw1, zr1, zb = -0.8, 0.0, 0.95, 1.8
    car = C.Car(b, plan, belt, top, zw0, zw1, zr1, zb, windows=[(-0.66, 0.36), (0.44, 1.28)], tumble=0.17, seal_mat="chrome",
                rocker_mat="trim")
    spans = [(-0.74, 0.4), (0.4, 1.28)]
    body, glass = car.build(doors=spans)
    det = Mesh()
    front_rear(car, det, "hl_slim", "tl_sport", "gr_cascade", (0.3, 0.95, 0.47, 0.72), (0.3, 0.95, 0.78, 0.97),
               (0.42, 0.37, 0.53), (0.29, 0.37), (0.5, 0.6), intake=(0.66, 0.285, 0.36), fog=(0.58, 0.7, 0.29, 0.36), badge_f=0.47,
               badge_r=0.86)
    # Bumper lip and rear diffuser, a small lip spoiler, the roof antenna.
    K.rbox((-0.72, 0.25, -h - 0.02), (0.72, 0.29, -h + 0.18), 0.012, "trim", seg=1, m=det)
    K.rbox((-0.7, 0.25, h - 0.2), (0.7, 0.32, h + 0.02), 0.012, "trim", seg=1, m=det)
    yd = car.top_y(h - 0.08, 0)
    K.rbox((-0.62, yd - 0.005, h - 0.14), (0.62, yd + 0.025, h - 0.02), 0.01, "paint", seg=1, m=det)
    fin(det, car, 1.0)
    C.mirror(det, car, zw0 + 0.1)
    door_parts(v, car, W, [0.2, 1.05], 0.9)
    interior2(v, det, car, W, 0.22, 0.93, True, trim="dark", accent="piano")
    ex = tips(det, [0.52], 0.27, h + 0.04, 0.035)
    body.extend(det)
    finish(v, car, body, glass, W, 0.95, 0.56, 0.79, wheel(b.wheel_r, 0.23, "rim_alloy"), [(-0.62, 0.6, -h), (0.62, 0.6, -h)], ex)
    return v


# ------------------------------------------------------------------------------------------------ chase pickup
def pickup():
    v = V.Vehicle("pickup")
    W = 2.02
    b = V.Body(length=5.6, width=W, sill=0.42, bumper_low=0.46, over_f=0.95, over_r=1.15, wz_f=-1.75, wz_r=1.7, wheel_r=0.42)
    h = 2.8
    plan = [(-h, 0.93), (-h + 0.1, 0.99), (-h + 0.3, 1.01), (h - 0.1, 1.01), (h, 0.98)]
    belt = [(-h, 1.08), (-h + 0.18, 1.17), (-1.0, 1.22), (0.9, 1.25), (h, 1.25)]
    top = [(-h, 1.12), (-h + 0.16, 1.22), (-1.1, 1.29), (-1.0, 1.3), (-0.35, 1.9), (0.3, 1.93), (0.85, 1.9), (0.9, 1.28), (h, 1.27)]
    zw0, zw1, zr1, zb = -1.0, -0.35, 0.85, 0.9
    bed0 = 1.02
    car = C.Car(b, plan, belt, top, zw0, zw1, zr1, zb, windows=[(-0.85, 0.08), (0.17, 0.8)], tumble=0.1, rail=0.06, shoulder=0.14,
                rocker=0.12, hood_crease=0.55, cowl=0.3, tail=0.001, open_top=(bed0, h - 0.07), rocker_mat="trim", seal_mat="trim",
                c_mat="trim", extra=[bed0 - 0.06])
    spans = [(-0.95, 0.12), (0.12, 0.86)]
    body, glass = car.build(doors=spans)
    det = Mesh()
    front_rear(car, det, "hl_truck", "tl_tall", "gr_truck", (0.56, 1.03, 0.94, 1.13), (0.86, 1.03, 0.8, 1.24), (0.5, 0.66, 1.1),
               (0.5, 0.6), (0.62, 0.72), intake=(0.75, 0.48, 0.62), fog=(0.66, 0.8, 0.5, 0.58), badge_f=0.88, badge_r=1.08, tl_dot=0.3)
    C.arch_flares(det, car, "trim", 0.08, 0.03)
    # Bed: liner floor, inner walls, front wall, wheel housings.
    fy = 0.8
    xi = C.lin(car.plan, 2.0) - 0.07
    K.box((-xi, fy, bed0 - 0.06), (xi, fy + 0.03, h - 0.07), "dark", m=det)
    for s in (-1, 1):
        K.box((s * xi - 0.025, fy, bed0 - 0.06), (s * xi + 0.025, 1.255, h - 0.07), "dark", m=det)
        K.box((s * (xi - 0.03), fy, b.wz_r - 0.5), (s * (xi - 0.23), 0.98, b.wz_r + 0.5), "dark", m=det)
    K.box((-xi, fy, bed0 - 0.08), (xi, 1.255, bed0 - 0.04), "dark", m=det)
    K.box((-xi, fy, h - 0.1), (xi, 1.24, h - 0.07), "dark", m=det)
    # Running boards, tow hitch, bumpers, bull bar.
    for s in (-1, 1):
        K.rbox((s * (1.0 + 0.03) - 0.1, 0.38, -0.92), (s * (1.0 + 0.03) + 0.1, 0.44, 0.86), 0.015, "trim", seg=1, m=det)
    K.rbox((-0.98, 0.42, -h - 0.06), (0.98, 0.6, -h + 0.12), 0.03, "grey", seg=1, m=det)
    K.rbox((-0.98, 0.44, h - 0.1), (0.98, 0.62, h + 0.08), 0.03, "grey", seg=1, m=det)
    K.box((-0.04, 0.42, h + 0.08), (0.04, 0.48, h + 0.25), "steel", m=det)
    for x in (-0.45, 0.45):
        K.tube([v3(x, 0.55, -h - 0.1), v3(x, 1.02, -h - 0.14), v3(x * 0.85, 1.12, -h + 0.02)], 0.035, "trim", n=8, m=det)
    K.tube([v3(-0.45, 1.02, -h - 0.14), v3(0.45, 1.02, -h - 0.14)], 0.035, "trim", n=8, m=det)
    C.mirror(det, car, zw0 + 0.09, mat="trim")
    # Chase gear: roof bars with a light bar, GPS puck, antennas; instrument mast and boxes in the bed.
    roof = car.top_y(0.3, 0)
    xr = car.section(0.3)[8][0] - 0.05
    for z in (zw1 + 0.15, zr1 - 0.15):
        for s in (-1, 1):
            K.box((s * xr - 0.03, roof - 0.02, z - 0.03), (s * xr + 0.03, roof + 0.06, z + 0.03), "trim", m=det)
        K.box((-xr, roof + 0.05, z - 0.03), (xr, roof + 0.09, z + 0.03), "trim", m=det)
    K.rbox((-0.72, roof + 0.09, zw1 + 0.08), (0.72, roof + 0.19, zw1 + 0.22), 0.03, "trim", seg=1, m=det)
    for i in range(6):
        x = -0.6 + i * 0.24
        K.rbox((x - 0.09, roof + 0.11, zw1 + 0.075), (x + 0.09, roof + 0.175, zw1 + 0.09), 0.01, "amber" if i % 2 == 0 else "head_light",
               seg=1, m=det)
    K.sphere((0.45, roof + 0.06, zr1 - 0.3), 0.09, 0.06, 0.09, "white", n=12, rows=6, m=det, top_only=True)
    for x in (-0.5, -0.35):
        K.tube([v3(x, roof + 0.02, zr1 - 0.25), v3(x, roof + 0.85, zr1 - 0.25)], 0.006, "black", n=5, m=det)
    mast = (0.62, fy, bed0 + 0.25)
    K.tube([v3(*mast), v3(mast[0], 2.75, mast[2])], 0.025, "steel", n=8, m=det)
    K.rbox((mast[0] - 0.12, fy, mast[2] - 0.12), (mast[0] + 0.12, fy + 0.35, mast[2] + 0.12), 0.03, "white", seg=1, m=det)
    K.rbox((-0.85, fy, h - 0.85), (-0.2, fy + 0.32, h - 0.3), 0.04, "dark", seg=1, m=det)
    for z in (h - 0.55, h - 0.95):
        K.sphere((0.42, fy, z), 0.2, 0.14, 0.2, "orange", n=14, rows=7, m=det, top_only=True)
    door_parts(v, car, W, [-0.12, 0.62], 1.18, handle_mat="trim")
    interior2(v, det, car, W, 0.5, 1.2, True, trim="dark", accent="dash", front_z=0.95, rear_z=1.6)
    ex = tips(det, [0.62], 0.46, h + 0.04, 0.045, mat="steel")
    body.extend(det)
    finish(v, car, body, glass, W, 1.3, 0.62, 0.86, wheel(b.wheel_r, 0.3, "rim_offroad", tyre_n=28), [(-0.8, 1.03, -h), (0.8, 1.03, -h)], ex)
    v.part("anemometer", V.anemometer())
    v.part("dish", V.dish(0.36))
    v.geo.update(anemometer=(mast[0], 2.78, mast[2]), dish=(-0.3, fy + 0.03, h - 1.45))
    return v


# ------------------------------------------------------------------------------------------------ full-size SUV
def suv():
    v = V.Vehicle("suv")
    W = 2.06
    b = V.Body(length=5.7, width=W, sill=0.44, bumper_low=0.46, over_f=1.0, over_r=1.15, wz_f=-1.85, wz_r=1.7, wheel_r=0.42)
    h = 2.85
    plan = [(-h, 0.95), (-h + 0.1, 1.01), (-h + 0.3, 1.03), (h - 0.2, 1.03), (h - 0.05, 1.0), (h, 0.97)]
    belt = [(-h, 1.12), (-h + 0.15, 1.2), (-1.0, 1.25), (2.5, 1.3), (h - 0.08, 1.29), (h, 1.24)]
    top = [(-h, 1.18), (-h + 0.14, 1.26), (-1.1, 1.31), (-1.0, 1.32), (-0.3, 1.95), (1.2, 1.97), (2.55, 1.95), (2.7, 1.33), (h, 1.28)]
    zw0, zw1, zr1, zb = -1.0, -0.3, 2.55, 2.7
    car = C.Car(b, plan, belt, top, zw0, zw1, zr1, zb, windows=[(-0.86, 0.2), (0.3, 1.3), (1.4, 2.42)], tumble=0.1, rail=0.06,
                shoulder=0.16, rocker=0.12, hood_crease=0.55, cowl=0.3, tail=0.12, rocker_mat="trim", seal_mat="chrome", c_mat="paint")
    spans = [(-0.95, 0.25), (0.25, 1.32)]
    body, glass = car.build(doors=spans)
    det = Mesh()
    front_rear(car, det, "hl_truck", "tl_tall", "gr_truck", (0.58, 1.05, 0.98, 1.17), (0.82, 1.05, 0.78, 1.27), (0.56, 0.64, 1.16),
               (0.5, 0.6), (0.66, 0.76), intake=(0.78, 0.48, 0.62), fog=(0.68, 0.82, 0.5, 0.58), badge_f=0.9, badge_r=1.12, tl_dot=0.3)
    # Chrome bar across the grille, roof rails, running boards, bumpers.
    K.rbox((-0.6, 0.86, -h - 0.025), (0.6, 0.92, -h + 0.02), 0.012, "chrome", seg=1, m=det)
    roof = car.top_y(1.0, 0)
    xr = car.section(1.0)[8][0] - 0.04
    for s in (-1, 1):
        K.rbox((s * xr - 0.025, roof + 0.05, zw1 + 0.2), (s * xr + 0.025, roof + 0.09, zr1 - 0.12), 0.012, "chrome", seg=1, m=det)
        for z in (zw1 + 0.25, (zw1 + zr1) / 2, zr1 - 0.17):
            K.box((s * xr - 0.02, roof - 0.02, z - 0.05), (s * xr + 0.02, roof + 0.06, z + 0.05), "trim", m=det)
        K.rbox((s * 1.05 - 0.11, 0.4, -0.9), (s * 1.05 + 0.11, 0.46, 1.3), 0.015, "trim", seg=1, m=det)
        K.box((s * 1.05 - 0.1, 0.46, -0.85), (s * 1.05 + 0.1, 0.462, 1.25), "steel", m=det)
    K.rbox((-0.98, 0.44, -h - 0.04), (0.98, 0.62, -h + 0.15), 0.04, "paint", seg=1, m=det)
    K.rbox((-0.98, 0.44, h - 0.12), (0.98, 0.62, h + 0.06), 0.04, "trim", seg=1, m=det)
    K.rbox((-0.6, 1.92, zr1 - 0.04), (0.6, 1.98, zr1 + 0.06), 0.015, "paint", seg=1, m=det)
    K.box((-0.2, 1.93, zr1 + 0.055), (0.2, 1.955, zr1 + 0.062), "tail_light", m=det)
    fin(det, car, 2.2)
    C.mirror(det, car, zw0 + 0.09)
    door_parts(v, car, W, [0.0, 1.08], 1.2, trim="tan")
    interior2(v, det, car, W, 0.52, 1.24, True, trim="tan", accent="wood", front_z=1.0, rear_z=1.9)
    # Third-row bench.
    K.rbox((-W / 2 + 0.17, 0.6, 1.95), (W / 2 - 0.17, 0.74, 2.3), 0.04, "seat_tan", seg=1, m=det)
    K.rbox((-W / 2 + 0.17, 0.74, 2.26), (W / 2 - 0.17, 1.25, 2.38), 0.04, "seat_tan", seg=1, m=det)
    for x in (-0.45, 0.45):
        Q(det, "quilt_tan", (x, 0.742, 2.12), (0.18, 0, 0), (0, 0, -0.15))
        K.rbox((x - 0.12, 1.3, 2.28), (x + 0.12, 1.45, 2.37), 0.04, "seat_tan", seg=1, m=det)
    K.box((-W / 2 + 0.12, 0.52, 2.38), (W / 2 - 0.12, 0.55, h - 0.12), "carpet", m=det)
    ex = tips(det, [-0.65, 0.65], 0.48, h + 0.06, 0.045)
    body.extend(det)
    finish(v, car, body, glass, W, 1.35, 0.64, 0.87, wheel(b.wheel_r, 0.3, "rim_multi", tyre_n=28), [(-0.82, 1.08, -h), (0.82, 1.08, -h)], ex)
    return v


# ------------------------------------------------------------------------------------------------ muscle coupe
def coupe():
    v = V.Vehicle("coupe")
    W = 1.94
    b = V.Body(length=4.8, width=W, sill=0.17, bumper_low=0.2, over_f=0.95, over_r=0.9, wz_f=-1.42, wz_r=1.42, wheel_r=0.36)
    h = 2.4
    plan = [(-h, 0.74), (-h + 0.08, 0.86), (-h + 0.3, 0.93), (-1.42, 0.96), (-0.8, 0.95), (0.6, 0.94), (1.42, 0.97), (h - 0.25, 0.95),
            (h - 0.07, 0.9), (h, 0.84)]
    belt = [(-h, 0.6), (-h + 0.12, 0.74), (-1.5, 0.86), (-0.5, 0.95), (1.0, 1.01), (1.75, 1.04), (h - 0.1, 1.02), (h, 0.96)]
    top = [(-h, 0.62), (-h + 0.12, 0.76), (-1.9, 0.84), (-1.4, 0.92), (-1.0, 0.95), (-0.48, 0.97), (0.22, 1.32), (0.75, 1.34),
           (1.7, 1.05), (h - 0.12, 1.05), (h, 0.98)]
    zw0, zw1, zr1, zb = -0.48, 0.22, 0.75, 1.7
    car = C.Car(b, plan, belt, top, zw0, zw1, zr1, zb, windows=[(-0.36, 0.8), (0.86, 1.18)], tumble=0.2, rail=0.07, shoulder=0.11,
                rocker=0.1, hood_crease=0.62, cowl=0.4, tail=0.3, seal_mat="trim", c_mat="paint", rocker_mat="trim")
    spans = [(-0.5, 0.84)]
    body, glass = car.build(doors=spans)
    det = Mesh()
    front_rear(car, det, "hl_muscle", "tl_muscle", "gr_mesh", (0.46, 0.97, 0.5, 0.635), (0.36, 0.9, 0.78, 0.94), (0.66, 0.49, 0.63),
               (0.22, 0.3), (0.52, 0.62), intake=(0.64, 0.23, 0.43), badge_f=0.56, badge_r=0.75)
    # Hood bulge (a raised wedge rising toward the windshield) with a cowl-induction opening.
    bulge = Mesh()
    zs_b = [-1.85, -1.5, -0.9, -0.6]
    rise = [0.0, 0.03, 0.055, 0.06]
    wid = [0.22, 0.3, 0.33, 0.33]
    ring_b = []
    for z, r, w in zip(zs_b, rise, wid):
        ring_b.append([v3(-w - 0.06, car.top_y(z, w + 0.06) - 0.004, z), v3(-w, car.top_y(z, w) + r, z), v3(w, car.top_y(z, w) + r, z),
                       v3(w + 0.06, car.top_y(z, w + 0.06) - 0.004, z)])
    for i in range(len(ring_b) - 1):
        for j in range(3):
            q = [ring_b[i][j], ring_b[i][j + 1], ring_b[i + 1][j + 1], ring_b[i + 1][j]]
            bulge.add_oriented(q, "paint", v3(0, 1, 0) if j == 1 else v3(j - 1, 1, 0), smooth=False)
    r = ring_b[-1]
    bulge.add_oriented([r[0], r[1], r[2], r[3]], "black", v3(0, 0, 1), smooth=False)
    det.extend(bulge)
    # Racing stripes over hood, bulge, roof and deck.
    for x0 in (0.08, -0.3):
        C.project(car.skins() + [bulge], det, "black", (x0, 0.3, -h - 0.1), (x0 + 0.22, 2.0, h + 0.1), "+y", min_dot=0.25, offset=0.004)
        C.project(car.skins(), det, "black", (x0, 0.3, -h - 0.1), (x0 + 0.22, 0.6, -h + 0.15), "-z", min_dot=0.5, offset=0.004)
    for s in (-1, 1):
        K.box((s * 0.6 - 0.1, car.top_y(-1.2, 0.6) + 0.002, -1.3), (s * 0.6 + 0.1, car.top_y(-1.2, 0.6) + 0.012, -1.05), "black", m=det)
        # Side scoop ahead of the rear wheel.
        zs = 1.0
        x, y = car.section(zs)[2]
        C.project(car.skins(), det, "vent_mesh", (0.3 * s if s > 0 else -5, y - 0.03, zs - 0.15), (5 if s > 0 else -0.3, y + 0.05, zs + 0.03),
                  "+x" if s > 0 else "-x", offset=0.005)
    # Spoiler on the deck lid, splitter, diffuser.
    yd = car.top_y(h - 0.15, 0)
    for x in (-0.6, 0.6):
        K.box((x - 0.02, yd - 0.01, h - 0.16), (x + 0.02, yd + 0.04, h - 0.1), "black", m=det)
    sp = Mesh()
    sp.add_oriented([v3(-0.8, yd + 0.035, h - 0.2), v3(0.8, yd + 0.035, h - 0.2), v3(0.8, yd + 0.06, h - 0.04), v3(-0.8, yd + 0.06, h - 0.04)],
                    "paint", v3(0, 1, 0), smooth=False)
    sp.add_oriented([v3(-0.8, yd + 0.035, h - 0.2), v3(0.8, yd + 0.035, h - 0.2), v3(0.8, yd + 0.045, h - 0.04), v3(-0.8, yd + 0.045, h - 0.04)],
                    "black", v3(0, -1, 0), smooth=False)
    sp.add_oriented([v3(-0.8, yd + 0.045, h - 0.04), v3(0.8, yd + 0.045, h - 0.04), v3(0.8, yd + 0.06, h - 0.04), v3(-0.8, yd + 0.06, h - 0.04)],
                    "paint", v3(0, 0, 1), smooth=False)
    det.extend(sp)
    K.rbox((-0.76, 0.17, -h - 0.04), (0.76, 0.2, -h + 0.25), 0.01, "trim", seg=1, m=det)
    K.rbox((-0.75, 0.18, h - 0.22), (0.75, 0.26, h + 0.05), 0.01, "trim", seg=1, m=det)
    C.mirror(det, car, zw0 + 0.1)
    door_parts(v, car, W, [0.6], 0.92, trim="red")
    interior2(v, det, car, W, 0.2, 0.94, False, trim="red", accent="alu", sport=True, front_z=1.05)
    ex = tips(det, [-0.52, -0.42, 0.42, 0.52], 0.24, h + 0.06, 0.04)
    body.extend(det)
    finish(v, car, body, glass, W, 0.98, 0.56, 0.84, wheel(b.wheel_r, 0.27, "rim_sport", caliper="red"), [(-0.72, 0.58, -h), (0.72, 0.58, -h)], ex)
    return v


# ------------------------------------------------------------------------------------------------ sport sedan
def sport():
    v = V.Vehicle("sport")
    W = 1.94
    b = V.Body(length=5.0, width=W, sill=0.17, bumper_low=0.2, over_f=0.95, over_r=1.05, wz_f=-1.5, wz_r=1.48, wheel_r=0.36)
    h = 2.5
    plan = [(-h, 0.76), (-h + 0.08, 0.87), (-h + 0.3, 0.94), (-1.5, 0.97), (-0.8, 0.95), (0.8, 0.95), (1.48, 0.97), (h - 0.22, 0.95),
            (h - 0.06, 0.9), (h, 0.84)]
    belt = [(-h, 0.58), (-h + 0.18, 0.76), (-1.6, 0.84), (-0.75, 0.92), (1.3, 0.98), (2.0, 1.0), (h - 0.1, 0.98), (h, 0.92)]
    top = [(-h, 0.59), (-h + 0.18, 0.77), (-1.6, 0.87), (-0.75, 0.94), (0.05, 1.42), (0.55, 1.45), (1.0, 1.42), (1.85, 1.02),
           (h - 0.12, 1.02), (h, 0.95)]
    zw0, zw1, zr1, zb = -0.75, 0.05, 1.0, 1.85
    car = C.Car(b, plan, belt, top, zw0, zw1, zr1, zb, windows=[(-0.62, 0.38), (0.46, 1.32)], tumble=0.18, rail=0.07, shoulder=0.1,
                rocker=0.11, hood_crease=0.58, seal_mat="trim", frame_mat="trim", rocker_mat="trim")
    spans = [(-0.7, 0.42), (0.42, 1.32)]
    body, glass = car.build(doors=spans)
    det = Mesh()
    front_rear(car, det, "hl_sport", "tl_sport", "gr_hex", (0.36, 0.98, 0.5, 0.75), (0.32, 0.95, 0.8, 0.98), (0.36, 0.44, 0.62),
               (0.27, 0.35), (0.52, 0.62), intake=(0.3, 0.25, 0.37), badge_f=0.66, badge_r=0.88)
    # Big side intakes in the bumper, blue light blades, vents behind the front wheels.
    for s in (-1, 1):
        x0, x1 = (0.42, 0.82) if s > 0 else (-0.82, -0.42)
        C.project(car.skins(), det, "vent_mesh", (x0, 0.24, -h - 0.1), (x1, 0.45, -h + 0.3), "-z", min_dot=0.5, offset=0.006)
        zv = -0.95
        y = car.section(zv)[3][1]
        C.project(car.skins(), det, "vent_mesh", (0.3 if s > 0 else -5, y - 0.12, zv - 0.15) , (5 if s > 0 else -0.3, y - 0.04, zv + 0.02),
                  "+x" if s > 0 else "-x", offset=0.005)
        K.box((s * 0.62 - 0.12, 0.24, -h - 0.005), (s * 0.62 + 0.12, 0.25, -h + 0.02), "blue_light", m=det)
    # Side skirts, splitter, diffuser with fins, boot lip.
    for s in (-1, 1):
        K.rbox((s * 0.975 - 0.03, 0.15, -1.0), (s * 0.975 + 0.03, 0.22, 1.05), 0.01, "trim", seg=1, m=det)
    K.rbox((-0.8, 0.16, -h - 0.05), (0.8, 0.19, -h + 0.25), 0.01, "trim", seg=1, m=det)
    K.rbox((-0.78, 0.17, h - 0.25), (0.78, 0.27, h + 0.06), 0.01, "trim", seg=1, m=det)
    for x in (-0.3, -0.1, 0.1, 0.3):
        K.box((x - 0.01, 0.15, h - 0.2), (x + 0.01, 0.26, h + 0.07), "trim", m=det)
    yd = car.top_y(h - 0.08, 0)
    K.rbox((-0.64, yd - 0.005, h - 0.14), (0.64, yd + 0.03, h - 0.02), 0.01, "paint", seg=1, m=det)
    fin(det, car, 1.15)
    C.mirror(det, car, zw0 + 0.1)
    door_parts(v, car, W, [0.2, 1.1], 0.9, trim="tan")
    interior2(v, det, car, W, 0.22, 0.94, True, trim="tan", accent="alu", sport=True)
    ex = tips(det, [-0.62, -0.5, 0.5, 0.62], 0.25, h + 0.07, 0.04)
    body.extend(det)
    finish(v, car, body, glass, W, 0.96, 0.58, 0.84, wheel(b.wheel_r, 0.27, "rim_sport", caliper="blue_light"), [(-0.68, 0.62, -h), (0.68, 0.62, -h)], ex)
    return v
