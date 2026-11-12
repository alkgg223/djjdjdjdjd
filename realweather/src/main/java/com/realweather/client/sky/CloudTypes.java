package com.realweather.client.sky;

import com.realweather.client.ClientWeather;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * Which kinds of cloud are about today, 0..1 each: low (cumulus, stratocumulus, stratus, nimbostratus,
 * cumulonimbus), middle (altocumulus, altostratus) and high (cirrus, cirrocumulus, cirrostratus). Every day has its own
 * mix (a fair-weather cumulus day, a grey stratocumulus deck, a day of high cirrus streaks and veils, a dull stratus
 * day, an afternoon of towering storm clouds...), rain and thunder bring nimbostratus and cumulonimbus, and the
 * renderers vary the mix from place to place on top of that, so the sky over one valley is not the same as over the
 * next.
 */
public final class CloudTypes {
    public static float cumulus = 1F, stratocumulus, stratus, nimbostratus, cumulonimbus;
    public static float altocumulus, altostratus, cirrus, cirrocumulus, cirrostratus;
    /** A per-day offset for the cloud patterns, so the same place looks different tomorrow. */
    public static float offsetX, offsetZ;
    private static final float[] TARGET = new float[10];
    private static final float[] NOW = {1F, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static long lastDay = Long.MIN_VALUE;

    private CloudTypes() {}

    /** The day's mix (with the weather), eased so the sky never jumps. */
    static void update(ClientLevel level) {
        long day = Math.floorDiv(level.getDayTime(), 24000L);
        boolean first = lastDay == Long.MIN_VALUE;
        if (day != lastDay) {
            lastDay = day;
            offsetX = h(day, 1) * 20000F;
            offsetZ = h(day, 2) * 20000F;
            int regime = Math.floorMod((int) (h(day, 3) * 1000F), 7);
            float[] t = TARGET;
            java.util.Arrays.fill(t, 0F);
            switch (regime) {
                case 0 -> { // fair-weather cumulus, a few cirrus
                    t[0] = 1F;
                    t[7] = 0.3F;
                }
                case 1 -> { // a grey-white stratocumulus deck with breaks
                    t[1] = 1F;
                    t[0] = 0.3F;
                }
                case 2 -> { // high cloud: cirrus streaks, a cirrostratus veil coming in
                    t[7] = 1F;
                    t[9] = 0.6F;
                    t[8] = 0.4F;
                    t[0] = 0.25F;
                }
                case 3 -> { // mackerel sky: altocumulus and cirrocumulus, some cumulus below
                    t[5] = 1F;
                    t[8] = 0.6F;
                    t[0] = 0.4F;
                }
                case 4 -> { // dull: low stratus, altostratus above
                    t[2] = 1F;
                    t[6] = 0.7F;
                }
                case 5 -> { // building towers, a storm cloud or two by afternoon
                    t[0] = 1F;
                    t[4] = 0.8F;
                    t[7] = 0.3F;
                }
                default -> { // a mixed sky
                    t[0] = 0.7F;
                    t[5] = 0.5F;
                    t[7] = 0.6F;
                    t[6] = 0.3F;
                }
            }
        }
        // Rain lays a nimbostratus deck under everything; thunder piles up cumulonimbus.
        float rain = Mth.clamp(level.getRainLevel(1F), 0F, 1F);
        float thunder = Mth.clamp(ClientWeather.thunder * level.getThunderLevel(1F) * 1.5F, 0F, 1F);
        for (int i = 0; i < 10; i++) {
            float target = TARGET[i] * (1F - rain * 0.7F);
            if (i == 3) {
                target = Math.max(target, rain);
            } else if (i == 2) {
                target = Math.max(target, rain * 0.5F);
            } else if (i == 4) {
                target = Math.max(target, thunder);
            }
            NOW[i] = first ? target : NOW[i] + (target - NOW[i]) * 0.002F;
        }
        cumulus = NOW[0];
        stratocumulus = NOW[1];
        stratus = NOW[2];
        nimbostratus = NOW[3];
        cumulonimbus = NOW[4];
        altocumulus = NOW[5];
        altostratus = NOW[6];
        cirrus = NOW[7];
        cirrocumulus = NOW[8];
        cirrostratus = NOW[9];
    }

    /** Snap straight to today's mix (on joining a world). */
    public static void snap() {
        lastDay = Long.MIN_VALUE;
    }

    private static float h(long day, int salt) {
        long x = day * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L;
        x ^= x >>> 31;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 29;
        return (x >>> 40) / (float) (1L << 24);
    }
}
