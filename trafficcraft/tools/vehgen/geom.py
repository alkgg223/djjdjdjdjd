"""Core geometry helpers for the TrafficCraft procedural vehicle generator.

Model space: +Z forward, +Y up, +X = vehicle LEFT side. Units are metres (= blocks).
"""
import math
import numpy as np

# ---------------------------------------------------------------- materials
# Material ids understood by the Java renderer (VehicleMesh.Material).
PAINT, SOLID, GLASS, HEAD, HIGH, DRL, TAIL, BRAKE, IND_L, IND_R, REVERSE, INTERIOR_LIGHT, PLATE_LIGHT, \
    BEACON_A, BEACON_B, TAXI_SIGN, MARKER, PAINT2, SCREEN = range(19)
MAT_NAMES = ["paint", "solid", "glass", "head", "high", "drl", "tail", "brake", "ind_l", "ind_r", "reverse",
             "interior_light", "plate_light", "beacon_a", "beacon_b", "taxi_sign", "marker", "paint2", "screen"]

# UV regions of the shared 256x256 vehicle atlas (u0, v0, u1, v1) in 0..1
UV = {
    "white": (0 / 256, 0 / 256, 8 / 256, 8 / 256),
    "grille": (16 / 256, 0 / 256, 80 / 256, 32 / 256),
    "grille_fine": (80 / 256, 0 / 256, 144 / 256, 32 / 256),
    "plate": (144 / 256, 0 / 256, 208 / 256, 16 / 256),
    "led": (208 / 256, 0 / 256, 240 / 256, 16 / 256),
    "logo": (240 / 256, 0 / 256, 256 / 256, 16 / 256),
    "tread": (0 / 256, 32 / 256, 64 / 256, 48 / 256),
    "gauges": (64 / 256, 32 / 256, 128 / 256, 64 / 256),
    "headlamp": (128 / 256, 32 / 256, 160 / 256, 64 / 256),
    "taillamp": (160 / 256, 32 / 256, 192 / 256, 64 / 256),
    "fabric": (192 / 256, 32 / 256, 224 / 256, 64 / 256),
    "led_tail": (224 / 256, 32 / 256, 256 / 256, 48 / 256),
    "reflector": (224 / 256, 48 / 256, 256 / 256, 64 / 256),
    "rim_steel": (0 / 256, 64 / 256, 32 / 256, 96 / 256),
    "tyre_side": (32 / 256, 64 / 256, 64 / 256, 96 / 256),
    "cargo": (64 / 256, 64 / 256, 128 / 256, 96 / 256),
    "taxi": (128 / 256, 64 / 256, 192 / 256, 80 / 256),
    "stripe_amb": (128 / 256, 80 / 256, 192 / 256, 96 / 256),
    "police": (192 / 256, 64 / 256, 256 / 256, 80 / 256),
    "ambulance": (192 / 256, 80 / 256, 256 / 256, 96 / 256),
    "vent": (0 / 256, 96 / 256, 32 / 256, 112 / 256),
    "carpet": (32 / 256, 96 / 256, 64 / 256, 128 / 256),
    "bus_dest": (64 / 256, 96 / 256, 128 / 256, 112 / 256),
    "courier": (128 / 256, 96 / 256, 192 / 256, 112 / 256),
    "freight": (128 / 256, 112 / 256, 192 / 256, 128 / 256),
    "screen": (192 / 256, 96 / 256, 256 / 256, 128 / 256),
    "glass": (0 / 256, 128 / 256, 64 / 256, 192 / 256),
    "matcap": (64 / 256, 128 / 256, 128 / 256, 192 / 256),
    "rotor": (128 / 256, 128 / 256, 160 / 256, 160 / 256),
    "sidewall": (160 / 256, 128 / 256, 256 / 256, 144 / 256),
    "gear_plate": (160 / 256, 144 / 256, 192 / 256, 160 / 256),
    "buttons": (192 / 256, 144 / 256, 224 / 256, 160 / 256),
    "speaker": (224 / 256, 144 / 256, 240 / 256, 160 / 256),
    "knob": (240 / 256, 144 / 256, 256 / 256, 160 / 256),
    "leather": (128 / 256, 160 / 256, 192 / 256, 192 / 256),
    "alu": (192 / 256, 160 / 256, 256 / 256, 176 / 256),
    "wood": (192 / 256, 176 / 256, 256 / 256, 192 / 256),
    "cluster_digital": (0 / 256, 192 / 256, 64 / 256, 224 / 256),
    "hvac": (64 / 256, 192 / 256, 128 / 256, 208 / 256),
    "frit": (64 / 256, 208 / 256, 128 / 256, 224 / 256),
    "checker": (128 / 256, 224 / 256, 192 / 256, 232 / 256),
    "stripe_police": (128 / 256, 232 / 256, 192 / 256, 240 / 256),
}


