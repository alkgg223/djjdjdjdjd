"""Skeleton builders for every body plan.

Each builder takes a Model and a parameter dict and produces named parts with animation roles.
Sizes are model pixels; the final in-game size comes from the species' render scale, so builders
work at a comfortable resolution (about 40-80 px long for big animals).

Roles written to the model file (read by the Java animator):
  BODY | CHEST | HEAD | JAW | NECK i n | TAIL i n | SPINE i n | LEG leg seg phase side |
  ARM side seg | WING side seg | FIN kind side | TENTACLE i j n | EAR side | FRILL | SADDLE |
  TRUNK i n | ANTENNA side | MANDIBLE side | STINGER i n | LURE | CORE | RING i
"""
import math
from geom import GROUND

PI = math.pi


class P(dict):
    def __getattr__(self, k):
        try:
            return self[k]
        except KeyError:
            raise AttributeError(k)


def params(defaults, over):
    p = P(defaults)
    for k, v in over.items():
        p[k] = v
    return p


def has(p, feat):
    return feat in p.get("features", ())


# ----------------------------------------------------------------------------- generic helpers
def chain(m, base, parent, pivot, n, length, w0, h0, w1, h1, pitch0=0.0, pitch_step=0.0, forward=False,
          mat="skin", role="TAIL", yoff=0.0, extra=None, first_rot=None):
    """Segmented chain.  Each segment is a child of the previous; it extends along +z (backwards) or -z."""
    parts = []
    prev = parent
    piv = pivot
    lens = length if isinstance(length, (list, tuple)) else [length] * n
    for i in range(n):
        t = i / max(1, n - 1)
        w = w0 + (w1 - w0) * t
        h = h0 + (h1 - h0) * t
        L = lens[i]
        rx = pitch0 if i == 0 else pitch_step
        rot = (rx, 0, 0) if first_rot is None or i > 0 else first_rot
        p = m.part("%s%d" % (base, i), prev, piv, rot, (role, i, n))
        zc = (-L / 2.0) if forward else (L / 2.0)
        p.cbox(0, yoff, zc, w, h, L + 1, mat, tone=i * 0.02)
        if extra:
            extra(p, i, n, w, h, L)
        parts.append(p)
        prev = p
        piv = (0, 0, -L if forward else L)
    return parts


def leg_chain(m, name, parent, pivot, segs, angles, parent_pitch=0.0, phase=0.0, side=1, leg_index=0,
              mat="skin", foot=None, splay=0.0, foot_mat="claw"):
    """segs: list of (length, w, d).  angles: absolute pitch of each segment (0 = straight down,
    negative = end swings forward).  foot: (length, w, h, mat) flat toes at the end."""
    prev = parent
    piv = pivot
    prev_abs = parent_pitch
    parts = []
    for k, (L, w, d) in enumerate(segs):
        a = angles[k]
        rz = splay * side if k == 0 else (-splay * side * 0.9 if k == 1 else 0.0)
        p = m.part("%s_%d" % (name, k), prev, piv, (a - prev_abs, 0, rz), ("LEG", leg_index, k, round(phase, 3), side))
        p.cbox(0, L / 2.0, 0, w, L + (1 if k < len(segs) - 1 else 0), d, mat, tone=0.03 * k)
        parts.append(p)
        prev_abs = a
        prev = p
        piv = (0, L, 0)
    if foot:
        fl, fw, fh, fmat = foot
        k = len(segs)
        p = m.part("%s_%d" % (name, k), prev, piv, (0 - prev_abs, 0, 0), ("LEG", leg_index, k, round(phase, 3), side))
        if fmat == "claw" and fw >= 4 and fl >= 4:
            # three toes splayed forward, each tipped with a claw; the pad joins them
            p.cbox(0, fh / 2.0, 0.5, fw * 0.7, fh + 0.5, max(2, fl * 0.35), "skin")
            tw = max(1, round(fw / 3.2))
            for i, (dx, ang) in enumerate(((-1, 0.32), (0, 0.0), (1, -0.32))):
                tl = fl * (0.95 if dx == 0 else 0.78)
                t = m.part("%s_%dt%d" % (name, k, i), p, (dx * fw * 0.28, 0, 0), (0, ang, 0))
                t.cbox(0, fh / 2.0, -tl / 2.0 + 0.5, tw, fh, tl, "claw")
        else:
            p.cbox(0, fh / 2.0, -fl / 2.0 + min(2, fl * 0.25), fw, fh, fl, fmat)
        parts.append(p)
    return parts


def horn(m, name, parent, pivot, length, w, rx, ry=0.0, rz=0.0, mat="horn", curve=0.0, segs=1):
    """Pointed horn made of shrinking boxes; extends along -y (up) of its own frame."""
    p = m.part(name, parent, pivot, (rx, ry, rz))
    L = length / segs
    prev = p
    for i in range(segs):
        ww = max(1, w * (1.0 - i / (segs + 0.5)))
        if i == 0:
            prev.cbox(0, -L / 2.0, 0, ww, L + 0.5, ww, mat)
        else:
            q = m.part("%s_%d" % (name, i), prev, (0, -L * 1.0, 0), (curve, 0, 0))
            q.cbox(0, -L / 2.0, 0, ww, L + 0.5, ww, mat)
            prev = q
    return p


def spike_row(m, base, parent, count, z0, z1, y, h0, h1, w=2, mat="horn", rx=0.0, xs=(0,), size_d=None):
    for i in range(count):
        t = i / max(1, count - 1)
        z = z0 + (z1 - z0) * t
        h = h0 + (h1 - h0) * t
        for j, x in enumerate(xs):
            p = m.part("%s%d_%d" % (base, i, j), parent, (x, y, z), (rx, 0, (0.25 if x > 0 else -0.25) if x != 0 else 0))
            p.cbox(0, -h / 2.0, 0, w, h, size_d or w, mat)


def head_block(m, name, parent, pivot, P, rot=(0, 0, 0), jaw=True, teeth=True, eyes=True, beak=False):
    """Cranium + snout + lower jaw."""
    head = m.part(name, parent, pivot, rot, ("HEAD",))
    crl, crw, crh = P.head_len, P.head_w, P.head_h
    snl, snw, snh = P.snout_len, P.snout_w, P.snout_h
    dec_c = ["eyes"] if eyes else []
    head.cbox(0, -crh * 0.15, -crl / 2.0 + 1, crw, crh, crl, P.get("head_mat", "skin"), decals=dec_c)
    sm = "beak" if beak else P.get("snout_mat", "skin")
    snout_dec = ["nostrils"] + (["teeth_down"] if teeth and not beak else [])
    if snl > 0:
        head.cbox(0, -crh * 0.15 + (crh - snh) / 2.0 - P.get("snout_drop", 0.5) * 0, -crl + 1 - snl / 2.0 + 0.5, snw, snh, snl, sm,
                  decals=snout_dec)
    jw = None
    if jaw and P.get("jaw_len", 0) > 0:
        jl = P.jaw_len
        jw = m.part(name + "_jaw", head, (0, crh * 0.35 - P.get("jaw_up", 0), -crl * 0.25), (0.0, 0, 0), ("JAW",))
        jw.cbox(0, P.jaw_h / 2.0, -jl / 2.0 + crl * 0.25, P.jaw_w, P.jaw_h, jl, "beak" if beak else P.get("jaw_mat", "skin"),
                decals=(["teeth_up", "mouth"] if teeth and not beak else ["mouth"]))
    return head, jw


