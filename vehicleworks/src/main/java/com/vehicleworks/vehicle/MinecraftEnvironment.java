package com.vehicleworks.vehicle;

import com.vehicleworks.Config;
import com.vehicleworks.physics.TerrainBoxes;
import com.vehicleworks.physics.aero.Atmosphere;
import com.vehicleworks.physics.sim.Environment;
import com.vehicleworks.physics.sim.VehicleSimulation;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridges the physics engine to a Minecraft level: terrain collision boxes, water/lava
 * surfaces with waves, ISA atmosphere by altitude, wind and turbulence, ground height.
 * All lookups are cached per tick; chunks are never force-loaded.
 */
public final class MinecraftEnvironment implements Environment {
    private final ServerLevel level;
    private final Map<VehicleSimulation, TerrainBoxes> terrain = new IdentityHashMap<>();
    private final Map<BlockState, double[]> shapeCache = new IdentityHashMap<>();
    private final Long2DoubleOpenHashMap surfaceCache = new Long2DoubleOpenHashMap();
    private final Long2DoubleOpenHashMap densityCache = new Long2DoubleOpenHashMap();
    private final Long2ObjectOpenHashMap<Object> frozenNotice = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
    private long time;
    private double windBaseX, windBaseZ, storm;
    public int lastTerrainBlocks;

    public MinecraftEnvironment(ServerLevel level) {
        this.level = level;
    }

    /** Called once per tick before stepping. */
    public void beginTick() {
        surfaceCache.clear();
        densityCache.clear();
        time = level.getGameTime();
        storm = level.isThundering() ? 2.5 : (level.isRaining() ? 1.5 : 1.0);
        double dir = (time / 6000.0) * 0.7 + Math.sin(time / 9000.0) * 1.3;
        double speed = Config.SERVER.windSpeed.get() * storm * (0.7 + 0.3 * Math.sin(time / 2400.0));
        windBaseX = Math.cos(dir) * speed;
        windBaseZ = Math.sin(dir) * speed;
        lastTerrainBlocks = 0;
    }

