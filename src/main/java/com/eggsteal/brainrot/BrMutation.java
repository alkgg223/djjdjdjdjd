package com.eggsteal.brainrot;

import net.minecraft.util.RandomSource;

/**
 * The 35 brainrot mutations. Each one recolours the whole brainrot with a three-colour gradient (shadow, middle, light),
 * lays a voxel pattern over it, can make parts of it glow, and adds its own particles and effects (fire, lightning, halo...).
 * The rarer the mutation, the more money the brainrot makes. Index 0 is "no mutation".
 */
public final class BrMutation {
    // effects drawn around the brainrot (bit mask)
    public static final int FIRE = 1, GLINT = 2, BUBBLES = 4, LIGHTNING = 8, HALO = 16, RAYS = 32, ORBIT = 64, VOID_RING = 128,
            CROWN = 256, RAINBOW_FIRE = 512, PULSE = 1024, SNOW = 2048, SHARDS = 4096;
    // special colouring
    public static final int NORMAL = 0, RAINBOW = 1, GLITCH = 2, YIN_YANG = 3, HOLO = 4, GHOST = 5, FULLBRIGHT = 6;
    // particles (client side switch)
    public static final int P_NONE = 0, P_WAX_ON = 1, P_WAX_OFF = 2, P_COMPOSTER = 3, P_SNOW = 4, P_SPRINKLE = 5, P_END_ROD = 6, P_SLIME = 7,
            P_FLAME = 8, P_LAVA = 9, P_SPARK = 10, P_WATER = 11, P_GLOW = 12, P_SMOKE = 13, P_SOUL = 14, P_ENCHANT = 15, P_HEART = 16,
            P_CHERRY = 17, P_CRIMSON = 18, P_VILLAGER = 19, P_HIT = 20, P_PORTAL = 21, P_INK = 22, P_REVERSE = 23, P_TOTEM = 24, P_SOUL_FIRE = 25;

    public final int index;
    public final String id, name, color;
    public final double weight, mult;
    public final int dark, mid, light;
    public final String pattern, glow;
    public final int glowColor, special, particle, extras;

    private BrMutation(int index, String id, String name, String color, double weight, double mult, int dark, int mid, int light,
                       String pattern, String glow, int glowColor, int special, int particle, int extras) {
        this.index = index; this.id = id; this.name = name; this.color = color; this.weight = weight; this.mult = mult;
        this.dark = dark; this.mid = mid; this.light = light; this.pattern = pattern; this.glow = glow; this.glowColor = glowColor;
        this.special = special; this.particle = particle; this.extras = extras;
    }

