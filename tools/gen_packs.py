import math, os, json, zlib, struct, random
R = 'src/main/resources/assets/eggsteal/'
J = 'src/main/java/com/eggsteal/'
S, RING = 12, 8
M, A, E = 'main', 'accent', 'eye'

def sym(g, x, y, z, rx, ry, rz):
    if x == 0: return [(g, 0, y, z, rx, ry, rz)]
    return [(g, x, y, z, rx, ry, rz), (g, -x, y, z, rx, ry, rz)]
def eyes(x, y, z, s=0.045): return sym(E, x, y, z, s, s * 1.2, s * 0.8)
def legs(x, y, z, r=0.08, h=0.12): return sym(M, x, y, z, r, h, r) + sym(M, x, y, -z, r, h, r)
def ring(g, n, r, y, zoff, size): return [(g, math.cos(2*math.pi*k/n)*r, y, math.sin(2*math.pi*k/n)*r + zoff, size, size*0.7, size) for k in range(n)]
def tent(n, r, y, h=.22, w=.05): return [(A, math.cos(2*math.pi*k/n)*r, y, math.sin(2*math.pi*k/n)*r, w, h, w) for k in range(n)]

# ---- templates ----
def t_quad(): return [(M,0,.35,0,.3,.24,.4),(M,0,.62,.36,.21,.19,.2),(A,0,.56,.53,.08,.06,.07)] + legs(.17,.1,.22) + eyes(.1,.66,.53,.035)
def t_blob(): return [(M,0,.32,0,.42,.32,.42)] + eyes(.14,.4,.36,.055)
def t_bird(): return [(M,0,.4,0,.32,.3,.3),(A,0,.4,.34,.07,.05,.1)] + sym(A,.12,.04,.08,.1,.04,.13) + eyes(.13,.52,.26)
def t_flyer(): return [(M,0,.42,0,.27,.27,.27)] + eyes(.1,.47,.22,.045)
def t_fish(): return [(M,0,.45,0,.3,.28,.6),(A,0,.3,.15,.22,.1,.4),(M,0,.5,-.7,.1,.14,.2)] + eyes(.22,.52,.42,.05)
def t_crab(): return [(M,0,.25,0,.42,.2,.32)] + sym(A,.5,.35,.22,.15,.1,.1) + sym(M,.3,.08,.2,.05,.1,.05) + sym(M,.3,.08,-.1,.05,.1,.05) + sym(E,.15,.5,.2,.04,.06,.04)
def t_jelly(): return [(M,0,.62,0,.38,.3,.38)] + tent(6,.2,.3) + eyes(.13,.66,.32,.05)
def t_star(): return [(M,0,.3,0,.2,.12,.2)] + [(M,math.cos(2*math.pi*k/5)*.32,.3,math.sin(2*math.pi*k/5)*.32,.14,.08,.14) for k in range(5)] + eyes(.08,.4,.15,.035)
def t_seahorse(): return [(M,0,.5,0,.18,.4,.18),(M,0,.98,.1,.17,.16,.2),(A,0,.92,.33,.05,.04,.13),(A,0,.12,-.12,.12,.12,.12)] + eyes(.1,1.02,.24,.035)
def t_octo(): return [(M,0,.6,0,.34,.32,.34)] + tent(8,.22,.28,.28,.06) + eyes(.12,.64,.3,.05)
def t_turtle(): return [(M,0,.25,0,.3,.14,.4),(M,0,.3,.42,.14,.12,.14),(A,0,.4,0,.34,.17,.38)] + legs(.2,.08,.25,.08,.08) + eyes(.07,.34,.52,.03)

TEMPL = dict(quad=t_quad, blob=t_blob, bird=t_bird, flyer=t_flyer, fish=t_fish, crab=t_crab, jelly=t_jelly, star=t_star, seahorse=t_seahorse, octo=t_octo, turtle=t_turtle)

# ---- features ----
def f_ears(kind):
    if kind == 'cat': return sym(M,.13,.88,.3,.06,.12,.05) + sym(A,.13,.86,.33,.03,.08,.02)
    if kind == 'long': return sym(M,.1,1.05,.25,.06,.28,.05) + sym(A,.1,1.02,.29,.03,.2,.02)
    if kind == 'round': return sym(M,.17,.8,.3,.08,.08,.05)
    if kind == 'tuft': return sym(A,.14,.84,.28,.04,.14,.04)
    return []