    /** True if every chunk under the bounds is loaded. */
    public boolean regionLoaded(double[] b) {
        int cx0 = Mth.floor(b[0] - 2) >> 4, cz0 = Mth.floor(b[2] - 2) >> 4, cx1 = Mth.floor(b[3] + 2) >> 4, cz1 = Mth.floor(b[5] + 2) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++)
                if (level.getChunkSource().getChunkNow(cx, cz) == null) return false;
        return true;
    }

    /** Collect terrain boxes for a simulation (called by the physics world once per tick). */
    public void prepareTerrain(VehicleSimulation sim, double[] bounds, double dtTick) {
        TerrainBoxes t = terrain.computeIfAbsent(sim, k -> new TerrainBoxes());
        t.clear();
        double v = sim.body.linearVelocity.length() * dtTick + sim.body.angularVelocity.length() * 2 + 1.2;
        int x0 = Mth.floor(bounds[0] - v), y0 = Mth.floor(bounds[1] - v), z0 = Mth.floor(bounds[2] - v);
        int x1 = Mth.floor(bounds[3] + v), y1 = Mth.floor(bounds[4] + v), z1 = Mth.floor(bounds[5] + v);
        y0 = Math.max(y0, level.getMinBuildHeight());
        y1 = Math.min(y1, level.getMaxBuildHeight() - 1);
        if (y1 < y0) return;
        // fast path: vehicle entirely above the terrain surface (heightmap sampled coarsely)
        int maxTop = level.getMinBuildHeight();
        int step = Math.max(1, Math.min(4, (x1 - x0) / 8 + 1));
        for (int x = x0; x <= x1 + step - 1; x += step)
            for (int z = z0; z <= z1 + step - 1; z += step) {
                int xx = Math.min(x, x1), zz = Math.min(z, z1);
                LevelChunk ch = level.getChunkSource().getChunkNow(xx >> 4, zz >> 4);
                if (ch == null) continue;
                maxTop = Math.max(maxTop, ch.getHeight(Heightmap.Types.MOTION_BLOCKING, xx & 15, zz & 15) + 1);
            }
        maxTop += 2;
        if (y0 > maxTop) return;
        y1 = Math.min(y1, maxTop);
        long volume = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        if (volume > 400_000) {
            // extremely large query: restrict to a shell near the vehicle bottom
            y1 = Math.min(y1, y0 + 6);
        }
        t.setQueryRange(x0, y0, z0, x1, y1, z1);
        int count = 0;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                int bx0 = Math.max(x0, cx << 4), bx1 = Math.min(x1, (cx << 4) + 15);
                int bz0 = Math.max(z0, cz << 4), bz1 = Math.min(z1, (cz << 4) + 15);
                for (int x = bx0; x <= bx1; x++)
                    for (int z = bz0; z <= bz1; z++)
                        for (int y = y0; y <= y1; y++) {
                            mpos.set(x, y, z);
                            BlockState st = ch.getBlockState(mpos);
                            if (st.isAir()) continue;
                            double[] boxes = boxesFor(st, mpos);
                            if (boxes.length == 0) continue;
                            float mu = BlockPhysics.friction(st);
                            boolean full = boxes.length == 6 && boxes[0] == 0 && boxes[1] == 0 && boxes[2] == 0 && boxes[3] == 1 && boxes[4] == 1 && boxes[5] == 1;
                            t.beginBlock(x, y, z, full);
                            for (int i = 0; i < boxes.length; i += 6) {
                                t.addBox(x + boxes[i], y + boxes[i + 1], z + boxes[i + 2], x + boxes[i + 3], y + boxes[i + 4], z + boxes[i + 5], mu);
                            }
                            count++;
                        }
            }
        }
        t.endBlock();
        lastTerrainBlocks += count;
    }

    private double[] boxesFor(BlockState st, BlockPos pos) {
        if (st.hasOffsetFunction()) return toArray(st.getCollisionShape(level, pos));
        double[] b = shapeCache.get(st);
        if (b == null) {
            b = toArray(st.getCollisionShape(level, pos));
            shapeCache.put(st, b);
        }
        return b;
    }

    private static double[] toArray(VoxelShape shape) {
        if (shape.isEmpty()) return new double[0];
        List<AABB> l = shape.toAabbs();
        double[] out = new double[l.size() * 6];
        for (int i = 0; i < l.size(); i++) {
            AABB a = l.get(i);
            out[i * 6] = a.minX; out[i * 6 + 1] = a.minY; out[i * 6 + 2] = a.minZ;
            out[i * 6 + 3] = a.maxX; out[i * 6 + 4] = a.maxY; out[i * 6 + 5] = a.maxZ;
        }
        return out;
    }

    public void forget(VehicleSimulation sim) {
        terrain.remove(sim);
    }

    @Override
    public TerrainBoxes terrainFor(VehicleSimulation sim) {
        TerrainBoxes t = terrain.get(sim);
        if (t == null) {
            t = new TerrainBoxes();
            t.clear();
            terrain.put(sim, t);
        }
        return t;
    }

    @Override
    public void wind(double x, double y, double z, Vector3d out) {
        double alt = Math.max(0, y - level.getSeaLevel());
        double shear = 0.6 + 0.4 * Math.min(1.5, alt / 60.0);
        double turb = Config.SERVER.turbulence.get() * storm * (0.4 + Math.min(1.0, alt / 120.0));
        double t = time / 20.0;
        // smooth pseudo-random gusts (sum of incommensurate sines)
        double gx = Math.sin(t * 0.73 + x * 0.013) + 0.6 * Math.sin(t * 1.91 + z * 0.021);
        double gy = 0.5 * Math.sin(t * 1.27 + x * 0.017 + z * 0.011) + 0.3 * Math.sin(t * 3.1);
        double gz = Math.sin(t * 0.87 + z * 0.015) + 0.6 * Math.sin(t * 2.33 + x * 0.019);
        out.set(windBaseX * shear + gx * turb, gy * turb * 0.6, windBaseZ * shear + gz * turb);
    }

    @Override
    public double ambientTemperature(double y) {
        return Atmosphere.temperature(altitude(y)) - 273.15;
    }

    @Override
    public double altitude(double y) {
        return Math.max(-500, (y - level.getSeaLevel()) * Config.SERVER.altitudeScale.get());
    }

    @Override
    public double heightAboveSurface(double x, double y, double z, double maxDist) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        LevelChunk ch = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
        if (ch == null) return maxDist;
        int h = ch.getHeight(Heightmap.Types.MOTION_BLOCKING, bx & 15, bz & 15) + 1;
        double d = y - h;
        return d < 0 ? (d > -3 ? 0 : maxDist) : Math.min(maxDist, d);
    }

    @Override
    public float frictionAt(double x, double y, double z) {
        mpos.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        LevelChunk ch = level.getChunkSource().getChunkNow(mpos.getX() >> 4, mpos.getZ() >> 4);
        if (ch == null) return 0.7f;
        BlockState st = ch.getBlockState(mpos);
        if (st.isAir()) {
            mpos.move(0, -1, 0);
            st = ch.getBlockState(mpos);
        }
        return BlockPhysics.friction(st);
    }

    @Override
    public double gravity() {
        return Config.SERVER.gravity.get();
    }

    @Override
    public void sample(double x, double y, double z, double[] out) {
        int bx = Mth.floor(x), by = Mth.floor(y), bz = Mth.floor(z);
        long key = BlockPos.asLong(bx, by, bz);
        double surface;
        double density;
        if (surfaceCache.containsKey(key)) {
            surface = surfaceCache.get(key);
            density = densityCache.get(key);
        } else {
            surface = Double.NEGATIVE_INFINITY;
            density = 0;
            LevelChunk ch = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
            if (ch != null) {
                // search down (element may be partially submerged) then up to the surface
                int start = 0;
                boolean found = false;
                for (int dy = 0; dy >= -2; dy--) {
                    mpos.set(bx, by + dy, bz);
                    FluidState fs = ch.getFluidState(mpos);
                    if (!fs.isEmpty()) { start = by + dy; found = true; density = fs.is(FluidTags.LAVA) ? Atmosphere.LAVA_DENSITY : Atmosphere.WATER_DENSITY; break; }
                }
                if (found) {
                    int top = start;
                    for (int i = 0; i < 24; i++) {
                        mpos.set(bx, top + 1, bz);
                        if (top + 1 >= level.getMaxBuildHeight() || ch.getFluidState(mpos).isEmpty()) break;
                        top++;
                    }
                    mpos.set(bx, top, bz);
                    FluidState fs = ch.getFluidState(mpos);
                    surface = top + fs.getHeight(level, mpos);
                    // waves on open water
                    double amp = Config.SERVER.waveAmplitude.get() * (level.isThundering() ? 2.2 : (level.isRaining() ? 1.4 : 1.0));
                    if (amp > 0 && density < 2000) {
                        double t = time / 20.0;
                        surface += amp * (Math.sin(x * 0.21 + t * 1.3) * 0.6 + Math.sin(z * 0.17 - t * 1.1) * 0.4);
                    }
                }
            }
            surfaceCache.put(key, surface);
            densityCache.put(key, density);
        }
        out[0] = surface;
        out[1] = density;
        out[2] = 0;
        out[3] = 0;
    }

    @Override
    public double airDensity(double y) {
        return Atmosphere.density(altitude(y));
    }
}