    public static final BrMutation[] ALL = {
        m(0, "none", "Normal", "§f", 3200, 1, 0, 0, 0, "vox", null, 0, NORMAL, P_NONE, 0),
        m(1, "bronze", "Bronze", "§6", 60, 1.5, 0x4a2a12, 0xb8733a, 0xf0c08a, "sparkle", null, 0, NORMAL, P_WAX_ON, 0),
        m(2, "silver", "Silver", "§7", 50, 1.75, 0x4a4f5a, 0xa8b0bd, 0xffffff, "sparkle", null, 0, NORMAL, P_WAX_OFF, GLINT),
        m(3, "gold", "Gold", "§e", 40, 2, 0x6b4a00, 0xe0a800, 0xfff2a0, "sparkle", null, 0, NORMAL, P_WAX_ON, GLINT),
        m(4, "mossy", "Mossy", "§2", 34, 2.25, 0x1f3a12, 0x4e8a2a, 0xb5e07a, "spots", null, 0, NORMAL, P_COMPOSTER, 0),
        m(5, "frozen", "Frozen", "§b", 30, 2.5, 0x2a5a8a, 0x8fd0ff, 0xffffff, "crystal", null, 0, NORMAL, P_SNOW, SNOW),
        m(6, "candy", "Candy", "§d", 26, 2.75, 0xd8b0c0, 0xfff0f6, 0xffffff, "candy", null, 0, NORMAL, P_SPRINKLE, 0),
        m(7, "bubblegum", "Bubblegum", "§d", 23, 3, 0xc23a7a, 0xff7ab8, 0xffd0e8, "dots", null, 0, NORMAL, P_NONE, BUBBLES),
        m(8, "zebra", "Zebra", "§f", 20, 3.25, 0xd8d8d8, 0xf2f2f2, 0xffffff, "zebra", null, 0, NORMAL, P_NONE, 0),
        m(9, "leopard", "Leopard", "§6", 18, 3.5, 0xa8701a, 0xe8b04a, 0xffe08a, "leopard", null, 0, NORMAL, P_NONE, 0),
        m(10, "diamond", "Diamond", "§b", 16, 4, 0x1a8a9a, 0x5fe8f0, 0xe8ffff, "crystal", "e_sparkle", 0xc8ffff, NORMAL, P_END_ROD, GLINT),
        m(11, "toxic", "Toxic", "§a", 14, 4.5, 0x1a3a0a, 0x5cd12a, 0xd8ff7a, "drips", "e_drips", 0x7aff3a, NORMAL, P_SLIME, 0),
        m(12, "fire", "Fire", "§c", 12, 5, 0x6a0a00, 0xff4a00, 0xffd040, "flames", "e_flames", 0xff8a20, NORMAL, P_FLAME, FIRE),
        m(13, "lava", "Lava", "§6", 10.5, 5.5, 0x140c08, 0x3a2418, 0x6a4430, "cracks", "e_cracks", 0xff6a00, NORMAL, P_LAVA, 0),
        m(14, "electric", "Electric", "§e", 9, 6, 0x5a4a00, 0xffe000, 0xffffa0, "zigzag", "e_zigzag", 0xffff60, NORMAL, P_SPARK, LIGHTNING),
        m(15, "ocean", "Ocean", "§9", 8, 6.5, 0x0a2a6a, 0x2a7ad8, 0x9ae0ff, "waves", null, 0, NORMAL, P_WATER, BUBBLES),
        m(16, "neon", "Neon", "§d", 7, 7, 0x0a0a14, 0x1a1a2a, 0x2a2a40, "circuit", "e_circuit", 0xff40e0, NORMAL, P_GLOW, 0),
        m(17, "shadow", "Shadow", "§8", 6, 7.5, 0x050508, 0x1a1a24, 0x3a3a4a, "vox", null, 0, NORMAL, P_SMOKE, 0),
        m(18, "ghost", "Ghost", "§f", 5.2, 8, 0xb8c4e8, 0xe0e8ff, 0xffffff, "vox", null, 0, GHOST, P_SOUL, 0),
        m(19, "glitch", "Glitch", "§a", 4.6, 8.5, 0x101010, 0x40ff80, 0xffffff, "checker", "e_checker", 0x40ff80, GLITCH, P_ENCHANT, 0),
        m(20, "camo", "Camo", "§2", 4, 9, 0xe0e0d0, 0xf4f4ea, 0xffffff, "camo", null, 0, NORMAL, P_NONE, 0),
        m(21, "love", "Lovestruck", "§d", 3.6, 10, 0xff7aa8, 0xffb0cc, 0xffe8f0, "hearts", null, 0, NORMAL, P_HEART, 0),
        m(22, "sakura", "Sakura", "§d", 3.2, 11, 0xffb8d0, 0xffdcea, 0xffffff, "petals", null, 0, NORMAL, P_CHERRY, 0),
        m(23, "bloodrot", "Bloodrot", "§4", 2.8, 12, 0x2a0004, 0x7a0010, 0xd02030, "drips", "e_drips", 0xff2030, NORMAL, P_CRIMSON, 0),
        m(24, "radioactive", "Radioactive", "§a", 2.4, 13, 0x102a00, 0x3aff2a, 0xe0ffb0, "spots", "e_spots", 0x60ff40, NORMAL, P_VILLAGER, PULSE),
        m(25, "amethyst", "Amethyst", "§5", 2.1, 14, 0x3a1a6a, 0x9a5ae0, 0xf0d0ff, "crystal", "e_sparkle", 0xe0a0ff, NORMAL, P_HIT, SHARDS),
        m(26, "starry", "Starry Night", "§9", 1.8, 16, 0x05082a, 0x1a2a6a, 0x3a5ab0, "stars", "e_stars", 0xfff4c0, NORMAL, P_END_ROD, 0),
        m(27, "rainbow", "Rainbow", "§c", 1.5, 18, 0, 0, 0, "sparkle", null, 0, RAINBOW, P_SPRINKLE, GLINT),
        m(28, "galaxy", "Galaxy", "§5", 1.25, 20, 0x0a0520, 0x4a1a9a, 0xff7ae0, "swirl", "e_stars", 0xffffff, NORMAL, P_PORTAL, ORBIT),
        m(29, "celestial", "Celestial", "§e", 1.0, 23, 0xd8d0ff, 0xfff8e0, 0xffffff, "sparkle", "e_sparkle", 0xfff0a0, NORMAL, P_END_ROD, HALO | GLINT),
        m(30, "yinyang", "Yin Yang", "§f", 0.8, 26, 0, 0, 0, "dots", null, 0, YIN_YANG, P_INK, 0),
        m(31, "holo", "Holographic", "§b", 0.65, 30, 0, 0, 0, "scan", "e_scan", 0x80e0ff, HOLO, P_WAX_OFF, GLINT),
        m(32, "solar", "Solar Flare", "§6", 0.5, 35, 0xff8a00, 0xffd000, 0xfffff0, "vox", null, 0, FULLBRIGHT, P_FLAME, RAYS | FIRE),
        m(33, "void", "Void", "§5", 0.4, 40, 0x000000, 0x05000a, 0x14002a, "stars", "e_stars", 0xb070ff, NORMAL, P_REVERSE, VOID_RING),
        m(34, "divine", "Divine", "§e§l", 0.3, 50, 0xffe8a0, 0xfffbe8, 0xffffff, "sparkle", "e_sparkle", 0xffe080, FULLBRIGHT, P_TOTEM, HALO | RAYS | CROWN),
        m(35, "cosmic_inferno", "Cosmic Inferno", "§c§l", 0.2, 75, 0x14002a, 0x8a1aff, 0xff60c0, "swirl", "e_stars", 0xffffff, NORMAL, P_SOUL_FIRE, RAINBOW_FIRE | ORBIT),
    };