def rgb(h):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def v3(x, y, z):
    return np.array([x, y, z], dtype=np.float64)


def normalize(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-12 else v


class Mesh:
    """Triangle soup grouped by (part, material)."""

    def __init__(self):
        self.groups = {}  # (part, mat) -> list of tris; tri = (p0,p1,p2, uv0,uv1,uv2, col, smooth)

    def tri(self, part, mat, p, uv, col, smooth=True):
        a, b, c = p
        if np.linalg.norm(np.cross(b - a, c - a)) < 1e-9:
            return
        self.groups.setdefault((part, mat), []).append((a, b, c, uv[0], uv[1], uv[2], col, smooth))

    def quad(self, part, mat, p0, p1, p2, p3, col, uvr=None, smooth=True, uvs=None):
        """p0..p3 counter-clockwise when seen from the visible side."""
        if uvs is None:
            u0, v0, u1, v1 = uvr if uvr else UV["white"]
            uvs = ((u0, v1), (u1, v1), (u1, v0), (u0, v0))
        self.tri(part, mat, (p0, p1, p2), (uvs[0], uvs[1], uvs[2]), col, smooth)
        self.tri(part, mat, (p0, p2, p3), (uvs[0], uvs[2], uvs[3]), col, smooth)

    def merge(self, other, xf=None):
        for k, tris in other.groups.items():
            for t in tris:
                if xf is None:
                    self.groups.setdefault(k, []).append(t)
                else:
                    self.groups.setdefault(k, []).append((xf(t[0]), xf(t[1]), xf(t[2])) + t[3:])

    def count(self):
        return sum(len(v) for v in self.groups.values())


# ---------------------------------------------------------------- interpolation
def pchip(points):
    """Monotone cubic interpolation through (x, y) keypoints. Returns f(x)."""
    pts = sorted(points)
    xs = np.array([p[0] for p in pts], dtype=float)
    ys = np.array([p[1] for p in pts], dtype=float)
    n = len(xs)
    if n == 1:
        return lambda x: float(ys[0])
    h = np.diff(xs)
    h[h == 0] = 1e-9
    d = np.diff(ys) / h
    m = np.zeros(n)
    m[0], m[-1] = d[0], d[-1]
    for i in range(1, n - 1):
        if d[i - 1] * d[i] <= 0:
            m[i] = 0
        else:
            w1 = 2 * h[i] + h[i - 1]
            w2 = h[i] + 2 * h[i - 1]
            m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])

    def f(x):
        if x <= xs[0]:
            return float(ys[0])
        if x >= xs[-1]:
            return float(ys[-1])
        i = int(np.searchsorted(xs, x) - 1)
        i = max(0, min(n - 2, i))
        t = (x - xs[i]) / h[i]
        h00 = 2 * t ** 3 - 3 * t ** 2 + 1
        h10 = t ** 3 - 2 * t ** 2 + t
        h01 = -2 * t ** 3 + 3 * t ** 2
        h11 = t ** 3 - t ** 2
        return float(h00 * ys[i] + h10 * h[i] * m[i] + h01 * ys[i + 1] + h11 * h[i] * m[i + 1])

    return f


def sexp(v, e):
    return math.copysign(abs(v) ** e, v)


# ---------------------------------------------------------------- primitives
def box(mesh, part, mat, c, size, col, uvr=None, rot=None):
    """Axis aligned box (optionally rotated by 3x3 matrix rot about its centre)."""
    cx, cy, cz = c
    sx, sy, sz = size[0] / 2, size[1] / 2, size[2] / 2
    corners = [v3(x, y, z) for x in (-sx, sx) for y in (-sy, sy) for z in (-sz, sz)]
    if rot is not None:
        corners = [rot @ p for p in corners]
    corners = [p + v3(cx, cy, cz) for p in corners]
    # index helper: x*4 + y*2 + z
    C = lambda x, y, z: corners[x * 4 + y * 2 + z]
    faces = [
        (C(1, 0, 0), C(1, 1, 0), C(1, 1, 1), C(1, 0, 1)),  # +x
        (C(0, 0, 1), C(0, 1, 1), C(0, 1, 0), C(0, 0, 0)),  # -x
        (C(0, 1, 0), C(0, 1, 1), C(1, 1, 1), C(1, 1, 0)),  # +y
        (C(0, 0, 0), C(1, 0, 0), C(1, 0, 1), C(0, 0, 1)),  # -y
        (C(0, 0, 1), C(1, 0, 1), C(1, 1, 1), C(0, 1, 1)),  # +z
        (C(1, 0, 0), C(0, 0, 0), C(0, 1, 0), C(1, 1, 0)),  # -z
    ]
    for f in faces:
        mesh.quad(part, mat, *f, col, uvr=uvr, smooth=False)


