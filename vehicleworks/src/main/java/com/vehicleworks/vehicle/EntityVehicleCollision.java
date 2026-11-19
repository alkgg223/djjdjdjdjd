package com.vehicleworks.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lets players, mobs and items stand, walk and build on moving vehicles.
 * <ul>
 *   <li>Entities resting on a vehicle are anchored in its local frame and carried along with
 *       its translation and rotation (no sliding).</li>
 *   <li>Collision with vehicle blocks is resolved in the vehicle's local frame, where blocks are
 *       axis aligned, using vanilla's axis-separated sweep plus step-up.</li>
 * </ul>
 * Players are simulated on their own client (vanilla is client-authoritative for player
 * movement); other entities on the server.
 */
public final class EntityVehicleCollision {
    private EntityVehicleCollision() {}

    private static final class Anchor {
        VehicleEntity vehicle;
        final Vector3d local = new Vector3d();
        double yaw;
        int airTicks;
    }

    private static final class MoveInfo {
        boolean processed;
        boolean vehicleGround;
        /** a vehicle really blocked the entity's horizontal motion this move */
        boolean blockedH;
        VehicleEntity groundVehicle;
        double carryY;
    }

    public static int DEBUG_PUSHES;
    public static String DEBUG = "", DEBUG2 = "";
    private static final Map<Entity, Anchor> ANCHORS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<MoveInfo> INFO = ThreadLocal.withInitial(MoveInfo::new);
    private static final Map<BlockState, VoxelShape> SHAPES = new IdentityHashMap<>();

    public static boolean isSimulatedHere(Entity e) {
        if (e instanceof VehicleEntity || e.isPassenger() || e.noPhysics || e.isSpectator()) return false;
        if (e.level().isClientSide) return e instanceof Player p && p.isLocalPlayer();
        return !(e instanceof Player);
    }

    /** Is the entity currently standing on / carried by a vehicle? */
    public static VehicleEntity anchoredVehicle(Entity e) {
        Anchor a = ANCHORS.get(e);
        return a != null && a.vehicle != null && !a.vehicle.isRemoved() ? a.vehicle : null;
    }

    private static double vehicleYaw(VehicleEntity v) {
        Vector3d a = v.localDirToWorld(0, 0, 1, new Vector3d());
        return Math.toDegrees(Math.atan2(-a.x, a.z));
    }

    /**
     * Carry an entity standing on (or jumping above) a vehicle along with the vehicle's motion
     * since the last tick. Done as a teleport before the move, so vanilla never sees the carry as
     * a collision (which would zero the entity's own velocity every tick: slow "feather" walking
     * and jumping). Also called after the client tick, when the vehicle may have moved later
     * than the player in the same tick (keeps the player from lagging one tick behind / inside).
     */
    public static void carry(Entity e) {
        if (!isSimulatedHere(e)) return;
        Anchor a = ANCHORS.get(e);
        if (a == null || a.vehicle == null) a = autoAnchor(e);
        if (a == null || a.vehicle == null || a.vehicle.isRemoved() || a.vehicle.level() != e.level()) return;
        VehicleEntity v = a.vehicle;
        Vector3d target = v.localToWorld(a.local.x, a.local.y, a.local.z, new Vector3d());
        double d2 = target.distanceSquared(e.getX(), e.getY(), e.getZ());
        // no distance limit: at high speed a lag spike can move the vehicle tens of blocks between
        // two ticks, and dropping the anchor then left the player behind the aircraft
        if (d2 > 400 * 400) { ANCHORS.remove(e); return; }
        double yaw = vehicleYaw(v);
        float dyaw = (float) Mth.wrapDegrees(yaw - a.yaw);
        a.yaw = yaw;
        if (Math.abs(dyaw) > 1e-4) {
            e.setYRot(e.getYRot() + dyaw);
            if (e instanceof LivingEntity le) {
                le.yHeadRot += dyaw;
                le.yBodyRot += dyaw;
            }
        }
        if (d2 > 1e-10) e.setPos(target.x, target.y, target.z);
    }

