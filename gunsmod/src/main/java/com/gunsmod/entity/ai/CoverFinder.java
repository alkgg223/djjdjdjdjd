package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Finds positions that block the line of sight from a threat (cover) and positions next to cover that see it (peek). */
public final class CoverFinder {
    private CoverFinder() {}

    public static boolean standable(Level level, BlockPos p) {
        return !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()
                && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getFluidState(p).isEmpty();
    }

    @Nullable
    public static BlockPos ground(Level level, BlockPos p) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos q = p.above(dy);
            if (standable(level, q)) return q;
        }
        return null;
    }

    public static boolean blocked(Level level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()))
                .getType() != HitResult.Type.MISS;
    }

    /** cover quality: 2 = hides a standing soldier, 1 = hides a crouching soldier, 0 = exposed */
    public static int coverQuality(Level level, BlockPos p, Vec3 threatEye) {
        Vec3 c = Vec3.atBottomCenterOf(p);
        boolean low = blocked(level, threatEye, c.add(0, 0.7, 0));
        boolean mid = blocked(level, threatEye, c.add(0, 1.2, 0));
        boolean high = blocked(level, threatEye, c.add(0, 1.65, 0));
        if (low && mid && high) return 2;
        if (low && mid) return 1;
        return 0;
    }

    @Nullable
    public static Vec3 findCover(BotEntity bot, Vec3 threatEye, int radius) {
        Level level = bot.level();
        RandomSource r = bot.getRandom();
        BlockPos origin = bot.blockPosition();
        Vec3 away = bot.position().subtract(threatEye).multiply(1, 0, 1).normalize();
        double threatDist = bot.position().distanceTo(threatEye);
        BlockPos best = null;
        double bestScore = -1e9;
        for (int i = 0; i < 44; i++) {
            double ang = r.nextDouble() * Mth.TWO_PI;
            double dist = 1.5 + r.nextDouble() * radius;
            BlockPos cand = ground(level, origin.offset(Mth.floor(Math.cos(ang) * dist), 0, Mth.floor(Math.sin(ang) * dist)));
            if (cand == null) continue;
            Vec3 c = Vec3.atBottomCenterOf(cand);
            double dThreat = c.distanceTo(threatEye);
            if (dThreat < 4.5) continue;
            int q = coverQuality(level, cand, threatEye);
            if (q == 0) continue;
            double score = q * 6 - c.distanceTo(bot.position()) * 0.9;
            // prefer cover that does not run towards the enemy
            Vec3 dir = c.subtract(bot.position()).multiply(1, 0, 1);
            if (dir.lengthSqr() > 0.01) score += dir.normalize().dot(away) * 2.0;
            if (dThreat < threatDist - 6) score -= 4;
            // a wall right next to the spot is the best cover
            if (adjacentSolidTowards(level, cand, threatEye)) score += 3;
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        if (best == null) return null;
        Path path = bot.getNavigation().createPath(best, 0);
        if (path == null || !path.canReach()) return null;
        return Vec3.atBottomCenterOf(best);
    }

    private static boolean adjacentSolidTowards(Level level, BlockPos p, Vec3 threat) {
        Vec3 d = threat.subtract(Vec3.atCenterOf(p)).multiply(1, 0, 1).normalize();
        BlockPos n = BlockPos.containing(p.getX() + 0.5 + d.x, p.getY() + 0.5, p.getZ() + 0.5 + d.z);
        return !level.getBlockState(n).getCollisionShape(level, n).isEmpty();
    }

    /** a nearby position (within 3 blocks of the cover spot) with a clear shot at the threat */
    @Nullable
    public static Vec3 findPeek(BotEntity bot, Vec3 coverPos, Vec3 threatEye) {
        Level level = bot.level();
        BlockPos base = BlockPos.containing(coverPos);
        BlockPos best = null;
        double bestD = 1e9;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos cand = ground(level, base.offset(dx, 0, dz));
                if (cand == null) continue;
                Vec3 eye = Vec3.atBottomCenterOf(cand).add(0, 1.55, 0);
                if (blocked(level, eye, threatEye)) continue;
                double d = Vec3.atBottomCenterOf(cand).distanceToSqr(coverPos);
                if (d < bestD) {
                    bestD = d;
                    best = cand;
                }
            }
        }
        return best == null ? null : Vec3.atBottomCenterOf(best);
    }

    /** a point at the side of the target, used to flank */
    @Nullable
    public static Vec3 findFlank(BotEntity bot, Vec3 target, double radius) {
        Level level = bot.level();
        Vec3 rel = bot.position().subtract(target).multiply(1, 0, 1);
        if (rel.lengthSqr() < 0.01) rel = new Vec3(1, 0, 0);
        double base = Math.atan2(rel.z, rel.x);
        for (int i = 0; i < 6; i++) {
            double side = (bot.getRandom().nextBoolean() ? 1 : -1) * (Math.PI / 2.2 + bot.getRandom().nextDouble() * 0.6);
            double a = base + side;
            BlockPos cand = ground(level, BlockPos.containing(target.x + Math.cos(a) * radius, target.y, target.z + Math.sin(a) * radius));
            if (cand == null) continue;
            Path p = bot.getNavigation().createPath(cand, 1);
            if (p != null && p.canReach()) return Vec3.atBottomCenterOf(cand);
        }
        return null;
    }
}
