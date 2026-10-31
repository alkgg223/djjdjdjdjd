package com.realweather.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realweather.ClientConfig;
import com.realweather.RealWeather;
import com.realweather.server.WeatherServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Draws the falling weather around the camera: layered rain streaks bent by the wind, drifting snow,
 * blizzard snow driven almost sideways, sandstorm dust sheets, and puddles on the ground.
 */
public final class PrecipitationRenderer {
    private static final ResourceLocation RAIN = RealWeather.id("textures/environment/rain_streaks.png");
    private static final ResourceLocation RAIN_DENSE = RealWeather.id("textures/environment/rain_dense.png");
    private static final ResourceLocation RAIN_MIST = RealWeather.id("textures/environment/rain_mist.png");
    private static final ResourceLocation SNOW = RealWeather.id("textures/environment/snow_flakes.png");
    private static final ResourceLocation HAIL = RealWeather.id("textures/environment/hail_streaks.png");
    private static final ResourceLocation SNOW_FINE = RealWeather.id("textures/environment/snow_fine.png");
    private static final ResourceLocation SNOW_STREAKS = RealWeather.id("textures/environment/snow_streaks.png");
    private static final ResourceLocation SAND = RealWeather.id("textures/environment/sand_sheet.png");
    private static final ResourceLocation SAND_GRAINS = RealWeather.id("textures/environment/sand_grains.png");
    private static final ResourceLocation PUDDLE_1 = RealWeather.id("textures/environment/puddle_1.png");
    private static final ResourceLocation PUDDLE_2 = RealWeather.id("textures/environment/puddle_2.png");
    private static final ResourceLocation PUDDLE_3 = RealWeather.id("textures/environment/puddle_3.png");

    private static final int NONE = 0, RAIN_COL = 1, SNOW_COL = 2, SAND_COL = 3;
    private static final int MAX_R = 16;
    private static final int SIZE = MAX_R * 2 + 1;
    private static final int[] type = new int[SIZE * SIZE];
    private static final int[] ground = new int[SIZE * SIZE];
    private static final int[] light = new int[SIZE * SIZE];

    private static BufferBuilder buffer;
    private static ResourceLocation bound;

    // Puddle cache.
    private static final int PUDDLE_R = 18;
    private static final int PUDDLE_MAX = (PUDDLE_R * 2 + 1) * (PUDDLE_R * 2 + 1);
    private static final int[] puddleX = new int[PUDDLE_MAX];
    private static final int[] puddleZ = new int[PUDDLE_MAX];
    private static final float[] puddleY = new float[PUDDLE_MAX];
    private static final int[] puddleLight = new int[PUDDLE_MAX];
    private static int puddleCount;
    private static int puddleCx = Integer.MIN_VALUE, puddleCz, puddleCy, puddleAge;

    private PrecipitationRenderer() {}

