package com.vehicleworks.physics.fluid;

import com.vehicleworks.physics.VoxelSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Voxel analysis of a structure:
 * <ul>
 *   <li>exposed faces of solid voxels (for drag),</li>
 *   <li>hull pockets — empty cells water cannot reach without rising above them. Found with a
 *       bucket-queue priority flood ("spill height"), so open-top hulls float until water
 *       spills over the gunwale, and sealed compartments always displace water,</li>
 *   <li>gas pockets — the same with gravity inverted (gas cannot escape without descending),
 *       bounded mostly by envelope material, e.g. a hot-air balloon with an open mouth.</li>
 * </ul>
 * Cost is O(bounding volume). Designed to run on a worker thread.
 */
public final class StructureAnalysis {
    public static final int FLAG_SOLID = 1;
    public static final int FLAG_ENVELOPE = 2;
    public static final int FLAG_NO_BODY_DRAG = 4;

    public final int minX, minY, minZ, sx, sy, sz;
    /** exposed face mask per solid voxel: bit k set if face k (−x,+x,−y,+y,−z,+z) touches outside air */
    public final VoxelSet exposedFaces;
    public final List<Pocket> pockets = new ArrayList<>();
    public final List<Pocket> gasPockets = new ArrayList<>();
    public int solidCount;

    public static final class Pocket {
        public final long[] cells;
        public final boolean sealed;
        /** Local height (cell y) at which water spills in (open pockets); Integer.MAX_VALUE if sealed. */
        public final int spillY;
        /** Up to 24 cells of the top layer, sampled to detect flooding over the rim. */
        public final long[] rim;
        public double cx, cy, cz;
        public final double volume;
        public double envelopeFraction;

        Pocket(long[] cells, boolean sealed, int spillY, long[] rim) {
            this.cells = cells;
            this.sealed = sealed;
            this.spillY = spillY;
            this.rim = rim;
            this.volume = cells.length;
            double x = 0, y = 0, z = 0;
            for (long c : cells) {
                x += VoxelSet.unpackX(c) + 0.5;
                y += VoxelSet.unpackY(c) + 0.5;
                z += VoxelSet.unpackZ(c) + 0.5;
            }
            if (cells.length > 0) { cx = x / cells.length; cy = y / cells.length; cz = z / cells.length; }
        }
    }

    private static final int[] DX = {-1, 1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, -1, 1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, -1, 1};

