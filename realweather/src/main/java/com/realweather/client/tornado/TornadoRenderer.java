package com.realweather.client.tornado;

import com.realweather.client.ClientStorms;
import com.realweather.client.sky.SkyBillboards;
import com.realweather.client.sky.StormClouds;
import com.realweather.server.Tornado;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Each tornado as soft, sorted sprites that blend with the storm clouds: a smooth condensation funnel built from
 * many thin horizontal slices (round-shaded, lit on the sun side, with spiral streaks that turn with the wind), the
 * lowered wall cloud it hangs from, a rotating skirt of dust and debris around the base, an outer debris sheath
 * on strong tornadoes and orbiting suction vortices on violent ones. Ropes for F0/F1, cones for F2/F3, wide
 * wedges for F4/F5. Detail follows the tornado graphics setting (LOW, MEDIUM, HIGH, ULTRA).
 */
public final class TornadoRenderer {
    private static final float[] MAX_DIST = {420F, 640F, 900F, 1300F};
    private static final int[] SLICES = {22, 38, 60, 90};
    private static final int[] WALL = {8, 14, 24, 36};
    private static final int[] DUST = {6, 12, 22, 36};
    private static final int[] SHEATH = {0, 10, 18, 30};

    /** Tornadoes drawn in the last frame (diagnostics). */
    public static int lastDrawn;

    private TornadoRenderer() {}

    public static void collect(ClientLevel level, Vec3 cam, float pt, int q, float lr, float lg, float lb, float sunSide) {
        lastDrawn = 0;
        float deck = StormClouds.deckY(level);
        for (ClientTornadoes.T t : ClientTornadoes.all()) {
            double tx = t.x(pt);
            double tz = t.z(pt);
            double dist = Math.sqrt((tx - cam.x) * (tx - cam.x) + (tz - cam.z) * (tz - cam.z));
            if (dist > MAX_DIST[q] || t.strength <= 0.01F) {
                continue;
            }
            lastDrawn++;
            float time = t.age + pt;
            float height = Mth.clamp(deck - 6F - t.groundY, 24F, t.height());
            RandomSource rnd = RandomSource.create(t.seed);
            wallCloud(t, tx, tz, cam, time, pt, q, height, deck, lr, lg, lb, sunSide, rnd);
            funnel(t, tx, tz, cam, time, pt, q, height, lr, lg, lb, sunSide);
            if (t.contact > 0.05F) {
                dust(t, tx, tz, cam, time, pt, q, height, lr, lg, lb, sunSide, rnd);
            }
            if (q >= 2 && t.rating >= 4 && t.contact > 0.8F && t.strength > 0.6F) {
                subVortices(t, tx, tz, cam, time, pt, q, lr, lg, lb, sunSide);
            }
        }
    }

    private static void funnel(ClientTornadoes.T t, double tx, double tz, Vec3 cam, float time, float pt, int q, float height,
                               float lr, float lg, float lb, float sunSide) {
        int n = SLICES[q];
        float gap = t.rating == 0 ? 0.3F : t.rating == 1 ? 0.12F : 0F;
        // While the funnel is still coming down, only the part below the cloud is condensed.
        float bottom = Math.max((1F - t.contact) * 0.85F, gap * t.contact);
        float spin = t.spin(pt);
        float rise = time * 0.012F * (1F + t.rating * 0.15F);
        float step = (1F - bottom) / n;
        float sliceH = height * step * 1.35F;
        float baseAlpha = (0.55F + 0.12F * Math.min(3, t.rating)) * (0.3F + 0.7F * t.strength);
        for (int j = 0; j <= n; j++) {
            float f = bottom + step * j;
            float rad = t.radiusAt(f) * tip(f, bottom);
            if (rad < 0.15F) {
                continue;
            }
            float px = (float) (tx + t.offsetX(f, time) - cam.x);
            float pz = (float) (tz + t.offsetZ(f, time) - cam.z);
            float py = (float) (t.groundY + f * height - cam.y);
            float alpha = baseAlpha * fade(f, bottom);
            // Condensation is pale grey; near the ground it is full of brown dust.
            float grey = 0.66F + 0.1F * f;
            float dust = t.contact * Mth.clamp(1F - f / (0.16F + t.rating * 0.035F), 0F, 1F);
            float r = Mth.lerp(dust, grey, 0.52F) * lr;
            float g = Mth.lerp(dust, grey * 1.01F, 0.44F) * lg;
            float b = Mth.lerp(dust, grey * 1.04F, 0.35F) * lb;
            // The streak texture wraps around the funnel: wider funnels show more streaks, all turning together.
            float uScale = Mth.clamp(rad / 10F, 0.35F, 2.2F);
            float u0 = spin * 1.6F + f * 0.35F;
            float v0 = f * height / 26F - rise;
            float v1 = v0 + sliceH / 26F;
            SkyBillboards.add(SkyBillboards.TEX_STREAKS, SkyBillboards.FUNNEL, px, py, pz, rad, sliceH * 0.5F, 0F,
                    u0, v0, u0 + uScale, v1, r, g, b, alpha, 0.94F, sunSide);
        }
        // A fainter, wider sheath of debris and spray on strong tornadoes.
        if (t.rating >= 2 && t.contact > 0.3F && SHEATH[q] > 0) {
            int m = SHEATH[q];
            float top = 0.18F + t.rating * 0.06F;
            for (int j = 0; j < m; j++) {
                float f = top * j / (m - 1F);
                float rad = t.radiusAt(f) * (1.35F + 0.25F * f / top) + 1.5F;
                float px = (float) (tx + t.offsetX(f, time) - cam.x);
                float pz = (float) (tz + t.offsetZ(f, time) - cam.z);
                float py = (float) (t.groundY + f * height - cam.y);
                float a = 0.28F * t.contact * t.strength * (1F - f / top);
                float u0 = spin * 2.4F;
                float sh = top * height / m * 1.6F;
                SkyBillboards.add(SkyBillboards.TEX_STREAKS, SkyBillboards.FUNNEL, px, py, pz, rad, sh * 0.5F, 0F,
                        u0, f * 2F - rise * 1.5F, u0 + 1.5F, f * 2F - rise * 1.5F + 0.3F,
                        0.5F * lr, 0.43F * lg, 0.35F * lb, a, 0.9F, sunSide * 0.6F);
            }
        }
    }

