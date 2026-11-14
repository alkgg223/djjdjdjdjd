package com.vehicleworks.client;

import com.vehicleworks.VehicleWorks;
import com.vehicleworks.network.Payloads;
import com.vehicleworks.physics.sim.ThrusterUnit;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleLayout;
import com.vehicleworks.vehicle.VehicleRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Comparator;

/**
 * Optional scripted client session used for automated visual checks (enabled only with
 * -Dvehicleworks.autotest=true). Spawns demo vehicles, seats the player, opens the
 * engineering screens, saves screenshots and quits.
 */
public final class ClientAutoTest {
    private static int ticks;
    private static int shot;

    private ClientAutoTest() {}

    public static boolean enabled() {
        return Boolean.getBoolean("vehicleworks.autotest");
    }

    private static void cmd(String c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendCommand(c);
    }

    private static void screenshot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "vw_" + (++shot) + "_" + name + ".png", mc.getMainRenderTarget(), m -> VehicleWorks.LOGGER.info("[autotest] screenshot {}", m.getString()));
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

    private static VehicleEntity nearestServerVehicle(ServerPlayer sp, String which) {
        return VehicleRegistry.all(sp.level()).stream().filter(v -> v.layout() != null && v.layout().pilotSeat() != null)
                .filter(v -> which.isEmpty() || v.profile().id().equals(which))
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(sp))).orElse(null);
    }

    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        switch (ticks) {
            case 40 -> {
                cmd("gamerule doDaylightCycle false");
                cmd("time set 1000");
                cmd("weather clear");
                cmd("gamemode creative");
                cmd("tp @s 0 -60 0 0 15");
            }
            case 60 -> { cmd("tp @s 0 -60 0 -150 10"); cmd("vehicleworks spawn fighter"); }
            case 70 -> { cmd("tp @s 0 -60 0 150 10"); cmd("vehicleworks spawn trainer"); }
            case 80 -> { cmd("tp @s 0 -60 0 90 10"); cmd("vehicleworks spawn car"); }
            case 90 -> { cmd("tp @s 0 -60 0 -90 10"); cmd("vehicleworks spawn helicopter"); }
            case 100 -> { cmd("tp @s 0 -60 0 0 10"); cmd("vehicleworks spawn airship"); }
            case 160 -> cmd("tp @s 6 -54 22 180 22");
            case 200 -> screenshot("parked_vehicles");
            case 210 -> cmd("tp @s -10 -53 12 -120 25");
            case 250 -> screenshot("fighter_closeup");
            case 260 -> onServer(sp -> {
                VehicleEntity v = nearestServerVehicle(sp, "aircraft");
                if (v != null) { VehicleLayout.Seat s = v.layout().pilotSeat(); v.trySit(sp, s.pos()); }
            });
            case 300 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 320 -> screenshot("cockpit_hud");
            case 330 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(sp -> {
                    if (sp.getVehicle() instanceof VehicleEntity v) {
                        for (ThrusterUnit t : v.simulation().thrusters) t.engine.requestStart();
                        v.controls.gearDown = true;
                    }
                });
            }
            case 420 -> screenshot("engine_running_third_person");
            case 430 -> {
                var v = ClientEvents.ridden();
                if (v != null) Payloads.toServer(new Payloads.Action(v.getId(), Payloads.ACT_OPEN_ENGINEERING, 0));
            }
            case 470 -> screenshot("engineering_overview");
            case 480 -> {
                if (mc.screen instanceof EngineeringScreen) mc.setScreen(null);
                var v = ClientEvents.ridden();
                if (v != null) {
                    CompoundTag d = new CompoundTag();
                    d.putString("Type", "delta_wing");
                    CompoundTag cfg = new CompoundTag();
                    com.vehicleworks.block.ComponentBlockEntity.fillDefaults(com.vehicleworks.component.ComponentType.DELTA_WING, cfg);
                    d.put("Config", cfg);
                    d.putInt("Plane", 0);
                    mc.setScreen(new ComponentScreen(v.getId(), 0L, d));
                }
            }
            case 510 -> screenshot("wing_configuration");
            case 520 -> {
                mc.setScreen(null);
                var v = ClientEvents.ridden();
                if (v != null) {
                    CompoundTag d = new CompoundTag();
                    net.minecraft.nbt.ListTag links = new net.minecraft.nbt.ListTag();
                    for (int i = 0; i < 4; i++) {
                        CompoundTag l = new CompoundTag();
                        l.putIntArray("Off", new int[]{i % 2 == 0 ? -1 : 1, i < 2 ? -1 : -2, i < 2 ? -4 : 3});
                        l.putInt("Ch", i < 2 ? 0 : 1);
                        l.putString("Name", i < 2 ? "block.vehicleworks.machine_gun" : "block.vehicleworks.missile_launcher");
                        l.putBoolean("Valid", true);
                        links.add(l);
                    }
                    d.put("Links", links);
                    mc.setScreen(new RemoteControlScreen(v.getId(), 0L, d));
                }
            }
            case 550 -> screenshot("remote_control_unit");
            case 560 -> {
                mc.setScreen(null);
                onServer(sp -> {
                    if (sp.getVehicle() instanceof VehicleEntity v) v.controls.throttle = 1;
                });
            }
            case 600 -> {
                // walk on a vehicle: stand on the airship gondola
                onServer(sp -> sp.stopRiding());
            }
            case 620 -> onServer(sp -> {
                VehicleEntity v = nearestServerVehicle(sp, "airship");
                if (v != null) sp.teleportTo(v.getX(), v.getBoundingBox().minY + 1.6, v.getZ() - 1.5);
            });
            case 680 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); screenshot("standing_on_airship"); }
            case 700 -> {
                VehicleWorks.LOGGER.info("[autotest] meshes drawn={} culled={}", RenderEvents.drawnVehicles, RenderEvents.culledVehicles);
                for (VehicleEntity v : VehicleRegistry.all(mc.level)) {
                    VehicleClientState s = ClientProxy.state(v);
                    VehicleWorks.LOGGER.info("[autotest] vehicle {} blocks={} meshVertices={} profile={} pos={}", v.getId(), v.structure.size(),
                            s.mesh != null ? s.mesh.vertexCount : -1, v.telemetry.profile, v.position());
                }
            }
            case 720 -> mc.stop();
            default -> {}
        }
    }
}
