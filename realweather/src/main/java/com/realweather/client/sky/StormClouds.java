package com.realweather.client.sky;

import com.realweather.client.ClientStorms;
import com.realweather.client.tornado.ClientTornadoes;
import com.realweather.server.StormCell;
import com.realweather.server.StormField;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The storms themselves as seen from the ground: a dark, flat base hanging over the rain, towering cauliflower
 * tops lit by the sun, an anvil spreading downwind, grey curtains of rain under the core, and on supercells the
 * lowered, slowly turning wall cloud with striated "mothership" layers and an inflow tail feeding into it.
 * Tornadoes that have no parent supercell nearby (summoned by command) get one built around them.
 */
public final class StormClouds {
    private static final float[] MAX_DIST = {800F, 1100F, 1500F, 1900F};
    private static final int[] BASE_N = {12, 22, 40, 70};
    private static final int[] EDGE_N = {6, 12, 20, 34};
    private static final int[] TOWER_N = {5, 10, 18, 30};
    private static final int[] ANVIL_N = {3, 6, 10, 16};
    private static final int[] SHAFT_N = {2, 4, 7, 11};
    private static final int[] WALL_N = {6, 10, 18, 28};
    private static final int[] STRIATION_N = {0, 6, 10, 16};
    private static final int[] TAIL_N = {0, 4, 7, 11};

    private StormClouds() {}

    /** Height of storm cloud bases. */
    public static float deckY(ClientLevel level) {
        return level.getSeaLevel() + 90F;
    }

    /** A cell built for a tornado with no supercell around it. */
    private record Synthetic(double cx, double cz, float cvx, float cvz, float cradius, float cintensity, float chail,
                             int ctype, float cstrength) implements StormField.Cell {}

    static void collect(ClientLevel level, Vec3 cam, float pt, int q) {
        float time = level.getGameTime() + pt;
        float deck = deckY(level);
        for (ClientStorms.Cell c : ClientStorms.cells()) {
            float s = c.cstrength();
            if (s < 0.02F) {
                continue;
            }
            double cx = c.x + c.vx * pt;
            double cz = c.z + c.vz * pt;
            boolean hasTornado = c.type == StormCell.SUPERCELL && tornadoUnder(c, cx, cz) != null;
            cell(level, cam, q, time, deck, c, cx, cz, c.seed, hasTornado);
        }
        // Tornadoes summoned on their own still hang from a supercell.
        for (ClientTornadoes.T t : ClientTornadoes.all()) {
            if (t.strength <= 0.01F || parentOf(t) != null) {
                continue;
            }
            float r = 200F + t.rating * 25F;
            float hx = Mth.sin(t.heading);
            float hz = -Mth.cos(t.heading);
            float speed = Math.max(0.05F, t.speed);
            // The updraft sits 0.3 R behind the cell centre, so put the centre ahead of the tornado.
            double cx = t.x(pt) + hx * r * 0.3;
            double cz = t.z(pt) + hz * r * 0.3;
            Synthetic syn = new Synthetic(cx, cz, hx * speed, hz * speed, r, 0.9F, 0.4F, StormCell.SUPERCELL,
                    Math.min(1F, 0.35F + t.strength));
            cell(level, cam, q, time, deck, syn, cx, cz, t.seed, true);
        }
    }

    /** The supercell a tornado hangs from, if one is close enough. */
    public static ClientStorms.Cell parentOf(ClientTornadoes.T t) {
        for (ClientStorms.Cell c : ClientStorms.cells()) {
            if (c.type != StormCell.SUPERCELL || c.cstrength() < 0.05F) {
                continue;
            }
            double[] u = StormField.updraft(c);
            if (Math.hypot(u[0] - t.x, u[1] - t.z) < c.radius * 0.45) {
                return c;
            }
        }
        return null;
    }

    private static ClientTornadoes.T tornadoUnder(ClientStorms.Cell c, double cx, double cz) {
        double[] u = StormField.updraft(c);
        for (ClientTornadoes.T t : ClientTornadoes.all()) {
            if (Math.hypot(u[0] - t.x, u[1] - t.z) < c.radius * 0.45) {
                return t;
            }
        }
        return null;
    }

