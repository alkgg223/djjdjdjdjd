"""Reusable gun part builders. Gun space: barrel along +X, up +Y, +Z = right side, centreline z=8.
1 unit ~ 2.2 cm."""
import math

Z = 8.0


def zr(w):
    return Z - w / 2, Z + w / 2


def block(m, x0, x1, y0, y1, w, mat, **kw):
    z0, z1 = zr(w)
    return m.box([x0, y0, z0], [x1, y1, z1], mat, **kw)


def barrel(m, x0, x1, cy, r, mat="gunmetal", sides=8, muzzle=True, part="body"):
    els = m.cyl("x", (cy, Z), r, x0, x1, mat, sides=sides, part=part)
    if muzzle:
        # dark bore disc on the muzzle end
        m.box([x1 - 0.02, cy - r * 0.45, Z - r * 0.45], [x1 + 0.03, cy + r * 0.45, Z + r * 0.45], "black", part=part)
    return els


def tube(m, x0, x1, cy, r, mat, sides=8, part="body", cz=Z):
    return m.cyl("x", (cy, cz), r, x0, x1, mat, sides=sides, part=part)


def chain(m, x, y, segs, depth, w, mat, part="body", deco=(), start_ang=0.0):
    """Chain of boxes going *down* from (x,y) (top-centre). segs = [(length, angle_deg)], angle >0 leans the
    bottom towards +X (forward). depth = size along the chain's local x."""
    cx, cy = x, y
    for (L, ang) in segs:
        a = math.radians(ang)
        # direction of travel (down, leaning forward for positive angle)
        dx, dy = math.sin(a), -math.cos(a)
        mx, my = cx + dx * L / 2, cy + dy * L / 2
        z0, z1 = zr(w)
        m.box([mx - depth / 2, my - L / 2, z0], [mx + depth / 2, my + L / 2, z1], mat,
              rot=("z", ang, [mx, my, Z]) if ang else None, part=part, deco=deco)
        cx, cy = cx + dx * L * 0.92, cy + dy * L * 0.92
    return cx, cy


def grip(m, x, ytop, length=4.2, depth=1.7, w=1.4, ang=-22.5, mat="polymer", deco=("grip",), part="body"):
    """pistol grip hanging from (x, ytop); negative angle sweeps the bottom backwards"""
    a = math.radians(ang)
    dx, dy = math.sin(a), -math.cos(a)
    mx, my = x + dx * length / 2, ytop + dy * length / 2
    z0, z1 = zr(w)
    m.box([mx - depth / 2, my - length / 2, z0], [mx + depth / 2, my + length / 2, z1], mat,
          rot=("z", ang, [mx, my, Z]), deco=deco, part=part)
    return mx + dx * length / 2, my + dy * length / 2


def trigger_guard(m, x0, x1, ybot, ytop, mat="gunmetal", w=0.5, part="body"):
    z0, z1 = zr(w)
    m.box([x0, ybot, z0], [x1, ybot + 0.3, z1], mat, part=part)
    m.box([x1 - 0.3, ybot, z0], [x1, ytop, z1], mat, part=part)
    # trigger
    tx = x0 + (x1 - x0) * 0.45
    m.box([tx, ybot + 0.6, Z - 0.15], [tx + 0.35, ytop, Z + 0.15], "steel", rot=("z", -22.5, [tx, ytop, Z]), part=part)


def rail(m, x0, x1, y, w=1.1, h=0.45, mat="blued", part="body"):
    z0, z1 = zr(w)
    m.box([x0, y, z0], [x1, y + h, z1], mat, deco=("rail",), part=part)


def front_post(m, x, y, h=1.6, mat="gunmetal", part="body", ears=True):
    block(m, x - 0.6, x + 0.6, y, y + 0.6, 0.9, mat, part=part)
    block(m, x - 0.12, x + 0.12, y, y + h, 0.25, mat, part=part)
    if ears:
        for s in (-1, 1):
            m.box([x - 0.35, y + 0.4, Z + s * 0.45 - 0.08], [x + 0.35, y + h + 0.1, Z + s * 0.45 + 0.08], mat, part=part)


