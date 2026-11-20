package com.trafficcraft.client.dev;

import com.trafficcraft.registry.TCRegistry;
import com.trafficcraft.TrafficCraft;
import com.trafficcraft.vehicle.VehicleEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Development-only showroom (-Pautotest=cars, optional -Dtrafficcraft.cars=a,b): screenshots of every car in the game. */
@EventBusSubscriber(modid = TrafficCraft.MODID, value = Dist.CLIENT)
public final class CarShow {
    public static final boolean ON = Boolean.getBoolean("trafficcraft.autotest") && "cars".equals(System.getProperty("trafficcraft.autotest.mode"));
    static int tt = -1;
    static double gy;
    static final List<Integer> ids = new ArrayList<>();
    static final List<Vec3> spots = new ArrayList<>();
    static List<String> models = new ArrayList<>();
    static int step = 0;

    static List<String> models() {
        String p = System.getProperty("trafficcraft.cars", "");
        List<String> out = new ArrayList<>();
        if (!p.isBlank()) for (String s : p.split(",")) out.add(s.trim());
        else for (String id : com.trafficcraft.vehicle.VehicleSpecs.ids()) if (com.trafficcraft.vehicle.VehicleSpecs.get(id).texture != null) out.add(id);
        return out;
    }

    static void view(Minecraft mc, Vec3 eye, Vec3 at) {
        var server = mc.getSingleplayerServer();
        Vec3 d = at.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.teleportTo(sp.serverLevel(), eye.x, eye.y - sp.getEyeHeight(), eye.z, yaw, pitch);
        });
    }

    static VehicleEntity car(Minecraft mc, int i) {
        return i < ids.size() && mc.level.getEntity(ids.get(i)) instanceof VehicleEntity v ? v : null;
    }

    static Vec3 rel(VehicleEntity v, double x, double y, double z) {
        return v.position().add(VehicleEntity.toWorldOffset(x, y, z, v.getYRot()));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        tt++;
        MinecraftServer server = mc.getSingleplayerServer();
        if (tt == 40) {
            models = models();
            server.execute(() -> setup(server));
            mc.options.hideGui = true;
        }
        if (tt < 120 || models.isEmpty()) return;
        // per car: front 3/4, rear 3/4, front close-up of the lamps (every 40 ticks)
        int per = 3;
        int n = models.size();
        int k = (tt - 120) / 40, ph = (tt - 120) % 40;
        if (k < n * per) {
            VehicleEntity v = car(mc, k / per);
            if (v == null) return;
            String id = v.getModel();
            int kind = k % per;
            float L = v.spec().length, W = v.spec().width;
            if (ph == 0) {
                if (kind == 0) view(mc, rel(v, W * 1.7, 1.5, L * 0.95), rel(v, 0, 0.55, 0.3));
                if (kind == 1) view(mc, rel(v, -W * 1.6, 1.6, -L * 0.95), rel(v, 0, 0.6, -0.3));
                if (kind == 2) view(mc, rel(v, W * 0.7, 1.05, L / 2 + 1.5), rel(v, 0, 0.55, L / 2 - 0.2));
            }
            if (ph == 30) shot(mc, "c_" + id + "_" + new String[]{"front34", "rear34", "lamps"}[kind] + ".png");
            return;
        }
        int t = tt - 120 - n * per * 40;
        if (t == 0) server.execute(() -> server.overworld().setDayTime(18000));
        if (t < 2) return;
        VehicleEntity v0 = car(mc, 0);
        if (v0 == null) return;
        float L0 = v0.spec().length, W0 = v0.spec().width;
        // night lighting sequence: DRL only, low beam (close-up, front, road), high beam on the road, brake, tail, indicators, reverse
        if (t < 420) {
            if (t == 5) flags(server, VehicleEntity.F_ENGINE, true);
            if (t == 6) flags(server, VehicleEntity.F_LOW | VehicleEntity.F_HIGH | VehicleEntity.F_BRAKE, false);
            if (t == 10) view(mc, rel(v0, W0 * 0.75, 1.05, L0 / 2 + 1.4), rel(v0, W0 * 0.28, 0.62, L0 / 2 - 0.25));
            if (t == 40) shot(mc, "c_hl_drl_close.png");
            if (t == 42) flags(server, VehicleEntity.F_LOW, true);
            if (t == 70) shot(mc, "c_hl_low_close.png");
            if (t == 72) view(mc, rel(v0, 0, 1.3, L0 / 2 + 5.0), rel(v0, 0, 0.55, L0 / 2));
            if (t == 100) shot(mc, "c_hl_low_front.png");
            if (t == 102) view(mc, rel(v0, 0.9, 3.4, -L0 / 2 - 2.5), rel(v0, 0, 0, L0 / 2 + 9));
            if (t == 130) shot(mc, "c_hl_low_road.png");
            if (t == 132) flags(server, VehicleEntity.F_HIGH, true);
            if (t == 160) shot(mc, "c_hl_high_road.png");
            if (t == 162) {
                flags(server, VehicleEntity.F_HIGH, false);
                flags(server, VehicleEntity.F_BRAKE, true);
                view(mc, rel(v0, -2.2, 1.4, -L0 / 2 - 4.0), rel(v0, 0, 0.7, -L0 / 2));
            }
            if (t == 190) shot(mc, "c_night_brake.png");
            if (t == 192) flags(server, VehicleEntity.F_BRAKE, false);
            if (t == 215) shot(mc, "c_night_tail.png");
            if (t == 217) flags(server, VehicleEntity.F_IND_L, true);
            for (int q = 0; q < 12; q++) if (t == 240 + q * 3) shot(mc, "c_night_ind_" + q + ".png");
            if (t == 278) {
                flags(server, VehicleEntity.F_IND_L, false);
                flags(server, VehicleEntity.F_REVERSE, true);
            }
            if (t == 296) shot(mc, "c_night_reverse.png");
            if (t == 298) flags(server, VehicleEntity.F_REVERSE, false);
            // backfires at the tail pipes: big bang, overrun crackle, single pop
            if (t == 300) view(mc, rel(v0, -1.6, 0.9, -L0 / 2 - 2.6), rel(v0, -0.2, 0.35, -L0 / 2 - 0.3));
            // slow motion for the backfire shots (the software renderer of the test only draws a few frames per second)
            if (t == 305) server.execute(() -> server.tickRateManager().setTickRate(2f));
            if (t == 405) server.execute(() -> server.tickRateManager().setTickRate(20f));
            for (int[] b : new int[][]{{320, 2}, {350, 1}, {390, 0}}) if (t == b[0]) server.execute(() -> {
                for (var en : server.overworld().getAllEntities()) if (en instanceof VehicleEntity sv) sv.level().broadcastEntityEvent(sv, (byte) (60 + b[1]));
            });
            for (int q : new int[]{321, 322, 324, 327}) if (t == q) shot(mc, "c_bf_bang_" + (q - 320) + ".png");
            for (int q : new int[]{352, 356, 360, 366}) if (t == q) shot(mc, "c_bf_crackle_" + (q - 350) + ".png");
            for (int q : new int[]{391, 392, 394}) if (t == q) shot(mc, "c_bf_pop_" + (q - 390) + ".png");
            return;
        }
        t -= 320;
        if (t == 100) server.execute(() -> server.overworld().setDayTime(3000));
        // driver's seat (steering wheel, gear lever, gauges)
        if (t == 105) server.execute(() -> ride(server, ids.get(0)));
        if (t == 115) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (t == 108) server.execute(() -> {
            if (server.overworld().getEntity(ids.get(0)) instanceof VehicleEntity sv) sv.setFlag(VehicleEntity.F_ENGINE, false);
        });
        if (t == 130) server.execute(() -> {
            if (server.overworld().getEntity(ids.get(0)) instanceof VehicleEntity sv) sv.setFlag(VehicleEntity.F_ENGINE, true);
        });
        if (t == 112 && mc.player.getVehicle() instanceof VehicleEntity rv) {
            mc.player.setYRot(rv.getYRot() + 24);
            mc.player.setXRot(34);
        }
        if (t == 120) shot(mc, "c_screen_off.png");
        if (t == 142) shot(mc, "c_screen_boot.png");
        if (t == 185) shot(mc, "c_screen_menu.png");
        if (t == 186 && mc.player.getVehicle() instanceof VehicleEntity rv) {
            mc.player.setYRot(rv.getYRot());
            mc.player.setXRot(18);
        }
        if (t == 195) shot(mc, "c_interior.png");
        // parking sensor: drive the first car that has a screen, wall just ahead
        if (t == 200) server.execute(() -> sensorScene(server));
        if (t == 215) {
            mc.options.hideGui = false;
            if (mc.player.getVehicle() instanceof VehicleEntity rv) {
                mc.player.setYRot(rv.getYRot() + 20);
                mc.player.setXRot(26);
            }
        }
        if (t == 240 && mc.player.getVehicle() instanceof VehicleEntity rv && rv.spec().screen != null) {
            float[] sc = rv.spec().screen;
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 ctr = rv.position().add(VehicleEntity.toWorldOffset(sc[0], sc[1], sc[2], rv.getYRot()));
            boolean took = com.trafficcraft.client.gui.CarScreen.click(rv, eye, ctr.subtract(eye).normalize());
            TrafficCraft.LOG.info("[carshow] screen tap took=" + took);
        }
        if (t == 250) shot(mc, "c_park_sensor.png");
        if (t == 255) {
            mc.options.hideGui = true;
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.stopRiding();
                sp.setGameMode(GameType.SPECTATOR);
            });
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        // line-up
        if (t == 260) {
            Vec3 mid = spots.get(0).add(spots.get(n - 1)).scale(0.5);
            view(mc, mid.add(-4, 9, -14), mid);
        }
        if (t == 300) shot(mc, "c_lineup.png");
        // tuning tool: open the screen on the first car and fit some parts
        if (t == 302) {
            server.execute(() -> server.getPlayerList().getPlayers().get(0).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(TCRegistry.TUNING_TOOL.get())));
            view(mc, rel(v0, 3.8, 1.5, 3.2), rel(v0, 0.6, 0.5, 0.2));
            mc.options.hideGui = false;
        }
        if (t == 306) mc.setScreen(new com.trafficcraft.client.gui.TuningScreen(v0));
        if (t == 308 && mc.screen instanceof com.trafficcraft.client.gui.TuningScreen ts) ts.testFit();
        if (t == 318) shot(mc, "c_tuning.png");
        if (t == 319) TrafficCraft.LOG.info("[carshow] tuning config=" + v0.getConfig() + " color=" + Integer.toHexString(v0.getColor()));
        if (t == 320) mc.setScreen(null);
        // maps: a small road grid with locations, the Map app on the car screen, a route, the full map screen and a search
        if (t == 322) server.execute(() -> {
            roadGrid(server.overworld());
            ride(server, ids.get(0));
        });
        if (t == 330) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            com.trafficcraft.client.gui.CarScreen.openApp(v0, 2);
        }
        if (t >= 334 && t <= 415 && mc.player.getVehicle() instanceof VehicleEntity rv && rv.spec().screen != null) {
            // look straight at the infotainment screen, zoomed in
            float[] sc = rv.spec().screen;
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 d = rv.position().add(VehicleEntity.toWorldOffset(sc[0], sc[1], sc[2], rv.getYRot())).subtract(eye);
            mc.player.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            mc.player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
            mc.options.fov().set(30);
            mc.options.hideGui = true;
        }
        if (t == 396) shot(mc, "c_map_car.png");
        if (t == 398) {
            var pl = com.trafficcraft.road.ClientRoads.net.places.values().stream().filter(p -> p.type.equals("hospital")).findFirst().orElse(null);
            TrafficCraft.LOG.info("[carshow] places on client=" + com.trafficcraft.road.ClientRoads.net.places.size() + " edges=" + com.trafficcraft.road.ClientRoads.net.edges.size());
            if (pl != null) {
                com.trafficcraft.client.nav.Nav.setDestination(pl.pos(), pl.name);
                com.trafficcraft.client.nav.Nav.compute(com.trafficcraft.road.ClientRoads.net, v0.position(), v0.forward(), pl.pos());
                TrafficCraft.LOG.info("[carshow] route points=" + com.trafficcraft.client.nav.Nav.route.size() + " length=" + Math.round(com.trafficcraft.client.nav.Nav.routeLen));
            }
        }
        if (t == 412) shot(mc, "c_map_route.png");
        if (t == 416) {
            mc.options.fov().set(70);
            mc.options.hideGui = false;
            mc.setScreen(new com.trafficcraft.client.gui.MapScreen(v0, false));
        }
        if (t == 432) shot(mc, "c_map_screen.png");
        if (t == 434 && mc.screen instanceof com.trafficcraft.client.gui.MapScreen ms) ms.testSearch("bank");
        if (t == 446) shot(mc, "c_map_search.png");
        if (t == 448) mc.setScreen(null);
        if (t == 452) {
            TrafficCraft.LOG.info("[carshow] done");
            mc.stop();
        }
    }

    /** 4 x 4 grid of two-lane roads 30 blocks apart around the cars, with a few locations on it. */
    static void roadGrid(ServerLevel l) {
        var net = com.trafficcraft.road.RoadData.get(l);
        if (!net.places.isEmpty()) return;
        int[][] id = new int[4][4];
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
            var n = net.addNode(-35 + i * 30, gy, -40 + j * 30, com.trafficcraft.road.NodeType.ROAD);
            id[i][j] = n.id;
        }
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
            if (i < 3) lanes(net.connect(id[i][j], id[i + 1][j], false), j == 1 ? 4 : 2);
            if (j < 3) lanes(net.connect(id[i][j], id[i][j + 1], false), 2);
        }
        net.addPlace(-35 + 85, gy, -40 + 62, "hospital", "City Hospital");
        net.addPlace(-35 + 28, gy, -40 + 4, "bank", "National Bank");
        net.addPlace(-35 + 58, gy, -40 + 33, "supermarket", "Al-Yaqeen Market");
        net.addPlace(-35 + 4, gy, -40 + 70, "restaurant", "Barakat Oven");
        net.addPlace(-35 + 72, gy, -40 + 4, "gas_station", "Fuel 24");
        net.addPlace(-35 + 33, gy, -40 + 88, "mosque", "Zahra Mosque");
        net.changed();
    }

    static void lanes(com.trafficcraft.road.RoadEdge e, int n) {
        if (e == null) return;
        e.lanes.clear();
        e.lanes.addAll(com.trafficcraft.road.RoadEdge.defaultLanes(n, false));
        e.width = n == 4 ? 12 : 7;
    }

    static void flags(MinecraftServer server, int f, boolean on) {
        server.execute(() -> {
            for (var en : server.overworld().getAllEntities()) if (en instanceof VehicleEntity v) v.setFlag(f, on);
        });
    }

    static void setup(MinecraftServer server) {
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        ServerLevel lvl = sp.serverLevel();
        gy = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        lvl.setDayTime(3000);
        lvl.setWeatherParameters(6000, 0, false, false);
        sp.setGameMode(GameType.SPECTATOR);
        sp.teleportTo(lvl, 0, gy + 4, -10, 0, 20);
        // clean slate: remove the cars left in the test world by earlier showroom runs
        List<VehicleEntity> old = new ArrayList<>();
        for (var en : lvl.getAllEntities()) if (en instanceof VehicleEntity ov) old.add(ov);
        for (VehicleEntity ov : old) ov.discard();
        TrafficCraft.LOG.info("[carshow] removed " + old.size() + " old cars; spawning " + models.size() + " cars at y=" + gy);
        for (int i = 0; i < models.size(); i++) {
            VehicleEntity v = new VehicleEntity(TCRegistry.VEHICLE.get(), lvl);
            v.setModel(models.get(i));
            v.setColor(v.spec().colors[i % v.spec().colors.length]);
            v.setPlate("TC " + (1000 + i));
            v.moveTo(i * 6.0, gy, 0, 20, 0);
            lvl.addFreshEntity(v);
            ids.add(v.getId());
            spots.add(v.position());
        }
    }

    static void ride(MinecraftServer server, int id) {
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        if (!(server.overworld().getEntity(id) instanceof VehicleEntity v)) return;
        sp.setGameMode(GameType.CREATIVE);
        sp.getAbilities().flying = false;
        sp.onUpdateAbilities();
        var seat = v.spec().seats.get(0);
        var w = v.position().add(VehicleEntity.toWorldOffset(seat.x * 1.8, 0, seat.z, v.getYRot()));
        sp.teleportTo(sp.serverLevel(), w.x, v.getY(), w.z, v.getYRot(), 0);
        sp.startRiding(v);
        v.setFlag(VehicleEntity.F_ENGINE, true);
    }

    static void sensorScene(MinecraftServer server) {
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        sp.stopRiding();
        VehicleEntity target = null;
        for (int id : ids) if (server.overworld().getEntity(id) instanceof VehicleEntity v && v.spec().screenSensor) target = v;
        if (target == null) {
            TrafficCraft.LOG.info("[carshow] no car with a screen");
            return;
        }
        ServerLevel lvl = server.overworld();
        Vec3 front = target.position().add(VehicleEntity.toWorldOffset(0, 0, target.spec().length / 2 + 0.7, target.getYRot()));
        for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy < 2; dy++)
            lvl.setBlockAndUpdate(BlockPos.containing(front.add(VehicleEntity.toWorldOffset(dx, 0, 0, target.getYRot()))).above(dy), Blocks.STONE_BRICKS.defaultBlockState());
        // another car beside it
        VehicleEntity side = new VehicleEntity(TCRegistry.VEHICLE.get(), lvl);
        side.setModel(target.getModel());
        Vec3 sp2 = target.position().add(VehicleEntity.toWorldOffset(-target.spec().width - 0.6, 0, -0.5, target.getYRot()));
        side.moveTo(sp2.x, sp2.y, sp2.z, target.getYRot(), 0);
        lvl.addFreshEntity(side);
        ride(server, target.getId());
    }

    static void shot(Minecraft mc, String name) {
        if (name.startsWith("c_bf")) for (var en : mc.level.entitiesForRendering()) if (en instanceof VehicleEntity v)
            TrafficCraft.LOG.info("[carshow] " + name + " " + v.getModel() + " bfx=" + v.bfx.size() + " sparks=" + v.sparks.size() + " lastBf=" + (v.tickCount - v.backfireTick));
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> {});
        TrafficCraft.LOG.info("[carshow] screenshot " + name);
    }
}
