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
    static final boolean ON = Boolean.getBoolean("trafficcraft.autotest") && "cars".equals(System.getProperty("trafficcraft.autotest.mode"));
    static int tt = -1;
    static double gy;
    static final List<Integer> ids = new ArrayList<>();
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
            sp.teleportTo(sp.serverLevel(), eye.x, eye.y, eye.z, yaw, pitch);
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
        if (t == 5) server.execute(() -> {
            for (var en : server.overworld().getAllEntities()) if (en instanceof VehicleEntity v) {
                v.setFlag(VehicleEntity.F_ENGINE, true);
                v.setFlag(VehicleEntity.F_LOW, true);
                v.setFlag(VehicleEntity.F_BRAKE, true);
            }
        });
        VehicleEntity v0 = car(mc, 0);
        if (v0 == null) return;
        if (t == 10) view(mc, rel(v0, 2.6, 1.6, 5.5), rel(v0, 0, 0.6, 0));
        if (t == 50) shot(mc, "c_night_front.png");
        if (t == 55) view(mc, rel(v0, -2.4, 1.5, -5.5), rel(v0, 0, 0.6, 0));
        if (t == 95) shot(mc, "c_night_rear.png");
        if (t == 100) server.execute(() -> server.overworld().setDayTime(3000));
        // driver's seat (steering wheel, gear lever, gauges)
        if (t == 105) server.execute(() -> ride(server, ids.get(0)));
        if (t == 115) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (t == 125 && mc.player.getVehicle() instanceof VehicleEntity rv) {
            mc.player.setYRot(rv.getYRot());
            mc.player.setXRot(18);
        }
        if (t == 150) shot(mc, "c_interior.png");
        // parking sensor: drive the first car that has a screen, wall just ahead
        if (t == 155) server.execute(() -> sensorScene(server));
        if (t == 175) {
            mc.options.hideGui = false;
            if (mc.player.getVehicle() instanceof VehicleEntity rv) {
                mc.player.setYRot(rv.getYRot());
                mc.player.setXRot(10);
            }
        }
        if (t == 215) shot(mc, "c_park_sensor.png");
        if (t == 220) {
            mc.options.hideGui = true;
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.stopRiding();
                sp.setGameMode(GameType.SPECTATOR);
            });
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        // line-up
        if (t == 230) {
            VehicleEntity last = car(mc, n - 1);
            Vec3 mid = v0.position().add(last.position()).scale(0.5);
            view(mc, mid.add(-4, 9, -14), mid);
        }
        if (t == 270) shot(mc, "c_lineup.png");
        if (t == 280) {
            TrafficCraft.LOG.info("[carshow] done");
            mc.stop();
        }
    }

    static void setup(MinecraftServer server) {
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        ServerLevel lvl = sp.serverLevel();
        gy = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        lvl.setDayTime(3000);
        lvl.setWeatherParameters(6000, 0, false, false);
        sp.setGameMode(GameType.SPECTATOR);
        sp.teleportTo(lvl, 0, gy + 4, -10, 0, 20);
        TrafficCraft.LOG.info("[carshow] spawning " + models.size() + " cars at y=" + gy);
        for (int i = 0; i < models.size(); i++) {
            VehicleEntity v = new VehicleEntity(TCRegistry.VEHICLE.get(), lvl);
            v.setModel(models.get(i));
            v.setColor(v.spec().colors[i % v.spec().colors.length]);
            v.setPlate("TC " + (1000 + i));
            v.moveTo(i * 6.0, gy, 0, 20, 0);
            lvl.addFreshEntity(v);
            ids.add(v.getId());
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
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> {});
        TrafficCraft.LOG.info("[carshow] screenshot " + name);
    }
}
