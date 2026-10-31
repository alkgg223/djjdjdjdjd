package com.realweather.client.radar;

import com.realweather.client.ClientStorms;
import com.realweather.client.ClientWeather;
import com.realweather.client.tornado.ClientTornadoes;
import com.realweather.server.StormCell;
import com.realweather.server.StormField;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * What a dual-polarisation Doppler radar sees, worked out from the storm cells and tornadoes:
 * reflectivity (dBZ), radial velocity (m/s, + away from the radar), differential reflectivity ZDR (dB) and
 * correlation coefficient CC. Hail cores show as very high reflectivity with low ZDR and lowered CC; tornado
 * debris gives the classic debris ball: high reflectivity, ZDR near zero and very low CC (a TDS), inside a tight
 * couplet of inbound and outbound velocities.
 */
public final class RadarProducts {
    public static final int REF = 0, VEL = 1, ZDR = 2, CC = 3;
    public static final int NONE = 0;

    /** One sample of all four products at a point. */
    public static final class Sample {
        public float dbz;
        public float vel;
        public float zdr;
        public float cc;
        public boolean echo;
    }

    private RadarProducts() {}

    /** Metres per second per block per tick. */
    private static final float MPS = 20F;

    public static void sample(ClientLevel level, double x, double z, double ox, double oz, float time, Sample out) {
        float rainLevel = level.getRainLevel(1F);
        float rain;
        float hail;
        if (ClientStorms.synced) {
            rain = ClientStorms.rainAt(x, z) * Math.max(0.15F, rainLevel);
            hail = ClientStorms.hailAt(x, z) * rainLevel;
        } else {
            rain = rainLevel * ClientWeather.rain * (0.55F + 0.45F * noise(x / 90.0, z / 90.0 + time * 0.0007, 3));
            hail = 0F;
        }
        float debris = 0F;
        float tvx = 0F;
        float tvz = 0F;
        for (ClientTornadoes.T t : ClientTornadoes.all()) {
            double dx = x - t.x;
            double dz = z - t.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            float rc = Math.max(2F, t.coreRadius());
            float lofted = t.contact * t.strength;
            debris = Math.max(debris, lofted * (float) Math.exp(-Math.pow(d / (rc * 1.7 + 5 + t.rating * 3), 2)) * (0.45F + t.rating * 0.11F));
            // Rankine vortex, counter-clockwise seen from above.
            float peak = peakWind(t);
            float v = (float) (d < rc ? peak * d / rc : peak * Math.pow(rc / d, 0.7));
            if (d > 0.5) {
                tvx += (float) (dz / d) * v;
                tvz += (float) (-dx / d) * v;
            }
            // The wrapping rain around the tornado.
            rain = Math.max(rain, 0.35F * lofted * (float) Math.exp(-Math.pow(d / (rc * 6 + 40), 2)));
        }
        // Speckle and texture, slowly changing.
        float n = noise(x / 34.0, z / 34.0 + time * 0.0011, 1) * 0.6F + noise(x / 11.0, z / 11.0 - time * 0.0016, 2) * 0.4F;
        float dbz = rain > 0.012F ? 8F + 48F * (float) Math.pow(rain, 0.6) + (n - 0.5F) * 7F : 0F;
        if (hail > 0.04F) {
            dbz = Math.max(dbz, 52F + 20F * hail + (n - 0.5F) * 4F);
        }
        if (debris > 0.06F) {
            dbz = Math.max(dbz, 45F + 25F * debris + (n - 0.5F) * 6F);
        }
        out.echo = dbz >= 10F;
        out.dbz = dbz;
        if (!out.echo) {
            out.vel = 0F;
            out.zdr = 0F;
            out.cc = 0F;
            return;
        }
        // Velocity: environment wind, storm motion inside cells, mesocyclones and tornadoes.
        float vx = ClientWeather.windX * 24F;
        float vz = ClientWeather.windZ * 24F;
        for (ClientStorms.Cell c : ClientStorms.cells()) {
            float w = Math.min(1F, StormField.rain(c, x, z) * 2F);
            if (w <= 0.01F) {
                continue;
            }
            vx = Mth.lerp(w, vx, c.vx * MPS + ClientWeather.windX * 10F);
            vz = Mth.lerp(w, vz, c.vz * MPS + ClientWeather.windZ * 10F);
            if (c.type == StormCell.SUPERCELL) {
                double[] u = StormField.updraft(c);
                double dx = x - u[0];
                double dz = z - u[1];
                double d = Math.sqrt(dx * dx + dz * dz);
                float rc = c.radius * 0.1F;
                float vmax = 26F * c.cstrength();
                float vt = (float) (d < rc ? vmax * d / rc : vmax * Math.pow(rc / d, 0.8));
                if (d > 0.5) {
                    vx += (float) (dz / d) * vt;
                    vz += (float) (-dx / d) * vt;
                }
            }
        }
        vx += tvx;
        vz += tvz;
        double rx = x - ox;
        double rz = z - oz;
        double range = Math.sqrt(rx * rx + rz * rz);
        out.vel = range < 0.5 ? 0F : (float) ((vx * rx + vz * rz) / range) + (n - 0.5F) * 2.5F;

        // Dual-pol: big flattened drops in heavy rain (high ZDR), tumbling hail (ZDR ~ 0, CC lower), debris (CC very low).
        float zdr = 0.3F + 3.2F * (float) Math.pow(Math.min(1F, rain), 1.3) + (n - 0.5F) * 0.6F;
        float cc = 0.985F - 0.03F * (1F - Math.min(1F, rain * 3F)) + (n - 0.5F) * 0.02F;
        if (hail > 0.04F) {
            zdr = Mth.lerp(Math.min(1F, hail * 1.5F), zdr, -0.3F + n * 0.6F);
            cc = Mth.lerp(Math.min(1F, hail * 1.5F), cc, 0.88F + n * 0.05F);
        }
        if (debris > 0.06F) {
            float k = Math.min(1F, debris * 1.6F);
            zdr = Mth.lerp(k, zdr, -1F + n * 2.2F);
            cc = Mth.lerp(k, cc, 0.25F + n * 0.45F);
        }
        out.zdr = zdr;
        out.cc = Mth.clamp(cc, 0.05F, 1F);
    }

