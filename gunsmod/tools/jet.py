"""F-16C Fighting Falcon (1 unit = 0.375 block, rendered at scale 6; front -Z, +X right, ground y=0 with the gear down,
(8, 0, 8) = entity origin). Real proportions: 15 m long, 9.5 m span. Every point the game needs is stored in GEO."""
import math

from modelkit import Model

P = "paint_white"      # tinted grey per team at runtime
F_ = "dark_paint"
G_ = "glass_clear"
GEO = {}

WING_Y = 5.0
SEAT = (8.0, 5.8, -8.2)


def swept_surface(m, x_root, x_tip, z_le_root, z_te_root, z_te_tip, y, t, sweep45=True, part="body", n=10, deco=()):
    """a 45 degree swept surface (wing / stabilator) built from spanwise strips plus a rotated leading edge strip.
    x_root / x_tip may run either way (left or right side)."""
    side = 1 if x_tip > x_root else -1
    span = abs(x_tip - x_root)
    w = span / n
    for i in range(n):
        xa = x_root + side * i * w
        xb = xa + side * w
        far = max(abs(xa - x_root), abs(xb - x_root))
        z0 = z_le_root + far                     # leading edge (45 deg) at the strip's outer end
        k = (i + 0.5) / n
        z1 = z_te_root + (z_te_tip - z_te_root) * k
        tt = t * (1.0 - 0.35 * k)
        if z1 - z0 < 0.3:
            continue
        m.box([min(xa, xb), y - tt / 2, z0], [max(xa, xb), y + tt / 2, z1], P, part=part, deco=deco)
    # smooth leading edge: one strip along the 45 degree line
    L = span * math.sqrt(2)
    cx, cz = (x_root + x_tip) / 2, z_le_root + span / 2
    m.box([cx - L / 2, y - t * 0.3, cz - 0.42], [cx + L / 2, y + t * 0.3, cz + 0.42], P, part=part,
          rot=("y", -45.0 * side, [cx, y, cz]))


def aim9(m, x, y, z0, part):
    """AIM-9 Sidewinder on a wingtip rail, nose at z0 (pointing -Z)"""
    m.cyl("z", (x, y), 0.25, z0 + 0.6, z0 + 7.6, "white", sides=8, part=part)
    m.cyl("z", (x, y), 0.18, z0, z0 + 0.6, "lens", sides=8, part=part)
    for d in (1, -1):
        m.box([x - 0.03, y - 0.02, z0 + 1.0], [x + 0.03, y + 0.02, z0 + 1.5], "white", part=part)
        m.box([x + d * 0.25, y - 0.03, z0 + 0.9], [x + d * 0.65, y + 0.03, z0 + 1.6], "white", part=part)
        m.box([x - 0.03, y + d * 0.25, z0 + 0.9], [x + 0.03, y + d * 0.65, z0 + 1.6], "white", part=part)
        m.box([x + d * 0.25, y - 0.03, z0 + 6.6], [x + d * 0.85, y + 0.03, z0 + 7.6], "white", part=part)
        m.box([x - 0.03, y + d * 0.25, z0 + 6.6], [x + 0.03, y + d * 0.85, z0 + 7.6], "white", part=part)
    m.box([x - 0.26, y - 0.04, z0 + 1.9], [x + 0.26, y + 0.04, z0 + 2.3], "yellow", part=part)


def maverick(m, x, y, z0, part):
    """AGM-65 Maverick under a pylon, nose at z0"""
    m.cyl("z", (x, y), 0.42, z0 + 0.7, z0 + 6.6, "od_paint", sides=8, part=part)
    m.cyl("z", (x, y), 0.32, z0, z0 + 0.7, "lens", sides=8, part=part)
    for d in (1, -1):
        m.box([x + d * 0.42, y - 0.03, z0 + 2.4], [x + d * 1.0, y + 0.03, z0 + 5.4], "od_paint", part=part)
        m.box([x - 0.03, y + d * 0.42, z0 + 2.4], [x + 0.03, y + d * 1.0, z0 + 5.4], "od_paint", part=part)
        m.box([x + d * 0.42, y - 0.03, z0 + 5.8], [x + d * 0.8, y + 0.03, z0 + 6.6], "od_paint", part=part)
    m.box([x - 0.44, y - 0.05, z0 + 1.2], [x + 0.44, y + 0.05, z0 + 1.5], "yellow", part=part)


