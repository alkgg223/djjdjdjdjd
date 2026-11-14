package com.realweather.server;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * One earthquake: an epicentre, a magnitude on the Richter scale (1 to 10), a fault line through the epicentre and
 * how long the ground shakes. How hard it shakes at a place falls off with the distance from the epicentre (a
 * Mercalli-like intensity, 0..12), rising fast as the waves arrive, peaking and dying away.
 *
 * <p>Strong quakes split the ground: a main rupture along the fault, branching cracks off it and a web of fine
 * cracks near the epicentre. The crack network is generated from the quake's seed, so the server (which tears the
 * ground open along it) and every client (which draws it) build exactly the same cracks without sending them.
 * Cracks run outward from the epicentre as the rupture spreads and widen while the ground shakes.
 */
public class Earthquake {
    /** Ticks the cracks stay drawn on the ground after the shaking stops. */
    public static final int AFTER = 20 * 60 * 8;
    /** Crack width (blocks) from which the ground actually opens. */
    public static final float OPEN_WIDTH = 0.55F;

    public final int id;
    public final double x, z;
    public final float magnitude;
    public final long seed;
    /** Direction of the fault, radians. */
    public final float fault;
    public final int duration;
    public int age;
    private List<Crack> cracks;

    /** One crack: a jagged line of points, each with its final width and the tick it opens at. */
    public static final class Crack {
        public final float[] xs, zs, width;
        public final int[] openAt;
        public final int n;

        Crack(List<float[]> pts) {
            n = pts.size();
            xs = new float[n];
            zs = new float[n];
            width = new float[n];
            openAt = new int[n];
            for (int i = 0; i < n; i++) {
                float[] p = pts.get(i);
                xs[i] = p[0];
                zs[i] = p[1];
                width[i] = p[2];
                openAt[i] = (int) p[3];
            }
        }
    }

    public Earthquake(int id, double x, double z, float magnitude, long seed, float fault) {
        this.id = id;
        this.x = x;
        this.z = z;
        this.magnitude = Mth.clamp(magnitude, 1F, 10F);
        this.seed = seed;
        this.fault = fault;
        this.duration = durationFor(this.magnitude);
    }

    /** How long the ground shakes, ticks: a few seconds for a small one, a full minute for the greatest. */
    public static int durationFor(float m) {
        return Math.round(20F * (3F + m * m * 0.56F));
    }

    /** Distance (blocks) at which it is still just felt. */
    public float feltRadius() {
        return 12F * (float) Math.pow(10.0, (1.55 * magnitude - 2.6) / 3.2);
    }

    public boolean shaking() {
        return age < duration;
    }

    /** 0..1: the shaking over time: a quick rise as the waves arrive, the strong part, then dying away. */
    public float envelope() {
        if (age >= duration) {
            return 0F;
        }
        float t = (float) age / duration;
        float rise = Mth.clamp(t / 0.1F, 0F, 1F);
        // A first jolt (the P-wave), then the stronger S-wave.
        float shape = t < 0.1F ? 0.35F + 0.65F * rise * rise : t < 0.55F ? 1F : (float) Math.pow(1F - (t - 0.55F) / 0.45F, 1.6);
        return Mth.clamp(shape, 0F, 1F);
    }

    /** Peak intensity (Mercalli-like, 0..12) at a distance from the epicentre. */
    public float peakIntensity(double dist) {
        float i = 1.55F * magnitude - 3.2F * (float) Math.log10(1.0 + dist / 10.0) - 1.2F;
        return Mth.clamp(i, 0F, 12.5F);
    }

    /** Intensity right now at a point. */
    public float intensityAt(double px, double pz) {
        double dx = px - x, dz = pz - z;
        return peakIntensity(Math.sqrt(dx * dx + dz * dz)) * envelope();
    }

    /** Width a crack point has opened to by now (blocks), 0 before the rupture reaches it. */
    public float widthNow(Crack c, int i) {
        int since = age - c.openAt[i];
        if (since <= 0) {
            return 0F;
        }
        float grow = Mth.clamp(since / (duration * 0.35F + 20F), 0F, 1F);
        return c.width[i] * (0.15F + 0.85F * grow);
    }

    // ------------------------------------------------------------------------------------ the cracks
    public List<Crack> cracks() {
        if (cracks == null) {
            cracks = build();
        }
        return cracks;
    }