    /** Narrows the funnel to a point while it is still aloft. */
    private static float tip(float f, float bottom) {
        if (bottom <= 0.001F) {
            return 1F;
        }
        float t = Mth.clamp((f - bottom) / 0.15F, 0F, 1F);
        return 0.12F + 0.88F * t * t * (3F - 2F * t);
    }

    private static float fade(float f, float bottom) {
        float top = f > 0.9F ? (1F - f) / 0.1F * 0.6F + 0.4F : 1F;
        float low = bottom > 0.001F ? Mth.clamp((f - bottom) / 0.06F, 0F, 1F) : 1F;
        return Mth.clamp(top, 0F, 1F) * low;
    }

    /** The dark, lowered and rotating wall cloud right above the funnel, rising into the storm base. */
    private static void wallCloud(ClientTornadoes.T t, double tx, double tz, Vec3 cam, float time, float pt, int q, float height,
                                  float deck, float lr, float lg, float lb, float sunSide, RandomSource rnd) {
        int n = WALL[q];
        float rc = Math.max(2F, t.coreRadius());
        float spin = t.spin(pt) * Mth.TWO_PI * 0.08F;
        float cx = (float) (tx + t.offsetX(1F, time));
        float cz = (float) (tz + t.offsetZ(1F, time));
        float base = t.groundY + height;
        float wallR = rc * 2.4F + 22F + t.rating * 4F;
        float fade = 0.35F + 0.65F * t.strength;
        for (int i = 0; i < n; i++) {
            float a = rnd.nextFloat() * Mth.TWO_PI - spin * (0.8F + rnd.nextFloat() * 0.4F);
            float r = (0.25F + 0.75F * Mth.sqrt(rnd.nextFloat())) * wallR;
            float px = cx + Mth.cos(a) * r - (float) cam.x;
            float pz = cz + Mth.sin(a) * r - (float) cam.z;
            // Lowest in the middle, stepping up toward the storm base at the edges.
            float k = r / wallR;
            float py = Mth.lerp(k * k, base - 2F, deck - 1F) + rnd.nextFloat() * 3F - (float) cam.y;
            float w = wallR * (0.32F + rnd.nextFloat() * 0.22F);
            float shade = 0.34F + 0.08F * k + rnd.nextFloat() * 0.05F;
            SkyBillboards.cell(SkyBillboards.SPHERE, SkyBillboards.WALL + (i & 1), px, py, pz, w, w * 0.5F, a,
                    lr * shade, lg * shade, lb * shade * 1.04F, 0.92F * fade, 0.72F, sunSide * 0.4F);
        }
        // The collar where the funnel meets the cloud.
        int collar = Math.max(3, n / 4);
        for (int i = 0; i < collar; i++) {
            float a = (float) i / collar * Mth.TWO_PI - spin * 1.6F;
            float r = t.radiusAt(1F) * 0.9F + 3F;
            float px = cx + Mth.cos(a) * r - (float) cam.x;
            float pz = cz + Mth.sin(a) * r - (float) cam.z;
            float w = t.radiusAt(1F) * 0.9F + 6F;
            float shade = 0.4F;
            SkyBillboards.cell(SkyBillboards.SPHERE, SkyBillboards.SCUD + (i & 1), px, base - 2F - (float) cam.y, pz, w, w * 0.45F, a,
                    lr * shade, lg * shade, lb * shade * 1.04F, 0.85F * fade, 0.75F, sunSide * 0.5F);
        }
        // Inflow scud streaming into the wall cloud (the parent supercell, if any, supplies its own).
        ClientStorms.Cell parent = StormClouds.parentOf(t);
        if (parent == null && q >= 1) {
            float hx = Mth.sin(t.heading);
            float hz = -Mth.cos(t.heading);
            StormClouds.tail(cam, q, time, deck, 220F + t.rating * 25F, cx, cz, hx, hz, -hz, hx, 0.42F, fade, rnd);
        }
    }

