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
        step(5, () -> goTo("minecraft:snowy_plains", 30, 8));
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
            goTo("minecraft:desert", 30, 6);
        });
        step(60, () -> cmd("rw sandstorm 100 10"));
        step(760, () -> {
            shot("8_sandstorm");
            state("sandstorm");
        });
        step(5, () -> {
            cmd("rw sandstorm stop");
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
        cmd("tp @a ~ ~ ~ " + yaw + " " + pitch);
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
            level.getChunk(p.getX() >> 4, p.getZ() >> 4);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
            LOG.info("going to {} at {} {} {}", biome, p.getX(), y, p.getZ());
            player.teleportTo(level, p.getX() + 0.5, y + 0.1, p.getZ() + 0.5, yaw, pitch);
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
                ClientWeather.covered, ClientWeather.wetness, mc.level.getRainLevel(1F), mc.particleEngine.countParticles(),
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

    private static void floods() {
        MinecraftServer server = server();
        server.execute(() -> LOG.info("flood blocks: {}", WeatherData.get(server.overworld()).floodList.size()));
    }

    private static void player() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            LOG.info("player frozen ticks={} / {} at {}", p.getTicksFrozen(), p.getTicksRequiredToFreeze(), p.blockPosition());
        });
    }
}
