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
        if (suite.contains("types")) {
            typesSuite();
        }
        if (suite.contains("new")) {
            newSuite();
        }
        if (suite.contains("quake")) {
            quakeSuite();
        }
        if (suite.contains("eclipse")) {
            eclipseSuite();
        }
        if (suite.contains("opt")) {
            optSuite();
        }
        if (suite.contains("space")) {
            spaceSuite();
        }
        if (suite.contains("telescope")) {
            telescopeSuite();
        }
        if (suite.contains("planets")) {
            planetsSuite();
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

    /** Every tornado type, one after the other, fully developed, seen from the platform; then a downpour close up. */
    private static void typesSuite() {
        step(5, () -> {
            cmd("rw clear");
            cmd("time set 6000");
            goTo("minecraft:plains", 0, 5);
            quality(com.realweather.ClientConfig.TornadoQuality.ULTRA);
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
        });
        step(40, SelfTest::platform);
        step(160, () -> LOG.info("waiting for the world around the platform to load"));
        String[][] list = {{"rope", "1", "75"}, {"cone", "2", "100"}, {"bowl", "3", "125"}, {"drill_bit", "2", "100"}, {"wedge", "5", "175"},
                {"truncated_cone", "3", "125"}, {"bulb", "4", "150"}, {"hourglass", "3", "125"}, {"stovepipe", "4", "150"},
                {"segmented", "1", "80"}, {"landspout", "0", "70"}, {"flared_base", "2", "100"}};
        int k = 0;
        for (String[] e : list) {
            String name = e[0];
            int rating = Integer.parseInt(e[1]);
            double dist = Double.parseDouble(e[2]);
            String label = String.format("60_%02d_%s_f%d", k++, name, rating);
            step(5, SelfTest::removeTornadoes);
            step(10, () -> typeTornado(name, rating, dist));
            step(100, () -> lookAtTornado(-7));
            step(5, () -> {
                shot(label);
                tornadoState(name);
            });
        }
        // The same tornado at each graphics setting (and how fast it runs).
        for (com.realweather.ClientConfig.TornadoQuality q : com.realweather.ClientConfig.TornadoQuality.values()) {
            step(5, () -> quality(q));
            step(60, () -> {
                shot("61_flared_base_" + q.name().toLowerCase(java.util.Locale.ROOT));
                tornadoState("quality " + q);
            });
        }
        step(5, () -> {
            removeTornadoes();
            quality(com.realweather.ClientConfig.TornadoQuality.ULTRA);
            cmd("rw clear");
            goTo("minecraft:forest", 0, 2);
        });
        step(60, () -> cmd("rw rain 100 5"));
        step(200, () -> {
            Minecraft.getInstance().player.setXRot(35F);
            shot("62_downpour_ground");
            skyState("downpour");
        });
    }

    /** 1.5.0: ULTRA rain, snow and a sandstorm on the plains, a custom tornado, and tsunamis at the coast. */
    private static void newSuite() {
        step(5, () -> {
            cmd("rw clear");
            cmd("time set 6000");
            goTo("minecraft:plains", 0, 2);
            weatherQuality(com.realweather.ClientConfig.Quality.ULTRA);
        });
        step(80, () -> cmd("rw rain 100 5"));
        step(220, () -> {
            shot("70_rain_ultra");
            skyState("rain ultra");
        });
        step(5, () -> cmd("rw snow 100 5"));
        step(320, () -> {
            shot("71_snow_plains");
            skyState("snow plains");
        });
        step(5, () -> cmd("rw sandstorm 100 3"));
        step(300, () -> {
            shot("72_sandstorm_plains");
            skyState("sand plains");
        });
        step(5, () -> {
            cmd("rw clear");
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
        });
        step(40, SelfTest::platform);
        step(120, () -> LOG.info("waiting for the world to load"));
        step(10, () -> {
            MinecraftServer server = server();
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = server.overworld();
                WeatherData d = WeatherData.get(level);
                com.realweather.server.Tornado t = com.realweather.server.TornadoManager.spawnCustom(level, d,
                        new net.minecraft.world.phys.Vec3(p.getX() + 190, p.getY(), p.getZ() + 25), com.realweather.server.TornadoShape.WEDGE, 60, true,
                        level.random);
                t.heading = 0F;
                t.speed = 0.02F;
                t.stage = com.realweather.server.Tornado.MATURE;
                t.contact = 1F;
                t.strength = 1F;
                t.life = 100000;
                com.realweather.server.TornadoManager.sync(level, d);
                LOG.info("custom wedge: rating F{} core {}", t.rating, t.coreRadius());
            });
        });
        step(100, () -> lookAtTornado(-7));
        step(5, () -> {
            shot("73_custom_wedge_60");
            tornadoState("custom wedge 60");
        });
        step(5, () -> {
            removeTornadoes();
            cmd("rw clear");
            goTo("minecraft:beach", 0, 0);
        });
        step(60, SelfTest::platform);
        step(120, () -> LOG.info("waiting for the coast to load"));
        // Onto dry land near the shore, so the wave has land to climb and flood.
        step(5, SelfTest::moveToLand);
        step(60, () -> LOG.info("waiting for the land to load"));
        step(5, () -> tsunamiAtPlayer(300));
        step(5, SelfTest::carInWavePath);
        step(155, () -> {
            lookAtWave(-4);
            tsunamiState("approach");
        });
        step(5, () -> shot("74_tsunami_300_far"));
        step(170, () -> {
            lookAtWave(-6);
            tsunamiState("near");
        });
        step(5, () -> shot("75_tsunami_300_near"));
        step(130, () -> tsunamiState("passing"));
        step(5, () -> shot("76_tsunami_300_passing"));
        step(160, () -> {
            Minecraft.getInstance().player.setXRot(30F);
            tsunamiState("flooded");
        });
        step(5, () -> shot("77_tsunami_flood"));
        step(5, () -> carState("after the wave"));
        // Down into the flood, as a survival player: the air runs out under the water.
        step(5, () -> {
            cmd("gamemode survival @a");
            MinecraftServer server = server();
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = server.overworld();
                int y = com.realweather.server.TsunamiGround.top(level, p.getBlockX() - 3, p.getBlockZ());
                landX = p.getX();
                landY = p.getY();
                landZ = p.getZ();
                p.teleportTo(level, p.getBlockX() - 2.5, y, p.getZ(), p.getYRot(), 10F);
                p.setAirSupply(p.getMaxAirSupply());
                LOG.info("down into the flood at y {} (flood surface {}, block there {})", y,
                        com.realweather.server.TsunamiManager.WAVES.isEmpty() ? Float.NaN
                                : com.realweather.server.TsunamiManager.WAVES.get(0).floodSurface(level, p.getX(), p.getZ()),
                        level.getBlockState(p.blockPosition().above()));
            });
        });
        step(30, () -> tsunamiState("in the flood (1.5 s)"));
        step(60, () -> tsunamiState("in the flood"));
        step(5, () -> shot("77b_tsunami_in_flood"));
        step(5, () -> {
            cmd("gamemode creative @a");
            lookAtCar(40F);
        });
        step(5, SelfTest::hover);
        step(5, () -> shot("77c_tsunami_car"));
        // Stop it and check that all of its water drains away again.
        step(5, () -> {
            MinecraftServer server = server();
            server.execute(() -> com.realweather.server.TsunamiManager.stop(server.overworld()));
        });
        step(5, SelfTest::backToPlatform);
        step(300, () -> tsunamiState("draining"));
        step(5, () -> shot("77d_tsunami_draining"));
        step(340, () -> {
            MinecraftServer server = server();
            server.execute(() -> LOG.info("tsunami water left after draining: waves={} lists={}", com.realweather.server.TsunamiManager.WAVES.size(),
                    WeatherData.get(server.overworld()).tsunamiWater.values().stream().mapToInt(java.util.List::size).sum()));
        });
        step(5, () -> {
            stopTsunamis();
            removeTornadoes();
        });
        step(40, () -> tsunamiAtPlayer(1000));
        step(200, () -> {
            lookAtWave(-12);
            tsunamiState("1000");
        });
        step(5, () -> shot("78_tsunami_1000"));
        step(5, SelfTest::stopTsunamis);
    }

    /** 1.7.0: ULTRA 3D clouds, an earthquake next to buildings of different materials, deep hail. */
    private static void quakeSuite() {
        step(5, () -> {
            cmd("rw clear");
            cmd("time set 5000");
            goTo("minecraft:plains", 0, -12);
            weatherQuality(com.realweather.ClientConfig.Quality.ULTRA_3D);
        });
        step(60, SelfTest::platform);
        step(120, () -> {
            look(30F, -14F);
            LOG.info("3d clouds available={}", com.realweather.client.sky.VolumeClouds.available());
        });
        step(5, () -> shot("80_clouds_3d_fair"));
        step(5, () -> look(150F, -25F));
        step(5, () -> shot("80b_clouds_3d_fair_up"));
        step(5, () -> cmd("time set 12500"));
        step(40, () -> shot("80c_clouds_3d_sunset"));
        step(5, () -> {
            cmd("time set 6000");
            cmd("rw rain 80 5");
        });
        step(260, () -> shot("81_clouds_3d_rain"));
        step(5, () -> {
            cmd("rw clear");
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
        });
        // Buildings of different materials around a spot on the ground, then a magnitude 8 earthquake under them.
        step(20, SelfTest::buildTown);
        step(40, () -> {
            townState("before");
            look(0F, 38F);
        });
        step(5, () -> shot("82_town_before"));
        step(5, () -> {
            MinecraftServer server = server();
            server.execute(() -> {
                com.realweather.server.Earthquake q = com.realweather.server.QuakeManager.spawn(server.overworld(), townX + 4, townZ + 14, 8F,
                        server.overworld().random, false);
                LOG.info("quake M{} duration {} cracks {} felt radius {}", q.magnitude, q.duration, q.cracks().size(), q.feltRadius());
            });
        });
        step(120, () -> {
            quakeState("shaking");
            shot("83_quake_shaking");
        });
        step(260, () -> {
            quakeState("strong");
            shot("83b_quake_strong");
        });
        step(400, () -> {
            quakeState("after");
            townState("after");
            look(0F, 55F);
        });
        step(10, () -> shot("84_quake_after"));
        step(5, () -> look(90F, 45F));
        step(10, () -> shot("84b_quake_cracks"));
        // A small one: fine cracks only.
        step(5, () -> {
            MinecraftServer server = server();
            server.execute(() -> com.realweather.server.QuakeManager.spawn(server.overworld(), townX - 30, townZ, 4.5F, server.overworld().random, false));
        });
        step(200, () -> quakeState("small"));
        // Deep hail.
        step(5, () -> cmd("execute as @a at @s run rw hailstorm 100 6"));
        step(1200, () -> {
            snowState("hail 60 s");
            look(0F, 40F);
        });
        step(5, () -> shot("85_hail_deep"));
        step(1200, () -> snowState("hail 120 s"));
        step(5, () -> shot("85b_hail_deeper"));
        step(5, () -> cmd("rw clear"));
    }

    /** 1.8.0: a total eclipse (partial phase, diamond ring, totality), eclipse glasses, cloud types, lightning in the clouds. */
    /**
     * 1.9.0: every funnel down on the ground (the bowl hanging over its debris), the top of the funnel seen from close
     * below, and max optimize off and on side by side for the same views (they must look the same).
     */
    private static void optSuite() {
        step(5, () -> {
            cmd("rw clear");
            cmd("time set 6000");
            goTo("minecraft:plains", 0, 5);
            quality(com.realweather.ClientConfig.TornadoQuality.ULTRA);
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
        });
        step(40, SelfTest::platform);
        step(160, () -> LOG.info("waiting for the world around the platform to load"));
        String[][] list = {{"cone", "0", "70"}, {"rope", "1", "75"}, {"cone", "2", "100"}, {"drill_bit", "2", "100"}, {"truncated_cone", "3", "125"},
                {"bulb", "4", "150"}, {"wedge", "5", "175"}, {"bowl", "3", "125"}};
        int k = 0;
        for (String[] e : list) {
            String name = e[0];
            int rating = Integer.parseInt(e[1]);
            double dist = Double.parseDouble(e[2]);
            String label = String.format("90_%02d_%s_f%d", k++, name, rating);
            step(5, SelfTest::removeTornadoes);
            step(10, () -> typeTornado(name, rating, dist));
            step(100, () -> lookAtTornado(-7));
            step(5, () -> {
                shot(label);
                tornadoState(name);
            });
        }
        // Close under the top of a funnel: no ring of flat ellipses round it.
        step(5, SelfTest::removeTornadoes);
        step(10, () -> typeTornado("cone", 3, 60));
        step(100, () -> lookAtTornado(-35));
        step(5, () -> shot("91_cone_top_close"));
        // Max optimize off and on: the tornado in the middle, then at the edge of the screen.
        for (int edge = 0; edge < 2; edge++) {
            int ed = edge;
            for (int on = 0; on < 2; on++) {
                boolean opt = on == 1;
                step(5, () -> {
                    com.realweather.ClientConfig.MAX_OPTIMIZE.set(opt);
                    lookAtTornado(-7);
                });
                if (ed == 1) {
                    step(3, () -> Minecraft.getInstance().player.setYRot(Minecraft.getInstance().player.getYRot() + 42F));
                }
                step(20, () -> {
                    shot("92_tornado_" + (ed == 1 ? "edge" : "middle") + (opt ? "_opt_on" : "_opt_off"));
                    skyState("tornado " + (ed == 1 ? "edge" : "middle") + " maxOptimize=" + opt);
                });
            }
        }
        // A supercell with a tornado and rain, looking at it and looking away along the edge of the storm.
        step(5, () -> {
            removeTornadoes();
            supercellBeside(300);
        });
        step(60, () -> tornadoAtUpdraft(3));
        step(120, () -> lookAtStorm(-6));
        for (int on = 0; on < 2; on++) {
            boolean opt = on == 1;
            step(5, () -> com.realweather.ClientConfig.MAX_OPTIMIZE.set(opt));
            step(20, () -> {
                shot("93_supercell" + (opt ? "_opt_on" : "_opt_off"));
                skyState("supercell maxOptimize=" + opt);
            });
        }
        // Fair-weather clouds and rain right here, at the sprite and the 3D levels.
        step(5, () -> {
            removeTornadoes();
            cmd("rw clear");
            cmd("execute as @a at @s run rw rain 60 5");
        });
        step(200, () -> look(30, -12));
        for (com.realweather.ClientConfig.Quality q : new com.realweather.ClientConfig.Quality[]{com.realweather.ClientConfig.Quality.ULTRA,
                com.realweather.ClientConfig.Quality.MEDIUM_3D}) {
            for (int on = 0; on < 2; on++) {
                boolean opt = on == 1;
                step(5, () -> {
                    weatherQuality(q);
                    com.realweather.ClientConfig.MAX_OPTIMIZE.set(opt);
                });
                step(40, () -> {
                    shot("94_rain_" + q.name().toLowerCase(java.util.Locale.ROOT) + (opt ? "_opt_on" : "_opt_off"));
                    skyState("rain " + q + " maxOptimize=" + opt);
                });
            }
        }
        step(5, () -> {
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
            com.realweather.ClientConfig.MAX_OPTIMIZE.set(true);
            Minecraft.getInstance().player.setXRot(40F);
        });
        step(20, () -> shot("95_rain_ground_opt_on"));
        step(5, () -> com.realweather.ClientConfig.MAX_OPTIMIZE.set(false));
        step(20, () -> shot("95_rain_ground_opt_off"));
    }

    /** 1.10.0: the four kinds of lightning, the rainbow, annular and hybrid eclipses, and every sky event. */
    private static void spaceSuite() {
        step(5, () -> {
            cmd("rw clear");
            cmd("time set 15000");
            cmd("rw sky natural off");
            goTo("minecraft:plains", 0, 0);
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
        });
        step(60, SelfTest::platform);
        step(140, () -> LOG.info("waiting for the world around the platform to load"));
        // Sky events at night, each looked at directly.
        int[] night = {com.realweather.server.SkyEvent.LUNAR_ECLIPSE, com.realweather.server.SkyEvent.SUPERMOON,
                com.realweather.server.SkyEvent.METEOR_SHOWER, com.realweather.server.SkyEvent.COMET, com.realweather.server.SkyEvent.FIREBALLS,
                com.realweather.server.SkyEvent.CONJUNCTION, com.realweather.server.SkyEvent.ALIGNMENT, com.realweather.server.SkyEvent.OCCULTATION,
                com.realweather.server.SkyEvent.AURORA, com.realweather.server.SkyEvent.ISS, com.realweather.server.SkyEvent.STARLINK,
                com.realweather.server.SkyEvent.SUPERNOVA, com.realweather.server.SkyEvent.RECURRENT_NOVA,
                com.realweather.server.SkyEvent.DEBRIS_REENTRY, com.realweather.server.SkyEvent.SATELLITE_FLARE,
                com.realweather.server.SkyEvent.AURORA_STORM, com.realweather.server.SkyEvent.SPORADIC_METEORS,
                com.realweather.server.SkyEvent.STAR_OCCULTATION, com.realweather.server.SkyEvent.TRIPLE_CONJUNCTION,
                com.realweather.server.SkyEvent.WANDERING_COMET, com.realweather.server.SkyEvent.FAINT_COMET,
                com.realweather.server.SkyEvent.DUST_GLOW, com.realweather.server.SkyEvent.STAR_FLARES};
        float[] at = {0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.33F, 0.5F, 0.5F, 0.42F, 0.05F, 0.035F, 0.33F, 0.5F, 0.5F, 0.5F, 0.42F, 0.5F,
                0.5F, 0.5F, 0.5F, 0.5F};
        for (int i = 0; i < night.length; i++) {
            int type = night[i];
            float progress = at[i];
            String name = com.realweather.server.SkyEvent.NAMES[type];
            boolean faint = type == com.realweather.server.SkyEvent.FAINT_COMET;
            step(5, () -> {
                cmd(faint ? "time set 13300" : "time set 15000");
                skyAt(type, progress);
            });
            step(30, () -> lookAtEvent(type));
            if (type == com.realweather.server.SkyEvent.FIREBALLS) {
                // The first fireball comes a few seconds in.
                step(5, () -> skyAt(type, 0.0125F));
                step(8, () -> lookAtEvent(type));
            }
            if (type == com.realweather.server.SkyEvent.LUNAR_ECLIPSE) {
                // Partly in the shadow first.
                step(5, () -> skyAt(type, 0.37F));
                step(10, () -> shot("b00_lunar_eclipse_partial"));
                step(5, () -> skyAt(type, 0.5F));
                step(10, () -> lookAtEvent(type));
            }
            step(4, () -> shot(String.format("b%02d_%s", 0, name)));
            if (type == com.realweather.server.SkyEvent.METEOR_SHOWER || type == com.realweather.server.SkyEvent.SPORADIC_METEORS) {
                step(9, () -> shot(String.format("b%02d_%s", 1, name)));
                step(13, () -> shot(String.format("b%02d_%s", 2, name)));
            }
            step(1, () -> skyState(name));
        }
        // In the daytime, at the sun.
        int[] day = {com.realweather.server.SkyEvent.TRANSIT, com.realweather.server.SkyEvent.SATELLITE_TRANSIT,
                com.realweather.server.SkyEvent.CORONAL_MASS_EJECTION};
        float[] dayAt = {0.5F, 0.0025F, 0.02F};
        for (int i = 0; i < day.length; i++) {
            int type = day[i];
            float progress = dayAt[i];
            String name = com.realweather.server.SkyEvent.NAMES[type];
            step(5, () -> {
                cmd("time set 1500");
                skyAt(type, progress);
            });
            step(25, () -> lookAtEvent(type));
            step(4, () -> shot("d0_" + name));
        }
        // The rainbow with the sun low behind, in a light shower; then on its own.
        step(5, () -> {
            cmd("time set 500");
            skyAt(com.realweather.server.SkyEvent.RAINBOW, 0.3F);
        });
        step(200, () -> lookAtEvent(com.realweather.server.SkyEvent.RAINBOW));
        step(5, () -> shot("e0_rainbow"));
        step(5, () -> Minecraft.getInstance().player.setXRot(-25F));
        step(5, () -> shot("e1_rainbow_up"));
        step(5, () -> cmd("execute as @a at @s run rw thunderstorm"));
        step(200, () -> lookAtEvent(com.realweather.server.SkyEvent.RAINBOW));
        step(5, () -> shot("e2_rainbow_storm"));
        step(5, () -> {
            cmd("rw clear");
            stopSky();
        });
        // Annular and hybrid solar eclipses.
        step(5, () -> cmd("time set 3000"));
        float[] annular = {0.3F, 0.5F};
        for (float pr : annular) {
            step(5, () -> eclipseKindAt(pr, com.realweather.server.Eclipse.ANNULAR));
            step(30, () -> lookAtSun(0F));
            step(4, () -> shot(String.format("f0_annular_%02d", (int) (pr * 100))));
        }
        float[] hybrid = {0.42F, 0.5F};
        for (float pr : hybrid) {
            step(5, () -> eclipseKindAt(pr, com.realweather.server.Eclipse.HYBRID));
            step(30, () -> lookAtSun(0F));
            step(4, () -> shot(String.format("f1_hybrid_%02d", (int) (pr * 100))));
        }
        step(5, () -> eclipseState("hybrid"));
        // Lightning high over a storm: sprites, jets and elves.
        step(5, () -> {
            cmd("time set 15000");
            skyAt(com.realweather.server.SkyEvent.UPPER_LIGHTNING, 0.3F);
            cmd("execute as @a at @s run rw thunderstorm");
        });
        for (int i = 0; i < 4; i++) {
            int n = i;
            step(40, () -> com.realweather.client.sky.ClientSkyEvents.upperFlash(Minecraft.getInstance().level,
                    Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()));
            step(1, () -> lookAtEvent(com.realweather.server.SkyEvent.UPPER_LIGHTNING));
            step(2, () -> shot("c0_upper_lightning_" + n));
        }
        step(5, () -> cmd("rw clear"));
        // The four kinds of lightning at night under a storm, each with the cloud lit in its colour.
        step(5, () -> {
            cmd("time set 15000");
            cmd("execute as @a at @s run rw thunderstorm");
        });
        step(200, () -> turnClient(180F, -8F));
        String[] kinds = com.realweather.LightningKind.NAMES;
        for (int k = 0; k < kinds.length; k++) {
            int kind = k;
            step(30, () -> boltAhead(kind, 70));
            step(3, () -> shot("a0_lightning_" + kinds[kind] + "_1"));
            step(3, () -> shot("a0_lightning_" + kinds[kind] + "_2"));
            step(3, () -> shot("a0_lightning_" + kinds[kind] + "_3"));
            step(20, () -> cloudFlashAhead(kind));
            step(1, () -> shot("a1_cloud_flash_" + kinds[kind]));
        }
        step(5, () -> cmd("rw clear"));
        // A command to check it is all wired up.
        step(5, () -> {
            cmd("rw sky meteor_shower");
            cmd("rw lightning purple 40");
            cmd("rw sky stop");
        });
    }

    /** 1.11.0: the telescope: each kind of target, found and zoomed in on. */
    private static void telescopeSuite() {
        step(5, () -> {
            cmd("rw clear");
            cmd("time set 18000");
            cmd("rw sky natural off");
            cmd("rw sky stop");
            goTo("minecraft:plains", 0, 0);
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
        });
        step(60, SelfTest::platform);
        step(100, () -> {
            MinecraftServer server = server();
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                BlockPos at = p.blockPosition().north();
                server.overworld().setBlock(at, com.realweather.device.ModDevices.TELESCOPE.get().defaultBlockState(), 3);
                telescopeAt = at;
                LOG.info("telescope at {}", at);
            });
        });
        step(20, () -> {
            turnClient(0F, 20F);
            shot("t0_telescope_block");
        });
        String[][] targets = {{"jupiter", "0.6"}, {"saturn", "0.3"}, {"pluto", "0.009"}, {"haumea", "0.009"}, {"betelgeuse", "0.012"},
                {"uy_scuti", "0.014"}, {"eta_carinae", "0.03"}, {"vy_canis_majoris", "0.03"}, {"fomalhaut", "0.016"}, {"spica", "0.005"},
                {"capella", "0.006"}, {"altair", "0.005"}, {"proxima_centauri", "0.006"}, {"alpha_centauri_a", "0.006"}, {"hr8799c", "0.016"},
                {"betapic_b", "0.02"}, {"psr_b1620_26b", "0.02"}, {"gj436b", "0.006"}, {"trappist1e", "0.01"}, {"kepler16b", "0.01"},
                {"wasp12b", "0.005"}, {"cnc55e", "0.005"}, {"kelt9b", "0.005"}, {"hd189733b", "0.005"}, {"toi700d", "0.01"}, {"earth", "70"}};
        for (int i = 0; i < targets.length; i++) {
            String id = targets[i][0];
            double fov = Double.parseDouble(targets[i][1]);
            String label = String.format("t%02d_%s_%s", i + 1, id, targets[i][1].replace('.', '_'));
            step(5, () -> {
                com.realweather.client.space.Telescope.start(telescopeAt, com.realweather.client.space.Catalog.byId(id));
                lookAtTarget(id);
            });
            // First the marker in the ordinary view, then zoomed right in.
            if (i == 0 || i == 6) {
                step(6, () -> shot(label + "_marker"));
            }
            step(4, () -> {
                com.realweather.client.space.Telescope.setFov(fov);
                lookAtTarget(id);
            });
            step(14, () -> lookAtTarget(id));
            step(2, () -> {
                shot(label);
                LOG.info("[telescope {}] {}", id, com.realweather.client.space.TelescopeView.debug);
            });
        }
        // The Sun in the daytime, through the solar filter.
        step(5, () -> {
            cmd("time set 3000");
            com.realweather.client.space.Telescope.start(telescopeAt, com.realweather.client.space.Catalog.byId("sun"));
            lookAtTarget("sun");
        });
        step(5, () -> {
            com.realweather.client.space.Telescope.setFov(12);
            lookAtTarget("sun");
        });
        step(12, () -> lookAtTarget("sun"));
        step(2, () -> shot("t80_sun_12"));
        step(5, () -> {
            com.realweather.client.space.Telescope.setFov(3);
            lookAtTarget("sun");
        });
        step(10, () -> shot("t81_sun_3"));
        step(5, () -> {
            com.realweather.client.space.Telescope.stop();
            cmd("time set 18000");
        });
        step(5, () -> com.realweather.client.space.Telescope.stop());
        // The 3D sky events from close by, through the telescope too.
        step(5, () -> skyAt(com.realweather.server.SkyEvent.ISS, 0.5F));
        step(10, () -> {
            com.realweather.client.space.Telescope.start(telescopeAt, com.realweather.client.space.Catalog.byId("moon"));
            lookAtEvent(com.realweather.server.SkyEvent.ISS);
        });
        step(5, () -> shot("t90_iss"));
        step(5, () -> {
            com.realweather.client.space.Telescope.setFov(6);
            lookAtEvent(com.realweather.server.SkyEvent.ISS);
        });
        step(3, () -> lookAtEvent(com.realweather.server.SkyEvent.ISS));
        step(2, () -> shot("t91_iss_zoomed"));
        step(5, () -> skyAt(com.realweather.server.SkyEvent.CONJUNCTION, 0.5F));
        step(10, () -> {
            com.realweather.client.space.Telescope.setFov(5);
            lookAtEvent(com.realweather.server.SkyEvent.CONJUNCTION);
        });
        step(5, () -> shot("t92_conjunction_zoomed"));
        step(5, () -> {
            com.realweather.client.space.Telescope.stop();
            stopSky();
        });
    }

    /**
     * 1.12: the telescope at its highest powers (planets made of blocks, TON 618), the planet map (round a planet, down
     * to its block ground, landed), and flying up out of the air until the whole Earth is below.
     */
    private static void planetsSuite() {
        step(5, () -> {
            cmd("rw clear");
            cmd("time set 18000");
            cmd("rw sky natural off");
            cmd("rw sky stop");
            goTo("minecraft:plains", 0, 0);
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
        });
        step(60, SelfTest::platform);
        step(100, () -> {
            MinecraftServer server = server();
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                BlockPos at = p.blockPosition().north();
                server.overworld().setBlock(at, com.realweather.device.ModDevices.TELESCOPE.get().defaultBlockState(), 3);
                server.overworld().setBlock(at.east(2), com.realweather.device.ModDevices.PLANET_MAP.get().defaultBlockState(), 3);
                int i = 0;
                for (var b : com.realweather.device.PlanetBlocks.ALL) {
                    server.overworld().setBlock(at.west(2 + i % 5).north(i / 5), b.get().defaultBlockState(), 3);
                    i++;
                }
                telescopeAt = at;
            });
        });
        step(5, () -> cmd("time set 6000"));
        step(20, () -> {
            turnClient(75F, 38F);
            shot("p00_blocks");
        });
        // Up and up: out of the air, through the ozone layer, until the Earth is a ball below.
        double[][] heights = {{450, 20}, {760, 35}, {760, 70}, {1300, 75}, {2600, 85}, {4200, 89}};
        for (String time : new String[]{"6000", "18000"}) {
            step(5, () -> cmd("time set " + time));
            for (double[] hp : heights) {
                String label = String.format("s_%s_%d_%d", time, (int) hp[0], (int) hp[1]);
                step(5, () -> {
                    MinecraftServer server = server();
                    server.execute(() -> {
                        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                        p.getAbilities().mayfly = true;
                        p.getAbilities().flying = true;
                        p.onUpdateAbilities();
                        p.teleportTo(server.overworld(), p.getX(), hp[0], p.getZ(), 0F, (float) hp[1]);
                    });
                });
                step(40, () -> {
                    turnClient(0F, (float) hp[1]);
                    shot(label);
                });
            }
        }
        step(5, () -> {
            cmd("time set 18000");
            MinecraftServer server = server();
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                p.teleportTo(server.overworld(), telescopeAt.getX() + 0.5, telescopeAt.getY(), telescopeAt.getZ() + 1.5, 0F, 0F);
            });
        });
        // The planet map.
        String[] worlds = {"earth", "mars", "jupiter", "toi700d"};
        double[][] views = {{-1, 0, -90}, {300, 0, -90}, {60, 0, -60}, {14, 20, -15}, {1.62, 40, -8}, {1.62, 120, 12}};
        for (String w : worlds) {
            step(5, () -> Minecraft.getInstance().setScreen(new com.realweather.client.space.PlanetViewScreen(
                    com.realweather.client.space.Catalog.byId(w))));
            for (int v = 0; v < views.length; v++) {
                double[] vw = views[v];
                String label = String.format("m_%s_%d", w, v);
                step(3, () -> {
                    if (Minecraft.getInstance().screen instanceof com.realweather.client.space.PlanetViewScreen ps) {
                        ps.testView(vw[0], vw[1], vw[2]);
                    }
                });
                step(8, () -> shot(label));
            }
        }
        step(5, () -> Minecraft.getInstance().setScreen(null));
        String[][] targets = {{"mars", "0.0007"}, {"moon", "0.004"}, {"toi700d", "0.0007"}, {"jupiter", "0.0007"}, {"ton618", "0.004"},
                {"ton618", "0.0007"}};
        for (int i = 0; i < targets.length; i++) {
            String id = targets[i][0];
            double fov = Double.parseDouble(targets[i][1]);
            String label = String.format("p%02d_%s_%s", i + 1, id, targets[i][1].replace('.', '_'));
            step(5, () -> {
                com.realweather.client.space.Telescope.start(telescopeAt, com.realweather.client.space.Catalog.byId(id));
                lookAtTarget(id);
            });
            step(4, () -> {
                com.realweather.client.space.Telescope.setFov(fov);
                lookAtTarget(id);
            });
            step(16, () -> lookAtTarget(id));
            // Two frames a little apart: the target must stay put (no jumping about).
            step(2, () -> shot(label));
            step(7, () -> {
                shot(label + "_b");
                LOG.info("[telescope {}] {}", id, com.realweather.client.space.TelescopeView.debug);
            });
        }
        step(5, () -> com.realweather.client.space.Telescope.stop());
        step(5, () -> com.realweather.client.space.Telescope.stop());
    }

    private static BlockPos telescopeAt = BlockPos.ZERO;

    private static void lookAtTarget(String id) {
        Minecraft mc = Minecraft.getInstance();
        double[] d = com.realweather.client.space.TelescopeView.direction(com.realweather.client.space.Catalog.byId(id), mc.level, 1F);
        if (com.realweather.client.space.Telescope.active()) {
            com.realweather.client.space.Telescope.point(d, mc.level, 1F);
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-d[0], d[2]));
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, d[1]))));
        turnClient(yaw, pitch);
    }

    /** One sky event, already this far through. */
    private static void skyAt(int type, float progress) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            long now = level.getGameTime();
            int dur = com.realweather.server.SkyEvent.DURATION[type];
            com.realweather.server.SkyEventManager.EVENTS.clear();
            com.realweather.server.SkyEventManager.EVENTS.add(new com.realweather.server.SkyEvent(type, now - Math.round(progress * dur), dur,
                    0x5EED1234L + type * 7919L));
            com.realweather.server.SkyEventManager.sync(level);
        });
    }

    private static void stopSky() {
        MinecraftServer server = server();
        server.execute(() -> com.realweather.server.SkyEventManager.stop(server.overworld(), -1));
    }

    private static void lookAtEvent(int type) {
        Minecraft mc = Minecraft.getInstance();
        float[] d = com.realweather.client.sky.SpaceRenderer.focus(type, mc.level, 1F);
        if (d == null) {
            LOG.warn("no {} to look at", com.realweather.server.SkyEvent.NAMES[type]);
            return;
        }
        if (com.realweather.client.space.Telescope.active()) {
            com.realweather.client.space.Telescope.point(new double[]{d[0], d[1], d[2]}, mc.level, 1F);
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-d[0], d[2]));
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1F, Math.min(1F, d[1]))));
        turnClient(yaw, pitch);
    }

    private static void boltAhead(int kind, double dist) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            // North of the player (the camera looks north).
            com.realweather.server.LightningStrikes.strike(server.overworld(), p.getX() + 6, p.getZ() - dist, kind);
        });
    }

    private static void cloudFlashAhead(int kind) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.phys.Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
        float deck = com.realweather.client.sky.StormClouds.deckY(mc.level);
        com.realweather.client.sky.CloudFlashes.add(c.x - 30, deck + 25, c.z - 180, 1.4F, true, kind);
        com.realweather.client.sky.CloudFlashes.add(c.x + 60, deck + 40, c.z - 260, 1.1F, false, kind);
    }

    private static void eclipseKindAt(float progress, int kind) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            long now = level.getGameTime();
            int dur = com.realweather.server.EclipseManager.DURATION;
            com.realweather.server.EclipseManager.current = new com.realweather.server.Eclipse(now - Math.round(progress * dur), dur, 0.01F, 0.6F, kind);
            com.realweather.server.EclipseManager.sync(level);
        });
    }

    private static void eclipseSuite() {
        step(5, () -> {
            cmd("rw clear");
            cmd("time set 3000");
            cmd("gamerule doDaylightCycle false");
            goTo("minecraft:plains", 0, 0);
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
        });
        step(60, SelfTest::platform);
        step(100, () -> lookAtSun(0F));
        step(5, () -> shot("90_sun_round"));
        step(5, () -> eclipseAt(0.30F, true));
        step(30, () -> {
            lookAtSun(0F);
            eclipseState("partial");
        });
        step(5, () -> shot("91_eclipse_partial"));
        step(5, () -> eclipseAt(0.418F, true));
        step(20, () -> eclipseState("diamond"));
        step(3, () -> shot("92_eclipse_diamond_ring"));
        step(5, () -> eclipseAt(0.5F, true));
        step(30, () -> eclipseState("total"));
        step(5, () -> shot("93_eclipse_total"));
        step(5, () -> lookAtSun(35F));
        step(10, () -> shot("94_eclipse_total_landscape"));
        // Through eclipse glasses.
        step(5, () -> {
            eclipseAt(0.34F, true);
            wearGlasses(true);
            lookAtSun(0F);
        });
        step(30, () -> shot("95_glasses_partial"));
        step(5, () -> wearGlasses(false));
        // Behind 3D clouds, deep in the eclipse.
        step(5, () -> {
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH_3D);
            eclipseAt(0.47F, true);
            lookAtSun(20F);
        });
        step(40, () -> shot("96_eclipse_3d_clouds"));
        // Under a thick overcast: the sun's glow in the clouds, dimming, then the flash of the diamond ring.
        step(5, () -> {
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
            cmd("rw rain 100 5");
        });
        step(240, () -> {
            eclipseAt(0.2F, true);
            lookAtSun(0F);
        });
        step(20, () -> shot("96b_eclipse_overcast_partial"));
        step(5, () -> eclipseAt(0.418F, true));
        step(18, () -> shot("96c_eclipse_overcast_diamond"));
        step(5, () -> cmd("rw clear"));
        step(5, () -> {
            MinecraftServer server = server();
            server.execute(() -> com.realweather.server.EclipseManager.stop(server.overworld()));
        });
        // A week of skies: every day another mix of cloud types (3D, then the sprite clouds with their high sheets).
        for (int d = 0; d < 6; d++) {
            final int day = d;
            step(5, () -> {
                cmd("time set " + (day * 24000L + 5000L));
                com.realweather.client.sky.CloudTypes.snap();
                weatherQuality(day % 2 == 0 ? com.realweather.ClientConfig.Quality.ULTRA_3D : com.realweather.ClientConfig.Quality.MEDIUM_3D);
                look(60F + day * 50F, -22F);
            });
            step(60, () -> {
                LOG.info("day {}: cumulus {} stratocumulus {} stratus {} cumulonimbus {} altocumulus {} altostratus {} cirrus {} cirrocumulus {} cirrostratus {}",
                        day, com.realweather.client.sky.CloudTypes.cumulus, com.realweather.client.sky.CloudTypes.stratocumulus,
                        com.realweather.client.sky.CloudTypes.stratus, com.realweather.client.sky.CloudTypes.cumulonimbus,
                        com.realweather.client.sky.CloudTypes.altocumulus, com.realweather.client.sky.CloudTypes.altostratus,
                        com.realweather.client.sky.CloudTypes.cirrus, com.realweather.client.sky.CloudTypes.cirrocumulus,
                        com.realweather.client.sky.CloudTypes.cirrostratus);
                shot("97_types_3d_day" + day);
            });
        }
        for (int d = 2; d < 5; d++) {
            final int day = d;
            step(5, () -> {
                cmd("time set " + (day * 24000L + 5000L));
                com.realweather.client.sky.CloudTypes.snap();
                weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
                look(60F + day * 50F, -22F);
            });
            step(60, () -> shot("98_types_sprites_day" + day));
        }
        // Lightning inside a storm cloud.
        step(5, () -> {
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
            cmd("execute as @a at @s run rw supercell 10");
            cmd("rw thunder 100 10");
        });
        step(300, () -> look(0F, -12F));
        step(5, () -> {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.world.phys.Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
            float deck = com.realweather.client.sky.StormClouds.deckY(mc.level);
            com.realweather.client.sky.CloudFlashes.add(c.x + 10, deck + 30, c.z + 160, 1.3F, true);
            com.realweather.client.sky.CloudFlashes.add(c.x - 70, deck + 50, c.z + 230, 1.0F, false);
        });
        step(1, () -> shot("99_lightning_in_cloud"));
        step(5, () -> weatherQuality(com.realweather.ClientConfig.Quality.HIGH_3D));
        step(40, () -> {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.world.phys.Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
            float deck = com.realweather.client.sky.StormClouds.deckY(mc.level);
            com.realweather.client.sky.CloudFlashes.add(c.x + 20, deck + 40, c.z + 180, 1.4F, true);
        });
        step(1, () -> shot("99b_lightning_in_3d_cloud"));
        // A rain storm seen from outside it: volumetric in 3D, grey sprites otherwise.
        step(5, () -> {
            cmd("rw clear");
            cmd("time set 5000");
        });
        step(40, () -> {
            cmd("rw rain 12 6");
            cmd("execute as @a at @s run rw supercell 10");
        });
        step(240, SelfTest::lookAtStorm);
        step(120, () -> weatherQuality(com.realweather.ClientConfig.Quality.HIGH_3D));
        step(40, () -> shot("99c_storms_3d"));
        step(5, () -> weatherQuality(com.realweather.ClientConfig.Quality.HIGH));
        step(40, () -> shot("99d_storms_sprites"));
        step(5, () -> {
            cmd("rw clear");
            cmd("gamerule doDaylightCycle true");
        });
    }

    /** Faces the nearest storm cell, a little above its base. */
    private static void lookAtStorm() {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.phys.Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
        com.realweather.client.ClientStorms.Cell best = null;
        double bd = Double.MAX_VALUE;
        for (com.realweather.client.ClientStorms.Cell cell : com.realweather.client.ClientStorms.cells()) {
            double d = (cell.x - c.x) * (cell.x - c.x) + (cell.z - c.z) * (cell.z - c.z);
            if (d < bd) {
                bd = d;
                best = cell;
            }
        }
        if (best == null) {
            LOG.warn("no storm cell to look at");
            look(0F, -10F);
            return;
        }
        double dx = best.x - c.x, dz = best.z - c.z;
        double len = Math.max(1.0, Math.sqrt(bd));
        // Stand well away from it (about a thousand blocks), to see the whole storm.
        double away = 1000.0 - len;
        final double tx = c.x - dx / len * away, tz = c.z - dz / len * away;
        final float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        LOG.info("storm cell {} blocks away, radius {}; moving out to see it", (int) len, best.radius);
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.teleportTo(server.overworld(), tx, p.getY() + 30, tz, yaw, -6F);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        });
    }

    /** Puts the eclipse at a given point of its course (0 first contact .. 1 last). */
    private static void eclipseAt(float progress, boolean total) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            long now = level.getGameTime();
            int dur = com.realweather.server.EclipseManager.DURATION;
            com.realweather.server.EclipseManager.current = new com.realweather.server.Eclipse(now - Math.round(progress * dur), dur, total ? 0F : 0.8F, 0.6F);
            com.realweather.server.EclipseManager.sync(level);
        });
    }

    private static void eclipseState(String label) {
        LOG.info("[{}] eclipse coverage {} dark {} diamond {} corona {}", label, com.realweather.client.sky.ClientEclipse.coverage,
                com.realweather.client.sky.ClientEclipse.dark, com.realweather.client.sky.ClientEclipse.diamond, com.realweather.client.sky.ClientEclipse.corona);
    }

    private static void wearGlasses(boolean on) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                    on ? new net.minecraft.world.item.ItemStack(com.realweather.device.ModDevices.ECLIPSE_GLASSES.get()) : net.minecraft.world.item.ItemStack.EMPTY);
        });
    }

    /** Turns to face the sun, `below` degrees under it (0: right at it). */
    private static void lookAtSun(float below) {
        Minecraft mc = Minecraft.getInstance();
        float a = mc.level.getTimeOfDay(1F) * net.minecraft.util.Mth.TWO_PI;
        double dx = -net.minecraft.util.Mth.sin(a), dy = net.minecraft.util.Mth.cos(a), dz = 0.0;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.asin(dy)) + below;
        look(yaw, pitch);
    }

    private static int townX, townY, townZ;

    /** A cobblestone hut with glass windows, a timber house and a stone-brick tower next to each other. */
    private static void buildTown() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            townX = p.getBlockX() + 6;
            townZ = p.getBlockZ() + 10;
            townY = com.realweather.server.TsunamiGround.top(level, townX, townZ);
            net.minecraft.world.level.block.state.BlockState[] walls = {net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(),
                    net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(),
                    net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState()};
            int[] heights = {5, 6, 11};
            for (int b = 0; b < 3; b++) {
                int x0 = townX - 12 + b * 9, z0 = townZ;
                for (int dx = 0; dx < 7; dx++) {
                    for (int dz = 0; dz < 7; dz++) {
                        int ground = com.realweather.server.TsunamiGround.top(level, x0 + dx, z0 + dz);
                        level.setBlock(new BlockPos(x0 + dx, ground - 1, z0 + dz), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
                        for (int y = ground; y < townY + heights[b]; y++) {
                            boolean edge = dx == 0 || dz == 0 || dx == 6 || dz == 6;
                            boolean roof = y == townY + heights[b] - 1;
                            boolean window = edge && (dx == 3 || dz == 3) && (y - townY) % 4 == 2;
                            net.minecraft.world.level.block.state.BlockState st = roof || edge ? (window
                                    ? net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState() : walls[b])
                                    : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                            level.setBlock(new BlockPos(x0 + dx, y, z0 + dz), st, 3);
                        }
                    }
                }
            }
            // The camera on a platform looking down at the town.
            int py = townY + 16;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlock(new BlockPos(townX + dx, py - 1, townZ - 12 + dz), net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
            p.teleportTo(level, townX + 0.5, py, townZ - 11.5, 0F, 38F);
            LOG.info("town built at {} {} {}", townX, townY, townZ);
        });
    }

    private static void townState(String label) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            String[] names = {"cobblestone", "planks", "stone bricks"};
            StringBuilder sb = new StringBuilder();
            for (int b = 0; b < 3; b++) {
                int x0 = townX - 12 + b * 9;
                int solid = 0, glass = 0;
                for (int dx = 0; dx < 7; dx++) {
                    for (int dz = 0; dz < 7; dz++) {
                        for (int y = townY; y < townY + 12; y++) {
                            net.minecraft.world.level.block.state.BlockState st = level.getBlockState(new BlockPos(x0 + dx, y, townZ + dz));
                            if (st.is(net.minecraft.world.level.block.Blocks.GLASS)) {
                                glass++;
                            } else if (!st.isAir()) {
                                solid++;
                            }
                        }
                    }
                }
                sb.append(names[b]).append(": ").append(solid).append(" blocks, ").append(glass).append(" glass; ");
            }
            LOG.info("[{}] town {}", label, sb);
        });
    }

    private static void quakeState(String label) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            for (com.realweather.server.Earthquake q : com.realweather.server.QuakeManager.QUAKES) {
                LOG.info("[{}] quake #{} M{} age {}/{} intensity here {} cracks {}", label, q.id, q.magnitude, q.age, q.duration,
                        q.intensityAt(p.getX(), p.getZ()), q.cracks().size());
            }
        });
        Minecraft mc = Minecraft.getInstance();
        LOG.info("[{}] client quakes={} particles={} fps={}", label, com.realweather.client.quake.ClientQuakes.all().size(),
                mc.particleEngine.countParticles(), mc.getFps());
    }

    private static void snowState(String label) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            int max = 0, sum = 0, n = 0;
            for (int dx = -10; dx <= 10; dx += 2) {
                for (int dz = 0; dz <= 20; dz += 2) {
                    BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                            new BlockPos(p.getBlockX() + dx, 0, p.getBlockZ() + dz));
                    BlockPos pos = com.realweather.server.SnowPile.layers(level.getBlockState(top)) > 0 ? top : top.below();
                    int d = com.realweather.server.SnowPile.layers(level.getBlockState(pos)) > 0 ? com.realweather.server.SnowPile.depth(level, pos) : 0;
                    max = Math.max(max, d);
                    sum += d;
                    n++;
                }
            }
            LOG.info("[{}] hail/snow depth: max {} layers, mean {}", label, max, sum / (float) n);
        });
    }

    private static void tsunamiAtPlayer(int strength) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            com.realweather.server.Tsunami w = com.realweather.server.TsunamiManager.spawn(level, p.position(), strength, Float.NaN, level.random);
            LOG.info("tsunami {} from {} {} heading {} {} base {}", strength, (int) w.ox, (int) w.oz, w.dirX, w.dirZ, w.baseY);
        });
    }

    private static void stopTsunamis() {
        MinecraftServer server = server();
        server.execute(() -> com.realweather.server.TsunamiManager.clearNow(server.overworld()));
    }

    private static double landX, landY, landZ;

    /** Finds dry land a little above the sea near the player and puts the viewing platform over it. */
    private static void moveToLand() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            int sea = level.getSeaLevel();
            int px = p.getBlockX(), pz = p.getBlockZ();
            for (int r = 0; r <= 120; r += 3) {
                for (int a = 0; a < Math.max(1, r * 2); a++) {
                    double ang = a * Math.PI * 2 / Math.max(1, r * 2);
                    int x = px + (int) Math.round(Math.cos(ang) * r), z = pz + (int) Math.round(Math.sin(ang) * r);
                    int top = com.realweather.server.TsunamiGround.top(level, x, z);
                    boolean dry = level.getFluidState(new BlockPos(x, top, z)).isEmpty() && level.getFluidState(new BlockPos(x, top - 1, z)).isEmpty();
                    if (dry && top >= sea + 2 && top <= sea + 9) {
                        int y = top + 16;
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                level.setBlock(new BlockPos(x + dx, y - 1, z + dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                            }
                        }
                        p.teleportTo(level, x + 0.5, y, z + 0.5, p.getYRot(), p.getXRot());
                        LOG.info("land for the tsunami at {} {} (ground {}), {} blocks from the shore spot", x, z, top, r);
                        return;
                    }
                }
            }
            LOG.warn("no dry land near the coast");
        });
    }

    /** Creative flight, so the camera stays where it is put. */
    private static void hover() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        });
    }

    private static void backToPlatform() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            if (landY != 0) {
                p.teleportTo(server.overworld(), landX, landY, landZ, p.getYRot(), 20F);
            }
        });
    }

    /** A sedan on the ground beside the player (under the platform), in the wave's path. */
    private static void carInWavePath() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            if (com.realweather.server.TsunamiManager.WAVES.isEmpty()) {
                return;
            }
            com.realweather.server.Tsunami w = com.realweather.server.TsunamiManager.WAVES.get(0);
            double x = p.getX() + 3, z = p.getZ() + 3;
            int y = com.realweather.server.TsunamiGround.top(level, net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(z));
            com.realweather.vehicle.StormVehicle v = com.realweather.vehicle.VehicleRegistry.type(com.realweather.vehicle.VehicleType.SEDAN).create(level);
            v.moveTo(x, y, z, 30F, 0F);
            v.setColor(com.realweather.vehicle.VehicleType.SEDAN.colors[0]);
            level.addFreshEntity(v);
            VEHICLE_IDS[0] = v.getUUID();
            LOG.info("car in the wave's path at {} {} {}", (int) x, y, (int) z);
        });
    }

    private static void carState(String label) {
        MinecraftServer server = server();
        server.execute(() -> {
            if (serverVehicle(server, 0) instanceof com.realweather.vehicle.StormVehicle v) {
                LOG.info("[{}] car at {} glass broken={} cabin water={}", label, v.blockPosition(), v.flag(com.realweather.vehicle.StormVehicle.FLAG_GLASS_BROKEN),
                        v.cabinWater());
            } else {
                LOG.warn("[{}] car gone", label);
            }
        });
    }

    private static void lookAtCar(float pitch) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            if (!(serverVehicle(server, 0) instanceof com.realweather.vehicle.StormVehicle v)) {
                return;
            }
            p.teleportTo(server.overworld(), v.getX() + 4, v.getY() + 4, v.getZ() + 4, 135F, pitch);
        });
    }

    /** Faces the incoming wave. */
    private static void lookAtWave(float pitch) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            if (com.realweather.server.TsunamiManager.WAVES.isEmpty()) {
                LOG.warn("no tsunami to look at");
                return;
            }
            com.realweather.server.Tsunami w = com.realweather.server.TsunamiManager.WAVES.get(0);
            // Towards where it comes from (against its direction of travel).
            float yaw = (float) Math.toDegrees(Math.atan2(w.dirX, -w.dirZ));
            p.teleportTo(server.overworld(), p.getX(), p.getY(), p.getZ(), yaw, pitch);
        });
    }

    private static void tsunamiState(String label) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            for (com.realweather.server.Tsunami w : com.realweather.server.TsunamiManager.WAVES) {
                LOG.info("[{}] tsunami #{} strength={} front={} player along={} height={} ground={} flood={} stage={} drained={} air={} water={}", label,
                        w.id, w.strength, (int) w.front, (int) w.along(p.getX(), p.getZ()), w.height(), w.ground(p.level(), p.getX(), p.getZ()),
                        w.floodSurface(p.level(), p.getX(), p.getZ()), w.stage, w.drained, p.getAirSupply(),
                        com.realweather.server.WeatherData.get(p.serverLevel()).tsunamiWater.getOrDefault(w.id, new it.unimi.dsi.fastutil.longs.LongArrayList()).size());
            }
        });
        Minecraft mc = Minecraft.getInstance();
        LOG.info("[{}] client tsunamis={} particles={} fps={}", label, com.realweather.client.tsunami.ClientTsunamis.all().size(),
                mc.particleEngine.countParticles(), mc.getFps());
    }

    private static void removeTornadoes() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            WeatherData d = WeatherData.get(level);
            d.tornadoes.clear();
            com.realweather.server.TornadoManager.sync(level, d);
        });
    }

    /** A fully developed tornado of the given type east of the player. */
    private static void typeTornado(String shape, int rating, double dist) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            WeatherData d = WeatherData.get(level);
            com.realweather.server.Tornado t = com.realweather.server.TornadoManager.spawnAt(level, d,
                    new net.minecraft.world.phys.Vec3(p.getX() + dist, p.getY(), p.getZ() + 25), rating,
                    com.realweather.server.TornadoShape.byName(shape), level.random);
            t.heading = 0F;
            t.speed = 0.02F;
            t.stage = com.realweather.server.Tornado.MATURE;
            t.contact = 1F;
            t.strength = 1F;
            t.life = 100000;
            com.realweather.server.TornadoManager.sync(level, d);
            LOG.info("spawned {} F{} tornado at {} {}", shape, rating, (int) t.x, (int) t.z);
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
    private static final java.util.UUID[] VEHICLE_IDS = new java.util.UUID[6];

    private static net.minecraft.world.entity.Entity serverVehicle(MinecraftServer server, int index) {
        java.util.UUID id = VEHICLE_IDS[index];
        return id == null ? null : server.overworld().getEntity(id);
    }

    private static com.realweather.vehicle.StormVehicle clientVehicle(int index) {
        java.util.UUID id = VEHICLE_IDS[index];
        Minecraft mc = Minecraft.getInstance();
        if (id == null || mc.level == null) {
            return null;
        }
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof com.realweather.vehicle.StormVehicle v && id.equals(e.getUUID())) {
                return v;
            }
        }
        return null;
    }

    private static void vehicleSuite() {
        step(5, () -> {
            cmd("rw clear");
            cmd("time set 6000");
            goTo("minecraft:plains", 0, 15);
            weatherQuality(com.realweather.ClientConfig.Quality.HIGH);
        });
        step(40, SelfTest::testPad);
        step(20, () -> spawnVehicles(false));
        step(160, () -> lookAt(1, 14));
        step(40, () -> {
            shot("40_vehicles");
            vehicleState("parked");
            standAt(0, 27.5, 180F, 12F);
        });
        step(30, () -> {
            shot("40b_new_cars_front");
            standAt(11, 12, 45F, 14F);
        });
        step(30, () -> {
            shot("40c_new_cars_rear");
            standAt(0, 3.5, 0F, 6F);
        });
        step(30, () -> {
            shot("40d_through_glass");
            standAt(0, 0, 0F, 14F);
        });
        step(5, () -> clickDoor(1, 0));
        step(30, () -> {
            shot("48_door_open");
            vehicleState("door open");
        });
        step(5, () -> clickDoor(1, 0));
        step(30, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            turnClient(0F, 24F);
            vehicleState("entered by door");
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
        step(50, () -> {
            shot("42_pickup_driving_hud");
            vehicleState("driving");
            drive(0.6F, 1F);
        });
        step(25, () -> {
            shot("42b_pickup_turning");
            vehicleState("turning");
            drive(-1F, 0F);
        });
        step(40, () -> {
            drive(0F, 0F);
            vehicleState("braked");
        });
        step(5, SelfTest::dismount);
        step(20, () -> ride(2));
        step(20, () -> {
            vehicleAction(2, 4);
            tornadoNearVehicle(2, 3, 70);
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
            turnClient(0F, 4F);
        });
        step(20, () -> shot("46_intercept_interior"));
        step(5, () -> turnClient(-70F, 4F));
        step(20, () -> shot("46b_intercept_side_window"));
        // The muscle coupe: start-up, then lift off the throttle at high revs for the backfire.
        step(5, () -> {
            cmd("rw clear");
            dismount();
            Minecraft.getInstance().options.hideGui = true;
        });
        step(10, () -> placeVehicle(4, 11, 6, 0F));
        step(20, () -> ride(4));
        step(30, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            turnClient(0F, 18F);
        });
        step(20, () -> shot("51_coupe_interior"));
        step(5, () -> turnClient(-60F, 22F));
        step(20, () -> shot("51b_coupe_door_card"));
        step(5, () -> turnClient(160F, 18F));
        step(20, () -> shot("51c_coupe_seats"));
        step(10, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            turnClient(0F, 12F);
            vehicleState("coupe started");
            drive(1F, 0F);
        });
        step(26, () -> {
            vehicleState("coupe flat out");
            drive(0F, 0F);
        });
        step(2, () -> {
            com.realweather.vehicle.StormVehicle v = clientVehicle(4);
            LOG.info("backfire after lift: pops left={} flame={} rpm={}", v == null ? -1 : v.backfirePops, v == null ? -1 : v.flame,
                    v == null ? -1 : Math.round(v.rpm));
            shot("50_backfire");
        });
        step(10, () -> {
            com.realweather.client.vehicle.Backfire.debugHold = 30;
            vehicleState("coupe coasting");
        });
        step(5, () -> shot("50b_backfire_flame"));
        step(30, () -> {
            drive(-1F, 0F);
        });
        step(30, () -> drive(0F, 0F));
        // A blizzard (they only happen where it is cold enough): snow on the glass, then the wipers.
        step(5, () -> {
            cmd("rw clear");
            dismount();
        });
        step(10, () -> goTo("minecraft:snowy_plains", 0, 15));
        step(40, SelfTest::testPad);
        step(20, () -> spawnVehicles(true));
        step(100, () -> ride(0));
        step(20, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            turnClient(0F, 8F);
            cmd("rw blizzard 100 10");
        });
        step(500, () -> {
            shot("43_blizzard_windshield");
            vehicleState("blizzard");
            vehicleAction(0, 1);
            vehicleAction(0, 1);
        });
        step(60, () -> {
            shot("44_wipers");
            vehicleState("wipers");
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            turnClient(150F, 25F);
        });
        step(20, () -> shot("47_snowy_sedan"));
        step(5, () -> {
            cmd("rw blizzard stop");
            dismount();
        });
        step(10, () -> stepUnderFront(0));
        step(40, () -> ride(0));
        step(30, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            turnClient(0F, 10F);
            vehicleState("on a step");
        });
        step(20, () -> shot("49_slope_seat"));
        step(5, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            turnClient(60F, 15F);
        });
        step(20, () -> shot("49b_slope_outside"));
        step(5, () -> {
            cmd("rw blizzard stop");
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
            // Keep animals out of the pictures.
            for (net.minecraft.world.entity.animal.Animal a : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,
                    new net.minecraft.world.phys.AABB(c).inflate(64))) {
                a.discard();
            }
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

    private static void spawnVehicles(boolean sedanOnly) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            padX = p.getX();
            padZ = p.getZ();
            com.realweather.vehicle.VehicleType[] kinds = com.realweather.vehicle.VehicleType.values();
            for (int i = 0; i < (sedanOnly ? 1 : kinds.length); i++) {
                // Two rows: sedan, pickup, intercept facing the camera; SUV, coupe, sport sedan behind them facing away.
                double x = p.getX() - 7 + (i % 3) * 7;
                double z = p.getZ() + (i < 3 ? 9 : 17);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.minecraft.util.Mth.floor(x),
                        net.minecraft.util.Mth.floor(z));
                com.realweather.vehicle.StormVehicle v = com.realweather.vehicle.VehicleRegistry.type(kinds[i]).create(level);
                v.moveTo(x, y, z, i < 3 ? 200F + i * 15F : -15F + (i - 3) * 15F, 0F);
                v.setColor(kinds[i].colors[0]);
                level.addFreshEntity(v);
                VEHICLE_IDS[i] = v.getUUID();
                // A block under one wheel of the sedan, to see it lean.
                if (i == 0 && !sedanOnly) {
                    level.setBlockAndUpdate(new BlockPos(net.minecraft.util.Mth.floor(x + 0.8), y, net.minecraft.util.Mth.floor(z - 1.2)),
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                }
            }
            LOG.info("vehicles spawned {}", java.util.Arrays.toString(VEHICLE_IDS));
        });
    }

    /** Moves the test player to a spot relative to the pad origin (where the vehicles were spawned from). */
    private static double padX, padZ;

    private static void standAt(double dx, double dz, float yaw, float pitch) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.teleportTo(server.overworld(), padX + dx, p.getY(), padZ + dz, yaw, pitch);
        });
    }

    private static void placeVehicle(int index, double dx, double dz, float yaw) {
        MinecraftServer server = server();
        server.execute(() -> {
            net.minecraft.world.entity.Entity e = serverVehicle(server, index);
            if (e != null) {
                e.moveTo(padX + dx, e.getY(), padZ + dz, yaw, 0F);
                LOG.info("moved {} to {}", e.getName().getString(), e.position());
            }
        });
    }

    private static void lookAt(int index, float pitch) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            net.minecraft.world.entity.Entity e = serverVehicle(server, index);
            if (e == null) {
                LOG.warn("no vehicle {}", index);
                return;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-(e.getX() - p.getX()), e.getZ() - p.getZ()));
            p.teleportTo(server.overworld(), p.getX(), p.getY(), p.getZ(), yaw, pitch);
        });
    }

    private static void ride(int index) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            net.minecraft.world.entity.Entity e = serverVehicle(server, index);
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

    private static void vehicleAction(int index, int action) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            if (serverVehicle(server, index) instanceof com.realweather.vehicle.StormVehicle v) {
                v.applyInput(p, 0F, 0F, false, action);
            }
        });
    }

    private static void tornadoNearVehicle(int index, int rating, double dist) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            net.minecraft.world.entity.Entity e = serverVehicle(server, index);
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
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            for (int i = 0; i < VEHICLE_IDS.length; i++) {
                net.minecraft.world.entity.Entity e = serverVehicle(server, i);
                LOG.info("[{}] server vehicle {}: {}", label, i, e == null ? "missing" : e.getName().getString() + " at " + e.position()
                        + " health=" + ((net.minecraft.world.entity.LivingEntity) e).getHealth() + " passengers=" + e.getPassengers().size()
                        + " removed=" + e.getRemovalReason());
            }
            LOG.info("[{}] server player at {} vehicle={}", label, sp.blockPosition(), sp.getVehicle() == null ? "none" : sp.getVehicle().getId());
        });
        for (int i = 0; i < VEHICLE_IDS.length; i++) {
            com.realweather.vehicle.StormVehicle v = clientVehicle(i);
            if (v != null) {
                LOG.info("[{}] {} speed={} km/h steer={} gear={} pitch={} roll={} lift={} spikes={} wipers={} glass={} bodySnow={} rpm={} wind={} riding={}",
                        label, v.kind.id, Math.round(v.displaySpeed() * 72), v.displaySteer(), v.gear(), v.pitch, v.roll, v.lift, v.spikeDeploy, v.wipers(),
                        com.realweather.client.vehicle.VehicleClient.glassCover(v), v.bodySnow, Math.round(v.rpm), v.measuredWind,
                        mc.player != null && mc.player.getVehicle() == v);
            }
        }
    }


    /** Clicks a vehicle door like a player would (the hit point picks the door). */
    private static void clickDoor(int index, int door) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            if (!(serverVehicle(server, index) instanceof com.realweather.vehicle.StormVehicle v)) {
                return;
            }
            float[] hinge = v.kind.doorRows[door / 2];
            float mx = (door % 2 == 0 ? -1 : 1) * hinge[0];
            float mz = hinge[2] + 0.5F;
            net.minecraft.world.phys.Vec3 hit = v.toWorld(mx, 1.0, mz).subtract(v.position());
            v.interactAt(p, hit, net.minecraft.world.InteractionHand.MAIN_HAND);
            net.minecraft.world.InteractionResult r = p.interactOn(v, net.minecraft.world.InteractionHand.MAIN_HAND);
            LOG.info("clicked door {} of {}: {} open={} riding={}", door, v.kind.id, r, v.doorOpen(door), p.getVehicle() == v);
        });
    }

    /** Lifts the front of a vehicle onto a block step (the wheels sit on different heights). */
    private static void stepUnderFront(int index) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            if (!(serverVehicle(server, index) instanceof com.realweather.vehicle.StormVehicle v)) {
                return;
            }
            for (int w = 0; w < 2; w++) {
                float[] wp = v.kind.wheels[w];
                net.minecraft.world.phys.Vec3 at = v.toWorld(wp[0], 0, wp[2]);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        level.setBlockAndUpdate(BlockPos.containing(at.x + dx, v.getY(), at.z + dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    }
                }
            }
            LOG.info("step under the front of {}", v.kind.id);
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
            LOG.info("player frozen ticks={} / {} at {} velocity={} health={}", p.getTicksFrozen(), p.getTicksRequiredToFreeze(), p.blockPosition(),
                    p.getDeltaMovement(), p.getHealth());
        });
    }
}
