"""Planet blocks (16 x 16 pixel textures, and the 4 x 4 atlas the ray-traced planets are built from), the planet map
block, and the Earth seen from space: continents, oceans, ice, clouds and city lights, as equirectangular maps."""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from gen_space3d import sphere_grid, fbm, smooth

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'realweather', 'textures')
BLOCK = os.path.join(RES, 'block')
SPACE = os.path.join(RES, 'environment', 'space3d')
rng = np.random.default_rng(1977)


def save_rgb(a, path):
    Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8)).save(path)
    print('saved', os.path.basename(path))


def lum(a):
    return a[..., 0] * 0.3 + a[..., 1] * 0.59 + a[..., 2] * 0.11


def tile_noise(seed, scale=4.0, octaves=3):
    """Seamless 16 x 16 noise (on a torus)."""
    y, x = np.mgrid[0:16, 0:16] / 16.0
    a, b = x * 2 * np.pi, y * 2 * np.pi
    return fbm(np.cos(a) * scale / 6.28, np.sin(a) * scale / 6.28 + np.cos(b) * scale / 6.28, np.sin(b) * scale / 6.28, 1.0, octaves, seed)


def colorize(v, c0, c1):
    v = v[..., None]
    return np.array(c0) * (1 - v) + np.array(c1) * v


def speckle(img, density, color, seed, size=1):
    r = np.random.default_rng(seed)
    out = img.copy()
    n = int(256 * density)
    for _ in range(n):
        x, y = r.integers(0, 16), r.integers(0, 16)
        for dx in range(size):
            for dy in range(size):
                out[(y + dy) % 16, (x + dx) % 16] = color
    return out


def edges(img, k=0.08):
    """A faint darker rim so blocks read as blocks from afar."""
    out = img.copy()
    out[0, :] *= 1 - k
    out[-1, :] *= 1 - k * 1.5
    out[:, 0] *= 1 - k
    out[:, -1] *= 1 - k * 1.5
    return out


def regolith():
    n = tile_noise(11, 5)
    img = colorize(smooth(0.3, 0.75, n), (0.42, 0.41, 0.4), (0.66, 0.65, 0.63))
    img = speckle(img, 0.12, (0.3, 0.3, 0.3), 1)
    img = speckle(img, 0.06, (0.78, 0.77, 0.75), 2)
    # A little crater.
    for (x, y) in [(10, 4), (11, 4), (9, 5), (12, 5), (9, 6), (12, 6), (10, 7), (11, 7)]:
        img[y, x] = (0.36, 0.35, 0.34)
    img[5:7, 10:12] = (0.5, 0.49, 0.48)
    return edges(img)


def basalt():
    n = tile_noise(12, 4)
    img = colorize(smooth(0.25, 0.8, n), (0.13, 0.13, 0.14), (0.3, 0.3, 0.32))
    for x in (3, 8, 13):
        img[:, x] *= 0.6
    for y in (5, 11):
        img[y, 0:3] *= 0.6
        img[y, 9:13] *= 0.6
    img = speckle(img, 0.05, (0.38, 0.37, 0.4), 3)
    return edges(img)


def sand():
    n = tile_noise(13, 6)
    y = np.mgrid[0:16, 0:16][0]
    ripple = 0.5 + 0.5 * np.sin((y + n * 3) * 1.2)
    img = colorize(smooth(0.2, 0.9, n * 0.6 + ripple * 0.4), (0.74, 0.62, 0.42), (0.92, 0.84, 0.64))
    img = speckle(img, 0.08, (0.62, 0.5, 0.33), 4)
    return edges(img, 0.05)


def ice():
    n = tile_noise(14, 3)
    img = colorize(smooth(0.2, 0.8, n), (0.55, 0.75, 0.95), (0.82, 0.93, 1.0))
    crack = [(2, 3), (3, 4), (4, 4), (5, 5), (6, 7), (7, 8), (8, 8), (9, 9), (10, 11), (11, 12), (12, 12), (13, 14),
             (6, 2), (7, 3), (7, 4), (9, 10), (8, 11), (7, 12), (14, 4), (13, 5), (12, 6)]
    for (x, y) in crack:
        img[y, x] = (0.95, 0.98, 1.0)
    img = speckle(img, 0.03, (0.45, 0.62, 0.86), 5)
    return edges(img, 0.06)


