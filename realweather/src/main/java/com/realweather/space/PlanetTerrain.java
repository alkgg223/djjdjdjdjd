package com.realweather.space;

import com.realweather.RealWeather;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ground of each world in its dimension: the height and the blocks of every column, from the world's own map (its
 * bright and dark lands, its seas, its ice) with hills, mountains and, on airless worlds, craters on top. Pure
 * functions of the position, so any thread can make any chunk.
 */
public final class PlanetTerrain {
    public static final int SEA_LEVEL = 63;
    private static final ConcurrentHashMap<String, int[]> MAPS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, int[]> SIZES = new ConcurrentHashMap<>();

    private PlanetTerrain() {}

    private static int[] map(String name) {
        return MAPS.computeIfAbsent(name, n -> {
            String path = "/assets/realweather/textures/environment/space3d/" + n + ".png";
            try (InputStream in = PlanetTerrain.class.getResourceAsStream(path)) {
                BufferedImage img = ImageIO.read(in);
                int w = img.getWidth(), h = img.getHeight();
                int[] px = img.getRGB(0, 0, w, h, null, 0, w);
                SIZES.put(n, new int[]{w, h});
                return px;
            } catch (Exception e) {
                RealWeather.LOG.warn("Could not read the map {}", path, e);
                SIZES.put(n, new int[]{1, 1});
                return new int[]{0xFF808080};
            }
        });
    }

    /** The map's colour (0..1, red green blue), smoothly filtered. */
    public static float[] color(String name, double u, double v) {
        int[] px = map(name);
        int[] wh = SIZES.get(name);
        int w = wh[0], h = wh[1];
        double fx = u * w - 0.5, fy = v * h - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double ax = fx - x0, ay = fy - y0;
        float[] out = new float[3];
        for (int k = 0; k < 4; k++) {
            int xi = Math.floorMod(x0 + (k & 1), w), yi = Math.max(0, Math.min(h - 1, y0 + (k >> 1)));
            double wgt = ((k & 1) == 1 ? ax : 1 - ax) * ((k >> 1) == 1 ? ay : 1 - ay);
            int c = px[yi * w + xi];
            out[0] += (float) (wgt * ((c >> 16) & 0xFF) / 255.0);
            out[1] += (float) (wgt * ((c >> 8) & 0xFF) / 255.0);
            out[2] += (float) (wgt * (c & 0xFF) / 255.0);
        }
        return out;
    }

    public static float lum(float[] c) {
        return c[0] * 0.3F + c[1] * 0.59F + c[2] * 0.11F;
    }

    public static boolean isSea(float[] c) {
        return c[2] > c[0] * 1.15F && c[2] > c[1] * 0.95F;
    }

    // ------------------------------------------------------------------------------------------- noise