def rbox(mesh, part, mat, c, size, r, col, seg=1, uvr=None, rot=None, smooth=True):
    """Rounded box (edges rounded with radius r) built from a clamp-and-push lattice."""
    sx, sy, sz = size[0] / 2, size[1] / 2, size[2] / 2
    r = max(0.0, min(r, sx * 0.98, sy * 0.98, sz * 0.98))
    S = [sx, sy, sz]
    def coords(s):
        th = np.linspace(math.pi / 2, 0, seg + 1)
        a = [-(s - r) - r * math.sin(t) for t in th]
        b = [(s - r) + r * math.sin(t) for t in th[::-1]]
        return a + b
    inner = np.array([sx - r, sy - r, sz - r])
    cc = np.array(c, dtype=float)
    def mapc(q):
        cl = np.clip(q, -inner, inner)
        d = q - cl
        dn = np.linalg.norm(d)
        if dn < 1e-12:
            return q
        return cl + d / dn * r
    for axis in range(3):
        a1, a2 = [a for a in range(3) if a != axis]
        c1, c2 = coords(S[a1]), coords(S[a2])
        for sign in (-1, 1):
            grid = []
            for x1 in c1:
                row = []
                for x2 in c2:
                    p = np.zeros(3)
                    p[axis] = sign * S[axis]
                    p[a1] = x1
                    p[a2] = x2
                    q = mapc(p)
                    if rot is not None:
                        q = rot @ q
                    row.append(q + cc)
                grid.append(row)
            for i in range(len(c1) - 1):
                for j in range(len(c2) - 1):
                    p00, p10, p11, p01 = grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1]
                    nrm = np.cross(p10 - p00, p01 - p00)
                    ctr = (p00 + p11) / 2 - cc
                    if np.dot(nrm, ctr) < 0:
                        mesh.quad(part, mat, p00, p01, p11, p10, col, uvr=uvr, smooth=smooth)
                    else:
                        mesh.quad(part, mat, p00, p10, p11, p01, col, uvr=uvr, smooth=smooth)


def revolve(mesh, part, mat, profile, seg, col, axis='x', center=(0, 0, 0), uvr=None, smooth=True, a0=0.0, a1=2 * math.pi,
            uv_mode=None, cap=False):
    """Revolve a profile of (radius, axial) points around an axis. axis='x' -> wheel axis."""
    cx, cy, cz = center
    pts = []
    for k in range(seg + 1):
        a = a0 + (a1 - a0) * k / seg
        ca, sa = math.cos(a), math.sin(a)
        ring = []
        for (r, ax) in profile:
            if axis == 'x':
                ring.append(v3(cx + ax, cy + r * ca, cz + r * sa))
            elif axis == 'y':
                ring.append(v3(cx + r * ca, cy + ax, cz + r * sa))
            else:
                ring.append(v3(cx + r * ca, cy + r * sa, cz + ax))
        pts.append(ring)
    u0, v0, u1, v1 = uvr if uvr else UV["white"]
    for k in range(seg):
        for j in range(len(profile) - 1):
            p00, p01 = pts[k][j], pts[k][j + 1]
            p10, p11 = pts[k + 1][j], pts[k + 1][j + 1]
            if uv_mode == 'tread':
                uvs = ((u0 + (u1 - u0) * k / seg * 4 % (u1 - u0), v1), (u0 + (u1 - u0) * (k + 1) / seg * 4 % (u1 - u0) or u1, v1),
                       (u0 + (u1 - u0) * (k + 1) / seg * 4 % (u1 - u0) or u1, v0), (u0 + (u1 - u0) * k / seg * 4 % (u1 - u0), v0))
                uvs = ((u0, v1), (u1, v1), (u1, v0), (u0, v0))
            else:
                uvs = None
            mesh.quad(part, mat, p00, p10, p11, p01, col, uvr=uvr, smooth=smooth, uvs=uvs)


def disc(mesh, part, mat, center, normal, radius, seg, col, uvr=None, up=None):
    c = np.array(center, dtype=float)
    n = normalize(np.array(normal, dtype=float))
    a = normalize(np.cross(n, v3(0, 1, 0) if abs(n[1]) < 0.9 else v3(1, 0, 0)))
    b = np.cross(n, a)
    u0, v0, u1, v1 = uvr if uvr else UV["white"]
    um, vm = (u0 + u1) / 2, (v0 + v1) / 2
    for k in range(seg):
        t0 = 2 * math.pi * k / seg
        t1 = 2 * math.pi * (k + 1) / seg
        p0 = c + radius * (math.cos(t0) * a + math.sin(t0) * b)
        p1 = c + radius * (math.cos(t1) * a + math.sin(t1) * b)
        uv0 = (um + (u1 - u0) / 2 * math.cos(t0), vm + (v1 - v0) / 2 * math.sin(t0))
        uv1 = (um + (u1 - u0) / 2 * math.cos(t1), vm + (v1 - v0) / 2 * math.sin(t1))
        mesh.tri(part, mat, (c, p0, p1), ((um, vm), uv0, uv1), col, smooth=False)


