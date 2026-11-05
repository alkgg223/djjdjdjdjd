package com.gunsmod.entity.ai;

import com.gunsmod.Config;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotRideable;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.net.Net;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A player's squad: the soldiers of his side he called to him ("follow me"). He orders them about through the squad
 * screen: follow, hold, attack or defend a spot, patrol, fall back, get into or out of vehicles, guard someone, all fire
 * on one enemy, hold fire; and sets their formation. Orders reach the soldiers within the squad radius of the config.
 */
public final class Squad {
    private Squad() {}

    public enum Order { NONE, FOLLOW, HOLD, ATTACK, DEFEND, PATROL, RETREAT, PROTECT }

    public enum Formation { LINE, COLUMN, WEDGE, DEFENSIVE, CIRCLE, ESCORT }

    /** commands from the squad screen (the order ids sent by the client) */
    public static final int C_FOLLOW = 0, C_HOLD = 1, C_ATTACK = 2, C_DEFEND = 3, C_PATROL = 4, C_RETREAT = 5, C_ENTER = 6, C_EXIT = 7,
            C_PROTECT = 8, C_FOCUS = 9, C_HOLD_FIRE = 10, C_DISMISS = 11, C_FORMATION = 12;

    /** where squad member i of n stands, relative to the point it forms on, facing yaw (degrees, Minecraft convention) */
    public static Vec3 slot(Formation f, int i, int n, float yaw) {
        double x, z;
        switch (f) {
            case LINE -> {
                x = (i - (n - 1) / 2.0) * 3.0;
                z = -2.5;
            }
            case COLUMN -> {
                x = 0;
                z = -(i + 1) * 3.0;
            }
            case WEDGE -> {
                int k = i / 2 + 1;
                x = (i % 2 == 0 ? -1 : 1) * k * 2.6;
                z = -k * 2.6;
            }
            case DEFENSIVE -> {
                int row = i % 2;
                int col = i / 2;
                int cols = (n + 1) / 2;
                x = (col - (cols - 1) / 2.0) * 3.2 + (row == 1 ? 1.6 : 0);
                z = -2.0 - row * 3.0;
            }
            case CIRCLE -> {
                double a = 2 * Math.PI * i / Math.max(1, n);
                x = Math.cos(a) * 4.5;
                z = Math.sin(a) * 4.5;
            }
            default -> {
                // escort: round a vehicle, the front corners first, then the sides and the back
                double[][] p = {{-4.5, 5}, {4.5, 5}, {-5, 0}, {5, 0}, {-4.5, -5}, {4.5, -5}, {0, 7}, {0, -7}};
                double[] q = p[i % p.length];
                double ring = 1 + i / p.length * 0.4;
                x = q[0] * ring;
                z = q[1] * ring;
            }
        }
        // local (x right, z forward) to world; Minecraft yaw 0 faces +Z
        double r = Math.toRadians(yaw);
        double fx = -Math.sin(r), fz = Math.cos(r);
        double rx = -fz, rz = fx;
        return new Vec3(rx * x + fx * z, 0, rz * x + fz * z);
    }

    public static List<BotEntity> members(ServerPlayer p) {
        List<BotEntity> l = p.serverLevel().getEntitiesOfClass(BotEntity.class, p.getBoundingBox().inflate(256, 128, 256),
                b -> b.isAlive() && p.getUUID().equals(b.squadLeader));
        l.sort(Comparator.comparing(Entity::getUUID));
        return l;
    }

    private static List<BotEntity> recruits(ServerPlayer p) {
        double r = Config.squadRadius();
        BotTeam team = BotTeam.of(p);
        return p.serverLevel().getEntitiesOfClass(BotEntity.class, p.getBoundingBox().inflate(r, 24, r),
                b -> b.isAlive() && b.getBotTeam() == team && !b.operator && (b.squadLeader == null || p.getUUID().equals(b.squadLeader)));
    }

