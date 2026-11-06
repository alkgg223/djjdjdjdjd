package com.arkragnarok.test;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.entity.RexEntity;
import com.arkragnarok.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dedicated-server self test, enabled with -Dark.smoketest=true (the CI "smoke" run).  Spawns the Rex, lets its
 * AI run, fires its triggered animations, saddles it, damages and kills it, then prints a result and stops.
 */
@EventBusSubscriber(modid = ArkRagnarok.MODID)
public final class SmokeTest {
    private static final Logger LOG = LoggerFactory.getLogger("ARK-SMOKE");
    private static final boolean ENABLED = Boolean.getBoolean("ark.smoketest");
    private static int tick = -1;
    private static RexEntity rex;
    private static int failures;

    private SmokeTest() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        if (!ENABLED) return;
        LOG.info("ARK-SMOKE start");
        tick = 0;
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        if (!ENABLED || tick < 0) return;
        MinecraftServer server = e.getServer();
        ServerLevel level = server.overworld();
        try {
            tick++;
            if (tick == 20) {
                BlockPos spawn = level.getSharedSpawnPos();
                BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.offset(8, 0, 8));
                rex = ModEntities.REX.get().spawn(level, at, MobSpawnType.COMMAND);
                check(rex != null, "rex spawned");
                if (rex != null) LOG.info("ARK-SMOKE rex at {} hp {}", rex.blockPosition(), rex.getHealth());
            }
            if (rex == null) {
                if (tick > 40) finish(server);
                return;
            }
            if (tick == 60) {
                rex.roar();
                rex.triggerAnim("action", "bite");
                rex.setSaddled(true);
                check(rex.isSaddled(), "saddle state");
            }
            if (tick == 120) {
                check(rex.isAlive(), "alive after AI ticks");
                rex.hurt(level.damageSources().generic(), 30f);
                check(rex.getHealth() < rex.getMaxHealth(), "takes damage");
            }
            if (tick == 140) rex.kill();
            if (tick == 160) check(rex.isDeadOrDying(), "death animation phase");
            if (tick == 240) {
                check(rex.isRemoved(), "corpse removed after the death animation");
                finish(server);
            }
        } catch (Throwable t) {
            failures++;
            LOG.error("ARK-SMOKE exception", t);
            finish(server);
        }
    }

    private static void check(boolean ok, String what) {
        if (ok) LOG.info("ARK-SMOKE ok   {}", what);
        else {
            failures++;
            LOG.error("ARK-SMOKE FAIL {}", what);
        }
    }

    private static void finish(MinecraftServer server) {
        LOG.info("ARK-SMOKE RESULT {} ({} failures)", failures == 0 ? "PASS" : "FAIL", failures);
        tick = -1;
        server.halt(false);
    }
}