    public static boolean render(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
        if (!ClientWeather.active(level)) {
            return false;
        }
        float rainLevel = level.getRainLevel(partialTick);
        boolean puddles = ClientConfig.PUDDLES.get() && ClientWeather.wetness > 0.02F;
        float sand = ClientWeather.sand;
        if (rainLevel <= 0.001F && sand <= 0.002F && !puddles) {
            return true;
        }
        float q = ClientConfig.qualityScale();
        int r = q < 0.5F ? 8 : q < 0.7F ? 11 : q < 0.95F ? 14 : 17;
        double time = (ticks % 240000) + partialTick;

        lightTexture.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        if (puddles) {
            renderPuddles(level, camX, camY, camZ, ticks);
        }

        int cx = Mth.floor(camX);
        int cy = Mth.floor(camY);
        int cz = Mth.floor(camZ);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int i = (dz + MAX_R) * SIZE + dx + MAX_R;
                int x = cx + dx;
                int z = cz + dz;
                int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                ground[i] = g;
                m.set(x, Math.max(g, cy), z);
                Biome biome = level.getBiome(m).value();
                int t = NONE;
                if (WeatherServer.isDryBiome(biome)) {
                    t = SAND_COL;
                } else if (biome.hasPrecipitation()) {
                    m.setY(Math.max(g, cy - r));
                    Biome.Precipitation p = biome.getPrecipitationAt(m);
                    t = p == Biome.Precipitation.RAIN ? RAIN_COL : p == Biome.Precipitation.SNOW ? SNOW_COL : NONE;
                }
                type[i] = t;
                if (t != NONE) {
                    m.set(x, Math.max(g, cy), z);
                    light[i] = LevelRenderer.getLightColor(level, m);
                }
            }
        }

        float wx = ClientWeather.windX;
        float wz = ClientWeather.windZ;
        float rain = ClientWeather.localRain * rainLevel;
        float snow = Math.max(ClientWeather.snow, ClientWeather.blizzard) * rainLevel;
        float bliz = ClientWeather.blizzard * rainLevel;

        // More columns are drawn at higher quality: keep the overall density the same so the view stays open.
        float norm = Math.min(1F, 10F / r);
        if (rain > 0.001F) {
            // Main streaks; they lean with the wind.
            layer(RAIN, RAIN_COL, r, camX, camY, camZ, time, 0.58F + 0.12F * rain, 3.5F, 1.6F, 0F,
                    0.5F * norm * (float) Math.pow(rain, 0.55), 0.3F + 0.35F * ClientWeather.wind, wx, wz, 0.95F, 0.97F, 1F, 11, false);
            if (rain > 0.3F) {
                layer(RAIN_DENSE, RAIN_COL, r, camX, camY, camZ, time, 0.66F + 0.1F * rain, 2.6F, 1.3F, 0F,
                        0.42F * norm * (rain - 0.3F) / 0.7F * Math.min(1F, q + 0.3F), 0.3F + 0.35F * ClientWeather.wind, wx, wz, 0.9F, 0.94F, 1F, 23, false);
            }
            if (rain > 0.55F && q > 0.5F) {
                // Distant curtains of a downpour.
                layer(RAIN_MIST, RAIN_COL, r, camX, camY, camZ, time, 0.75F, 6F, 4F, 0F,
                        0.26F * norm * (rain - 0.55F) / 0.45F, 0.3F + 0.35F * ClientWeather.wind, wx, wz, 0.85F, 0.88F, 0.92F, 37, true);
            }
        }
        float hail = ClientWeather.camHail;
        if (hail > 0.01F) {
            // Hailstones fall faster and straighter than rain, as bright pellets.
            layer(HAIL, RAIN_COL, r, camX, camY, camZ, time, 1.05F, 3F, 1.5F, 0F,
                    0.75F * norm * (float) Math.pow(hail, 0.6), 0.12F, wx, wz, 1F, 1F, 1F, 131, false);
        }
        if (snow > 0.001F) {
            float drift = 0.6F + bliz * 1.8F;
            layer(SNOW, SNOW_COL, r, camX, camY, camZ, time, 0.055F + 0.05F * bliz, 4F, 2F, drift,
                    0.95F * (float) Math.pow(snow, 0.5), 0.15F, wx, wz, 1F, 1F, 1F, 41, false);
            layer(SNOW_FINE, SNOW_COL, r, camX, camY, camZ, time, 0.07F + 0.08F * bliz, 2.2F, 1.1F, drift * 1.2F,
                    0.75F * snow * Math.min(1F, q + 0.3F), 0.15F, wx, wz, 1F, 1F, 1F, 53, false);
            if (bliz > 0.02F) {
                layer(SNOW_STREAKS, SNOW_COL, r, camX, camY, camZ, time, 0.06F, 4F, 4F, 3.2F,
                        0.85F * (float) Math.pow(bliz, 0.7), 0.1F, wx, wz, 1F, 1F, 1F, 61, false);
            }
        }
        if (sand > 0.002F) {
            layer(SAND, SAND_COL, r, camX, camY, camZ, time, 0.012F, 7F, 9F, 2.4F,
                    0.34F * (float) Math.pow(sand, 0.6), 0F, wx, wz, 1F, 1F, 1F, 71, true);
            layer(SAND, SAND_COL, r, camX, camY, camZ, time, -0.008F, 4F, 5F, 3.4F,
                    0.2F * sand, 0F, wx, wz, 1F, 0.95F, 0.9F, 83, false);
            layer(SAND_GRAINS, SAND_COL, r, camX, camY, camZ, time, 0.02F, 3F, 3F, 5.5F,
                    0.55F * sand, 0F, wx, wz, 1F, 1F, 1F, 97, false);
        }
        flush();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        lightTexture.turnOffLightLayer();
        return true;
    }

    /**
     * One sheet layer over every column of the given kind.
     *
     * @param fall       fall speed in blocks per tick (negative rises)
     * @param tileV      blocks of height covered by one texture repeat
     * @param tileU      blocks of width covered by one texture repeat
     * @param drift      how strongly the sideways wind scrolls the texture (snow, sand)
     * @param lean       how far the sheet leans with the wind per block of height (rain)
     * @param farOnly    only draw in the outer part of the radius (curtains)
     */
    private static void layer(ResourceLocation tex, int kind, int r, double camX, double camY, double camZ, double time,
                              float fall, float tileV, float tileU, float drift, float alpha, float lean, float wx, float wz,
                              float red, float green, float blue, int salt, boolean farOnly) {
        if (alpha <= 0.003F) {
            return;
        }
        int cx = Mth.floor(camX);
        int cy = Mth.floor(camY);
        int cz = Mth.floor(camZ);
        float sv = 1F / tileV;
        float su = 1F / tileU;
        boolean started = false;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int i = (dz + MAX_R) * SIZE + dx + MAX_R;
                if (type[i] != kind) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                double ox = x + 0.5 - camX;
                double oz = z + 0.5 - camZ;
                float dist = (float) Math.sqrt(ox * ox + oz * oz);
                if (dist > r + 0.5F) {
                    continue;
                }
                float fade = 1F - smooth(r * 0.55F, r + 0.5F, dist);
                fade *= farOnly ? smooth(r * 0.3F, r * 0.6F, dist) : kind == SAND_COL ? smooth(1.5F, 4.5F, dist) : smooth(0.7F, 2.2F, dist);
                float a = alpha * fade;
                if (a <= 0.004F) {
                    continue;
                }
                int g = ground[i];
                int yBottom = Math.max(g, cy - r);
                int yTop = Math.max(g, cy + r);
                if (kind == SAND_COL) {
                    yBottom = Math.max(g, cy - r);
                    yTop = Math.max(g + 2, cy + r);
                }
                if (yBottom >= yTop) {
                    continue;
                }
                if (!started) {
                    begin(tex);
                    started = true;
                }
                float px;
                float pz;
                if (dist < 0.01F) {
                    px = 1F;
                    pz = 0F;
                } else {
                    px = (float) (-oz / dist);
                    pz = (float) (ox / dist);
                }
                float h = yTop - yBottom;
                // Rain coming from upwind: the top of the sheet sits against the wind.
                float shx = -wx * lean * h;
                float shz = -wz * lean * h;
                float lateral = wx * px + wz * pz;
                int hash = (x * 3129871) ^ (z * 116129781) ^ salt * 0x5bd1e995;
                hash = hash * hash * 42317861 + hash * 11;
                float phaseU = ((hash >>> 8) & 0xFFFF) / 65536F;
                float phaseV = ((hash >>> 4) & 0xFFF) / 4096F;
                float speedVar = 0.9F + ((hash >>> 20) & 0xFF) / 255F * 0.2F;

                double uOff = phaseU - time * lateral * drift * 0.12 * su;
                if (kind == SNOW_COL) {
                    uOff += Math.sin(time * 0.035 + phaseU * 6.283) * 0.06;
                }
                double vOff = phaseV - time * fall * speedVar * sv;
                uOff -= Math.floor(uOff);
                double vBase = -yBottom * sv + vOff;
                double vShift = Math.floor(vBase - h * sv);
                float vB = (float) (vBase - vShift);
                float vT = (float) (vBase - h * sv - vShift);
                float u0 = (float) uOff;
                float u1 = u0 + su;

                float bx = (float) ox;
                float bz = (float) oz;
                float yb = (float) (yBottom - camY);
                float yt = (float) (yTop - camY);
                float hw = 0.5F;
                int lc = light[i];
                BufferBuilder b = buffer;
                b.addVertex(bx - px * hw, yb, bz - pz * hw).setUv(u0, vB).setColor(red, green, blue, a).setLight(lc);
                b.addVertex(bx + px * hw, yb, bz + pz * hw).setUv(u1, vB).setColor(red, green, blue, a).setLight(lc);
                b.addVertex(bx + shx + px * hw, yt, bz + shz + pz * hw).setUv(u1, vT).setColor(red, green, blue, a).setLight(lc);
                b.addVertex(bx + shx - px * hw, yt, bz + shz - pz * hw).setUv(u0, vT).setColor(red, green, blue, a).setLight(lc);
            }
        }
    }

    private static void begin(ResourceLocation tex) {
        if (bound != tex || buffer == null) {
            flush();
            RenderSystem.setShaderTexture(0, tex);
            buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            bound = tex;
        }
    }

    private static void flush() {
        if (buffer != null) {
            MeshData mesh = buffer.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
            buffer = null;
            bound = null;
        }
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    // ------------------------------------------------------------------ puddles

    private static void renderPuddles(ClientLevel level, double camX, double camY, double camZ, int ticks) {
        int cx = Mth.floor(camX);
        int cy = Mth.floor(camY);
        int cz = Mth.floor(camZ);
        if (Math.abs(cx - puddleCx) > 2 || Math.abs(cz - puddleCz) > 2 || Math.abs(cy - puddleCy) > 3 || ticks - puddleAge > 40 || ticks < puddleAge) {
            scanPuddles(level, cx, cy, cz);
            puddleCx = cx;
            puddleCy = cy;
            puddleCz = cz;
            puddleAge = ticks;
        }
        if (puddleCount == 0) {
            return;
        }
        float w = ClientWeather.wetness;
        float[] fog = RenderSystem.getShaderFogColor();
        // Puddles mirror the sky: tint them with the horizon colour.
        float sr = 0.55F + fog[0] * 0.45F;
        float sg = 0.58F + fog[1] * 0.42F;
        float sb = 0.62F + fog[2] * 0.38F;
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1F, -12F);
        ResourceLocation[] stages = {PUDDLE_1, PUDDLE_2, PUDDLE_3};
        for (int s = 0; s < 3; s++) {
            float a = Mth.clamp((w - s * 0.3F) / 0.3F, 0F, 1F);
            if (a <= 0.01F) {
                continue;
            }
            begin(stages[s]);
            for (int i = 0; i < puddleCount; i++) {
                float x0 = (float) (puddleX[i] - camX);
                float z0 = (float) (puddleZ[i] - camZ);
                double dx = x0 + 0.5;
                double dz = z0 + 0.5;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                float fa = a * (1F - smooth(PUDDLE_R - 5F, PUDDLE_R, dist));
                if (fa <= 0.01F) {
                    continue;
                }
                float y = (float) (puddleY[i] - camY) + 0.002F;
                float u0 = Math.floorMod(puddleX[i], 8) / 8F;
                float v0 = Math.floorMod(puddleZ[i], 8) / 8F;
                float u1 = u0 + 0.125F;
                float v1 = v0 + 0.125F;
                int lc = puddleLight[i];
                buffer.addVertex(x0, y, z0).setUv(u0, v0).setColor(sr, sg, sb, fa).setLight(lc);
                buffer.addVertex(x0, y, z0 + 1F).setUv(u0, v1).setColor(sr, sg, sb, fa).setLight(lc);
                buffer.addVertex(x0 + 1F, y, z0 + 1F).setUv(u1, v1).setColor(sr, sg, sb, fa).setLight(lc);
                buffer.addVertex(x0 + 1F, y, z0).setUv(u1, v0).setColor(sr, sg, sb, fa).setLight(lc);
            }
        }
        flush();
        RenderSystem.polygonOffset(0F, 0F);
        RenderSystem.disablePolygonOffset();
    }

    private static void scanPuddles(ClientLevel level, int cx, int cy, int cz) {
        puddleCount = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dz = -PUDDLE_R; dz <= PUDDLE_R; dz++) {
            for (int dx = -PUDDLE_R; dx <= PUDDLE_R; dx++) {
                if (dx * dx + dz * dz > PUDDLE_R * PUDDLE_R) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (top < cy - 12 || top > cy + 8) {
                    continue;
                }
                m.set(x, top, z);
                if (!level.getBlockState(m).isAir()) {
                    continue;
                }
                Biome biome = level.getBiome(m).value();
                if (!biome.hasPrecipitation() || biome.getPrecipitationAt(m) != Biome.Precipitation.RAIN) {
                    continue;
                }
                m.set(x, top - 1, z);
                BlockState s = level.getBlockState(m);
                if (!s.getFluidState().isEmpty() || !s.isFaceSturdy(level, m, Direction.UP) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SAND)
                        || s.is(BlockTags.ICE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || s.is(BlockTags.WOOL)) {
                    continue;
                }
                double topY = s.getCollisionShape(level, m).max(Direction.Axis.Y);
                if (topY <= 0.5) {
                    continue;
                }
                puddleX[puddleCount] = x;
                puddleZ[puddleCount] = z;
                puddleY[puddleCount] = (float) (top - 1 + topY);
                m.set(x, top, z);
                puddleLight[puddleCount] = LevelRenderer.getLightColor(level, m);
                puddleCount++;
            }
        }
    }
}
