package com.vehicleworks.physics;

import java.util.Arrays;

/**
 * World-space collision boxes of static terrain collected for one simulation tick.
 * Boxes of the same block are stored contiguously and indexed by block coordinate.
 */
public final class TerrainBoxes {
    public double[] minX = new double[256], minY = new double[256], minZ = new double[256];
    public double[] maxX = new double[256], maxY = new double[256], maxZ = new double[256];
    public float[] friction = new float[256];
    public int count;
    private final VoxelSet blockIndex = new VoxelSet(256);   // block → (start << 8 | count)
    private final VoxelSet fullBlocks = new VoxelSet(256);
    public double boundsMinX, boundsMinY, boundsMinZ, boundsMaxX, boundsMaxY, boundsMaxZ;
    /** Block coordinate range that was queried (inclusive). */
    public int qMinX, qMinY, qMinZ, qMaxX, qMaxY, qMaxZ;
    private int blockStart = -1, bx, by, bz;

    public void clear() {
        count = 0;
        blockIndex.clearAll();
        fullBlocks.clearAll();
        boundsMinX = boundsMinY = boundsMinZ = Double.MAX_VALUE;
        boundsMaxX = boundsMaxY = boundsMaxZ = -Double.MAX_VALUE;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public void setQueryRange(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        qMinX = minX; qMinY = minY; qMinZ = minZ; qMaxX = maxX; qMaxY = maxY; qMaxZ = maxZ;
    }

    public void beginBlock(int x, int y, int z, boolean fullCube) {
        endBlock();
        blockStart = count;
        bx = x; by = y; bz = z;
        if (fullCube) fullBlocks.put(x, y, z, 1);
    }

    public void addBox(double x0, double y0, double z0, double x1, double y1, double z1, float mu) {
        if (count == minX.length) grow();
        minX[count] = x0; minY[count] = y0; minZ[count] = z0;
        maxX[count] = x1; maxY[count] = y1; maxZ[count] = z1;
        friction[count] = mu;
        count++;
        if (x0 < boundsMinX) boundsMinX = x0;
        if (y0 < boundsMinY) boundsMinY = y0;
        if (z0 < boundsMinZ) boundsMinZ = z0;
        if (x1 > boundsMaxX) boundsMaxX = x1;
        if (y1 > boundsMaxY) boundsMaxY = y1;
        if (z1 > boundsMaxZ) boundsMaxZ = z1;
    }

    public void endBlock() {
        if (blockStart >= 0) {
            int n = count - blockStart;
            if (n > 0) blockIndex.put(bx, by, bz, (blockStart << 8) | Math.min(n, 255));
            blockStart = -1;
        }
    }

    /** Packed (start << 8 | count) of boxes in a block or -1. */
    public int boxesAt(int x, int y, int z) {
        return blockIndex.get(x, y, z, -1);
    }

    public boolean isFull(int x, int y, int z) {
        return fullBlocks.contains(x, y, z);
    }

    public boolean overlaps(double x0, double y0, double z0, double x1, double y1, double z1) {
        return count > 0 && x1 > boundsMinX && x0 < boundsMaxX && y1 > boundsMinY && y0 < boundsMaxY && z1 > boundsMinZ && z0 < boundsMaxZ;
    }

    private void grow() {
        int n = minX.length * 2;
        minX = Arrays.copyOf(minX, n); minY = Arrays.copyOf(minY, n); minZ = Arrays.copyOf(minZ, n);
        maxX = Arrays.copyOf(maxX, n); maxY = Arrays.copyOf(maxY, n); maxZ = Arrays.copyOf(maxZ, n);
        friction = Arrays.copyOf(friction, n);
    }

    /** Index of the box containing the world point, or -1. */
    public int boxContaining(double x, double y, double z) {
        int packed = boxesAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (packed < 0) return -1;
        int start = packed >>> 8, n = packed & 0xFF;
        for (int i = start; i < start + n; i++) {
            if (x > minX[i] && x < maxX[i] && y > minY[i] && y < maxY[i] && z > minZ[i] && z < maxZ[i]) return i;
        }
        return -1;
    }

    /**
     * Ray cast against terrain boxes using voxel traversal. Returns hit distance or -1.
     * Direction must be normalised.
     */
    public double raycast(double ox, double oy, double oz, double dx, double dy, double dz, double maxDist, double[] normalOut) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double tDeltaX = dx != 0 ? Math.abs(1.0 / dx) : Double.MAX_VALUE;
        double tDeltaY = dy != 0 ? Math.abs(1.0 / dy) : Double.MAX_VALUE;
        double tDeltaZ = dz != 0 ? Math.abs(1.0 / dz) : Double.MAX_VALUE;
        double tMaxX = dx != 0 ? ((dx > 0 ? (x + 1 - ox) : (ox - x)) * tDeltaX) : Double.MAX_VALUE;
        double tMaxY = dy != 0 ? ((dy > 0 ? (y + 1 - oy) : (oy - y)) * tDeltaY) : Double.MAX_VALUE;
        double tMaxZ = dz != 0 ? ((dz > 0 ? (z + 1 - oz) : (oz - z)) * tDeltaZ) : Double.MAX_VALUE;
        double t = 0;
        int guard = 0;
        while (t <= maxDist && guard++ < 512) {
            int packed = boxesAt(x, y, z);
            if (packed >= 0) {
                int start = packed >>> 8, n = packed & 0xFF;
                double best = Double.MAX_VALUE;
                for (int i = start; i < start + n; i++) {
                    double h = rayBox(ox, oy, oz, dx, dy, dz, minX[i], minY[i], minZ[i], maxX[i], maxY[i], maxZ[i], normalOut != null ? tmpN : null);
                    if (h >= 0 && h < best) {
                        best = h;
                        if (normalOut != null) { normalOut[0] = tmpN[0]; normalOut[1] = tmpN[1]; normalOut[2] = tmpN[2]; }
                    }
                }
                if (best <= maxDist) return best;
            }
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) { x += stepX; t = tMaxX; tMaxX += tDeltaX; }
                else { z += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; }
            } else {
                if (tMaxY < tMaxZ) { y += stepY; t = tMaxY; tMaxY += tDeltaY; }
                else { z += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; }
            }
        }
        return -1;
    }

    private final double[] tmpN = new double[3];

    /** Slab ray/box test; returns entry distance (>=0) or -1. */
    public static double rayBox(double ox, double oy, double oz, double dx, double dy, double dz,
                                double x0, double y0, double z0, double x1, double y1, double z1, double[] n) {
        double tmin = 0, tmax = Double.MAX_VALUE;
        int axis = -1;
        double sign = 0;
        double[] o = {ox, oy, oz}, d = {dx, dy, dz}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-12) {
                if (o[a] < lo[a] || o[a] > hi[a]) return -1;
            } else {
                double inv = 1.0 / d[a];
                double t1 = (lo[a] - o[a]) * inv, t2 = (hi[a] - o[a]) * inv;
                double s = -1;
                if (t1 > t2) { double tt = t1; t1 = t2; t2 = tt; s = 1; }
                if (t1 > tmin) { tmin = t1; axis = a; sign = s; }
                if (t2 < tmax) tmax = t2;
                if (tmin > tmax) return -1;
            }
        }
        if (n != null) {
            n[0] = n[1] = n[2] = 0;
            if (axis >= 0) n[axis] = sign;
        }
        return tmin;
    }
}