    /** Rotating skirt of dust and debris around the base, and a dusty ring spreading on the ground. */
    private static void dust(ClientTornadoes.T t, double tx, double tz, Vec3 cam, float time, float pt, int q, float height,
                             float lr, float lg, float lb, float sunSide, RandomSource rnd) {
        int n = Math.round(DUST[q] * (0.6F + t.rating * 0.18F));
        float rc = Math.max(2F, t.coreRadius());
        float spin = t.spin(pt) * Mth.TWO_PI;
        float amount = t.contact * (0.35F + 0.65F * t.strength);
        float gx = (float) (tx - cam.x);
        float gz = (float) (tz - cam.z);
        float gy = t.groundY - (float) cam.y;
        float cloudTop = Math.min(height * 0.4F, 6F + t.rating * 6F);
        for (int i = 0; i < n; i++) {
            float h = rnd.nextFloat();
            float a = rnd.nextFloat() * Mth.TWO_PI - spin * (0.5F + 0.3F * (1F - h));
            float r = rc * (0.8F + rnd.nextFloat() * 1.3F + h * 0.6F);
            float px = gx + Mth.cos(a) * r;
            float pz = gz + Mth.sin(a) * r;
            float w = rc * (0.55F + rnd.nextFloat() * 0.5F) + 3F;
            float py = gy + w * 0.35F + h * cloudTop;
            float shade = 0.52F + rnd.nextFloat() * 0.1F;
            float alpha = 0.6F * amount * (1F - h * 0.5F);
            SkyBillboards.cell(SkyBillboards.SPHERE, SkyBillboards.DUST, px, py, pz, w, w * 0.8F, a,
                    lr * shade * 1.05F, lg * shade * 0.92F, lb * shade * 0.75F, alpha, 0.75F, sunSide * 0.5F);
        }
        float ring = rc * 3.2F + 10F;
        SkyBillboards.cell(SkyBillboards.FLAT, SkyBillboards.RING, gx, gy + 0.3F, gz, ring, ring, -spin * 0.3F,
                lr * 0.5F, lg * 0.44F, lb * 0.36F, 0.5F * amount, 1F, 0F);
    }

    /** Suction vortices orbiting inside violent tornadoes. */
    private static void subVortices(ClientTornadoes.T t, double tx, double tz, Vec3 cam, float time, float pt, int q,
                                    float lr, float lg, float lb, float sunSide) {
        int count = q >= 3 ? 5 : 3;
        float rc = t.coreRadius();
        float spin = t.spin(pt);
        float h = 14F + t.rating * 3F;
        int m = 6;
        for (int k = 0; k < count; k++) {
            float orbit = (float) (-spin * Math.PI * 2 * 1.3F + k * Math.PI * 2 / count);
            float ox = (float) (tx + Mth.cos(orbit) * rc * 0.8F - cam.x);
            float oz = (float) (tz + Mth.sin(orbit) * rc * 0.8F - cam.z);
            float rs = rc * 0.12F + 1F;
            for (int j = 0; j < m; j++) {
                float f = (j + 0.5F) / m;
                float py = (float) (t.groundY + f * h - cam.y);
                float a = 0.45F * (1F - f) * t.contact;
                SkyBillboards.add(SkyBillboards.TEX_STREAKS, SkyBillboards.FUNNEL, ox, py, oz, rs * (0.7F + f), h / m * 0.8F, 0F,
                        spin * 5F, f - time * 0.04F, spin * 5F + 0.5F, f - time * 0.04F + 0.2F,
                        0.46F * lr, 0.4F * lg, 0.33F * lb, a, 0.9F, sunSide * 0.5F);
            }
        }
    }

    /** How much tornado dust surrounds a point (0..1): the debris cloud near the base. */
    public static float dustAt(double x, double y, double z) {
        float best = 0F;
        for (ClientTornadoes.T t : ClientTornadoes.all()) {
            float rc = Math.max(2F, t.coreRadius());
            double d = Math.sqrt((x - t.x) * (x - t.x) + (z - t.z) * (z - t.z));
            double above = y - t.groundY;
            if (above > 40 + t.rating * 10) {
                continue;
            }
            float f = (float) Mth.clamp(1.0 - (d - rc) / (rc * 3 + 15), 0.0, 1.0) * t.contact * (0.4F + 0.6F * t.strength)
                    * (0.5F + Tornado.RADIUS[t.rating] / 54F);
            best = Math.max(best, f);
        }
        return Math.min(1F, best);
    }
}