    private static void cell(ClientLevel level, Vec3 cam, int q, float time, float deck, StormField.Cell c, double cx, double cz,
                             long seed, boolean hasTornado) {
        float s = c.cstrength();
        float rad = c.cradius();
        double dx = cx - cam.x;
        double dz = cz - cam.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist - rad * 1.4 > MAX_DIST[q]) {
            return;
        }
        RandomSource rnd = RandomSource.create(seed);
        boolean supercell = c.ctype() == StormCell.SUPERCELL;
        float inten = c.cintensity() * s;
        float size = rad / 250F;
        float area = Mth.clamp(size * size, 0.35F, 2.2F);
        float sp = Math.max(1.0E-4F, Mth.sqrt(c.cvx() * c.cvx() + c.cvz() * c.cvz()));
        float fx = c.cvx() / sp;
        float fz = c.cvz() / sp;
        // Right of the motion (looking along it).
        float rx = -fz;
        float rz = fx;
        float lr = SkyLight.r, lg = SkyLight.g, lb = SkyLight.b * 1.03F;
        float dark = Mth.clamp(0.62F - 0.3F * inten - (supercell ? 0.08F : 0F), 0.2F, 0.7F);
        float grow = 0.35F + 0.65F * s;

        // Dark flat base over the whole storm.
        int nBase = Math.round(BASE_N[q] * area);
        for (int i = 0; i < nBase; i++) {
            float a = rnd.nextFloat() * Mth.TWO_PI;
            float r = Mth.sqrt(rnd.nextFloat()) * 0.95F;
            float along = Mth.cos(a) * r * (supercell ? 1.25F : 1F) + (supercell ? 0.15F : 0F);
            float across = Mth.sin(a) * r * (supercell ? 0.8F : 1F);
            float px = (float) (cx + (fx * along + rx * across) * rad - cam.x);
            float pz = (float) (cz + (fz * along + rz * across) * rad - cam.z);
            float py = deck + rnd.nextFloat() * 4F - (float) cam.y;
            float w = rad * (0.2F + rnd.nextFloat() * 0.12F) * grow;
            float shade = dark * (0.85F + rnd.nextFloat() * 0.2F);
            float alpha = 0.92F * Mth.clamp(s * 1.4F, 0F, 1F) * (1F - 0.35F * r * r);
            SkyBillboards.cell(SkyBillboards.FLAT, SkyBillboards.BASE + (i & 1), px, py, pz, w, w, rnd.nextFloat() * Mth.TWO_PI,
                    lr * shade, lg * shade, lb * shade, alpha, 1F, 0F);
        }
        // Puffy edges and the underside seen from the side (FLAT sprites vanish at a grazing angle).
        int nEdge = Math.round(EDGE_N[q] * Mth.sqrt(area));
        for (int i = 0; i < nEdge; i++) {
            float a = rnd.nextFloat() * Mth.TWO_PI;
            float r = 0.55F + rnd.nextFloat() * 0.4F;
            float along = Mth.cos(a) * r * (supercell ? 1.25F : 1F) + (supercell ? 0.15F : 0F);
            float across = Mth.sin(a) * r * (supercell ? 0.8F : 1F);
            float px = (float) (cx + (fx * along + rx * across) * rad - cam.x);
            float pz = (float) (cz + (fz * along + rz * across) * rad - cam.z);
            float w = rad * (0.14F + rnd.nextFloat() * 0.08F) * grow;
            float py = deck + w * 0.35F - (float) cam.y;
            float shade = dark * (0.95F + rnd.nextFloat() * 0.25F);
            SkyBillboards.cell(SkyBillboards.SPHERE, SkyBillboards.BASE + (i & 1), px, py, pz, w, w * 0.55F, (rnd.nextFloat() - 0.5F) * 0.3F,
                    lr * shade, lg * shade, lb * shade, 0.85F * Mth.clamp(s * 1.4F, 0F, 1F), 0.75F, SkyLight.sunSide);
        }