def f_horns(size=1.0): return sym(A,.1,.88,.3,.035,.1*size,.035)
def f_bighorns(): return sym(A,.18,.82,.3,.06,.1,.06) + sym(A,.26,.9,.3,.05,.08,.05)
def f_unicorn(): return [(A,0,.9,.4,.03,.16,.03)]
def f_wings(kind):
    if kind == 'bat': return sym(A,.46,.55,-.05,.27,.04,.2)
    return sym(A,.4,.6,-.08,.1,.3,.2) + sym(A,.5,.5,-.08,.07,.22,.15)
def f_tail(kind):
    if kind == 'long': return [(M,0,.4,-.5,.07,.07,.26),(A,0,.4,-.8,.06,.06,.12)]
    if kind == 'fluffy': return [(M,0,.46,-.5,.13,.13,.24),(A,0,.46,-.72,.1,.1,.1)]
    if kind == 'short': return [(A,0,.42,-.42,.07,.07,.08)]
    if kind == 'fin': return [(A,0,.5,-.8,.3,.04,.12)]
    return []
def f_halo(y=1.0, z=.3): return ring(A, 8, .24, y, z, .045)
def f_crown(y=.95, z=.3): return [(A,x*.12,y,z,.035,.09,.035) for x in (-2,-1,0,1,2)] + [(A,0,y-.05,z,.16,.04,.16)]
def f_tent(n=6): return tent(n,.28,.12)
def f_antenna(): return sym(M,.08,1.0,.1,.015,.14,.015) + sym(A,.08,1.16,.1,.05,.05,.05)
def f_spikes(n=4): return [(A,0,.62-.05*k,.1-.14*k,.04,.07,.06) for k in range(n)]
def f_mane(): return ring(A, 9, .22, .66, .34, .1)
def f_shell(): return [(A,0,.5,-.02,.3,.14,.32)]
def f_eyes3(): return sym(E,.14,.58,.34,.04,.05,.03) + [(E,0,.7,.38,.04,.05,.03)]
def f_star(): return [(A,0,.82,.4,.06,.06,.02)]
def f_stars(): return [(A,.45,.6,.1,.04,.04,.04),(A,-.4,.75,-.1,.04,.04,.04),(A,.2,1.0,.1,.035,.035,.035),(A,-.2,.95,.3,.035,.035,.035)]
def f_ring(): return ring(A, 10, .36, .42, 0, .05)
def f_hat(): return [(A,0,.82,.3,.24,.03,.24),(A,0,.96,.3,.12,.14,.12)]
def f_stem(): return [(A,0,.84,.34,.04,.08,.04)]
def f_wrap(): return [(A,0,.35,0,.31,.04,.41),(A,0,.3,0,.3,.04,.4),(A,0,.4,0,.29,.04,.39)]
def f_frosting(): return [(A,0,.8,.34,.18,.06,.18),(A,0,.88,.34,.12,.06,.12),(A,0,.94,.34,.06,.06,.06)]
def f_stick(): return [(A,0,.2,0,.025,.45,.025)]
def f_fangs(): return sym(A,.05,.3,.3,.015,.04,.015)
def f_legs8(): return [(A,math.cos(2*math.pi*k/8)*.38,.14,math.sin(2*math.pi*k/8)*.38,.04,.1,.04) for k in range(8)]
def f_antlers(): return sym(A,.1,1.0,.3,.03,.14,.03) + sym(A,.18,1.14,.3,.03,.1,.03) + sym(A,.06,1.12,.3,.025,.07,.025)
def f_hood(): return [(A,0,.7,.28,.26,.24,.2)]
def f_lamp(): return [(A,0,.95,.3,.015,.2,.015),(A,0,1.18,.38,.07,.07,.07)]
def f_tusk(): return [(A,0,.45,.95,.03,.03,.3)]
def f_dorsal(): return [(A,0,.78,-.1,.04,.16,.18)]
def f_collar(): return [(A,0,.55,.3,.22,.04,.2)]
def f_ribs(): return [(A,0,.4,.05,.31,.03,.2),(A,0,.33,.05,.31,.03,.2),(A,0,.26,.05,.31,.03,.2)]
def f_cheeks(): return sym(A,.18,.55,.5,.05,.05,.03)
FEAT = {k[2:]: v for k, v in globals().items() if k.startswith('f_')}

