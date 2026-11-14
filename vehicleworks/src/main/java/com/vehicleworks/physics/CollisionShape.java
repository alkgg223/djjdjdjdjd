package com.vehicleworks.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * Optimised collision representation of a voxel structure: adjacent full voxels are
 * greedily merged into a small set of boxes, a voxel→box index gives O(1) point
 * queries, and a reduced set of surface probe points is used for contact generation.
 * Immutable after {@link Builder#build()} so it may be built on a worker thread.
 */
public final class CollisionShape {
    public final LocalBox[] boxes;
    /** voxel → index into {@link #boxes} (full voxels and partial-voxel boxes). */
    private final VoxelSet voxelIndex;
    /** Full-voxel occupancy (for internal face rejection). */
    private final VoxelSet fullVoxels;
    /** Probe points xyz triplets in local space; voxelKeys holds the owning voxel. */
    public final double[] probes;
    public final long[] probeVoxels;
    public final double minX, minY, minZ, maxX, maxY, maxZ;

    private CollisionShape(LocalBox[] boxes, VoxelSet voxelIndex, VoxelSet fullVoxels, double[] probes, long[] probeVoxels,
                           double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.boxes = boxes;
        this.voxelIndex = voxelIndex;
        this.fullVoxels = fullVoxels;
        this.probes = probes;
        this.probeVoxels = probeVoxels;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    public int probeCount() {
        return probes.length / 3;
    }

    public boolean isEmpty() {
        return boxes.length == 0;
    }

    /** Index of the box containing the local point, or -1. */
    public int boxAt(double x, double y, double z) {
        int vx = (int) Math.floor(x), vy = (int) Math.floor(y), vz = (int) Math.floor(z);
        int idx = voxelIndex.get(vx, vy, vz, -1);
        if (idx < 0) return -1;
        if (idx >= 0x40000000) {
            // multiple partial boxes in this voxel: stored as list offset
            int start = idx - 0x40000000;
            int count = multiCounts[start];
            for (int i = 0; i < count; i++) {
                int b = multiIndices[start + 1 + i];
                if (boxes[b].contains(x, y, z)) return b;
            }
            return -1;
        }
        return boxes[idx].contains(x, y, z) ? idx : -1;
    }

    private int[] multiCounts = new int[0];
    private int[] multiIndices = new int[0];

    public boolean isFullVoxel(int x, int y, int z) {
        return fullVoxels.contains(x, y, z);
    }

    public int fullVoxelCount() {
        return fullVoxels.size();
    }

    public static final class Builder {
        private final VoxelSet full = new VoxelSet(256);
        private final List<double[]> partial = new ArrayList<>();
        private final List<int[]> partialVoxel = new ArrayList<>();
        private final List<Float> partialFriction = new ArrayList<>();
        /** friction table: material id → friction */
        private final List<Float> materials = new ArrayList<>();

        private int materialId(float friction) {
            for (int i = 0; i < materials.size(); i++) if (materials.get(i) == friction) return i;
            materials.add(friction);
            return materials.size() - 1;
        }

        /** Add a full 1x1x1 voxel at integer local coordinates. */
        public Builder addFullVoxel(int x, int y, int z, float friction) {
            full.put(x, y, z, materialId(friction));
            return this;
        }

        /** Add a partial box (e.g. a slab) inside voxel (vx,vy,vz); coordinates local. */
        public Builder addPartialBox(int vx, int vy, int vz, double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ, float friction) {
            partial.add(new double[]{minX, minY, minZ, maxX, maxY, maxZ});
            partialVoxel.add(new int[]{vx, vy, vz});
            partialFriction.add(friction);
            return this;
        }

        public CollisionShape build() {
            List<LocalBox> boxes = new ArrayList<>();
            VoxelSet index = new VoxelSet(Math.max(16, full.size()));
            VoxelSet visited = new VoxelSet(Math.max(16, full.size()));
            // Collect and sort voxels for deterministic greedy merge (y, z, x order)
            long[] keys = new long[full.size()];
            int[] n = {0};
            full.forEach((x, y, z, v) -> keys[n[0]++] = VoxelSet.pack(x, y, z));
            java.util.Arrays.sort(keys);
            double mnX = Double.MAX_VALUE, mnY = Double.MAX_VALUE, mnZ = Double.MAX_VALUE;
            double mxX = -Double.MAX_VALUE, mxY = -Double.MAX_VALUE, mxZ = -Double.MAX_VALUE;
            for (long key : keys) {
                int x0 = VoxelSet.unpackX(key), y0 = VoxelSet.unpackY(key), z0 = VoxelSet.unpackZ(key);
                if (visited.contains(x0, y0, z0)) continue;
                int mat = full.get(x0, y0, z0, -1);
                // extend along z (last packed axis, so sorted neighbours are contiguous)
                int z1 = z0;
                while (canTake(x0, y0, z1 + 1, mat, visited)) z1++;
                // extend along y
                int y1 = y0;
                outerY:
                while (true) {
                    for (int z = z0; z <= z1; z++) if (!canTake(x0, y1 + 1, z, mat, visited)) break outerY;
                    y1++;
                }
                // extend along x
                int x1 = x0;
                outerX:
                while (true) {
                    for (int y = y0; y <= y1; y++)
                        for (int z = z0; z <= z1; z++) if (!canTake(x1 + 1, y, z, mat, visited)) break outerX;
                    x1++;
                }
                int idx = boxes.size();
                int count = 0;
                for (int x = x0; x <= x1; x++)
                    for (int y = y0; y <= y1; y++)
                        for (int z = z0; z <= z1; z++) {
                            visited.put(x, y, z, 1);
                            index.put(x, y, z, idx);
                            count++;
                        }
                boxes.add(new LocalBox(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1, materials.get(mat), count));
                mnX = Math.min(mnX, x0); mnY = Math.min(mnY, y0); mnZ = Math.min(mnZ, z0);
                mxX = Math.max(mxX, x1 + 1); mxY = Math.max(mxY, y1 + 1); mxZ = Math.max(mxZ, z1 + 1);
            }
            int fullBoxCount = boxes.size();
            // Partial boxes: group by voxel
            VoxelSet partialFirst = new VoxelSet(16);
            List<List<Integer>> groups = new ArrayList<>();
            for (int i = 0; i < partial.size(); i++) {
                double[] b = partial.get(i);
                int[] v = partialVoxel.get(i);
                if (full.contains(v[0], v[1], v[2])) continue;
                int bi = boxes.size();
                boxes.add(new LocalBox(b[0], b[1], b[2], b[3], b[4], b[5], partialFriction.get(i), 0));
                int g = partialFirst.get(v[0], v[1], v[2], -1);
                if (g < 0) {
                    g = groups.size();
                    groups.add(new ArrayList<>());
                    partialFirst.put(v[0], v[1], v[2], g);
                }
                groups.get(g).add(bi);
                mnX = Math.min(mnX, b[0]); mnY = Math.min(mnY, b[1]); mnZ = Math.min(mnZ, b[2]);
                mxX = Math.max(mxX, b[3]); mxY = Math.max(mxY, b[4]); mxZ = Math.max(mxZ, b[5]);
            }
            // encode groups
            int total = 0;
            for (List<Integer> g : groups) total += g.size() + 1;
            int[] mCounts = new int[Math.max(1, total)];
            int[] mIdx = new int[Math.max(1, total)];
            int[] off = {0};
            List<int[]> groupOffsets = new ArrayList<>();
            for (List<Integer> g : groups) {
                int start = off[0];
                if (g.size() == 1) {
                    groupOffsets.add(new int[]{-1, g.get(0)});
                    continue;
                }
                mCounts[start] = g.size();
                for (int i = 0; i < g.size(); i++) mIdx[start + 1 + i] = g.get(i);
                off[0] += g.size() + 1;
                groupOffsets.add(new int[]{start, -1});
            }
            partialFirst.forEach((x, y, z, g) -> {
                int[] go = groupOffsets.get(g);
                index.put(x, y, z, go[0] < 0 ? go[1] : 0x40000000 + go[0]);
            });

            if (boxes.isEmpty()) {
                mnX = mnY = mnZ = 0;
                mxX = mxY = mxZ = 0;
            }

            // Probe points: lattice points on every face (<= 1 m spacing, includes edges and corners),
            // excluding interior lattice points, inset 1 mm toward the box centre so that grid-aligned
            // contacts are detected robustly.
            List<double[]> pts = new ArrayList<>();
            List<Long> ptVox = new ArrayList<>();
            VoxelSet seenLattice = new VoxelSet(256);
            for (int bi = 0; bi < boxes.size(); bi++) {
                LocalBox b = boxes.get(bi);
                boolean fullBox = bi < fullBoxCount;
                sampleFaces(pts, ptVox, seenLattice, b, fullBox, full);
            }
            double[] probes = new double[pts.size() * 3];
            long[] pv = new long[pts.size()];
            for (int i = 0; i < pts.size(); i++) {
                double[] p = pts.get(i);
                probes[i * 3] = p[0];
                probes[i * 3 + 1] = p[1];
                probes[i * 3 + 2] = p[2];
                pv[i] = ptVox.get(i);
            }
            CollisionShape shape = new CollisionShape(boxes.toArray(new LocalBox[0]), index, full, probes, pv, mnX, mnY, mnZ, mxX, mxY, mxZ);
            shape.multiCounts = mCounts;
            shape.multiIndices = mIdx;
            return shape;
        }

        private static int clampVoxel(double v, double min, double max) {
            int lo = (int) Math.floor(min + 1e-6);
            int hi = Math.max(lo, (int) Math.ceil(max - 1e-6) - 1);
            int f = (int) Math.floor(v);
            return Math.max(lo, Math.min(hi, f));
        }

        private boolean canTake(int x, int y, int z, int mat, VoxelSet visited) {
            return full.get(x, y, z, -1) == mat && !visited.contains(x, y, z);
        }

        private static void sampleFaces(List<double[]> pts, List<Long> ptVox, VoxelSet seen, LocalBox b,
                                        boolean fullBox, VoxelSet full) {
            int nx = Math.max(1, (int) Math.ceil(b.maxX - b.minX - 1e-9));
            int ny = Math.max(1, (int) Math.ceil(b.maxY - b.minY - 1e-9));
            int nz = Math.max(1, (int) Math.ceil(b.maxZ - b.minZ - 1e-9));
            for (int i = 0; i <= nx; i++)
                for (int j = 0; j <= ny; j++)
                    for (int k = 0; k <= nz; k++) {
                        boolean surface = i == 0 || i == nx || j == 0 || j == ny || k == 0 || k == nz;
                        if (!surface) continue;
                        double x = b.minX + (b.maxX - b.minX) * i / nx;
                        double y = b.minY + (b.maxY - b.minY) * j / ny;
                        double z = b.minZ + (b.maxZ - b.minZ) * k / nz;
                        if (fullBox) {
                            int lx = (int) Math.round(x), ly = (int) Math.round(y), lz = (int) Math.round(z);
                            if (seen.contains(lx, ly, lz)) continue;
                            seen.put(lx, ly, lz, 1);
                            boolean interior = true;
                            for (int dx = -1; dx <= 0 && interior; dx++)
                                for (int dy = -1; dy <= 0 && interior; dy++)
                                    for (int dz = -1; dz <= 0; dz++)
                                        if (!full.contains(lx + dx, ly + dy, lz + dz)) { interior = false; break; }
                            if (interior) continue;
                        }
                        double e = 1e-3;
                        double px = i == 0 ? x + e : (i == nx ? x - e : x);
                        double py = j == 0 ? y + e : (j == ny ? y - e : y);
                        double pz = k == 0 ? z + e : (k == nz ? z - e : z);
                        pts.add(new double[]{px, py, pz});
                        ptVox.add(VoxelSet.pack(clampVoxel(px, b.minX, b.maxX), clampVoxel(py, b.minY, b.maxY), clampVoxel(pz, b.minZ, b.maxZ)));
                    }
        }
    }
}