def f16():
    pylons = [(3.4, WING_Y - 1.05, 1.6), (12.6, WING_Y - 1.05, 1.6), (0.4, WING_Y - 1.05, 3.4), (15.6, WING_Y - 1.05, 3.4)]
    g = dict(seat=SEAT, cannon=(5.9, 5.6, -4.5), nozzle=(8.0, 5.6, 24.2), burner=(8.0, 5.6, 24.2),
             gearNose=(8.0, 3.1, -5.4), gearMain=(8.0, 3.5, 3.6),
             tips=[(-4.95, WING_Y, 4.6), (20.95, WING_Y, 4.6)], pylons=pylons,
             flares=[(6.2, 3.6, 17.5), (9.8, 3.6, 17.5)],
             ends=[(8.0, 5.4, -15.9), (8.0, 6.0, 24.0), (-4.6, WING_Y, 9.6), (20.6, WING_Y, 9.6), (8.0, 13.2, 21.4), (8.0, 2.2, 2.0)],
             pivotY=2.2, size=(3.0, 2.2))
    GEO["f16"] = g
    m = Model("f16", density=5.0, atlas_w=2048)
    b = m.box
    # ---- radome and forward fuselage
    for z0, z1, r in ((-15.9, -15.0, 0.45), (-15.0, -14.0, 0.85), (-14.0, -12.8, 1.2), (-12.8, -11.4, 1.5)):
        m.cyl("z", (8.0, 5.5), r, z0, z1, "grey_paint", sides=16)
    b([7.95, 5.45, -16.0], [8.05, 5.55, -15.9], "steel")
    m.cyl("z", (8.0, 5.8), 1.75, -11.4, -3.0, P, sides=16, deco=("panel",))
    # leading edge root extensions (chines) running into the wings
    for z0, z1, hw in ((-10.0, -7.5, 2.15), (-7.5, -5.0, 2.55), (-5.0, -2.5, 3.0), (-2.5, 0.0, 3.5), (0.0, 2.5, 4.0)):
        b([8 - hw, 4.95, z0], [8 + hw, 5.35, z1], P)
    # canopy: one-piece bubble on a sill, ACES II seat inside, HUD frame
    b([6.3, 6.9, -11.6], [9.7, 7.4, -4.0], P, deco=("panel",))
    b([6.5, 7.4, -11.2], [9.5, 9.0, -5.0], G_)
    b([6.9, 9.0, -10.4], [9.1, 9.6, -5.6], G_)
    b([6.7, 7.4, -12.2], [9.3, 8.6, -11.4], G_, rot=("x", 22.5, [8, 7.4, -11.4]))
    b([6.4, 7.35, -5.2], [9.6, 9.2, -4.6], F_)
    b([7.2, 6.2, -7.0], [8.8, 8.9, -6.2], F_)
    b([7.3, 7.6, -10.4], [8.7, 8.3, -10.0], F_)
    b([7.4, 8.3, -10.35], [8.6, 8.7, -10.3], "screen", light=True)
    # dorsal spine to the fin
    b([7.0, 7.4, -4.6], [9.0, 8.1, 14.0], P, deco=("panel",))
    # centre fuselage blended into the wing roots
    b([5.6, 3.8, -3.0], [10.4, 7.4, 15.0], P, deco=("panel",))
    b([4.6, 4.4, 0.0], [11.4, 6.4, 13.0], P)
    # ventral intake with the splitter and its dark mouth
    b([6.3, 2.3, -7.4], [9.7, 4.4, 4.0], P, deco=("panel", "grime"))
    b([6.55, 2.5, -7.46], [9.45, 4.15, -7.4], "black")
    b([6.3, 4.2, -7.8], [9.7, 4.4, -7.0], P)
    b([6.25, 2.3, -7.6], [6.45, 4.4, -7.0], P)
    b([9.55, 2.3, -7.6], [9.75, 4.4, -7.0], P)
    # aft fuselage, nozzle with its petals
    m.cyl("z", (8.0, 5.6), 1.95, 15.0, 19.0, P, sides=16, deco=("panel",))
    m.cyl("z", (8.0, 5.6), 1.75, 19.0, 21.4, P, sides=16)
    m.cyl("z", (8.0, 5.6), 1.6, 21.4, 23.6, "gunmetal", sides=16, deco=("vlines:12",))
    m.cyl("z", (8.0, 5.6), 1.45, 23.6, 24.2, "steel", sides=16, deco=("vlines:16",))
    b([7.0, 4.6, 24.15], [9.0, 6.6, 24.22], "black")
    # vertical fin (swept), rudder, base fairing with the brake chute housing
    for i in range(6):
        y0 = 7.4 + i
        z0 = 13.6 + i * 1.05
        z1 = 21.6 + i * 0.1
        b([7.72, y0, z0], [8.28, y0 + 1.0, z1], P)
    # smooth swept leading edge of the fin
    b([7.75, 10.4 - 4.25, 16.75 - 0.42], [8.25, 10.4 + 4.25, 16.75 + 0.42], P, rot=("x", 45.0, [8.0, 10.4, 16.75]))
    b([7.7, 7.4, 21.6], [8.3, 13.2, 22.4], P, deco=("hlines:3",))
    b([7.6, 13.0, 19.2], [8.4, 13.4, 22.4], F_)
    b([7.4, 7.4, 21.2], [8.6, 8.2, 23.4], P)
    b([7.82, 12.4, 22.38], [8.18, 12.8, 22.5], "glow_white", light=True)
    # ventral fins
    for x in (6.4, 9.3):
        b([x, 2.4, 15.4], [x + 0.3, 3.9, 18.6], P)
    # wings: 45 degree swept leading edge, straight trailing edge; flaperons marked
    swept_surface(m, 10.2, 20.6, -1.0, 12.2, 10.6, WING_Y, 0.45, deco=("panel",))
    swept_surface(m, 5.8, -4.6, -1.0, 12.2, 10.6, WING_Y, 0.45, deco=("panel",))
    for x0, x1 in ((10.4, 15.6), (0.4, 5.6)):
        b([x0, WING_Y + 0.22, 10.6], [x1, WING_Y + 0.26, 12.0], F_, deco=("vlines:3",))
    # stabilators
    swept_surface(m, 10.0, 15.6, 16.4, 22.4, 22.6, 5.3, 0.35, n=6)
    swept_surface(m, 6.0, 0.4, 16.4, 22.4, 22.6, 5.3, 0.35, n=6)
    # wingtip launch rails, pylons
    for x in (-4.95, 20.95):
        b([x - 0.2, WING_Y - 0.25, 3.6], [x + 0.2, WING_Y + 0.25, 11.2], F_)
    for (px, py, pz) in pylons:
        b([px - 0.18, py + 0.45, pz + 0.6], [px + 0.18, WING_Y - 0.2, pz + 5.2], F_)
    # M61 gun port on the left wing root, navigation and formation lights
    b([5.5, 5.4, -5.0], [5.95, 5.8, -3.8], "black")
    b([-4.7, WING_Y - 0.1, 9.4], [-4.4, WING_Y + 0.15, 9.8], "glow_red", light=True)
    b([20.4, WING_Y - 0.1, 9.4], [20.7, WING_Y + 0.15, 9.8], "glow_green", light=True)
    for fx, fy, fz in g["flares"]:
        b([fx - 0.4, fy - 0.2, fz - 0.8], [fx + 0.4, fy + 0.2, fz + 0.8], F_, deco=("vlines:4",))
    # team markings: roundels on the upper wings and the intake, fin stripe
    for x0, x1 in ((15.0, 18.0), (-2.0, 1.0)):
        m.box([x0, WING_Y + 0.24, 6.6], [x1, WING_Y + 0.27, 9.6], "decal", part="mark_green", deco=("star",))
        m.box([x0, WING_Y + 0.24, 6.6], [x1, WING_Y + 0.27, 9.6], "decal", part="mark_red", deco=("star:red",))
    for x0 in (6.25, 9.7):
        m.box([x0, 2.6, -4.0], [x0 + 0.05, 4.1, -1.0], "decal", part="mark_green", deco=("star",))
        m.box([x0, 2.6, -4.0], [x0 + 0.05, 4.1, -1.0], "decal", part="mark_red", deco=("star:red",))
    for x0 in (7.67, 8.28):
        m.box([x0, 10.6, 18.6], [x0 + 0.05, 11.6, 22.2], "decal", part="mark_green", deco=("stripe:white",))
        m.box([x0, 10.6, 18.6], [x0 + 0.05, 11.6, 22.2], "decal", part="mark_red", deco=("stripe:red",))
    # ---- landing gear (own parts so they can fold away): nose gear folds back, main gear forward
    nx, ny, nz = g["gearNose"]
    k = dict(part="gear_nose")
    b([nx - 0.15, 0.75, nz - 0.15], [nx + 0.15, ny, nz + 0.15], "steel", **k)
    b([nx - 0.35, 0.75, nz - 0.3], [nx + 0.35, 1.6, nz - 0.1], "steel", **k)
    m.cyl("x", (0.75, nz - 0.2), 0.75, nx - 0.3, nx + 0.3, "tire", sides=8, **k)
    m.cyl("x", (0.75, nz - 0.2), 0.4, nx - 0.35, nx + 0.35, "grey_metal", sides=8, **k)
    b([nx - 0.6, 1.9, nz + 0.2], [nx + 0.6, 2.3, nz + 0.4], P, **k)
    b([nx - 0.05, 2.0, nz - 0.6], [nx + 0.05, 2.6, nz - 0.4], "glow_white", light=True, **k)
    mx, my, mz = g["gearMain"]
    k = dict(part="gear_main")
    for s in (-1, 1):
        x = mx + s * 3.1
        b([x - 0.17, 1.0, mz - 0.17], [x + 0.17, my, mz + 0.17], "steel", **k)
        b([x - 0.6 * s - 0.1, 2.2, mz - 0.1], [x - 0.6 * s + 0.1, my, mz + 0.1], "steel", **k)
        m.cyl("x", (1.0, mz + 0.2), 1.0, x - 0.38, x + 0.38, "tire", sides=8, **k)
        m.cyl("x", (1.0, mz + 0.2), 0.55, x - 0.42, x + 0.42, "grey_metal", sides=8, **k)
        b([x + s * 0.45, 1.6, mz - 1.1], [x + s * 0.55, my, mz + 1.3], P, **k)
    # ---- stores (each its own part: hidden once fired)
    aim9(m, -4.95, WING_Y, 3.0, "aim9_l")
    aim9(m, 20.95, WING_Y, 3.0, "aim9_r")
    for i, (px, py, pz) in enumerate(pylons):
        maverick(m, px, py, pz, f"mav_{i}")
    # ---- afterburner plume (drawn only in reheat)
    bx, by, bz = g["burner"]
    k = dict(part="burner")
    m.cyl("z", (bx, by), 1.25, bz, bz + 1.4, "glow_orange", sides=16, **k)
    m.cyl("z", (bx, by), 0.95, bz + 1.4, bz + 3.0, "glow_orange", sides=8, **k)
    m.cyl("z", (bx, by), 0.6, bz + 3.0, bz + 4.6, "glow_white", sides=8, **k)
    for i in range(4):
        m.cyl("z", (bx, by), 0.45 - i * 0.06, bz + 0.6 + i * 0.9, bz + 0.85 + i * 0.9, "glow_white", sides=8, **k)
    return m


JETS = {"f16": f16}