    /** Called at the end of Entity.collide with vanilla's result. Returns the final movement. */
    public static Vec3 collide(Entity e, Vec3 movement, Vec3 resolved) {
        MoveInfo info = INFO.get();
        info.processed = false;
        info.vehicleGround = false;
        info.groundVehicle = null;
        info.carryY = 0;
        info.blockedH = false;
        if (!isSimulatedHere(e)) return resolved;
        AABB box = e.getBoundingBox();
        Anchor anchor = ANCHORS.get(e);
        List<VehicleEntity> near = VehicleRegistry.near(e.level(), box.expandTowards(resolved).inflate(1.5));
        if (near.isEmpty() && (anchor == null || anchor.vehicle == null)) return resolved;
        info.processed = true;
        Vec3 total = resolved;
        for (VehicleEntity v : near) {
            if (v.structure.isEmpty()) continue;
            Vector3d start = v.worldToLocal(e.getX(), e.getY(), e.getZ(), new Vector3d());
            total = collideWith(e, v, start, total, anchor != null && anchor.vehicle == v, info);
        }
        // On a tilted deck the motion follows the deck plane, which differs slightly from the
        // requested world motion. Vanilla would read that as hitting a wall and zero the walking
        // velocity every tick (slow walking), so keep the requested horizontal motion unless a
        // vehicle block really stopped it.
        if (!info.blockedH) total = new Vec3(resolved.x, total.y, resolved.z);
        // the frame round trip leaves ~1e-15 noise; vanilla compares the vertical movement exactly
        // and would treat the noise as a collision (zeroing the jump / fall velocity)
        double tx = Math.abs(total.x - resolved.x) < 1e-7 ? resolved.x : total.x;
        double ty = Math.abs(total.y - resolved.y) < 1e-7 ? resolved.y : total.y;
        double tz = Math.abs(total.z - resolved.z) < 1e-7 ? resolved.z : total.z;
        if (tx != total.x || ty != total.y || tz != total.z) total = new Vec3(tx, ty, tz);
        if (e instanceof Player) DEBUG += String.format(" | move=(%.5f,%.5f,%.5f) vanilla=(%.5f,%.5f,%.5f) out=(%.5f,%.5f,%.5f)", movement.x, movement.y, movement.z, resolved.x, resolved.y, resolved.z, total.x, total.y, total.z);
        return total;
    }

    /** Called at the end of Entity.move: ground state and anchoring. */
    public static void afterMove(Entity e) {
        MoveInfo info = INFO.get();
        if (!info.processed) return;
        info.processed = false;
        if (info.vehicleGround) {
            e.setOnGround(true);
            e.resetFallDistance();
        } else if (info.carryY < 0) {
            e.fallDistance = Math.max(0, e.fallDistance + (float) info.carryY);
        }
        Anchor a = ANCHORS.get(e);
        // landed on the world (fell off the vehicle): stop carrying immediately
        if (!info.vehicleGround && e.onGround()) {
            ANCHORS.remove(e);
            return;
        }
        VehicleEntity v = info.vehicleGround ? info.groundVehicle : (a != null ? a.vehicle : null);
        if (v == null || v.isRemoved()) {
            ANCHORS.remove(e);
            return;
        }
        if (a == null) {
            a = new Anchor();
            ANCHORS.put(e, a);
            a.yaw = vehicleYaw(v);
        }
        if (a.vehicle != v) a.yaw = vehicleYaw(v);
        a.vehicle = v;
        v.worldToLocal(e.getX(), e.getY(), e.getZ(), a.local);
        if (info.vehicleGround) a.airTicks = 0;
        else if (++a.airTicks > 80 || !nearStructure(v, a.local)) ANCHORS.remove(e);
    }