def ears(m, head, P, x, y, z, length=4, w=3, mat="fur", rx=-0.2, splay=0.35):
    for side in (-1, 1):
        e = m.part("ear_%s" % ("l" if side < 0 else "r"), head, (x * side, y, z), (rx, 0, splay * side), ("EAR", side))
        e.cbox(0, -length / 2.0, 0, w, length, max(1, w // 2), mat)


def saddle(m, parent, y, z, w, l, h=2):
    s = m.part("saddle", parent, (0, y, z), (0, 0, 0), ("SADDLE",))
    s.cbox(0, -h / 2.0, 0, w + 1, h, l, "saddle")
    s.cbox(0, -h - 1, -l / 2.0 + 1.5, max(3, w // 2), 2, 2, "saddle")
    # stirrups / straps hanging on both sides
    s.cbox(-(w / 2.0 + 1), h * 1.5, 0, 1, h * 3 + 2, 2, "saddle")
    s.cbox((w / 2.0 + 1), h * 1.5, 0, 1, h * 3 + 2, 2, "saddle")
    return s


# ============================================================================= THEROPOD / BIPED
THEROPOD = dict(
    hip_h=26, body_pitch=0.0, body_len=24, body_back=7, body_w=12, body_h=13,
    chest_len=11, chest_w=10, chest_h=12, chest_pitch=0.08,
    neck_n=2, neck_len=7, neck_w=7, neck_h=8, neck_pitch=-0.55, neck_step=0.25,
    head_pitch=0.3, head_len=10, head_w=9, head_h=8, snout_len=7, snout_w=6, snout_h=5,
    jaw_len=14, jaw_w=7, jaw_h=3,
    tail_n=5, tail_len=8, tail_w0=8, tail_h0=8, tail_w1=2, tail_h1=2, tail_pitch=-0.05, tail_step=0.03,
    thigh=(13, 9, 12), shin=(11, 4, 4), meta=(8, 3, 3), toes=(7, 6, 2),
    leg_angles=(-0.62, 0.62, -0.42), arm=(5, 2, 2), forearm=(4, 2, 2), arm_pitch=0.6, hand=2,
    features=(), saddle=True, body_mat="skin",
)


def theropod(m, over):
    p = params(THEROPOD, over)
    G = GROUND
    body = m.part("body", None, (0, G - p.hip_h, 0), (p.body_pitch, 0, 0), ("BODY",))
    front = p.body_len - p.body_back
    body.cbox(0, -p.body_h * 0.18, (-front + p.body_back) / 2.0, p.body_w, p.body_h, p.body_len, p.body_mat, decals=["belly"])
    chest = m.part("chest", body, (0, -p.body_h * 0.12, -front + 1), (p.chest_pitch, 0, 0), ("CHEST",))
    chest.cbox(0, 0, -p.chest_len / 2.0, p.chest_w, p.chest_h, p.chest_len, p.body_mat, decals=["belly"])

    # neck + head
    neck = chain(m, "neck", chest, (0, -p.chest_h * 0.22, -p.chest_len + 1), p.neck_n, p.neck_len, p.neck_w, p.neck_h,
                 p.neck_w * 0.9, p.neck_h * 0.9, pitch0=p.neck_pitch, pitch_step=p.neck_step, forward=True, role="NECK")
    hp = P(p)
    head, jaw = head_block(m, "head", neck[-1], (0, -1, -p.neck_len + 1), hp, rot=(p.head_pitch, 0, 0),
                           beak=has(p, "beak"), teeth=not has(p, "beak"))
    # tail
    tail = chain(m, "tail", body, (0, -p.body_h * 0.22, p.body_back - 1), p.tail_n, p.tail_len, p.tail_w0, p.tail_h0,
                 p.tail_w1, p.tail_h1, pitch0=p.tail_pitch, pitch_step=p.tail_step, role="TAIL")
    # legs
    hx = p.body_w / 2.0 - p.thigh[1] / 2.0 + 1
    for side in (-1, 1):
        leg_chain(m, "leg%s" % ("l" if side < 0 else "r"), body, (hx * side, 0, 0), [p.thigh, p.shin, p.meta], p.leg_angles,
                  parent_pitch=p.body_pitch, phase=0.0 if side < 0 else 0.5, side=side, leg_index=0 if side < 0 else 1,
                  mat=p.body_mat, foot=(p.toes[0], p.toes[1], p.toes[2], "claw"))
    # arms
    if p.arm[0] > 0:
        ax = p.chest_w / 2.0 - 0.5
        for side in (-1, 1):
            s = "l" if side < 0 else "r"
            a = m.part("arm_%s" % s, chest, (ax * side, p.chest_h * 0.15, -p.chest_len + 3), (p.arm_pitch, 0, -0.15 * side),
                       ("ARM", side, 0))
            a.cbox(0, p.arm[0] / 2.0, 0, p.arm[1], p.arm[0], p.arm[2], p.body_mat)
            f = m.part("forearm_%s" % s, a, (0, p.arm[0], 0), (-0.9, 0, 0), ("ARM", side, 1))
            f.cbox(0, p.forearm[0] / 2.0, 0, p.forearm[1], p.forearm[0], p.forearm[2], p.body_mat)
            f.cbox(0, p.forearm[0] + p.hand / 2.0, -0.5, p.forearm[1] + 1, p.hand, p.forearm[2] + 1, "claw")
            if has(p, "long_claws"):
                for k in (-1, 0, 1):
                    c = m.part("claw_%s%d" % (s, k + 1), f, (k * 1.2, p.forearm[0] + p.hand, 0), (0.5, 0, 0))
                    c.cbox(0, 3.5, 0, 1, 8, 1, "claw")
            if has(p, "arm_feathers"):
                a.cbox(side * 0.5, p.arm[0] / 2.0, 2, 1, p.arm[0] + 2, 4, "feather")
                f.cbox(side * 0.5, p.forearm[0] / 2.0 + 1, 2.5, 1, p.forearm[0] + 3, 5, "feather")
    _theropod_features(m, p, body, chest, neck, head, jaw, tail)
    if p.saddle:
        saddle(m, body, -p.body_h * 0.18 - p.body_h / 2.0, -front * 0.3, p.body_w, min(12, p.body_len * 0.45))
    return p


def _theropod_features(m, p, body, chest, neck, head, jaw, tail):
    crl, crh, crw = p.head_len, p.head_h, p.head_w
    top = -crh * 0.15 - crh / 2.0
    if has(p, "brow_horns"):   # carnotaurus
        for side in (-1, 1):
            horn(m, "browhorn_%d" % side, head, (side * (crw / 2.0 - 1), top + 1, -crl * 0.35), 6, 3, rx=0.1,
                 rz=0.9 * side, segs=2, curve=0.25)
    if has(p, "nose_horn"):
        horn(m, "nosehorn", head, (0, top + 1, -crl - p.snout_len * 0.4), 4, 2, rx=-0.3)
    if has(p, "eye_horns"):   # allosaurus / acro style small lacrimal horns
        for side in (-1, 1):
            horn(m, "lachorn_%d" % side, head, (side * (crw / 2.0 - 1.5), top + 0.5, -crl * 0.55), 2, 2, rx=0.4)
    if has(p, "double_crest"):  # dilophosaurus
        for side in (-1, 1):
            c = m.part("crest_%d" % side, head, (side * 1.5, top, -crl * 0.4), (0, 0, 0.12 * side))
            c.cbox(0, -2.5, -2, 1, 5, crl + p.snout_len * 0.6, "frill")
    if has(p, "frill"):
        fr = m.part("frill", head, (0, -crh * 0.1, -1), (0.35, 0, 0), ("FRILL",))
        fr.cbox(0, 0, 0, crw + 12, crh + 10, 1, "frill")
    if has(p, "dome"):  # pachycephalosaurus
        head.cbox(0, top - 2.5, -crl / 2.0 + 1, crw + 1, 5, crl - 1, "horn")
        for i in range(5):
            a = -PI / 2 + i * PI / 4
            k = m.part("knob%d" % i, head, (math.sin(a) * (crw / 2.0), top - 1, -1 + math.cos(a) * 1.5), (0.6, 0, 0))
            k.cbox(0, -1, 0, 2, 2, 2, "horn")
    if has(p, "head_crest"):   # crested head (oviraptor / cassowary style)
        c = m.part("crest", head, (0, top, -crl * 0.6), (-0.35, 0, 0))
        c.cbox(0, -3, 0, 2, 6, 6, "frill")
    if has(p, "sail"):   # spinosaurus
        h = p.get("sail_h", 16)
        for i, part in enumerate([body, chest]):
            seg = m.part("sail%d" % i, part, (0, -p.body_h * 0.6, (2 if i == 0 else -p.chest_len * 0.5)), (0, 0, 0))
            L = p.body_len - 4 if i == 0 else p.chest_len
            seg.cbox(0, -h / 2.0 * (1.0 if i == 0 else 0.8), 0 if i else -2, 1, h * (1.0 if i == 0 else 0.8), L, "sail")
    if has(p, "back_spines"):
        spike_row(m, "bspine", body, 5, -p.body_len * 0.5, p.body_back - 2, -p.body_h * 0.65, 4, 3, w=2)
    if has(p, "back_feathers") or has(p, "feathered"):
        body.cbox(0, -p.body_h * 0.68, (-(p.body_len - p.body_back) + p.body_back) / 2.0, p.body_w - 2, 2, p.body_len - 2, "feather")
        for i, t in enumerate(tail[:3]):
            t.cbox(0, -p.tail_h0 * (0.55 - i * 0.12), p.tail_len / 2.0, max(2, p.tail_w0 - 2 - i * 2), 2, p.tail_len, "feather")
    if has(p, "tail_fan"):
        last = tail[-1]
        fan = m.part("tailfan", last, (0, 0, p.tail_len), (0, 0, 0))
        fan.cbox(0, 0, 3, 8, 1, 7, "feather")
    if has(p, "quill_crest"):
        for i in range(3):
            q = m.part("quill%d" % i, head, (0, top, -crl * 0.1 + i * 2.5), (-0.9 + i * 0.25, 0, 0))
            q.cbox(0, -2.5, 0, 1, 5, 2, "feather")
    if has(p, "neck_mane"):
        for i, n in enumerate(neck):
            n.cbox(0, -p.neck_h * 0.55, -p.neck_len / 2.0, p.neck_w - 2, 3, p.neck_len + 1, "feather")
    if has(p, "sickle_claw"):
        for side in ("l", "r"):
            foot = m.parts["leg%s_3" % side]
            c = m.part("sickle_%s" % side, foot, (0, 0, -1), (-0.9, 0, 0))
            c.cbox(0, -2, 0, 1, 4, 1, "claw")
    if has(p, "tail_spikes"):
        last = tail[-1]
        for side in (-1, 1):
            for j in range(2):
                s = m.part("tspike%d_%d" % (j, side), last, (side * 1, 0, 2 + j * 3), (0, 0.4 * side, 1.3 * side))
                s.cbox(0, -3, 0, 1, 6, 1, "horn")
    if has(p, "tail_quills"):   # velonasaur
        last = tail[-2]
        for i in range(7):
            a = -PI / 2 + i * PI / 6
            s = m.part("tquill%d" % i, last, (0, 0, p.tail_len * 0.6), (-0.6, 0, a))
            s.cbox(0, -4, 0, 1, 8, 1, "horn")
    if has(p, "throat_pouch"):
        jaw.cbox(0, p.jaw_h + 1.5, -p.jaw_len * 0.35, p.jaw_w - 2, 3, p.jaw_len * 0.4, "frill") if jaw else None
    if has(p, "wattle"):
        if jaw:
            jaw.cbox(0, p.jaw_h + 2, -p.jaw_len * 0.2, 2, 3, 2, "frill")


# ============================================================================= QUADRUPED
QUAD = dict(
    body_y=20, body_len=30, body_w=16, body_h=15, body_pitch=0.0, body_mat="skin",
    neck_n=2, neck_len=6, neck_w=8, neck_h=9, neck_pitch=-0.25, neck_step=0.1,
    head_pitch=0.2, head_len=10, head_w=9, head_h=8, snout_len=5, snout_w=6, snout_h=5,
    jaw_len=11, jaw_w=6, jaw_h=2,
    tail_n=4, tail_len=7, tail_w0=7, tail_h0=7, tail_w1=2, tail_h1=2, tail_pitch=0.2, tail_step=0.08,
    front_leg=((9, 5, 5), (8, 4, 4)), back_leg=((10, 6, 7), (9, 4, 4)),
    front_angles=(0.05, -0.05), back_angles=(-0.15, 0.15), foot=(3, 5, 2), foot_mat="hoof",
    splay=0.0, gait="walk", features=(), saddle=True, chest_drop=0.0,
)


def quadruped(m, over):
    p = params(QUAD, over)
    G = GROUND
    body = m.part("body", None, (0, G - p.body_y, 0), (p.body_pitch, 0, 0), ("BODY",))
    body.cbox(0, 0, 0, p.body_w, p.body_h, p.body_len, p.body_mat, decals=["belly"])
    if has(p, "hump"):
        body.cbox(0, -p.body_h / 2.0 - p.get("hump_h", 4) / 2.0 + 1, -p.body_len * 0.15, p.body_w - 4, p.get("hump_h", 4), p.body_len * 0.4, p.body_mat)
    if has(p, "shoulder_hump"):
        body.cbox(0, -p.body_h / 2.0 - 1.5, -p.body_len * 0.3, p.body_w - 2, 5, p.body_len * 0.35, p.body_mat)
    # neck & head
    nz = -p.body_len / 2.0 + 1
    neck = chain(m, "neck", body, (0, -p.body_h * 0.15 + p.chest_drop, nz), p.neck_n, p.neck_len, p.neck_w, p.neck_h,
                 p.neck_w * 0.85, p.neck_h * 0.85, pitch0=p.neck_pitch, pitch_step=p.neck_step, forward=True, role="NECK") if p.neck_n > 0 else []
    hp = P(p)
    hparent = neck[-1] if neck else body
    hpiv = (0, 0, -p.neck_len + 1) if neck else (0, -p.body_h * 0.1, nz - 1)
    head, jaw = head_block(m, "head", hparent, hpiv, hp, rot=(p.head_pitch, 0, 0), beak=has(p, "beak"),
                           teeth=p.get("teeth", True) and not has(p, "beak"))
    # tail
    tail = []
    if p.tail_n > 0:
        tail = chain(m, "tail", body, (0, -p.body_h * 0.25, p.body_len / 2.0 - 1), p.tail_n, p.tail_len, p.tail_w0, p.tail_h0,
                     p.tail_w1, p.tail_h1, pitch0=p.tail_pitch, pitch_step=p.tail_step, role="TAIL")
    # legs: index 0 FL, 1 FR, 2 BL, 3 BR. trot-like phase for walk.
    fx = p.body_w / 2.0 - p.front_leg[0][1] / 2.0 + (1.5 if p.splay else 0.5)
    bx = p.body_w / 2.0 - p.back_leg[0][1] / 2.0 + (1.5 if p.splay else 0.5)
    fz = -p.body_len / 2.0 + p.front_leg[0][2] / 2.0 + 2
    bz = p.body_len / 2.0 - p.back_leg[0][2] / 2.0 - 2
    phases = {"walk": (0.0, 0.5, 0.75, 0.25), "trot": (0.0, 0.5, 0.5, 0.0), "bound": (0.0, 0.05, 0.5, 0.55),
              "amble": (0.0, 0.5, 0.25, 0.75)}[p.gait]
    fm = (p.foot[0], p.foot[1], p.foot[2], p.foot_mat) if p.foot else None
    y_leg = p.body_h * 0.3
    for i, (side, front) in enumerate([(-1, True), (1, True), (-1, False), (1, False)]):
        segs = list(p.front_leg if front else p.back_leg)
        ang = list(p.front_angles if front else p.back_angles)
        leg_chain(m, "leg%d" % i, body, ((fx if front else bx) * side, y_leg, fz if front else bz), segs, ang,
                  parent_pitch=p.body_pitch, phase=phases[i], side=side, leg_index=i, mat=p.body_mat, foot=fm,
                  splay=p.splay)
    _quad_features(m, p, body, neck, head, jaw, tail)
    if p.saddle:
        saddle(m, body, -p.body_h / 2.0, -p.body_len * 0.12, p.body_w, min(14, p.body_len * 0.4))
    return p


def _quad_features(m, p, body, neck, head, jaw, tail):
    crl, crh, crw = p.head_len, p.head_h, p.head_w
    top = -crh * 0.15 - crh / 2.0
    if has(p, "frill"):   # ceratopsian frill
        fr = m.part("frill", head, (0, top + 1, -1), (-0.75, 0, 0), ("FRILL",))
        fw, fh = p.get("frill_w", crw + 10), p.get("frill_h", 12)
        fr.cbox(0, -fh / 2.0, 1, fw, fh, 2, "frill")
        if has(p, "frill_spikes"):
            for i in range(5):
                a = -0.9 + i * 0.45
                s = m.part("fspike%d" % i, fr, (math.sin(a) * fw / 2.0, -fh + 1 + abs(math.sin(a)) * 3, 1), (0, 0, a))
                s.cbox(0, -2.5, 0, 2, 5, 2, "horn")
    if has(p, "brow_horns2"):   # triceratops
        for side in (-1, 1):
            horn(m, "browhorn_%d" % side, head, (side * (crw / 2.0 - 2), top + 1, -crl * 0.45), p.get("horn_len", 12), 3,
                 rx=-1.1, rz=0.12 * side, segs=3, curve=0.12)
    if has(p, "nose_horn"):
        horn(m, "nosehorn", head, (0, top + 2, -crl - p.snout_len * 0.3), p.get("nose_horn_len", 5), 3, rx=-0.45, segs=2, curve=-0.1)
    if has(p, "nose_boss"):  # pachyrhinosaurus
        head.cbox(0, top - 0.5, -crl - p.snout_len * 0.3 + 1, crw - 3, 3, p.snout_len + 2, "horn")
    if has(p, "rhino_horns"):
        horn(m, "rhinohorn0", head, (0, top + 1.5, -crl - p.snout_len + 2), 10, 4, rx=-0.55, segs=3, curve=-0.12)
        horn(m, "rhinohorn1", head, (0, top + 1, -crl - 1), 5, 3, rx=-0.35, segs=2)
    if has(p, "antlers"):    # megaloceros
        for side in (-1, 1):
            a = m.part("antler_%d" % side, head, (side * 2.5, top, -crl * 0.25), (-0.3, 0, 0.9 * side))
            a.cbox(0, -4, 0, 2, 8, 2, "horn")
            pal = m.part("antlerpalm_%d" % side, a, (0, -8, 0), (0, 0, 0.6 * side))
            pal.cbox(0, -2, 0, 1, 4, 10, "horn")
            for k in range(3):
                t = m.part("antlertine_%d%d" % (side, k), pal, (0, -3, -4 + k * 4), (-0.2, 0, 0))
                t.cbox(0, -2.5, 0, 1, 5, 1, "horn")
    if has(p, "ram_horns"):
        for side in (-1, 1):
            horn(m, "ramhorn_%d" % side, head, (side * (crw / 2.0 - 1), top + 1, -crl * 0.4), 9, 3, rx=0.5, rz=0.9 * side,
                 segs=4, curve=0.7)
    if has(p, "small_horns"):
        for side in (-1, 1):
            horn(m, "shorn_%d" % side, head, (side * (crw / 2.0 - 1.5), top, -crl * 0.3), 3, 2, rx=-0.3, rz=0.3 * side)
    if has(p, "tusks"):   # mammoth
        for side in (-1, 1):
            t = m.part("tusk_%d" % side, head, (side * 2.5, crh * 0.25, -crl * 0.6), (1.2, 0, -0.15 * side))
            t.cbox(0, 4, 0, 2, 8, 2, "tusk")
            t2 = m.part("tusk2_%d" % side, t, (0, 8, 0), (-1.4, 0, 0.2 * side))
            t2.cbox(0, 3, 0, 2, 6, 2, "tusk")
    if has(p, "short_tusks"):
        for side in (-1, 1):
            t = m.part("stusk_%d" % side, jaw if jaw else head, (side * 2, 0, -p.jaw_len * 0.8), (-0.9, 0, 0.3 * side))
            t.cbox(0, -2, 0, 1, 4, 1, "tusk")
    if has(p, "trunk"):
        tr = chain(m, "trunk", head, (0, crh * 0.1, -crl + 1), 4, 4, 4, 4, 2, 2, pitch0=1.2, pitch_step=0.2,
                   forward=True, role="TRUNK", mat=p.get("trunk_mat", p.body_mat))
    if has(p, "plates"):   # stegosaurus
        n = p.get("plate_n", 7)
        for i in range(n):
            t = i / (n - 1)
            z = -p.body_len * 0.42 + t * p.body_len * 0.85
            h = 4 + 7 * math.sin(t * PI)
            for side in (-1, 1):
                pl = m.part("plate%d_%d" % (i, side), body, (side * 1.5, -p.body_h / 2.0 + 1, z + side * 1.5), (0, 0, 0.18 * side))
                pl.cbox(0, -h / 2.0, 0, 1, h, h * 0.8 + 1, "plate")
        for i, t in enumerate(tail[:2]):
            pl = m.part("tplate%d" % i, t, (0, -p.tail_h0 * 0.4, p.tail_len * 0.5), (0, 0, 0))
            pl.cbox(0, -2.5, 0, 1, 5, 4, "plate")
    if has(p, "thagomizer"):
        last = tail[-1]
        for side in (-1, 1):
            for j in range(2):
                s = m.part("thago%d_%d" % (j, side), last, (side, -0.5, 1 + j * 3), (-0.4, 0.3 * side, 1.15 * side))
                s.cbox(0, -4, 0, 1, 8, 1, "horn")
    if has(p, "shoulder_spikes"):  # kentrosaurus / anky
        for side in (-1, 1):
            s = m.part("shspike_%d" % side, body, (side * (p.body_w / 2.0), -p.body_h * 0.15, -p.body_len * 0.3), (0, 0, 1.2 * side))
            s.cbox(0, -4, 0, 2, 8, 2, "horn")
    if has(p, "back_spikes"):
        spike_row(m, "bspike", body, p.get("spike_n", 6), -p.body_len * 0.4, p.body_len * 0.4, -p.body_h / 2.0,
                  4, 3, w=2, xs=(-2.5, 2.5))
    if has(p, "armor"):    # ankylosaurus osteoderm rows
        for row, x in enumerate((-p.body_w / 2.0 + 1, 0, p.body_w / 2.0 - 1)):
            for i in range(5):
                z = -p.body_len * 0.38 + i * p.body_len * 0.19
                o = m.part("osteo%d_%d" % (row, i), body, (x, -p.body_h / 2.0 + 0.5, z), (0, 0, 0.5 * (-1 if x < 0 else (1 if x > 0 else 0))))
                o.cbox(0, -1.5, 0, 3, 3, 3, "plate")
        for side in (-1, 1):
            for i in range(4):
                z = -p.body_len * 0.35 + i * p.body_len * 0.23
                s = m.part("sidespike%d_%d" % (i, side), body, (side * p.body_w / 2.0, p.body_h * 0.1, z), (0, 0, 1.45 * side))
                s.cbox(0, -2, 0, 2, 4, 2, "horn")
    if has(p, "tail_club"):
        last = tail[-1]
        c = m.part("tailclub", last, (0, 0, p.tail_len), (0, 0, 0))
        c.cbox(0, 0, 1.5, p.get("club_w", 8), 4, 5, "plate")
    if has(p, "shell"):    # turtle / glyptodont dome
        sh = p.get("shell_h", 8)
        body.cbox(0, -p.body_h / 2.0 - sh / 2.0 + 2, 0, p.body_w + 4, sh, p.body_len + 4, "shell")
        body.cbox(0, -p.body_h / 2.0 - sh + 1, 0, p.body_w - 2, 3, p.body_len - 4, "shell")
    if has(p, "tail_spiked_club"):  # doedicurus
        last = tail[-1]
        c = m.part("tailmace", last, (0, 0, p.tail_len), (0, 0, 0))
        c.cbox(0, 0, 2, 6, 6, 6, "shell")
        for k, (ax, ay) in enumerate(((1, 0), (-1, 0), (0, -1), (0, 1))):
            s = m.part("mace%d" % k, c, (ax * 3, ay * 3, 2), (ay * 1.2, 0, -ax * 1.2))
            s.cbox(0, -2, 0, 1, 4, 1, "horn")
    if has(p, "mane"):
        if neck:
            for n in neck:
                n.cbox(0, -p.neck_h * 0.35, -p.neck_len / 2.0, p.neck_w + 3, p.neck_h + 2, p.neck_len + 1, "mane")
        head.cbox(0, -crh * 0.1, 1, crw + 4, crh + 3, 4, "mane")
    if has(p, "ruff"):     # fur collar (direwolf / bear)
        body.cbox(0, -p.body_h * 0.05, -p.body_len / 2.0 + 3, p.body_w + 2, p.body_h + 2, 7, "fur2")
    if has(p, "long_fur"):   # mammoth / rhino / sloth shaggy skirt
        body.cbox(0, p.body_h * 0.35, 0, p.body_w + 2, p.body_h * 0.45, p.body_len - 2, "fur2")
    if has(p, "ears"):
        ears(m, head, p, crw / 2.0 - 1.5, top + 1, -crl * 0.15, length=p.get("ear_len", 4), w=p.get("ear_w", 3),
             mat=p.get("ear_mat", "fur"), splay=p.get("ear_splay", 0.35))
    if has(p, "big_ears"):   # elephant style
        for side in (-1, 1):
            e = m.part("bigear_%d" % side, head, (side * crw / 2.0, top + 3, -crl * 0.2), (0, 0.5 * side, 0), ("EAR", side))
            e.cbox(side * 3, 3, 0, 6, 8, 1, p.body_mat)
    if has(p, "sabers"):
        for side in (-1, 1):
            s = m.part("saber_%d" % side, head, (side * 1.8, -crh * 0.15 + (crh - p.snout_h) / 2.0 + p.snout_h / 2.0, -crl - p.snout_len * 0.4 + 1), (0.12, 0, 0))
            s.cbox(0, 3, 0, 1, 7, 2, "tusk")
    if has(p, "crest_tube"):   # parasaurolophus
        c = m.part("crest", head, (0, top + 1, -crl * 0.4), (-2.2, 0, 0))
        c.cbox(0, -p.get("crest_len", 14) / 2.0, 0, 3, p.get("crest_len", 14), 3, "frill")
    if has(p, "duck_bill"):
        head.cbox(0, -crh * 0.15 + (crh - p.snout_h) / 2.0 + 1, -crl - p.snout_len + 1, p.snout_w + 2, 2, 4, "beak")
    if has(p, "sail"):   # dimetrodon
        h = p.get("sail_h", 12)
        s = m.part("sail", body, (0, -p.body_h / 2.0 + 1, 0), (0, 0, 0))
        s.cbox(0, -h / 2.0, 0, 1, h, p.body_len - 4, "sail")
    if has(p, "spiky_skin"):   # thorny dragon
        for i in range(4):
            for side in (-1, 1):
                s = m.part("thorn%d_%d" % (i, side), body, (side * (p.body_w / 2.0 - 2), -p.body_h / 2.0, -p.body_len * 0.35 + i * p.body_len * 0.23), (0, 0, 0.6 * side))
                s.cbox(0, -2, 0, 2, 4, 2, "horn")
        for side in (-1, 1):
            horn(m, "cheekthorn_%d" % side, head, (side * (crw / 2.0), top + 2, -crl * 0.3), 4, 2, rx=0.2, rz=1.0 * side)
    if has(p, "head_spikes"):
        for side in (-1, 1):
            horn(m, "hspike_%d" % side, head, (side * (crw / 2.0 - 1), top + 1, -1), 5, 2, rx=0.9, rz=0.35 * side, segs=2, curve=0.2)
    if has(p, "gills_ext"):  # diplocaulus / axolotl-like
        for side in (-1, 1):
            g = m.part("gill_%d" % side, head, (side * crw / 2.0, top + 2, 0), (0, 0, 1.0 * side))
            g.cbox(0, -2, 0, 1, 4, 3, "frill")
    if has(p, "boomerang_head"):
        for side in (-1, 1):
            b = m.part("bhead_%d" % side, head, (side * crw / 2.0, -crh * 0.1, -1), (0, -0.7 * side, 0))
            b.cbox(side * 4, 0, 2, 9, 2, 4, p.body_mat)
    if has(p, "pouch"):
        body.cbox(0, p.body_h / 2.0, -p.body_len * 0.15, p.body_w - 4, 3, 8, "belly")
    if has(p, "claws_front"):
        for i in (0, 1):
            ft = m.parts.get("leg%d_2" % i)
            if ft:
                for k in (-1, 1):
                    c = m.part("fclaw_%d%d" % (i, k), ft, (k * 1.5, p.foot[2], -p.foot[0] + 1), (1.1, 0, 0))
                    c.cbox(0, 1.5, 0, 1, 4, 1, "claw")
    if has(p, "beard"):
        head.cbox(0, crh * 0.45, -crl * 0.5, crw - 3, 4, 4, "fur2")
    if has(p, "nose_trunk_short"):
        head.cbox(0, -crh * 0.1, -crl - p.snout_len - 1, p.snout_w - 2, p.snout_h - 1, 3, p.body_mat)


# ============================================================================= FLYER
FLY = dict(
    kind="ptero", body_y=12, body_len=12, body_w=7, body_h=7, body_mat="skin",
    neck_n=2, neck_len=5, neck_w=4, neck_h=4, neck_pitch=-0.6, neck_step=0.3,
    head_pitch=0.25, head_len=7, head_w=5, head_h=5, snout_len=10, snout_w=3, snout_h=3,
    jaw_len=13, jaw_w=3, jaw_h=2,
    tail_n=2, tail_len=4, tail_w0=3, tail_h0=3, tail_w1=1, tail_h1=1, tail_pitch=0.1, tail_step=0.05,
    wing=((10, 2, 3), (10, 2, 2), (12, 1, 1)), membrane=(14, 12, 8), wing_mat="membrane",
    leg=((5, 2, 2), (5, 1, 1)), leg_angles=(0.2, -0.2), foot=(3, 3, 1),
    quad_legs=False, front_leg=((7, 4, 4), (7, 3, 3)), back_leg=((8, 5, 6), (7, 3, 3)),
    features=(), saddle=True,
)


def flyer(m, over):
    p = params(FLY, over)
    G = GROUND
    body = m.part("body", None, (0, G - p.body_y, 0), (0, 0, 0), ("BODY",))
    body.cbox(0, 0, 0, p.body_w, p.body_h, p.body_len, p.body_mat, decals=["belly"])
    neck = chain(m, "neck", body, (0, -p.body_h * 0.2, -p.body_len / 2.0 + 1), p.neck_n, p.neck_len, p.neck_w, p.neck_h,
                 p.neck_w * 0.85, p.neck_h * 0.85, pitch0=p.neck_pitch, pitch_step=p.neck_step, forward=True, role="NECK") if p.neck_n else []
    hparent = neck[-1] if neck else body
    hpiv = (0, 0, -p.neck_len + 1) if neck else (0, -p.body_h * 0.2, -p.body_len / 2.0)
    beak = p.kind in ("bird", "ptero") and not has(p, "toothed")
    head, jaw = head_block(m, "head", hparent, hpiv, P(p), rot=(p.head_pitch, 0, 0), beak=beak, teeth=not beak)
    tail = chain(m, "tail", body, (0, -p.body_h * 0.1, p.body_len / 2.0 - 1), p.tail_n, p.tail_len, p.tail_w0, p.tail_h0,
                 p.tail_w1, p.tail_h1, pitch0=p.tail_pitch, pitch_step=p.tail_step, role="TAIL") if p.tail_n else []
    # wings: each side, 3 segments along +-x. membrane plates hang behind the bones (+z).
    mw, md0, md1 = p.membrane
    for side in (-1, 1):
        s = "l" if side < 0 else "r"
        prev = body
        piv = (side * (p.body_w / 2.0 - 0.5), -p.body_h * 0.3, -p.body_len * 0.25)
        for k, (L, w, d) in enumerate(p.wing):
            wp = m.part("wing_%s%d" % (s, k), prev, piv, (0, (-0.15 if k == 0 else 0.1) * side, (0.12 if k == 0 else 0.0) * side),
                        ("WING", side, k))
            wp.cbox(side * L / 2.0, 0, 0, L + 1, w, d, "bone" if p.wing_mat == "membrane" else p.wing_mat)
            dd = [md0, (md0 + md1) / 2.0, md1][k]
            if p.wing_mat == "membrane":
                wp.cbox(side * L / 2.0, 0, d / 2.0 + dd / 2.0, L + 1, 1, dd, "membrane")
            else:   # feathered wing: layered feather plates
                wp.cbox(side * L / 2.0, 0, d / 2.0 + dd / 2.0 - 0.5, L + 1, 1, dd, p.wing_mat)
                if k == len(p.wing) - 1:
                    wp.cbox(side * (L + 2), 0, d / 2.0 + dd / 2.0 - 1, 4, 1, dd - 2, p.wing_mat)
            prev = wp
            piv = (side * L, 0, 0)
    # legs
    if p.quad_legs:
        fx = p.body_w / 2.0 - 1
        for i, (side, front) in enumerate([(-1, True), (1, True), (-1, False), (1, False)]):
            segs = list(p.front_leg if front else p.back_leg)
            leg_chain(m, "leg%d" % i, body, (fx * side, p.body_h * 0.3, (-p.body_len / 2.0 + 3) if front else (p.body_len / 2.0 - 3)),
                      segs, [0.1, -0.1] if front else [-0.2, 0.2], phase=(0.0, 0.5, 0.75, 0.25)[i], side=side, leg_index=i,
                      mat=p.body_mat, foot=(p.foot[0], p.foot[1], p.foot[2], "claw"))
    else:
        for side in (-1, 1):
            leg_chain(m, "leg%s" % ("l" if side < 0 else "r"), body, (side * (p.body_w / 2.0 - 1.5), p.body_h * 0.3, p.body_len * 0.2),
                      list(p.leg), p.leg_angles, phase=0.0 if side < 0 else 0.5, side=side, leg_index=0 if side < 0 else 1,
                      mat=p.body_mat if p.kind != "bird" else "beak", foot=(p.foot[0], p.foot[1], p.foot[2], "claw"))
    _flyer_features(m, p, body, neck, head, jaw, tail)
    if p.saddle:
        saddle(m, body, -p.body_h / 2.0, -p.body_len * 0.1, p.body_w, min(10, p.body_len * 0.5))
    return p


def _flyer_features(m, p, body, neck, head, jaw, tail):
    crl, crh, crw = p.head_len, p.head_h, p.head_w
    top = -crh * 0.15 - crh / 2.0
    if has(p, "head_crest"):    # pteranodon backwards crest
        c = m.part("crest", head, (0, top + 1, 0), (0.55, 0, 0))
        c.cbox(0, -1, p.get("crest_len", 10) / 2.0, 1, 3, p.get("crest_len", 10), "frill")
    if has(p, "sail_crest"):   # tapejara / quetzal tall crest
        c = m.part("crest", head, (0, top, -crl * 0.5), (0.2, 0, 0))
        c.cbox(0, -p.get("crest_h", 8) / 2.0, 1, 1, p.get("crest_h", 8), p.get("crest_len", 10), "frill")
    if has(p, "horns_back"):    # wyvern / dragon
        for side in (-1, 1):
            horn(m, "horn_%d" % side, head, (side * (crw / 2.0 - 1), top + 1, -1), p.get("horn_len", 8), 2, rx=1.1, rz=0.2 * side,
                 segs=3, curve=0.15)
    if has(p, "horns_side"):
        for side in (-1, 1):
            horn(m, "shorn_%d" % side, head, (side * crw / 2.0, top + 2, -crl * 0.3), 5, 2, rx=0.6, rz=1.1 * side, segs=2, curve=0.3)
    if has(p, "horn_crown"):
        for i in range(5):
            a = -0.8 + i * 0.4
            horn(m, "crown%d" % i, head, (math.sin(a) * crw / 2.0, top, -0.5 + abs(a)), 4, 1, rx=0.9, rz=a * 0.6)
    if has(p, "back_spikes"):
        spike_row(m, "bspike", body, 4, -p.body_len * 0.4, p.body_len * 0.4, -p.body_h / 2.0, 3, 2, w=1)
        for i, t in enumerate(tail):
            if i % 2 == 0:
                s = m.part("tspike%d" % i, t, (0, -p.tail_h0 * 0.4, p.tail_len * 0.5), (0, 0, 0))
                s.cbox(0, -1.5, 0, 1, 3, 2, "horn")
    if has(p, "tail_blade"):
        last = tail[-1]
        b = m.part("tailblade", last, (0, 0, p.tail_len), (0, 0, 0))
        b.cbox(0, 0, 3, 6, 1, 6, "membrane")
    if has(p, "tail_spade_v"):
        last = tail[-1]
        b = m.part("tailspade", last, (0, 0, p.tail_len), (0, 0, 0))
        b.cbox(0, 0, 3, 1, 6, 6, "frill")
    if has(p, "tail_feathers"):
        last = tail[-1] if tail else body
        b = m.part("tailfeathers", last, (0, 0, (p.tail_len if tail else p.body_len / 2.0)), (-0.1, 0, 0))
        b.cbox(0, 0, p.get("tail_feather_len", 6) / 2.0, p.get("tail_feather_w", 7), 1, p.get("tail_feather_len", 6), p.get("plume_mat", "feather"))
    if has(p, "plumage"):
        body.cbox(0, -0.5, 0, p.body_w + 1, p.body_h + 1, p.body_len - 1, "feather", inflate=0.3)
    if has(p, "throat_spines"):
        if jaw:
            for i in range(3):
                s = m.part("tspine%d" % i, jaw, (0, p.jaw_h, -2 - i * 2), (-0.5, 0, 0))
                s.cbox(0, 1.5, 0, 1, 3, 1, "horn")
    if has(p, "lion_mane"):
        if neck:
            for n in neck:
                n.cbox(0, 0, -p.neck_len / 2.0, p.neck_w + 4, p.neck_h + 4, p.neck_len + 1, "mane")
    if has(p, "scorpion_tail"):
        last = tail[-1]
        st = chain(m, "sting", last, (0, 0, p.tail_len), 3, 4, 3, 3, 2, 2, pitch0=-0.9, pitch_step=-0.6, role="STINGER", mat="chitin")
        s = m.part("stinger", st[-1], (0, 0, 4), (-0.6, 0, 0))
        s.cbox(0, 0, 1.5, 1, 1, 4, "claw")
        for i in range(4):
            q = m.part("tailspine%d" % i, st[-1], (0, -1.5, 2), (-0.9 + i * 0.1, 0, -0.9 + i * 0.6))
            q.cbox(0, -2, 0, 1, 4, 1, "horn")
    if has(p, "fire_plumes"):
        for i in range(3):
            fp = m.part("plume%d" % i, head, (0, top, i * 1.5), (0.9 + i * 0.2, 0, 0))
            fp.cbox(0, -3, 0, 1, 6, 2, "fire")
    if has(p, "beak_hook"):
        head.cbox(0, -crh * 0.15 + crh / 2.0 - 0.5, -crl - p.snout_len + 1.5, p.snout_w, 3, 2, "beak")
    if has(p, "ear_tufts"):
        for side in (-1, 1):
            e = m.part("tuft_%d" % side, head, (side * (crw / 2.0 - 1), top + 0.5, -1), (0.4, 0, 0.3 * side))
            e.cbox(0, -2, 0, 2, 4, 1, p.get("plume_mat", "feather"))


# ============================================================================= SWIMMER
SWIM = dict(
    kind="fish", body_y=8, seg_n=4, seg_len=(9, 9, 8, 7), w0=10, h0=11, w1=3, h1=4, body_mat="skin",
    head_len=10, head_w=9, head_h=9, snout_len=5, snout_w=6, snout_h=5, jaw_len=12, jaw_w=7, jaw_h=2, head_pitch=0.0,
    tail_fin=(12, 1, 8), tail_fin_kind="vertical", dorsal=(8, 7), pectoral=(7, 1, 4), pelvic=None,
    flippers=None, neck_n=0, neck_len=6, neck_w=5, neck_h=5, features=(), saddle=True,
)


def swimmer(m, over):
    p = params(SWIM, over)
    G = GROUND
    lens = list(p.seg_len)
    body = m.part("body", None, (0, G - p.body_y, -lens[0] / 2.0), (0, 0, 0), ("BODY",))
    # first segment is the body part itself (front trunk); the rest chain backwards
    body.cbox(0, 0, lens[0] / 2.0, p.w0, p.h0, lens[0] + 1, p.body_mat, decals=["belly"])
    segs = [body]
    prev = body
    for i in range(1, p.seg_n):
        t = i / max(1, p.seg_n - 1)
        w = p.w0 + (p.w1 - p.w0) * t
        h = p.h0 + (p.h1 - p.h0) * t
        s = m.part("spine%d" % i, prev, (0, 0, lens[i - 1]), (0, 0, 0), ("SPINE", i, p.seg_n))
        s.cbox(0, 0, lens[i] / 2.0, w, h, lens[i] + 1, p.body_mat, decals=["belly"], tone=0.02 * i)
        segs.append(s)
        prev = s
    # tail fin on last segment
    last = segs[-1]
    tf = m.part("tailfin", last, (0, 0, lens[-1]), (0, 0, 0), ("FIN", "TAIL", 0))
    fl, fw, fd = p.tail_fin
    if p.tail_fin_kind == "vertical":       # fish / shark: crescent made of two angled lobes
        up = m.part("tailfin_up", tf, (0, 0, 0), (-0.75, 0, 0))
        up.cbox(0, -fl / 2.0, fd / 2.0 - 1, 1, fl, fd, "fin")
        lo = m.part("tailfin_lo", tf, (0, 0, 0), (0.75 if not has(p, "heterocercal") else 0.95, 0, 0))
        lo.cbox(0, (fl * (0.5 if not has(p, "heterocercal") else 0.35)), fd / 2.0 - 1, 1, fl * (1.0 if not has(p, "heterocercal") else 0.7), fd, "fin")
    elif p.tail_fin_kind == "fluke":        # whale / ichthyosaur horizontal fluke
        for side in (-1, 1):
            lb = m.part("fluke_%d" % side, tf, (0, 0, 0), (0, 0.6 * side, 0))
            lb.cbox(side * fl / 4.0, 0, fd / 2.0, fl / 2.0, 1, fd, "fin")
    elif p.tail_fin_kind == "paddle":       # eel / sea snake ribbon
        tf.cbox(0, 0, fd / 2.0, 1, fl, fd, "fin")
    # head
    if p.neck_n:
        neck = chain(m, "neck", body, (0, -p.h0 * 0.1, 0), p.neck_n, p.neck_len, p.neck_w, p.neck_h, p.neck_w * 0.8, p.neck_h * 0.8,
                     pitch0=-0.15, pitch_step=0.05, forward=True, role="NECK")
        hpar, hpiv = neck[-1], (0, 0, -p.neck_len + 1)
    else:
        hpar, hpiv = body, (0, 0, 1)
    head, jaw = head_block(m, "head", hpar, hpiv, P(p), rot=(p.head_pitch, 0, 0), teeth=p.get("teeth", True),
                           beak=has(p, "beak"))
    # dorsal / pectoral fins
    if p.dorsal:
        dl, dh = p.dorsal
        seg = segs[min(1, len(segs) - 1)] if not has(p, "dorsal_front") else body
        d = m.part("dorsal", seg, (0, -(p.h0 if seg is body else p.h0 * 0.85) / 2.0 + 0.5, 2), (-0.35, 0, 0), ("FIN", "DORSAL", 0))
        d.cbox(0, -dh / 2.0, dl / 2.0, 1, dh, dl, "fin")
    if p.pectoral:
        pl, pw, pd = p.pectoral
        for side in (-1, 1):
            f = m.part("pectoral_%d" % side, body, (side * p.w0 / 2.0, p.h0 * 0.3, lens[0] * 0.25), (0.3, 0, 0.5 * side), ("FIN", "PECTORAL", side))
            f.cbox(side * pl / 2.0, 0, pd / 2.0, pl, pw, pd, "fin")
    if p.pelvic:
        pl, pw, pd = p.pelvic
        for side in (-1, 1):
            seg = segs[min(2, len(segs) - 1)]
            f = m.part("pelvic_%d" % side, seg, (side * p.w1 * 0.8, p.h0 * 0.3, 2), (0.4, 0, 0.6 * side), ("FIN", "PELVIC", side))
            f.cbox(side * pl / 2.0, 0, pd / 2.0, pl, pw, pd, "fin")
    if p.flippers:   # marine reptiles: front + back flippers
        (fl1, fw1, fd1), (fl2, fw2, fd2) = p.flippers
        for side in (-1, 1):
            f = m.part("flipper_f%d" % side, body, (side * p.w0 / 2.0, p.h0 * 0.35, lens[0] * 0.3), (0.2, -0.3 * side, 0.25 * side), ("FIN", "FLIPPER_F", side))
            f.cbox(side * fl1 / 2.0, 0, fd1 / 2.0 - 1, fl1, fw1, fd1, "fin")
            seg = segs[min(2, len(segs) - 1)]
            f2 = m.part("flipper_b%d" % side, seg, (side * p.w0 * 0.4, p.h0 * 0.3, 2), (0.2, -0.3 * side, 0.25 * side), ("FIN", "FLIPPER_B", side))
            f2.cbox(side * fl2 / 2.0, 0, fd2 / 2.0 - 1, fl2, fw2, fd2, "fin")
    _swim_features(m, p, body, segs, head, jaw)
    if p.saddle:
        saddle(m, body, -p.h0 / 2.0, lens[0] * 0.6, p.w0, min(10, lens[0]))
    return p


def _swim_features(m, p, body, segs, head, jaw):
    crl, crh, crw = p.head_len, p.head_h, p.head_w
    top = -crh * 0.15 - crh / 2.0
    if has(p, "armor_head"):   # dunkleosteus bony plates
        head.cbox(0, -crh * 0.15, -crl / 2.0 + 1, crw + 2, crh + 2, crl + 1, "plate")
        if jaw:
            jaw.cbox(0, p.jaw_h / 2.0, -p.jaw_len / 2.0 + crl * 0.25, p.jaw_w + 1, p.jaw_h + 1, p.jaw_len + 1, "plate")
    if has(p, "lure"):    # anglerfish
        l = m.part("lure", head, (0, top, -crl * 0.6), (-0.6, 0, 0), ("LURE",))
        l.cbox(0, -4, 0, 1, 8, 1, "bone")
        b = m.part("lure_bulb", l, (0, -8, 0), (1.2, 0, 0))
        b.cbox(0, -1, -1, 3, 3, 3, "glow")
    if has(p, "sword_snout"):
        head.cbox(0, -crh * 0.1, -crl - p.snout_len - 6, 2, 2, 12, "bone")
    if has(p, "whiskers"):
        for side in (-1, 1):
            w = m.part("whisker_%d" % side, head, (side * 2, crh * 0.2, -crl - 1), (0.5, 0.5 * side, 0))
            w.cbox(0, 3, 0, 1, 6, 1, "skin")
    if has(p, "eye_stalks"):
        for side in (-1, 1):
            e = m.part("eyestalk_%d" % side, head, (side * crw / 2.0, top + 2, -crl * 0.3), (0, 0, 0.6 * side))
            e.cbox(0, -2, 0, 2, 4, 2, "skin", decals=["eyes"])
    if has(p, "ray_wings"):   # manta: big pectoral wings chained
        for side in (-1, 1):
            prev = body
            piv = (side * p.w0 / 2.0, 0, 2)
            for k, (L, D) in enumerate(((12, 16), (10, 10), (7, 5))):
                wp = m.part("raywing_%d%d" % (side, k), prev, piv, (0, 0, 0), ("WING", side, k))
                wp.cbox(side * L / 2.0, 0, D / 2.0 - 4 + k * 2, L + 1, max(1, 3 - k), D, p.body_mat, decals=["belly"])
                prev = wp
                piv = (side * L, 0, 0)
        for side in (-1, 1):   # cephalic fins
            cf = m.part("cephfin_%d" % side, head, (side * (crw / 2.0 - 1), 0, -crl + 1), (0.3, 0, 0))
            cf.cbox(0, 1, -2, 2, 2, 5, p.body_mat)
    if has(p, "spiny_dorsal"):
        for i, s in enumerate(segs[:3]):
            sp = m.part("dspine%d" % i, s, (0, -p.h0 * 0.45, 3), (-0.4, 0, 0))
            sp.cbox(0, -2.5, 0, 1, 5, 1, "horn")
    if has(p, "nose_crest"):
        head.cbox(0, top - 1, -crl * 0.4, 2, 3, crl * 0.6, "frill")
    if has(p, "back_ridge"):
        for i, s in enumerate(segs):
            s.cbox(0, -p.h0 * (0.5 - 0.05 * i), (p.seg_len[i] if isinstance(p.seg_len, (list, tuple)) else 6) / 2.0, 2, 2, 5, "plate")


# ============================================================================= ARTHROPOD
ARTH = dict(
    kind="spider", body_y=8, ceph=(8, 6, 8), abdomen=(11, 10, 12), body_mat="chitin",
    leg_n=4, leg=((7, 2, 2), (9, 1, 1), (3, 1, 1)), leg_up=-0.9, leg_down=1.5, leg_spread=0.6,
    mandibles=True, fangs=True, eyes=True, wings=None, tail_n=0, pincers=False, antennae=False,
    segments=0, features=(), saddle=True,
)


def arthropod(m, over):
    p = params(ARTH, over)
    G = GROUND
    cw, ch, cl = p.ceph
    aw, ah, al = p.abdomen
    body = m.part("body", None, (0, G - p.body_y, 0), (0, 0, 0), ("BODY",))
    body.cbox(0, 0, 0, cw, ch, cl, p.body_mat, decals=["eyes_front"] if p.eyes else [])
    if p.segments:   # arthropluera / trilobite: chain of body segments, a leg pair on each
        prev = body
        seg_l = p.get("seg_len", 5)
        segs = []
        for i in range(p.segments):
            s = m.part("spine%d" % (i + 1), prev, (0, 0, cl / 2.0 if i == 0 else seg_l), (0, 0, 0), ("SPINE", i + 1, p.segments + 1))
            s.cbox(0, 0, seg_l / 2.0, cw - 1, ch - 1, seg_l + 1, p.body_mat, tone=0.03 * (i % 2))
            segs.append(s)
            prev = s
        for i, s in enumerate(segs):
            for side in (-1, 1):
                lp = m.part("leg%d_%d" % (i, 0 if side < 0 else 1), s, (side * (cw / 2.0 - 0.5), ch * 0.3, seg_l / 2.0),
                            (0, 0, -0.7 * side), ("LEG", i * 2 + (0 if side < 0 else 1), 0, round(((i * 0.17) % 1.0) + (0.5 if side > 0 else 0), 3) % 1.0, side))
                lp.cbox(side * 2.5, 0, 0, 5, 1, 1, p.body_mat)
                lp2 = m.part("leg%d_%d_k" % (i, 0 if side < 0 else 1), lp, (side * 5, 0, 0), (0, 0, -1.0 * side), ("LEG", i * 2 + (0 if side < 0 else 1), 1, 0, side))
                lp2.cbox(side * 2.5, 0, 0, 5, 1, 1, p.body_mat)
        abd = segs[-1]
    else:
        abd = m.part("abdomen", body, (0, -ch * 0.1, cl / 2.0), (p.get("abd_pitch", -0.15), 0, 0), ("TAIL", 0, 1))
        abd.cbox(0, 0, al / 2.0, aw, ah, al, p.body_mat, decals=["belly"])
        if has(p, "egg_sac"):
            abd.cbox(0, ah * 0.4, al * 0.6, aw - 2, ah * 0.7, al * 0.6, "egg")
        if has(p, "abdomen_stripes"):
            abd.cbox(0, -ah / 2.0, al / 2.0, aw - 2, 1, al - 2, "skin2")
        # legs radiating from the cephalothorax
        n = p.leg_n
        for i in range(n):
            t = i / max(1, n - 1)
            z = -cl / 2.0 + 1 + t * (cl - 2)
            for side in (-1, 1):
                ry = (-0.7 + t * 1.4) * p.leg_spread * side
                idx = i * 2 + (0 if side < 0 else 1)
                phase = ((i % 2) * 0.5 + (0.5 if side > 0 else 0.0)) % 1.0
                L1, L2, L3 = p.leg
                up = m.part("leg%d_%d" % (i, 0 if side < 0 else 1), body, (side * (cw / 2.0 - 0.5), ch * 0.1, z),
                            (0, ry, p.leg_up * side), ("LEG", idx, 0, phase, side))
                up.cbox(side * L1[0] / 2.0, 0, 0, L1[0] + 1, L1[1], L1[2], p.body_mat)
                lo = m.part("leg%d_%d_k" % (i, 0 if side < 0 else 1), up, (side * L1[0], 0, 0), (0, 0, p.leg_down * side),
                            ("LEG", idx, 1, phase, side))
                lo.cbox(side * L2[0] / 2.0, 0, 0, L2[0] + 1, L2[1], L2[2], p.body_mat)
                if L3[0] > 0:
                    ft = m.part("leg%d_%d_f" % (i, 0 if side < 0 else 1), lo, (side * L2[0], 0, 0), (0, 0, -0.5 * side),
                                ("LEG", idx, 2, phase, side))
                    ft.cbox(side * L3[0] / 2.0, 0, 0, L3[0], L3[1], L3[2], "claw")
    # head bits
    hz = -cl / 2.0
    if p.get("head", None):
        hw, hh, hl = p.head
        head = m.part("head", body, (0, -ch * 0.1, hz), (p.get("head_pitch", 0.2), 0, 0), ("HEAD",))
        head.cbox(0, 0, -hl / 2.0, hw, hh, hl, p.body_mat, decals=["eyes_big"] if p.eyes else [])
        hpart, hz2 = head, -hl
    else:
        hpart, hz2 = body, hz
        m.parts["body"].role = ("BODY",)
    if p.mandibles:
        for side in (-1, 1):
            md = m.part("mandible_%d" % side, hpart, (side * 1.5, ch * 0.2, hz2), (0.3, 0.3 * side, 0), ("MANDIBLE", side))
            md.cbox(0, 0, -2, 1, 2, 4, "claw")
    if p.fangs:
        for side in (-1, 1):
            fg = m.part("fang_%d" % side, hpart, (side * 1.5, ch * 0.35, hz2 + 0.5), (0.4, 0, 0), ("MANDIBLE", side))
            fg.cbox(0, 1.5, 0, 2, 3, 2, "claw")
    if p.antennae:
        for side in (-1, 1):
            a = m.part("antenna_%d" % side, hpart, (side * 1.5, -ch * 0.3, hz2), (-0.9, 0.3 * side, 0), ("ANTENNA", side))
            a.cbox(0, -p.get("antenna_len", 8) / 2.0, 0, 1, p.get("antenna_len", 8), 1, p.body_mat)
    if p.pincers:   # scorpion claws
        for side in (-1, 1):
            arm = m.part("pincer_%d" % side, body, (side * cw / 2.0, ch * 0.1, hz + 1), (0, -0.6 * side, 0), ("ARM", side, 0))
            arm.cbox(0, 0, -3, 2, 2, 7, p.body_mat)
            cl_ = m.part("pincer_claw_%d" % side, arm, (0, 0, -7), (0, 0.9 * side, 0), ("ARM", side, 1))
            cl_.cbox(0, 0, -3, 4, 3, 6, p.body_mat)
            cl_.cbox(side * 1.5, 0, -7, 1, 2, 3, "claw")
            cl_.cbox(-side * 1.5, 0, -7, 1, 2, 3, "claw")
    if p.tail_n:   # scorpion tail
        piv = (0, -ah * 0.2, al) if not p.segments else (0, 0, p.get("seg_len", 5))
        st = chain(m, "sting", abd, piv, p.tail_n, 4, 3, 3, 2, 2, pitch0=-0.8, pitch_step=-0.45, role="STINGER", mat=p.body_mat)
        s = m.part("stinger", st[-1], (0, 0, 4), (-0.8, 0, 0))
        s.cbox(0, 0, 1.5, 2, 2, 3, "glow" if has(p, "glow_sting") else "claw")
        s.cbox(0, 0.5, 3.5, 1, 1, 2, "claw")
    if p.wings:   # insects: 2 or 4 wing plates
        wl, wd, pairs = p.wings
        for k in range(pairs):
            for side in (-1, 1):
                w = m.part("wing_%s%d" % ("l" if side < 0 else "r", k), body, (side * (cw / 2.0 - 1), -ch / 2.0, -cl * 0.2 + k * 3),
                           (0, (-0.25 + k * 0.5) * side, 0.15 * side), ("WING", side, k))
                w.cbox(side * wl / 2.0, 0, wd / 2.0 - 1, wl, 1, wd, p.get("wing_mat", "insectwing"))
    if has(p, "raptorial"):   # mantis forelegs
        for side in (-1, 1):
            a = m.part("raptor_%d" % side, body, (side * (cw / 2.0 - 0.5), ch * 0.2, hz + 2), (-0.9, 0, 0), ("ARM", side, 0))
            a.cbox(0, 3, 0, 2, 7, 2, p.body_mat)
            b = m.part("raptor2_%d" % side, a, (0, 7, 0), (2.2, 0, 0), ("ARM", side, 1))
            b.cbox(0, 3, 0, 2, 7, 1, p.body_mat)
            for k in range(3):
                b.cbox(0, 1 + k * 2, 1, 1, 1, 2, "claw")
    if has(p, "shell_dome"):   # beetle / trilobite
        body.cbox(0, -ch / 2.0, 0, cw + 2, 3, cl + 2, "shell")
        if not p.segments:
            abd.cbox(0, -ah / 2.0 - 0.5, al / 2.0, aw + 2, 3, al + 1, "shell")
    if has(p, "horn"):
        horn(m, "horn", hpart, (0, -ch * 0.3, hz2 - 1), 6, 2, rx=-0.6, segs=2, curve=-0.25)
    if has(p, "fuzz"):
        body.cbox(0, 0, 0, cw + 1, ch + 1, cl + 1, "fur", inflate=0.4)
    if has(p, "paddles"):
        for side in (-1, 1):
            pd = m.part("paddle_%d" % side, body, (side * cw / 2.0, ch * 0.2, cl * 0.3), (0, 0, 0.3 * side), ("FIN", "PECTORAL", side))
            pd.cbox(side * 3, 0, 0, 6, 1, 4, p.body_mat)
    if p.saddle:
        saddle(m, body, -ch / 2.0, 0, cw, min(8, cl))
    return p


# ============================================================================= SERPENT
SERP = dict(
    body_y=3, seg_n=8, seg_len=6, w0=6, h0=5, w1=2, h1=2, body_mat="skin",
    head_len=7, head_w=7, head_h=5, snout_len=3, snout_w=5, snout_h=3, jaw_len=9, jaw_w=5, jaw_h=1, head_pitch=0.0,
    raise_front=0.0, features=(), saddle=False, mouth_round=False,
)


def serpent(m, over):
    p = params(SERP, over)
    G = GROUND
    body = m.part("body", None, (0, G - p.body_y, 0), (-p.raise_front, 0, 0), ("BODY",))
    L = p.seg_len
    body.cbox(0, 0, L / 2.0, p.w0, p.h0, L + 1, p.body_mat, decals=["belly"])
    prev = body
    segs = [body]
    for i in range(1, p.seg_n):
        t = i / max(1, p.seg_n - 1)
        w = p.w0 + (p.w1 - p.w0) * (t ** 1.6)
        h = p.h0 + (p.h1 - p.h0) * (t ** 1.6)
        s = m.part("spine%d" % i, prev, (0, 0, L), (p.raise_front if i == 1 else 0, 0, 0), ("SPINE", i, p.seg_n))
        s.cbox(0, 0, L / 2.0, w, h, L + 1, p.body_mat, decals=["belly"], tone=0.02 * (i % 3))
        segs.append(s)
        prev = s
    if p.mouth_round:   # worm: round toothed maw
        head = m.part("head", body, (0, 0, 0), (0, 0, 0), ("HEAD",))
        head.cbox(0, 0, -p.head_len / 2.0, p.head_w, p.head_h, p.head_len, p.body_mat, decals=["maw"])
        for k in range(4):
            a = k * PI / 2
            mand = m.part("mandible_%d" % k, head, (math.cos(a) * p.head_w * 0.35, math.sin(a) * p.head_h * 0.35, -p.head_len),
                          (math.sin(a) * 0.5, -math.cos(a) * 0.5, 0), ("MANDIBLE", 1 if k % 2 else -1))
            mand.cbox(0, 0, -2, 2, 2, 5, "tusk")
        jaw = None
    else:
        head, jaw = head_block(m, "head", body, (0, 0, 0), P(p), rot=(p.head_pitch, 0, 0))
    if has(p, "frill"):
        fr = m.part("frill", head, (0, 0, 0), (0.0, 0, 0), ("FRILL",))
        fr.cbox(0, -1, 1, p.head_w + 10, p.head_h + 8, 1, "frill")
    if has(p, "horns"):
        for side in (-1, 1):
            horn(m, "horn_%d" % side, head, (side * (p.head_w / 2.0 - 1), -p.head_h * 0.6, -1), 6, 2, rx=1.2, rz=0.3 * side, segs=2, curve=0.2)
    if has(p, "ice_spikes"):
        for i in range(0, p.seg_n, 2):
            s = m.part("icespike%d" % i, segs[i], (0, -p.h0 * 0.5, L / 2.0), (-0.3, 0, 0))
            s.cbox(0, -2.5, 0, 2, 5, 2, "ice")
    if has(p, "suckers"):
        head.cbox(0, 0, -p.head_len - 1, p.head_w - 1, p.head_h - 1, 2, "mouth")
    if has(p, "dorsal_ridge"):
        for i in range(1, p.seg_n - 1):
            segs[i].cbox(0, -p.h0 * 0.5, L / 2.0, 1, 2, L, "frill")
    return p


# ============================================================================= PRIMATE / GOLEM (upright)
PRIM = dict(
    hip_h=14, body_mat="fur", torso=(12, 12, 7), chest=(14, 9, 9), body_pitch=0.55,
    head_len=7, head_w=7, head_h=7, snout_len=3, snout_w=5, snout_h=3, jaw_len=5, jaw_w=5, jaw_h=2, head_pitch=-0.4,
    arm=((10, 4, 4), (10, 4, 4)), hand=(3, 4, 4), arm_pitch=-0.4,
    leg=((8, 5, 5), (7, 4, 4)), leg_angles=(-0.4, 0.3), foot=(5, 4, 2), tail_n=0, tail_len=5, features=(), saddle=True,
    face_mat="skin",
)


def primate(m, over):
    p = params(PRIM, over)
    G = GROUND
    tw, th, td = p.torso
    body = m.part("body", None, (0, G - p.hip_h, 0), (p.body_pitch, 0, 0), ("BODY",))
    body.cbox(0, -th / 2.0, 0, tw, th, td, p.body_mat, decals=["belly"])
    cw, ch, cd = p.chest
    chest = m.part("chest", body, (0, -th + 1, 0), (0, 0, 0), ("CHEST",))
    chest.cbox(0, -ch / 2.0, 0, cw, ch, cd, p.body_mat, decals=["belly"])
    head = m.part("head", chest, (0, -ch + 1, -cd * 0.2), (p.head_pitch, 0, 0), ("HEAD",))
    head.cbox(0, -p.head_h / 2.0, -p.head_len / 2.0 + 1, p.head_w, p.head_h, p.head_len, p.get("head_mat", p.body_mat), decals=["eyes", "face"])
    if p.snout_len > 0:
        head.cbox(0, -p.head_h * 0.3, -p.head_len + 1 - p.snout_len / 2.0 + 0.5, p.snout_w, p.snout_h, p.snout_len, p.face_mat, decals=["nostrils"])
    jaw = m.part("head_jaw", head, (0, -1, -p.head_len * 0.4), (0, 0, 0), ("JAW",))
    jaw.cbox(0, p.jaw_h / 2.0, -p.jaw_len / 2.0 - 0.5, p.jaw_w, p.jaw_h, p.jaw_len, p.face_mat, decals=["teeth_up", "mouth"])
    for side in (-1, 1):
        s = "l" if side < 0 else "r"
        (a1, a2), hd = p.arm, p.hand
        a = m.part("arm_%s" % s, chest, (side * (cw / 2.0 + a1[1] / 2.0 - 1), -ch + 2.5, 0), (p.arm_pitch - p.body_pitch, 0, 0.12 * side), ("ARM", side, 0))
        a.cbox(0, a1[0] / 2.0 - 1, 0, a1[1], a1[0], a1[2], p.body_mat)
        f = m.part("forearm_%s" % s, a, (0, a1[0] - 1, 0), (-0.3, 0, 0), ("ARM", side, 1))
        f.cbox(0, a2[0] / 2.0, 0, a2[1], a2[0], a2[2], p.body_mat)
        f.cbox(0, a2[0] + hd[0] / 2.0 - 0.5, -0.5, hd[1], hd[0], hd[2], p.face_mat)
        leg_chain(m, "leg%s" % s, body, (side * (tw / 2.0 - p.leg[0][1] / 2.0), 0, 0), list(p.leg), p.leg_angles,
                  parent_pitch=p.body_pitch, phase=0.0 if side < 0 else 0.5, side=side, leg_index=0 if side < 0 else 1,
                  mat=p.body_mat, foot=(p.foot[0], p.foot[1], p.foot[2], p.face_mat))
    if p.tail_n:
        chain(m, "tail", body, (0, -1, td / 2.0), p.tail_n, p.tail_len, 3, 3, 1, 1, pitch0=0.6, pitch_step=-0.25, role="TAIL", mat=p.body_mat)
    if has(p, "brow"):
        head.cbox(0, -p.head_h + 1, -p.head_len + 1.5, p.head_w + 1, 2, 2, p.body_mat)
    if has(p, "crystals"):   # golems: crystal / rock spikes on shoulders and back
        for i, (x, z) in enumerate(((-cw / 2.0, -1), (cw / 2.0, -1), (-2, cd / 2.0), (2, cd / 2.0), (0, cd / 2.0 - 1))):
            c = m.part("crystal%d" % i, chest, (x, -ch, z), (0.3 if z > 0 else -0.2, 0, 0.5 if x > 0 else (-0.5 if x < 0 else 0)))
            c.cbox(0, -3, 0, 3, 6, 3, p.get("crystal_mat", "ice"))
    if has(p, "boulder_back"):
        chest.cbox(0, -ch * 0.6, cd / 2.0 + 1, cw - 2, ch * 0.8, 4, p.body_mat)
    if has(p, "armor"):    # megapithecus war armor plates
        chest.cbox(0, -ch / 2.0, -cd / 2.0 - 0.5, cw - 2, ch - 2, 2, "metal")
        for side in (-1, 1):
            pa = m.part("pauldron_%d" % side, chest, (side * cw / 2.0, -ch + 1, 0), (0, 0, 0.3 * side))
            pa.cbox(0, 0, 0, 6, 3, 7, "metal")
    if has(p, "kangaroo"):
        chain(m, "tail", body, (0, 0, td / 2.0), 4, 6, 5, 5, 2, 2, pitch0=1.0, pitch_step=0.15, role="TAIL", mat=p.body_mat)
        ears(m, head, p, p.head_w / 2.0 - 1.5, -p.head_h + 1, -1, length=5, w=2, splay=0.25)
    if has(p, "ears"):
        ears(m, head, p, p.head_w / 2.0, -p.head_h * 0.6, -p.head_len * 0.3, length=3, w=2, mat=p.face_mat, rx=0, splay=1.3)
    if p.saddle:
        saddle(m, chest, -ch / 2.0, cd / 2.0 + 1, cw - 2, 6)
    return p


# ============================================================================= CEPHALOPOD / JELLY / AMMONITE
CEPH = dict(kind="squid", body_y=10, mantle=(10, 10, 18), body_mat="skin", arm_n=8, arm_len=5, arm_segs=4,
            tentacle_len=7, tentacles=2, features=(), saddle=True)


def cephalopod(m, over):
    p = params(CEPH, over)
    G = GROUND
    mw, mh, ml = p.mantle
    body = m.part("body", None, (0, G - p.body_y, 0), (0, 0, 0), ("BODY",))
    if p.kind == "jelly":
        body.cbox(0, 0, 0, mw, mh, ml, p.body_mat)
        body.cbox(0, -mh / 2.0 - 1, 0, mw - 4, 3, ml - 4, p.body_mat)
        n = p.arm_n
        for i in range(n):
            a = 2 * PI * i / n
            x, z = math.cos(a) * mw * 0.35, math.sin(a) * ml * 0.35
            chain(m, "tent%d_" % i, body, (x, mh / 2.0, z), p.arm_segs, p.arm_len, 1, 1, 1, 1,
                  pitch0=0, pitch_step=0, role="TENTACLE", mat="glow", first_rot=(PI / 2, 0, 0))
        return p
    if p.kind == "ammonite":
        body.cbox(0, -2, 2, mw, mh, ml, "shell")
        body.cbox(0, -2, 2, mw - 2, mh + 2, ml - 4, "shell")
        hd = m.part("head", body, (0, 2, -ml / 2.0 + 1), (0, 0, 0), ("HEAD",))
        hd.cbox(0, 0, -2, mw - 4, mh - 4, 4, p.body_mat, decals=["eyes"])
        for i in range(p.arm_n):
            a = 2 * PI * i / p.arm_n
            chain(m, "arm%d_" % i, hd, (math.cos(a) * 2, math.sin(a) * 2, -4), p.arm_segs, p.arm_len, 1.5, 1.5, 1, 1,
                  forward=True, role="TENTACLE", mat=p.body_mat, pitch0=math.sin(a) * 0.3)
        return p
    # squid: mantle points backwards, head + arms forward
    body.cbox(0, 0, ml / 2.0, mw, mh, ml, p.body_mat, decals=["belly"])
    for side in (-1, 1):
        fin = m.part("mfin_%d" % side, body, (side * mw / 2.0, 0, ml * 0.8), (0, 0, 0), ("FIN", "PECTORAL", side))
        fin.cbox(side * 3, 0, 0, 6, 1, 8, p.body_mat)
    hd = m.part("head", body, (0, 0, 0), (0, 0, 0), ("HEAD",))
    hd.cbox(0, 0, -3, mw - 2, mh - 2, 6, p.body_mat, decals=["eyes_big"])
    for i in range(p.arm_n):
        a = 2 * PI * i / p.arm_n
        chain(m, "arm%d_" % i, hd, (math.cos(a) * (mw / 2.0 - 2), math.sin(a) * (mh / 2.0 - 2), -6), p.arm_segs, p.arm_len,
              2, 2, 1, 1, forward=True, role="TENTACLE", mat=p.body_mat, pitch0=math.sin(a) * 0.15)
    for k in range(p.tentacles):
        side = -1 if k == 0 else 1
        chain(m, "tentacle%d_" % k, hd, (side * 2, 1, -6), 5, p.tentacle_len, 2, 2, 3, 3, forward=True, role="TENTACLE",
              mat=p.body_mat)
    return p


# ============================================================================= OVERSEER / CONSTRUCT
def construct(m, over):
    p = params(dict(body_y=30, core=10, features=(), saddle=False), over)
    G = GROUND
    body = m.part("body", None, (0, G - p.body_y, 0), (0, 0, 0), ("CORE",))
    c = p.core
    body.cbox(0, 0, 0, c, c, c, "tek")
    body.cbox(0, 0, 0, c - 4, c + 4, c - 4, "glow")
    for i in range(3):
        r = m.part("ring%d" % i, body, (0, 0, 0), (i * PI / 3, i * 0.4, 0), ("RING", i))
        R = c + 6 + i * 5
        for k in range(8):
            a = 2 * PI * k / 8
            seg = m.part("ring%d_%d" % (i, k), r, (math.cos(a) * R, 0, math.sin(a) * R), (0, -a, 0))
            seg.cbox(0, 0, 0, 2, 2, R * 0.75, "tek")
    for k in range(4):
        a = 2 * PI * k / 4 + PI / 4
        arm = m.part("blade%d" % k, body, (math.cos(a) * c * 0.6, c * 0.5, math.sin(a) * c * 0.6), (0.4 * math.sin(a), 0, -0.4 * math.cos(a)), ("TENTACLE", k, 0, 1))
        arm.cbox(0, 8, 0, 3, 16, 3, "metal")
        arm.cbox(0, 17, 0, 2, 4, 2, "glow")
    return p


BUILDERS = {
    "theropod": theropod, "quadruped": quadruped, "flyer": flyer, "swimmer": swimmer, "arthropod": arthropod,
    "serpent": serpent, "primate": primate, "cephalopod": cephalopod, "construct": construct,
}
