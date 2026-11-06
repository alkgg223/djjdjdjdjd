package com.arkragnarok.client.test;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.entity.ArkCreature;
import com.arkragnarok.registry.ModEntities;
import com.arkragnarok.species.Species;
import com.arkragnarok.species.SpeciesList;
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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-game screenshot run for CI (-Dark.shots=true with --quickPlaySingleplayer): one portrait of every species at
 * real size next to a villager for scale, then the T-Rex from all sides, saddled with a rider, and frames of its
 * animations; then the game closes.
 */
@EventBusSubscriber(modid = ArkRagnarok.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ShotDirector {
    private static final Logger LOG = LoggerFactory.getLogger("ARK-SHOTS");
    private static final boolean ENABLED = Boolean.getBoolean("ark.shots");

    private record Shot(String name, String species, float angle, float dist, float eye, float lookY, boolean saddled, int forced,
                        String trigger, int delay, boolean rider, float headYaw) {}

    private static final List<Shot> SHOTS = new ArrayList<>();

    static {
        for (Species s : SpeciesList.all()) {
            float d = Math.max(3.5f, s.lengthM * 1.05f + 2.5f);
            SHOTS.add(new Shot("a_" + s.id, s.id, 55, d, Math.max(1.0f, s.height * 0.75f), s.height * 0.45f, false, 0, null, 30, false, 0));
        }
        String r = "tyrannosaurus";
        SHOTS.add(new Shot("rex_front", r, 0, 16, 3.6f, 3.0f, false, 0, null, 30, false, 0));
        SHOTS.add(new Shot("rex_left", r, 90, 17, 3.0f, 2.6f, false, 0, null, 25, false, 0));
        SHOTS.add(new Shot("rex_back", r, 180, 16, 4.0f, 2.8f, false, 0, null, 25, false, 0));
        SHOTS.add(new Shot("rex_top", r, 60, 5, 18.0f, 1.8f, false, 0, null, 30, false, 0));
        SHOTS.add(new Shot("rex_head", r, 35, 7, 4.6f, 4.2f, false, 0, null, 25, false, 0));
        SHOTS.add(new Shot("rex_look_left", r, 20, 12, 3.4f, 3.4f, false, 0, null, 25, false, 45));
        SHOTS.add(new Shot("rex_look_right", r, 340, 12, 3.4f, 3.4f, false, 0, null, 25, false, -45));
        SHOTS.add(new Shot("rex_saddle", r, 70, 11, 7.0f, 4.0f, true, 0, null, 30, false, 0));
        SHOTS.add(new Shot("rex_rider", r, 60, 13, 5.2f, 4.2f, true, 0, null, 35, true, 0));
        SHOTS.add(new Shot("rex_walk", r, 90, 17, 2.8f, 2.6f, false, 1, null, 23, false, 0));
        SHOTS.add(new Shot("rex_run", r, 90, 17, 2.8f, 2.6f, false, 2, null, 25, false, 0));
        SHOTS.add(new Shot("rex_roar", r, 40, 15, 3.6f, 3.6f, false, 0, "roar", 26, false, 0));
        SHOTS.add(new Shot("rex_bite", r, 40, 13, 3.0f, 3.0f, false, 0, "bite", 6, false, 0));
        SHOTS.add(new Shot("rex_attack", r, 40, 13, 3.0f, 3.0f, false, 0, "attack", 12, false, 0));
        SHOTS.add(new Shot("rex_death", r, 60, 15, 3.6f, 1.2f, false, 6, null, 75, false, 0));
        SHOTS.add(new Shot("trike_attack", "triceratops", 50, 12, 2.6f, 1.6f, false, 0, "attack", 12, false, 0));
        SHOTS.add(new Shot("stego_attack", "stegosaurus", 300, 13, 3.0f, 2.0f, false, 0, "attack", 15, false, 0));
        SHOTS.add(new Shot("bronto_walk", "brontosaurus", 80, 30, 6.0f, 5.0f, false, 1, null, 25, false, 0));
        SHOTS.add(new Shot("raptor_pounce", "utahraptor", 70, 8, 1.8f, 1.4f, false, 0, "attack", 13, false, 0));
    }

    private static int phase = -1;
    private static int wait;
    private static int index;
    private static UUID creatureId;
    private static String creatureSpecies;
    private static UUID riderId;
    private static UUID scaleId;
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
                Screenshot.grab(mc.gameDirectory, s.name() + ".png", mc.getMainRenderTarget(), msg -> LOG.info("ARK-SHOTS {}", msg.getString()));
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
        srv.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, srv);
        srv.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, srv);
        ServerPlayer sp = srv.getPlayerList().getPlayer(playerId);
        BlockPos base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp != null ? sp.blockPosition() : level.getSharedSpawnPos());
        ox = base.getX() + 0.5;
        oy = base.getY();
        oz = base.getZ() + 0.5;
        if (sp != null) {
            sp.getAbilities().flying = true;
            sp.getAbilities().mayfly = true;
            sp.onUpdateAbilities();
        }
        // a villager standing beside the creature shows the real size
        Villager v = EntityType.VILLAGER.create(level);
        if (v != null) {
            v.moveTo(ox + 4, oy, oz + 3, 200f, 0f);
            v.setNoAi(true);
            v.setInvulnerable(true);
            level.addFreshEntity(v);
            scaleId = v.getUUID();
        }
    }

    private static ArkCreature creature(ServerLevel level, String id) {
        Entity ent = creatureId != null ? level.getEntity(creatureId) : null;
        if (ent instanceof ArkCreature c && id.equals(creatureSpecies)) return c;
        if (ent != null) ent.discard();
        ArkCreature c = ModEntities.type(id).create(level);
        if (c == null) throw new IllegalStateException("could not create " + id);
        c.setLevel(1);
        c.moveTo(ox, oy, oz, 0f, 0f);
        c.setNoAi(true);
        c.setPersistenceRequired();
        c.setInvulnerable(true);
        level.addFreshEntity(c);
        creatureId = c.getUUID();
        creatureSpecies = id;
        return c;
    }

    private static void stage(IntegratedServer srv, UUID playerId, Shot s) {
        ServerLevel level = srv.overworld();
        ArkCreature c = creature(level, s.species());
        c.moveTo(ox, oy, oz, 0f, 0f);
        c.setYBodyRot(0f);
        c.setYHeadRot(s.headYaw());
        c.setSaddled(s.saddled());
        c.setForcedAnim(s.forced());
        if (s.trigger() != null) c.triggerAnim("action", s.trigger());
        Entity scale = scaleId != null ? level.getEntity(scaleId) : null;
        if (scale != null) {
            double side = Math.max(2.5, c.getBbWidth() * 0.5 + 1.8);
            scale.moveTo(ox + side, oy, oz + side * 0.6, 200f, 0f);
        }
        if (s.rider() && riderId == null) {
            Villager v = EntityType.VILLAGER.create(level);
            if (v != null) {
                v.moveTo(ox, oy + 6, oz, 0f, 0f);
                v.setNoAi(true);
                v.setInvulnerable(true);
                level.addFreshEntity(v);
                v.startRiding(c, true);
                riderId = v.getUUID();
            }
        } else if (!s.rider() && riderId != null) {
            Entity v = level.getEntity(riderId);
            if (v != null) v.discard();
            riderId = null;
        }
        double a = Math.toRadians(s.angle());
        double cx = ox + Math.sin(a) * s.dist();
        double cz = oz + Math.cos(a) * s.dist();
        double cy = oy + s.eye();
        double dx = ox - cx, dy = oy + s.lookY() - cy, dz = oz - cz;
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
