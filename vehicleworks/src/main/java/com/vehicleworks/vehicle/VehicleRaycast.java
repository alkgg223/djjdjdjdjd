package com.vehicleworks.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;

/** Precise ray casts against vehicle blocks (voxel DDA in each vehicle's local frame). */
public final class VehicleRaycast {
    private VehicleRaycast() {}

    public record Hit(VehicleEntity vehicle, long block, Direction face, Vec3 location, Vector3d localHit, double distance) {}

    @Nullable
    public static Hit raycast(Level level, Vec3 from, Vec3 to, @Nullable VehicleEntity exclude) {
        AABB seg = new AABB(from, to).inflate(0.5);
        Hit best = null;
        for (VehicleEntity v : VehicleRegistry.near(level, seg)) {
            if (v == exclude) continue;
            Hit h = raycast(v, from, to);
            if (h != null && (best == null || h.distance < best.distance)) best = h;
        }
        return best;
    }

    @Nullable
    public static Hit raycast(VehicleEntity v, Vec3 from, Vec3 to) {
        Vector3d a = v.worldToLocal(from.x, from.y, from.z, new Vector3d());
        Vector3d b = v.worldToLocal(to.x, to.y, to.z, new Vector3d());
        Vector3d d = new Vector3d(b).sub(a);
        double len = d.length();
        if (len < 1e-6) return null;
        d.div(len);
        VehicleStructure s = v.structure;
        // clip to structure bounds
        double[] t = clip(a, d, s.minX(), s.minY(), s.minZ(), s.maxX() + 1, s.maxY() + 1, s.maxZ() + 1);
        if (t == null || t[0] > len) return null;
        double t0 = Math.max(0, t[0]) + 1e-6;
        double ox = a.x + d.x * t0, oy = a.y + d.y * t0, oz = a.z + d.z * t0;
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int sx = d.x > 0 ? 1 : -1, sy = d.y > 0 ? 1 : -1, sz = d.z > 0 ? 1 : -1;
        double tdx = d.x != 0 ? Math.abs(1 / d.x) : Double.MAX_VALUE;
        double tdy = d.y != 0 ? Math.abs(1 / d.y) : Double.MAX_VALUE;
        double tdz = d.z != 0 ? Math.abs(1 / d.z) : Double.MAX_VALUE;
        double tmx = d.x != 0 ? ((d.x > 0 ? (x + 1 - ox) : (ox - x)) * tdx) + t0 : Double.MAX_VALUE;
        double tmy = d.y != 0 ? ((d.y > 0 ? (y + 1 - oy) : (oy - y)) * tdy) + t0 : Double.MAX_VALUE;
        double tmz = d.z != 0 ? ((d.z > 0 ? (z + 1 - oz) : (oz - z)) * tdz) + t0 : Double.MAX_VALUE;
        double tc = t0;
        Direction face = null;
        // initial face from entry axis
        if (t[0] > 0) face = t[1] == 0 ? (sx > 0 ? Direction.WEST : Direction.EAST) : t[1] == 1 ? (sy > 0 ? Direction.DOWN : Direction.UP) : (sz > 0 ? Direction.NORTH : Direction.SOUTH);
        int guard = 0;
        while (tc <= Math.min(len, t[2]) && guard++ < 1024) {
            long key = BlockPos.asLong(x, y, z);
            if (s.contains(key)) {
                var st = s.get(key);
                var shape = st.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                double hitT = tc;
                Direction hitFace = face == null ? Direction.UP : face;
                boolean hit = false;
                if (shape.isEmpty()) {
                    hit = false;
                } else if (shape == net.minecraft.world.phys.shapes.Shapes.block()) {
                    hit = true;
                } else {
                    Vec3 lf = new Vec3(a.x + d.x * tc - x, a.y + d.y * tc - y, a.z + d.z * tc - z);
                    Vec3 lt = lf.add(d.x * 1.8, d.y * 1.8, d.z * 1.8);
                    var r = shape.clip(lf, lt, BlockPos.ZERO);
                    if (r != null) {
                        hit = true;
                        hitT = tc + r.getLocation().distanceTo(lf);
                        hitFace = r.getDirection();
                    }
                }
                if (hit && hitT <= len) {
                    Vector3d lh = new Vector3d(a).fma(hitT, d);
                    Vector3d wh = v.localToWorld(lh.x, lh.y, lh.z, new Vector3d());
                    return new Hit(v, key, hitFace, new Vec3(wh.x, wh.y, wh.z), lh, hitT);
                }
            }
            if (tmx < tmy) {
                if (tmx < tmz) { x += sx; tc = tmx; tmx += tdx; face = sx > 0 ? Direction.WEST : Direction.EAST; }
                else { z += sz; tc = tmz; tmz += tdz; face = sz > 0 ? Direction.NORTH : Direction.SOUTH; }
            } else {
                if (tmy < tmz) { y += sy; tc = tmy; tmy += tdy; face = sy > 0 ? Direction.DOWN : Direction.UP; }
                else { z += sz; tc = tmz; tmz += tdz; face = sz > 0 ? Direction.NORTH : Direction.SOUTH; }
            }
        }
        return null;
    }

    /** Slab clip; returns {tEnter, entryAxis, tExit} or null. */
    private static double[] clip(Vector3d o, Vector3d d, double x0, double y0, double z0, double x1, double y1, double z1) {
        double tmin = -Double.MAX_VALUE, tmax = Double.MAX_VALUE;
        int axis = 0;
        double[] oo = {o.x, o.y, o.z}, dd = {d.x, d.y, d.z}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dd[i]) < 1e-12) {
                if (oo[i] < lo[i] || oo[i] > hi[i]) return null;
                continue;
            }
            double ta = (lo[i] - oo[i]) / dd[i], tb = (hi[i] - oo[i]) / dd[i];
            if (ta > tb) { double tt = ta; ta = tb; tb = tt; }
            if (ta > tmin) { tmin = ta; axis = i; }
            tmax = Math.min(tmax, tb);
            if (tmin > tmax) return null;
        }
        if (tmax < 0) return null;
        return new double[]{tmin, axis, tmax};
    }
}
