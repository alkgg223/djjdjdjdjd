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
    private static final int CELL = 64;
    private static final float[] RADIUS = {450F, 650F, 900F, 1200F};
    /** Puffs per cloudy cell. Each one overlaps the next cells, so neighbouring cloud joins into one mass. */
    private static final int[] PUFFS = {1, 2, 3, 5};

    private record Puff(float dx, float dz, float size, int cell, float rot, float lift) {}

    private static final Long2ObjectOpenHashMap<Puff[]> LAYOUT = new Long2ObjectOpenHashMap<>();
    /** Cloudiness of each grid cell (it never changes, so it is worked out once). */
    private static final it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap FIELD = new it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap();
    static double driftX, driftZ;

    private CloudField() {}

    static float radius(int q) {
        return RADIUS[q];
    }

    static void tick() {
        // Upper winds move the clouds a few metres per second.
        driftX += ClientWeather.windX * 0.25 + 0.03;
        driftZ += ClientWeather.windZ * 0.25 + 0.01;
        if (LAYOUT.size() > 8000) {
            LAYOUT.clear();
        }
        if (FIELD.size() > 30000) {
            FIELD.clear();
        }
    }

    static void collect(ClientLevel level, Vec3 cam, float pt, int q, float rainLevel) {
        float radius = RADIUS[q];
        int maxPuffs = PUFFS[q];
        long dayIndex = Math.floorDiv(level.getDayTime(), 24000L);
        // Even fair days carry a good deal of cumulus that clumps into large masses with gaps of blue between.
        float clear = 0.45F + 0.17F * (hash(dayIndex, 11) * 0.5F + 0.5F);
        // Fewer cumulus on a day of high cloud, more on a cumulus or stratocumulus day.
        clear *= 0.35F + 0.65F * Mth.clamp(CloudTypes.cumulus + CloudTypes.stratocumulus + CloudTypes.stratus * 0.5F, 0F, 1F);
        float cover = Mth.lerp(Mth.clamp(rainLevel * 1.3F, 0F, 1F), clear, 0.98F);
        float threshold = 0.5F + (0.5F - cover) * 0.5F;
        float overcast = Mth.clamp((rainLevel - 0.15F) / 0.55F, 0F, 1F);
        float base = Mth.lerp(overcast, 176F, 152F);
        double ox = cam.x - driftX;
        double oz = cam.z - driftZ;
        int gx0 = Mth.floor((ox - radius) / CELL);
        int gx1 = Mth.floor((ox + radius) / CELL);
        int gz0 = Mth.floor((oz - radius) / CELL);
        int gz1 = Mth.floor((oz + radius) / CELL);
        float grey = 1F - 0.4F * overcast;
        float puffAlpha = 1F - 0.5F * overcast;
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
                // How deep inside a mass this cell is: the middle of a big cloud grows tall, its edges stay low.
                float s = Mth.clamp((n - threshold) / 0.16F, 0F, 1F);
                int around = 0;
                around += field(gx + 1, gz) >= threshold ? 1 : 0;
                around += field(gx - 1, gz) >= threshold ? 1 : 0;
                around += field(gx, gz + 1) >= threshold ? 1 : 0;
                around += field(gx, gz - 1) >= threshold ? 1 : 0;
                float inside = around / 4F;
                float fade = 1F - smooth(radius * 0.7F, radius, dist);
                // A continuous grey-white underside joins the cells of a mass when seen from below.
                float under = (0.76F + 0.1F * hash(gx * 31L + gz, 5) - 0.14F * overcast) * grey * 1.1F;
                SkyBillboards.cell(SkyBillboards.FLAT, SkyBillboards.BASE + ((gx + gz) & 1), (float) dx, base + 1F - (float) cam.y, (float) dz,
                        CELL * (0.62F + 0.2F * inside), CELL * (0.62F + 0.2F * inside), (gx * 7 + gz * 13) * 0.7F,
                        SkyLight.r * under, SkyLight.g * under, SkyLight.b * under * 1.03F, (0.55F + 0.4F * overcast) * fade * (0.5F + 0.5F * inside),
                        1F, 0F);
                long key = (long) gx << 32 ^ (gz & 0xFFFFFFFFL);
                Puff[] puffs = LAYOUT.computeIfAbsent(key, k -> layout(k));
                float height = (8F + 40F * s * (0.4F + 0.6F * inside)) * (1F - 0.7F * overcast);
                int use = Math.min(maxPuffs, puffs.length);
                for (int i = 0; i < use; i++) {
                    Puff p = puffs[i];
                    float size = CELL * p.size * (q == 0 ? 1.25F : q == 1 ? 1.05F : 1F) * (0.75F + 0.35F * inside + 0.25F * s) * (1F + 0.3F * overcast);
                    float px = (float) (cx + p.dx * CELL - cam.x);
                    float pz = (float) (cz + p.dz * CELL - cam.z);
                    float py = base + size * 0.42F + p.lift * height - (float) cam.y;
                    float h01 = Mth.clamp(p.lift * (0.5F + s), 0F, 1F);
                    float shade = (0.92F + 0.16F * h01 + SkyLight.silver(p.dx, h01 - 0.3F, p.dz)) * grey;
                    boolean flat = overcast > 0.5F && i % 2 == 0;
                    SkyBillboards.cell(SkyBillboards.SPHERE, flat ? SkyBillboards.STRATUS : p.cell, px, py, pz, size, size * (flat ? 0.45F : 0.8F),
                            flat ? 0F : p.rot, SkyLight.r * shade, SkyLight.g * shade, SkyLight.b * shade * 1.02F, 0.95F * fade * puffAlpha, 0.72F,
                            SkyLight.sunSide);
                }
            }
        }
    }

    private static Puff[] layout(long key) {
        RandomSource rnd = RandomSource.create(key * 0x9E3779B97F4A7C15L);
        Puff[] puffs = new Puff[5];
        // Spread over the whole cell (and a little past it) so neighbouring cells overlap.
        float[][] spots = {{0F, 0F}, {-0.3F, -0.25F}, {0.3F, 0.25F}, {0.28F, -0.3F}, {-0.28F, 0.3F}};
        for (int i = 0; i < puffs.length; i++) {
            float jx = spots[i][0] + (rnd.nextFloat() - 0.5F) * 0.3F;
            float jz = spots[i][1] + (rnd.nextFloat() - 0.5F) * 0.3F;
            float size = i == 0 ? 0.78F + rnd.nextFloat() * 0.2F : 0.52F + rnd.nextFloat() * 0.22F;
            float lift = i == 0 ? 0.15F : rnd.nextFloat();
            puffs[i] = new Puff(jx, jz, size, rnd.nextInt(6), (rnd.nextFloat() - 0.5F) * 0.4F, lift);
        }
        return puffs;
    }

    /** Smooth cloudiness 0..1 over the grid. */
    private static float field(int gx, int gz) {
        long key = (long) gx << 32 ^ (gz & 0xFFFFFFFFL);
        float v = FIELD.getOrDefault(key, -1F);
        if (v < 0F) {
            v = computeField(gx, gz);
            FIELD.put(key, v);
        }
        return v;
    }

    private static float computeField(int gx, int gz) {
        return Mth.clamp(0.55F * noise(gx / 6.0, gz / 6.0, 1) + 0.3F * noise(gx / 2.6, gz / 2.6, 2) + 0.15F * noise(gx / 1.2, gz / 1.2, 3), 0F, 1F);
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
