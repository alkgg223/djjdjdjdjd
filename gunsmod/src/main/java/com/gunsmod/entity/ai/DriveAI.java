package com.gunsmod.entity.ai;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Patrol waypoints for vehicles driven or flown by soldiers. */
public final class DriveAI {
    private DriveAI() {}

    /**
     * A point on dry, loaded ground {@code min..max} blocks away, mostly ahead of the vehicle so the patrol keeps
     * flowing, turning back towards home near the edge of the leash. Null when nothing suitable was found.
     */
    @Nullable
    public static Vec3 groundWaypoint(Entity v, Vec3 home, double leash, double min, double max, double maxClimb) {
        Level level = v.level();
        RandomSource r = v.getRandom();
        double fromHome = Math.sqrt(v.position().distanceToSqr(home.x, v.getY(), home.z));
        for (int i = 0; i < 16; i++) {
            float dir;
            if (fromHome > leash * 0.75) {
                Vec3 back = home.subtract(v.position());
                dir = (float) (Mth.atan2(back.z, back.x) * Mth.RAD_TO_DEG) - 90f + (r.nextFloat() - 0.5f) * 90f;
            } else if (i > 8 || r.nextInt(5) == 0) {
                dir = r.nextFloat() * 360f;
            } else {
                dir = v.getYRot() + (r.nextFloat() - 0.5f) * 140f;
            }
            double d = min + r.nextDouble() * (max - min);
            double a = dir * Mth.DEG_TO_RAD;
            double x = v.getX() - Math.sin(a) * d, z = v.getZ() + Math.cos(a) * d;
            if (Math.sqrt((x - home.x) * (x - home.x) + (z - home.z) * (z - home.z)) > leash) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            if (Math.abs(y - v.getY()) > maxClimb) continue;
            BlockPos below = BlockPos.containing(x, y - 1, z);
            if (!level.getFluidState(below).isEmpty() || !level.getFluidState(below.above()).isEmpty()) continue;
            return new Vec3(x, y, z);
        }
        return null;
    }

    /** highest ground (or water surface) along a line, sampled every few blocks; used to keep aircraft clear of hills */
    public static int maxGround(Level level, Vec3 from, Vec3 dir, double length) {
        int best = level.getMinBuildHeight();
        for (double t = 0; t <= length; t += 4) {
            Vec3 p = from.add(dir.scale(t));
            best = Math.max(best, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z)));
        }
        return best;
    }
}