def cylinder(mesh, part, mat, p0, p1, radius, seg, col, caps=True, uvr=None):
    p0 = np.array(p0, dtype=float)
    p1 = np.array(p1, dtype=float)
    ax = normalize(p1 - p0)
    a = normalize(np.cross(ax, v3(0, 1, 0) if abs(ax[1]) < 0.9 else v3(1, 0, 0)))
    b = np.cross(ax, a)
    ring0, ring1 = [], []
    for k in range(seg):
        t = 2 * math.pi * k / seg
        d = radius * (math.cos(t) * a + math.sin(t) * b)
        ring0.append(p0 + d)
        ring1.append(p1 + d)
    for k in range(seg):
        k2 = (k + 1) % seg
        mesh.quad(part, mat, ring0[k], ring0[k2], ring1[k2], ring1[k], col, uvr=uvr)
    if caps:
        disc(mesh, part, mat, p1, ax, radius, seg, col, uvr=uvr)
        disc(mesh, part, mat, p0, -ax, radius, seg, col, uvr=uvr)


def torus(mesh, part, mat, center, axis_normal, R, r, seg, seg2, col, arc=2 * math.pi):
    c = np.array(center, dtype=float)
    n = normalize(np.array(axis_normal, dtype=float))
    a = normalize(np.cross(n, v3(0, 1, 0) if abs(n[1]) < 0.9 else v3(1, 0, 0)))
    b = np.cross(n, a)
    grid = []
    for i in range(seg + 1):
        t = arc * i / seg - (arc - 2 * math.pi) / 2 if arc < 2 * math.pi else 2 * math.pi * i / seg
        d = math.cos(t) * a + math.sin(t) * b
        row = []
        for j in range(seg2 + 1):
            s = 2 * math.pi * j / seg2
            row.append(c + d * (R + r * math.cos(s)) + n * r * math.sin(s))
        grid.append(row)
    for i in range(seg):
        for j in range(seg2):
            mesh.quad(part, mat, grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1], col)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def bar(mesh, part, mat, p0, p1, w, h, col, up=(0, 1, 0)):
    """Rectangular bar from p0 to p1 with width w and height h."""
    p0 = np.array(p0, float)
    p1 = np.array(p1, float)
    ax = normalize(p1 - p0)
    upv = np.array(up, float)
    s = normalize(np.cross(ax, upv))
    if np.linalg.norm(s) < 1e-6:
        s = normalize(np.cross(ax, v3(1, 0, 0)))
    u = np.cross(s, ax)
    cs = [(-1, -1), (1, -1), (1, 1), (-1, 1)]
    r0 = [p0 + s * w / 2 * a + u * h / 2 * b for a, b in cs]
    r1 = [p1 + s * w / 2 * a + u * h / 2 * b for a, b in cs]
    for k in range(4):
        k2 = (k + 1) % 4
        mesh.quad(part, mat, r0[k], r0[k2], r1[k2], r1[k], col, smooth=False)
    mesh.quad(part, mat, r1[0], r1[1], r1[2], r1[3], col, smooth=False)
    mesh.quad(part, mat, r0[3], r0[2], r0[1], r0[0], col, smooth=False)


# ---------------------------------------------------------------- smoothing / export helpers
def compute_normals(tris, angle_deg=48.0):
    """Auto-smooth normals. tris: list of tri tuples. Returns list of 3 normals per tri."""
    cos_t = math.cos(math.radians(angle_deg))
    fn = []
    for t in tris:
        n = np.cross(t[1] - t[0], t[2] - t[0])
        fn.append(normalize(n))
    buckets = {}
    def key(p):
        return (round(p[0] * 2000), round(p[1] * 2000), round(p[2] * 2000))
    for i, t in enumerate(tris):
        if not t[7]:
            continue
        for j in range(3):
            buckets.setdefault(key(t[j]), []).append(i)
    out = []
    for i, t in enumerate(tris):
        if not t[7]:
            out.append((fn[i], fn[i], fn[i]))
            continue
        ns = []
        for j in range(3):
            acc = np.zeros(3)
            for k in buckets[key(t[j])]:
                d = np.dot(fn[k], fn[i])
                if d > cos_t:
                    acc += fn[k]
                elif d < -cos_t:
                    acc -= fn[k]  # flipped winding neighbour (double sided panels)
            ns.append(normalize(acc) if np.linalg.norm(acc) > 1e-9 else fn[i])
        out.append(tuple(ns))
    return out
