package com.vehicleworks;

import com.mojang.logging.LogUtils;
import com.vehicleworks.command.VehicleCommands;
import com.vehicleworks.network.Payloads;
import com.vehicleworks.registry.*;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehiclePhysicsManager;
import com.vehicleworks.vehicle.VehicleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;
import org.slf4j.Logger;

/**
 * VehicleWorks — an independent vehicle engineering sandbox: block-built vehicles with a
 * custom rigid-body physics engine, aerodynamics, propulsion, buoyancy and weapon systems.
 */
@Mod(VehicleWorks.MODID)
public class VehicleWorks {
    public static final String MODID = "vehicleworks";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static CommonProxy PROXY = new CommonProxy();

    public VehicleWorks(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModTabs.TABS.register(modBus);
        modBus.addListener(Payloads::register);
        container.registerConfig(ModConfig.Type.COMMON, Config.SERVER_SPEC); // one file in config/, editable from the Mods screen
        container.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        NeoForge.EVENT_BUS.addListener(VehicleWorks::onLevelTick);
        NeoForge.EVENT_BUS.addListener(VehicleWorks::onExplosion);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> VehicleCommands.register(e.getDispatcher(), e.getBuildContext()));
        if (Integer.getInteger("vehicleworks.autostop", 0) > 0) {
            // automated runs: generate the world, then stop cleanly
            int stopAfter = Integer.getInteger("vehicleworks.autostop");
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> {
                if (e.getServer().getTickCount() == stopAfter) e.getServer().halt(false);
            });
        }
    }

    private static void onLevelTick(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel sl) VehiclePhysicsManager.get(sl).tick();
    }

    /** Explosions (weapons, TNT, creepers) damage vehicle blocks within their radius. */
    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        Vec3 c = event.getExplosion().center();
        float power = event.getExplosion().radius();
        double r = power * 2.0;
        AABB box = new AABB(c, c).inflate(r);
        for (VehicleEntity v : VehicleRegistry.near(sl, box)) {
            Vector3d local = v.worldToLocal(c.x, c.y, c.z, new Vector3d());
            int rr = (int) Math.ceil(r);
            for (int x = -rr; x <= rr; x++)
                for (int y = -rr; y <= rr; y++)
                    for (int z = -rr; z <= rr; z++) {
                        int bx = (int) Math.floor(local.x) + x, by = (int) Math.floor(local.y) + y, bz = (int) Math.floor(local.z) + z;
                        long key = BlockPos.asLong(bx, by, bz);
                        if (!v.structure.contains(key)) continue;
                        double d = Math.sqrt((bx + 0.5 - local.x) * (bx + 0.5 - local.x) + (by + 0.5 - local.y) * (by + 0.5 - local.y) + (bz + 0.5 - local.z) * (bz + 0.5 - local.z));
                        if (d > r) continue;
                        v.damageBlock(key, (float) (power * 45 * (1 - d / r)));
                    }
            // blast impulse
            var sim = v.simulation();
            if (sim != null) {
                Vector3d dir = new Vector3d(v.getX() - c.x, v.getY() - c.y, v.getZ() - c.z);
                double dist = Math.max(1, dir.length());
                dir.div(dist);
                double impulse = power * 4000 / dist;
                sim.body.applyImpulseAtPoint(dir.x * impulse, dir.y * impulse, dir.z * impulse, c.x, c.y, c.z);
                sim.body.wake();
            }
        }
    }
}
