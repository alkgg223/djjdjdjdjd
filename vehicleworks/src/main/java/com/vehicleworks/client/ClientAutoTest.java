package com.vehicleworks.client;

import com.vehicleworks.VehicleWorks;
import com.vehicleworks.vehicle.VehicleControls;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleLayout;
import com.vehicleworks.vehicle.VehicleRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3d;

import java.util.Comparator;

/**
 * Scripted client session for automated in-game checks (only with -Dvehicleworks.autotest=true):
 * creates a flat world, spawns the Pelican, takes screenshots of the exterior (day / night lights),
 * cockpit instruments, overhead panel, HUD modes and screens, runs the switch start sequence and
 * measures walking / jumping on a moving aircraft.
 */
public final class ClientAutoTest {
    private static int ticks, shot;
    private static boolean creating;
    private static int waitTicks;
    private static double walkStartZ, jumpBaseY, jumpMaxY;
    private static Vector3d walkStart;

    private ClientAutoTest() {}

    public static boolean enabled() {
        return Boolean.getBoolean("vehicleworks.autotest");
    }

    private static void cmd(String c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendCommand(c);
    }

    private static void log(String s, Object... a) {
        VehicleWorks.LOGGER.info("[autotest] " + s, a);
    }

    private static void screenshot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "vw_" + String.format("%02d", ++shot) + "_" + name + ".png", mc.getMainRenderTarget(), m -> log("screenshot {}", m.getString()));
    }

    private static void onServer(java.util.function.Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) action.accept(sp);
        });
    }

    private static VehicleEntity serverVehicle(ServerPlayer sp) {
        return VehicleRegistry.all(sp.level()).stream().filter(v -> v.layout() != null && v.layout().pilotSeat() != null)
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(sp))).orElse(null);
    }

    private static VehicleEntity clientVehicle() {
        Minecraft mc = Minecraft.getInstance();
        return VehicleRegistry.all(mc.level).stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(mc.player))).orElse(null);
    }

    private static void look(float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        mc.player.setYRot(yaw); mc.player.setXRot(pitch);
        mc.player.yRotO = yaw; mc.player.xRotO = pitch;
    }

    private static void createWorld(Minecraft mc) {
        creating = true;
        log("creating flat world");
        LevelSettings settings = new LevelSettings("AutoTest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("AutoTest", settings, new WorldOptions(12345L, false, false),
                ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
    }

    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            // title screen or the first-launch accessibility onboarding screen
            if (!creating && mc.screen != null && mc.getOverlay() == null && ++waitTicks > 60) { mc.options.onboardAccessibility = false; createWorld(mc); }
            return;
        }
        ticks++;
        VehicleEntity cv;
        if (ticks >= 6000) { lightTest(mc, ticks - 6000); return; }
        if (ticks >= 5000) { sitTest(mc, ticks - 5000); return; }
        if (ticks >= 2000) { cockpitAndCabin(mc, ticks - 2000); return; }
        switch (ticks) {
            case 40 -> {
                cmd("gamerule doDaylightCycle false");
                cmd("time set 6000");
                cmd("weather clear");
                cmd("tp @s 0 -60 0 180 0");
            }
            case 60 -> {
                if (Boolean.getBoolean("vehicleworks.autotest.lights")) ticks = 6000;
                else if (Boolean.getBoolean("vehicleworks.autotest.sit")) ticks = 5000;
                else if (Boolean.getBoolean("vehicleworks.autotest.cabin")) ticks = 2000;
                else if (Boolean.getBoolean("vehicleworks.autotest.quick")) ticks = 1079; else cmd("vehicleworks spawn pelican");
            }
            case 120 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); cmd("tp @s 15 -55 -4 facing 0 -58 -18"); }
            case 160 -> screenshot("pelican_day");
            case 170 -> {
                cmd("time set 18000");
                onServer(sp -> {
                    VehicleEntity v = serverVehicle(sp);
                    if (v == null) return;
                    VehicleControls c = v.controls;
                    c.battery = true; c.avionics = true; c.lights = true; c.beacon = true; c.strobe = true; c.landingLights = true; c.taxiLights = true; c.turnoffLights = true;
                    c.cabinLight = 2; c.panelLight = 2;
                });
            }
            case 200 -> cmd("tp @s 10 -56 -34 facing 0 -58 -18");
            case 230 -> screenshot("pelican_night_lights_front");
            case 240 -> cmd("tp @s 16 -55 -6 facing 0 -58 -18");
            case 270 -> screenshot("pelican_night_lights_side");
            case 280 -> onServer(sp -> {
                VehicleEntity v = serverVehicle(sp);
                if (v != null) { VehicleLayout.Seat s = v.layout().pilotSeat(); v.trySit(sp, s.pos()); }
            });
            case 310 -> look(180, 22);
            case 330 -> screenshot("cockpit_instruments_night");
            case 335 -> cmd("time set 6000");
            case 360 -> { look(180, 22); screenshot("cockpit_instruments_day"); }
            case 370 -> look(180, -70);
            case 390 -> screenshot("overhead_panel");
            case 400 -> { if ((cv = ClientEvents.ridden()) != null) mc.setScreen(new PanelScreen(cv)); }
            case 420 -> screenshot("panel_screen");
            case 430 -> mc.setScreen(null);
            // manual start sequence through the switches: battery is on, fuel pump, ignition, starter
            case 440 -> { if ((cv = ClientEvents.ridden()) != null) PanelScreen.pressSwitch(cv, VehicleControls.SW_FUEL_PUMP); }
            case 445 -> { if ((cv = ClientEvents.ridden()) != null) PanelScreen.pressSwitch(cv, VehicleControls.SW_IGNITION); }
            case 450 -> { if ((cv = ClientEvents.ridden()) != null) PanelScreen.pressSwitch(cv, VehicleControls.SW_STARTER); }
            case 600 -> {
                cv = ClientEvents.ridden();
                if (cv != null) {
                    StringBuilder sb = new StringBuilder();
                    for (byte st : cv.telemetry.engineState) sb.append(st).append(' ');
                    log("engine states after switch start: {} bus={} gen={} battery={}%", sb, cv.telemetry.busPowered, cv.telemetry.generator, Math.round(cv.telemetry.batteryCharge * 100));
                }
                look(180, 5);
                com.vehicleworks.Config.CLIENT.hudMode.set(0);
            }
            case 615 -> screenshot("hud_full");
            case 620 -> com.vehicleworks.Config.CLIENT.hudMode.set(1);
            case 635 -> screenshot("hud_minimal");
            case 640 -> com.vehicleworks.Config.CLIENT.hudMode.set(2);
            case 655 -> screenshot("hud_military");
            case 660 -> com.vehicleworks.Config.CLIENT.hudMode.set(3);
            case 675 -> screenshot("hud_navigation");
            case 680 -> { com.vehicleworks.Config.CLIENT.hudMode.set(0); if ((cv = ClientEvents.ridden()) != null) {
                CompoundTag d = new CompoundTag(); d.putInt("Grades", 4); d.putBoolean("Creative", true); mc.setScreen(new RefuelScreen(cv, d)); } }
            case 700 -> screenshot("refuel_screen");
            case 710 -> mc.setScreen(null);
            // walking on the wing: first parked, then while taxiing
            case 720 -> onServer(sp -> {
                VehicleEntity v = serverVehicle(sp);
                sp.stopRiding();
                if (v == null) return;
                v.controls.testPilot = true;
                v.controls.throttle = 0;
                v.controls.parkingBrake = true;
                Vector3d p = v.localToWorld(-5.5, 5.0, 0.5, new Vector3d());
                sp.teleportTo(p.x, p.y + 0.2, p.z);
            });
            case 760 -> walkBegin(mc);
            case 780 -> walkEnd(mc, "parked");
            case 785 -> jumpBegin(mc);
            case 815 -> log("jump on parked wing: peak {} blocks (vanilla 1.25)", String.format("%.2f", jumpMaxY - jumpBaseY));
            case 820 -> onServer(sp -> {
                VehicleEntity v = serverVehicle(sp);
                if (v == null) return;
                v.controls.parkingBrake = false;
                v.controls.throttle = 0.6;
                Vector3d p = v.localToWorld(-5.5, 5.0, 0.5, new Vector3d());
                sp.teleportTo(p.x, p.y + 0.2, p.z);
            });
            case 880 -> walkBegin(mc);
            case 900 -> walkEnd(mc, "moving");
            case 905 -> jumpBegin(mc);
            case 935 -> log("jump on moving wing: peak {} blocks (vanilla 1.25)", String.format("%.2f", jumpMaxY - jumpBaseY));
            case 940 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case 960 -> screenshot("standing_on_moving_wing");
            case 975 -> onServer(sp -> {
                VehicleEntity v = serverVehicle(sp);
                if (v == null) return;
                Vector3d p = v.localToWorld(-7.5, 5.0, 0.5, new Vector3d());
                sp.teleportTo(p.x, p.y + 0.2, p.z);
            });
            case 1000 -> { cv = clientVehicle(); if (cv != null) walkStart = cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d()); }
            case 1060 -> {
                cv = clientVehicle();
                if (cv != null && walkStart != null) {
                    Vector3d now = cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d());
                    log("carried at {} m/s for 3 s: drift {} blocks (local {} -> {}), onGround={}", String.format("%.0f", cv.telemetry.groundSpeed),
                            String.format("%.2f", now.distance(walkStart)), walkStart, now, mc.player.onGround());
                }
            }
            case 1065 -> screenshot("standing_on_fast_aircraft");
            // glass cockpit of the A320
            case 1080 -> onServer(sp -> { VehicleEntity v = serverVehicle(sp); if (v != null) v.discard(); sp.teleportTo(0.5, -60, 200.5); });
            case 1090 -> { look(180, 0); cmd("vehicleworks spawn a320"); }
            case 1160 -> onServer(sp -> {
                VehicleEntity v = serverVehicle(sp);
                if (v == null) return;
                v.controls.battery = true; v.controls.avionics = true; v.controls.panelLight = 2; v.controls.cabinLight = 1;
                VehicleLayout.Seat s = v.layout().pilotSeat(); v.trySit(sp, s.pos());
            });
            case 1190 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); lookAlong(14); }
            case 1210 -> screenshot("a320_glass_cockpit");
            case 1215 -> { lookAlong(-60); if (Boolean.getBoolean("vehicleworks.autotest.flight")) ticks = 1425; }
            case 1235 -> screenshot("a320_overhead_panel");
            case 1240 -> { cmd("time set 18000"); lookAlong(14); }
            case 1270 -> screenshot("a320_cockpit_night");
            // high speed: walk in the cabin, sit down with a right click, break a block
            case 1275 -> { cmd("time set 6000"); onServer(sp -> {
                VehicleEntity v = serverVehicle(sp);
                sp.stopRiding();
                if (v == null) return;
                Vector3d p = v.localToWorld(0.5, 1.05, -1.5, new Vector3d());
                sp.teleportTo(p.x, p.y, p.z);
            }); }
            case 1310 -> { cv = clientVehicle(); if (cv != null) log("in the cabin at {} m/s: local {}", String.format("%.0f", cv.telemetry.groundSpeed), cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d())); }
            case 1315 -> aimAt(1.5, 1.45, -0.5);
            case 1318 -> net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey());
            case 1335 -> log("right-click seat at speed: seated={}", mc.player.getVehicle() instanceof VehicleEntity);
            case 1340 -> onServer(sp -> {
                VehicleEntity v = serverVehicle(sp);
                sp.stopRiding();
                if (v == null) return;
                Vector3d p = v.localToWorld(0.5, 1.05, -1.5, new Vector3d());
                sp.teleportTo(p.x, p.y, p.z);
                blocksBefore = v.structure.size();
            });
            case 1360 -> aimAt(-1.5, 2.5, -1.5);
            case 1363 -> net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey());
            case 1380 -> onServer(sp -> { VehicleEntity v = serverVehicle(sp); if (v != null) log("break block at speed: blocks {} -> {}", blocksBefore, v.structure.size()); });
            case 1385 -> aimAt(1.5, 1.6, 3.5);
            case 1400 -> screenshot("passenger_seats");
            case 1405 -> { cv = clientVehicle(); if (cv != null) { Vector3d p = cv.localToWorld(-0.5, 1.6, -9.5, new Vector3d()); } aimAt(-0.5, 1.4, -11.5); }
            case 1420 -> screenshot("pilot_seats");
            // ---- real flight at 100 then 150 m/s, turning and banking, player walking in the cabin
            case 1425 -> ticks = Boolean.getBoolean("vehicleworks.autotest.quick") ? 1425 : 1425;
            case 1430 -> onServer(sp -> {
                VehicleEntity v = serverVehicle(sp);
                if (v == null || v.simulation() == null) return;
                v.simulation().body.position.add(0, 30, 0);
                flySpeed = 100;
            });
            case 1445 -> onServer(ServerPlayer::stopRiding);
            case 1448 -> placeInVehicle(0.5, 1.05, -1.5);
            case 1465 -> { cv = clientVehicle(); if (cv != null) walkStart = cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d()); }
            case 1480 -> logDrift("flying 100 m/s, turning");
            case 1482 -> { aimAt(0.5, 1.6, -12); walkStartLocal(); mc.options.keyUp.setDown(true); }
            case 1497 -> { mc.options.keyUp.setDown(false); logWalk("aisle at 100 m/s (15 ticks, vanilla ~2.9)"); }
            case 1502 -> jumpBegin(mc);
            case 1530 -> log("jump in cabin at 100 m/s: peak {} blocks (vanilla 1.25)", String.format("%.2f", jumpMaxY - jumpBaseY));
            case 1532 -> { flySpeed = 150; placeInVehicle(0.5, 1.05, -1.5); }
            case 1550 -> { cv = clientVehicle(); if (cv != null) walkStart = cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d()); }
            case 1565 -> logDrift("flying 150 m/s, turning");
            case 1567 -> aimAt(1.5, 1.45, -0.5);
            case 1570 -> net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey());
            case 1585 -> log("right-click seat at 150 m/s: seated={}", mc.player.getVehicle() instanceof VehicleEntity);
            case 1588 -> onServer(sp -> { sp.stopRiding(); VehicleEntity v = serverVehicle(sp); if (v != null) blocksBefore = v.structure.size(); });
            case 1600 -> { cv = clientVehicle(); if (cv != null) log("stepped out of the seat at 150 m/s: aboard={} local {}", EntityAboard(), cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d())); }
            case 1612 -> { cv = clientVehicle(); if (cv != null) log("2 s later at 150 m/s: aboard={} local {}", EntityAboard(), cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d())); }
            case 1613 -> aimAt(2.5, 2.6, -0.5);
            case 1616 -> net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey());
            case 1630 -> onServer(sp -> { VehicleEntity v = serverVehicle(sp); if (v != null) log("break block at 150 m/s: blocks {} -> {}", blocksBefore, v.structure.size()); });
            case 1633 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case 1640 -> screenshot("cabin_at_150ms");
            case 1645 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); cv = clientVehicle(); if (cv != null) log("end of flight: still aboard={} local {}", EntityAboard(), cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d())); }
            case 1650 -> mc.stop();
            default -> {}
        }
        if (((ticks >= 900 && ticks <= 1070) || (ticks >= 1445 && ticks <= 1650)) && ticks % 5 == 0 && mc.player != null && (cv = clientVehicle()) != null) {
            Vector3d lp = cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d());
            log("c{} local=({},{},{}) onGround={} anchored={} vSpeed={} dist={} | {}", ticks, String.format("%.2f", lp.x), String.format("%.2f", lp.y), String.format("%.2f", lp.z),
                    mc.player.onGround(), com.vehicleworks.vehicle.EntityVehicleCollision.anchoredVehicle(mc.player) != null,
                    String.format("%.1f", cv.telemetry.groundSpeed), String.format("%.1f", Math.sqrt(cv.distanceToSqr(mc.player))), com.vehicleworks.vehicle.EntityVehicleCollision.DEBUG);
        }
        // flight: hold speed and altitude, keep turning (yaw) and rocking the wings (roll)
        if (ticks > 1430 && ticks < 1650) onServer(sp -> {
            VehicleEntity v = serverVehicle(sp);
            if (v == null || v.simulation() == null) return;
            var b = v.simulation().body;
            Vector3d f = v.localDirToWorld(0, 0, -1, new Vector3d());
            Vector3d up = v.localDirToWorld(0, 1, 0, new Vector3d());
            Vector3d fl = new Vector3d(f.x, 0, f.z).normalize();
            b.linearVelocity.set(fl.x * flySpeed, 0, fl.z * flySpeed);
            double roll = Math.sin(ticks * 0.08) * 0.25;
            b.angularVelocity.set(0, 0.12, 0).add(new Vector3d(f).mul(roll)).sub(new Vector3d(up).mul(0));
            b.wake();
        });
        // fast carry: hold the aircraft at 60 m/s with the player on board
        if ((ticks > 990 && ticks < 1066) || (ticks > 1280 && ticks < 1400)) onServer(sp -> {
            VehicleEntity v = serverVehicle(sp);
            if (v == null || v.simulation() == null) return;
            Vector3d f = v.localDirToWorld(0, 0, -1, new Vector3d());
            v.simulation().body.linearVelocity.set(f.x * 60, 0, f.z * 60);
            v.simulation().body.wake();
        });
        if (((ticks > 785 && ticks < 815) || (ticks > 905 && ticks < 935) || (ticks > 1502 && ticks < 1530)) && (cv = clientVehicle()) != null)
            jumpMaxY = Math.max(jumpMaxY, cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d()).y);
        if (((ticks > 760 && ticks <= 780) || (ticks > 880 && ticks <= 900) || (ticks > 785 && ticks < 795) || (ticks >= 1500 && ticks < 1510)) && mc.player != null)
            log("t{} onGround={} hColl={} vColl={} dm={} {}", ticks, mc.player.onGround(), mc.player.horizontalCollision, mc.player.verticalCollision,
                    mc.player.getDeltaMovement(), com.vehicleworks.vehicle.EntityVehicleCollision.DEBUG);
    }

    // ================================================================== 1.2: cockpit power-up, engine start, side stick, cabin

    private static void press(int id) {
        VehicleEntity v = ClientEvents.ridden();
        if (v == null) v = clientVehicle();
        if (v != null) PanelScreen.pressSwitch(v, id);
    }

    private static void cabin(int control) {
        VehicleEntity v = clientVehicle();
        if (v != null) com.vehicleworks.network.Payloads.toServer(new com.vehicleworks.network.Payloads.Action(v.getId(), com.vehicleworks.network.Payloads.ACT_CABIN, control));
    }

    private static void sit(double x, double y, double z) {
        onServer(sp -> {
            VehicleEntity v = serverVehicle(sp);
            if (v == null) { log("sit: no vehicle"); return; }
            sp.stopRiding();
            long k = net.minecraft.core.BlockPos.asLong((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            boolean ok = v.trySit(sp, k);
            if (!ok) log("sit at {} {} {} failed: block {} seats {}", x, y, z, v.structure.get(k), v.layout() == null ? -1 : v.layout().seats.size());
        });
    }

    private static long suiteKey = Long.MIN_VALUE;
    private static float suiteRot;

    /** seat-frame offset (x right, y up, z backward) of the suite seat, in the vehicle's local frame */
    private static double[] suite(double ox, double oy, double oz) {
        Vector3d o = new Vector3d(ox, oy, oz).rotateY(-Math.toRadians(suiteRot));
        return new double[]{net.minecraft.core.BlockPos.getX(suiteKey) + 0.5 + o.x, net.minecraft.core.BlockPos.getY(suiteKey) + o.y, net.minecraft.core.BlockPos.getZ(suiteKey) + 0.5 + o.z};
    }

    private static void findSuite() {
        onServer(sp -> {
            VehicleEntity v = serverVehicle(sp);
            if (v == null) return;
            for (long k : v.structure.blockEntities().keySet()) {
                var st = v.structure.get(k);
                if (com.vehicleworks.vehicle.VehicleBuilder.typeOf(st) != com.vehicleworks.component.ComponentType.SEAT_FIRST) continue;
                suiteKey = k;
                suiteRot = CockpitRenderer.facingRot(com.vehicleworks.vehicle.VehicleBuilder.facing(st));
                sp.stopRiding();
                log("first suite at {} facing {} sat={}", net.minecraft.core.BlockPos.of(k), suiteRot, v.trySit(sp, k));
                return;
            }
        });
    }

    private static void state(String what) {
        VehicleEntity v = clientVehicle();
        if (v == null) return;
        var t = v.telemetry;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.engineState.length; i++) b.append(String.format(" [st=%d ph=%d N=%.2f egt=%.0f]", t.engineState[i], i < t.enginePhase.length ? t.enginePhase[i] : -1, t.engineSpool[i], t.engineTemp[i]));
        log("{}: hdg={} bank={} pitch={} gs={}", what, String.format("%.0f", t.heading), String.format("%.0f", t.bank), String.format("%.0f", t.pitch), String.format("%.1f", t.groundSpeed));
        log("{}: bus={} av={} stage={} hud={} backlight={} batt={} gen={} starter={} switches={}{}", what, t.busPowered, t.avionicsPowered(), Avionics.stage(v),
                String.format("%.2f", Avionics.hud(v)), String.format("%.2f", Avionics.backlight(v)), String.format("%.3f", t.batteryCharge), t.generator,
                t.sw(VehicleControls.BIT_STARTER), Integer.toBinaryString(t.switches), b);
    }

    private static void cockpitAndCabin(Minecraft mc, int t) {
        VehicleEntity cv;
        switch (t) {
            case 0 -> { cmd("tp @s 0.5 -60 200.5 180 0"); cmd("time set 6000"); if (Boolean.getBoolean("vehicleworks.autotest.suite")) ticks = 2000 + 1119; }
            case 10 -> cmd("vehicleworks spawn a320");
            case 14, 18, 22, 26, 30, 34, 38, 42, 46, 50, 55, 60, 65, 69, 75, 80, 85 -> onServer(sp -> { VehicleEntity v = serverVehicle(sp); if (v != null) {
                var up = v.localDirToWorld(0, 1, 0, new Vector3d()); log("pose t{}: up=({},{},{}) y={} v={} ground={}", t, String.format("%.2f", up.x), String.format("%.2f", up.y), String.format("%.2f", up.z),
                        String.format("%.2f", v.getY()), String.format("%.2f", v.linearVelocity.length()), v.simulation() != null && v.simulation().onGround); } });
            case 70 -> sit(-1, 1, -11);
            case 90 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); lookAlong(14); state("cold and dark"); }
            case 100 -> screenshot("c01_cold_dark_cockpit");
            case 105 -> { lookAlong(-62); }
            case 112 -> screenshot("c02_overhead_cold_dark");
            case 115 -> { press(VehicleControls.SW_BATTERY); }
            case 122 -> { press(VehicleControls.SW_MASTER); }
            case 132 -> { state("battery + master"); press(VehicleControls.SW_AVIONICS); }
            case 140 -> screenshot("c03_overhead_bus_on");
            case 150, 160, 170, 180, 190, 200, 210 -> onServer(sp -> { VehicleEntity v = serverVehicle(sp); if (v != null) log("server t{}: batt={} master={} av={} bus={} charge={}", t, v.controls.battery, v.controls.masterElec, v.controls.avionics, v.controls.busPowered, v.controls.batteryCharge); });
            case 145 -> lookAlong(14);
            case 165 -> { state("boot glow"); screenshot("c04_boot_glow"); }
            case 195 -> { state("boot logo"); screenshot("c05_boot_logo"); }
            case 222 -> { state("boot self test"); screenshot("c06_boot_selftest"); }
            case 252 -> { state("data appearing"); screenshot("c07_boot_data"); }
            case 300 -> { state("ready"); screenshot("c08_avionics_ready_hud"); }
            // manual turbofan start: fuel pump, engine master, ignition, starter
            case 310 -> press(VehicleControls.SW_FUEL_PUMP);
            case 315 -> press(VehicleControls.SW_ENG_MASTER);
            case 320 -> press(VehicleControls.SW_IGNITION);
            case 325 -> { press(VehicleControls.SW_STARTER); lookAlong(-62); }
            case 345 -> { state("starter engaged"); screenshot("c09_overhead_starting"); }
            case 400, 450, 500, 550, 600, 650, 799, 899, 999, 1099 -> state("start t" + t);
            case 660 -> screenshot("c10_overhead_engines_running");
            case 665 -> { cmd("time set 18000"); press(VehicleControls.SW_PANEL); }
            case 668 -> press(VehicleControls.SW_CONSOLE);
            case 690 -> screenshot("c11_overhead_night_backlit");
            case 692 -> { press(VehicleControls.SW_NIGHT); press(VehicleControls.SW_DIMMER); }
            case 710 -> screenshot("c12_overhead_night_mode");
            case 712 -> lookAlong(14);
            case 725 -> screenshot("c13_cockpit_night");
            case 730 -> { press(VehicleControls.SW_NIGHT); cmd("time set 6000"); }
            // side stick: hold pitch up + roll right on the server and look at the captain's stick
            case 740 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case 745 -> onServer(sp -> { VehicleEntity v = serverVehicle(sp); if (v != null) { v.controls.parkingBrake = true; } });
            case 750 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); aimAt(-0.8, 1.55, -11.25); }
            case 760 -> screenshot("c14_sidestick_neutral");
            case 762 -> { mc.options.keyDown.setDown(true); mc.options.keyRight.setDown(true); }
            case 796 -> { mc.options.keyDown.setDown(false); mc.options.keyRight.setDown(false); }
            case 790 -> { cv = clientVehicle(); if (cv != null) log("side stick deflection: pitch {} roll {} fbw={}", cv.telemetry.stickPitch, cv.telemetry.stickRoll, cv.telemetry.fbw); screenshot("c15_sidestick_deflected"); }
            // passenger: sit in a business seat, no flight HUD / controls
            case 800 -> sit(-1, 1, -7);
            case 820 -> {
                cv = clientVehicle();
                if (cv != null) log("business seat: passenger={} class={} ridden={}", cv.isPassengerSeated(mc.player), cv.seatClassOf(mc.player), ClientEvents.ridden() != null);
                aimAt(-1 + 0.5 - 0.47, 1.56, -7 + 0.5 - 0.15);
            }
            case 830 -> screenshot("c16_passenger_view_buttons");
            // press the arm-rest buttons: aim at the TV button and right-click it
            case 832 -> {
                cv = clientVehicle();
                if (cv != null) {
                    java.util.List<Integer> l = CabinRenderer.buttons(4);
                    int i = l.indexOf(com.vehicleworks.vehicle.CabinSeat.SCREEN);
                    float[] p = CabinRenderer.buttonPos(4, i);
                    // seat (-1,1,-7) faces north: seat frame = vehicle frame around the block centre
                    aimAt(-1 + 0.5 + p[0], 1 + p[1], -7 + 0.5 + p[2]);
                }
            }
            case 836 -> net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey());
            case 838 -> cmd("time set 18000"); // the cabin at night: reading lights, LEDs, screens
            case 850 -> {
                cv = clientVehicle();
                Long k = cv == null ? null : cv.seats.get(mc.player.getUUID());
                if (k != null) log("after TV button: screen={} state={}", com.vehicleworks.vehicle.CabinSeat.screen(cv.cabin.get((long) k)), cv.cabin.get((long) k));
                cabin(com.vehicleworks.vehicle.CabinSeat.TRAY);
                cabin(com.vehicleworks.vehicle.CabinSeat.LIGHT);
            }
            case 860 -> aimAt(-0.5, 1.82, -7.05); // the business seat's own screen
            case 900 -> screenshot("c17_business_seat_screen_tray");
            case 905 -> { cabin(com.vehicleworks.vehicle.CabinSeat.FLAT); }
            case 955 -> onServer(ServerPlayer::stopRiding);
            case 962 -> { placeInVehicle(0.5, 1.05, -5.6); aimAt(-0.5, 1.2, -7.2); }
            case 975 -> screenshot("c18_business_lie_flat");
            case 980 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); sit(-1, 1, -3); }
            case 990 -> { cabin(com.vehicleworks.vehicle.CabinSeat.SCREEN); cabin(com.vehicleworks.vehicle.CabinSeat.RECLINE_DOWN); cabin(com.vehicleworks.vehicle.CabinSeat.RECLINE_DOWN); cabin(com.vehicleworks.vehicle.CabinSeat.LEG_UP); }
            case 994 -> { cabin(com.vehicleworks.vehicle.CabinSeat.PAGE); }
            case 1000 -> sit(1, 1, -1);
            case 1010 -> { cabin(com.vehicleworks.vehicle.CabinSeat.RECLINE_DOWN); cabin(com.vehicleworks.vehicle.CabinSeat.LEG_UP); cabin(com.vehicleworks.vehicle.CabinSeat.SLIDE_FWD); }
            case 1020 -> sit(-1, 1, 1);
            case 1030 -> { cabin(com.vehicleworks.vehicle.CabinSeat.TRAY); cabin(com.vehicleworks.vehicle.CabinSeat.LIGHT); }
            case 1040 -> sit(1, 1, 3);
            // overview of the cabin classes from the aisle (standing, looking forward)
            case 1050 -> onServer(ServerPlayer::stopRiding);
            case 1056 -> { placeInVehicle(0.5, 1.05, 6.5); }
            case 1060 -> aimAt(0.5, 1.2, -8);
            case 1075 -> screenshot("c19_cabin_classes_forward");
            case 1078 -> { placeInVehicle(0.5, 1.05, -9.5); }
            case 1080 -> aimAt(0.0, 1.2, 4);
            case 1095 -> screenshot("c20_cabin_classes_aft");
            case 1100 -> { placeInVehicle(-0.5, 1.15, -1.9); aimAt(-0.87, 1.78, -3.0); }
            case 1115 -> screenshot("c21_premium_screen_map");
            // 787: first class suite with its door, bed, big screen and private lights
            case 1120 -> { onServer(sp -> { VehicleEntity v = serverVehicle(sp); if (v != null) v.discard(); }); cmd("tp @s 0.5 -60 400.5 180 0"); }
            case 1130 -> cmd("vehicleworks spawn b787");
            case 1200 -> findSuite();
            case 1215 -> { cabin(com.vehicleworks.vehicle.CabinSeat.SCREEN); cabin(com.vehicleworks.vehicle.CabinSeat.LIGHT); cabin(com.vehicleworks.vehicle.CabinSeat.LIGHT); }
            case 1220 -> onServer(sp -> { VehicleEntity v = serverVehicle(sp); if (v != null) { v.controls.battery = v.controls.masterElec = v.controls.avionics = true; } });
            case 1260 -> { double[] a = suite(0.5, 1.12, -1.43); aimAt(a[0], a[1], a[2]); }
            case 1300 -> { cv = clientVehicle(); if (cv != null) log("first suite passenger={} class={}", cv.isPassengerSeated(mc.player), cv.seatClassOf(mc.player)); screenshot("c22_first_suite_screen"); }
            case 1305 -> onServer(ServerPlayer::stopRiding);
            case 1310 -> { double[] c = suite(2.1, 0.05, 0.6), a = suite(1.45, 0.7, -0.5); placeInVehicle(c[0], c[1], c[2]); aimAt(a[0], a[1], a[2]); }
            case 1325 -> screenshot("c23_first_suite_door_closed");
            case 1327 -> findSuite();
            case 1330 -> { cabin(com.vehicleworks.vehicle.CabinSeat.DOOR); cabin(com.vehicleworks.vehicle.CabinSeat.FLAT); }
            case 1335 -> onServer(ServerPlayer::stopRiding);
            case 1345 -> { double[] c = suite(2.1, 0.05, 0.6), a = suite(0.6, 0.5, -0.5); placeInVehicle(c[0], c[1], c[2]); aimAt(a[0], a[1], a[2]); }
            case 1385 -> screenshot("c24_first_suite_door_open_bed");
            case 1388 -> { double[] c = suite(0.5, 1.15, -0.5), a = suite(0.5, 0, -0.45); placeInVehicle(c[0], c[1], c[2]); aimAt(a[0], a[1], a[2]); }
            case 1394 -> screenshot("c24b_first_suite_top_down");
            case 1395 -> cmd("time set 18000");
            case 1399 -> screenshot("c24c_first_suite_top_down_night");
            case 1400 -> { placeInVehicle(0.5, 1.05, -4.5); aimAt(-0.5, 1.2, -6.8); }
            case 1412 -> screenshot("c25_787_business_night");
            case 1420 -> mc.stop();
            default -> {}
        }
    }

    /** sit in every seat type the way a player does (right-click it), then press a cockpit switch while seated */
    private static final double[][] SEATS = {{-1, 1, -7}, {1, 1, -3}, {-1, 1, -1}, {1, 1, 3}, {1, 1, -11}, {-1, 1, -11}};
    private static final String[] SEAT_NAMES = {"business", "premium", "economy plus", "economy", "first officer", "captain"};

    private static void sitTest(Minecraft mc, int t) {
        VehicleEntity cv = clientVehicle();
        if (t == 0) { cmd("tp @s 0.5 -60 200.5 180 0"); cmd("time set 6000"); }
        if (t == 10) cmd("vehicleworks spawn a320");
        int i = (t - 80) / 40, ph = (t - 80) % 40;
        if (t >= 80 && i < SEATS.length) {
            double[] p = SEATS[i];
            if (ph == 0) { onServer(ServerPlayer::stopRiding); }
            if (ph == 5) placeInVehicle(0.5, 1.05, p[2] + (i >= 4 ? 1.6 : 0.9));
            if (ph == 12) aimAt(p[0] + 0.5, p[1] + 0.35, p[2] + 0.45);
            if (ph == 15) net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey());
            if (ph == 35 && cv != null) {
                Long k = cv.seats.get(mc.player.getUUID());
                log("sit by right-click on {}: seated={} seat={} passenger={} class={}", SEAT_NAMES[i], mc.player.getVehicle() == cv,
                        k == null ? "-" : net.minecraft.core.BlockPos.of(k).toShortString(), cv.isPassengerSeated(mc.player), cv.seatClassOf(mc.player));
            }
        }
        int t2 = t - 80 - 40 * SEATS.length; // seated as captain: right-click the BATT switch on the overhead panel
        if (t2 == 0 && cv != null) {
            for (long k : cv.structure.blockEntities().keySet()) {
                var st = cv.structure.get(k);
                if (com.vehicleworks.vehicle.VehicleBuilder.typeOf(st) != com.vehicleworks.component.ComponentType.COCKPIT_PANEL) continue;
                CockpitRenderer.Ctl c = CockpitRenderer.ctl(VehicleControls.SW_BATTERY);
                Vector3d o = new Vector3d(CockpitRenderer.cellX(c.col()), 14.5 / 16.0 - 0.03, CockpitRenderer.cellZ(c.row()) + 0.04)
                        .rotateY(-Math.toRadians(CockpitRenderer.facingRot(com.vehicleworks.vehicle.VehicleBuilder.facing(st))));
                aimAt(net.minecraft.core.BlockPos.getX(k) + 0.5 + o.x, net.minecraft.core.BlockPos.getY(k) + o.y, net.minecraft.core.BlockPos.getZ(k) + 0.5 + o.z);
                log("battery before click: {}", cv.telemetry.sw(VehicleControls.SW_BATTERY));
            }
        }
        if (t2 == 4) net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey());
        if (t2 == 25 && cv != null) { log("battery after right-click on the overhead switch while seated: {}", cv.telemetry.sw(VehicleControls.SW_BATTERY)); screenshot("s_captain_pressed_batt"); }
        if (t2 == 30) mc.stop();
    }

    /** put the (flying) camera at a vehicle-local point and look at another */
    private static void cam(double x, double y, double z, double tx, double ty, double tz) {
        Minecraft mc = Minecraft.getInstance();
        VehicleEntity v = clientVehicle();
        if (v == null) { log("cam: no vehicle"); return; }
        mc.player.getAbilities().flying = true;
        Vector3d w = v.localToWorld(x, y, z, new Vector3d());
        mc.player.setPos(w.x, w.y - 1.62, w.z);
        mc.player.setDeltaMovement(0, 0, 0);
        aimAt(tx, ty, tz);
    }

    /** exterior lights by day and by night, near and far */
    private static void lightTest(Minecraft mc, int t) {
        switch (t) {
            case 0 -> { cmd("tp @s 0.5 -60 200.5 180 0"); cmd("time set 6000"); mc.options.hideGui = true; }
            case 10 -> cmd("vehicleworks spawn a320");
            case 60 -> onServer(sp -> { VehicleEntity v = serverVehicle(sp); if (v != null) { var c = v.controls; c.battery = c.masterElec = true;
                c.lights = c.beacon = c.strobe = c.landingLights = c.taxiLights = c.turnoffLights = true; } });
            case 80 -> { cam(-14, 2, -22, 0, 0, -2); mc.options.hideGui = true; }
            case 100 -> screenshot("l1_day_front_left");
            case 105 -> cmd("time set 18000");
            case 140 -> screenshot("l2_night_front_left");
            case 143 -> screenshot("l3_night_front_left_b");
            case 146 -> screenshot("l4_night_front_left_c");
            case 150 -> cam(-20, 1.5, -5, -16, 0, 2);
            case 170 -> screenshot("l5_night_left_wingtip");
            case 173 -> screenshot("l6_night_left_wingtip_b");
            case 176 -> cam(0.5, 1, -40, 0.5, -0.5, -10);
            case 196 -> screenshot("l7_night_head_on");
            case 200 -> cam(26, 7, -26, 0, -2, -26);
            case 220 -> screenshot("l8_night_beams_side");
            case 224 -> cam(-60, 16, -45, 0, 0, 0);
            case 244 -> screenshot("l9_night_far");
            case 247 -> screenshot("l10_night_far_b");
            case 250 -> mc.stop();
            default -> {}
        }
    }

    private static void look(VehicleEntity v, float pitch) {
        look(v != null ? v.telemetry.heading + 180 : 180, pitch);
    }

    /** look along the ridden vehicle's nose (yaw from its heading) */
    private static void lookAlong(float pitch) {
        VehicleEntity v = ClientEvents.ridden();
        look(v != null ? v.telemetry.heading + 180 : 180, pitch);
    }

    private static int blocksBefore;

    /** put the local player at a point of the nearest vehicle as this client sees it (like walking in) */
    private static void placeInVehicle(double lx, double ly, double lz) {
        Minecraft mc = Minecraft.getInstance();
        VehicleEntity v = clientVehicle();
        if (v == null || mc.player.isPassenger()) return;
        Vector3d p = v.localToWorld(lx, ly, lz, new Vector3d());
        mc.player.setPos(p.x, p.y, p.z);
        mc.player.setDeltaMovement(0, 0, 0);
        com.vehicleworks.vehicle.EntityVehicleCollision.forget(mc.player);
    }
    private static double flySpeed = 100;
    private static Vector3d walkLocal;

    private static boolean EntityAboard() {
        return com.vehicleworks.vehicle.EntityVehicleCollision.anchoredVehicle(Minecraft.getInstance().player) != null || Minecraft.getInstance().player.getVehicle() != null;
    }

    private static void logDrift(String what) {
        Minecraft mc = Minecraft.getInstance();
        VehicleEntity cv = clientVehicle();
        if (cv == null || walkStart == null) return;
        Vector3d now = cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d());
        log("{}: speed {} m/s, drift {} blocks, aboard={}, onGround={}", what, String.format("%.0f", cv.telemetry.groundSpeed), String.format("%.2f", now.distance(walkStart)), EntityAboard(), mc.player.onGround());
    }

    private static void walkStartLocal() {
        Minecraft mc = Minecraft.getInstance();
        VehicleEntity cv = clientVehicle();
        if (cv != null) walkLocal = cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d());
    }

    private static void logWalk(String what) {
        Minecraft mc = Minecraft.getInstance();
        VehicleEntity cv = clientVehicle();
        if (cv == null || walkLocal == null) return;
        Vector3d now = cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d());
        log("walk {}: {} blocks, aboard={}", what, String.format("%.2f", Math.hypot(now.x - walkLocal.x, now.z - walkLocal.z)), EntityAboard());
    }

    /** point the camera at a point given in the nearest vehicle's local coordinates */
    private static void aimAt(double lx, double ly, double lz) {
        Minecraft mc = Minecraft.getInstance();
        VehicleEntity v = clientVehicle();
        if (v == null) return;
        Vector3d w = v.localToWorld(lx, ly, lz, new Vector3d());
        var eye = mc.player.getEyePosition(1f);
        double dx = w.x - eye.x, dy = w.y - eye.y, dz = w.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        look(yaw, pitch);
    }

    private static void walkBegin(Minecraft mc) {
        VehicleEntity cv = clientVehicle();
        if (cv != null) walkStart = cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d());
        look(90, 10); // walk out toward the left wingtip
        mc.options.keyUp.setDown(true);
    }

    private static void walkEnd(Minecraft mc, String what) {
        mc.options.keyUp.setDown(false);
        VehicleEntity cv = clientVehicle();
        if (cv != null && walkStart != null) {
            Vector3d now = cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d());
            double d = Math.hypot(now.x - walkStart.x, now.z - walkStart.z);
            log("walk on {} wing: {} blocks in 1 s (vanilla ~3.9 from standstill), vehicle {} m/s", what, String.format("%.2f", d), String.format("%.1f", cv.telemetry.groundSpeed));
        }
    }

    private static void jumpBegin(Minecraft mc) {
        VehicleEntity cv = clientVehicle();
        if (cv != null) { jumpBaseY = cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d()).y; jumpMaxY = jumpBaseY; }
        mc.player.jumpFromGround();
    }
}