    /** where the player is looking (blocks up to 256 away), or null */
    @Nullable
    private static Vec3 lookPoint(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        BlockHitResult bh = p.level().clip(new ClipContext(eye, eye.add(p.getViewVector(1f).scale(256)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : null;
    }

    @Nullable
    private static LivingEntity lookEntity(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1f);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.5), e -> e != p && e.isAlive())) {
            var hit = e.getBoundingBox().inflate(0.4).clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < bestD) {
                bestD = eye.distanceToSqr(hit.get());
                best = e;
            }
        }
        return best;
    }

    private static void say(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args), true);
    }

    /** a command from the player's squad screen */
    public static void command(ServerPlayer p, int cmd, int formation) {
        List<BotEntity> squad = members(p);
        if (cmd == C_FOLLOW || squad.isEmpty()) {
            for (BotEntity b : recruits(p)) {
                if (!squad.contains(b)) squad.add(b);
            }
            squad.sort(Comparator.comparing(Entity::getUUID));
        }
        if (squad.isEmpty()) {
            say(p, "gunsmod.squad.none");
            return;
        }
        int n = squad.size();
        Vec3 look = lookPoint(p);
        float facing = p.getYRot();
        switch (cmd) {
            case C_FOLLOW -> {
                for (int i = 0; i < n; i++) squad.get(i).giveOrder(p.getUUID(), Order.FOLLOW, p.position(), -1, i, n);
                say(p, "gunsmod.squad.follow", n);
            }
            case C_HOLD -> {
                for (int i = 0; i < n; i++) squad.get(i).giveOrder(p.getUUID(), Order.HOLD, squad.get(i).position(), -1, i, n);
                say(p, "gunsmod.squad.hold", n);
            }
            case C_ATTACK, C_DEFEND, C_PATROL -> {
                if (look == null) {
                    say(p, "gunsmod.msg.too_far");
                    return;
                }
                Order o = cmd == C_ATTACK ? Order.ATTACK : cmd == C_DEFEND ? Order.DEFEND : Order.PATROL;
                for (int i = 0; i < n; i++) {
                    BotEntity b = squad.get(i);
                    b.giveOrder(p.getUUID(), o, look, -1, i, n);
                    b.orderFacing = facing;
                    if (o == Order.PATROL) b.patrolFrom = p.position();
                }
                say(p, cmd == C_ATTACK ? "gunsmod.squad.attack" : cmd == C_DEFEND ? "gunsmod.squad.defend" : "gunsmod.squad.patrol", Mth.floor(look.x),
                        Mth.floor(look.y), Mth.floor(look.z));
            }
            case C_RETREAT -> {
                for (int i = 0; i < n; i++) {
                    BotEntity b = squad.get(i);
                    Vec3 home = b.home != null ? Vec3.atBottomCenterOf(b.home) : p.position();
                    b.giveOrder(p.getUUID(), Order.RETREAT, home, -1, i, n);
                    b.setTarget(null);
                }
                say(p, "gunsmod.squad.retreat", n);
            }
            case C_ENTER -> {
                int in = 0;
                for (BotEntity b : squad) {
                    if (b.isPassenger()) continue;
                    Entity best = null;
                    int seat = -1;
                    double bd = Double.MAX_VALUE;
                    for (Entity e : p.level().getEntities(b, b.getBoundingBox().inflate(24), e -> e instanceof BotRideable)) {
                        BotRideable v = (BotRideable) e;
                        if (v.getBotTeam() != b.getBotTeam() || !v.fitForBots()) continue;
                        int s = v.seatForBot(b);
                        double d = e.distanceToSqr(b);
                        if (s >= 0 && d < bd) {
                            bd = d;
                            best = e;
                            seat = s;
                        }
                    }
                    if (best != null) {
                        ((BotRideable) best).board(b, seat);
                        in++;
                    }
                }
                say(p, "gunsmod.squad.enter", in);
            }
            case C_EXIT -> {
                int out = 0;
                for (int i = 0; i < n; i++) {
                    BotEntity b = squad.get(i);
                    if (b.isPassenger()) {
                        b.stopRiding();
                        b.vehicleCooldown = 400;
                        out++;
                    }
                    b.giveOrder(p.getUUID(), Order.FOLLOW, p.position(), -1, i, n);
                }
                say(p, "gunsmod.squad.exit", out);
            }
            case C_PROTECT -> {
                LivingEntity e = lookEntity(p, 64);
                if (e == null || BotTeam.of(e) != BotTeam.of(p)) e = p;
                for (int i = 0; i < n; i++) squad.get(i).giveOrder(p.getUUID(), Order.PROTECT, e.position(), e.getId(), i, n);
                say(p, "gunsmod.squad.protect", e.getDisplayName());
            }
            case C_FOCUS -> {
                LivingEntity e = lookEntity(p, 200);
                if (e == null || BotTeam.of(e) == BotTeam.of(p)) {
                    say(p, "gunsmod.squad.no_target");
                    return;
                }
                for (BotEntity b : squad) {
                    b.focusTarget = e.getId();
                    b.holdFire = false;
                    b.lastKnownEnemyPos = e.position();
                    if (b.canSeeTarget(e)) b.setTarget(e);
                }
                say(p, "gunsmod.squad.focus", e.getDisplayName());
            }
            case C_HOLD_FIRE -> {
                boolean hold = !squad.get(0).holdFire;
                for (BotEntity b : squad) b.holdFire = hold;
                say(p, hold ? "gunsmod.squad.hold_fire" : "gunsmod.squad.fire_at_will");
            }
            case C_DISMISS -> {
                for (BotEntity b : squad) b.giveOrder(null, Order.NONE, null, -1, 0, 1);
                say(p, "gunsmod.squad.dismiss", n);
            }
            case C_FORMATION -> {
                Formation f = Formation.values()[Math.floorMod(formation, Formation.values().length)];
                for (BotEntity b : squad) b.formation = f;
                say(p, "gunsmod.squad.formation." + f.name().toLowerCase());
            }
            default -> { }
        }
        status(p);
    }

    /** the squad strip on the leader's HUD */
    public static void status(ServerPlayer p) {
        List<BotEntity> squad = members(p);
        float hp = 0;
        for (BotEntity b : squad) hp += b.getHealth() / b.getMaxHealth();
        BotEntity first = squad.isEmpty() ? null : squad.get(0);
        PacketDistributor.sendToPlayer(p, new Net.SquadStatus(squad.size(), squad.isEmpty() ? 0f : hp / squad.size(),
                first == null ? 0 : first.order.ordinal(), first == null ? 0 : first.formation.ordinal(), first != null && first.holdFire));
    }

    /** once a second: every leader gets the state of his squad */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerLevel l = p.serverLevel();
            if (!l.getEntitiesOfClass(BotEntity.class, p.getBoundingBox().inflate(256, 128, 256), b -> p.getUUID().equals(b.squadLeader)).isEmpty()
                    || p.getPersistentData().getBoolean("gunsmod_had_squad")) {
                status(p);
                p.getPersistentData().putBoolean("gunsmod_had_squad", !members(p).isEmpty());
            }
        }
    }

    /** helper for the goal: the squad mates of a soldier in order (for the slots) */
    public static List<BotEntity> mates(BotEntity b) {
        if (b.squadLeader == null) return new ArrayList<>(List.of(b));
        List<BotEntity> l = b.level().getEntitiesOfClass(BotEntity.class, b.getBoundingBox().inflate(160, 64, 160),
                o -> o.isAlive() && b.squadLeader.equals(o.squadLeader));
        l.sort(Comparator.comparing(Entity::getUUID));
        return l;
    }
}