def frost():
    n = tile_noise(15, 5)
    img = colorize(smooth(0.2, 0.9, n), (0.82, 0.88, 0.95), (0.97, 0.98, 1.0))
    img = speckle(img, 0.05, (1.0, 1.0, 1.0), 6)
    img = speckle(img, 0.05, (0.72, 0.8, 0.9), 7)
    return edges(img, 0.05)


def magma():
    n = tile_noise(16, 4)
    img = colorize(smooth(0.3, 0.8, n), (0.12, 0.06, 0.05), (0.25, 0.12, 0.08))
    v = tile_noise(17, 3)
    veins = np.abs(v - 0.5) < 0.06
    img[veins] = (1.0, 0.55, 0.12)
    hot = np.abs(v - 0.5) < 0.025
    img[hot] = (1.0, 0.9, 0.45)
    return edges(img, 0.05)


def cloud():
    n = tile_noise(18, 3, 4)
    img = colorize(smooth(0.25, 0.85, n), (0.78, 0.74, 0.68), (0.97, 0.95, 0.9))
    return img


def water():
    y, x = np.mgrid[0:16, 0:16]
    n = tile_noise(19, 4)
    w = 0.5 + 0.5 * np.sin((x + y * 0.5 + n * 4) * 0.8)
    img = colorize(smooth(0.1, 0.95, w * 0.7 + n * 0.3), (0.1, 0.25, 0.55), (0.25, 0.5, 0.8))
    img = speckle(img, 0.03, (0.6, 0.78, 0.95), 8)
    return img


def moss():
    n = tile_noise(20, 6)
    img = colorize(smooth(0.25, 0.8, n), (0.2, 0.4, 0.16), (0.42, 0.66, 0.28))
    img = speckle(img, 0.1, (0.55, 0.78, 0.35), 9)
    img = speckle(img, 0.08, (0.14, 0.3, 0.12), 10)
    return edges(img, 0.06)