    public StructureAnalysis(VoxelSet voxels, int maxVolume) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        int[] count = {0};
        voxels.forEach((x, y, z, v) -> {
            if ((v & FLAG_SOLID) == 0) return;
            count[0]++;
            b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], y); b[2] = Math.min(b[2], z);
            b[3] = Math.max(b[3], x); b[4] = Math.max(b[4], y); b[5] = Math.max(b[5], z);
        });
        solidCount = count[0];
        exposedFaces = new VoxelSet(Math.max(16, voxels.size()));
        if (b[0] == Integer.MAX_VALUE) {
            minX = minY = minZ = 0;
            sx = sy = sz = 0;
            return;
        }
        minX = b[0] - 1; minY = b[1] - 1; minZ = b[2] - 1;
        sx = b[3] - b[0] + 3; sy = b[4] - b[1] + 3; sz = b[5] - b[2] + 3;
        long vol = (long) sx * sy * sz;
        if (vol > maxVolume) {
            voxels.forEach((x, y, z, v) -> {
                if ((v & FLAG_SOLID) == 0) return;
                int mask = 0;
                for (int f = 0; f < 6; f++) if ((voxels.get(x + DX[f], y + DY[f], z + DZ[f], 0) & FLAG_SOLID) == 0) mask |= 1 << f;
                exposedFaces.put(x, y, z, mask);
            });
            return;
        }
        int n = (int) vol;
        byte[] solid = new byte[n];
        voxels.forEach((x, y, z, v) -> {
            if ((v & FLAG_SOLID) == 0) return;
            solid[idx(x - minX, y - minY, z - minZ)] = (byte) ((v & FLAG_ENVELOPE) != 0 ? 2 : 1);
        });

        int[] waterSpill = priorityFlood(solid, false);
        int[] gasSpill = priorityFlood(solid, true);

        // exterior = reachable at its own level (spill == own height) for exposure purposes
        voxels.forEach((x, y, z, v) -> {
            if ((v & FLAG_SOLID) == 0) return;
            int lx = x - minX, ly = y - minY, lz = z - minZ;
            int mask = 0;
            for (int f = 0; f < 6; f++) {
                int nx = lx + DX[f], ny = ly + DY[f], nz = lz + DZ[f];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) { mask |= 1 << f; continue; }
                int ni = idx(nx, ny, nz);
                if (solid[ni] == 0 && waterSpill[ni] != Integer.MAX_VALUE) mask |= 1 << f;
            }
            exposedFaces.put(x, y, z, mask);
        });

        collectPockets(solid, waterSpill, false, pockets);
        collectPockets(solid, gasSpill, true, gasPockets);
        gasPockets.removeIf(p -> p.envelopeFraction < 0.4);
    }

    private void collectPockets(byte[] solid, int[] spill, boolean gas, List<Pocket> out) {
        int n = solid.length;
        boolean[] seen = new boolean[n];
        int[] queue = new int[n];
        for (int i = 0; i < n; i++) {
            if (solid[i] != 0 || seen[i] || !isDry(i, spill, gas)) continue;
            List<Long> cells = new ArrayList<>();
            int head = 0, tail = 0;
            queue[tail++] = i;
            seen[i] = true;
            int minSpill = Integer.MAX_VALUE;
            int topY = Integer.MIN_VALUE;
            int boundary = 0, envelope = 0;
            while (head < tail) {
                int c = queue[head++];
                int cx = c % sx, cy = (c / sx) % sy, cz = c / (sx * sy);
                cells.add(VoxelSet.pack(cx + minX, cy + minY, cz + minZ));
                minSpill = Math.min(minSpill, spill[c]);
                topY = Math.max(topY, gas ? -cy : cy);
                for (int f = 0; f < 6; f++) {
                    int nx = cx + DX[f], ny = cy + DY[f], nz = cz + DZ[f];
                    if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) continue;
                    int ni = idx(nx, ny, nz);
                    if (solid[ni] != 0) {
                        boundary++;
                        if (solid[ni] == 2) envelope++;
                        continue;
                    }
                    if (seen[ni] || !isDry(ni, spill, gas)) continue;
                    seen[ni] = true;
                    queue[tail++] = ni;
                }
            }
            long[] arr = new long[cells.size()];
            for (int k = 0; k < arr.length; k++) arr[k] = cells.get(k);
            boolean sealed = minSpill == Integer.MAX_VALUE;
            long[] rim = new long[0];
            int spillY = Integer.MAX_VALUE;
            if (!sealed) {
                int ty = (gas ? -topY : topY) + minY;
                rim = Arrays.stream(arr).filter(k -> VoxelSet.unpackY(k) == ty).limit(24).toArray();
                spillY = gas ? (sy - 1 - minSpill) + minY : minSpill + minY;
            }
            Pocket p = new Pocket(arr, sealed, spillY, rim);
            p.envelopeFraction = boundary > 0 ? (double) envelope / boundary : 0;
            out.add(p);
        }
    }

    private boolean isDry(int i, int[] spill, boolean gas) {
        int y = (i / sx) % sy;
        int h = gas ? (sy - 1 - y) : y;
        return spill[i] > h;
    }

    private int idx(int x, int y, int z) {
        return x + sx * (y + sy * z);
    }

    /**
     * Bucket-queue priority flood from the bounding shell. Returns for every empty cell the
     * minimal "maximum height" over all paths from outside (spill height). With {@code invert}
     * heights are mirrored (gas rising instead of water sinking). Solid cells and cells never
     * reached get Integer.MAX_VALUE.
     */
    private int[] priorityFlood(byte[] solid, boolean invert) {
        int n = solid.length;
        int[] spill = new int[n];
        Arrays.fill(spill, Integer.MAX_VALUE);
        int levels = sy;
        int[][] buckets = new int[levels][];
        int[] sizes = new int[levels];
        for (int l = 0; l < levels; l++) buckets[l] = new int[16];
        for (int z = 0; z < sz; z++)
            for (int y = 0; y < sy; y++)
                for (int x = 0; x < sx; x++) {
                    if (!(x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1)) continue;
                    int i = idx(x, y, z);
                    if (solid[i] != 0) continue;
                    int h = invert ? sy - 1 - y : y;
                    spill[i] = h;
                    sizes[h] = push(buckets, sizes, h, i);
                }
        for (int level = 0; level < levels; level++) {
            int pos = 0;
            while (pos < sizes[level]) {
                int c = buckets[level][pos++];
                if (spill[c] != level) continue;
                int cx = c % sx, cy = (c / sx) % sy, cz = c / (sx * sy);
                for (int f = 0; f < 6; f++) {
                    int nx = cx + DX[f], ny = cy + DY[f], nz = cz + DZ[f];
                    if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) continue;
                    int ni = idx(nx, ny, nz);
                    if (solid[ni] != 0) continue;
                    int h = invert ? sy - 1 - ny : ny;
                    int ns = Math.max(level, h);
                    if (ns < spill[ni]) {
                        spill[ni] = ns;
                        sizes[ns] = push(buckets, sizes, ns, ni);
                    }
                }
            }
        }
        return spill;
    }

    private static int push(int[][] buckets, int[] sizes, int level, int value) {
        int s = sizes[level];
        if (s == buckets[level].length) buckets[level] = Arrays.copyOf(buckets[level], s * 2);
        buckets[level][s] = value;
        return s + 1;
    }
}
