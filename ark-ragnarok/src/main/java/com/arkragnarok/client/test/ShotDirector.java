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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-game screenshot run for CI (-Dark.shots=true with --quickPlaySingleplayer): every species at real size next to a
 * villager for scale plus a close-up of its face, head-tracking checks (looking up and sideways for 2 s), then the
 * T-Rex from all sides, saddled with a rider, and frames of its animations; then the game closes.
 */
@EventBusSubscriber(modid = ArkRagnarok.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ShotDirector {
    private static final Logger LOG = LoggerFactory.getLogger("ARK-SHOTS");
    private static final boolean ENABLED = Boolean.getBoolean("ark.shots");

    /**
     * angle: camera around the creature (0 = in front); dist/eye/lookY in blocks; target: "body", "head" or "rex"
     * (fixed numbers for the Rex suite); pitch/headYaw: forced look; forced: forced animation id.
     */
    private record Shot(String name, String species, float angle, float dist, float eye, float lookY, boolean saddled, int forced,
                        String trigger, int delay, boolean rider, float headYaw, float pitch, String target) {}

    private static final List<Shot> SHOTS = new ArrayList<>();

    private static void rex(String name, float angle, float dist, float eye, float lookY, boolean saddled, int forced, String trigger,
                            int delay, boolean rider, float headYaw) {
        SHOTS.add(new Shot(name, "tyrannosaurus", angle, dist, eye, lookY, saddled, forced, trigger, delay, rider, headYaw, 0, "rex"));
    }

    static {
        for (Species s : SpeciesList.all()) {
            // whole animal from the side, a little from the front, villager in front of it for scale
            SHOTS.add(new Shot("a_" + s.id, s.id, 70, 0, 0, 0, false, 0, null, 30, false, 0, 0, "body"));
            // the face, three-quarter front, at eye level
            SHOTS.add(new Shot("h_" + s.id, s.id, 38, 0, 0, 0, false, 0, null, 20, false, 0, 0, "head"));
        }
        // head tracking: looking up and to the side for 2 s - the neck must stay smooth (no twisting / winding up)
        for (String id : new String[] {"dodo", "therizinosaurus", "gallimimus", "utahraptor", "pachycephalosaurus", "triceratops",
                "brontosaurus", "tyrannosaurus"}) {
            SHOTS.add(new Shot("look_" + id, id, 60, 0, 0, 0, false, 0, null, 45, false, 45, -35, "body"));
        }
        SHOTS.add(new Shot("look_walk_therizinosaurus", "therizinosaurus", 300, 0, 0, 0, false, 1, null, 45, false, -40, -30, "body"));
        SHOTS.add(new Shot("look_walk_dodo", "dodo", 300, 0, 0, 0, false, 1, null, 45, false, -40, 30, "body"));
        rex("rex_front", 0, 16, 3.6f, 3.0f, false, 0, null, 30, false, 0);
        rex("rex_left", 90, 17, 3.0f, 2.6f, false, 0, null, 25, false, 0);
        rex("rex_back", 180, 16, 4.0f, 2.8f, false, 0, null, 25, false, 0);
        rex("rex_top", 60, 5, 18.0f, 1.8f, false, 0, null, 30, false, 0);
        rex("rex_look_left", 20, 12, 3.4f, 3.4f, false, 0, null, 25, false, 45);
        rex("rex_look_right", 340, 12, 3.4f, 3.4f, false, 0, null, 25, false, -45);
        rex("rex_saddle", 70, 11, 7.0f, 4.0f, true, 0, null, 30, false, 0);
        rex("rex_rider", 60, 13, 5.2f, 4.2f, true, 0, null, 35, true, 0);
        rex("rex_walk", 90, 17, 2.8f, 2.6f, false, 1, null, 23, false, 0);
        rex("rex_run", 90, 17, 2.8f, 2.6f, false, 2, null, 25, false, 0);
        rex("rex_roar", 40, 15, 3.6f, 3.6f, false, 0, "roar", 26, false, 0);
        rex("rex_bite", 40, 13, 3.0f, 3.0f, false, 0, "bite", 6, false, 0);
        rex("rex_attack", 40, 13, 3.0f, 3.0f, false, 0, "attack", 12, false, 0);
        rex("rex_death", 60, 15, 3.6f, 1.2f, false, 6, null, 75, false, 0);
        SHOTS.add(new Shot("trike_attack", "triceratops", 50, 0, 0, 0, false, 0, "attack", 12, false, 0, 0, "body"));
        SHOTS.add(new Shot("stego_attack", "stegosaurus", 300, 0, 0, 0, false, 0, "attack", 15, false, 0, 0, "body"));
        SHOTS.add(new Shot("bronto_walk", "brontosaurus", 80, 0, 0, 0, false, 1, null, 25, false, 0, 0, "body"));
        SHOTS.add(new Shot("raptor_pounce", "utahraptor", 70, 0, 0, 0, false, 0, "attack", 13, false, 0, 0, "body"));
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
            sp.setGameMode(GameType.SPECTATOR);   // no collisions with the creatures, nothing of the player in the frame
            // a tiny camera holder: its eye can go down to the eye level of a Compy or a Dodo
            AttributeInstance scale = sp.getAttribute(Attributes.SCALE);
            if (scale != null) scale.setBaseValue(0.0625);
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
        c.setXRot(s.pitch());
        c.xRotO = s.pitch();
        c.setSaddled(s.saddled());
        c.setForcedAnim(s.forced());
        if (s.trigger() != null) c.triggerAnim("action", s.trigger());
        Entity scale = scaleId != null ? level.getEntity(scaleId) : null;
        if (scale != null) {
            // in front of the nose and a little to the side: never between the camera and the creature
            Species sv = SpeciesList.get(s.species());
            double side = Math.sin(Math.toRadians(s.angle())) >= 0 ? -1 : 1;     // the side away from the camera
            scale.moveTo(ox + side * Math.max(0.9, sv.width * 0.6), oy, oz + sv.headZ * 1.15 + Math.max(0.9, sv.length * 0.12), 200f, 0f);
            if (scale instanceof Villager vv) vv.setInvisible(s.target().equals("head"));
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
        Species sp_ = SpeciesList.get(s.species());
        // what the camera looks at, and from how far
        double tx = ox, ty, tz = oz, dist, camY;
        if (s.target().equals("rex")) {
            ty = oy + s.lookY();
            dist = s.dist();
            camY = oy + s.eye();
        } else if (s.target().equals("head")) {
            ty = oy + sp_.eye * 0.97;
            tz = oz + sp_.headZ * 1.08;
            dist = Math.max(0.45, sp_.length * 0.11 + 0.35);
            camY = ty + dist * 0.12;
        } else {
            ty = oy + sp_.height * 0.5;
            tz = oz + sp_.headZ * 1.1 - sp_.length * 0.5;
            dist = Math.max(1.5, (sp_.length * 0.5 + 1.2) / 1.05);
            camY = ty + dist * 0.16;
        }
        double a = Math.toRadians(s.angle());
        double cx = tx + Math.sin(a) * dist;
        double cz = tz + Math.cos(a) * dist;
        double cy = camY;
        double dx = tx - cx, dy = ty - cy, dz = tz - cz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        ServerPlayer sp = srv.getPlayerList().getPlayer(playerId);
        if (sp != null) {
            sp.teleportTo(level, cx, cy - sp.getEyeHeight(), cz, yaw, pitch);
            sp.setYHeadRot(yaw);
        }
    }
}
