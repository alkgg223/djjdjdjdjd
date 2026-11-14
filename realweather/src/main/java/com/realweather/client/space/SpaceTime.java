package com.realweather.client.space;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * The universe's own clock for what you see in space (the planets on their orbits, moons, belts): normally one game
 * day for one day, but it can be paused or run faster (up to ten thousand days a day) without anything jumping, and
 * each world's spin can be sped up or slowed in the planet map.
 */
public final class SpaceTime {
    public static final double[] RATES = {0, 1, 10, 100, 1000, 10000};
    private static double rate = 1, anchorReal = Double.NaN, anchorSim;
    /** How fast worlds turn in the planet map (1 normal). */
    public static double spin = 0;
    /** What to show: magnetic fields round the planets, orbits, the belts. */
    public static boolean fields, orbits = true, belts = true;

    private SpaceTime() {}

    private static double real(ClientLevel level, float pt) {
        return level == null ? 0 : (level.getDayTime() + pt) / 24000.0;
    }

    /** Days on the universe's clock. */
    public static double days(ClientLevel level, float pt) {
        double r = real(level, pt);
        if (Double.isNaN(anchorReal) || Math.abs(r - anchorReal) > 1.0E6) {
            anchorReal = r;
            anchorSim = r;
        }
        return anchorSim + (r - anchorReal) * rate;
    }

    public static double rate() {
        return rate;
    }

    /** A new rate, carrying on from where the clock is now. */
    public static void setRate(ClientLevel level, double r) {
        double now = days(level, 0);
        anchorReal = real(level, 0);
        anchorSim = now;
        rate = r;
    }
}