def crystal():
    y, x = np.mgrid[0:16, 0:16]
    facet = ((x + y) // 5 + (x - y + 16) // 6) % 3
    img = np.zeros((16, 16, 3))
    cols = [(0.62, 0.7, 0.95), (0.78, 0.84, 1.0), (0.52, 0.56, 0.86)]
    for k in range(3):
        img[facet == k] = cols[k]
    img = speckle(img, 0.04, (1.0, 1.0, 1.0), 11)
    return edges(img, 0.1)


def sulfur():
    n = tile_noise(21, 5)
    img = colorize(smooth(0.25, 0.8, n), (0.75, 0.62, 0.12), (0.96, 0.88, 0.35))
    img = speckle(img, 0.08, (0.7, 0.42, 0.1), 12, 2)
    img = speckle(img, 0.04, (0.45, 0.3, 0.12), 13)
    return edges(img)


def ironstone():
    n = tile_noise(22, 4)
    y, x = np.mgrid[0:16, 0:16]
    streak = 0.5 + 0.5 * np.sin(y * 0.9 + n * 5)
    img = colorize(smooth(0.2, 0.85, n * 0.6 + streak * 0.4), (0.42, 0.18, 0.1), (0.72, 0.36, 0.2))
    img = speckle(img, 0.06, (0.28, 0.12, 0.08), 14)
    return edges(img)


def storm():
    y, x = np.mgrid[0:16, 0:16]
    n = tile_noise(23, 3)
    bands = 0.5 + 0.5 * np.sin(y * 0.8 + n * 6)
    img = colorize(smooth(0.1, 0.9, bands), (0.6, 0.44, 0.3), (0.92, 0.82, 0.66))
    return img


def obsidian():
    n = tile_noise(24, 4)
    img = colorize(smooth(0.3, 0.9, n), (0.05, 0.03, 0.08), (0.16, 0.09, 0.22))
    img = speckle(img, 0.05, (0.45, 0.3, 0.6), 15)
    return edges(img, 0.04)


def hotrock():
    n = tile_noise(25, 4)
    img = colorize(smooth(0.25, 0.85, n), (0.25, 0.1, 0.06), (0.45, 0.2, 0.1))
    v = tile_noise(26, 2)
    img[np.abs(v - 0.5) < 0.05] = (0.95, 0.42, 0.1)
    return edges(img)


def ash():
    n = tile_noise(27, 6)
    img = colorize(smooth(0.25, 0.8, n), (0.32, 0.31, 0.3), (0.52, 0.5, 0.48))
    img = speckle(img, 0.1, (0.22, 0.21, 0.2), 16)
    return edges(img, 0.05)


TILES = [('planet_regolith', regolith), ('planet_basalt', basalt), ('planet_sand', sand), ('planet_ice', ice), ('planet_frost', frost),
         ('planet_magma', magma), ('planet_cloud', cloud), (None, water), ('planet_moss', moss), ('planet_crystal', crystal),
         ('planet_sulfur', sulfur), ('planet_ironstone', ironstone), ('planet_storm_cloud', storm), ('planet_obsidian', obsidian),
         ('planet_hot_rock', hotrock), ('planet_ash', ash)]


def blocks():
    atlas = np.zeros((64, 64, 3))
    for i, (name, fn) in enumerate(TILES):
        img = np.clip(fn(), 0, 1)
        if name:
            save_rgb(img, os.path.join(BLOCK, name + '.png'))
        # In the atlas each tile is brought to the same mean brightness (the planet's own map gives the colour).
        k = 0.62 / max(lum(img).mean(), 0.05)
        norm = np.clip(img * k, 0, 1)
        if name == 'planet_magma' or name == 'planet_hot_rock':
            norm = np.clip(img * 1.6, 0, 1)
        r, c = divmod(i, 4)
        atlas[r * 16:(r + 1) * 16, c * 16:(c + 1) * 16] = norm
    save_rgb(atlas, os.path.join(SPACE, 'planet_blocks.png'))


def planet_map_block():
    n = tile_noise(30, 4)
    base = colorize(smooth(0.3, 0.8, n), (0.16, 0.17, 0.2), (0.26, 0.27, 0.31))
    base[0, :] = base[-1, :] = (0.75, 0.6, 0.3)
    base[:, 0] = base[:, -1] = (0.6, 0.48, 0.24)
    for x in range(3, 13, 3):
        base[7:9, x] = (0.3, 0.75, 1.0)
    save_rgb(base, os.path.join(BLOCK, 'planet_map_base.png'))
    brass = colorize(smooth(0.2, 0.9, tile_noise(31, 3)), (0.55, 0.42, 0.18), (0.88, 0.72, 0.38))
    save_rgb(brass, os.path.join(BLOCK, 'planet_map_brass.png'))
    # The globe: a little Earth in pixels, oceans and continents.
    y, x = np.mgrid[0:16, 0:16]
    land = tile_noise(32, 3) > 0.52
    globe = np.zeros((16, 16, 3))
    globe[:] = (0.12, 0.35, 0.75)
    globe[land] = (0.3, 0.62, 0.26)
    globe[(tile_noise(33, 5) > 0.66) & land] = (0.7, 0.62, 0.38)
    globe[(y <= 1) | (y >= 14)] = (0.92, 0.95, 1.0)
    clouds = tile_noise(34, 4) > 0.64
    globe[clouds] = globe[clouds] * 0.4 + 0.6
    save_rgb(globe, os.path.join(BLOCK, 'planet_map_globe.png'))


# ------------------------------------------------------------------------------------------ the Earth

LAND = {
    'north_america': [(-168, 66), (-162, 70), (-150, 71), (-140, 70), (-128, 70), (-115, 73), (-100, 72), (-90, 70), (-82, 66), (-80, 63),
                      (-88, 58), (-80, 54), (-77, 59), (-70, 61), (-64, 59), (-60, 55), (-56, 51), (-60, 47), (-65, 45), (-70, 42),
                      (-75, 39), (-76, 35), (-81, 31), (-80, 26), (-81, 25), (-83, 29), (-86, 30), (-90, 29), (-95, 29), (-97, 26),
                      (-97, 21), (-94, 18), (-90, 19), (-87, 21), (-88, 16), (-84, 15), (-83, 10), (-79, 9), (-77, 8),
                      (-80, 7), (-83, 8), (-86, 11), (-91, 14), (-96, 16), (-102, 18), (-105, 20), (-106, 23), (-109, 23), (-112, 28),
                      (-110, 24), (-112, 26), (-114, 30), (-117, 32), (-120, 34), (-123, 38), (-124, 42), (-124, 47), (-126, 50),
                      (-131, 54), (-135, 58), (-140, 60), (-147, 61), (-152, 59), (-157, 57), (-162, 55), (-160, 58), (-165, 61),
                      (-168, 66)],
    'greenland': [(-73, 78), (-62, 82), (-40, 83.5), (-22, 82), (-18, 77), (-20, 72), (-24, 69), (-32, 66), (-40, 64), (-43, 60), (-48, 61),
                  (-52, 65), (-54, 69), (-58, 75), (-66, 77), (-73, 78)],
    'arctic_islands': [(-122, 71), (-105, 70), (-90, 70), (-80, 72), (-70, 74), (-78, 79), (-90, 81), (-110, 78), (-122, 75), (-122, 71)],
    'south_america': [(-77, 8), (-72, 12), (-66, 11), (-60, 10), (-52, 5), (-50, 1), (-45, -2), (-38, -4), (-35, -7), (-37, -12), (-39, -18),
                      (-41, -22), (-46, -24), (-48, -28), (-53, -33), (-57, -36), (-58, -39), (-62, -40), (-65, -42), (-66, -47),
                      (-68, -51), (-70, -54), (-73, -53), (-75, -48), (-74, -42), (-73, -37), (-71, -30), (-70, -22), (-70, -18),
                      (-75, -15), (-77, -12), (-80, -7), (-81, -4), (-80, -1), (-79, 2), (-77, 4), (-77, 8)],
    'eurasia': [(-10, 36), (-9, 39), (-9, 43), (-2, 43.5), (-1, 46), (-4, 48), (2, 51), (4, 52), (8, 54), (8, 57), (10, 58), (5, 58),
                (5, 62), (10, 64), (14, 68), (20, 70), (28, 71), (35, 69), (41, 67), (44, 68), (55, 69), (60, 69), (66, 70), (70, 73),
                (80, 73), (90, 76), (100, 77), (113, 74), (128, 73), (140, 72), (150, 71), (160, 70), (170, 70), (180, 69), (180, 65),
                (172, 64), (170, 60), (163, 58), (161, 54), (156, 51), (156, 57), (152, 59), (143, 59), (138, 55), (141, 52), (141, 48),
                (138, 44), (133, 43), (130, 42), (129, 37), (126, 35), (126, 38), (125, 40), (121, 40), (119, 39), (121, 36), (120, 33),
                (122, 30), (121, 28), (119, 25), (116, 23), (111, 21), (108, 21), (106, 18), (109, 12), (106, 9), (104, 9), (101, 13),
                (99, 9), (101, 6), (104, 1.5), (101, 3), (98, 8), (98, 13), (97, 17), (94, 17), (93, 20), (91, 22), (88, 21.5), (86, 20),
                (82, 16), (80, 13), (79, 8), (77, 8), (75, 12), (73, 17), (72.5, 21), (69, 22), (67, 24.5), (62, 25), (57, 25.5),
                (56, 24), (59, 22.5), (58, 20), (55, 17), (52, 16), (45, 13), (43, 13), (42, 16), (39, 21), (37, 25), (35, 28),
                (34.5, 31.5), (35.5, 34), (36, 36.5), (32, 36.5), (28, 36.7), (27, 38), (26, 40), (23, 40.5), (23.5, 38), (22, 37),
                (21, 38.5), (20, 40), (19, 42), (16, 43.5), (13.6, 45.5), (12.3, 44.5), (14, 42), (16, 41.5), (18.5, 40), (16.5, 38.5),
                (15.7, 38), (15.6, 40), (14, 41), (12, 42), (10.5, 43.5), (8, 44), (6, 43), (3, 43.3), (0, 39.5), (-1, 37.5), (-5.5, 36),
                (-10, 36)],
    'africa': [(-17, 21), (-16, 25), (-13, 28), (-10, 30), (-9.5, 33), (-6, 35.8), (3, 36.8), (10, 37.2), (11, 33.5), (15, 32), (20, 31),
               (25, 31.7), (30, 31.4), (32.5, 31), (34, 28), (36, 24), (38, 18), (40, 15), (43, 12), (47, 11), (51, 12), (51, 10),
               (48, 4.5), (44, 0), (41, -2), (40, -6), (39.5, -10), (40.5, -15), (37, -18), (35, -22), (35.5, -24), (33, -26),
               (32.5, -29), (30, -31), (27, -33.5), (22, -34.5), (18.5, -34), (18, -31), (16.5, -28.5), (15, -26), (14.5, -22),
               (12, -17), (13.5, -11), (13, -8), (12, -5), (9, -1), (9.5, 2), (9, 4), (6, 4.3), (3, 6.3), (-1, 5), (-4, 5),
               (-7.5, 4.3), (-10, 6), (-13, 8), (-15, 11), (-17, 14.5), (-16.5, 17), (-16, 20), (-17, 21)],
    'madagascar': [(43.5, -25), (47, -25.5), (49.5, -17), (50.3, -15.5), (49.3, -12), (47, -14), (44, -17), (43.5, -25)],
    'arabia_red_sea_fix': [],
    'australia': [(114, -22), (113.5, -26), (115, -34), (118, -35), (123, -34), (127, -32.5), (131, -31.5), (135, -34.5), (138, -35.5),
                  (140, -38), (144, -38.5), (146, -39), (150, -37.5), (152.5, -32.5), (153.5, -28), (153, -25), (150, -22), (146, -19),
                  (145.5, -15), (143.5, -14), (142.5, -10.7), (141.5, -13), (141.5, -17), (139.5, -17.5), (137, -16), (136.5, -12),
                  (132.5, -11.5), (130, -13), (129.5, -15), (127, -14), (125, -15), (122.5, -17), (121, -19.5), (117, -20.7),
                  (114, -22)],
    'new_guinea': [(131, -1.5), (134, -1), (138, -1.7), (141, -2.6), (145, -4), (147.5, -6), (150, -10.5), (147, -10), (143.5, -9),
                   (141, -9), (138, -8.3), (135, -4.5), (132, -3), (131, -1.5)],
    'borneo': [(109, 1.8), (109.6, -1), (111, -3), (114, -3.8), (116.5, -3.5), (118.8, 1), (117.7, 4.5), (117, 7), (116, 6.5), (113.5, 3.5),
               (111, 2.3), (109, 1.8)],
    'sumatra': [(95.2, 5.6), (97.5, 5.2), (100.5, 2), (103.8, -1.5), (106, -3), (106, -6), (104.5, -5.8), (102, -4), (100, -1.5),
                (97.5, 2), (95.2, 5.6)],
    'java': [(105.2, -6.7), (108, -6.3), (111, -6.5), (114.5, -7.5), (114.5, -8.7), (110, -8.2), (106, -7.4), (105.2, -6.7)],
    'sulawesi': [(119.5, -5.5), (120.5, -2), (121.5, 1), (125, 1.5), (123, 0.5), (121.5, -1), (123, -4), (121, -5), (119.5, -5.5)],
    'philippines': [(120, 18.5), (122.5, 18.5), (122, 14), (124, 12.5), (126, 9.5), (125.5, 6), (123, 7), (122, 10), (121, 12.5),
                    (120, 15), (120, 18.5)],
    'japan': [(130, 31.2), (131.5, 33.8), (133.5, 34.5), (135.5, 34), (137, 34.6), (139.8, 35.1), (140.9, 37), (141.5, 39.5), (140.9, 41.3),
              (140, 41.4), (140.2, 43), (141.5, 45.4), (145.4, 44.2), (143, 42), (141.9, 42.6), (141.2, 41.3), (142, 39.5), (140.9, 36),
              (139.2, 35), (136.8, 34.2), (135.1, 33.6), (133, 33), (131.7, 31.2), (130, 31.2)],
    'uk': [(-5.7, 50), (1.4, 51.2), (1.7, 52.7), (0.3, 53.5), (-1.5, 55.2), (-2, 57.6), (-3, 58.6), (-5, 58.6), (-6.2, 57.5), (-5.7, 55.5),
           (-3.3, 54.5), (-4.6, 53.2), (-4.3, 52.2), (-5.3, 51.7), (-5.7, 50)],
    'ireland': [(-10.3, 51.7), (-6.2, 52.2), (-6, 53.9), (-5.6, 55.2), (-7.5, 55.3), (-10, 54.2), (-10.3, 51.7)],
    'iceland': [(-24.3, 65.5), (-22, 64), (-18, 63.4), (-14, 64.5), (-13.5, 65.5), (-16, 66.5), (-22.5, 66.4), (-24.3, 65.5)],
    'cuba': [(-85, 21.8), (-82, 23.1), (-80, 23.2), (-77, 21.6), (-74.2, 20.2), (-77.5, 19.9), (-80.5, 21.6), (-85, 21.8)],
    'hispaniola': [(-74.4, 18.5), (-72, 19.9), (-69, 19.7), (-68.3, 18.6), (-71, 17.6), (-74.4, 18.5)],
    'new_zealand_n': [(172.6, -34.4), (174.5, -36.5), (176, -37.6), (178.5, -37.7), (177, -39.5), (175.2, -41.6), (174.6, -39.6),
                      (173.8, -38.9), (174.6, -37), (172.6, -34.4)],
    'new_zealand_s': [(172.6, -40.5), (174.3, -41.5), (173.2, -43.5), (171, -45.3), (169, -46.6), (166.5, -46), (167.5, -44.7),
                      (170.5, -43), (172.6, -40.5)],
    'sri_lanka': [(79.8, 9.8), (81.2, 8.5), (81.8, 7.3), (81.2, 6.1), (80.1, 6), (79.8, 7.5), (79.8, 9.8)],
    'taiwan': [(120.1, 23), (121, 25.2), (122, 25), (121, 22), (120.1, 23)],
    'svalbard': [(11, 78.5), (16, 80.4), (27, 80.2), (22, 77.5), (16, 76.5), (11, 78.5)],
    'novaya_zemlya': [(52, 71), (55, 73.5), (60, 76.5), (68, 77), (60, 74), (56, 71.5), (52, 71)],
    'sakhalin': [(142, 46), (143.5, 49), (142.5, 54), (141.8, 51), (142, 46)],
    'antarctica': [(-180, -90), (-180, -84), (-150, -77), (-120, -74), (-100, -73), (-75, -73), (-68, -69), (-62, -64), (-57, -63.5),
                   (-60, -66), (-62, -70), (-58, -75), (-40, -78), (-20, -73), (0, -70), (20, -69.5), (40, -68.5), (60, -67),
                   (80, -66.5), (100, -65.5), (120, -66.5), (140, -66.5), (160, -70), (168, -72), (170, -78), (180, -84), (180, -90)],
}
WATER = {
    'hudson_bay': [(-95, 59), (-92, 57), (-86, 55.5), (-82, 53), (-79, 52), (-79, 56), (-78, 59), (-77.5, 62.5), (-82, 64.5), (-87, 64),
                   (-93, 61.5), (-95, 59)],
    'baltic': [(10.5, 54.3), (14, 54.3), (20, 54.6), (21.5, 57), (24, 57.5), (24, 59.5), (30, 60), (25, 60.5), (22.5, 61.5), (21.5, 63.5),
               (25, 65), (22, 65.8), (17.5, 62.5), (18.7, 60), (16.5, 57), (14.5, 56), (12.6, 56), (10.5, 54.3)],
    'black_sea': [(28, 41.5), (29, 44.5), (31.5, 46.6), (34, 44.5), (36.5, 45.2), (39, 47), (38, 44.5), (41.5, 41.5), (37, 41), (32, 41.6),
                  (28, 41.5)],
    'caspian': [(47, 44.5), (49, 46.5), (53, 46.8), (51.5, 45), (53, 42), (54, 38), (52.5, 36.8), (49.5, 37.5), (49.2, 40), (47.5, 42.5),
                (47, 44.5)],
    'red_sea': [(32.6, 29.9), (35, 28), (39, 21.5), (42.5, 15), (43.5, 12.6), (42.5, 13), (40, 16), (37.5, 19), (35.5, 23.5), (33.5, 27),
                (32.6, 29.9)],
    'persian_gulf': [(48, 30), (50.5, 30), (54.5, 26.5), (56.4, 26.2), (56, 24.5), (52, 24), (51, 25.5), (49.5, 27), (48, 30)],
    'adriatic_med_fix': [],
    'gulf_of_california': [(-114.8, 31.8), (-112.5, 29), (-109.5, 25), (-110, 23.3), (-111.8, 26), (-113.5, 29), (-114.8, 31.8)],
    'great_lakes': [(-92, 46.8), (-87, 46.5), (-84.5, 46), (-82.5, 45.8), (-80, 44.5), (-79, 43.3), (-76.5, 43.5), (-79, 42.5), (-81.5, 41.8),
                    (-83.5, 41.7), (-82.5, 43), (-84, 44), (-86.5, 42), (-87.8, 42.5), (-86.5, 45.5), (-90, 46.5), (-92, 46.8)],
}
MOUNTAINS = [((-120, -105, 35, 60), 1.0), ((-80, -65, -45, 8), 1.0), ((70, 100, 27, 38), 1.3), ((5, 16, 44, 48), 0.7),
             ((40, 50, 38, 44), 0.6), ((85, 120, 38, 50), 0.5), ((-125, -115, 45, 60), 0.6), ((30, 40, -10, 15), 0.5),
             ((140, 150, -40, -28), 0.4), ((-10, 10, 28, 35), 0.4)]
DESERTS = [((-17, 35, 15, 32), 1.0), ((35, 60, 15, 32), 1.0), ((60, 75, 24, 38), 0.8), ((88, 112, 38, 46), 0.8), ((117, 142, -32, -19), 0.9),
           ((12, 26, -28, -18), 0.7), ((-72, -68, -28, -15), 0.8), ((-118, -105, 28, 40), 0.6), ((50, 62, 38, 45), 0.6)]


def earth(W=2048, H=1024):
    S = 2

    def raster(polys, value):
        img = Image.new('L', (W * S, H * S), 0)
        d = ImageDraw.Draw(img)
        for pts in polys.values():
            if len(pts) < 3:
                continue
            d.polygon([((lon + 180) / 360 * W * S, (90 - lat) / 180 * H * S) for lon, lat in pts], fill=value)
        return np.asarray(img.resize((W, H), Image.LANCZOS)).astype(np.float64) / 255

    land = raster(LAND, 255)
    water = raster(WATER, 255)
    land = np.clip(land - water, 0, 1)
    x, y, z, lat = sphere_grid(W, H)
    latd = np.degrees(lat)
    lond = (np.arange(W) + 0.5) / W * 360 - 180
    lond = np.broadcast_to(lond, (H, W))
    # Ragged, natural coasts and islands.
    n = fbm(x, y, z, 9.0, 6, 501)
    soft = np.asarray(Image.fromarray((land * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3))).astype(np.float64) / 255
    land = smooth(0.5, 0.56, soft + (n - 0.5) * 0.5)
    # Deserts, mountains.
    def region(boxes, warp):
        out = np.zeros((H, W))
        # Bent out of their boxes by noise, so they have natural edges.
        wl = (fbm(x, y, z, 3.0, 4, 520) - 0.5) * warp
        wa = (fbm(x, y, z, 3.0, 4, 521) - 0.5) * warp
        lo, la = lond + wl, latd + wa
        for (lo0, lo1, la0, la1), k in boxes:
            m = smooth(-2, 9, np.minimum.reduce([lo - lo0, lo1 - lo, la - la0, la1 - la]))
            out = np.maximum(out, m * k)
        return out
    desert = np.clip(region(DESERTS, 22) * (0.9 + 1.2 * fbm(x, y, z, 6.0, 5, 502)), 0, 1)
    mount = np.clip(region(MOUNTAINS, 10) * smooth(0.42, 0.68, fbm(x, y, z, 14.0, 5, 503)), 0, 1)
    a = np.abs(latd)
    tropic = smooth(18, 6, a)
    boreal = smooth(48, 58, a) * smooth(70, 62, a)
    tundra = smooth(62, 70, a)
    v = fbm(x, y, z, 20.0, 5, 504)
    forest = np.array((0.12, 0.3, 0.1)) * (0.8 + 0.4 * v[..., None])
    temperate = np.array((0.25, 0.42, 0.16)) * (0.8 + 0.4 * v[..., None])
    jungle = np.array((0.07, 0.24, 0.07)) * (0.8 + 0.4 * v[..., None])
    taiga = np.array((0.11, 0.22, 0.13)) * (0.85 + 0.3 * v[..., None])
    tund = np.array((0.42, 0.4, 0.33)) * (0.85 + 0.3 * v[..., None])
    sand = np.array((0.78, 0.66, 0.45)) * (0.88 + 0.24 * v[..., None])
    rock = np.array((0.5, 0.44, 0.38)) * (0.8 + 0.4 * v[..., None])
    col = temperate * (1 - tropic[..., None]) + jungle * tropic[..., None]
    col = col * (1 - boreal[..., None]) + taiga * boreal[..., None]
    col = col * (1 - tundra[..., None]) + tund * tundra[..., None]
    col = col * (1 - 0.3 * smooth(0.4, 0.7, v)[..., None]) + forest * 0.3 * smooth(0.4, 0.7, v)[..., None]
    col = col * (1 - desert[..., None]) + sand * desert[..., None]
    col = col * (1 - mount[..., None]) + rock * mount[..., None]
    snowcap = smooth(0.75, 0.95, mount) * smooth(20, 40, a)
    col = col * (1 - snowcap[..., None]) + np.array((0.92, 0.94, 0.97)) * snowcap[..., None]
    # Ice: Antarctica and Greenland white, the Arctic sea frozen in part.
    gl = np.zeros((H, W))
    ice_land = smooth(-62, -68, latd) + ((lond > -75) & (lond < -15) & (latd > 59)).astype(np.float64) * smooth(0.3, 0.6, land)
    ice_land = np.clip(ice_land, 0, 1)
    col = col * (1 - ice_land[..., None]) + np.array((0.93, 0.95, 0.98)) * ice_land[..., None]
    # Oceans: deep blue, paler over the shelves near the coasts.
    near = np.asarray(Image.fromarray((land * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(6))).astype(np.float64) / 255
    deep = np.array((0.02, 0.08, 0.24)) * (0.85 + 0.3 * fbm(x, y, z, 6.0, 3, 505)[..., None])
    shelf = np.array((0.06, 0.24, 0.42))
    sea = deep * (1 - near[..., None]) + shelf * near[..., None]
    seaice = smooth(76, 84, latd) * smooth(0.35, 0.6, fbm(x, y, z, 12.0, 4, 506))
    sea = sea * (1 - seaice[..., None]) + np.array((0.86, 0.9, 0.95)) * seaice[..., None]
    rgb = col * land[..., None] + sea * (1 - land[..., None])
    Image.fromarray(np.clip(rgb * 255, 0, 255).astype(np.uint8)).save(os.path.join(SPACE, 'earth.png'))
    print('saved earth.png')

    # Clouds: the storm belts of the tropics and of the middle latitudes, clearer over the deserts.
    cn = fbm(x, y, z, 7.0, 6, 507)
    swirl = fbm(x * 1.3 + cn, y * 1.3, z * 1.3 - cn, 4.0, 4, 508)
    belt = 0.85 + 0.15 * smooth(14, 0, np.abs(latd - 5)) + 0.2 * smooth(20, 0, np.abs(a - 55)) - 0.15 * smooth(14, 0, np.abs(a - 25))
    streak = fbm(x * 0.6, y * 2.5, z * 0.6, 9.0, 4, 510)
    cover = smooth(0.5, 0.72, (cn * 0.55 + swirl * 0.3 + streak * 0.15) * belt + 0.08)
    clouds = np.zeros((H, W, 4))
    clouds[..., 0:3] = 1
    clouds[..., 3] = cover * 0.92
    Image.fromarray(np.clip(clouds * 255, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(SPACE, 'earth_clouds.png'))
    print('saved earth_clouds.png')

    # City lights on the night side: clustered where people live, densest on coasts and in the busy middle latitudes.
    people = smooth(0.3, 0.9, land) * (1 - ice_land) * (1 - 0.85 * desert) * (1 - tundra) * (0.25 + 0.75 * smooth(60, 30, a))
    coast = np.clip(near * (1 - near) * 4, 0, 1)
    dots = rng.random((H, W)) ** 18
    city = fbm(x, y, z, 40.0, 3, 509)
    light = np.clip(people * (0.4 + coast) * (smooth(0.55, 0.75, city) * 0.9 + dots * 3), 0, 1)
    lights = np.zeros((H, W, 3))
    lights[..., 0] = light
    lights[..., 1] = light * 0.82
    lights[..., 2] = light * 0.5
    Image.fromarray(np.clip(lights * 255, 0, 255).astype(np.uint8)).save(os.path.join(SPACE, 'earth_lights.png'))
    print('saved earth_lights.png')


if __name__ == '__main__':
    blocks()
    planet_map_block()
    earth()
