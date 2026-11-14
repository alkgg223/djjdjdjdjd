package com.realweather;

import net.minecraft.util.RandomSource;

import java.util.UUID;

/**
 * The four kinds of lightning, weakest to strongest: orange, green, white and purple. A bolt's kind is written into
 * its UUID (which the client receives with the bolt), so the server and every client agree on it without an extra
 * packet, and vanilla bolts get one too. Stronger kinds hit harder, set more fire, flash brighter and thunder louder;
 * each lights the cloud it comes from in its own colour.
 */
public final class LightningKind {
    public static final int ORANGE = 0, GREEN = 1, WHITE = 2, PURPLE = 3;
    public static final String[] NAMES = {"orange", "green", "white", "purple"};
    /** The glow round the channel and the light it puts on the clouds (white is the old blue-white). */
    public static final float[][] GLOW = {{1F, 0.56F, 0.14F}, {0.3F, 1F, 0.42F}, {0.81F, 0.865F, 1F}, {0.66F, 0.3F, 1F}};
    /** The white-hot core, tinted. */
    public static final float[][] CORE = {{1F, 0.92F, 0.66F}, {0.88F, 1F, 0.88F}, {1F, 1F, 1F}, {0.95F, 0.86F, 1F}};
    /** Brightness and loudness. */
    public static final float[] POWER = {0.7F, 1F, 1.3F, 1.7F};
    /** Damage to whatever it hits (vanilla lightning does 5). */
    public static final float[] DAMAGE = {3F, 5F, 8F, 13F};
    /** How thick the channel is. */
    public static final float[] WIDTH = {0.8F, 1F, 1.18F, 1.45F};
    /** Ranges of the 10 bits kept in the UUID: 40% orange, 30% green, 20% white, 10% purple. */
    private static final int[] FROM = {0, 410, 717, 922}, TO = {410, 717, 922, 1024};

    private LightningKind() {}

    public static int of(UUID id) {
        int v = (int) ((id.getLeastSignificantBits() >>> 8) & 1023L);
        return v < 410 ? ORANGE : v < 717 ? GREEN : v < 922 ? WHITE : PURPLE;
    }

    /** The same UUID with the bits that pick the kind set to `kind`. */
    public static UUID withKind(UUID id, int kind, RandomSource rnd) {
        int v = FROM[kind] + rnd.nextInt(TO[kind] - FROM[kind]);
        long lsb = (id.getLeastSignificantBits() & ~(1023L << 8)) | ((long) v << 8);
        return new UUID(id.getMostSignificantBits(), lsb);
    }

    /** A kind for a flash in a storm of the given strength (0..1): the stronger the storm, the more strong lightning. */
    public static int roll(RandomSource rnd, float storm) {
        float s = Math.max(0F, Math.min(1F, storm));
        float u = rnd.nextFloat();
        float pPurple = 0.05F + 0.12F * s, pWhite = 0.15F + 0.12F * s, pGreen = 0.3F;
        if (u < pPurple) {
            return PURPLE;
        }
        if (u < pPurple + pWhite) {
            return WHITE;
        }
        if (u < pPurple + pWhite + pGreen) {
            return GREEN;
        }
        return ORANGE;
    }

    public static int byName(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
