package com.gunsmod.gun;

import com.gunsmod.Config;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.net.Net;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Bullets with a flight: each one leaves the muzzle at its gun's velocity, slows a little in the air and falls under
 * gravity, tick by tick. Its path through the (still) world is worked out when it is fired: soft or thin blocks it can
 * go through (glass, leaves, wood, a heavy round through brick) cost it penetration and damage, a shallow hit on stone
 * or metal can make it glance off. Living things are checked along the stretch it flies each tick, so a target that
 * moves can be missed and a long shot has to be led. Near targets are hit in the tick it is fired.
 */
public final class Ballistics {
    private Ballistics() {}

    /** gravity, blocks per tick squared (a little more than real so the drop shows at game ranges), and air drag */
    public static final double GRAVITY = 0.032, DRAG = 0.992;
    private static final int MAX_TICKS = 40;

    /** one stretch of the path, flown during tick {@code tick}; what happens at its end */
    private record Seg(Vec3 from, Vec3 to, int tick, int event, @Nullable BlockPos block) {}

    static final int FLY = 0, STOP = 1, PENETRATE = 2, RICOCHET = 3;

    private static final class Round {
        final Level level;
        final LivingEntity shooter;
        final GunType type;
        final float damage;
        final List<Seg> path;
        final double[] travelled;
        float mul = 1f;
        int index, tick;

        Round(Level level, LivingEntity shooter, GunType type, float damage, List<Seg> path) {
            this.level = level;
            this.shooter = shooter;
            this.type = type;
            this.damage = damage;
            this.path = path;
            this.travelled = new double[path.size() + 1];
            for (int i = 0; i < path.size(); i++) travelled[i + 1] = travelled[i] + path.get(i).from.distanceTo(path.get(i).to);
        }
    }

    private static final List<Round> ACTIVE = new ArrayList<>();

    /** what a fired round will first run into, for the muzzle's shot effect: the point and 0 nothing / 1 block / 2 entity */
    public record First(Vec3 end, byte kind) {}

    // ------------------------------------------------------------------------------------ the path
    private static float cost(BlockState st) {
        SoundType s = st.getSoundType();
        if (s == SoundType.GLASS) return 0.3f;
        if (s == SoundType.GRASS || s == SoundType.AZALEA_LEAVES || s == SoundType.CHERRY_LEAVES || s == SoundType.CROP || s == SoundType.WET_GRASS) return 0.3f;
        if (s == SoundType.WOOL) return 0.6f;
        if (s == SoundType.WOOD || s == SoundType.BAMBOO_WOOD || s == SoundType.CHERRY_WOOD || s == SoundType.NETHER_WOOD || s == SoundType.LADDER) return 1.6f;
        if (s == SoundType.SAND || s == SoundType.GRAVEL || s == SoundType.ROOTED_DIRT || s == SoundType.MUD) return 3.5f;
        if (s == SoundType.METAL || s == SoundType.ANVIL || s == SoundType.NETHERITE_BLOCK || s == SoundType.CHAIN || s == SoundType.COPPER) return 14f;
        return Math.max(1.0f, Math.min(16f, st.getBlock().getExplosionResistance() * 1.1f));
    }

    private static boolean hard(BlockState st) {
        SoundType s = st.getSoundType();
        return s == SoundType.STONE || s == SoundType.DEEPSLATE || s == SoundType.METAL || s == SoundType.ANVIL || s == SoundType.DEEPSLATE_BRICKS
                || s == SoundType.POLISHED_DEEPSLATE || s == SoundType.TUFF || s == SoundType.BASALT || s == SoundType.COPPER || s == SoundType.NETHERITE_BLOCK;
    }

