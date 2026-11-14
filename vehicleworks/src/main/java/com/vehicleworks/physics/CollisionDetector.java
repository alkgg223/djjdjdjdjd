package com.vehicleworks.physics;

import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * Narrow-phase contact generation between voxel bodies and terrain or other bodies.
 * <p>
 * Two complementary point tests are used: (1) surface probe points of the body are
 * tested against terrain/other-body boxes, and (2) exposed corners of terrain boxes
 * (or the other body's probes) are tested against the body's merged boxes. Normals
 * are chosen along the axis of minimum penetration, rejecting internal faces between
 * adjacent solid voxels so bodies do not snag on seams.
 */
public final class CollisionDetector {
    public static final float DEFAULT_RESTITUTION = 0.05f;

    private final Vector3d tmp = new Vector3d();
    private final Vector3d tmp2 = new Vector3d();
    private final VoxelSet lattice = new VoxelSet(512);
    private final VoxelSet buckets = new VoxelSet(256);
    private boolean[] keep = new boolean[256];

    /**
     * Generate contacts between a body and static terrain.
     *
     * @param com structure-local center of mass (shape coordinates are structure-local)
     */
    public int detectTerrain(RigidBody body, CollisionShape shape, Vector3d com, TerrainBoxes terrain,
                             ContactBuffer out, double margin, int maxContacts) {
        if (shape.isEmpty() || terrain.isEmpty()) return 0;
        int start = out.count;
        Matrix3d r = body.rotation;
        double px = body.position.x, py = body.position.y, pz = body.position.z;
        double tx0 = terrain.boundsMinX - margin, ty0 = terrain.boundsMinY - margin, tz0 = terrain.boundsMinZ - margin;
        double tx1 = terrain.boundsMaxX + margin, ty1 = terrain.boundsMaxY + margin, tz1 = terrain.boundsMaxZ + margin;

        // (1) body probes inside terrain boxes
        double[] probes = shape.probes;
        int pc = shape.probeCount();
        for (int i = 0; i < pc; i++) {
            double lx = probes[i * 3] - com.x, ly = probes[i * 3 + 1] - com.y, lz = probes[i * 3 + 2] - com.z;
            double wx = r.m00 * lx + r.m10 * ly + r.m20 * lz + px;
            double wy = r.m01 * lx + r.m11 * ly + r.m21 * lz + py;
            double wz = r.m02 * lx + r.m12 * ly + r.m22 * lz + pz;
            if (wx < tx0 || wx > tx1 || wy < ty0 || wy > ty1 || wz < tz0 || wz > tz1) continue;
            int b = terrain.boxContaining(wx, wy, wz);
            if (b < 0) continue;
            int bx = (int) Math.floor(wx), by = (int) Math.floor(wy), bz = (int) Math.floor(wz);
            // penetration depth per exit face, skipping faces covered by full neighbour blocks
            double best = Double.MAX_VALUE;
            int bestAxis = -1;
            double d;
            d = wy - terrain.minY[b]; // exit -y
            if (d < best && !terrain.isFull(bx, by - 1, bz)) { best = d; bestAxis = 2; }
            d = terrain.maxY[b] - wy; // exit +y
            if (d < best && !(terrain.maxY[b] >= by + 1 && terrain.isFull(bx, by + 1, bz))) { best = d; bestAxis = 3; }
            d = wx - terrain.minX[b];
            if (d < best && !terrain.isFull(bx - 1, by, bz)) { best = d; bestAxis = 0; }
            d = terrain.maxX[b] - wx;
            if (d < best && !terrain.isFull(bx + 1, by, bz)) { best = d; bestAxis = 1; }
            d = wz - terrain.minZ[b];
            if (d < best && !terrain.isFull(bx, by, bz - 1)) { best = d; bestAxis = 4; }
            d = terrain.maxZ[b] - wz;
            if (d < best && !terrain.isFull(bx, by, bz + 1)) { best = d; bestAxis = 5; }
            if (bestAxis < 0) { // fully buried: push up
                bestAxis = 3;
                best = terrain.maxY[b] - wy;
            }
            double nX = 0, nY = 0, nZ = 0;
            switch (bestAxis) {
                case 0 -> nX = -1;
                case 1 -> nX = 1;
                case 2 -> nY = -1;
                case 3 -> nY = 1;
                case 4 -> nZ = -1;
                default -> nZ = 1;
            }
            float mu = (float) Math.sqrt(terrain.friction[b] * boxFriction(shape, probes[i * 3], probes[i * 3 + 1], probes[i * 3 + 2]));
            out.add(body, null, wx, wy, wz, nX, nY, nZ, best, mu, DEFAULT_RESTITUTION, shape.probeVoxels[i], VoxelSet.pack(bx, by, bz));
        }

        // (2) exposed terrain corners inside body boxes
        lattice.clearAll();
        for (int b = 0; b < terrain.count; b++) {
            double x0 = terrain.minX[b], y0 = terrain.minY[b], z0 = terrain.minZ[b];
            double x1 = terrain.maxX[b], y1 = terrain.maxY[b], z1 = terrain.maxZ[b];
            for (int c = 0; c < 8; c++) {
                double cx = (c & 1) == 0 ? x0 : x1;
                double cy = (c & 2) == 0 ? y0 : y1;
                double cz = (c & 4) == 0 ? z0 : z1;
                int lxI = (int) Math.round(cx), lyI = (int) Math.round(cy), lzI = (int) Math.round(cz);
                boolean onLattice = Math.abs(cx - lxI) < 1e-6 && Math.abs(cy - lyI) < 1e-6 && Math.abs(cz - lzI) < 1e-6;
                if (onLattice) {
                    if (lattice.contains(lxI, lyI, lzI)) continue;
                    lattice.put(lxI, lyI, lzI, 1);
                    if (terrain.isFull(lxI, lyI, lzI) && terrain.isFull(lxI - 1, lyI, lzI) && terrain.isFull(lxI, lyI - 1, lzI)
                            && terrain.isFull(lxI, lyI, lzI - 1) && terrain.isFull(lxI - 1, lyI - 1, lzI) && terrain.isFull(lxI - 1, lyI, lzI - 1)
                            && terrain.isFull(lxI, lyI - 1, lzI - 1) && terrain.isFull(lxI - 1, lyI - 1, lzI - 1)) continue;
                }
                // to local
                double dx = cx - px, dy = cy - py, dz = cz - pz;
                double lx = r.m00 * dx + r.m01 * dy + r.m02 * dz + com.x;
                double ly = r.m10 * dx + r.m11 * dy + r.m12 * dz + com.y;
                double lz = r.m20 * dx + r.m21 * dy + r.m22 * dz + com.z;
                if (lx <= shape.minX || lx >= shape.maxX || ly <= shape.minY || ly >= shape.maxY || lz <= shape.minZ || lz >= shape.maxZ) continue;
                int bi = shape.boxAt(lx, ly, lz);
                if (bi < 0) continue;
                // exposed terrain faces at this corner: the push direction must agree with one of them
                int bx = (int) Math.floor(x0 + 1e-6), by = (int) Math.floor(y0 + 1e-6), bz = (int) Math.floor(z0 + 1e-6);
                int ex = (c & 1) == 0 ? -1 : 1, ey = (c & 2) == 0 ? -1 : 1, ez = (c & 4) == 0 ? -1 : 1;
                boolean fx = !terrain.isFull(bx + ex, by, bz), fy = !terrain.isFull(bx, by + ey, bz), fz = !terrain.isFull(bx, by, bz + ez);
                if (!fx && !fy && !fz) continue;
                addBoxExitContact(body, null, shape, bi, lx, ly, lz, cx, cy, cz, terrain.friction[b], out,
                        VoxelSet.pack(bx, by, bz), fx ? ex : 0, fy ? ey : 0, fz ? ez : 0);
            }
        }
        return reduce(out, start, maxContacts, body, com);
    }

    /**
     * Contacts between two dynamic bodies. Normals are applied to body A (pointing the way A must move).
     */
    public int detectBodies(RigidBody a, CollisionShape sa, Vector3d comA, RigidBody b, CollisionShape sb, Vector3d comB,
                            ContactBuffer out, int maxContacts) {
        int start = out.count;
        probeAgainst(a, sa, comA, b, sb, comB, out, false);
        probeAgainst(b, sb, comB, a, sa, comA, out, true);
        return reduce(out, start, maxContacts, a, comA);
    }

    /** Probes of P tested against boxes of Q. If {@code flip}, contacts are written as (Q-as-A... ) i.e. normal negated so A stays the first body. */
    private void probeAgainst(RigidBody p, CollisionShape sp, Vector3d comP, RigidBody q, CollisionShape sq, Vector3d comQ,
                              ContactBuffer out, boolean flip) {
        Matrix3d rp = p.rotation, rq = q.rotation;
        double[] probes = sp.probes;
        int pc = sp.probeCount();
        for (int i = 0; i < pc; i++) {
            double lx = probes[i * 3] - comP.x, ly = probes[i * 3 + 1] - comP.y, lz = probes[i * 3 + 2] - comP.z;
            double wx = rp.m00 * lx + rp.m10 * ly + rp.m20 * lz + p.position.x;
            double wy = rp.m01 * lx + rp.m11 * ly + rp.m21 * lz + p.position.y;
            double wz = rp.m02 * lx + rp.m12 * ly + rp.m22 * lz + p.position.z;
            double dx = wx - q.position.x, dy = wy - q.position.y, dz = wz - q.position.z;
            double qx = rq.m00 * dx + rq.m01 * dy + rq.m02 * dz + comQ.x;
            double qy = rq.m10 * dx + rq.m11 * dy + rq.m12 * dz + comQ.y;
            double qz = rq.m20 * dx + rq.m21 * dy + rq.m22 * dz + comQ.z;
            if (qx <= sq.minX || qx >= sq.maxX || qy <= sq.minY || qy >= sq.maxY || qz <= sq.minZ || qz >= sq.maxZ) continue;
            int bi = sq.boxAt(qx, qy, qz);
            if (bi < 0) continue;
            int before = out.count;
            // exit contact: point of P inside box of Q → normal on P points out of Q's box
            addBoxExitContact(q, p, sq, bi, qx, qy, qz, wx, wy, wz,
                    boxFriction(sp, probes[i * 3], probes[i * 3 + 1], probes[i * 3 + 2]), out, sp.probeVoxels[i], 0, 0, 0);
            if (out.count > before) {
                int c = out.count - 1;
                // addBoxExitContact produced contact for body q (normal pushing q). Convert to body p perspective.
                out.nx[c] = -out.nx[c]; out.ny[c] = -out.ny[c]; out.nz[c] = -out.nz[c];
                long vq = out.voxelB[c];
                out.voxelB[c] = out.voxelA[c];
                out.voxelA[c] = vq;
                out.bodyA[c] = p; out.bodyB[c] = q;
                if (flip) {
                    // keep 'a' as first body: swap back
                    out.nx[c] = -out.nx[c]; out.ny[c] = -out.ny[c]; out.nz[c] = -out.nz[c];
                    long t = out.voxelA[c]; out.voxelA[c] = out.voxelB[c]; out.voxelB[c] = t;
                    out.bodyA[c] = q; out.bodyB[c] = p;
                }
            }
        }
    }

    /**
     * A world point lies inside box {@code bi} of body {@code owner}. Adds a contact for {@code owner}
     * whose normal pushes the owner away from the point (point exits through nearest exposed face).
     * voxelOther identifies the voxel of the other party.
     */
    private void addBoxExitContact(RigidBody owner, RigidBody other, CollisionShape shape, int bi,
                                   double lx, double ly, double lz, double wx, double wy, double wz,
                                   float otherFriction, ContactBuffer out, long voxelOther,
                                   int allowX, int allowY, int allowZ) {
        LocalBox box = shape.boxes[bi];
        int vx = (int) Math.floor(lx), vy = (int) Math.floor(ly), vz = (int) Math.floor(lz);
        boolean filter = allowX != 0 || allowY != 0 || allowZ != 0;
        Matrix3d r = owner.rotation;
        double best = Double.MAX_VALUE;
        int axis = -1;
        for (int f = 0; f < 6; f++) {
            double d;
            boolean blocked;
            switch (f) {
                case 0 -> { d = lx - box.minX; blocked = shape.isFullVoxel((int) Math.floor(box.minX) - 1, vy, vz); }
                case 1 -> { d = box.maxX - lx; blocked = shape.isFullVoxel((int) Math.ceil(box.maxX), vy, vz); }
                case 2 -> { d = ly - box.minY; blocked = shape.isFullVoxel(vx, (int) Math.floor(box.minY) - 1, vz); }
                case 3 -> { d = box.maxY - ly; blocked = shape.isFullVoxel(vx, (int) Math.ceil(box.maxY), vz); }
                case 4 -> { d = lz - box.minZ; blocked = shape.isFullVoxel(vx, vy, (int) Math.floor(box.minZ) - 1); }
                default -> { d = box.maxZ - lz; blocked = shape.isFullVoxel(vx, vy, (int) Math.ceil(box.maxZ)); }
            }
            if (blocked || d >= best) continue;
            if (filter) {
                // push direction (world) = -(outward face normal)
                double nlx = f == 0 ? -1 : (f == 1 ? 1 : 0), nly = f == 2 ? -1 : (f == 3 ? 1 : 0), nlz = f == 4 ? -1 : (f == 5 ? 1 : 0);
                double px = -(r.m00 * nlx + r.m10 * nly + r.m20 * nlz);
                double py = -(r.m01 * nlx + r.m11 * nly + r.m21 * nlz);
                double pz = -(r.m02 * nlx + r.m12 * nly + r.m22 * nlz);
                double agree = Math.max(allowX * px, Math.max(allowY * py, allowZ * pz));
                if (agree < 0.25) continue;
            }
            best = d;
            axis = f;
        }
        if (axis < 0) return; // deep inside: ignore, other probes resolve it
        // outward face normal in local space
        double nlx = 0, nly = 0, nlz = 0;
        switch (axis) {
            case 0 -> nlx = -1;
            case 1 -> nlx = 1;
            case 2 -> nly = -1;
            case 3 -> nly = 1;
            case 4 -> nlz = -1;
            default -> nlz = 1;
        }
        double nwx = r.m00 * nlx + r.m10 * nly + r.m20 * nlz;
        double nwy = r.m01 * nlx + r.m11 * nly + r.m21 * nlz;
        double nwz = r.m02 * nlx + r.m12 * nly + r.m22 * nlz;
        float mu = (float) Math.sqrt(Math.max(0, otherFriction * box.friction));
        // owner must move opposite to the outward normal of the face the point exits through
        out.add(owner, other, wx, wy, wz, -nwx, -nwy, -nwz, best, mu, DEFAULT_RESTITUTION,
                VoxelSet.pack(Math.max((int) Math.floor(box.minX), Math.min((int) Math.ceil(box.maxX) - 1, vx)),
                        Math.max((int) Math.floor(box.minY), Math.min((int) Math.ceil(box.maxY) - 1, vy)),
                        Math.max((int) Math.floor(box.minZ), Math.min((int) Math.ceil(box.maxZ) - 1, vz))),
                voxelOther);
    }

    private static float boxFriction(CollisionShape shape, double lx, double ly, double lz) {
        // probe lies on the surface; sample slightly inside each neighbouring voxel
        for (int dx = -1; dx <= 0; dx++)
            for (int dy = -1; dy <= 0; dy++)
                for (int dz = -1; dz <= 0; dz++) {
                    int b = shape.boxAt(lx + dx * 0.01 + 0.005, ly + dy * 0.01 + 0.005, lz + dz * 0.01 + 0.005);
                    if (b >= 0) return shape.boxes[b].friction;
                }
        return 0.6f;
    }

    /**
     * Reduce contacts added since {@code start}: keep the deepest contact per 1.5 m spatial bucket
     * and normal direction, then cap the total by depth.
     */
    private int reduce(ContactBuffer out, int start, int maxContacts, RigidBody frame, Vector3d com) {
        int n = out.count - start;
        if (n <= 0) return 0;
        if (keep.length < n) keep = new boolean[Math.max(n, keep.length * 2)];
        buckets.clearAll();
        for (int i = 0; i < n; i++) {
            int c = start + i;
            int bx = (int) Math.floor((out.px[c] - frame.position.x) / 1.5);
            int by = (int) Math.floor((out.py[c] - frame.position.y) / 1.5);
            int bz = (int) Math.floor((out.pz[c] - frame.position.z) / 1.5);
            int dir = normalBucket(out.nx[c], out.ny[c], out.nz[c]);
            int bkx = bx * 8 + dir;
            int prev = buckets.get(bkx, by, bz, -1);
            if (prev < 0) {
                buckets.put(bkx, by, bz, i);
                keep[i] = true;
            } else if (out.depth[c] > out.depth[start + prev]) {
                keep[prev] = false;
                keep[i] = true;
                buckets.put(bkx, by, bz, i);
            } else {
                keep[i] = false;
            }
        }
        out.compact(keep, start);
        n = out.count - start;
        if (n > maxContacts) {
            // simple selection by depth: find threshold
            double[] depths = new double[n];
            for (int i = 0; i < n; i++) depths[i] = out.depth[start + i];
            java.util.Arrays.sort(depths);
            double threshold = depths[n - maxContacts];
            int kept = 0;
            for (int i = 0; i < n; i++) {
                keep[i] = out.depth[start + i] >= threshold && kept < maxContacts;
                if (keep[i]) kept++;
            }
            out.compact(keep, start);
        }
        return out.count - start;
    }

    private static int normalBucket(double x, double y, double z) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ax >= ay && ax >= az) return x > 0 ? 0 : 1;
        if (ay >= az) return y > 0 ? 2 : 3;
        return z > 0 ? 4 : 5;
    }
}