    private static BrMutation m(int i, String id, String name, String color, double w, double mult, int d, int mid, int l, String pat, String glow, int gc, int sp, int par, int ex) {
        return new BrMutation(i, id, name, color, w, mult, d, mid, l, pat, glow, gc, sp, par, ex);
    }

    public static BrMutation of(int i) { return i > 0 && i < ALL.length ? ALL[i] : ALL[0]; }

    /** "Rainbow " in rainbow letters, "Gold " in yellow... and "" for normal brainrots. */
    public String prefix() {
        if (index == 0) return "";
        if (special == RAINBOW) {
            String[] c = {"§c", "§6", "§e", "§a", "§b", "§9", "§d"};
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < name.length(); k++) sb.append(c[k % c.length]).append(name.charAt(k));
            return sb.append(" ").toString();
        }
        return color + "[" + name + "] ";
    }

    public static double totalWeight() {
        double t = 0;
        for (BrMutation m : ALL) t += m.weight;
        return t;
    }

    /** The chance (0..1) of this mutation on a carpet brainrot. */
    public double chance() { return weight / totalWeight(); }

    /**
     * Rolls a mutation. boost makes every mutation likelier, favour (if > 0) is a mutation that a random event makes 25x likelier,
     * and minIndex can force a mutation (used by the 10-minute rare brainrot).
     */
    public static int roll(RandomSource r, double boost, int favour, int minIndex) {
        double tot = 0;
        for (BrMutation m : ALL) if (m.index >= minIndex) tot += w(m, boost, favour);
        double x = r.nextDouble() * tot;
        for (BrMutation m : ALL) {
            if (m.index < minIndex) continue;
            x -= w(m, boost, favour);
            if (x < 0) return m.index;
        }
        return minIndex;
    }

    static double w(BrMutation m, double boost, int favour) {
        if (m.index == 0) return m.weight;
        return m.weight * boost * (m.index == favour ? 25 : 1);
    }
}