def rear_sight(m, x, y, mat="gunmetal", part="body"):
    block(m, x - 0.6, x + 0.6, y, y + 0.45, 1.0, mat, part=part)
    for s in (-1, 1):
        m.box([x - 0.25, y + 0.45, Z + s * 0.32 - 0.12], [x + 0.25, y + 1.0, Z + s * 0.32 + 0.12], mat, part=part)


def muzzle_brake(m, x0, x1, cy, r, mat="gunmetal", part="body"):
    m.cyl("x", (cy, Z), r, x0, x1, mat, sides=8, part=part, deco=("vslots",))
    m.box([x1 - 0.02, cy - r * 0.5, Z - r * 0.5], [x1 + 0.03, cy + r * 0.5, Z + r * 0.5], "black", part=part)


def flash_hider(m, x0, x1, cy, r, part="body"):
    m.cyl("x", (cy, Z), r, x0, x1, "blued", sides=8, part=part, deco=("vslots",))
    m.box([x1 - 0.02, cy - r * 0.55, Z - r * 0.55], [x1 + 0.03, cy + r * 0.55, Z + r * 0.55], "black", part=part)


def sling_loop(m, x, y, part="body"):
    m.box([x - 0.35, y - 0.35, Z - 0.5], [x + 0.35, y + 0.05, Z + 0.5], "steel", part=part)


def ar_receiver(m, x0, x1, y0, ytop, mat="dark_paint", part="body", port=True):
    """AR-15 style upper + lower"""
    w = 1.55
    # lower receiver
    block(m, x0, x1 - 2.0, y0, y0 + 1.6, w * 0.95, mat, part=part, deco=("panel",))
    # magwell
    # upper receiver
    block(m, x0, x1, y0 + 1.6, ytop, w, mat, part=part, deco=("port",) if port else ())
    # forward assist
    m.box([x0 + 4.2, y0 + 2.0, Z + w / 2 - 0.1], [x0 + 5.6, y0 + 2.9, Z + w / 2 + 0.55], mat, rot=("y", -22.5, [x0 + 4.9, y0 + 2.4, Z + w / 2]), part=part)
    # flat top rail
    rail(m, x0 + 0.2, x1 - 0.1, ytop, w=1.1, part=part)


def m_lok_handguard(m, x0, x1, cy, h=2.6, w=2.0, mat="dark_paint", part="body", top_rail=True):
    block(m, x0, x1, cy - h / 2, cy + h / 2, w, mat, part=part, deco=("slots:5",))
    m.cyl("x", (cy, Z), h / 2 + 0.05, x0 + 0.1, x1 - 0.1, mat, sides=8, part=part, deco=("slots:5",))
    if top_rail:
        rail(m, x0, x1, cy + h / 2 + 0.05, w=1.1, part=part)


def ar_stock(m, x1, ybuf, mat="polymer", part="body"):
    """collapsible M4 stock; x1 = rear of receiver"""
    tube(m, x1 - 7.6, x1, ybuf, 0.62, "blued", part=part)
    block(m, x1 - 9.4, x1 - 3.8, ybuf - 0.85, ybuf + 0.85, 1.45, mat, part=part, deco=("panel",))
    block(m, x1 - 9.4, x1 - 7.6, ybuf - 3.9, ybuf + 0.85, 1.5, mat, part=part)
    m.box([x1 - 8.0, ybuf - 3.9, Z - 0.6], [x1 - 2.9, ybuf - 2.7, Z + 0.6], mat, rot=("z", 22.5, [x1 - 8.0, ybuf - 3.9, Z]), part=part)
    block(m, x1 - 9.9, x1 - 9.4, ybuf - 4.0, ybuf + 0.95, 1.65, "rubber", part=part)
    m.box([x1 - 6.0, ybuf - 1.4, Z - 0.3], [x1 - 5.0, ybuf - 0.85, Z + 0.3], "steel", part=part)  # adjustment lever


def stanag_mag(m, x, ytop, length=6.5, depth=2.5, w=1.15, mat="dark_paint", part="mag", curve=True):
    segs = [(length * 0.55, 0.0), (length * 0.5, 22.5 if curve else 0.0)]
    end = chain(m, x, ytop, segs, depth, w, mat, part=part, deco=("ribs",))
    return end


def scope_tube(m, x0, x1, cy, r, part, mat="blued", sides=16):
    return m.cyl("x", (cy, Z), r, x0, x1, mat, sides=sides, part=part)
