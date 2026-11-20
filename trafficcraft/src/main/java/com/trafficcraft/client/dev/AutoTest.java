package com.trafficcraft.client.dev;

import com.trafficcraft.TrafficCraft;
import com.trafficcraft.client.DriverInput;
import com.trafficcraft.registry.TCRegistry;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehicleInput;
import com.trafficcraft.vehicle.VehicleSpecs;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Development-only automated in-game test (enabled with -Dtrafficcraft.autotest=true). */
@EventBusSubscriber(modid = TrafficCraft.MODID, value = Dist.CLIENT)
public final class AutoTest {
    static final boolean ENABLED = Boolean.getBoolean("trafficcraft.autotest") && !"town".equals(System.getProperty("trafficcraft.autotest.mode"))
            && !"cars".equals(System.getProperty("trafficcraft.autotest.mode"));
    static final boolean TOWN = "town".equals(System.getProperty("trafficcraft.autotest.mode"));
    static int tt = -1;
    static double gy;

    static void view(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.teleportTo(sp.serverLevel(), x, gy + y, z, yaw, pitch);
        });
    }

    @SubscribeEvent
    public static void townTick(ClientTickEvent.Post e) {
        if (!TOWN) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        tt++;
        var server = mc.getSingleplayerServer();
        if (tt == 60) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            ServerLevel lvl = sp.serverLevel();
            var rn = com.trafficcraft.road.RoadData.get(lvl);
            gy = rn.nodes.isEmpty() ? lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0) : rn.nodes.values().iterator().next().y;
            if (rn.nodes.isEmpty()) com.trafficcraft.world.DemoTown.build(lvl, new net.minecraft.core.BlockPos(0, (int) gy, 0));
            lvl.setDayTime(3000);
            lvl.setWeatherParameters(6000, 0, false, false);
            sp.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            sp.getAbilities().flying = true;
            sp.getAbilities().mayfly = true;
            sp.onUpdateAbilities();
            mc.options.hideGui = true;
        });
        int[] at = {700, 760, 820, 880, 940, 1000, 1060, 1120, 1700, 1820, 1880};
        if (tt == 640) view(mc, -14, 26, 24, 150, 50);
        if (tt == at[0]) shot(mc, "t01_crossroads.png");
        if (tt == at[0] + 5) view(mc, -26, 18, 18, -150, 40);
        if (tt == at[1]) shot(mc, "t02_avenue_west.png");
        if (tt == at[1] + 5) view(mc, 78, 34, 22, 180, 60);
        if (tt == at[2]) shot(mc, "t03_roundabout.png");
        if (tt == at[2] + 5) view(mc, -14, 16, 46, 120, 40);
        if (tt == at[3]) shot(mc, "t04_parking.png");
        if (tt == at[3] + 5) view(mc, 44, 14, -12, 135, 32);
        if (tt == at[4]) shot(mc, "t05_gas_station.png");
        if (tt == at[4] + 5) view(mc, 10, 10, 28, 140, 30);
        if (tt == at[5]) shot(mc, "t06_border.png");
        if (tt == at[5] + 5) view(mc, 9, 3.5, 11, 135, 12);
        if (tt == at[6]) shot(mc, "t07_street_level.png");
        if (tt == at[6] + 5) view(mc, 60, 22, 14, 160, 45);
        if (tt == at[7]) shot(mc, "t08_east_road.png");
        if (tt == at[7] + 5) view(mc, -14, 3.2, -14, -40, 14);
        if (tt == 1180) shot(mc, "t10_ped_crossing.png");
        if (tt == 1185) view(mc, 30, 2.3, -42.6, 180, 16);
        if (tt == 1240) shot(mc, "t11_cashier.png");
        if (tt == 1245) view(mc, 32.5, 3.2, -23.5, 140, 22);
        if (tt == 1300) shot(mc, "t12_attendant.png");
        if (tt == 1305) view(mc, 7, 3.5, 45, 127, 18);
        if (tt == 1360) shot(mc, "t13_officer.png");
        if (tt == 1365) view(mc, -36, 22, 6, 160, 40);
        if (tt == 1400) shot(mc, "t14_kits.png");
        if (tt == 1405) view(mc, 55, 45, 100, 170, 50);
        if (tt == 1440) shot(mc, "t15_road_pieces.png");
        if (tt == 1445) server.execute(() -> closeUp(server, false));
        if (tt == 1470) shot(mc, "t16_ai_driver.png");
        if (tt == 1475) server.execute(() -> closeUp(server, true));
        if (tt == 1500) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        if (tt == 1530) {
            if (mc.player.getVehicle() instanceof VehicleEntity rv) {
                mc.player.setYRot(rv.getYRot() - 55);
                mc.player.setXRot(12);
                log("riding " + rv.getModel() + " seat=" + rv.seatOf(mc.player) + " mode=" + rv.getMode());
            } else log("not riding");
        }
        if (tt == 1540) shot(mc, "t17_passenger_view.png");
        if (tt == 1545) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        if (tt == 1590) shot(mc, "t18_riding_third_person.png");
        if (tt == 1600) {
            if (mc.player.getVehicle() instanceof VehicleEntity rv) log(String.format("after 100 ticks riding: speed=%.1f km/h state=%s", rv.visSpeed() * 3.6, rv.getMode()));
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.stopRiding();
                sp.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            });
        }
        if (tt == 1610) server.execute(() -> bikeShot(server, false));
        if (tt == 1630) shot(mc, "t19_bike_rider.png");
        if (tt == 1632) server.execute(() -> bikeShot(server, true));
        if (tt == 1637 && mc.level.getEntity(bikeId) instanceof VehicleEntity cb) {
            cb.handleEntityEvent((byte) 62);
            log("client backfire on " + cb.getModel() + " exhausts=" + cb.spec().exhausts.size());
        }
        if (tt == 1638) shot(mc, "t20_bike_backfire.png");
        if (tt == 1640) server.execute(() -> server.overworld().setDayTime(18000));
        if (tt == at[8] - 20) view(mc, -14, 22, 24, 150, 40);
        if (tt == at[8]) shot(mc, "t09_night_crossroads.png");
        if (tt == at[9]) {
            StringBuilder sb = new StringBuilder();
            for (var en : server.overworld().getAllEntities()) if (en instanceof VehicleEntity v && v.driver != null)
                sb.append(String.format("%s %s v=%.1f (%.1f,%.1f,%.1f)%n", v.getModel(), v.driver.debug(), v.speed * 3.6, v.getX(), v.getY() - gy, v.getZ()));
            log(sb.toString());
            try (java.io.FileWriter w = new java.io.FileWriter(mc.gameDirectory.toPath().resolve("autotest_report.txt").toFile())) {
                for (String s2 : log) w.write(s2 + "\n");
            } catch (Exception ex) {
            }
        }
        if (tt == at[10]) mc.stop();
    }
    /** Camera beside a waiting AI car (or, with ride, put the player into it as a passenger). */
    static void closeUp(net.minecraft.server.MinecraftServer server, boolean ride) {
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        VehicleEntity best = null;
        double bd = 1e9;
        for (var en : server.overworld().getAllEntities()) {
            if (!(en instanceof VehicleEntity v) || v.getMode() != VehicleEntity.MODE_AI || v.spec().seats.size() < 2) continue;
            double d = v.position().distanceTo(new net.minecraft.world.phys.Vec3(-10, gy, 0)) + (Math.abs(v.speed) < 0.5f ? 0 : 40) + (v.driver != null && v.driver.state == com.trafficcraft.ai.TrafficDriver.State.DRIVE ? 0 : 200);
            if (d < bd) {
                bd = d;
                best = v;
            }
        }
        if (best == null) {
            log("closeUp: no AI vehicle");
            return;
        }
        if (ride) {
            sp.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            // stand at the right front door so the nearest free seat is the front passenger seat
            var seat = best.spec().seats.get(1);
            var w = best.position().add(VehicleEntity.toWorldOffset(seat.x * 1.8, 0, seat.z, best.getYRot()));
            sp.teleportTo(sp.serverLevel(), w.x, best.getY(), w.z, best.getYRot(), 0);
            boolean ok = sp.startRiding(best);
            log("ride " + best.getModel() + " ok=" + ok);
            return;
        }
        var side = VehicleEntity.toWorldOffset(Math.signum(best.spec().seats.get(0).x) * 2.4, 0, best.spec().seats.get(0).z + 0.9, best.getYRot());
        var cam = best.position().add(side).add(0, 1.55, 0);
        var to = best.position().add(VehicleEntity.toWorldOffset(best.spec().seats.get(0).x, 0, best.spec().seats.get(0).z, best.getYRot())).add(0, 0.9, 0).subtract(cam);
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z)));
        sp.teleportTo(sp.serverLevel(), cam.x, cam.y, cam.z, yaw, pitch);
        log("closeUp " + best.getModel() + " " + best.driver.debug());
    }

    /** Camera beside the nearest AI motorcycle; with fire, make it backfire (big bang) for the flame shot. */
    static void bikeShot(net.minecraft.server.MinecraftServer server, boolean fire) {
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        VehicleEntity best = null;
        double bd = 1e9;
        for (var en : server.overworld().getAllEntities()) {
            if (!(en instanceof VehicleEntity v) || !v.spec().bike) continue;
            double d = v.position().distanceTo(sp.position()) + (Math.abs(v.speed) < 0.5f ? 0 : 30) + (v.getModel().equals("sport_bike") ? 0 : 60);
            if (fire && bikeId >= 0) d = v.getId() == bikeId ? 0 : 1e8;
            if (d < bd) {
                bd = d;
                best = v;
            }
        }
        if (best == null) {
            log("bikeShot: no motorcycle");
            return;
        }
        if (!fire) bikeId = best.getId();
        // rider shot from the left-front, flame shot from behind on the exhaust (right) side
        var side = fire ? VehicleEntity.toWorldOffset(-1.6, 0, -2.4, best.getYRot()) : VehicleEntity.toWorldOffset(2.2, 0, -1.2, best.getYRot());
        var cam = best.position().add(side).add(0, 1.2, 0);
        var to = best.position().add(0, 0.7, 0).subtract(cam);
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z)));
        sp.teleportTo(sp.serverLevel(), cam.x, cam.y, cam.z, yaw, pitch);
        log("bikeShot " + best.getModel() + " " + (best.driver != null ? best.driver.debug() : ""));
    }

    static void fireBike(net.minecraft.server.MinecraftServer server) {
        if (server.overworld().getEntity(bikeId) instanceof VehicleEntity v) server.overworld().broadcastEntityEvent(v, (byte) 62);
    }

    static int bikeId = -1;
    static int t = -1;
    static VehicleInput input;
    static final List<String> log = new ArrayList<>();
    static int sedanId = -1;
    static double groundY;
    static double x0, z0;
    static float yaw0;

    static void log(String s) {
        log.add(s);
        TrafficCraft.LOG.info("[autotest] " + s);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (t < 0) {
            VehicleEntity.CLIENT_INPUT = v -> input != null ? input : DriverInput.read(v);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        t++;
        var server = mc.getSingleplayerServer();
        switch (t) {
            case 60 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                ServerLevel lvl = sp.serverLevel();
                lvl.setDayTime(5000);
                lvl.setWeatherParameters(6000, 0, false, false);
                groundY = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
                List<String> ids = new ArrayList<>(VehicleSpecs.ids());
                ids.remove("sedan");
                ids.add(4, "sedan");
                for (int i = 0; i < ids.size(); i++) {
                    VehicleEntity v = new VehicleEntity(TCRegistry.VEHICLE.get(), lvl);
                    v.setModel(ids.get(i));
                    var spec = VehicleSpecs.get(ids.get(i));
                    v.setColor(spec.colors[0]);
                    v.setFuel(spec.fuelCapacity * 0.7f);
                    v.setPlate(VehicleEntity.randomPlate(lvl.random));
                    v.moveTo(-17 + i * 4.25, groundY, 10 - spec.length / 2, 0, 0);
                    lvl.addFreshEntity(v);
                    if (ids.get(i).equals("sedan")) sedanId = v.getId();
                }
                sp.getAbilities().flying = true;
                sp.onUpdateAbilities();
                sp.teleportTo(lvl, 0, groundY + 3.2, -3, 0, 18);
                log("spawned " + ids.size() + " vehicles at y=" + groundY);
            });
            case 140 -> shot(mc, "01_lineup_day.png");
            case 150 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.teleportTo(sp.serverLevel(), 4.6, groundY + 1.7, 12.6, 135, 12);
            });
            case 190 -> shot(mc, "02_sedan_front34.png");
            case 200 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.teleportTo(sp.serverLevel(), -4.2, groundY + 1.7, 2.6, -45, 12);
            });
            case 240 -> shot(mc, "03_sedan_rear34.png");
            case 250 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.getAbilities().flying = false;
                var v = (VehicleEntity) sp.serverLevel().getEntity(serverSedanId(sp));
                if (v != null) {
                    sp.startRiding(v, true);
                    log("mounted sedan, passengers=" + v.getPassengers().size());
                }
            });
            case 300 -> shot(mc, "04_driver_view_engine_off.png");
            case 305 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                if (sp.getVehicle() instanceof VehicleEntity v) v.startEngine();
            });
            case 380 -> {
                VehicleEntity v = clientSedan(mc);
                log("engine on after start sequence: " + (v != null && v.has(VehicleEntity.F_ENGINE)) + " rpm=" + (v == null ? 0 : (int) v.rpm));
                shot(mc, "05_driver_view_engine_on.png");
                VehicleEntity sv = clientSedan(mc);
                if (sv != null) {
                    x0 = sv.getX();
                    z0 = sv.getZ();
                    yaw0 = sv.getYRot();
                }
                input = new VehicleInput(1, 0, false);
            }
            case 400, 420, 440, 460 -> {
                VehicleEntity v = clientSedan(mc);
                if (v != null) log(String.format("accelerating t=%d speed=%.1f km/h gear=%d rpm=%d", t - 380, v.speed * 3.6, v.gear, (int) v.rpm));
            }
            case 470 -> {
                VehicleEntity v = clientSedan(mc);
                if (v != null) log(String.format("distance driven=%.1f m, heading change=%.1f deg", Math.hypot(v.getX() - x0, v.getZ() - z0), v.getYRot() - yaw0));
                shot(mc, "06_driving_first_person.png");
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                input = new VehicleInput(0.7f, 1, false);
                yaw0 = v != null ? v.getYRot() : 0;
            }
            case 520 -> {
                VehicleEntity v = clientSedan(mc);
                if (v != null) log(String.format("turn left: heading change=%.1f deg speed=%.1f km/h steer=%.1f", v.getYRot() - yaw0, v.speed * 3.6, v.steer));
                shot(mc, "07_driving_third_person_turn.png");
                input = new VehicleInput(-1, 0, false);
            }
            case 580 -> {
                VehicleEntity v = clientSedan(mc);
                if (v != null) log(String.format("after braking: speed=%.2f km/h brake light=%s", v.speed * 3.6, v.has(VehicleEntity.F_BRAKE)));
            }
            case 620 -> {
                VehicleEntity v = clientSedan(mc);
                if (v != null) log(String.format("reversing: speed=%.1f km/h gear=%d reverse light=%s", v.speed * 3.6, v.gear, v.has(VehicleEntity.F_REVERSE)));
                input = new VehicleInput(0, 0, true);
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                    sp.serverLevel().setDayTime(18000);
                    if (sp.getVehicle() instanceof VehicleEntity sv) {
                        sv.handleAction(sp, VehicleEntity.A_LIGHTS);
                        sv.handleAction(sp, VehicleEntity.A_HAZARD);
                    }
                    for (var en : sp.serverLevel().getAllEntities()) if (en instanceof VehicleEntity ov && ov.getId() != serverSedanId(sp)) {
                        ov.startEngine();
                        ov.setFlag(VehicleEntity.F_LOW, true);
                        if (ov.spec().siren) ov.setFlag(VehicleEntity.F_BEACONS, true);
                    }
                });
            }
            case 700 -> shot(mc, "08_night_third_person.png");
            case 705 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 740 -> shot(mc, "09_night_front_headlights.png");
            case 745 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                input = null;
            }
            case 780 -> shot(mc, "10_night_driver_view.png");
            case 790 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.serverLevel().setDayTime(18000);
                sp.stopRiding();
                sp.teleportTo(sp.serverLevel(), 0, groundY + 3.2, -3, 0, 18);
                sp.getAbilities().flying = true;
                sp.onUpdateAbilities();
            });
            case 840 -> shot(mc, "11_lineup_night.png");
            case 850 -> {
                // collision test: two vehicles must not overlap
                int overlaps = 0, count = 0;
                List<VehicleEntity> vs = new ArrayList<>();
                for (var en : mc.level.entitiesForRendering()) if (en instanceof VehicleEntity v) vs.add(v);
                for (int i = 0; i < vs.size(); i++) for (int j = i + 1; j < vs.size(); j++) {
                    count++;
                    if (vs.get(i).overlaps(vs.get(j), vs.get(i).position(), vs.get(i).getYRot())) overlaps++;
                }
                log("vehicle pairs checked=" + count + " overlapping=" + overlaps);
                try (FileWriter w = new FileWriter(mc.gameDirectory.toPath().resolve("autotest_report.txt").toFile())) {
                    for (String s : log) w.write(s + "\n");
                } catch (Exception ex) {
                    TrafficCraft.LOG.error("report", ex);
                }
            }
            case 870 -> mc.stop();
            default -> { }
        }
    }

    static int serverSedanId(ServerPlayer sp) {
        return sedanId;
    }

    static VehicleEntity clientSedan(Minecraft mc) {
        return mc.player.getVehicle() instanceof VehicleEntity v ? v : null;
    }

    static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> {});
        log("screenshot " + name);
    }
}
