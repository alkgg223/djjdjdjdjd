package com.eggsteal;

/** Special egg variants that only appear on the belts during random events. A mutated creature earns more money. */
public enum Mutation {
    NONE("", 1),
    RAINBOW("Rainbow", 5),
    GLITCH("Glitch", 4),
    MOLTEN("Molten", 3),
    GALAXY("Galaxy", 6);

    static final String[] RAINBOW_COLORS = {"§c", "§6", "§e", "§a", "§b", "§9", "§d"};
    public final String label;
    public final double mult;

    Mutation(String label, double mult) {
        this.label = label;
        this.mult = mult;
    }

    public static Mutation of(int i) {
        Mutation[] v = values();
        return i > 0 && i < v.length ? v[i] : NONE;
    }

    /** HSV (all 0..1) to 0xRRGGBB. */
    public static int hsv(float h, float s, float v) {
        h = (h - (float) Math.floor(h)) * 6f;
        int i = (int) h;
        float f = h - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q;
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    /** Coloured name plus a trailing space ("" for normal creatures). */
    public String prefix() {
        switch (this) {
            case NONE: return "";
            case RAINBOW: {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < label.length(); i++) sb.append(RAINBOW_COLORS[i % RAINBOW_COLORS.length]).append("§l").append(label.charAt(i));
                return sb.append(" ").toString();
            }
            case GLITCH: return "§a§l§kG§a§llitch ";
            case MOLTEN: return "§6§lMolten ";
            default: return "§5§lGalaxy ";
        }
    }
}
