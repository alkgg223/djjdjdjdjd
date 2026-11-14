package com.realweather.client.radar;

import com.mojang.blaze3d.platform.NativeImage;
import com.realweather.RealWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * A radar picture drawn the way the real thing builds it up: the beam sweeps round, refreshing one wedge at a
 * time. Under the echoes a dim map of the loaded terrain, range rings and bearing lines help to find your way.
 */
public final class RadarCanvas {
    public static final int SECTORS = 48;
    private static int nextId;

    public final int size;
    private final NativeImage image;
    private final DynamicTexture texture;
    public final ResourceLocation id;
    private final int[][] sectorPixels;
    private final float[] pixelRange;
    private final RadarProducts.Sample sample = new RadarProducts.Sample();
    public int product;
    public float range = 256F;
    public double ox, oz;
    public int originY;
    public boolean signal = true;
    /** Sector the beam is on. */
    public int beam;
    private boolean dirty;
    public long lastUsed;

    public RadarCanvas(int size) {
        this.size = size;
        this.image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        this.texture = new DynamicTexture(image);
        this.id = RealWeather.id("radar/canvas_" + nextId++);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        int[] counts = new int[SECTORS];
        int[] sectorOf = new int[size * size];
        pixelRange = new float[size * size];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                float dx = i + 0.5F - size / 2F;
                float dy = j + 0.5F - size / 2F;
                double ang = Math.atan2(dx, -dy);
                if (ang < 0) {
                    ang += Math.PI * 2;
                }
                int s = Math.min(SECTORS - 1, (int) (ang / (Math.PI * 2) * SECTORS));
                sectorOf[j * size + i] = s;
                counts[s]++;
                pixelRange[j * size + i] = (float) Math.sqrt(dx * dx + dy * dy) / (size / 2F);
            }
        }
        sectorPixels = new int[SECTORS][];
        for (int s = 0; s < SECTORS; s++) {
            sectorPixels[s] = new int[counts[s]];
            counts[s] = 0;
        }
        for (int p = 0; p < sectorOf.length; p++) {
            int s = sectorOf[p];
            sectorPixels[s][counts[s]++] = p;
        }
        image.fillRect(0, 0, size, size, abgr(0xFF0B1016));
        texture.upload();
    }

    /** Draws every sector at once (when the picture is first opened or the settings change). */
    public void redrawAll(ClientLevel level) {
        for (int s = 0; s < SECTORS; s++) {
            drawSector(level, s);
        }
        texture.upload();
        dirty = false;
    }

    /** Moves the beam on by {@code sectors} wedges. */
    public void sweep(ClientLevel level, int sectors) {
        for (int k = 0; k < sectors; k++) {
            beam = (beam + 1) % SECTORS;
            drawSector(level, beam);
        }
        dirty = true;
    }

    public void upload() {
        if (dirty) {
            texture.upload();
            dirty = false;
        }
    }

    public float beamAngle() {
        return (beam + 1F) / SECTORS * Mth.TWO_PI;
    }

    private void drawSector(ClientLevel level, int s) {
        int half = size / 2;
        float scale = range * 2F / size;
        float time = level.getGameTime();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        float ringStep = range / 4F;
        for (int p : sectorPixels[s]) {
            int i = p % size;
            int j = p / size;
            float r = pixelRange[p];
            int argb;
            if (r > 1F) {
                argb = 0xFF0B1016;
            } else if (!signal) {
                argb = (((i + j + (int) time / 4) & 7) == 0) ? 0xFF1A2026 : 0xFF0D1218;
            } else {
                double x = ox + (i + 0.5 - half) * scale;
                double z = oz + (j + 0.5 - half) * scale;
                RadarProducts.sample(level, x, z, ox, oz, time, sample);
                argb = RadarProducts.color(product, sample);
                if (argb == RadarProducts.NONE) {
                    argb = basemap(level, x, z, m);
                }
                // Range rings and bearing lines.
                float dist = r * range;
                float ringPx = Math.abs(dist - Math.round(dist / ringStep) * ringStep) / scale;
                if (ringPx < 0.55F && dist > 1) {
                    argb = blend(argb, 0xFF8090A0, 0.45F);
                } else if (r > 0.02F) {
                    float dx = i + 0.5F - half;
                    float dy = j + 0.5F - half;
                    float ang = (float) Math.atan2(dx, -dy);
                    float step = Mth.PI / 6F;
                    float off = Math.abs(ang - Math.round(ang / step) * step) * r * half;
                    if (off < 0.5F) {
                        argb = blend(argb, 0xFF607080, 0.3F);
                    }
                }
                if (r > 0.985F) {
                    argb = 0xFF9AA8B8;
                }
            }
            image.setPixelRGBA(i, j, abgr(argb));
        }
    }

    /** Dim terrain from the loaded chunks, so the echoes can be placed on the land. */
    private static int basemap(ClientLevel level, double x, double z, BlockPos.MutableBlockPos m) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) {
            return 0xFF101820;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
        m.set(bx, top, bz);
        BlockState state = level.getBlockState(m);
        MapColor color = state.getMapColor(level, m);
        if (color == MapColor.NONE) {
            return 0xFF101820;
        }
        int c = color.col;
        if (!state.getFluidState().isEmpty()) {
            c = 0x1a3050;
        }
        int r = (c >> 16) & 255;
        int g = (c >> 8) & 255;
        int b = c & 255;
        float k = 0.22F;
        return 0xFF000000 | (int) (r * k + 10) << 16 | (int) (g * k + 14) << 8 | (int) (b * k + 20);
    }

    static int blend(int a, int b, float t) {
        return 0xFF000000 | RadarProducts.mix(a & 0xFFFFFF, b & 0xFFFFFF, t);
    }

    /** NativeImage stores pixels as ABGR. */
    private static int abgr(int argb) {
        int a = (argb >>> 24) & 255;
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        return a << 24 | b << 16 | g << 8 | r;
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release(id);
    }
}
