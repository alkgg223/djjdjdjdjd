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
    static final boolean ENABLED = Boolean.getBoolean("trafficcraft.autotest");
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