        // Towers: big cauliflower turrets rising over the updraft, brighter higher up where the sun reaches.
        double ux = cx;
        double uz = cz;
        if (supercell) {
            ux = cx - fx * rad * 0.3F + rx * rad * 0.06F;
            uz = cz - fz * rad * 0.3F + rz * rad * 0.06F;
        }
        float top = deck + (55F + 95F * inten + (supercell ? 40F : 0F)) * grow;
        int nTower = Math.round(TOWER_N[q] * Mth.sqrt(area));
        for (int i = 0; i < nTower; i++) {
            float h = rnd.nextFloat();
            float spread = rad * (0.42F - 0.22F * h);
            float a = rnd.nextFloat() * Mth.TWO_PI;
            float r = Mth.sqrt(rnd.nextFloat());
            float ox = Mth.cos(a) * r * spread;
            float oz = Mth.sin(a) * r * spread;
            float py = Mth.lerp(h, deck + 15F, top) - (float) cam.y;
            float w = rad * (0.2F - 0.08F * h + rnd.nextFloat() * 0.06F) * grow;
            float shade = Mth.lerp(h, dark + 0.12F, 0.96F) + SkyLight.silver(ox, (h - 0.3F) * rad, oz);
            float alpha = 0.95F * Mth.clamp(s * 1.6F, 0F, 1F);
            // Boiling: the turrets slowly turn and swell.
            float rot = rnd.nextFloat() * Mth.TWO_PI + time * 0.0004F * (i % 2 == 0 ? 1 : -1);
            SkyBillboards.cell(SkyBillboards.SPHERE, SkyBillboards.CUMULUS + rnd.nextInt(6), (float) (ux + ox - cam.x), py,
                    (float) (uz + oz - cam.z), w, w, rot, lr * shade, lg * shade, lb * shade, alpha, 0.7F, SkyLight.sunSide);
        }

        // Anvil: a flat sheet at the top spreading downstream.
        if (inten > 0.35F && c.ctype() != StormCell.SHOWER) {
            int nAnvil = Math.round(ANVIL_N[q] * Mth.sqrt(area));
            for (int i = 0; i < nAnvil; i++) {
                float along = -0.2F + rnd.nextFloat() * 1.6F;
                float across = (rnd.nextFloat() - 0.5F) * (0.6F + along * 0.5F);
                float px = (float) (ux + (fx * along + rx * across) * rad - cam.x);
                float pz = (float) (uz + (fz * along + rz * across) * rad - cam.z);
                float py = top - 8F + rnd.nextFloat() * 10F - (float) cam.y;
                float w = rad * (0.3F + rnd.nextFloat() * 0.2F) * grow;
                float shade = 0.82F + 0.1F * rnd.nextFloat();
                SkyBillboards.cell(SkyBillboards.SPHERE, SkyBillboards.STRATUS, px, py, pz, w, w * 0.28F, 0F,
                        lr * shade, lg * shade, lb * shade, 0.75F * inten * (1F - along / 1.6F * 0.6F), 0.8F, SkyLight.sunSide);
            }
        }

        // Rain (and hail) shafts under the heaviest parts.
        int nShaft = Math.round(SHAFT_N[q] * Mth.sqrt(area));
        float rainTop = deck + 2F;
        for (int i = 0, tries = 0; i < nShaft && tries < nShaft * 6; tries++) {
            float a = rnd.nextFloat() * Mth.TWO_PI;
            float r = Mth.sqrt(rnd.nextFloat()) * 0.85F;
            double wx = cx + (fx * Mth.cos(a) + rx * Mth.sin(a)) * r * rad * (supercell ? 1.2F : 1F) + fx * (supercell ? rad * 0.2F : 0F);
            double wz = cz + (fz * Mth.cos(a) + rz * Mth.sin(a)) * r * rad * (supercell ? 1.2F : 1F) + fz * (supercell ? rad * 0.2F : 0F);
            float rain = StormField.rain(c, wx, wz) / Math.max(0.1F, c.cintensity());
            float hail = StormField.hail(c, wx, wz);
            float pick = rnd.nextFloat();
            if (rain < 0.35F + pick * 0.3F) {
                continue;
            }
            i++;
            float w = rad * (0.1F + rnd.nextFloat() * 0.1F);
            double sdx = wx - cam.x;
            double sdz = wz - cam.z;
            if (sdx * sdx + sdz * sdz < w * w * 1.5F) {
                // We are standing in it: the near rain is drawn by the precipitation renderer.
                continue;
            }
            float ground = groundAt(level, wx, wz);
            float h = (rainTop - ground) * 0.5F;
            float py = ground + h - (float) cam.y;
            float shade = dark * 0.95F + hail * 0.35F;
            float alpha = Mth.clamp(rain, 0F, 1F) * 0.42F * s;
            SkyBillboards.cell(SkyBillboards.CYLINDER, SkyBillboards.SHAFT, (float) sdx, py, (float) sdz, w, h, 0F,
                    lr * shade, lg * shade, lb * shade * 1.05F, alpha, 0.85F, 0F);
        }