    /** where a ray entering the block at p along d leaves it (full blocks: the far face; anything thinner counts a quarter block) */
    private static Vec3 exit(Level level, BlockPos bp, BlockState st, Vec3 p, Vec3 d) {
        if (!st.isCollisionShapeFullBlock(level, bp)) return p.add(d.scale(0.3));
        double tmax = 2.0;
        double[] lo = {bp.getX(), bp.getY(), bp.getZ()}, hi = {bp.getX() + 1, bp.getY() + 1, bp.getZ() + 1};
        double[] o = {p.x, p.y, p.z}, v = {d.x, d.y, d.z};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(v[i]) < 1e-9) continue;
            double t = ((v[i] > 0 ? hi[i] : lo[i]) - o[i]) / v[i];
            if (t > 0) tmax = Math.min(tmax, t);
        }
        return p.add(d.scale(tmax + 0.01));
    }

    private static List<Seg> path(Level level, Entity shooter, Vec3 eye, Vec3 dir, GunType t) {
        List<Seg> segs = new ArrayList<>();
        double v = t.muzzleVelocity();
        Vec3 vel = dir.normalize().scale(v);
        Vec3 pos = eye;
        double budget = t.range * 1.6;
        double flown = 0;
        float power = t.penetration();
        int ricochets = 0;
        double g = GRAVITY * Config.bulletDrop();
        for (int tick = 0; tick < MAX_TICKS && flown < budget; tick++) {
            Vec3 step = vel;
            Vec3 next = pos.add(step);
            // up to a few block events inside one tick's stretch
            for (int guard = 0; guard < 6; guard++) {
                BlockHitResult bh = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
                if (bh.getType() == HitResult.Type.MISS) {
                    segs.add(new Seg(pos, next, tick, FLY, null));
                    flown += pos.distanceTo(next);
                    pos = next;
                    break;
                }
                Vec3 p = bh.getLocation();
                BlockPos bp = bh.getBlockPos();
                BlockState st = level.getBlockState(bp);
                Vec3 d = next.subtract(pos).normalize();
                flown += pos.distanceTo(p);
                Vec3 out = exit(level, bp, st, p, d);
                float c = cost(st) * (float) Math.max(0.25, out.distanceTo(p));
                Vec3 n = new Vec3(bh.getDirection().getStepX(), bh.getDirection().getStepY(), bh.getDirection().getStepZ());
                double cosIn = -d.dot(n);
                if (power >= c && !(st.getBlock() instanceof com.gunsmod.block.ExplosiveBarrelBlock)) {
                    power -= c;
                    segs.add(new Seg(pos, p, tick, PENETRATE, bp));
                    double rest = Math.max(0.0, next.distanceTo(pos) - pos.distanceTo(out));
                    pos = out;
                    vel = vel.scale(0.85);
                    next = pos.add(d.scale(rest));
                    continue;
                }
                if (hard(st) && ricochets == 0 && cosIn < 0.3 && level.random.nextFloat() < 0.45f) {
                    ricochets++;
                    segs.add(new Seg(pos, p, tick, RICOCHET, bp));
                    Vec3 r = d.subtract(n.scale(2 * d.dot(n)));
                    vel = r.scale(vel.length() * 0.6);
                    pos = p.add(n.scale(0.02));
                    next = pos.add(r.scale(Math.max(0.0, next.distanceTo(p)) * 0.6));
                    power *= 0.5f;
                    continue;
                }
                segs.add(new Seg(pos, p, tick, STOP, bp));
                return segs;
            }
            vel = vel.scale(DRAG).add(0, -g, 0);
        }
        return segs;
    }

    // ------------------------------------------------------------------------------------ hits
    @Nullable
    private static GunLogic.Trace entityOn(Level level, LivingEntity shooter, Vec3 from, Vec3 to) {
        Entity best = null;
        Vec3 bestHit = null;
        double bestD = Double.MAX_VALUE;
        AABB box = new AABB(from, to).inflate(1.0);
        for (Entity e : level.getEntities(shooter, box, e -> GunLogic.canHit(shooter, e))) {
            var hit = e.getBoundingBox().inflate(e instanceof JeepEntity || e instanceof VehicleEntity ? 0.0 : 0.12).clip(from, to);
            if (hit.isPresent()) {
                double d = from.distanceToSqr(hit.get());
                if (d < bestD) {
                    bestD = d;
                    best = e;
                    bestHit = hit.get();
                }
            }
        }
        if (best == null) return null;
        boolean head = best instanceof LivingEntity le && !(best instanceof JeepEntity) && !(best instanceof VehicleEntity) && le.getBbHeight() > 1.2f
                && bestHit.y > le.getEyeY() - 0.28;
        return new GunLogic.Trace(bestHit, (byte) 2, best, head, null);
    }

    /** fly a round through this tick's stretches; true when it is spent */
    private static boolean advance(Round r) {
        while (r.index < r.path.size() && r.path.get(r.index).tick <= r.tick) {
            Seg s = r.path.get(r.index);
            GunLogic.Trace hit = entityOn(r.level, r.shooter, s.from, s.to);
            if (hit != null) {
                double dist = r.travelled[r.index] + s.from.distanceTo(hit.end());
                float dmg = r.damage * r.mul * GunLogic.falloffAt(r.type, dist);
                if (hit.headshot()) dmg *= r.type.headshotMultiplier();
                VehicleEntity.hitPoint = hit.end();
                try {
                    GunLogic.damage(r.shooter, hit.entity(), dmg, hit.headshot(), r.type);
                } finally {
                    VehicleEntity.hitPoint = null;
                }
                return true;
            }
            r.index++;
            switch (s.event) {
                case STOP -> {
                    GunLogic.impactBlock(r.level, r.shooter, s.block);
                    return true;
                }
                case PENETRATE -> {
                    GunLogic.impactBlock(r.level, r.shooter, s.block);
                    r.mul *= 0.7f;
                }
                case RICOCHET -> r.mul *= 0.5f;
                default -> { }
            }
        }
        r.tick++;
        return r.index >= r.path.size();
    }

    // ------------------------------------------------------------------------------------ firing
    /**
     * fire one round; returns what it will first meet (for the shot effect). The legs after a penetration or a
     * ricochet are sent to the players around as their own tracer and impact.
     */
    public static First fire(Level level, LivingEntity shooter, Vec3 eye, Vec3 dir, GunType t, float damage) {
        List<Seg> segs = path(level, shooter, eye, dir, t);
        // the visual legs: muzzle to the first event (the shot effect draws it), then one leg after each penetration / ricochet
        List<Vec3> legs = new ArrayList<>();
        List<Byte> kinds = new ArrayList<>();
        Vec3 legStart = eye;
        First first = null;
        boolean ended = false;
        for (Seg s : segs) {
            GunLogic.Trace hit = entityOn(level, shooter, s.from, s.to);
            Vec3 end;
            byte kind;
            if (hit != null) {
                end = hit.end();
                kind = 2;
            } else if (s.event != FLY) {
                end = s.to;
                kind = 1;
            } else {
                continue;
            }
            if (first == null) {
                first = new First(end, kind);
            } else {
                legs.add(legStart);
                legs.add(end);
                kinds.add(kind);
            }
            if (hit != null || s.event == STOP) {
                ended = true;
                break;
            }
            legStart = end;
        }
        Vec3 last = segs.isEmpty() ? eye.add(dir.scale(t.range)) : segs.get(segs.size() - 1).to;
        if (first == null) {
            first = new First(last, (byte) 0);
        } else if (!ended) {
            legs.add(legStart);
            legs.add(last);
            kinds.add((byte) 0);
        }
        if (!legs.isEmpty() && level instanceof ServerLevel sl) {
            byte[] k = new byte[kinds.size()];
            for (int i = 0; i < k.length; i++) k[i] = kinds.get(i);
            PacketDistributor.sendToPlayersNear(sl, null, eye.x, eye.y, eye.z, GunLogic.SHOT_RANGE, new Net.BulletPath(legs, k));
        }
        Round r = new Round(level, shooter, t, damage, segs);
        if (!advance(r)) ACTIVE.add(r);
        return first;
    }

    /** every server tick */
    public static void tick() {
        if (ACTIVE.isEmpty()) return;
        Iterator<Round> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Round r = it.next();
            if (r.level.isClientSide || advance(r)) it.remove();
        }
        if (ACTIVE.size() > 4000) ACTIVE.subList(0, ACTIVE.size() - 4000).clear();
    }
}
