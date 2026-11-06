package com.arkragnarok.client.test;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.entity.RexEntity;
import com.arkragnarok.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-game screenshot run for CI (-Dark.shots=true, started with --quickPlaySingleplayer).  Spawns the Rex in the
 * world, flies the camera around it and saves screenshots of every side, close-ups, the saddle with a rider, and
 * frames of its animations; then closes the game.
 */
@EventBusSubscriber(modid = ArkRagnarok.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ShotDirector {
    private static final Logger LOG = LoggerFactory.getLogger("ARK-SHOTS");
    private static final boolean ENABLED = Boolean.getBoolean("ark.shots");

    /** a camera position around the Rex: angle (deg, 0 = in front of it), distance, eye height, look-at height, state */
    private record Shot(String name, float angle, float dist, float eye, float lookY, boolean saddled, int forced, String trigger,
                        int delay, boolean rider) {}

    private static final List<Shot> SHOTS = new ArrayList<>();

    static {
        SHOTS.add(new Shot("01_front", 0, 13, 3.0f, 2.6f, false, 0, null, 40, false));
        SHOTS.add(new Shot("02_front_left", 45, 13, 3.4f, 2.4f, false, 0, null, 30, false));
        SHOTS.add(new Shot("03_left", 90, 14, 2.6f, 2.2f, false, 0, null, 30, false));
        SHOTS.add(new Shot("04_back_left", 135, 13, 3.6f, 2.2f, false, 0, null, 30, false));
        SHOTS.add(new Shot("05_back", 180, 13, 3.4f, 2.4f, false, 0, null, 30, false));
        SHOTS.add(new Shot("06_back_right", 225, 13, 3.6f, 2.2f, false, 0, null, 30, false));
        SHOTS.add(new Shot("07_right", 270, 14, 2.6f, 2.2f, false, 0, null, 30, false));
        SHOTS.add(new Shot("08_front_right", 315, 13, 3.4f, 2.4f, false, 0, null, 30, false));
        SHOTS.add(new Shot("09_top", 60, 4, 15.0f, 1.5f, false, 0, null, 40, false));
        SHOTS.add(new Shot("10_low_front", 20, 9, 0.6f, 3.0f, false, 0, null, 30, false));
        SHOTS.add(new Shot("11_head_closeup", 35, 5.5f, 3.6f, 3.3f, false, 0, null, 30, false));
        SHOTS.add(new Shot("12_feet_closeup", 60, 5.5f, 0.9f, 0.4f, false, 0, null, 30, false));
        SHOTS.add(new Shot("13_saddle", 70, 9, 5.5f, 3.2f, true, 0, null, 40, false));
        SHOTS.add(new Shot("14_saddle_closeup", 110, 5.5f, 4.6f, 3.3f, true, 0, null, 30, false));
        SHOTS.add(new Shot("15_rider", 60, 11, 4.2f, 3.4f, true, 0, null, 40, true));
        SHOTS.add(new Shot("16_walk_a", 90, 14, 2.4f, 2.2f, false, 1, null, 23, false));
        SHOTS.add(new Shot("17_walk_b", 90, 14, 2.4f, 2.2f, false, 1, null, 16, false));
        SHOTS.add(new Shot("18_run", 90, 14, 2.4f, 2.2f, false, 2, null, 30, false));
        SHOTS.add(new Shot("19_sprint", 90, 14, 2.4f, 2.2f, false, 3, null, 30, false));
        SHOTS.add(new Shot("20_roar", 40, 12, 3.0f, 3.0f, false, 0, "roar", 26, false));
        SHOTS.add(new Shot("21_bite", 40, 11, 2.6f, 2.4f, false, 0, "bite", 6, false));
        SHOTS.add(new Shot("22_attack", 40, 11, 2.6f, 2.4f, false, 0, "attack", 12, false));
        SHOTS.add(new Shot("23_eat", 60, 12, 2.6f, 1.6f, false, 4, null, 30, false));
        SHOTS.add(new Shot("24_sleep", 60, 11, 2.6f, 1.0f, false, 5, null, 40, false));
        SHOTS.add(new Shot("25_death", 60, 13, 3.0f, 1.0f, false, 6, null, 75, false));
    }

    private static int phase = -1;
    private static int wait;
    private static int index;
    private static UUID rexId;
    private static UUID riderId;
    private static double ox, oy, oz;

    private ShotDirector() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer srv = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || srv == null) return;
        if (phase == -1) {
            phase = 0;
            wait = 120;
            mc.options.hideGui = true;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            LOG.info("ARK-SHOTS world loaded, preparing");
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            if (phase == 0) {
                srv.execute(() -> setup(srv, mc.player.getUUID()));
                phase = 1;
                wait = 40;
                return;
            }
            if (phase == 1) {
                if (index >= SHOTS.size()) {
                    LOG.info("ARK-SHOTS done ({} shots)", SHOTS.size());
                    phase = 3;
                    wait = 20;
                    return;
                }
                Shot s = SHOTS.get(index);
                srv.execute(() -> stage(srv, mc.player.getUUID(), s));
                phase = 2;
                wait = s.delay();
                return;
            }
            if (phase == 2) {
                Shot s = SHOTS.get(index);
                Screenshot.grab(mc.gameDirectory, "rex_" + s.name() + ".png", mc.getMainRenderTarget(), msg -> LOG.info("ARK-SHOTS {}", msg.getString()));
                LOG.info("ARK-SHOTS captured {}", s.name());
                index++;
                phase = 1;
                wait = 2;
                return;
            }
            if (phase == 3) {
                phase = 4;
                mc.stop();
            }
        } catch (Throwable t) {
            LOG.error("ARK-SHOTS failed", t);
            phase = 3;
        }
    }

    private static void setup(IntegratedServer srv, UUID playerId) {
        ServerLevel level = srv.overworld();
        level.setDayTime(6000);
        level.setWeatherParameters(12000, 0, false, false);
        srv.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, srv);
        srv.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, srv);
        ServerPlayer sp = srv.getPlayerList().getPlayer(playerId);
        BlockPos base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp != null ? sp.blockPosition() : level.getSharedSpawnPos());
        ox = base.getX() + 0.5;
        oy = base.getY();
        oz = base.getZ() + 0.5;
        RexEntity rex = ModEntities.REX.get().create(level);
        if (rex == null) throw new IllegalStateException("could not create the rex");
        rex.moveTo(ox, oy, oz, 0f, 0f);
        rex.setYBodyRot(0f);
        rex.setYHeadRot(0f);
        rex.setNoAi(true);
        rex.setPersistenceRequired();
        rex.setInvulnerable(true);
        level.addFreshEntity(rex);
        rexId = rex.getUUID();
        if (sp != null) {
            sp.getAbilities().flying = true;
            sp.getAbilities().mayfly = true;
            sp.onUpdateAbilities();
        }
        LOG.info("ARK-SHOTS rex spawned at {} {} {}", ox, oy, oz);
    }

    private static void stage(IntegratedServer srv, UUID playerId, Shot s) {
        ServerLevel level = srv.overworld();
        Entity ent = level.getEntity(rexId);
        if (!(ent instanceof RexEntity rex)) return;
        rex.moveTo(ox, oy, oz, 0f, 0f);
        rex.setYBodyRot(0f);
        rex.setYHeadRot(0f);
        rex.setSaddled(s.saddled());
        rex.setForcedAnim(s.forced());
        if (s.trigger() != null) rex.triggerAnim("action", s.trigger());
        if (s.rider() && riderId == null) {
            Villager v = EntityType.VILLAGER.create(level);
            if (v != null) {
                v.moveTo(ox, oy + 4, oz, 0f, 0f);
                v.setNoAi(true);
                v.setInvulnerable(true);
                level.addFreshEntity(v);
                v.startRiding(rex, true);
                riderId = v.getUUID();
            }
        } else if (!s.rider() && riderId != null) {
            Entity v = level.getEntity(riderId);
            if (v != null) v.discard();
            riderId = null;
        }
        // the Rex faces +z (south) at yaw 0; angle 0 puts the camera in front of it
        double a = Math.toRadians(s.angle());
        double cx = ox + Math.sin(a) * s.dist();
        double cz = oz + Math.cos(a) * s.dist();
        double cy = oy + s.eye();
        double tx = ox, ty = oy + s.lookY(), tz = oz;
        double dx = tx - cx, dy = ty - cy, dz = tz - cz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        ServerPlayer sp = srv.getPlayerList().getPlayer(playerId);
        if (sp != null) {
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            sp.teleportTo(level, cx, cy - sp.getEyeHeight(), cz, yaw, pitch);
            sp.setYHeadRot(yaw);
        }
    }
}
