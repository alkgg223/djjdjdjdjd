package com.realweather.client.sky;

import com.realweather.client.ClientWeather;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The everyday sky: scattered cumulus on fair days, building and merging into a grey overcast deck as the rain
 * comes. Clouds are anchored to the moving air, so they drift across the sky with the wind.
 */
final class CloudField {
    private static final int CELL = 72;
    private static final float[] RADIUS = {520F, 760F, 1050F, 1400F};
    private static final int[] PUFFS = {3, 5, 7, 10};

    private record Puff(float dx, float dy, float dz, float size, int cell, float rot, float shade) {}

    private static final Long2ObjectOpenHashMap<Puff[]> LAYOUT = new Long2ObjectOpenHashMap<>();
    static double driftX, driftZ;

    private CloudField() {}

    static float radius(int q) {
        return RADIUS[q];
    }

    static void tick() {
        // Upper winds move the clouds a few metres per second.
        driftX += ClientWeather.windX * 0.25 + 0.03;
        driftZ += ClientWeather.windZ * 0.25 + 0.01;
        if (LAYOUT.size() > 6000) {
            LAYOUT.clear();
        }
    }

    static void collect(ClientLevel level, Vec3 cam, float pt, int q, float rainLevel) {
        float radius = RADIUS[q];
        int maxPuffs = PUFFS[q];
        long dayIndex = Math.floorDiv(level.getDayTime(), 24000L);
        float clear = 0.22F + 0.2F * (hash(dayIndex, 11) * 0.5F + 0.5F);
        float cover = Mth.lerp(rainLevel, clear, 0.95F);
        float threshold = 1F - cover;
        float overcast = Mth.clamp((rainLevel - 0.2F) / 0.6F, 0F, 1F);
        float base = Mth.lerp(overcast, 168F, 150F);
        double ox = cam.x - driftX;
        double oz = cam.z - driftZ;
        int gx0 = Mth.floor((ox - radius) / CELL);
        int gx1 = Mth.floor((ox + radius) / CELL);
        int gz0 = Mth.floor((oz - radius) / CELL);
        int gz1 = Mth.floor((oz + radius) / CELL);
        float grey = 1F - 0.45F * overcast;
        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                double cx = (gx + 0.5) * CELL + driftX;
                double cz = (gz + 0.5) * CELL + driftZ;
                double dx = cx - cam.x;
                double dz = cz - cam.z;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) {
                    continue;
                }
                float n = field(gx, gz);
                if (n < threshold) {
                    continue;
                }
                float s = Mth.clamp((n - threshold) / Math.max(0.05F, cover), 0F, 1F);
                float fade = 1F - smooth(radius * 0.7F, radius, dist);
                long key = (long) gx << 32 ^ (gz & 0xFFFFFFFFL);
                Puff[] puffs = LAYOUT.computeIfAbsent(key, k -> layout(k));
                float scale = 0.55F + 0.9F * s;
                int use = Math.min(maxPuffs, puffs.length);
                for (int i = 0; i < use; i++) {
                    Puff p = puffs[i];
                    float px = (float) (cx + p.dx * scale - cam.x);
                    float py = (float) (base + p.dy * scale * (0.6F + 0.6F * s) * (1F - 0.6F * overcast) - cam.y);
                    float pz = (float) (cz + p.dz * scale - cam.z);
                    float size = p.size * scale * (1F + 0.5F * overcast);
                    float shade = (0.62F + 0.4F * p.shade + SkyLight.silver(p.dx, p.dy, p.dz)) * grey;
                    int cell = overcast > 0.5F && i % 2 == 0 ? SkyBillboards.STRATUS + (i & 1) : p.cell;
                    SkyBillboards.cell(SkyBillboards.SPHERE, cell, px, py, pz, size, size * (overcast > 0.5F ? 0.6F : 0.8F), p.rot,
                            SkyLight.r * shade, SkyLight.g * shade, SkyLight.b * shade * 1.02F, 0.9F * fade, 0.72F, SkyLight.sunSide);
                }
            }
        }
    }

    private static Puff[] layout(long key) {
        RandomSource rnd = RandomSource.create(key * 0x9E3779B97F4A7C15L);
        Puff[] puffs = new Puff[10];
        float jx = (rnd.nextFloat() - 0.5F) * CELL * 0.6F;
        float jz = (rnd.nextFloat() - 0.5F) * CELL * 0.6F;
        // A broad base first, then smaller turrets on top.
        puffs[0] = new Puff(jx, 6F, jz, 30F + rnd.nextFloat() * 10F, rnd.nextInt(6), rnd.nextFloat() * 6.28F, 0.25F);
        for (int i = 1; i < puffs.length; i++) {
            float a = rnd.nextFloat() * Mth.TWO_PI;
            float rr = (float) Math.sqrt(rnd.nextFloat()) * 30F;
            float h = (1F - rr / 34F) * (8F + rnd.nextFloat() * 26F);
            puffs[i] = new Puff(jx + Mth.cos(a) * rr, h, jz + Mth.sin(a) * rr, 14F + rnd.nextFloat() * 16F, rnd.nextInt(6),
                    rnd.nextFloat() * 6.28F, Mth.clamp(h / 30F, 0F, 1F));
        }
        return puffs;
    }

    /** Smooth cloudiness 0..1 over the grid. */
    private static float field(int gx, int gz) {
        return Mth.clamp(0.62F * noise(gx / 5.0, gz / 5.0, 1) + 0.38F * noise(gx / 1.8, gz / 1.8, 2), 0F, 1F);
    }

    private static float noise(double x, double z, int salt) {
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        float fx = (float) (x - ix);
        float fz = (float) (z - iz);
        float sx = fx * fx * (3 - 2 * fx);
        float sz = fz * fz * (3 - 2 * fz);
        float a = hash2(ix, iz, salt), b = hash2(ix + 1, iz, salt), c = hash2(ix, iz + 1, salt), d = hash2(ix + 1, iz + 1, salt);
        return Mth.lerp(sz, Mth.lerp(sx, a, b), Mth.lerp(sx, c, d)) * 0.5F + 0.5F;
    }

    private static float hash2(int x, int z, int salt) {
        return hash((long) x * 73856093L ^ (long) z * 19349663L, salt);
    }

    static float hash(long v, int salt) {
        long h = v * 0x9E3779B97F4A7C15L + salt * 0x632BE59BD9B4E019L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return ((h >>> 40) / (float) (1L << 24)) * 2F - 1F;
    }

    static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }
}
