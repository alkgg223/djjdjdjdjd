package com.realweather.selftest;

import com.mojang.datafixers.util.Pair;
import com.realweather.client.ClientWeather;
import com.realweather.server.WeatherData;
import com.realweather.server.WeatherServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Development-only automated run (compiled only with -Pselftest): drives the weather through every
 * scene, takes screenshots and logs the state, then quits.
 */
public final class SelfTest {
    private static final Logger LOG = LoggerFactory.getLogger("RW-SELFTEST");
    private static final List<Step> STEPS = new ArrayList<>();
    private static int index;
    private static int wait;
    private static int ready;

    private record Step(int delay, Runnable action) {}

    private SelfTest() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(SelfTest::onTick);
        step(0, () -> {
            cmd("gamerule doDaylightCycle false");
            cmd("gamerule doWeatherCycle false");
            cmd("gamerule doMobSpawning false");
            cmd("difficulty peaceful");
            cmd("time set 6000");
            cmd("rw clear");
            cmd("effect give @a minecraft:resistance infinite 255 true");
            cmd("effect give @a minecraft:fire_resistance infinite 0 true");
            cmd("effect give @a minecraft:water_breathing infinite 0 true");
            Minecraft.getInstance().options.hideGui = true;
        });
        step(20, () -> goTo("minecraft:river", 30, 10));
        step(80, () -> {
            cmd("rw rain 100 30");
            cmd("rw thunder 70 30");
            cmd("rw wind 45 60");
        });
        step(400, () -> {
            shot("1_rain");
            state("rain");
            cmd("execute at @p run summon minecraft:villager ~4 ~ ~2");
            cmd("execute at @p run summon minecraft:villager ~-3 ~ ~5");
            cmd("execute at @p run summon minecraft:villager ~2 ~ ~-4");
        });
        step(200, () -> {
            villagers();
            look(150, 20);
        });
        step(60, () -> state("on the bank"));
        step(200, () -> {
            shot("2_rain_puddles");
            state("rain later");
        });
        step(500, () -> {
            floods();
            look(-60, 30);
        });
        step(60, () -> shot("3_flood"));
        step(5, () -> {
            cmd("rw clear");
            cmd("rw wind 12 90");
            cmd("time set 1000");
            look(-90, 18);
        });
        step(400, () -> {
            shot("4_water_morning");
            state("calm");
            villagers();
            floods();
        });
        step(5, () -> {
            cmd("time set 6000");
            look(20, 40);
        });
        step(120, () -> shot("5_water_noon"));
        step(5, () -> goTo("minecraft:snowy_plains", 30, 15));
        step(60, () -> cmd("rw blizzard 100 10"));
        step(760, () -> {
            shot("6_blizzard");
            state("blizzard");
            player();
        });
        step(5, () -> {
            cmd("rw blizzard stop");
            cmd("rw snow 70 10");
            cmd("rw wind 15");
        });
        step(760, () -> {
            shot("7_snow");
            state("snow");
        });
        step(5, () -> {
            cmd("rw clear");
            goTo("minecraft:desert", 30, 18);
        });
        step(60, () -> cmd("rw sandstorm 100 10"));
        step(760, () -> {
            shot("8_sandstorm");
            state("sandstorm");
        });
        // Tornadoes, watched from a platform above the plain.
        step(5, () -> {
            cmd("rw sandstorm stop");
            cmd("rw clear");
            goTo("minecraft:plains", 0, 5);
            cmd("time set 6000");
            quality(com.realweather.ClientConfig.TornadoQuality.ULTRA);
        });
        step(40, SelfTest::platform);
        step(20, () -> tornadoBeside(3, 75, 0.05F));
        step(5, () -> {
            cmd("rw rain 25 20");
            cmd("rw thunder 40 20");
        });
        step(420, () -> lookAtTornado(-6));
        step(5, () -> {
            shot("10_tornado_f3_ultra");
            tornadoState("f3");
            quality(com.realweather.ClientConfig.TornadoQuality.LOW);
        });
        step(5, () -> {
            shot("11_tornado_f3_low");
            quality(com.realweather.ClientConfig.TornadoQuality.MEDIUM);
        });
        step(5, () -> {
            shot("11b_tornado_f3_medium");
            quality(com.realweather.ClientConfig.TornadoQuality.HIGH);
        });
        step(5, () -> {
            shot("12_tornado_f3_high");
            cmd("rw tornado stop");
        });
        step(330, () -> lookAtTornado(-6));
        step(5, () -> {
            shot("13_tornado_roping_out");
            tornadoState("roping out");
            quality(com.realweather.ClientConfig.TornadoQuality.ULTRA);
        });
        step(300, () -> tornadoBeside(5, 120, 0.05F));
        step(420, () -> lookAtTornado(-8));
        step(5, () -> {
            shot("14_tornado_f5");
            tornadoState("f5");
            sendTornadoAtPlayer(0.3F);
        });
        step(190, () -> lookAtTornado(-6));
        step(5, () -> {
            shot("15_tornado_f5_close");
            tornadoState("f5 close");
            player();
        });
        step(120, () -> {
            tornadoState("f5 passing");
            player();
        });
        step(5, () -> {
            cmd("rw tornado stop");
            goTo("minecraft:plains", 0, 5);
        });
        step(40, SelfTest::platform);
        step(560, () -> tornadoBeside(0, 45, 0.04F));
        step(420, () -> lookAtTornado(-4));
        step(5, () -> {
            shot("16_tornado_f0");
            tornadoState("f0");
        });
        step(5, () -> {
            cmd("rw clear");
            goTo("minecraft:plains", 0, 5);
            cmd("time set 18000");
        });
        step(60, () -> {
            cmd("rw rain 90 5");
            cmd("rw thunder 100 5");
        });
        step(500, () -> {
            shot("9_night_storm");
            state("night storm");
        });
        step(5, SelfTest::platform);
        step(40, () -> cmd("execute as @p at @p run summon minecraft:lightning_bolt ^ ^-18 ^45"));
        step(2, () -> {
            shot("17_lightning");
            LOG.info("lightning flash={} worldLight={}", com.realweather.client.LightningFx.flash, com.realweather.client.LightningFx.worldLight);
        });
        step(3, () -> shot("18_lightning_restrike"));
        step(40, () -> {
            cmd("rw info");
            LOG.info("done");
        });
        step(40, () -> Minecraft.getInstance().stop());
    }

    private static void step(int delay, Runnable action) {
        STEPS.add(new Step(delay, action));
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.screen != null) {
            return;
        }
        if (ready < 120) {
            if (++ready == 120) {
                LOG.info("world ready, fps={}", mc.getFps());
                wait = STEPS.isEmpty() ? 0 : STEPS.get(0).delay;
            }
            return;
        }
        if (index >= STEPS.size()) {
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step s = STEPS.get(index++);
        try {
            s.action.run();
        } catch (RuntimeException e) {
            LOG.error("step {} failed", index, e);
        }
        if (index < STEPS.size()) {
            wait = STEPS.get(index).delay;
        }
    }

    private static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    private static void cmd(String command) {
        MinecraftServer server = server();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void look(float yaw, float pitch) {
        // Relative to each player: the console itself sits at world spawn.
        cmd("execute as @a at @s run tp @s ~ ~ ~ " + yaw + " " + pitch);
    }

    private static void goTo(String biome, float yaw, float pitch) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biome));
            Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(key), player.blockPosition(), 6400, 32, 64);
            if (found == null) {
                LOG.warn("biome {} not found", biome);
                return;
            }
            BlockPos p = found.getFirst();
            // Stand on dry ground next to the spot (not in the river or lake itself).
            int bx = p.getX(), bz = p.getZ();
            search:
            for (int r = 0; r <= 24; r += 2) {
                for (int a = 0; a < 16; a++) {
                    int x = p.getX() + (int) Math.round(Math.cos(a * Math.PI / 8) * r);
                    int z = p.getZ() + (int) Math.round(Math.sin(a * Math.PI / 8) * r);
                    level.getChunk(x >> 4, z >> 4);
                    int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (level.getFluidState(new BlockPos(x, top - 1, z)).isEmpty()) {
                        bx = x;
                        bz = z;
                        break search;
                    }
                }
            }
            level.getChunk(bx >> 4, bz >> 4);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            LOG.info("going to {} at {} {} {}", biome, bx, y, bz);
            player.teleportTo(level, bx + 0.5, y + 0.1, bz + 0.5, yaw, pitch);
        });
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> LOG.info("screenshot {}: {}", name, c.getString()));
        LOG.info("fps at {}: {}", name, mc.getFps());
    }

    private static void state(String label) {
        Minecraft mc = Minecraft.getInstance();
        LOG.info("[{}] global rain={} snow={} thunder={} wind={} sand={} blizzard={} | cam rain={} snow={} sand={} blizzard={} exposure={} covered={} wetness={} | rainLevel={} particles={} sounds={}",
                label, ClientWeather.rain, ClientWeather.snow, ClientWeather.thunder, ClientWeather.wind, ClientWeather.sand, ClientWeather.blizzard,
                ClientWeather.camRain, ClientWeather.camSnow, ClientWeather.camSand, ClientWeather.camBlizzard, ClientWeather.exposure,
                ClientWeather.covered + " underwater=" + ClientWeather.underwater + " groundNear=" + ClientWeather.groundNear + " camY=" + (int) mc.gameRenderer.getMainCamera().getPosition().y,
                ClientWeather.wetness, mc.level.getRainLevel(1F), mc.particleEngine.countParticles(),
                com.realweather.client.WeatherSounds.debug());
    }

    private static void villagers() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            for (Villager v : level.getEntities(EntityType.VILLAGER, v -> true)) {
                LOG.info("villager at {} activity={} shelterTag={} walkTarget={}", v.blockPosition(), v.getBrain().getActiveNonCoreActivity(),
                        v.getPersistentData().getBoolean("realweather_shelter"),
                        v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition()));
            }
        });
    }

    private static void quality(com.realweather.ClientConfig.TornadoQuality q) {
        com.realweather.ClientConfig.TORNADO_QUALITY.set(q);
    }

    /** Turns the player to face the nearest tornado. */
    private static void lookAtTornado(float pitch) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            WeatherData d = WeatherData.get(server.overworld());
            com.realweather.server.Tornado t = com.realweather.server.TornadoManager.nearest(d, p.getX(), p.getZ(), 2000);
            if (t == null) {
                LOG.warn("no tornado to look at");
                return;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-(t.x - p.getX()), t.z - p.getZ()));
            p.teleportTo(server.overworld(), p.getX(), p.getY(), p.getZ(), yaw, pitch);
        });
    }

    /** Puts the player on a small stone platform well above the ground, for an open view. */
    private static void platform() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            int x = net.minecraft.util.Mth.floor(p.getX());
            int z = net.minecraft.util.Mth.floor(p.getZ());
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) + 18;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlock(new BlockPos(x + dx, y - 1, z + dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                }
            }
            p.teleportTo(level, x + 0.5, y, z + 0.5, p.getYRot(), p.getXRot());
            LOG.info("platform at {} {} {}", x, y, z);
        });
    }

    /** A slow tornado to the east of the player, drifting north across the view. */
    private static void tornadoBeside(int rating, double dist, float speed) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            WeatherData d = WeatherData.get(level);
            com.realweather.server.Tornado t = com.realweather.server.TornadoManager.spawnAt(level, d,
                    new net.minecraft.world.phys.Vec3(p.getX() + dist, p.getY(), p.getZ() + 25), rating, level.random);
            t.heading = 0F;
            t.speed = speed;
            LOG.info("spawned F{} tornado at {} {}", rating, (int) t.x, (int) t.z);
        });
    }

    private static void sendTornadoAtPlayer(float speed) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            WeatherData d = WeatherData.get(server.overworld());
            com.realweather.server.Tornado t = com.realweather.server.TornadoManager.nearest(d, p.getX(), p.getZ(), 2000);
            if (t != null) {
                t.heading = (float) Math.atan2(p.getX() - t.x, -(p.getZ() - t.z));
                t.speed = speed;
            }
        });
    }

    private static void tornadoState(String label) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            WeatherData d = WeatherData.get(server.overworld());
            for (com.realweather.server.Tornado t : d.tornadoes) {
                LOG.info("[{}] tornado #{} F{} stage={} contact={} strength={} at {} {} {} distance={} blocksDestroyed={}", label, t.id, t.rating, t.stage,
                        t.contact, t.strength, (int) t.x, (int) t.groundY, (int) t.z, (int) Math.sqrt(p.distanceToSqr(t.x, p.getY(), t.z)),
                        com.realweather.server.TornadoManager.blocksDestroyed);
            }
        });
        Minecraft mc = Minecraft.getInstance();
        LOG.info("[{}] client tornadoes={} drawn={} particles={} fps={}", label, com.realweather.client.tornado.ClientTornadoes.all().size(),
                com.realweather.client.tornado.TornadoRenderer.lastDrawn, mc.particleEngine.countParticles(), mc.getFps());
    }

    private static void floods() {
        MinecraftServer server = server();
        server.execute(() -> LOG.info("flood blocks: {}", WeatherData.get(server.overworld()).floodList.size()));
    }

    private static void player() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            LOG.info("player frozen ticks={} / {} at {} velocity={} health={}", p.getTicksFrozen(), p.getTicksRequiredToFreeze(), p.blockPosition(),
                    p.getDeltaMovement(), p.getHealth());
        });
    }
}
