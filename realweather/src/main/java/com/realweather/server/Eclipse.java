package com.realweather.server;

import net.minecraft.util.Mth;

/**
 * A solar eclipse: the moon's disc slides across the sun's. Positions are in sun radii in the plane of the sun's disc
 * (x along the moon's path, z across it). For a total eclipse the moon (a little larger than the sun) passes right
 * over its centre; for a partial one it passes to one side and only covers part of it. The moon slows as it nears the
 * centre, so the deep part of the eclipse (and totality) lasts a good while.
 */
public final class Eclipse {
    /** Moon radius in sun radii: just bigger, so it can cover the sun completely. */
    public static final float MOON = 1.12F;

    public final long start;
    public final int duration;
    /** How far the moon's path passes from the sun's centre (0 = right over it), sun radii. */
    public final float miss;
    /** Direction of the path across the sun's disc, radians. */
    public final float angle;

    public Eclipse(long start, int duration, float miss, float angle) {
        this.start = start;
        this.duration = duration;
        this.miss = miss;
        this.angle = angle;
    }

    public boolean total() {
        return miss + 1F <= MOON;
    }

    /** 0..1 over the whole eclipse (first contact near 0, last near 1). */
    public float progress(double gameTime) {
        return (float) Mth.clamp((gameTime - start) / duration, 0.0, 1.0);
    }

    public boolean active(double gameTime) {
        return gameTime >= start && gameTime <= start + duration;
    }

    /** Moon centre relative to the sun's, in sun radii {along, across}, rotated by `angle`. */
    public float[] moonOffset(double gameTime) {
        float p = progress(gameTime) * 2F - 1F;
        float along = Math.copySign((float) Math.pow(Math.abs(p), 1.55), p) * 2.35F;
        float c = Mth.cos(angle), s = Mth.sin(angle);
        return new float[]{along * c - miss * s, along * s + miss * c};
    }

    /** Fraction of the sun's disc hidden by the moon, 0..1. */
    public float coverage(double gameTime) {
        float[] o = moonOffset(gameTime);
        float d = Mth.sqrt(o[0] * o[0] + o[1] * o[1]);
        return overlap(d, 1F, MOON) / Mth.PI;
    }

    /** Area where two discs (radii r1, r2, centres d apart) overlap. */
    static float overlap(float d, float r1, float r2) {
        if (d >= r1 + r2) {
            return 0F;
        }
        if (d <= Math.abs(r2 - r1)) {
            float r = Math.min(r1, r2);
            return Mth.PI * r * r;
        }
        double a = r1 * r1 * Math.acos((d * d + r1 * r1 - r2 * r2) / (2 * d * r1));
        double b = r2 * r2 * Math.acos((d * d + r2 * r2 - r1 * r1) / (2 * d * r2));
        double c = 0.5 * Math.sqrt(Math.max(0, (-d + r1 + r2) * (d + r1 - r2) * (d - r1 + r2) * (d + r1 + r2)));
        return (float) (a + b - c);
    }

    /**
     * How dark it gets, 0..1. The eye hardly notices until most of the sun is gone; then the light falls fast, and in
     * totality it is a deep twilight with the stars out.
     */
    public static float darkness(float coverage) {
        if (coverage >= 0.9995F) {
            return 1F;
        }
        float visible = 1F - coverage;
        return Mth.clamp(1F - (float) Math.pow(visible, 0.32), 0F, 1F) * 0.88F;
    }

    /** 0..1: the 'diamond ring': the last bead of sunlight flaring at the edge just before and after totality. */
    public static float diamond(float coverage) {
        if (coverage >= 0.9995F || coverage < 0.975F) {
            return 0F;
        }
        float t = (coverage - 0.975F) / (0.9995F - 0.975F);
        return (float) Math.pow(t, 2.2) * (1F - (float) Math.pow(t, 18));
    }
}
