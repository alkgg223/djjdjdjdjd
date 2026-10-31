package com.realweather.server;

import net.minecraft.nbt.CompoundTag;

/**
 * One convective storm moving across the map: a shower, a thunderstorm, a hailstorm or a rotating supercell.
 * Rain, hail, the clouds overhead and the radar picture all come from these cells.
 */
public class StormCell implements StormField.Cell {
    public static final int SHOWER = 0, STORM = 1, HAIL = 2, SUPERCELL = 3;

    public final int id;
    public double x, z;
    public float vx, vz;
    public float radius;
    /** Peak intensity of this cell, 0..1. */
    public float intensity;
    /** Hail production, 0..1 (hailstorms and supercells). */
    public float hail;
    public final int type;
    public int age;
    public int life;
    public final long seed;
    /** Tornado spawned by this cell (0 = none yet). */
    public int tornadoId;

    public StormCell(int id, double x, double z, float vx, float vz, float radius, float intensity, float hail, int type, int life, long seed) {
        this.id = id;
        this.x = x;
        this.z = z;
        this.vx = vx;
        this.vz = vz;
        this.radius = radius;
        this.intensity = intensity;
        this.hail = hail;
        this.type = type;
        this.life = life;
        this.seed = seed;
    }

    /** Growth and decay over the cell's life: 0 -> 1 -> 0. */
    public float strength() {
        return lifeCurve(age, life);
    }

    public static float lifeCurve(int age, int life) {
        float t = (float) age / Math.max(1, life);
        if (t < 0.2F) {
            return t / 0.2F;
        }
        if (t > 0.75F) {
            return Math.max(0F, (1F - t) / 0.25F);
        }
        return 1F;
    }

    @Override public double cx() { return x; }
    @Override public double cz() { return z; }
    @Override public float cvx() { return vx; }
    @Override public float cvz() { return vz; }
    @Override public float cradius() { return radius; }
    @Override public float cintensity() { return intensity; }
    @Override public float chail() { return hail; }
    @Override public int ctype() { return type; }
    @Override public float cstrength() { return strength(); }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putDouble("x", x);
        t.putDouble("z", z);
        t.putFloat("vx", vx);
        t.putFloat("vz", vz);
        t.putFloat("radius", radius);
        t.putFloat("intensity", intensity);
        t.putFloat("hail", hail);
        t.putInt("type", type);
        t.putInt("age", age);
        t.putInt("life", life);
        t.putLong("seed", seed);
        t.putInt("tornado", tornadoId);
        return t;
    }

    static StormCell load(CompoundTag t) {
        StormCell c = new StormCell(t.getInt("id"), t.getDouble("x"), t.getDouble("z"), t.getFloat("vx"), t.getFloat("vz"), t.getFloat("radius"),
                t.getFloat("intensity"), t.getFloat("hail"), t.getInt("type"), t.getInt("life"), t.getLong("seed"));
        c.age = t.getInt("age");
        c.tornadoId = t.getInt("tornado");
        return c;
    }
}
