package com.realweather.vehicle;

import com.realweather.ModContent;
import com.realweather.server.Tornado;
import com.realweather.server.WeatherData;
import com.realweather.server.WeatherServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * What the road and the weather do to a vehicle: grip of the surface (wet, snowy, icy, sandy), sandstorm and
 * blizzard strength where it is, flood water depth, and the tornado wind. Works on the server (unmanned vehicles)
 * and on the driver's client (the client installs its own weather source).
 */
public final class VehicleEnv {
    public float grip = 1F;
    /** 1 on loose sand. */
    public float sandy;
    public float snowy;
    public float sandstorm;
    public float blizzard;
    public float snowfall;
    public float wet;
    /** Water depth over the floor, blocks. */
    public float flood;
    public double floodFlowX, floodFlowZ;
    /** Tornado wind, blocks per tick, and how strongly it acts (0..1). */
    public double windX, windY, windZ;
    public boolean lifting;

    /** Weather where the local player is driving (set up by the client). */
    public interface ClientWeather {
        void fill(StormVehicle vehicle, VehicleEnv env);
    }

    public static ClientWeather client;

    private VehicleEnv() {}

    public static VehicleEnv sample(StormVehicle v) {
        VehicleEnv e = new VehicleEnv();
        Level level = v.level();
        BlockPos feet = v.blockPosition();
        BlockState at = level.getBlockState(feet);
        BlockState below = level.getBlockState(feet.below());
        e.grip = surfaceGrip(at, below);
        e.sandy = isSand(below) || isSand(at) ? 1F : 0F;
        e.snowy = isSnow(at) || isSnow(below) ? 1F : 0F;
        if (level.isClientSide) {
            if (client != null) {
                client.fill(v, e);
            }
        } else if (level instanceof ServerLevel sl) {
            server(sl, v, e);
        }
        // Rain makes the road wet; snow falling and blizzards cover it.
        e.grip *= 1F - 0.18F * e.wet;
        e.grip *= 1F - 0.35F * e.blizzard - 0.15F * e.snowfall;
        // Water.
        float depth = 0F;
        for (int dy = 0; dy < 3; dy++) {
            BlockPos p = feet.above(dy);
            FluidState f = level.getFluidState(p);
            if (f.isEmpty()) {
                break;
            }
            depth += f.getHeight(level, p);
            Vec3 flow = f.getFlow(level, p);
            e.floodFlowX += flow.x;
            e.floodFlowZ += flow.z;
        }
        e.flood = depth;
        if (depth > 0.2F) {
            e.grip *= 0.6F;
        }
        return e;
    }

    private static void server(ServerLevel level, StormVehicle v, VehicleEnv e) {
        BlockPos pos = v.blockPosition();
        Biome biome = level.getBiome(pos).value();
        boolean dry = WeatherServer.isDryBiome(biome);
        boolean raining = level.isRaining();
        boolean cold = biome.coldEnoughToSnow(pos);
        e.sandstorm = dry ? WeatherServer.sand : 0F;
        e.blizzard = cold && raining ? WeatherServer.blizzard : 0F;
        e.snowfall = cold && raining ? WeatherServer.snow : 0F;
        e.wet = !dry && !cold && raining ? WeatherServer.rain : 0F;
        for (Tornado t : WeatherData.get(level).tornadoes) {
            wind(e, v, t.x, t.z, t.groundY, t.coreRadius(), t.wind() * t.contact * (0.4F + 0.6F * t.strength), Tornado.LIFT[t.rating]);
        }
    }

    /** Adds one tornado's wind at the vehicle (same vortex as the one that drags creatures along). */
    public static void wind(VehicleEnv e, StormVehicle v, double tx, double tz, float groundY, float coreRadius, float vmax, float lift) {
        double rc = Math.max(2.0, coreRadius);
        double dx = v.getX() - tx;
        double dz = v.getZ() - tz;
        double r = Math.sqrt(dx * dx + dz * dz) + 0.01;
        double reach = rc * 4 + 10;
        if (r > reach || vmax < 0.02) {
            return;
        }
        double vt = r < rc ? vmax * r / rc : vmax * Math.pow(rc / r, 0.7);
        double inflow = vt * 0.32;
        double ux = dz / r * vt - dx / r * inflow;
        double uz = -dx / r * vt - dz / r * inflow;
        double height = v.getY() - groundY;
        double uy = 0;
        if (r < rc * 1.4 && lift > 0) {
            if (height < lift) {
                uy = vmax * 0.6 * (1 - r / (rc * 1.4));
            } else {
                ux += dx / r * vmax * 0.8;
                uz += dz / r * vmax * 0.8;
            }
        }
        e.windX += ux;
        e.windY += uy;
        e.windZ += uz;
        if (vt >= v.kind.liftWind * (v.spikesAnchored() ? 1.35 : 1.0) && r < rc * 1.4) {
            e.lifting = true;
        }
    }

    public static float surfaceGrip(BlockState at, BlockState below) {
        BlockState s = at.isAir() || at.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
                && !isSnow(at) ? below : at;
        if (s.is(Blocks.BLUE_ICE)) {
            return 0.05F;
        }
        if (s.is(Blocks.PACKED_ICE)) {
            return 0.08F;
        }
        if (s.is(BlockTags.ICE)) {
            return 0.11F;
        }
        if (isSnow(s)) {
            return 0.33F;
        }
        if (s.is(ModContent.HAIL_COVER.get())) {
            return 0.42F;
        }
        if (isSand(s)) {
            return 0.6F;
        }
        if (s.is(Blocks.MUD) || s.is(Blocks.SOUL_SOIL)) {
            return 0.5F;
        }
        if (s.is(Blocks.GRAVEL)) {
            return 0.7F;
        }
        if (s.is(Blocks.GRASS_BLOCK) || s.is(BlockTags.DIRT)) {
            return 0.85F;
        }
        if (s.is(Blocks.SLIME_BLOCK) || s.is(Blocks.HONEY_BLOCK)) {
            return 0.4F;
        }
        return 1F;
    }

    public static boolean isSnow(BlockState s) {
        return s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || s.is(ModContent.HAIL_SNOW.get());
    }

    public static boolean isSand(BlockState s) {
        return s.is(BlockTags.SAND) || s.is(Blocks.SOUL_SAND);
    }
}
