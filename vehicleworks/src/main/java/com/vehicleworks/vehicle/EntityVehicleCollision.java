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
        VehicleEntity groundVehicle;
        double carryY;
    }

    private static final Map<Entity, Anchor> ANCHORS = new WeakHashMap<>();
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

    /** Called at the end of Entity.collide with vanilla's result. Returns the final movement. */
    public static Vec3 collide(Entity e, Vec3 movement, Vec3 resolved) {
        MoveInfo info = INFO.get();
        info.processed = false;
        info.vehicleGround = false;
        info.groundVehicle = null;
        info.carryY = 0;
        if (!isSimulatedHere(e)) return resolved;
        AABB box = e.getBoundingBox();
        Anchor anchor = ANCHORS.get(e);
        List<VehicleEntity> near = VehicleRegistry.near(e.level(), box.expandTowards(resolved).inflate(1.5));
        if (near.isEmpty() && (anchor == null || anchor.vehicle == null)) return resolved;
        info.processed = true;
        Vec3 total = resolved;
        VehicleEntity anchored = null;
        if (anchor != null && anchor.vehicle != null && !anchor.vehicle.isRemoved() && anchor.vehicle.level() == e.level()) {
            Vector3d target = anchor.vehicle.localToWorld(anchor.local.x, anchor.local.y, anchor.local.z, new Vector3d());
            if (target.distanceSquared(e.getX(), e.getY(), e.getZ()) < 400) {
                anchored = anchor.vehicle;
                info.carryY = target.y - e.getY();
                double yaw = vehicleYaw(anchored);
                float dy = (float) Mth.wrapDegrees(yaw - anchor.yaw);
                if (Math.abs(dy) > 1e-4) {
                    e.setYRot(e.getYRot() + dy);
                    if (e instanceof LivingEntity le) {
                        le.yHeadRot += dy;
                        le.yBodyRot += dy;
                    }
                }
                anchor.yaw = yaw;
                // start from the anchor point in the vehicle frame: the carry is implicit
                total = collideWith(e, anchored, new Vector3d(anchor.local), resolved, true, info);
            }
        }
        for (VehicleEntity v : near) {
            if (v == anchored || v.structure.isEmpty()) continue;
            Vector3d start = v.worldToLocal(e.getX(), e.getY(), e.getZ(), new Vector3d());
            total = collideWith(e, v, start, total, false, info);
        }
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
        else if (++a.airTicks > 60 || !v.getBoundingBox().inflate(2, 6, 2).contains(e.position())) ANCHORS.remove(e);
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
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 20000) return anchored ? toWorldDisplacement(e, v, feet, lm.x, lm.y, lm.z) : motion;
        List<VoxelShape> shapes = new ArrayList<>();
        VehicleStructure s = v.structure;
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    long k = BlockPos.asLong(x, y, z);
                    if (!s.contains(k)) continue;
                    VoxelShape sh = shape(s.get(k));
                    if (!sh.isEmpty()) shapes.add(sh.move(x, y, z));
                }
        if (shapes.isEmpty()) return anchored ? toWorldDisplacement(e, v, feet, lm.x, lm.y, lm.z) : motion;
        // push out if the vehicle moved into the entity
        double push = anchored ? 0 : depenetrateUp(box, shapes);
        if (push > 0) {
            box = box.move(0, push, 0);
            lm.y = Math.max(lm.y, 0) + push;
            if (push < 0.75) info.vehicleGround = true;
            info.groundVehicle = v;
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
                }
            }
        }
        if (groundedNow) {
            info.vehicleGround = true;
            info.groundVehicle = v;
        }
        return toWorldDisplacement(e, v, feet, dx, dy, dz);
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

    public static void forget(Entity e) {
        ANCHORS.remove(e);
    }
}
