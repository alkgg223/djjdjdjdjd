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
        String suite = System.getenv().getOrDefault("RW_SUITE", "all");
        LOG.info("suite {}", suite);
        if (suite.contains("sky")) {
            skySuite();
        }
        if (suite.contains("vehicles")) {
            vehicleSuite();
        }
        if (suite.contains("all")) {
            weatherSuite();
        }
        step(40, () -> {
            cmd("rw info");
            LOG.info("done");
        });
        step(40, () -> Minecraft.getInstance().stop());
    }

    /** Clouds at every quality, sunset, overcast, a supercell with its tornado, lone tornadoes, radar and hail. */
    private static void skySuite() {
        step(20, () -> goTo("minecraft:plains", 0, -8));
        step(40, SelfTest::platform);
        step(5, () -> {
            cmd("time set 6000");
            weatherQuality(com.realweather.ClientConfig.Quality.ULTRA);
            look(0, -8);
        });
        step(200, () -> {
            shot("20_clouds_fair_ultra");
            skyState("fair ultra");
        });
        step(5, () -> look(90, -50));
        step(40, () -> shot("21_clouds_up"));
        step(5, () -> {
            look(0, -8);
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
        });
        step(40, () -> {
            shot("22_clouds_fair_high");
            skyState("fair high");
            weatherQuality(com.realweather.ClientConfig.Quality.MEDIUM);
        });
        step(40, () -> {
            shot("22b_clouds_fair_medium");
            skyState("fair medium");
            weatherQuality(com.realweather.ClientConfig.Quality.LOW);
        });
        step(40, () -> {
            shot("22c_clouds_fair_low");
            skyState("fair low");
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
            cmd("time set 12500");
            look(90, -6);
        });
        step(100, () -> shot("23_clouds_sunset"));
        step(5, () -> {
            cmd("time set 6000");
            cmd("rw rain 60 20");
            look(0, -25);
        });
        step(500, () -> {
            shot("24_overcast_rain");
            skyState("overcast");
        });
        step(5, () -> {
            cmd("rw clear");
            supercellBeside(260);
        });
        step(200, () -> lookAtStorm(-12));
        step(5, () -> {
            shot("25_supercell");
            skyState("supercell");
        });
        step(5, SelfTest::placeRadar);
        step(60, () -> look(180, 25));
        step(5, () -> shot("34_radar_monitor"));
        step(5, () -> com.realweather.client.radar.RadarClient.openRadar(radarPos));
        step(5, () -> {
            if (Minecraft.getInstance().screen instanceof com.realweather.client.radar.RadarScreen rs) {
                rs.setRange(2);
            }
        });
        step(60, () -> shot("35_radar_ref"));
        step(5, () -> {
            if (Minecraft.getInstance().screen instanceof com.realweather.client.radar.RadarScreen rs) {
                rs.setProduct(1);
            }
        });
        step(30, () -> {
            shot("36_radar_vel");
            Minecraft.getInstance().setScreen(null);
        });
        step(5, () -> tornadoAtUpdraft(3));
        step(400, () -> lookAtTornado(-8));
        step(5, () -> {
            shot("27_supercell_tornado_f3_ultra");
            tornadoState("supercell f3");
            skyState("supercell f3");
            quality(com.realweather.ClientConfig.TornadoQuality.LOW);
            weatherQuality(com.realweather.ClientConfig.Quality.LOW);
        });
        step(5, () -> {
            shot("28_supercell_tornado_f3_low");
            skyState("supercell f3 low");
            quality(com.realweather.ClientConfig.TornadoQuality.MEDIUM);
            weatherQuality(com.realweather.ClientConfig.Quality.MEDIUM);
        });
        step(5, () -> {
            shot("28b_supercell_tornado_f3_medium");
            quality(com.realweather.ClientConfig.TornadoQuality.ULTRA);
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
            com.realweather.client.radar.RadarClient.openRadar(radarPos);
        });
        step(60, () -> {
            if (Minecraft.getInstance().screen instanceof com.realweather.client.radar.RadarScreen rs) {
                rs.setRange(1);
                rs.setProduct(1);
            }
        });
        step(60, () -> shot("37_radar_tornado_vel"));
        step(5, () -> {
            if (Minecraft.getInstance().screen instanceof com.realweather.client.radar.RadarScreen rs) {
                rs.setProduct(3);
            }
        });
        step(60, () -> {
            shot("38_radar_tornado_cc");
            Minecraft.getInstance().setScreen(null);
        });
        step(5, () -> {
            cmd("rw clear");
            goTo("minecraft:plains", 0, 5);
        });
        step(40, SelfTest::platform);
        step(20, () -> tornadoBeside(5, 170, 0.04F));
        step(420, () -> lookAtTornado(-8));
        step(5, () -> {
            shot("29_tornado_f5_wedge");
            tornadoState("f5");
            skyState("f5");
        });
        step(5, () -> sendTornadoAtPlayer(0.3F));
        step(200, () -> lookAtTornado(-6));
        step(5, () -> {
            shot("29b_tornado_f5_close");
            tornadoState("f5 close");
        });
        step(5, () -> {
            cmd("rw clear");
            goTo("minecraft:plains", 0, 5);
        });
        step(40, SelfTest::platform);
        step(20, () -> tornadoBeside(1, 80, 0.04F));
        step(420, () -> lookAtTornado(-6));
        step(5, () -> {
            shot("30_tornado_f1_rope");
            tornadoState("f1");
        });
        // Hail on the ground and on animals, then packed hail-snow with footprints.
        step(5, () -> {
            cmd("rw clear");
            goTo("minecraft:plains", 0, 40);
        });
        step(40, () -> {
            cmd("execute as @p at @p run summon minecraft:cow ~3 ~ ~4");
            cmd("execute as @p at @p run summon minecraft:sheep ~-3 ~ ~5");
            cmd("execute as @p at @p run summon minecraft:pig ~1 ~ ~6");
            cmd("execute as @p at @p run rw hailstorm 100 10");
        });
        step(700, () -> {
            look(0, 35);
            shot("31_hail_ground");
            hailState("hail 700");
        });
        step(5, SelfTest::snowField);
        step(20, () -> walk(0));
        for (int i = 1; i <= 9; i++) {
            final int k = i;
            step(5, () -> walk(k));
        }
        step(10, () -> look(0, 55));
        step(40, () -> {
            shot("33_hail_snow_footprints");
            hailState("snow");
        });
    }

    private static BlockPos radarPos = BlockPos.ZERO;

    /** A radar tower and a monitor on the viewing platform. */
    private static void placeRadar() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            BlockPos at = p.blockPosition();
            radarPos = at.offset(2, 0, -2);
            level.setBlockAndUpdate(radarPos, com.realweather.device.ModDevices.WEATHER_RADAR.get().defaultBlockState());
            level.setBlockAndUpdate(at.offset(0, 0, -2), com.realweather.device.ModDevices.RADAR_MONITOR.get().defaultBlockState()
                    .setValue(com.realweather.device.MonitorBlock.FACING, net.minecraft.core.Direction.SOUTH));
            level.setBlockAndUpdate(at.offset(-1, 0, -2), com.realweather.device.ModDevices.RADAR_MONITOR.get().defaultBlockState()
                    .setValue(com.realweather.device.MonitorBlock.FACING, net.minecraft.core.Direction.SOUTH)
                    .setValue(com.realweather.device.MonitorBlock.MODE, 1));
            LOG.info("radar at {}", radarPos);
        });
    }

    /** A supercell whose updraft is {@code dist} blocks east of the player, moving north: we watch from the clear inflow side. */
    private static void supercellBeside(double dist) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            WeatherData d = WeatherData.get(level);
            com.realweather.server.StormCell c = com.realweather.server.StormManager.spawnFor(level, d, p.position(),
                    com.realweather.server.StormCell.SUPERCELL, 1F, 20, level.random);
            c.vx = 0F;
            c.vz = -0.08F;
            c.x = p.getX() + dist;
            c.z = p.getZ();
            double[] u = com.realweather.server.StormField.updraft(c);
            c.x += p.getX() + dist - u[0];
            c.z += p.getZ() - u[1];
            com.realweather.server.StormManager.sync(level, d);
            LOG.info("supercell #{} placed, rain at player={}", c.id, com.realweather.server.StormField.rain(c, p.getX(), p.getZ()));
        });
    }

    /** A flat field of packed hail-snow around the player (to see footprints). */
    private static void snowField() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            BlockPos c = p.blockPosition();
            net.minecraft.world.level.block.state.BlockState snow = com.realweather.ModContent.HAIL_SNOW.get().defaultBlockState()
                    .setValue(com.realweather.server.CoverBlock.LAYERS, 3);
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -12; dz <= 4; dz++) {
                    for (int dy = 0; dy <= 6; dy++) {
                        level.setBlockAndUpdate(c.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    }
                    level.setBlockAndUpdate(c.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
                    level.setBlockAndUpdate(c.offset(dx, 0, dz), snow);
                }
            }
            p.teleportTo(level, c.getX() + 0.5, c.getY() + 0.4, c.getZ() + 3.5, 180F, 30F);
            level.getEntities(EntityType.COW, e -> true).forEach(e -> e.teleportTo(c.getX() + 3.5, c.getY() + 0.4, c.getZ() - 6.5));
        });
    }

    private static void weatherSuite() {
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
    }

    private static void step(int delay, Runnable action) {
        STEPS.add(new Step(delay, action));
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null
                || (mc.screen != null && !(mc.screen instanceof com.realweather.client.radar.RadarScreen))) {
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


    private static void weatherQuality(com.realweather.ClientConfig.Quality q) {
        com.realweather.ClientConfig.QUALITY.set(q);
    }

    private static void skyState(String label) {
        Minecraft mc = Minecraft.getInstance();
        LOG.info("[{}] sky sprites={} storms={} tornadoesDrawn={} fps={} quality={} tornadoQuality={}", label,
                com.realweather.client.sky.SkyBillboards.count(), com.realweather.client.ClientStorms.cells().size(),
                com.realweather.client.tornado.TornadoRenderer.lastDrawn, mc.getFps(), com.realweather.ClientConfig.QUALITY.get(),
                com.realweather.ClientConfig.TORNADO_QUALITY.get());
    }

    /** Turns the player toward the nearest storm cell. */
    private static void lookAtStorm(float pitch) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            WeatherData d = WeatherData.get(server.overworld());
            com.realweather.server.StormCell best = null;
            double bd = Double.MAX_VALUE;
            for (com.realweather.server.StormCell c : d.storms) {
                double dd = Math.hypot(c.x - p.getX(), c.z - p.getZ());
                if (dd < bd) {
                    bd = dd;
                    best = c;
                }
            }
            if (best == null) {
                LOG.warn("no storm to look at");
                return;
            }
            double[] u = com.realweather.server.StormField.updraft(best);
            float yaw = (float) Math.toDegrees(Math.atan2(-(u[0] - p.getX()), u[1] - p.getZ()));
            p.teleportTo(server.overworld(), p.getX(), p.getY(), p.getZ(), yaw, pitch);
            LOG.info("storm #{} type={} radius={} strength={} updraft distance={}", best.id, best.type, best.radius, best.strength(),
                    (int) Math.hypot(u[0] - p.getX(), u[1] - p.getZ()));
        });
    }

    /** A tornado under the updraft of the nearest supercell. */
    private static void tornadoAtUpdraft(int rating) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            WeatherData d = WeatherData.get(level);
            for (com.realweather.server.StormCell c : d.storms) {
                if (c.type != com.realweather.server.StormCell.SUPERCELL) {
                    continue;
                }
                double[] u = com.realweather.server.StormField.updraft(c);
                com.realweather.server.Tornado t = com.realweather.server.TornadoManager.spawnAt(level, d,
                        new net.minecraft.world.phys.Vec3(u[0], p.getY(), u[1]), rating, level.random);
                t.heading = (float) Math.atan2(c.vx, -c.vz);
                t.speed = (float) Math.hypot(c.vx, c.vz);
                c.tornadoId = t.id;
                LOG.info("spawned F{} tornado under supercell #{} at distance {}", rating, c.id, (int) Math.hypot(u[0] - p.getX(), u[1] - p.getZ()));
                return;
            }
            LOG.warn("no supercell for a tornado");
        });
    }

    private static void hailState(String label) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            int pellets = 0;
            int snow = 0;
            BlockPos c = p.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(c.offset(-24, -6, -24), c.offset(24, 6, 24))) {
                net.minecraft.world.level.block.state.BlockState s = level.getBlockState(pos);
                if (s.is(com.realweather.ModContent.HAIL_COVER.get())) {
                    pellets++;
                } else if (s.is(com.realweather.ModContent.HAIL_SNOW.get())) {
                    snow++;
                }
            }
            LOG.info("[{}] hail blocks={} hail-snow blocks={} hailAt={} player health={}", label, pellets, snow,
                    com.realweather.server.StormManager.hailAt(WeatherData.get(level), p.getX(), p.getZ()), p.getHealth());
        });
        LOG.info("[{}] client camHail={} hail coverage on mobs: {}", label, ClientWeather.camHail,
                com.realweather.client.hail.HailCoverage.debug());
    }

    /** Covers the ground around the player with three layers of hail-snow (to test footprints quickly). */
    private static void hailSnowPatch() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            net.minecraft.world.level.block.state.BlockState snow = com.realweather.ModContent.HAIL_SNOW.get().defaultBlockState()
                    .setValue(com.realweather.server.CoverBlock.LAYERS, 3);
            BlockPos c = p.blockPosition();
            for (int dx = -10; dx <= 10; dx++) {
                for (int dz = -10; dz <= 10; dz++) {
                    BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, c.offset(dx, 0, dz));
                    net.minecraft.world.level.block.state.BlockState at = level.getBlockState(top);
                    if (at.is(com.realweather.ModContent.HAIL_COVER.get()) || at.isAir()) {
                        level.setBlockAndUpdate(top, snow);
                    } else if (level.getBlockState(top.below()).is(com.realweather.ModContent.HAIL_SNOW.get())) {
                        level.setBlockAndUpdate(top.below(), snow);
                    }
                }
            }
        });
    }

    /** Steps the player north in strides, so footprints are left behind. */
    private static void walk(int stepIndex) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            p.teleportTo(level, p.getX(), p.getY(), p.getZ() - 0.8, 180F, 30F);
        });
    }


    // ------------------------------------------------------------------------------------ vehicles
    private static final int[] VEHICLE_IDS = new int[3];

    private static void vehicleSuite() {
        step(5, () -> {
            cmd("rw clear");
            cmd("time set 6000");
            goTo("minecraft:plains", 0, 15);
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
        });
        step(40, SelfTest::testPad);
        step(20, SelfTest::spawnVehicles);
        step(60, () -> lookAt(VEHICLE_IDS[1], 14));
        step(40, () -> {
            shot("40_vehicles");
            vehicleState("parked");
        });
        step(5, () -> ride(VEHICLE_IDS[1]));
        step(30, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            turnClient(0F, 24F);
        });
        step(30, () -> {
            shot("41_pickup_interior");
            vehicleState("seated");
        });
        step(5, () -> {
            Minecraft.getInstance().options.hideGui = false;
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            turnClient(0F, 15F);
            drive(1F, 0F);
        });
        step(80, () -> {
            shot("42_pickup_driving_hud");
            vehicleState("driving");
            drive(1F, 1F);
        });
        step(30, () -> {
            shot("42b_pickup_turning");
            vehicleState("turning");
            drive(-1F, 0F);
        });
        step(60, () -> {
            drive(0F, 0F);
            vehicleState("braked");
            cmd("rw blizzard 100 10");
        });
        step(400, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            turnClient(0F, 8F);
        });
        step(20, () -> {
            shot("43_blizzard_windshield");
            vehicleState("blizzard");
            vehicleAction(VEHICLE_IDS[1], 1);
            vehicleAction(VEHICLE_IDS[1], 1);
        });
        step(50, () -> {
            shot("44_wipers");
            vehicleState("wipers");
        });
        step(5, () -> {
            cmd("rw blizzard stop");
            cmd("rw clear");
            dismount();
        });
        step(20, () -> ride(VEHICLE_IDS[2]));
        step(20, () -> {
            vehicleAction(VEHICLE_IDS[2], 4);
            tornadoNearVehicle(VEHICLE_IDS[2], 3, 70);
        });
        step(300, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            faceTornadoClient(-4F);
        });
        step(20, () -> {
            shot("45_intercept_anchored");
            vehicleState("intercept");
            tornadoState("intercept");
        });
        step(5, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            faceTornadoClient(6F);
        });
        step(20, () -> shot("46_intercept_interior"));
        step(5, () -> {
            cmd("rw clear");
            dismount();
            Minecraft.getInstance().options.hideGui = true;
            Minecraft.getInstance().player.input = new net.minecraft.client.player.KeyboardInput(Minecraft.getInstance().options);
        });
    }

    /** A flat stone pad south of the player for the vehicles. */
    private static void testPad() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            BlockPos c = p.blockPosition();
            for (int dx = -14; dx <= 14; dx++) {
                for (int dz = -4; dz <= 40; dz++) {
                    for (int dy = 0; dy <= 6; dy++) {
                        level.setBlock(c.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    }
                    level.setBlock(c.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                }
            }
            p.teleportTo(level, c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0F, 14F);
        });
    }

    /** Turns the local player's view without a teleport (a teleport would pull a passenger out of the vehicle). */
    private static void turnClient(float yaw, float pitch) {
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        if (p.getVehicle() != null) {
            yaw += p.getVehicle().getYRot();
        }
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.yRotO = yaw;
        p.setXRot(pitch);
        p.xRotO = pitch;
    }

    private static void faceTornadoClient(float pitch) {
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        com.realweather.client.tornado.ClientTornadoes.T best = null;
        double bd = Double.MAX_VALUE;
        for (com.realweather.client.tornado.ClientTornadoes.T t : com.realweather.client.tornado.ClientTornadoes.all()) {
            double d = Math.hypot(t.x - p.getX(), t.z - p.getZ());
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        if (best == null) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-(best.x - p.getX()), best.z - p.getZ()));
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.yRotO = yaw;
        p.setXRot(pitch);
        p.xRotO = pitch;
    }

    private static void spawnVehicles() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            com.realweather.vehicle.VehicleType[] kinds = com.realweather.vehicle.VehicleType.values();
            for (int i = 0; i < kinds.length; i++) {
                double x = p.getX() - 7 + i * 7;
                double z = p.getZ() + 9;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.minecraft.util.Mth.floor(x),
                        net.minecraft.util.Mth.floor(z));
                com.realweather.vehicle.StormVehicle v = com.realweather.vehicle.VehicleRegistry.type(kinds[i]).create(level);
                v.moveTo(x, y, z, 200F + i * 15F, 0F);
                v.setColor(kinds[i].colors[0]);
                level.addFreshEntity(v);
                VEHICLE_IDS[i] = v.getId();
                // A block under one wheel of the sedan, to see it lean.
                if (i == 0) {
                    level.setBlockAndUpdate(new BlockPos(net.minecraft.util.Mth.floor(x + 0.8), y, net.minecraft.util.Mth.floor(z - 1.2)),
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                }
            }
            LOG.info("vehicles spawned {} {} {}", VEHICLE_IDS[0], VEHICLE_IDS[1], VEHICLE_IDS[2]);
        });
    }

    private static void lookAt(int entityId, float pitch) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            net.minecraft.world.entity.Entity e = server.overworld().getEntity(entityId);
            if (e == null) {
                LOG.warn("no entity {}", entityId);
                return;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-(e.getX() - p.getX()), e.getZ() - p.getZ()));
            p.teleportTo(server.overworld(), p.getX(), p.getY(), p.getZ(), yaw, pitch);
        });
    }

    private static void ride(int entityId) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            net.minecraft.world.entity.Entity e = server.overworld().getEntity(entityId);
            if (e != null) {
                p.startRiding(e, true);
                LOG.info("riding {} : {}", e.getName().getString(), p.getVehicle() == e);
            }
        });
    }

    private static void dismount() {
        MinecraftServer server = server();
        server.execute(() -> server.getPlayerList().getPlayers().get(0).stopRiding());
    }

    /** Holds the driver's pedals and steering (the vehicle physics read them like the keyboard). */
    private static void drive(float forward, float turnRight) {
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        p.input = new net.minecraft.client.player.Input() {
            @Override
            public void tick(boolean sneaking, float speed) {
                this.forwardImpulse = forward;
                this.leftImpulse = -turnRight;
                this.up = forward > 0;
                this.down = forward < 0;
            }
        };
    }

    private static void vehicleAction(int entityId, int action) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            if (server.overworld().getEntity(entityId) instanceof com.realweather.vehicle.StormVehicle v) {
                v.applyInput(p, 0F, 0F, false, action);
            }
        });
    }

    private static void tornadoNearVehicle(int entityId, int rating, double dist) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            net.minecraft.world.entity.Entity e = level.getEntity(entityId);
            if (e == null) {
                return;
            }
            WeatherData d = WeatherData.get(level);
            com.realweather.server.Tornado t = com.realweather.server.TornadoManager.spawnAt(level, d,
                    new net.minecraft.world.phys.Vec3(e.getX() + dist, e.getY(), e.getZ() + 20), rating, level.random);
            t.heading = (float) Math.atan2(e.getX() - t.x, -(e.getZ() - t.z));
            t.speed = 0.06F;
            LOG.info("tornado F{} near the intercept vehicle", rating);
        });
    }

    private static void vehicleState(String label) {
        Minecraft mc = Minecraft.getInstance();
        for (int id : VEHICLE_IDS) {
            if (mc.level != null && mc.level.getEntity(id) instanceof com.realweather.vehicle.StormVehicle v) {
                LOG.info("[{}] {} speed={} km/h steer={} gear={} pitch={} roll={} lift={} spikes={} wipers={} glass={} bodySnow={} rpm={} wind={} riding={}",
                        label, v.kind.id, Math.round(v.displaySpeed() * 72), v.displaySteer(), v.gear(), v.pitch, v.roll, v.lift, v.spikeDeploy, v.wipers(),
                        com.realweather.client.vehicle.VehicleClient.glassCover(v), v.bodySnow, Math.round(v.rpm), v.measuredWind,
                        mc.player != null && mc.player.getVehicle() == v);
            }
        }
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
