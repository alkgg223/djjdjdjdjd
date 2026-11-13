"""Surface maps for the 3D sky: planets and moons as equirectangular maps (sampled on the sphere, so they wrap and have
no pinched poles), a star's granulated surface, Saturn's rings, a flame for the fire round stars, solar panels, the
telescope eyepiece mask and the telescope block's textures."""
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'realweather', 'textures')
OUT = os.path.join(RES, 'environment', 'space3d')
BLOCK = os.path.join(RES, 'block')
os.makedirs(OUT, exist_ok=True)


def save(rgba, name, folder=OUT):
    Image.fromarray(np.clip(rgba * 255, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(folder, name))
    print('saved', name)


def sphere_grid(w, h):
    lon = (np.arange(w) + 0.5) / w * 2 * np.pi
    lat = np.pi / 2 - (np.arange(h) + 0.5) / h * np.pi
    lon, lat = np.meshgrid(lon, lat)
    x = np.cos(lat) * np.cos(lon)
    y = np.sin(lat)
    z = np.cos(lat) * np.sin(lon)
    return x, y, z, lat


def hash3(ix, iy, iz, seed):
    h = (ix.astype(np.uint64) * np.uint64(73856093)) ^ (iy.astype(np.uint64) * np.uint64(19349663)) \
        ^ (iz.astype(np.uint64) * np.uint64(83492791)) ^ np.uint64(seed * 2654435761 % (1 << 32))
    h = (h ^ (h >> np.uint64(13))) * np.uint64(1274126177)
    h = h ^ (h >> np.uint64(16))
    return (h & np.uint64(0xFFFFFF)).astype(np.float64) / float(0xFFFFFF)


def noise3(x, y, z, seed):
    with np.errstate(over='ignore'):
        ix, iy, iz = np.floor(x), np.floor(y), np.floor(z)
        fx, fy, fz = x - ix, y - iy, z - iz
        fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
        ix, iy, iz = ix.astype(np.int64) + 100000, iy.astype(np.int64) + 100000, iz.astype(np.int64) + 100000
        out = 0
        for dx in (0, 1):
            for dy in (0, 1):
                for dz in (0, 1):
                    wgt = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                    out = out + wgt * hash3(ix + dx, iy + dy, iz + dz, seed)
        return out


def fbm(x, y, z, scale, octaves, seed, gain=0.5):
    s, a, tot, f = 0.0, 1.0, 0.0, scale
    for o in range(octaves):
        s = s + a * noise3(x * f, y * f, z * f, seed + o * 31)
        tot += a
        a *= gain
        f *= 2.03
    return s / tot


def smooth(e0, e1, v):
    t = np.clip((v - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def ramp(v, stops):
    """Colour from a list of (position, (r, g, b)) stops."""
    v = np.clip(v, 0, 1)
    out = np.zeros(v.shape + (3,))
    for i in range(len(stops) - 1):
        p0, c0 = stops[i]
        p1, c1 = stops[i + 1]
        m = (v >= p0) & (v <= p1)
        t = ((v - p0) / max(1e-6, p1 - p0))[m]
        for k in range(3):
            out[..., k][m] = c0[k] + (c1[k] - c0[k]) * t
    return out


def rgba(rgb, a=None):
    if a is None:
        a = np.ones(rgb.shape[:2])
    return np.dstack([rgb[..., 0], rgb[..., 1], rgb[..., 2], a])


# ------------------------------------------------------------------ gas giants
def gas_giant(name, stops, seed, w=1024, h=512, bands=9.0, warp=0.55, storms=(), glow=None):
    x, y, z, lat = sphere_grid(w, h)
    wx = fbm(x, y, z, 2.0, 5, seed) - 0.5
    wy = fbm(x + 5.2, y, z, 2.0, 5, seed + 7) - 0.5
    wz = fbm(x, y + 3.1, z, 2.0, 5, seed + 13) - 0.5
    px, py, pz = x + wx * warp, y + wy * warp * 0.35, z + wz * warp
    # Eddies: a second, finer warp curls the edges of the bands into swirls.
    ex = fbm(px, py * 3.0, pz, 5.0, 4, seed + 51) - 0.5
    ez = fbm(px, py * 3.0 + 2.7, pz, 5.0, 4, seed + 52) - 0.5
    px, pz = px + ex * warp * 0.5, pz + ez * warp * 0.5
    py = py + (fbm(px, py, pz, 7.0, 3, seed + 53) - 0.5) * warp * 0.12
    # Zonal bands (latitude) of uneven widths, sheared and swirled by the turbulence.
    lat2 = np.arcsin(np.clip(py, -1, 1))
    band = 0.5 + 0.5 * np.sin(lat2 * bands + 2.6 * (fbm(px, py, pz, 1.2, 3, seed + 3) - 0.5) + 0.8 * np.sin(lat2 * bands * 0.37 + seed))
    fine = fbm(px * 1.0, py * 6.0, pz * 1.0, 3.0, 5, seed + 21)
    v = np.clip(band * 0.75 + fine * 0.35 - 0.05, 0, 1)
    rgb = ramp(v, stops)
    for (slon, slat, sw, sh, col) in storms:
        lon = np.arctan2(z, x)
        dl = np.angle(np.exp(1j * (lon - slon)))
        d = (dl / sw) ** 2 + ((lat - slat) / sh) ** 2
        ring = np.exp(-d * 1.6)
        for k in range(3):
            rgb[..., k] = rgb[..., k] * (1 - ring) + col[k] * ring
    if glow is not None:
        # Young, hot giants glow red from their own heat in the darker belts.
        heat = (1 - v) ** 2 * glow
        rgb[..., 0] = np.clip(rgb[..., 0] + heat * 0.35, 0, 1)
        rgb[..., 1] = np.clip(rgb[..., 1] + heat * 0.06, 0, 1)
    save(rgba(rgb), name + '.png')


# ------------------------------------------------------------------ rocky worlds
def rocky(name, seed, w=1024, h=512, ocean=None, sea_level=0.5, land=None, ice_lat=1.3, clouds=0.0, craters=0.0,
          cloud_col=(1, 1, 1), haze=None):
    x, y, z, lat = sphere_grid(w, h)
    e = fbm(x, y, z, 1.6, 7, seed)
    e2 = fbm(x, y, z, 6.0, 5, seed + 5)
    elev = e * 0.8 + e2 * 0.25
    rgb = ramp(np.clip((elev - sea_level) / (1 - sea_level) * 1.4, 0, 1), land)
    if ocean is not None:
        sea = elev < sea_level
        depth = np.clip((sea_level - elev) / 0.25, 0, 1)
        oc = ramp(depth, ocean)
        rgb[sea] = oc[sea]
    if craters > 0:
        cr = fbm(x, y, z, 9.0, 3, seed + 9)
        pits = smooth(0.62, 0.72, cr) * (1 - smooth(0.72, 0.8, cr))
        rim = smooth(0.58, 0.62, cr) * (1 - smooth(0.62, 0.66, cr))
        rgb = rgb * (1 - 0.35 * pits * craters)[..., None] + 0.2 * rim[..., None] * craters
    ice = smooth(ice_lat - 0.12, ice_lat + 0.05, np.abs(lat))
    rgb = rgb * (1 - ice[..., None]) + np.array([0.93, 0.95, 0.98]) * ice[..., None]
    if clouds > 0:
        wx = fbm(x, y, z, 2.5, 4, seed + 40) - 0.5
        c = fbm(x + wx, y * 1.6 + wx, z - wx, 3.0, 6, seed + 41)
        cov = smooth(0.55 - clouds * 0.25, 0.75 - clouds * 0.2, c)
        rgb = rgb * (1 - cov[..., None] * 0.92) + np.array(cloud_col) * cov[..., None] * 0.92
    if haze is not None:
        rgb = rgb * (1 - haze[1]) + np.array(haze[0]) * haze[1]
    save(rgba(np.clip(rgb, 0, 1)), name + '.png')


def moon_map():
    x, y, z, lat = sphere_grid(1024, 512)
    base = fbm(x, y, z, 2.0, 6, 501)
    maria = smooth(0.55, 0.62, fbm(x, y, z, 1.1, 4, 502))
    g = 0.62 + 0.25 * (base - 0.5) - 0.28 * maria
    cr = fbm(x, y, z, 12.0, 3, 503)
    pits = smooth(0.66, 0.74, cr) * (1 - smooth(0.74, 0.8, cr))
    rims = smooth(0.62, 0.66, cr) * (1 - smooth(0.66, 0.69, cr))
    g = g - 0.18 * pits + 0.12 * rims
    big = fbm(x, y, z, 4.0, 2, 504)
    g = g - 0.12 * smooth(0.7, 0.76, big) * (1 - smooth(0.76, 0.8, big)) + 0.1 * smooth(0.66, 0.7, big) * (1 - smooth(0.7, 0.72, big))
    rgb = np.dstack([g * 0.98, g * 0.97, g * 0.95])
    save(rgba(np.clip(rgb, 0, 1)), 'moon.png')


def star_surface():
    """Granulation (bright cells in dark lanes), a few sunspots; grey, tinted by the star's colour in game."""
    x, y, z, lat = sphere_grid(1024, 512)
    g = fbm(x, y, z, 22.0, 3, 601)
    cells = smooth(0.35, 0.7, g)
    big = fbm(x, y, z, 5.0, 4, 602)
    spots = smooth(0.72, 0.78, fbm(x, y, z, 3.0, 4, 603)) * (np.abs(lat) < 0.7)
    v = 0.72 + 0.22 * cells + 0.1 * (big - 0.5)
    v = v * (1 - 0.65 * spots)
    save(rgba(np.dstack([v, v, v])), 'star_surface.png')


def rings():
    w = 1024
    r = np.linspace(0, 1, w)
    rr = np.random.default_rng(77)
    fine = np.convolve(rr.random(w), np.ones(5) / 5, mode='same')
    a = 0.55 + 0.35 * fine
    a *= smooth(0.0, 0.04, r) * (1 - smooth(0.96, 1.0, r))
    a *= 1 - 0.9 * (smooth(0.56, 0.58, r) * (1 - smooth(0.62, 0.64, r)))  # Cassini division
    a *= 1 - 0.5 * (smooth(0.86, 0.87, r) * (1 - smooth(0.88, 0.89, r)))
    a *= np.where(r < 0.2, 0.35, 1.0)
    col = np.stack([0.86 + 0.08 * fine, 0.78 + 0.06 * fine, 0.62 + 0.05 * fine], -1)
    img = np.zeros((8, w, 4))
    img[:, :, :3] = col
    img[:, :, 3] = a
    save(img, 'rings.png')


def flame():
    """A soft, ragged flame blob for the fire round stars, fireballs and meteor trains."""
    n = 64
    yy, xx = np.mgrid[0:n, 0:n] / (n - 1) * 2 - 1
    r = np.hypot(xx, yy)
    ang = np.arctan2(yy, xx)
    rr = np.random.default_rng(81)
    ragged = 0.75 + 0.15 * np.sin(ang * 5 + rr.random() * 6) + 0.1 * np.sin(ang * 11 + rr.random() * 6)
    a = np.clip(1 - r / ragged, 0, 1) ** 1.6
    save(np.dstack([np.ones_like(a), np.ones_like(a), np.ones_like(a), a]), 'flame.png')


def line():
    """A soft line: bright along the middle, fading to the edges (loops, filaments)."""
    w, h = 16, 32
    yy = (np.arange(h) + 0.5) / h * 2 - 1
    a = np.exp(-(yy / 0.35) ** 2)[:, None] * np.ones((1, w))
    save(np.dstack([np.ones_like(a)] * 3 + [a]), 'line.png')


def panel():
    n = 64
    img = np.zeros((n, n, 4))
    img[..., 0], img[..., 1], img[..., 2], img[..., 3] = 0.12, 0.17, 0.36, 1
    for k in range(0, n, 8):
        img[k, :, :3] = (0.6, 0.5, 0.25)
        img[:, k, :3] = (0.6, 0.5, 0.25)
    img[:, n // 2, :3] = (0.75, 0.62, 0.3)
    save(img, 'panel.png')


def metal():
    n = 32
    v = 0.85 + 0.1 * np.random.default_rng(5).random((n, n))
    save(np.dstack([v, v, v * 0.98, np.ones_like(v)]), 'metal.png')


def eyepiece():
    """The telescope eyepiece: black all round a round field of view with a soft edge."""
    n = 512
    yy, xx = np.mgrid[0:n, 0:n] / (n - 1) * 2 - 1
    r = np.hypot(xx, yy)
    a = smooth(0.9, 0.97, r)
    save(np.dstack([np.zeros_like(a)] * 3 + [a]), 'eyepiece.png')


def block_textures():
    rr = np.random.default_rng(9)

    def tex(base, var, name, stripes=False):
        v = rr.random((16, 16)) * var
        img = np.zeros((16, 16, 4))
        for k in range(3):
            img[..., k] = base[k] + v - var / 2
        if stripes:
            img[::4, :, :3] *= 0.8
        img[..., 3] = 1
        save(np.clip(img, 0, 1), name, BLOCK)

    tex((0.1, 0.12, 0.18), 0.05, 'telescope_tube.png')
    tex((0.78, 0.6, 0.25), 0.12, 'telescope_brass.png')
    tex((0.45, 0.3, 0.17), 0.1, 'telescope_wood.png', stripes=True)
    tex((0.06, 0.06, 0.07), 0.04, 'telescope_lens.png')


if __name__ == '__main__':
    gas_giant('jupiter', [(0, (0.5, 0.34, 0.24)), (0.35, (0.78, 0.62, 0.45)), (0.6, (0.93, 0.87, 0.76)), (1, (0.98, 0.95, 0.9))], 11,
              storms=[(1.0, -0.38, 0.28, 0.12, (0.75, 0.38, 0.26))])
    gas_giant('saturn', [(0, (0.72, 0.62, 0.42)), (0.5, (0.88, 0.8, 0.6)), (1, (0.96, 0.92, 0.78))], 12, bands=14, warp=0.3)
    gas_giant('uranus', [(0, (0.55, 0.8, 0.84)), (1, (0.7, 0.9, 0.92))], 13, bands=6, warp=0.15, w=512, h=256)
    gas_giant('neptune', [(0, (0.12, 0.25, 0.7)), (0.6, (0.25, 0.42, 0.88)), (1, (0.6, 0.72, 0.98))], 14, bands=7, warp=0.35,
              storms=[(2.0, -0.35, 0.22, 0.1, (0.08, 0.12, 0.4))], w=512, h=256)
    gas_giant('betapic_b', [(0, (0.4, 0.22, 0.14)), (0.45, (0.72, 0.45, 0.25)), (0.8, (0.9, 0.7, 0.45)), (1, (0.96, 0.85, 0.7))], 15,
              warp=0.7, storms=[(0.5, 0.3, 0.2, 0.08, (0.95, 0.8, 0.6))])
    gas_giant('hr8799b', [(0, (0.35, 0.08, 0.1)), (0.5, (0.75, 0.3, 0.22)), (1, (0.98, 0.7, 0.5))], 16, warp=0.8, glow=0.9)
    gas_giant('hr8799c', [(0, (0.3, 0.1, 0.25)), (0.5, (0.7, 0.35, 0.45)), (1, (0.95, 0.72, 0.7))], 17, warp=0.7, glow=0.7)
    gas_giant('hr8799d', [(0, (0.45, 0.2, 0.08)), (0.5, (0.85, 0.5, 0.2)), (1, (1.0, 0.85, 0.55))], 18, warp=0.9, glow=0.8,
              storms=[(3.0, 0.2, 0.25, 0.1, (1.0, 0.92, 0.7))])
    gas_giant('hr8799e', [(0, (0.4, 0.12, 0.08)), (0.4, (0.8, 0.38, 0.25)), (0.75, (0.95, 0.75, 0.6)), (1, (1, 0.92, 0.85))], 19, warp=0.6,
              glow=0.85)
    gas_giant('psr_b', [(0, (0.55, 0.55, 0.6)), (0.5, (0.75, 0.75, 0.8)), (1, (0.9, 0.9, 0.93))], 20, warp=0.4, w=512, h=256)
    gas_giant('gj436b', [(0, (0.35, 0.55, 0.85)), (0.6, (0.6, 0.78, 0.95)), (1, (0.88, 0.94, 1.0))], 21, bands=5, warp=0.3)
    gas_giant('k2_18b', [(0, (0.06, 0.3, 0.45)), (0.5, (0.12, 0.5, 0.6)), (1, (0.4, 0.75, 0.78))], 22, bands=6, warp=0.6)
    rocky('mercury', 31, w=512, h=256, land=[(0, (0.42, 0.4, 0.38)), (1, (0.7, 0.68, 0.64))], sea_level=0.0, craters=1.0, ice_lat=9)
    rocky('venus', 32, w=512, h=256, land=[(0, (0.8, 0.68, 0.45)), (1, (0.95, 0.88, 0.7))], sea_level=0.0, clouds=0.9,
          cloud_col=(0.96, 0.9, 0.72), ice_lat=9)
    rocky('mars', 33, w=512, h=256, land=[(0, (0.45, 0.2, 0.12)), (0.5, (0.72, 0.36, 0.2)), (1, (0.85, 0.55, 0.35))], sea_level=0.0,
          ice_lat=1.32, craters=0.4)
    rocky('toi700d', 34, ocean=[(0, (0.1, 0.3, 0.55)), (1, (0.02, 0.08, 0.25))], sea_level=0.52,
          land=[(0, (0.3, 0.42, 0.2)), (0.4, (0.5, 0.45, 0.28)), (0.8, (0.62, 0.55, 0.45)), (1, (0.9, 0.9, 0.9))], ice_lat=1.3, clouds=0.35, haze=((0.45, 0.62, 0.9), 0.08))
    rocky('toi700e', 35, ocean=[(0, (0.15, 0.32, 0.45)), (1, (0.05, 0.12, 0.25))], sea_level=0.45,
          land=[(0, (0.55, 0.4, 0.25)), (0.5, (0.7, 0.55, 0.35)), (1, (0.85, 0.78, 0.65))], ice_lat=1.35, clouds=0.25, haze=((0.7, 0.65, 0.6), 0.06))
    rocky('lhs1140b', 36, ocean=[(0, (0.25, 0.5, 0.7)), (1, (0.08, 0.2, 0.4))], sea_level=0.55,
          land=[(0, (0.6, 0.65, 0.7)), (1, (0.92, 0.95, 0.98))], ice_lat=0.95, clouds=0.35, cloud_col=(0.95, 0.97, 1.0))
    moon_map()
    star_surface()
    rings()
    flame()
    line()
    panel()
    metal()
    eyepiece()
    block_textures()