def spec(id_, name, pack, templ, feats, main, accent):
    return dict(id=id_, name=name, pack=pack, t=templ, f=feats, m=main, a=accent)

P = []
def add(*a): P.append(spec(*a))
# Universe (0)
add('nebula_cat','Nebula Cat',0,'quad',[('ears','cat'),('tail','fluffy'),('stars',)],0x6a3fc9,0xff7be5)
add('comet_pup','Comet Pup',0,'quad',[('ears','round'),('tail','long'),('stars',)],0x3a7bd5,0xaee6ff)
add('star_fox','Star Fox',0,'quad',[('ears','cat'),('tail','fluffy'),('star',)],0x2b2f8f,0xffe44d)
add('moon_bunny','Moon Bunny',0,'quad',[('ears','long'),('tail','short'),('halo',)],0xdfe6f5,0xffe9a8)
add('sun_lion','Sun Lion',0,'quad',[('mane',),('tail','long'),('ears','round')],0xf5a623,0xff5a1f)
add('blackhole_bat','Black Hole Bat',0,'flyer',[('wings','bat'),('ring',),('ears','cat')],0x15111f,0x8b3dff)
add('asteroid_mole','Asteroid Mole',0,'quad',[('spikes',5),('ears','round')],0x7d7468,0x4a443c)
add('galaxy_owl','Galaxy Owl',0,'bird',[('wings','feather'),('ears','tuft'),('stars',)],0x3b2f7a,0x7fd3ff)
add('alien_slime','Alien Slime',0,'blob',[('antenna',),('eyes3',)],0x6bff6b,0xff4fd8)
add('cosmic_dragon','Cosmic Dragon',0,'quad',[('horns',1.3),('wings','bat'),('tail','long'),('spikes',4),('stars',)],0x4a1fa8,0x4fe3ff)
# Gods (1)
add('angel_lamb','Angel Lamb',1,'quad',[('halo',),('wings','feather'),('ears','round')],0xffffff,0xffe27a)
add('halo_chick','Halo Chick',1,'bird',[('halo',0.8)],0xfff3b0,0xffd23f)
add('zeus_eagle','Zeus Eagle',1,'bird',[('wings','feather'),('crown',),('tail','long')],0xf2f2f2,0x58b8ff)
add('anubis_hound','Anubis Hound',1,'quad',[('ears','long'),('collar',),('tail','long')],0x1c1c24,0xd4af37)
add('poseidon_whale','Poseidon Whale',1,'fish',[('crown',.7,.5),('tusk',)],0x2f7fd8,0x7ff0ff)
add('ra_phoenix','Ra Phoenix',1,'bird',[('wings','feather'),('tail','long'),('crown',)],0xff6a1a,0xffd23f)
add('thor_ram','Thor Ram',1,'quad',[('bighorns',),('ears','round'),('tail','short')],0xcfd6e6,0xf2c14e)
add('odin_raven','Odin Raven',1,'bird',[('wings','feather'),('crown',),('tail','long')],0x14141c,0x9aa7ff)
add('athena_fawn','Athena Fawn',1,'quad',[('ears','round'),('horns',.6),('halo',)],0xe9d3a6,0xc9a227)
add('divine_dragon','Divine Dragon',1,'quad',[('horns',1.2),('wings','feather'),('tail','long'),('halo',)],0xfff0c2,0xffb300)
# Halloween (2)
add('pumpkin_pup','Pumpkin Pup',2,'quad',[('ears','round'),('stem',),('tail','short')],0xff7a00,0x2f6b1f)
add('ghost_kitty','Ghost Kitty',2,'blob',[('ears','cat'),('tail','short')],0xf4f4ff,0xb9b9ff)
add('mummy_mouse','Mummy Mouse',2,'quad',[('ears','round'),('wrap',),('tail','long')],0xd8cfae,0x8a8062)
add('vampire_bat','Vampire Bat',2,'flyer',[('wings','bat'),('fangs',),('ears','cat')],0x2a0a1f,0xc0143c)
add('zombie_frog','Zombie Frog',2,'crab',[('eyes3',)],0x7cae5a,0x4a5a2e)
add('witch_cat','Witch Cat',2,'quad',[('ears','cat'),('hat',),('tail','long')],0x201a2b,0x8a2be2)
add('skeleton_pup','Skeleton Pup',2,'quad',[('ears','round'),('ribs',),('tail','short')],0xeeeadc,0xc9c3a8)
add('candycorn_chick','Candy Corn Chick',2,'bird',[('stem',)],0xffb81c,0xffffff)
add('spider_plush','Spider Plush',2,'blob',[('legs8',),('eyes3',)],0x241a33,0xd11a2a)
add('reaper_wolf','Reaper Wolf',2,'quad',[('hood',),('ears','long'),('tail','long'),('horns',1)],0x2e2e3d,0x7be0a8)
# Candy Land (3)
add('gummy_bun','Gummy Bun',3,'blob',[('ears','long'),('cheeks',)],0xff5f8f,0xffd0e0)
add('choco_cat','Choco Cat',3,'quad',[('ears','cat'),('tail','fluffy')],0x5a341f,0xffd9a8)
add('lollipop_duck','Lollipop Duck',3,'bird',[('stick',)],0xffe14d,0xff4f9a)
add('cupcake_pup','Cupcake Pup',3,'quad',[('ears','round'),('frosting',)],0xf3c78e,0xff8fbf)
add('jelly_slime','Jelly Slime',3,'blob',[('crown',.8,.0)],0x5fd0ff,0xb9f0ff)
add('donut_frog','Donut Frog',3,'crab',[('ring',)],0xe9a35f,0xff6fb1)
add('cotton_fox','Cotton Fox',3,'quad',[('ears','cat'),('tail','fluffy'),('mane',)],0xffb3e6,0xb3e6ff)
add('mint_bunny','Mint Bunny',3,'quad',[('ears','long'),('tail','short')],0x8ff0c2,0xffffff)
add('caramel_deer','Caramel Deer',3,'quad',[('antlers',),('ears','round')],0xd68a2e,0xf6d58a)
add('rainbow_unicorn','Rainbow Unicorn',3,'quad',[('unicorn',),('mane',),('tail','long'),('ears','round')],0xffffff,0xff5fd0)
# Deep Ocean (4)
add('coral_crab','Coral Crab',4,'crab',[],0xff6f61,0xffb3a7)
add('glow_jelly','Glow Jelly',4,'jelly',[('lamp',)],0x6fd8ff,0xc7fffb)
add('pearl_turtle','Pearl Turtle',4,'turtle',[],0x4fb36b,0xf5f0ff)
add('shark_pup','Shark Pup',4,'fish',[('dorsal',),('tail','fin')],0x6f8ca8,0xe8f2ff)
add('octo_cutie','Octo Cutie',4,'octo',[('cheeks',)],0xc65bd6,0xffb3f2)
add('seahorse_prince','Seahorse Prince',4,'seahorse',[('crown',.7,.1)],0xffc23f,0xff7f50)
add('starfish_pal','Starfish Pal',4,'star',[],0xff9a3c,0xffe0a8)
add('angler_lamp','Angler Lamp',4,'fish',[('lamp',),('fangs',)],0x1f3b5c,0xfff27a)
add('narwhal_knight','Narwhal Knight',4,'fish',[('tusk',),('tail','fin')],0x8fb4d9,0xe8f5ff)
add('kraken_baby','Kraken Baby',4,'octo',[('horns',1)],0x7a2b6e,0x3fe0c5)