    private static double hash(long x, long z, long seed) {
        long h = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL + seed * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double noise(double x, double z, long seed) {
        long ix = (long) Math.floor(x), iz = (long) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz, seed), b = hash(ix + 1, iz, seed), c = hash(ix, iz + 1, seed), d = hash(ix + 1, iz + 1, seed);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    /** Ridged noise: sharp crests (mountain chains). */
    private static double ridge(double x, double z, long seed) {
        return 1 - Math.abs(noise(x, z, seed) * 2 - 1);
    }

    /** Bowls of impact craters with raised rims (cells of `cell` blocks, about half of them with a crater). */
    private static double craters(double x, double z, double cell, long seed) {
        long cx = (long) Math.floor(x / cell), cz = (long) Math.floor(z / cell);
        double h = 0;
        for (long i = cx - 1; i <= cx + 1; i++) {
            for (long j = cz - 1; j <= cz + 1; j++) {
                if (hash(i, j, seed) > 0.55) {
                    continue;
                }
                double px = (i + hash(i, j, seed + 1)) * cell, pz = (j + hash(i, j, seed + 2)) * cell;
                double r = cell * (0.12 + 0.38 * Math.pow(hash(i, j, seed + 3), 2));
                double d = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz)) / r;
                double depth = r * 0.22;
                if (d < 1) {
                    h -= depth * (1 - d * d);
                } else if (d < 1.5) {
                    h += depth * 0.32 * (1 - (d - 1) / 0.5);
                }
            }
        }
        return h;
    }

    // ------------------------------------------------------------------------------------------- the ground

    /** Height of the ground (the top block's y) of a column. */
    public static int height(Worlds.World w, int x, int z) {
        double[] uv = Worlds.mapUV(x, z);
        float[] c = color(w.map(), uv[0], uv[1]);
        float l = lum(c);
        long seed = w.id().hashCode();
        double hills = (noise(x / 170.0, z / 170.0, seed) - 0.5) * 2 * 24 + (noise(x / 46.0, z / 46.0, seed + 7) - 0.5) * 2 * 7
                + (noise(x / 12.0, z / 12.0, seed + 13) - 0.5) * 2 * 2.2;
        double mountains = Math.pow(ridge(x / 420.0, z / 420.0, seed + 21), 3) * 70 * smooth(0.45, 0.75, noise(x / 900.0, z / 900.0, seed + 29));
        double h;
        if (w.gas()) {
            // The tops of the clouds: rolling, with towering heads here and there.
            h = 70 + (l - 0.5) * 18 + hills * 0.35 + Math.pow(noise(x / 60.0, z / 60.0, seed + 3), 4) * 26;
        } else if (w.ocean() && isSea(c)) {
            // The sea floor, deeper away from the coasts.
            h = SEA_LEVEL - 6 - (1 - l) * 18 + hills * 0.3;
        } else {
            h = 66 + (l - 0.4) * 70 * w.relief() + (hills + mountains) * w.relief();
            if (w.ocean()) {
                h = Math.max(h, SEA_LEVEL + 1 + hills * 0.15);
            }
            GeologyProfiles.Profile g = GeologyProfiles.of(w.id());
            double cs = g == null ? 1 : g.craters;
            if (w.airless()) {
                h += (craters(x, z, 140, seed + 41) + craters(x, z, 37, seed + 43) * 0.6) * cs;
            }
            if (g != null) {
                h += landforms(g, x, z, seed);
            }
        }
        return (int) Math.max(-50, Math.min(300, Math.floor(h)));
    }

    /** Canyons cut in, volcanoes raised, dunes rippled, icy plains smoothed: each world's own landforms. */
    private static double landforms(GeologyProfiles.Profile g, int x, int z, long seed) {
        double h = 0;
        if (g.canyons > 0) {
            // Long winding valleys: where a slow noise crosses its middle.
            double c = Math.abs(noise(x / 520.0, z / 520.0, seed + 61) - 0.5) + (noise(x / 60.0, z / 60.0, seed + 62) - 0.5) * 0.03;
            h -= g.canyons * 34 * (1 - smooth(0.0, 0.05, c));
        }
        if (g.volcanoes > 0) {
            h += volcano(x, z, seed) * g.volcanoes;
        }
        if (g.dunes > 0) {
            double field = smooth(0.5, 0.7, noise(x / 400.0, z / 400.0, seed + 71));
            h += field * g.dunes * 3 * Math.sin(x / 9.0 + Math.sin(z / 23.0) * 2 + noise(x / 40.0, z / 40.0, seed + 72) * 3);
        }
        if (g.ice > 0) {
            h *= 1 - g.ice * 0.5;
        }
        return h;
    }

    /** Shield volcanoes, each with a caldera, in a few cells of 900 blocks. */
    private static double volcano(int x, int z, long seed) {
        double cell = 900;
        long cx = (long) Math.floor(x / cell), cz = (long) Math.floor(z / cell);
        double h = 0;
        for (long i = cx - 1; i <= cx + 1; i++) {
            for (long j = cz - 1; j <= cz + 1; j++) {
                if (hash(i, j, seed + 81) > 0.3) {
                    continue;
                }
                double px = (i + 0.2 + 0.6 * hash(i, j, seed + 82)) * cell, pz = (j + 0.2 + 0.6 * hash(i, j, seed + 83)) * cell;
                double r = 180 + 220 * hash(i, j, seed + 84);
                double d = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz)) / r;
                if (d < 1) {
                    double cone = (1 - d) * (1 - d) * (45 + 40 * hash(i, j, seed + 85));
                    double caldera = d < 0.12 ? (0.12 - d) / 0.12 * 14 : 0;
                    h = Math.max(h, cone - caldera);
                }
            }
        }
        return h;
    }

    /** How far into a volcanic region a column is (0 none, 1 well in). */
    private static double volcanic(int x, int z, long seed) {
        return volcano(x, z, seed) > 6 ? 1 : 0;
    }

    private static double smooth(double e0, double e1, double v) {
        double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** Which tile the top block of a column is. */
    public static int surfaceTile(Worlds.World w, int x, int z, int h) {
        GeologyProfiles.Profile g = GeologyProfiles.of(w.id());
        if (g != null && g.plains >= 0) {
            return geologic(w, g, x, z, h);
        }
        double[] uv = Worlds.mapUV(x, z);
        float[] c = color(w.map(), uv[0], uv[1]);
        float l = lum(c);
        if (w.ocean() && isSea(c)) {
            return Tiles.SEABED;
        }
        if (l > 0.8F) {
            return w.snow();
        }
        if (w.glow() > 0.2F && c[0] > 0.7F && c[1] < 0.62F && c[0] > c[2] * 1.6F) {
            return Tiles.MAGMA;
        }
        if (w.ocean() && h <= SEA_LEVEL + 2) {
            // Beaches.
            return w.low();
        }
        // A little speckle of the neighbouring kind, so the ground isn't all one block.
        double jitter = (hash(x, z, w.id().hashCode() + 99) - 0.5) * 0.08;
        double rel = l + jitter;
        return rel < 0.36 ? w.low() : rel < 0.58 ? w.mid() : w.high();
    }

    /** The top block by the world's geology: what lies where, by height, slope, craters, volcanoes, dunes, poles and deposits. */
    private static int geologic(Worlds.World w, GeologyProfiles.Profile g, int x, int z, int h) {
        double[] uv = Worlds.mapUV(x, z);
        float[] c = color(w.map(), uv[0], uv[1]);
        float l = lum(c);
        long seed = w.id().hashCode();
        if (w.ocean() && isSea(c)) {
            return g.seabed >= 0 ? g.seabed : Tiles.SEABED;
        }
        double lat = Math.abs(uv[1] - 0.5) * 180;
        double edge = (noise(x / 80.0, z / 80.0, seed + 91) - 0.5) * 10;
        if (g.polar >= 0 && lat + edge > 74) {
            return g.polar;
        }
        if (w.ocean() && h <= SEA_LEVEL + 1 && g.wet >= 0) {
            return g.wet;
        }
        if (w.ocean() && h <= SEA_LEVEL + 3 && g.beach >= 0) {
            return g.beach;
        }
        // Steep ground: cliffs and canyon walls.
        int dx = height(w, x + 2, z) - height(w, x - 2, z), dz = height(w, x, z + 2) - height(w, x, z - 2);
        double slope = Math.sqrt(dx * dx + dz * dz) / 4.0;
        if (g.slope >= 0 && slope > 1.6) {
            return g.slope;
        }
        // Craters: their floors and the debris round their rims.
        if (w.airless() && (g.craterFloor >= 0 || g.ejecta >= 0)) {
            double cr = craters(x, z, 140, seed + 41) + craters(x, z, 37, seed + 43) * 0.6;
            if (cr < -2.5 && g.craterFloor >= 0) {
                return g.craterFloor;
            }
            if (cr > 0.8 && g.ejecta >= 0) {
                return g.ejecta;
            }
        }
        if (g.volcanic >= 0 && g.volcanoes > 0 && volcanic(x, z, seed) > 0) {
            return g.volcanic;
        }
        // Scattered deposits: patches a few tens of blocks across.
        if (g.deposit >= 0) {
            long px = Math.floorDiv(x, 64), pz = Math.floorDiv(z, 64);
            double hx = (px + hash(px, pz, seed + 101)) * 64, hz = (pz + hash(px, pz, seed + 102)) * 64;
            double rad = 6 + 14 * hash(px, pz, seed + 103);
            if (hash(px, pz, seed + 104) < 0.22 && (x - hx) * (x - hx) + (z - hz) * (z - hz) < rad * rad * (0.7 + 0.6 * noise(x / 6.0, z / 6.0, seed + 105))) {
                return g.deposit2 >= 0 && hash(px, pz, seed + 106) < 0.4 ? g.deposit2 : g.deposit;
            }
        }
        double rel = l + (hash(x, z, seed + 99) - 0.5) * 0.08;
        if (g.dune >= 0 && g.dunes > 0 && rel < 0.5 && noise(x / 400.0, z / 400.0, seed + 71) > 0.58) {
            return g.dune;
        }
        if (g.twoFaced) {
            // Iapetus: the half facing forward along its orbit dark, the other bright.
            return uv[0] > 0.25 && uv[0] < 0.75 ? g.lowland : g.highland;
        }
        if (g.peak >= 0 && h > 150 + noise(x / 50.0, z / 50.0, seed + 111) * 20) {
            return g.peak;
        }
        return rel < 0.36 ? g.lowland : rel < 0.58 ? g.plains : g.highland;
    }

    /** The tile at a depth under the top (0 is the top block), at a height (for the layers). */
    public static int tileAt(Worlds.World w, int top, int depth, int x, int y, int z) {
        GeologyProfiles.Profile g = GeologyProfiles.of(w.id());
        if (g == null || g.plains < 0) {
            return tileAt(w, top, depth);
        }
        if (depth == 0) {
            return top;
        }
        if (depth < 4) {
            return top == g.seabed || top == Tiles.SEABED ? top : g.sub >= 0 ? g.sub : w.sub();
        }
        long seed = w.id().hashCode();
        // Mineral pockets.
        if (g.ore >= 0 && hash(Math.floorDiv(x, 3), Math.floorDiv(y, 3) * 31L + Math.floorDiv(z, 3), seed + 121) < 0.012) {
            return g.ore;
        }
        if (g.strata.length > 0 && y > 10) {
            // Layers a few blocks thick, gently warped, as they show in cliffs and canyon walls.
            double warp = noise(x / 90.0, z / 90.0, seed + 131) * 6;
            int k = Math.floorMod((int) Math.floor((y + warp) / 4.0), g.strata.length);
            return g.strata[k];
        }
        return g.deep >= 0 ? g.deep : w.deep();
    }

    /** The cloud deck tile at a height in a gas giant (-1: clear air there). */
    public static int cloudAt(Worlds.World w, int x, int y, int z) {
        GeologyProfiles.Profile g = GeologyProfiles.of(w.id());
        if (g == null || g.clouds.length == 0) {
            return -1;
        }
        long seed = w.id().hashCode();
        for (int i = 0; i + 1 < g.clouds.length; i += 2) {
            int base = g.clouds[i + 1];
            double puff = noise(x / 28.0, z / 28.0, seed + 140 + i) * 0.7 + noise(x / 7.0, z / 7.0, seed + 150 + i) * 0.3;
            if (puff < 0.52) {
                continue;
            }
            int thick = (int) ((puff - 0.52) * 18);
            if (y >= base - thick / 2 && y <= base + thick) {
                return g.clouds[i];
            }
        }
        return -1;
    }

    /** The tile at a depth under the top (0 is the top block). */
    public static int tileAt(Worlds.World w, int top, int depth) {
        if (depth == 0) {
            return top;
        }
        if (depth < 4) {
            return top == Tiles.SEABED ? Tiles.SEABED : w.sub();
        }
        return w.deep();
    }
}