    /** ARGB colour of a product value, or 0 for no echo. */
    public static int color(int product, Sample s) {
        if (!s.echo) {
            return NONE;
        }
        return switch (product) {
            case VEL -> velColor(s.vel);
            case ZDR -> lerpTable(ZDR_STOPS, ZDR_COLORS, s.zdr);
            case CC -> lerpTable(CC_STOPS, CC_COLORS, s.cc);
            default -> lerpTable(REF_STOPS, REF_COLORS, s.dbz);
        };
    }

    // NWS-style reflectivity scale.
    static final float[] REF_STOPS = {10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75};
    static final int[] REF_COLORS = {0x3a6e8f, 0x2fa0d9, 0x1e5fd6, 0x22c43a, 0x15962a, 0x0d6b1c, 0xf2e32a, 0xe6b422, 0xf07a1c,
            0xe02018, 0xb0141a, 0xe02ad8, 0x8b2ae0, 0xffffff};
    static final float[] ZDR_STOPS = {-2, -0.5F, 0, 0.5F, 1, 1.5F, 2, 3, 4, 5, 6};
    static final int[] ZDR_COLORS = {0x101010, 0x5a5a5a, 0x9a9a9a, 0x2a2ab0, 0x3a7ae0, 0x3ac0c0, 0x30b030, 0xe0e030, 0xe08020, 0xd02020,
            0xf0a0f0};
    static final float[] CC_STOPS = {0.2F, 0.45F, 0.6F, 0.7F, 0.8F, 0.85F, 0.9F, 0.93F, 0.95F, 0.97F, 0.99F, 1.0F};
    static final int[] CC_COLORS = {0x14143c, 0x2a2a90, 0x3a6ad0, 0x3ab0e0, 0x30c060, 0x90d030, 0xe0e030, 0xf0a020, 0xe05020, 0xc02040,
            0xd070c0, 0xf0d0f0};

    static int velColor(float v) {
        float a = Math.abs(v);
        if (a < 1.5F) {
            return 0xFF7a7a7a;
        }
        float t = Mth.clamp(a / 50F, 0F, 1F);
        int c;
        if (v < 0) {
            // Inbound: dark to bright green, then cyan-white at the extreme.
            c = t < 0.8F ? mix(0x0e4a12, 0x24f02c, t / 0.8F) : mix(0x24f02c, 0xb8fff0, (t - 0.8F) / 0.2F);
        } else {
            // Outbound: dark to bright red, then pink-white.
            c = t < 0.8F ? mix(0x4a0e10, 0xf0242c, t / 0.8F) : mix(0xf0242c, 0xffc0e0, (t - 0.8F) / 0.2F);
        }
        return 0xFF000000 | c;
    }

    static int lerpTable(float[] stops, int[] colors, float v) {
        if (v <= stops[0]) {
            return 0xFF000000 | colors[0];
        }
        for (int i = 1; i < stops.length; i++) {
            if (v < stops[i]) {
                float t = (v - stops[i - 1]) / (stops[i] - stops[i - 1]);
                return 0xFF000000 | mix(colors[i - 1], colors[i], t);
            }
        }
        return 0xFF000000 | colors[colors.length - 1];
    }

    static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    /** Smooth value noise 0..1. */
    static float noise(double x, double z, int salt) {
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        float fx = (float) (x - ix);
        float fz = (float) (z - iz);
        float sx = fx * fx * (3 - 2 * fx);
        float sz = fz * fz * (3 - 2 * fz);
        return Mth.lerp(sz, Mth.lerp(sx, hash(ix, iz, salt), hash(ix + 1, iz, salt)), Mth.lerp(sx, hash(ix, iz + 1, salt), hash(ix + 1, iz + 1, salt)));
    }

    private static float hash(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** Estimated Fujita rating from the peak wind (m/s). */
    public static int ratingFor(float windMps) {
        float[] thresholds = {29F, 38F, 49F, 60F, 74F, 90F};
        int r = 0;
        for (int i = 0; i < thresholds.length; i++) {
            if (windMps >= thresholds[i]) {
                r = i;
            }
        }
        return r;
    }

    /** Typical peak winds of each rating on the Enhanced Fujita scale (m/s). */
    public static final float[] EF_WIND = {33F, 44F, 55F, 67F, 82F, 101F};

    /** Peak wind of a tornado in m/s, from its rating and how mature it is. */
    public static float peakWind(ClientTornadoes.T t) {
        return EF_WIND[t.rating] * (0.6F + 0.4F * t.strength) * Math.min(1F, 0.25F + t.contact);
    }
}
