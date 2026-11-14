package com.realweather.server;

import net.minecraft.util.Mth;

/**
 * One tsunami: a straight wave front, {@code width} blocks either side of its centre line, travelling along
 * (dirX, dirZ) from where it rose out at sea. Strength 1 to 1000 decides how high it stands, how fast it runs, how
 * far inland it gets and what it can tear down. The wave rides on the ground under it, climbing hills and dropping into
 * valleys ({@link TsunamiGround}). Behind the front the land stays under water for the rest of the tsunami's five
 * minutes, then the water drains back.
 */
public class Tsunami {
    public static final int RUNNING = 0, DRAINING = 1;
    /** A tsunami lasts five minutes from the moment it rises; its flood drains over the last half minute. */
    public static final int LIFE = 6000, DRAIN_TICKS = 600;

    public final int id;
    public final double ox, oz;
    public final float dirX, dirZ;
    /** Water level it rides on (the sea, or the ground where there is no sea near). */
    public final float baseY;
    public final int strength;
    public final long seed;
    /** How far the front has come from its origin, blocks. */
    public float front;
    public int stage = RUNNING;
    /** 0 while the water stands behind the wave, rising to 1 as it drains away. */
    public float drained;
    public int age;
    /** Stopped by command: drain now instead of at the end of its life. */
    public boolean stopping;

    public Tsunami(int id, double ox, double oz, float dirX, float dirZ, float baseY, int strength, long seed) {
        this.id = id;
        this.ox = ox;
        this.oz = oz;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.baseY = baseY;
        this.strength = Mth.clamp(strength, 1, 1000);
        this.seed = seed;
    }

    /** Height of the wave when it reaches land, blocks above the water it rides on. */
    public static float heightFor(int strength) {
        return 1.5F + strength * 0.045F;
    }

    public static float speedFor(int strength) {
        return 0.35F + strength * 0.0009F;
    }

    /** Half the length of the front. */
    public static float widthFor(int strength) {
        return 130F + strength * 0.5F;
    }

    /** How far it runs in total before it has spent itself. */
    public static float runFor(int strength) {
        return 420F + strength * 1.25F;
    }

    public float speed() {
        // A little slower as it climbs the land and loses height.
        return speedFor(strength) * (0.65F + 0.35F * Math.max(0.2F, 1F - progress()));
    }

    public float width() {
        return widthFor(strength);
    }

    public float run() {
        return runFor(strength);
    }

    public float progress() {
        return Mth.clamp(front / run(), 0F, 1F);
    }

    /** Height of the wave face right now. */
    public float height() {
        float k = (float) Math.pow(Math.max(0F, 1F - progress()), 0.55);
        float grow = Mth.clamp(age / 120F, 0F, 1F);
        return heightFor(strength) * k * (0.4F + 0.6F * grow) * (stage == DRAINING ? 0F : 1F);
    }

    /** Depth of the water left standing on the land behind the wave at distance {@code s} along it (before draining). */
    public float floodDepth(double s) {
        float k = (float) Math.pow(Math.max(0.0, 1.0 - s / run()), 0.35);
        return (1.5F + heightFor(strength) * 0.16F) * Math.max(0.25F, k);
    }

    /** Whether the drain has started (by command, or near the end of the five minutes). */
    public boolean draining() {
        return stopping || age >= LIFE - DRAIN_TICKS;
    }

    /** Floor under the wave at a point (land, or the sea surface). */
    public float ground(net.minecraft.world.level.Level level, double x, double z) {
        return TsunamiGround.at(level, x, z, baseY + 0.2F);
    }

    /** Whether the floor at a point is land (not the sea). */
    public boolean overLand(float ground) {
        return ground > baseY + 0.75F;
    }

    /**
     * Surface of the flood standing on the land at a point behind the front, or NaN where there is none (ahead of the
     * wave, beyond its ends, or out over the sea).
     */
    public float floodSurface(net.minecraft.world.level.Level level, double x, double z) {
        double s = along(x, z);
        if (s > front - 1 || Math.abs(across(x, z)) > width() || drained >= 1F) {
            return Float.NaN;
        }
        float g = ground(level, x, z);
        if (!overLand(g)) {
            return Float.NaN;
        }
        return g + floodDepth(s) * (1F - drained);
    }

    /** Distance of a point along the travel direction (from the origin). */
    public double along(double x, double z) {
        return (x - ox) * dirX + (z - oz) * dirZ;
    }

    /** Distance of a point to the side of the centre line. */
    public double across(double x, double z) {
        return -(x - ox) * dirZ + (z - oz) * dirX;
    }
}
