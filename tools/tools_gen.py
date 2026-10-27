import math, os, json, zlib, struct
R = 'src/main/resources/assets/eggsteal/'
S, RING = 12, 8
M, A, E = 'main', 'accent', 'eye'

def sym(g, x, y, z, rx, ry, rz):
    if x == 0: return [(g, 0, y, z, rx, ry, rz)]
    return [(g, x, y, z, rx, ry, rz), (g, -x, y, z, rx, ry, rz)]
def eyes(x, y, z, s=0.045): return sym(E, x, y, z, s, s * 1.2, s * 0.8)
def legs(x, y, z, r=0.08, h=0.12): return sym(M, x, y, z, r, h, r) + sym(M, x, y, -z, r, h, r)

def fox(tailtip):
    return ([(M,0,.35,0,.28,.23,.4),(M,0,.62,.34,.21,.19,.2),(A,0,.55,.52,.1,.07,.09),(A,0,.4,.28,.14,.13,.1),(M,0,.46,-.5,.1,.11,.26),(A,0,.46,-.74,.09,.1,.1)]
        + sym(M,.12,.93,.3,.06,.16,.05) + sym(A,.12,.9,.34,.03,.1,.02) + legs(.15,.1,.2,.07,.12) + eyes(.09,.66,.5,.035) + tailtip)

C = {
 'slime_bun': [(M,0,.32,0,.46,.32,.46),(A,0,.62,0,.14,.1,.14),(A,.18,.55,.1,.07,.05,.07)] + eyes(.15,.38,.4,.06),
 'chick_puff': [(M,0,.4,0,.34,.34,.32),(A,0,.38,.34,.07,.05,.1),(A,0,.78,.05,.05,.1,.05)] + sym(A,.36,.4,0,.07,.17,.18) + sym(A,.12,.04,.08,.1,.04,.13) + eyes(.14,.5,.28),
 'mushy': [(A,0,.24,0,.16,.24,.16),(M,0,.55,0,.42,.2,.42),(A,0,.74,-.15,.09,.04,.09)] + sym(A,.2,.7,.1,.08,.04,.08) + eyes(.07,.3,.14,.035),
 'pebble_pup': [(M,0,.35,0,.3,.24,.4),(M,0,.62,.36,.22,.2,.2),(A,0,.56,.55,.09,.07,.07),(A,0,.48,-.45,.06,.06,.12)] + sym(A,.18,.82,.3,.06,.1,.05) + legs(.17,.1,.22) + eyes(.1,.66,.54,.035),
 'fox_kit': fox([]),
 'frog_pop': [(M,0,.28,0,.42,.26,.4),(A,0,.22,.1,.3,.15,.3)] + sym(M,.2,.56,.2,.1,.1,.1) + sym(E,.2,.58,.29,.05,.06,.04) + sym(M,.3,.04,.3,.12,.04,.14),
 'bat_mochi': [(M,0,.38,0,.32,.3,.3)] + sym(A,.5,.48,0,.28,.04,.2) + sym(M,.14,.74,0,.06,.13,.04) + sym(A,.05,.28,.3,.015,.04,.015) + eyes(.11,.44,.27,.05),
 'cloud_cat': [(M,0,.4,0,.32,.26,.4),(M,.25,.35,.1,.2,.2,.2),(M,-.25,.35,.1,.2,.2,.2),(M,0,.66,.34,.22,.2,.2),(A,0,.5,-.45,.08,.08,.2),(A,0,.64,.55,.03,.025,.025)] + sym(M,.15,.84,.3,.06,.08,.05) + sym(A,.15,.82,.33,.03,.05,.02) + eyes(.09,.7,.52,.035),
 'panda_bean': [(M,0,.38,0,.34,.3,.32),(M,0,.78,.1,.28,.24,.24),(A,0,.74,.34,.04,.03,.03)] + sym(A,.22,.98,.08,.08,.08,.05) + sym(A,.1,.8,.3,.07,.08,.03) + sym(E,.1,.8,.33,.03,.035,.02) + sym(A,.34,.35,.1,.09,.2,.09) + sym(A,.2,.08,.15,.1,.1,.1),
 'dragon_pup': [(M,0,.38,0,.28,.26,.4),(M,0,.7,.38,.2,.18,.2),(M,0,.64,.55,.1,.07,.1),(M,0,.3,-.5,.09,.09,.3),(M,0,.28,-.8,.06,.06,.2),(A,0,.3,.1,.18,.18,.28),(A,0,.65,-.1,.04,.06,.06),(A,0,.6,-.3,.04,.06,.06)] + sym(A,.1,.9,.3,.03,.1,.03) + sym(A,.4,.6,-.05,.25,.05,.2) + legs(.16,.08,.2,.07,.1) + eyes(.09,.74,.52,.035),
 'ice_fox': fox([(A,0,.78,-.2,.05,.14,.05),(A,0,.86,-.1,.04,.1,.04)] + sym(A,.2,.7,-.1,.04,.12,.04)),
 'lava_mole': [(M,0,.3,0,.4,.28,.46),(A,0,.3,.52,.09,.08,.14),(A,0,.3,.68,.04,.04,.04),(A,0,.55,-.1,.15,.03,.2)] + sym(A,.35,.12,.3,.1,.05,.12) + sym(A,.2,.5,.1,.06,.03,.1) + eyes(.14,.42,.4,.03),
 'star_bunny': [(M,0,.35,0,.3,.28,.32),(M,0,.68,.24,.22,.2,.2),(A,0,.82,.4,.06,.06,.02),(A,0,.35,-.34,.1,.1,.1)] + sym(M,.1,1.05,.2,.06,.28,.05) + sym(A,.1,1.02,.25,.03,.2,.02) + eyes(.09,.72,.41,.035) + sym(M,.14,.05,.18,.07,.05,.12),
 'ghost_moth': [(M,0,.42,0,.18,.3,.18),(M,0,.8,0,.16,.15,.15)] + sym(A,.42,.6,-.05,.34,.32,.03) + sym(A,.3,.3,-.05,.2,.18,.03) + sym(M,.08,1.0,0,.015,.14,.015) + eyes(.07,.82,.13,.04),
 'crystal_deer': [(M,0,.55,0,.25,.22,.4),(M,0,.8,.28,.1,.2,.1),(M,0,.98,.38,.14,.13,.18),(M,0,.94,.55,.07,.06,.08),(A,0,.8,-.1,.05,.12,.05),(A,0,.72,-.25,.05,.1,.05)] + sym(M,.15,.2,.28,.05,.2,.05) + sym(M,.15,.2,-.28,.05,.2,.05) + sym(A,.1,1.18,.3,.03,.14,.03) + sym(A,.18,1.3,.3,.03,.1,.03) + eyes(.1,1.02,.5,.03),
 'galaxy_whale': [(M,0,.5,0,.4,.36,.72),(A,0,.35,.2,.3,.15,.5),(M,0,.55,-.8,.16,.18,.3),(A,0,.58,-1.05,.34,.04,.12),(A,0,.9,.4,.04,.1,.04),(A,0,.82,0,.06,.02,.06),(A,.15,.8,-.3,.05,.02,.05)] + sym(A,.42,.35,.25,.2,.04,.1) + eyes(.3,.58,.5,.05),
}