    private static VoxelShape shape(BlockState st) {
        synchronized (SHAPES) {
            return SHAPES.computeIfAbsent(st, s -> s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        }
    }

    /**
     * Sweep the entity through the vehicle's blocks in the vehicle frame.
     *
     * @param feet  start position of the entity's feet in vehicle-local coordinates
     * @param motion desired world-space motion
     * @return world-space displacement from the entity's current position
     */
    private static Vec3 collideWith(Entity e, VehicleEntity v, Vector3d feet, Vec3 motion, boolean anchored, MoveInfo info) {
        double hw = e.getBbWidth() * 0.5, h = e.getBbHeight();
        AABB box = new AABB(feet.x - hw, feet.y, feet.z - hw, feet.x + hw, feet.y + h, feet.z + hw);
        Vector3d lm = v.worldDirToLocal(motion.x, motion.y, motion.z, new Vector3d());
        AABB sweep = box.expandTowards(lm.x, lm.y, lm.z).inflate(1.0);
        int x0 = Mth.floor(sweep.minX), y0 = Mth.floor(sweep.minY), z0 = Mth.floor(sweep.minZ);
        int x1 = Mth.floor(sweep.maxX), y1 = Mth.floor(sweep.maxY), z1 = Mth.floor(sweep.maxZ);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 20000) return motion;
        List<VoxelShape> shapes = new ArrayList<>();
        VehicleStructure s = v.structure;
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    long k = BlockPos.asLong(x, y, z);
                    if (!s.contains(k)) continue;
                    if (v.isPassable(k)) continue; // an open aircraft door / lavatory door
                    VoxelShape sh = shape(s.get(k));
                    if (!sh.isEmpty()) shapes.add(sh.move(x, y, z));
                }
        if (shapes.isEmpty()) return motion;
        // push out if the vehicle moved / rotated into the entity (never leave it stuck inside)
        double lmY0 = lm.y; // requested local vertical motion, before any push-out
        double push = depenetrateUp(box, shapes);
        boolean sidePushed = false;
        if (push > 0) DEBUG_PUSHES++;
        if (push > 0) {
            box = box.move(0, push, 0);
            feet.y += push;
            if (lm.y < 0) lm.y = 0;
            if (push < 0.75) info.vehicleGround = true;
            info.groundVehicle = v;
        } else {
            double[] side = depenetrateSide(box, shapes);
            if (side != null) {
                info.blockedH = true;
                sidePushed = true;
                box = box.move(side[0], 0, side[1]);
                feet.x += side[0];
                feet.z += side[1];
            }
        }
        double dy = Shapes.collide(Direction.Axis.Y, box, shapes, lm.y);
        AABB b = box.move(0, dy, 0);
        boolean xFirst = Math.abs(lm.x) < Math.abs(lm.z);
        double dx, dz;
        if (xFirst) {
            dx = Shapes.collide(Direction.Axis.X, b, shapes, lm.x);
            b = b.move(dx, 0, 0);
            dz = Shapes.collide(Direction.Axis.Z, b, shapes, lm.z);
            b = b.move(0, 0, dz);
        } else {
            dz = Shapes.collide(Direction.Axis.Z, b, shapes, lm.z);
            b = b.move(0, 0, dz);
            dx = Shapes.collide(Direction.Axis.X, b, shapes, lm.x);
            b = b.move(dx, 0, 0);
        }
        boolean groundedNow = lm.y < 0 && dy > lm.y + 1e-7;
        // step up onto blocks (stairs, slabs, deck edges)
        float step = e.maxUpStep();
        boolean blockedH = Math.abs(dx - lm.x) > 1e-7 || Math.abs(dz - lm.z) > 1e-7;
        if (step > 0 && blockedH && (groundedNow || e.onGround() || info.vehicleGround)) {
            AABB sb = box.move(0, step, 0);
            if (Shapes.collide(Direction.Axis.Y, box, shapes, step) >= step - 1e-6) {
                double sdx = Shapes.collide(Direction.Axis.X, sb, shapes, lm.x);
                sb = sb.move(sdx, 0, 0);
                double sdz = Shapes.collide(Direction.Axis.Z, sb, shapes, lm.z);
                sb = sb.move(0, 0, sdz);
                double down = Shapes.collide(Direction.Axis.Y, sb, shapes, -step + Math.min(0, lm.y));
                sb = sb.move(0, down, 0);
                if (sdx * sdx + sdz * sdz > dx * dx + dz * dz + 1e-6) {
                    dx = sdx;
                    dz = sdz;
                    dy = sb.minY - box.minY;
                    groundedNow = true;
                    blockedH = Math.abs(dx - lm.x) > 1e-7 || Math.abs(dz - lm.z) > 1e-7;
                }
            }
        }
        if (groundedNow) {
            info.vehicleGround = true;
            info.groundVehicle = v;
        }
        if (blockedH) info.blockedH = true;
        if (e instanceof Player) DEBUG2 = String.format("dyBlocked=%s", Math.abs(dy - lm.y) > 1e-7 || push > 0);
        if (e instanceof Player) DEBUG = String.format("lm=(%.5f,%.5f,%.5f) d=(%.5f,%.5f,%.5f) feetY=%.9f blockedH=%s shapes=%d", lm.x, lm.y, lm.z, dx, dy, dz, feet.y, blockedH, shapes.size());
        Vec3 out = toWorldDisplacement(e, v, feet, dx, dy, dz);
        // Only the axes that were really blocked may differ from the requested motion: in a tilted
        // or rotating vehicle frame, blocking one local axis slightly changes every world axis, and
        // vanilla would read that as a floor/ceiling or wall hit (zeroing jump or walk velocity).
        boolean vBlocked = Math.abs(dy - lmY0) > 1e-7 || push > 0.01; // tiny pushes are frame noise, not a floor hit
        boolean hBlocked = blockedH || sidePushed;
        double ox = hBlocked ? out.x : motion.x, oy = vBlocked ? out.y : motion.y, oz = hBlocked ? out.z : motion.z;
        return new Vec3(ox, oy, oz);
    }

    private static Vec3 toWorldDisplacement(Entity e, VehicleEntity v, Vector3d feet, double dx, double dy, double dz) {
        Vector3d end = v.localToWorld(feet.x + dx, feet.y + dy, feet.z + dz, new Vector3d());
        return new Vec3(end.x - e.getX(), end.y - e.getY(), end.z - e.getZ());
    }

    /** Smallest upward translation (≤ 1 block) that removes overlap with the shapes, or 0. */
    private static double depenetrateUp(AABB box, List<VoxelShape> shapes) {
        AABB shrunk = box.deflate(1e-4);
        double maxTop = -Double.MAX_VALUE;
        boolean overlap = false;
        for (VoxelShape s : shapes) {
            for (AABB a : s.toAabbs()) {
                if (a.intersects(shrunk)) {
                    overlap = true;
                    maxTop = Math.max(maxTop, a.maxY);
                }
            }
        }
        if (!overlap) return 0;
        double push = maxTop - box.minY;
        return push > 0 && push <= 1.0 ? push + 1e-4 : 0;
    }

    /** Smallest horizontal push (≤ 1.2 blocks) out of the shapes when pushing up is not possible. */
    private static double[] depenetrateSide(AABB box, List<VoxelShape> shapes) {
        AABB shrunk = box.deflate(1e-4);
        boolean overlap = false;
        for (VoxelShape s : shapes) for (AABB a : s.toAabbs()) if (a.intersects(shrunk)) { overlap = true; break; }
        if (!overlap) return null;
        double[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        double best = Double.MAX_VALUE;
        double[] out = null;
        for (double[] d : dirs) {
            for (double step = 0.05; step <= 1.2; step += 0.05) {
                AABB m = shrunk.move(d[0] * step, 0, d[1] * step);
                boolean hit = false;
                for (VoxelShape s : shapes) { for (AABB a : s.toAabbs()) if (a.intersects(m)) { hit = true; break; } if (hit) break; }
                if (!hit) {
                    if (step < best) { best = step; out = new double[]{d[0] * step, d[1] * step}; }
                    break;
                }
            }
        }
        return out;
    }

    /**
     * An entity standing just above vehicle blocks without an anchor (just placed, dismounted,
     * teleported in, or landed this very tick) is anchored at once: otherwise a fast vehicle
     * moves on for a whole tick and leaves it behind.
     */
    private static Anchor autoAnchor(Entity e) {
        for (VehicleEntity v : VehicleRegistry.near(e.level(), e.getBoundingBox().inflate(1.0, 3.0, 1.0))) {
            if (v.isRemoved() || v.structure.isEmpty()) continue;
            Vector3d l = v.worldToLocal(e.getX(), e.getY(), e.getZ(), new Vector3d());
            int bx = Mth.floor(l.x), bz = Mth.floor(l.z), by = Mth.floor(l.y - 0.001);
            for (int k = 0; k <= 2; k++) {
                if (!v.structure.contains(BlockPos.asLong(bx, by - k, bz))) continue;
                return anchorTo(e, v, l);
            }
        }
        return null;
    }

    private static Anchor anchorTo(Entity e, VehicleEntity v, Vector3d local) {
        Anchor a = new Anchor();
        a.vehicle = v;
        a.local.set(local);
        a.yaw = vehicleYaw(v);
        ANCHORS.put(e, a);
        return a;
    }

    /**
     * Put an entity at a vehicle-local point, in the vehicle pose this side sees, and anchor it
     * (used when stepping out of a seat at speed: the server's dismount position is computed in
     * its own, more recent vehicle pose, which is metres away from what the client shows).
     */
    public static void placeOnVehicle(Entity e, VehicleEntity v, double lx, double ly, double lz) {
        Vector3d w = v.localToWorld(lx, ly, lz, new Vector3d());
        e.setPos(w.x, w.y, w.z);
        e.setDeltaMovement(0, 0, 0);
        e.resetFallDistance();
        anchorTo(e, v, new Vector3d(lx, ly, lz));
    }

    /** inside the vehicle's block bounds (local frame, so it is exact at any speed / rotation) */
    private static boolean nearStructure(VehicleEntity v, Vector3d local) {
        VehicleStructure s = v.structure;
        return local.x > s.minX() - 2 && local.x < s.maxX() + 3 && local.z > s.minZ() - 2 && local.z < s.maxZ() + 3
                && local.y > s.minY() - 3 && local.y < s.maxY() + 8;
    }

    public static void forget(Entity e) {
        ANCHORS.remove(e);
    }
}
