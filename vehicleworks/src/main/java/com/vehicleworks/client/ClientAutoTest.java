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
        switch (ticks) {
            case 40 -> {
                cmd("gamerule doDaylightCycle false");
                cmd("time set 6000");
                cmd("weather clear");
                cmd("tp @s 0 -60 0 180 0");
            }
            case 60 -> { if (Boolean.getBoolean("vehicleworks.autotest.quick")) ticks = 1079; else cmd("vehicleworks spawn pelican"); }
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
            case 1215 -> lookAlong(-60);
            case 1235 -> screenshot("a320_overhead_panel");
            case 1240 -> { cmd("time set 18000"); lookAlong(14); }
            case 1270 -> screenshot("a320_cockpit_night");
            case 1290 -> mc.stop();
            default -> {}
        }
        if (ticks >= 900 && ticks <= 1070 && ticks % 3 == 0 && mc.player != null && (cv = clientVehicle()) != null) {
            Vector3d lp = cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d());
            log("c{} local=({},{},{}) onGround={} anchored={} vSpeed={} dist={} | {}", ticks, String.format("%.2f", lp.x), String.format("%.2f", lp.y), String.format("%.2f", lp.z),
                    mc.player.onGround(), com.vehicleworks.vehicle.EntityVehicleCollision.anchoredVehicle(mc.player) != null,
                    String.format("%.1f", cv.telemetry.groundSpeed), String.format("%.1f", Math.sqrt(cv.distanceToSqr(mc.player))), com.vehicleworks.vehicle.EntityVehicleCollision.DEBUG);
        }
        // fast carry: hold the Pelican at 60 m/s with the player standing on the wing
        if (ticks > 990 && ticks < 1066) onServer(sp -> {
            VehicleEntity v = serverVehicle(sp);
            if (v == null || v.simulation() == null) return;
            Vector3d f = v.localDirToWorld(0, 0, -1, new Vector3d());
            v.simulation().body.linearVelocity.set(f.x * 60, 0, f.z * 60);
            v.simulation().body.wake();
        });
        if (((ticks > 785 && ticks < 815) || (ticks > 905 && ticks < 935)) && (cv = clientVehicle()) != null)
            jumpMaxY = Math.max(jumpMaxY, cv.worldToLocal(mc.player.getX(), mc.player.getY(), mc.player.getZ(), new Vector3d()).y);
        if (((ticks > 760 && ticks <= 780) || (ticks > 880 && ticks <= 900) || (ticks > 785 && ticks < 795)) && mc.player != null)
            log("t{} onGround={} hColl={} vColl={} dm={} {}", ticks, mc.player.onGround(), mc.player.horizontalCollision, mc.player.verticalCollision,
                    mc.player.getDeltaMovement(), com.vehicleworks.vehicle.EntityVehicleCollision.DEBUG);
    }

    /** look along the ridden vehicle's nose (yaw from its heading) */
    private static void lookAlong(float pitch) {
        VehicleEntity v = ClientEvents.ridden();
        look(v != null ? v.telemetry.heading + 180 : 180, pitch);
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