        if (!supercell) {
            return;
        }
        float spin = time * 0.0012F;
        // Striations: stacked, broadening bands around the updraft (the "mothership").
        int nStri = STRIATION_N[q];
        for (int i = 0; i < nStri; i++) {
            float layer = (float) (i % 4) / 3F;
            float a = rnd.nextFloat() * Mth.TWO_PI + spin * (0.6F + layer * 0.3F);
            float ring = rad * (0.16F + layer * 0.1F);
            float px = (float) (ux + Mth.cos(a) * ring - cam.x);
            float pz = (float) (uz + Mth.sin(a) * ring - cam.z);
            float py = deck + 4F + layer * 26F - (float) cam.y;
            float w = rad * (0.16F + 0.08F * layer);
            float shade = dark * (1.05F + layer * 0.35F);
            SkyBillboards.cell(SkyBillboards.SPHERE, SkyBillboards.STRATUS, px, py, pz, w, w * 0.22F, 0F,
                    lr * shade, lg * shade, lb * shade, 0.8F * s, 0.7F, SkyLight.sunSide);
        }
        if (hasTornado) {
            // The tornado draws its own wall cloud around the funnel.
            return;
        }
        // Wall cloud: a lowered, rotating block of cloud under the updraft.
        int nWall = WALL_N[q];
        float wallR = rad * 0.11F;
        float lower = 10F + 8F * s;
        for (int i = 0; i < nWall; i++) {
            float a = rnd.nextFloat() * Mth.TWO_PI - spin * 3F;
            float r = Mth.sqrt(rnd.nextFloat()) * wallR;
            float px = (float) (ux + Mth.cos(a) * r - cam.x);
            float pz = (float) (uz + Mth.sin(a) * r - cam.z);
            float py = deck - lower * (1F - r / wallR * 0.6F) + rnd.nextFloat() * 3F - (float) cam.y;
            float w = wallR * (0.45F + rnd.nextFloat() * 0.3F);
            float shade = dark * (0.75F + rnd.nextFloat() * 0.15F);
            SkyBillboards.cell(SkyBillboards.SPHERE, SkyBillboards.WALL + (i & 1), px, py, pz, w, w * 0.6F, a,
                    lr * shade, lg * shade, lb * shade, 0.9F * s, 0.7F, SkyLight.sunSide * 0.5F);
        }
        tail(cam, q, time, deck, rad, ux, uz, fx, fz, rx, rz, dark, s, rnd);
    }

    /** Ragged scud flowing into the updraft from the forward flank. */
    public static void tail(Vec3 cam, int q, float time, float deck, float rad, double ux, double uz, float fx, float fz, float rx, float rz,
                     float dark, float s, RandomSource rnd) {
        int n = TAIL_N[q];
        for (int i = 0; i < n; i++) {
            float phase = ((float) i / n + time * 0.0006F) % 1F;
            float along = phase * 0.45F;
            float px = (float) (ux + (fx * along - rx * along * 0.35F) * rad - cam.x);
            float pz = (float) (uz + (fz * along - rz * along * 0.35F) * rad - cam.z);
            float py = deck - 6F - 4F * (1F - phase) + rnd.nextFloat() * 3F - (float) cam.y;
            float w = rad * (0.04F + rnd.nextFloat() * 0.03F);
            float fade = Math.min(1F, Math.min(phase / 0.15F, (1F - phase) / 0.2F));
            float shade = dark * 0.95F;
            SkyBillboards.cell(SkyBillboards.SPHERE, SkyBillboards.SCUD + (i & 1), px, py, pz, w * 1.6F, w * 0.7F, rnd.nextFloat() * 0.4F,
                    SkyLight.r * shade, SkyLight.g * shade, SkyLight.b * shade, 0.8F * s * fade, 0.8F, 0F);
        }
    }

    static float groundAt(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (level.hasChunk(bx >> 4, bz >> 4)) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        }
        return level.getSeaLevel();
    }
}