def build_parts(sp):
    parts = list(TEMPL[sp['t']]())
    for f in sp['f']:
        fn = FEAT[f[0]]
        parts += fn(*f[1:])
    return parts

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

def png(path, w, h, pix):
    raw = b''.join(b'\x00' + bytes(c for p in row for c in p) for row in pix)
    def ch(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + ch(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + ch(b'IDAT', zlib.compress(raw)) + ch(b'IEND', b''))
def rgb(h): return ((h >> 16) & 255, (h >> 8) & 255, h & 255)
spots = [(6, 5), (9, 7), (5, 9), (8, 11), (10, 4), (7, 13), (7, 8), (4, 6), (10, 10)]

PACK_NAMES = ['Universe', 'Gods', 'Halloween', 'Candy Land', 'Deep Ocean']
PACK_BASE = [600000, 900000, 400000, 300000, 500000]

os.makedirs(R + 'models/creature', exist_ok=True)
lang = json.load(open(R + 'lang/en_us.json'))
rows = []
for k, sp in enumerate(P):
    idx = k % 10
    v, n, f = build(build_parts(sp))
    with open(os.devnull, 'w') as o:  # the creature models now come from tools/gen_models.py
        o.write(f"# {sp['id']} (smooth normals)\n")
        for x, y, z in v: o.write(f'v {x:.4f} {y:.4f} {z:.4f}\n')
        for x, y, z in n: o.write(f'vn {x:.4f} {y:.4f} {z:.4f}\n')
        for g, tris in f.items():
            o.write(f'g {g}\n')
            for a, b, c in tris: o.write(f'f {a}//{a} {b}//{b} {c}//{c}\n')
    pix = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.8, (y - 8.5) / 7.2
            d = dx * dx + dy * dy
            if d <= 1.0:
                c = rgb(sp['m'])
                if (x, y) in spots: c = rgb(sp['a'])
                if d > 0.72: c = tuple(int(q * 0.6) for q in c)
                if d > 0.88: c = rgb(0xffd23f)  # golden rim = pack egg
                pix[y][x] = (*c, 255)
    png(R + f"textures/item/egg_{sp['id']}.png", 16, 16, pix)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"eggsteal:item/egg_{sp['id']}"}}, open(R + f"models/item/egg_{sp['id']}.json", 'w'))
    lang[f"item.eggsteal.egg_{sp['id']}"] = sp['name'] + ' Egg'
    price = int(round(PACK_BASE[sp['pack']] * (1.55 ** idx), -3))
    rows.append((sp['id'], sp['name'], sp['pack'], price, price // 50, sp['m'], sp['a']))
json.dump(lang, open(R + 'lang/en_us.json', 'w'), indent=1)

base16 = [
 ("slime_bun","Slime Bun",0,50,1,0x59d96b,0xd6ffd9),("chick_puff","Chick Puff",0,100,2,0xffe55c,0xff9d2e),("mushy","Mushy",0,200,4,0xe0463c,0xf7ead2),
 ("pebble_pup","Pebble Pup",0,400,8,0x9c9c9c,0x5e5e5e),("fox_kit","Fox Kit",1,800,16,0xf07f1d,0xfff2e0),("frog_pop","Frog Pop",1,1500,30,0x4cc24c,0xe8ff9c),
 ("bat_mochi","Bat Mochi",1,3000,60,0x7a4dbd,0x3b2266),("cloud_cat","Cloud Cat",1,6000,120,0xeaf4ff,0x9fd0ff),("panda_bean","Panda Bean",2,12000,240,0xf5f5f5,0x1d1d1d),
 ("dragon_pup","Dragon Pup",2,25000,500,0xd63a3a,0xffc247),("ice_fox","Ice Fox",2,50000,1000,0xbfe9ff,0x4aa8ff),("lava_mole","Lava Mole",3,100000,2000,0x4a3a36,0xff6a00),
 ("star_bunny","Star Bunny",3,250000,5000,0xffd1f0,0xffe44d),("ghost_moth","Ghost Moth",4,500000,10000,0xdcd6ff,0x7d6bff),("crystal_deer","Crystal Deer",4,1000000,20000,0xc59a6a,0x4ff0e0),
 ("galaxy_whale","Galaxy Whale",5,5000000,100000,0x2b2f8f,0xd66bff)]
lines = []
for id_, nm, rar, pr, inc, m, a in base16:
    lines.append(f'        add(l, "{id_}", "{nm}", {rar}, -1, {pr}, {inc}, 0x{m:06x}, 0x{a:06x});')
for id_, nm, pk, pr, inc, m, a in rows:
    lines.append(f'        add(l, "{id_}", "{nm}", 6, {pk}, {pr}, {inc}, 0x{m:06x}, 0x{a:06x});')
src = '''package com.eggsteal;

import java.util.ArrayList;
import java.util.List;

/** All creatures: 16 normal ones (base belts) and 50 pack creatures (only on the center belt). Generated by tools/gen_packs.py. */
public final class Creature {
    public static final String[] RARITY = {"Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic", "Pack"};
    public static final String[] RARITY_COLOR = {"§7", "§a", "§9", "§5", "§6", "§c", "§d"};
    public static final String[] PACKS = {"Universe", "Gods", "Halloween", "Candy Land", "Deep Ocean"};
    public static final Creature[] VALUES;

    public final String id, display;
    public final int rarity, pack;
    public final double price, income;
    public final int main, accent;
    private final int ordinal;

    private Creature(int ordinal, String id, String display, int rarity, int pack, double price, double income, int main, int accent) {
        this.ordinal = ordinal; this.id = id; this.display = display; this.rarity = rarity; this.pack = pack;
        this.price = price; this.income = income; this.main = main; this.accent = accent;
    }

    private static void add(List<Creature> l, String id, String display, int rarity, int pack, double price, double income, int main, int accent) {
        l.add(new Creature(l.size(), id, display, rarity, pack, price, income, main, accent));
    }

    static {
        List<Creature> l = new ArrayList<>();
''' + '\n'.join(lines) + '''
        VALUES = l.toArray(new Creature[0]);
    }

    public static Creature[] values() { return VALUES; }
    public int ordinal() { return ordinal; }
    public boolean isPack() { return pack >= 0; }
    /** How long a thief must stay next to it: pack creatures are hard to steal (8 s). */
    public int stealTicks() { return isPack() ? 160 : 0; }
    public String tag() { return (isPack() ? "§d" : RARITY_COLOR[rarity]) + display; }

    public static List<Creature> byRarity(int r) {
        List<Creature> l = new ArrayList<>();
        for (Creature c : VALUES) if (!c.isPack() && c.rarity == r) l.add(c);
        return l;
    }

    public static List<Creature> ofPack(int p) {
        List<Creature> l = new ArrayList<>();
        for (Creature c : VALUES) if (c.pack == p) l.add(c);
        return l;
    }
}
'''
open(J + 'Creature.java', 'w').write(src)

# ---- skin blocks (custom blocks) ----
skin_blocks = {
 'neon': ((255, 70, 170), (255, 200, 235), 'stripes'), 'crystal': ((70, 220, 255), (210, 255, 255), 'diamond'),
 'candy': ((255, 120, 170), (255, 255, 255), 'stripes'), 'golden': ((230, 180, 40), (255, 240, 150), 'bricks'),
 'rune': ((60, 30, 90), (180, 110, 255), 'runes'), 'frost': ((170, 215, 245), (240, 250, 255), 'bricks'),
 'slime': ((90, 210, 90), (190, 255, 160), 'diamond'), 'lavarock': ((60, 30, 25), (255, 120, 20), 'cracks')}
random.seed(7)
os.makedirs(R + 'textures/block', exist_ok=True)
for name, (b, a, pat) in skin_blocks.items():
    pix = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            j = random.randint(-10, 10)
            c = tuple(max(0, min(255, q + j)) for q in b)
            on = False
            if pat == 'stripes': on = ((x + y) // 3) % 2 == 0
            elif pat == 'bricks': on = (y % 4 == 0) or ((x + (y // 4) * 4) % 8 == 0)
            elif pat == 'diamond': on = abs(x - 7.5) + abs(y - 7.5) in (3.0, 7.0) or (abs(abs(x - 7.5) + abs(y - 7.5) - 5) < 0.6)
            elif pat == 'runes': on = (x in (4, 11) and 3 < y < 12) or (y == 8 and 4 <= x <= 11)
            elif pat == 'cracks': on = (x * 3 + y * 5) % 11 == 0 or (x == y)
            if on: c = tuple(max(0, min(255, q + j // 2)) for q in a)
            pix[y][x] = (*c, 255)
    png(R + f'textures/block/skin_{name}.png', 16, 16, pix)
    json.dump({"variants": {"": {"model": f"eggsteal:block/skin_{name}"}}}, open(R + f'blockstates/skin_{name}.json', 'w'))
    json.dump({"parent": "minecraft:block/cube_all", "textures": {"all": f"eggsteal:block/skin_{name}"}}, open(R + f'models/block/skin_{name}.json', 'w'))
    json.dump({"parent": f"eggsteal:block/skin_{name}"}, open(R + f'models/item/skin_{name}.json', 'w'))
    lang[f'block.eggsteal.skin_{name}'] = name.capitalize() + ' Block'
json.dump(lang, open(R + 'lang/en_us.json', 'w'), indent=1)
print(len(P), 'pack creatures;', len(rows) + 16, 'total')
