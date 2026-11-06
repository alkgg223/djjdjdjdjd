package com.gunsmod.entity.ai;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Column driving for a convoy laid out with the convoy planner: every vehicle keeps its place behind the one ahead of
 * it, at its own distance, waiting when it closes up. The head of the column drives its route (or patrols); when the
 * vehicle ahead is lost the next one takes over and goes on along the route.
 */
public final class Convoy {
    private Convoy() {}

    /** the vehicle ahead in the column (or the one a helicopter escorts), while it is still about */
    @Nullable
    public static Entity ahead(Entity me, @Nullable UUID id) {
        if (id == null || !(me.level() instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(id);
        return e != null && e.isAlive() && e.distanceToSqr(me) < 220 * 220 ? e : null;
    }

    public static Vec3 forward(Entity e) {
        float yr = e.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yr), 0, Mth.cos(yr));
    }

    /** the place in the column behind the vehicle ahead; null when it is already there (wait) */
    @Nullable
    public static Vec3 slot(Entity me, Entity ahead, double gap) {
        Vec3 spot = ahead.position().subtract(forward(ahead).scale(gap));
        double dx = spot.x - me.getX(), dz = spot.z - me.getZ();
        // closed up: wait unless the slot is behind us (the column turned)
        if (dx * dx + dz * dz < 9) return null;
        double toAhead = ahead.distanceTo(me);
        if (toAhead < gap * 0.75) return null;
        return spot;
    }

    /** a route follower that drove in the column joins the route at the point it is closest to */
    public static void rejoin(Entity me, @Nullable Waypoints w) {
        if (w == null || w.isEmpty()) return;
        int best = w.nearest(me.blockPosition(), 1e6);
        if (best < 0) return;
        w.at = best;
        // already past that point (it is behind): carry on to the next one
        int[] n = w.next.get(best);
        if (n.length > 0) {
            Vec3 p = Vec3.atBottomCenterOf(w.points.get(best)), q = Vec3.atBottomCenterOf(w.points.get(n[0]));
            if (me.position().distanceToSqr(q) < p.distanceToSqr(q)) w.at = n[0];
        }
    }
}
