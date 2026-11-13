package com.realweather.client.space;

import com.mojang.blaze3d.platform.NativeImage;
import com.realweather.RealWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The planets' surface maps read on the CPU too, to know how high the ground is under the camera (so the planet map
 * lands you on top of it), worked out exactly as the ray-traced view does.
 */
final class PlanetMaps {
    private static final Map<ResourceLocation, int[]> CACHE = new HashMap<>();
    private static final Map<ResourceLocation, int[]> SIZE = new HashMap<>();

    private PlanetMaps() {}

    private static int[] load(ResourceLocation map) {
        int[] px = CACHE.get(map);
        if (px != null) {
            return px;
        }
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(map); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth(), h = img.getHeight();
            px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    // ABGR
                    px[y * w + x] = img.getPixelRGBA(x, y);
                }
            }
            SIZE.put(map, new int[]{w, h});
        } catch (Exception e) {
            RealWeather.LOG.warn("Could not read {}", map, e);
            px = new int[]{0xFF808080};
            SIZE.put(map, new int[]{1, 1});
        }
        CACHE.put(map, px);
        return px;
    }

    /** The map's colour (0..1) in a direction of the planet's own frame, filtered as the GPU does. */
    static float[] color(ResourceLocation map, double x, double y, double z) {
        int[] px = load(map);
        int[] wh = SIZE.get(map);
        int w = wh[0], h = wh[1];
        double l = Math.sqrt(x * x + y * y + z * z);
        double u = Math.atan2(z, x) / (Math.PI * 2);
        u -= Math.floor(u);
        double v = Math.acos(Math.max(-1, Math.min(1, y / l))) / Math.PI;
        double fx = u * w - 0.5, fy = v * h - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double ax = fx - x0, ay = fy - y0;
        float[] out = new float[3];
        for (int k = 0; k < 4; k++) {
            int xi = Math.floorMod(x0 + (k & 1), w), yi = Math.max(0, Math.min(h - 1, y0 + (k >> 1)));
            double wgt = ((k & 1) == 1 ? ax : 1 - ax) * ((k >> 1) == 1 ? ay : 1 - ay);
            int c = px[yi * w + xi];
            out[0] += (float) (wgt * (c & 0xFF) / 255.0);
            out[1] += (float) (wgt * ((c >> 8) & 0xFF) / 255.0);
            out[2] += (float) (wgt * ((c >> 16) & 0xFF) / 255.0);
        }
        return out;
    }

    /** Height of the ground (blocks above the radius) in a direction, as the ray-traced planet has it. */
    static float ground(SpaceShaders.Planet p, double x, double y, double z) {
        float[] c = color(p.map, x, y, z);
        boolean ocean = p.sea >= 0 && c[2] > c[0] * 1.15F && c[2] > c[1] * 0.95F;
        if (ocean) {
            // The sea's surface.
            return p.sea;
        }
        float h = (float) Math.floor((c[0] * 0.3F + c[1] * 0.59F + c[2] * 0.11F) * p.relief + 0.5F);
        if (p.sea >= 0) {
            h = Math.max(h, p.sea);
        }
        return h;
    }
}
