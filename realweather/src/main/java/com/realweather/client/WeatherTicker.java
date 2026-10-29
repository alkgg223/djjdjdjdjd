package com.realweather.client;

import com.realweather.ClientConfig;
import com.realweather.ModContent;
import com.realweather.server.WeatherServer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/** Spawns the close-up weather particles: splashes, ripples, falling drops, flakes and blown sand. */
public final class WeatherTicker {
    private static final RandomSource RANDOM = RandomSource.create();

    private WeatherTicker() {}

    public static boolean tick(ClientLevel level, int ticks, Camera camera) {
        if (!ClientWeather.active(level)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        float q = ClientConfig.qualityScale();
        switch (mc.options.particles().get()) {
            case DECREASED -> q *= 0.5F;
            case MINIMAL -> q *= 0.15F;
            default -> {
            }
        }
        Vec3 cam = camera.getPosition();
        float rainLevel = level.getRainLevel(1F);
        float rain = ClientWeather.rain * rainLevel;
        float snow = Math.max(ClientWeather.snow, ClientWeather.blizzard) * rainLevel;
        float bliz = ClientWeather.blizzard * rainLevel;
        float sand = ClientWeather.sand;
        float wx = ClientWeather.windX;
        float wz = ClientWeather.windZ;

        if (rain > 0.01F && ClientConfig.SPLASHES.get()) {
            int n = Math.round(rain * q * 42F);
            for (int i = 0; i < n; i++) {
                splash(level, cam, 13F, rain);
            }
        }
        if (rain > 0.01F && ClientConfig.RAIN_DROPS_3D.get()) {
            int n = Math.round(rain * q * 16F);
            for (int i = 0; i < n; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                double d = 0.8 + Math.sqrt(RANDOM.nextDouble()) * 6.5;
                double x = cam.x + Math.cos(a) * d - wx * 4;
                double z = cam.z + Math.sin(a) * d - wz * 4;
                double y = cam.y + 3 + RANDOM.nextDouble() * 7;
                if (precipitationAt(level, x, y, z) == Biome.Precipitation.RAIN) {
                    double fall = 0.85 + RANDOM.nextDouble() * 0.25;
                    level.addParticle(ModContent.RAIN_DROP.get(), x, y, z, wx * 0.35, -fall, wz * 0.35);
                }
            }
        }
        if (snow > 0.01F) {
            int n = Math.round(snow * q * 10F);
            for (int i = 0; i < n; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                double d = 0.8 + Math.sqrt(RANDOM.nextDouble()) * 7;
                double x = cam.x + Math.cos(a) * d - wx * 3;
                double z = cam.z + Math.sin(a) * d - wz * 3;
                double y = cam.y + 1 + RANDOM.nextDouble() * 6;
                if (precipitationAt(level, x, y, z) == Biome.Precipitation.SNOW) {
                    level.addParticle(ModContent.SNOW_FLAKE.get(), x, y, z, wx * 0.08, -0.04 - RANDOM.nextDouble() * 0.03, wz * 0.08);
                }
            }
        }
        if (bliz > 0.02F) {
            int n = Math.round(bliz * q * 30F);
            for (int i = 0; i < n; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                double d = 1 + Math.sqrt(RANDOM.nextDouble()) * 8;
                double x = cam.x + Math.cos(a) * d - wx * 6;
                double z = cam.z + Math.sin(a) * d - wz * 6;
                double y = cam.y - 1.5 + RANDOM.nextDouble() * 5;
                if (precipitationAt(level, x, y, z) == Biome.Precipitation.SNOW) {
                    double speed = 0.45 + RANDOM.nextDouble() * 0.35;
                    level.addParticle(ModContent.BLIZZARD_FLAKE.get(), x, y, z, wx * speed * 1.6, -0.05 - RANDOM.nextDouble() * 0.05, wz * speed * 1.6);
                }
            }
        }
        if (sand > 0.02F) {
            int grains = Math.round(sand * q * 28F);
            for (int i = 0; i < grains; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                double d = 1 + Math.sqrt(RANDOM.nextDouble()) * 9;
                double x = cam.x + Math.cos(a) * d - wx * 7;
                double z = cam.z + Math.sin(a) * d - wz * 7;
                double y = cam.y - 1.6 + RANDOM.nextDouble() * 4;
                if (dryAt(level, x, y, z)) {
                    double speed = 0.5 + RANDOM.nextDouble() * 0.5;
                    level.addParticle(ModContent.SAND_GRAIN.get(), x, y, z, wx * speed * 1.5, (RANDOM.nextDouble() - 0.4) * 0.05, wz * speed * 1.5);
                }
            }
            int puffs = Math.round(sand * q * 1.6F);
            for (int i = 0; i < puffs; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                double d = 2 + Math.sqrt(RANDOM.nextDouble()) * 12;
                double x = cam.x + Math.cos(a) * d - wx * 10;
                double z = cam.z + Math.sin(a) * d - wz * 10;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
                double y = top + RANDOM.nextDouble() * 3;
                if (Math.abs(y - cam.y) < 12 && dryAt(level, x, y, z)) {
                    level.addParticle(ModContent.DUST.get(), x, y, z, wx * 0.6, 0.01, wz * 0.6);
                }
            }
        }
        return true;
    }

    private static void splash(ClientLevel level, Vec3 cam, float radius, float rain) {
        double a = RANDOM.nextDouble() * Math.PI * 2;
        double d = Math.sqrt(RANDOM.nextDouble()) * radius;
        int x = Mth.floor(cam.x + Math.cos(a) * d);
        int z = Mth.floor(cam.z + Math.sin(a) * d);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (top > cam.y + 14 || top < cam.y - 16) {
            return;
        }
        BlockPos pos = new BlockPos(x, top, z);
        Biome biome = level.getBiome(pos).value();
        if (!biome.hasPrecipitation() || WeatherServer.isDryBiome(biome) || biome.getPrecipitationAt(pos) != Biome.Precipitation.RAIN) {
            return;
        }
        BlockPos below = pos.below();
        BlockState state = level.getBlockState(below);
        FluidState fluid = state.getFluidState();
        double sx = x + RANDOM.nextDouble();
        double sz = z + RANDOM.nextDouble();
        if (!fluid.isEmpty()) {
            double y = below.getY() + fluid.getHeight(level, below);
            if (fluid.is(FluidTags.LAVA)) {
                level.addParticle(ParticleTypes.SMOKE, sx, y + 0.1, sz, 0, 0.02, 0);
            } else {
                level.addParticle(ModContent.RIPPLE.get(), sx, y + 0.01, sz, 0, 0, 0);
            }
            return;
        }
        double h = state.getCollisionShape(level, below).max(Direction.Axis.Y);
        if (h <= 0) {
            h = state.getShape(level, below).max(Direction.Axis.Y);
        }
        double y = below.getY() + h;
        if (ClientWeather.wetness > 0.5F && RANDOM.nextFloat() < (ClientWeather.wetness - 0.5F) * 0.6F && state.isFaceSturdy(level, below, Direction.UP)) {
            // Drops landing in standing water on the ground.
            level.addParticle(ModContent.RIPPLE.get(), sx, y + 0.003, sz, 0, 0, 0);
        }
        level.addParticle(ModContent.SPLASH.get(), sx, y, sz, 0, rain, 0);
    }

    static Biome.Precipitation precipitationAt(ClientLevel level, double x, double y, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        if (top > y) {
            return Biome.Precipitation.NONE;
        }
        BlockPos pos = BlockPos.containing(x, y, z);
        Biome biome = level.getBiome(pos).value();
        if (!biome.hasPrecipitation() || WeatherServer.isDryBiome(biome)) {
            return Biome.Precipitation.NONE;
        }
        return biome.getPrecipitationAt(pos);
    }

    static boolean dryAt(ClientLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= y && WeatherServer.isDryBiome(level.getBiome(pos).value());
    }
}
