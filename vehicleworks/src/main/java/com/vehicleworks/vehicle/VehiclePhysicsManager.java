package com.vehicleworks.vehicle;

import com.vehicleworks.Config;
import com.vehicleworks.physics.sim.PhysicsWorld;
import com.vehicleworks.physics.sim.VehicleSimulation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * One physics world per server level. Steps every loaded vehicle together at the start of
 * the level tick with a fixed timestep (1/20 s split into substeps), independent of any
 * client frame rate.
 */
public final class VehiclePhysicsManager {
    private static final Map<ServerLevel, VehiclePhysicsManager> MANAGERS = new WeakHashMap<>();

    public final ServerLevel level;
    public final PhysicsWorld world = new PhysicsWorld();
    public final MinecraftEnvironment env;
    private final Map<VehicleSimulation, VehicleEntity> owners = new IdentityHashMap<>();
    private final double[] bounds = new double[6];
    public long lastNanos;

    private VehiclePhysicsManager(ServerLevel level) {
        this.level = level;
        this.env = new MinecraftEnvironment(level);
        world.listener = (a, b, voxelA, voxelB, impulse, approach) -> {
            VehicleEntity va = owners.get(a);
            if (va != null) va.onImpact(voxelA, impulse, approach, b != null ? owners.get(b) : null);
            if (b != null) {
                VehicleEntity vb = owners.get(b);
                if (vb != null) vb.onImpact(voxelB, impulse, approach, va);
            }
        };
    }

    public static synchronized VehiclePhysicsManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, VehiclePhysicsManager::new);
    }

    public void tick() {
        long t0 = System.nanoTime();
        List<VehicleEntity> vehicles = VehicleRegistry.all(level);
        world.sims.clear();
        owners.clear();
        world.baseSubsteps = Config.SERVER.substeps.get();
        world.maxSubsteps = Math.max(world.baseSubsteps, Config.SERVER.maxSubsteps.get());
        env.beginTick();
        List<VehicleEntity> active = new ArrayList<>();
        for (VehicleEntity v : vehicles) {
            if (v.isRemoved()) continue;
            VehicleSimulation sim = v.simulation();
            if (sim == null) continue;
            v.prePhysics();
            sim.body.maxLinearSpeed = Config.SERVER.maxSpeed.get();
            sim.worldBounds(bounds);
            boolean loaded = env.regionLoaded(bounds) && level.isPositionEntityTicking(v.blockPosition());
            sim.frozen = !loaded || v.isFrozen();
            if (!sim.frozen) env.prepareTerrain(sim, bounds, 0.05);
            world.sims.add(sim);
            owners.put(sim, v);
            active.add(v);
        }
        if (!world.sims.isEmpty()) world.step(0.05, env);
        for (VehicleEntity v : active) v.postPhysics();
        lastNanos = System.nanoTime() - t0;
    }

    public void forget(VehicleSimulation sim) {
        env.forget(sim);
    }
}