    private List<Crack> build() {
        List<Crack> out = new ArrayList<>();
        if (magnitude < 3.2F) {
            return out;
        }
        Random r = new Random(seed);
        float m = magnitude;
        // Main rupture along the fault, out both ways from the epicentre.
        float length = Math.min(240F, 9F * (float) Math.pow(1.62, m - 3.5));
        float wMax = m < 5.2F ? 0.05F + 0.04F * (m - 3.2F) : Math.min(3.4F, 0.62F * (m - 5.25F) + 0.12F);
        int start = Math.round(durationFor(m) * 0.08F);
        float speed = 2.6F;
        List<float[]> main = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            List<float[]> half = walk(r, (float) x, (float) z, fault + (side < 0 ? Mth.PI : 0F), length * 0.5F * (0.8F + 0.4F * r.nextFloat()), wMax,
                    0.9F, start, speed, 0F);
            if (side < 0) {
                for (int i = half.size() - 1; i >= 1; i--) {
                    main.add(half.get(i));
                }
            } else {
                main.addAll(half);
            }
        }
        out.add(new Crack(main));
        // Branches off the main rupture, and smaller branches off those.
        int branches = Math.round((m - 3F) * 2.4F);
        List<List<float[]>> parents = new ArrayList<>();
        parents.add(main);
        for (int b = 0; b < branches; b++) {
            List<float[]> parent = parents.get(r.nextInt(parents.size()));
            if (parent.size() < 4) {
                continue;
            }
            int at = 1 + r.nextInt(parent.size() - 2);
            float[] p = parent.get(at);
            float[] q = parent.get(Math.min(parent.size() - 1, at + 1));
            float dir = (float) Math.atan2(q[1] - p[1], q[0] - p[0]);
            float turn = (0.45F + r.nextFloat() * 0.65F) * (r.nextBoolean() ? 1 : -1);
            List<float[]> br = walk(r, p[0], p[1], dir + turn, length * (0.12F + r.nextFloat() * 0.3F), p[2] * (0.45F + r.nextFloat() * 0.25F),
                    1.15F, (int) p[3], speed * 0.8F, 0F);
            if (br.size() > 2) {
                out.add(new Crack(br));
                if (parents.size() < 6) {
                    parents.add(br);
                }
            }
        }
        // A web of fine cracks in the ground around the epicentre.
        int fine = Math.round(m * m * 0.5F);
        float area = Math.min(70F, 6F + length * 0.35F);
        for (int f = 0; f < fine; f++) {
            float a = r.nextFloat() * Mth.TWO_PI;
            float d = (float) Math.sqrt(r.nextFloat()) * area;
            float px = (float) x + Mth.cos(a) * d, pz = (float) z + Mth.sin(a) * d;
            int open = start + Math.round(d / speed) + r.nextInt(30);
            List<float[]> fc = walk(r, px, pz, r.nextFloat() * Mth.TWO_PI, 2.5F + r.nextFloat() * (4F + m), Math.min(0.42F, 0.05F + 0.035F * m),
                    1.6F, open, speed, 0F);
            if (fc.size() > 2) {
                out.add(new Crack(fc));
            }
        }
        return out;
    }

    /**
     * A jagged crack from (x0, z0) heading `dir`: short steps whose direction wanders but is pulled back to the
     * heading, narrowing to a point at the end. Each point: x, z, width, tick it opens.
     */
    private static List<float[]> walk(Random r, float x0, float z0, float dir, float length, float width, float rough, int open0, float speed,
                                      float dist0) {
        List<float[]> pts = new ArrayList<>();
        float step = 0.9F;
        int n = Math.max(2, Math.round(length / step));
        float a = dir, px = x0, pz = z0;
        float wobble = 0F;
        for (int i = 0; i <= n; i++) {
            float t = (float) i / n;
            float taper = (float) Math.pow(1F - t, 0.7) * (0.75F + 0.5F * r.nextFloat());
            float w = width * Math.max(0.04F, taper);
            pts.add(new float[]{px, pz, w, open0 + (dist0 + i * step) / speed});
            wobble = wobble * 0.7F + (r.nextFloat() - 0.5F) * rough;
            a += wobble * 0.5F + (dir - a) * 0.18F;
            px += Mth.cos(a) * step;
            pz += Mth.sin(a) * step;
        }
        return pts;
    }
}