def build(parts):
    verts, norms, faces = [], [], {}
    for g, cx, cy, cz, rx, ry, rz in parts:
        base = len(verts)
        for i in range(RING + 1):
            th = math.pi * i / RING
            for j in range(S + 1):
                ph = 2 * math.pi * j / S
                ux, uy, uz = math.sin(th) * math.cos(ph), math.cos(th), math.sin(th) * math.sin(ph)
                verts.append((cx + rx * ux, cy + ry * uy, cz + rz * uz))
                nx, ny, nz = ux / rx, uy / ry, uz / rz
                l = math.sqrt(nx * nx + ny * ny + nz * nz) or 1
                norms.append((nx / l, ny / l, nz / l))
        for i in range(RING):
            for j in range(S):
                a = base + i * (S + 1) + j + 1
                b = a + 1
                c = base + (i + 1) * (S + 1) + j + 1
                d = c + 1
                faces.setdefault(g, []).extend([(a, c, b), (b, c, d)])
    return verts, norms, faces

os.makedirs(R + 'models/creature', exist_ok=True)
for name, parts in C.items():
    v, n, f = build(parts)
    with open(os.devnull, 'w') as o:  # the creature models now come from tools/gen_models.py
        o.write(f'# {name} (smooth normals)\n')
        for x, y, z in v: o.write(f'v {x:.4f} {y:.4f} {z:.4f}\n')
        for x, y, z in n: o.write(f'vn {x:.4f} {y:.4f} {z:.4f}\n')
        for g, tris in f.items():
            o.write(f'g {g}\n')
            for a, b, c in tris: o.write(f'f {a}//{a} {b}//{b} {c}//{c}\n')

