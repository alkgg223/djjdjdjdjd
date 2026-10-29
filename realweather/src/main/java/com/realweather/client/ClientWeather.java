package com.realweather.client;

import com.realweather.net.WeatherSyncPayload;
import com.realweather.server.WeatherServer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client copy of the server weather, smoothed, plus what the camera currently experiences. */
public final class ClientWeather {
    private static float tRain, tSnow, tThunder, tWind, tWindAngle, tSand, tBlizzard;
    private static boolean synced;

    // Global (whole overworld) values, smoothed.
    public static float rain, snow, thunder, wind, windAngle, sand, blizzard;
    /** Wind velocity direction scaled by speed (blocks the weather drifts per block of fall, roughly). */
    public static float windX, windZ;

    // What the camera experiences right now (biomes around it, rain level, shelter).
    public static float camRain, camSnow, camSand, camBlizzard;
    /** 0 = deep indoors, 1 = open sky all around. */
    public static float exposure = 1F;
    /** True when there is a roof directly above the camera. */
    public static boolean covered;
    /** 1 near the ground, fading out when high above it. */
    public static float groundNear = 1F;
    /** How wet the ground is (puddles). */
    public static float wetness;
    public static int ticks;

    private static float sRain, sSnow, sSand, sBlizzard, sOpen = 1F, sGround = 1F;

    private ClientWeather() {}

    public static void onSync(WeatherSyncPayload p) {
        tRain = p.rain();
        tSnow = p.snow();
        tThunder = p.thunder();
        tWind = p.wind();
        tWindAngle = p.windAngle();
        tSand = p.sand();
        tBlizzard = p.blizzard();
        if (!synced) {
            rain = tRain;
            snow = tSnow;
            thunder = tThunder;
            wind = tWind;
            windAngle = tWindAngle;
            sand = tSand;
            blizzard = tBlizzard;
            synced = true;
        }
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        synced = false;
        tRain = tSnow = tThunder = tWind = tSand = tBlizzard = 0F;
        rain = snow = thunder = wind = sand = blizzard = 0F;
        camRain = camSnow = camSand = camBlizzard = 0F;
        wetness = 0F;
        WeatherSounds.stopAll();
    }

    public static boolean active(ClientLevel level) {
        return level != null && level.dimension() == Level.OVERWORLD;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            WeatherSounds.stopAll();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        ticks++;
        boolean ow = active(level);
        if (!synced) {
            // Server without the mod: follow vanilla weather.
            tRain = level.isRaining() ? (level.isThundering() ? 0.85F : 0.5F) : 0F;
            tSnow = tRain;
            tThunder = level.isThundering() ? 0.6F : 0F;
            tWind = 0.2F + tRain * 0.3F + tThunder * 0.3F;
        }
        rain = approach(rain, tRain, 0.01F);
        snow = approach(snow, tSnow, 0.01F);
        thunder = approach(thunder, tThunder, 0.01F);
        wind = approach(wind, tWind, 0.005F);
        sand = approach(sand, tSand, 0.004F);
        blizzard = approach(blizzard, tBlizzard, 0.004F);
        float da = Mth.wrapDegrees((float) Math.toDegrees(tWindAngle - windAngle));
        windAngle += (float) Math.toRadians(da) * 0.02F;
        // windAngle is a compass bearing (0 = north = -Z, 90 = east = +X).
        windX = (float) Math.sin(windAngle) * wind;
        windZ = (float) -Math.cos(windAngle) * wind;

        Camera camera = mc.gameRenderer.getMainCamera();
        if (ow && ticks % 4 == 0 && camera.isInitialized()) {
            sample(level, camera.getPosition());
        }
        if (!ow) {
            sRain = sSnow = sSand = sBlizzard = 0F;
        }
        camRain = approach(camRain, sRain, 0.02F);
        camSnow = approach(camSnow, sSnow, 0.02F);
        camSand = approach(camSand, sSand, 0.01F);
        camBlizzard = approach(camBlizzard, sBlizzard, 0.01F);
        exposure += (sOpen - exposure) * 0.15F;
        groundNear += (sGround - groundNear) * 0.1F;

        float rainHere = camRain;
        if (rainHere > 0.05F) {
            wetness = Math.min(1F, wetness + rainHere * 0.0035F);
        } else {
            wetness = Math.max(0F, wetness - (sand > 0.1F ? 0.004F : 0.0012F));
        }

        WeatherSounds.tick(mc, level);
        LightningFx.tick(mc, level);
    }

    private static void sample(ClientLevel level, Vec3 cam) {
        int cx = Mth.floor(cam.x);
        int cy = Mth.floor(cam.y);
        int cz = Mth.floor(cam.z);
        float rainLevel = level.getRainLevel(1F);
        float wRain = 0, wSnow = 0, wSand = 0, wOpen = 0, total = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                int x = cx + i * 5;
                int z = cz + j * 5;
                float w = 1F / (1F + (i * i + j * j) * 0.5F);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                m.set(x, Math.max(top, cy), z);
                Biome biome = level.getBiome(m).value();
                if (WeatherServer.isDryBiome(biome)) {
                    wSand += w;
                } else if (biome.hasPrecipitation()) {
                    Biome.Precipitation p = biome.getPrecipitationAt(m);
                    if (p == Biome.Precipitation.RAIN) {
                        wRain += w;
                    } else if (p == Biome.Precipitation.SNOW) {
                        wSnow += w;
                    }
                }
                if (top <= cy + 1) {
                    wOpen += w;
                }
                total += w;
            }
        }
        sRain = wRain / total * rainLevel * rain;
        sSnow = wSnow / total * rainLevel * Math.max(snow, blizzard);
        sBlizzard = wSnow / total * rainLevel * blizzard;
        sSand = wSand / total * sand;
        sOpen = wOpen / total;
        int camTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz);
        covered = camTop > cy + 1;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        sGround = Mth.clamp(1F - (float) (cam.y - ground - 6) / 40F, 0.15F, 1F);
    }

    static float approach(float cur, float target, float step) {
        return cur < target ? Math.min(target, cur + step) : Math.max(target, cur - step);
    }
}
