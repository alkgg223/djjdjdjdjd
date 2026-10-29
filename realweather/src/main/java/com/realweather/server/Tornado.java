package com.realweather.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * One tornado. Ratings follow the Fujita scale (F0 to F5): size, wind, how much it lifts and what it can tear up.
 * Life: FORMING (the funnel lowers from the cloud), MATURE, DISSIPATING (it ropes out and lifts back up).
 */
public class Tornado {
    /** Core radius at full strength, blocks. F0 is a thin rope, F5 a wedge more than fifty blocks across. */
    public static final float[] RADIUS = {3.5F, 6F, 9F, 13F, 19F, 27F};
    /** Peak wind at the edge of the core, blocks per tick. */
    public static final float[] WIND = {0.22F, 0.34F, 0.48F, 0.64F, 0.82F, 1.05F};
    /** Hardest block the winds can tear loose (leaves; glass and dirt; wood; stone; iron; nearly anything). */
    public static final float[] HARDNESS = {0.25F, 0.8F, 2.05F, 3.05F, 5.05F, 30F};
    /** Ground spots hit per tick. */
    public static final int[] BREAKS = {2, 4, 8, 14, 22, 32};
    /** Damage per second to anything caught in the core. */
    public static final float[] DAMAGE = {0F, 0.5F, 1F, 2F, 3F, 5F};
    /** How high things get carried before being thrown out, blocks. */
    public static final float[] LIFT = {0F, 4F, 10F, 18F, 28F, 40F};

    public static final int FORMING = 0, MATURE = 1, DISSIPATING = 2;

    public final int id;
    public double x, z;
    public float groundY;
    public float heading;
    public float speed;
    public final int rating;
    public int age;
    public int life;
    public int stage = FORMING;
    /** 0 = funnel still aloft, 1 = on the ground. */
    public float contact;
    /** 0..1 how developed the vortex is (grows while forming, shrinks while roping out). */
    public float strength;
    public final long seed;

    public Tornado(int id, double x, double z, float groundY, float heading, float speed, int rating, int life, long seed) {
        this.id = id;
        this.x = x;
        this.z = z;
        this.groundY = groundY;
        this.heading = heading;
        this.speed = speed;
        this.rating = Mth.clamp(rating, 0, 5);
        this.life = life;
        this.seed = seed;
    }

    public float coreRadius() {
        return RADIUS[rating] * (0.35F + 0.65F * strength);
    }

    public float wind() {
        return WIND[rating] * strength;
    }

    /** Advances the life cycle; returns false once the tornado is gone. */
    boolean tickLife() {
        age++;
        switch (stage) {
            case FORMING -> {
                contact = Math.min(1F, contact + 1F / 400F);
                strength = Math.min(1F, strength + 1F / 360F);
                if (contact >= 1F && strength >= 1F) {
                    stage = MATURE;
                }
            }
            case MATURE -> {
                if (age >= life) {
                    stage = DISSIPATING;
                }
            }
            default -> {
                strength = Math.max(0F, strength - 1F / 600F);
                contact = Math.max(0F, contact - 1F / 500F);
                if (strength <= 0F) {
                    return false;
                }
            }
        }
        return true;
    }

    void dissipate() {
        if (stage != DISSIPATING) {
            stage = DISSIPATING;
        }
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putDouble("x", x);
        t.putDouble("z", z);
        t.putFloat("groundY", groundY);
        t.putFloat("heading", heading);
        t.putFloat("speed", speed);
        t.putInt("rating", rating);
        t.putInt("age", age);
        t.putInt("life", life);
        t.putInt("stage", stage);
        t.putFloat("contact", contact);
        t.putFloat("strength", strength);
        t.putLong("seed", seed);
        return t;
    }

    static Tornado load(CompoundTag t) {
        Tornado o = new Tornado(t.getInt("id"), t.getDouble("x"), t.getDouble("z"), t.getFloat("groundY"), t.getFloat("heading"),
                t.getFloat("speed"), t.getInt("rating"), t.getInt("life"), t.getLong("seed"));
        o.age = t.getInt("age");
        o.stage = t.getInt("stage");
        o.contact = t.getFloat("contact");
        o.strength = t.getFloat("strength");
        return o;
    }
}