def png(path, w, h, pix):
    raw = b''.join(b'\x00' + bytes(c for p in row for c in p) for row in pix)
    def ch(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + ch(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + ch(b'IDAT', zlib.compress(raw)) + ch(b'IEND', b''))

png(R + 'textures/entity/white.png', 4, 4, [[(255, 255, 255, 255)] * 4] * 4)

cols = {
 'slime_bun': (0x59d96b, 0xd6ffd9), 'chick_puff': (0xffe55c, 0xff9d2e), 'mushy': (0xe0463c, 0xf7ead2), 'pebble_pup': (0x9c9c9c, 0x5e5e5e),
 'fox_kit': (0xf07f1d, 0xfff2e0), 'frog_pop': (0x4cc24c, 0xe8ff9c), 'bat_mochi': (0x7a4dbd, 0x3b2266), 'cloud_cat': (0xeaf4ff, 0x9fd0ff),
 'panda_bean': (0xf5f5f5, 0x1d1d1d), 'dragon_pup': (0xd63a3a, 0xffc247), 'ice_fox': (0xbfe9ff, 0x4aa8ff), 'lava_mole': (0x4a3a36, 0xff6a00),
 'star_bunny': (0xffd1f0, 0xffe44d), 'ghost_moth': (0xdcd6ff, 0x7d6bff), 'crystal_deer': (0xc59a6a, 0x4ff0e0), 'galaxy_whale': (0x2b2f8f, 0xd66bff)}
def rgb(h): return ((h >> 16) & 255, (h >> 8) & 255, h & 255)
spots = [(6, 5), (9, 7), (5, 9), (8, 11), (10, 4), (7, 13)]
for name, (m, a) in cols.items():
    pix = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.8, (y - 8.5) / 7.2
            d = dx * dx + dy * dy
            if d <= 1.0:
                c = rgb(m)
                if (x, y) in spots: c = rgb(a)
                if d > 0.72: c = tuple(int(v * 0.65) for v in c)
                pix[y][x] = (*c, 255)
    png(R + f'textures/item/egg_{name}.png', 16, 16, pix)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"eggsteal:item/egg_{name}"}}, open(R + f'models/item/egg_{name}.json', 'w'))

pix = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
for i in range(11):
    x, y = 3 + i, 12 - i
    w = 1 + (i // 4)
    for k in range(-w // 2, w // 2 + 1):
        for (px, py) in [(x + k, y), (x, y + k)]:
            if 0 <= px < 16 and 0 <= py < 16: pix[py][px] = (150, 98, 52, 255) if (px + py) % 2 else (122, 78, 40, 255)
png(R + 'textures/item/bat.png', 16, 16, pix)
json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": "eggsteal:item/bat"}}, open(R + 'models/item/bat.json', 'w'))
json.dump({"variants": {"": {"model": "eggsteal:block/upgrade_station"}}}, open(R + 'blockstates/upgrade_station.json', 'w'))
json.dump({"parent": "minecraft:block/cube_all", "textures": {"all": "minecraft:block/emerald_block"}}, open(R + 'models/block/upgrade_station.json', 'w'))
json.dump({"parent": "eggsteal:block/upgrade_station"}, open(R + 'models/item/upgrade_station.json', 'w'))

names = {'slime_bun':'Slime Bun','chick_puff':'Chick Puff','mushy':'Mushy','pebble_pup':'Pebble Pup','fox_kit':'Fox Kit','frog_pop':'Frog Pop','bat_mochi':'Bat Mochi','cloud_cat':'Cloud Cat','panda_bean':'Panda Bean','dragon_pup':'Dragon Pup','ice_fox':'Ice Fox','lava_mole':'Lava Mole','star_bunny':'Star Bunny','ghost_moth':'Ghost Moth','crystal_deer':'Crystal Deer','galaxy_whale':'Galaxy Whale'}
lang = {"itemGroup.eggsteal": "Egg Steal", "item.eggsteal.bat": "Bat", "block.eggsteal.upgrade_station": "Upgrade Station",
        "entity.eggsteal.creature": "Creature", "entity.eggsteal.bot": "Bot", "generator.eggsteal.eggsteal": "Egg Steal Arena"}
for k, v in names.items(): lang[f"item.eggsteal.egg_{k}"] = v + " Egg"
json.dump(lang, open(R + 'lang/en_us.json', 'w'), indent=1)
print('done')
