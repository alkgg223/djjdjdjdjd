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
    private static final int[] WALL = {8, 14, 24, 36};
    private static final int[] DUST = {6, 12, 22, 36};

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

    private static final int[] SEGMENTS = {14, 22, 32, 44};
    private static final int[] RINGS = {16, 26, 40, 56};

    /**
     * The funnel as a real 3D surface: a tube round the vortex axis (following its bends), lit by the sun on one
     * side and shaded on the other, its silhouette soft where the surface turns away from you, with a second
     * looser shell of spray and dust round the lower part. Streaks in the texture turn with the wind.
     */
    private static void funnel(ClientTornadoes.T t, double tx, double tz, Vec3 cam, float time, float pt, int q, float height,
                               float lr, float lg, float lb, float sunSide) {
        int n = SEGMENTS[q];
        int m = RINGS[q];
        float gap = t.rating == 0 ? 0.3F : t.rating == 1 ? 0.12F : 0F;
        // While the funnel is still coming down, only the part below the cloud is condensed.
        float bottom = Math.max((1F - t.contact) * 0.85F, gap * t.contact);
        float spinTurns = t.spin(pt);
        float rise = time * 0.012F * (1F + t.rating * 0.15F);
        float[] cx = new float[m + 1], cy = new float[m + 1], cz = new float[m + 1], rad = new float[m + 1], fs = new float[m + 1];
        float[][] col = new float[m + 1][3];
        float[] ringAlpha = new float[m + 1];
        float strength = 0.3F + 0.7F * t.strength;
        for (int j = 0; j <= m; j++) {
            float f = bottom + (1F - bottom) * j / m;
            fs[j] = f;
            cx[j] = (float) (tx + t.offsetX(f, time) - cam.x);
            cz[j] = (float) (tz + t.offsetZ(f, time) - cam.z);
            cy[j] = (float) (t.groundY + f * height - cam.y);
            rad[j] = Math.max(0.2F, t.radiusAt(f) * tip(f, bottom));
            float grey = (t.rating >= 4 ? 0.52F : t.rating >= 2 ? 0.62F : 0.7F) + 0.12F * f;
            float dust = t.contact * Mth.clamp(1F - f / (0.08F + t.rating * 0.02F), 0F, 1F);
            col[j][0] = Mth.lerp(dust, grey, 0.55F) * lr;
            col[j][1] = Mth.lerp(dust, grey * 1.01F, 0.46F) * lg;
            col[j][2] = Mth.lerp(dust, grey * 1.04F, 0.36F) * lb;
            ringAlpha[j] = fade(f, bottom) * strength;
        }
        float seed = (t.seed & 1023) * 0.37F;
        float uRepeat = Mth.clamp(rad[m] / 6F, 1F, 5F);
        tube(n, m, cx, cy, cz, rad, col, ringAlpha, 1.0F, 0.06F + 0.02F * Math.min(3, t.rating), 0.95F, spinTurns, uRepeat,
                height / 22F, rise, time, seed, 0.18F);
        if (q >= 1 && t.rating >= 1 && t.contact > 0.2F) {
            // Loose outer shell: spray, rain and debris wrapped round the lower funnel.
            float top = Math.min(1F, 0.25F + t.rating * 0.12F);
            float[] outerA = new float[m + 1];
            float[][] dustCol = new float[m + 1][3];
            for (int j = 0; j <= m; j++) {
                float k = Mth.clamp(1F - (fs[j] - bottom) / Math.max(0.05F, top), 0F, 1F);
                outerA[j] = 0.34F * t.contact * t.strength * k * k;
                dustCol[j][0] = 0.56F * lr;
                dustCol[j][1] = 0.49F * lg;
                dustCol[j][2] = 0.4F * lb;
            }
            tube(Math.max(10, n * 3 / 4), m, cx, cy, cz, rad, dustCol, outerA, 1.32F, 0.16F, 0.7F, spinTurns * 1.4F, uRepeat * 1.3F,
                    height / 14F, rise * 1.6F, time, seed + 3F, 0.3F);
        }
    }

    private static final float[] QX = new float[4], QY = new float[4], QZ = new float[4], QL = new float[4], QA = new float[4];

    /**
     * Skins a tube through the rings. {@code scale} widens the rings, {@code rag} roughens the outline (it boils in
     * time), {@code opacity} is the alpha where the surface faces you.
     */
    static void tube(int n, int m, float[] cx, float[] cy, float[] cz, float[] rad, float[][] col, float[] ringAlpha, float scale, float rag,
                     float opacity, float spinTurns, float uRepeat, float vRepeat, float rise, float time, float seed, float edge) {
        float sx = com.realweather.client.sky.SkyLight.sunX, sy = com.realweather.client.sky.SkyLight.sunY;
        float sz = com.realweather.client.sky.SkyLight.sunZ;
        float day = com.realweather.client.sky.SkyLight.day;
        float[][] px = new float[m + 1][n + 1], py = new float[m + 1][n + 1], pz = new float[m + 1][n + 1];
        float[][] light = new float[m + 1][n + 1], alpha = new float[m + 1][n + 1];
        for (int j = 0; j <= m; j++) {
            float slope = 0F;
            if (j > 0 && j < m) {
                float dy = cy[j + 1] - cy[j - 1];
                slope = Math.abs(dy) > 1.0E-3F ? -(rad[j + 1] - rad[j - 1]) / dy : 0F;
            }
            float fj = (float) j / m;
            for (int i = 0; i <= n; i++) {
                float a = (float) i / n * Mth.TWO_PI;
                float ca = Mth.cos(a), sa = Mth.sin(a);
                // Ragged, boiling outline.
                float noise = 0.55F * Mth.sin(3F * a + 7F * fj + time * 0.05F + seed)
                        + 0.3F * Mth.sin(5F * a - 13F * fj + time * 0.083F + seed * 1.7F)
                        + 0.15F * Mth.sin(11F * a + 23F * fj - time * 0.12F);
                float r = rad[j] * scale * (1F + rag * noise);
                float x = cx[j] + ca * r, y = cy[j], z = cz[j] + sa * r;
                px[j][i] = x;
                py[j][i] = y;
                pz[j][i] = z;
                // Normal of the surface (round, tilted by how fast the radius changes with height).
                float nx = ca, ny = slope, nz = sa;
                float nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= nl;
                ny /= nl;
                nz /= nl;
                float vl = Mth.sqrt(x * x + y * y + z * z) + 1.0E-4F;
                float facing = Math.abs(-(nx * x + ny * y + nz * z) / vl);
                float soft = Mth.clamp((facing - edge * 0.25F) / edge, 0F, 1F);
                alpha[j][i] = opacity * ringAlpha[j] * soft * soft * (3F - 2F * soft);
                float sun = Math.max(0F, nx * sx + ny * sy + nz * sz) * day;
                light[j][i] = 0.62F + 0.5F * sun - 0.12F * (1F - facing);
            }
        }
        float u0base = -spinTurns * uRepeat;
        for (int j = 0; j < m; j++) {
            float v0 = (float) j / m * vRepeat - rise;
            float v1 = (float) (j + 1) / m * vRepeat - rise;
            float r = (col[j][0] + col[j + 1][0]) * 0.5F, g = (col[j][1] + col[j + 1][1]) * 0.5F, b = (col[j][2] + col[j + 1][2]) * 0.5F;
            for (int i = 0; i < n; i++) {
                QX[0] = px[j][i];
                QY[0] = py[j][i];
                QZ[0] = pz[j][i];
                QX[1] = px[j][i + 1];
                QY[1] = py[j][i + 1];
                QZ[1] = pz[j][i + 1];
                QX[2] = px[j + 1][i + 1];
                QY[2] = py[j + 1][i + 1];
                QZ[2] = pz[j + 1][i + 1];
                QX[3] = px[j + 1][i];
                QY[3] = py[j + 1][i];
                QZ[3] = pz[j + 1][i];
                QL[0] = light[j][i];
                QL[1] = light[j][i + 1];
                QL[2] = light[j + 1][i + 1];
                QL[3] = light[j + 1][i];
                QA[0] = alpha[j][i];
                QA[1] = alpha[j][i + 1];
                QA[2] = alpha[j + 1][i + 1];
                QA[3] = alpha[j + 1][i];
                float u0 = u0base + (float) i / n * uRepeat;
                float u1 = u0base + (float) (i + 1) / n * uRepeat;
                SkyBillboards.quad(SkyBillboards.TEX_STREAKS, QX, QY, QZ, u0, v1, u1, v0, r, g, b, QL, QA);
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
        float cloudTop = Math.min(height * 0.22F, 4F + t.rating * 3F);
        for (int i = 0; i < n; i++) {
            float h = rnd.nextFloat();
            float a = rnd.nextFloat() * Mth.TWO_PI - spin * (0.5F + 0.3F * (1F - h));
            float r = rc * (0.95F + rnd.nextFloat() * 0.7F + h * 0.4F);
            float px = gx + Mth.cos(a) * r;
            float pz = gz + Mth.sin(a) * r;
            float w = rc * (0.3F + rnd.nextFloat() * 0.3F) + 2.5F;
            float py = gy + w * 0.3F + h * cloudTop;
            float shade = 0.5F + rnd.nextFloat() * 0.1F;
            float alpha = 0.42F * amount * (1F - h * 0.6F);
            SkyBillboards.cell(SkyBillboards.SPHERE, SkyBillboards.DUST, px, py, pz, w, w * 0.8F, a,
                    lr * shade * 1.05F, lg * shade * 0.92F, lb * shade * 0.75F, alpha, 0.75F, sunSide * 0.5F);
        }
        float ring = rc * 2.4F + 8F;
        SkyBillboards.cell(SkyBillboards.FLAT, SkyBillboards.RING, gx, gy + 0.3F, gz, ring, ring, -spin * 0.3F,
                lr * 0.5F, lg * 0.44F, lb * 0.36F, 0.5F * amount, 1F, 0F);
    }

    /** Suction vortices orbiting inside violent tornadoes: short, dusty 3D tubes. */
    private static void subVortices(ClientTornadoes.T t, double tx, double tz, Vec3 cam, float time, float pt, int q,
                                    float lr, float lg, float lb, float sunSide) {
        int count = q >= 3 ? 5 : 3;
        float rc = t.coreRadius();
        float spin = t.spin(pt);
        float h = 14F + t.rating * 3F;
        int m = 5;
        float[] cx = new float[m + 1], cy = new float[m + 1], cz = new float[m + 1], rad = new float[m + 1], alpha = new float[m + 1];
        float[][] col = new float[m + 1][3];
        for (int k = 0; k < count; k++) {
            float orbit = (float) (-spin * Math.PI * 2 * 1.3F + k * Math.PI * 2 / count);
            float ox = (float) (tx + Mth.cos(orbit) * rc * 0.8F - cam.x);
            float oz = (float) (tz + Mth.sin(orbit) * rc * 0.8F - cam.z);
            for (int j = 0; j <= m; j++) {
                float f = (float) j / m;
                cx[j] = ox + Mth.sin(time * 0.1F + k + f * 3F) * 0.8F;
                cz[j] = oz + Mth.cos(time * 0.1F + k + f * 3F) * 0.8F;
                cy[j] = (float) (t.groundY + f * h - cam.y);
                rad[j] = (rc * 0.12F + 1F) * (0.7F + f);
                alpha[j] = 0.55F * (1F - f) * t.contact;
                col[j][0] = 0.48F * lr;
                col[j][1] = 0.42F * lg;
                col[j][2] = 0.34F * lb;
            }
            tube(10, m, cx, cy, cz, rad, col, alpha, 1F, 0.12F, 0.9F, spin * 4F, 1F, 0.6F, time * 0.04F, time, k * 2.3F, 0.3F);
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
