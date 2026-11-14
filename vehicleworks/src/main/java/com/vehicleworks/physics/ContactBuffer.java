package com.vehicleworks.physics;

import java.util.Arrays;

/** Struct-of-arrays contact storage reused every substep (no per-contact allocation). */
public final class ContactBuffer {
    public int count;
    public RigidBody[] bodyA = new RigidBody[64];
    public RigidBody[] bodyB = new RigidBody[64];
    public double[] px = new double[64], py = new double[64], pz = new double[64];
    public double[] nx = new double[64], ny = new double[64], nz = new double[64];
    public double[] depth = new double[64];
    public float[] friction = new float[64];
    public float[] restitution = new float[64];
    /** Packed local voxel of body A involved (for damage), and of body B (or terrain block). */
    public long[] voxelA = new long[64];
    public long[] voxelB = new long[64];
    // solver scratch
    public double[] t1x = new double[64], t1y = new double[64], t1z = new double[64];
    public double[] t2x = new double[64], t2y = new double[64], t2z = new double[64];
    public double[] massN = new double[64], massT1 = new double[64], massT2 = new double[64];
    public double[] bias = new double[64];
    public double[] accN = new double[64], accT1 = new double[64], accT2 = new double[64];
    /** Approach speed along normal at the start of the solve (m/s, positive = approaching). */
    public double[] approach = new double[64];

    public void clear() {
        count = 0;
    }

    public int add(RigidBody a, RigidBody b, double x, double y, double z, double nX, double nY, double nZ,
                   double d, float mu, float e, long vA, long vB) {
        if (count == px.length) grow();
        int i = count++;
        bodyA[i] = a; bodyB[i] = b;
        px[i] = x; py[i] = y; pz[i] = z;
        nx[i] = nX; ny[i] = nY; nz[i] = nZ;
        depth[i] = d;
        friction[i] = mu;
        restitution[i] = e;
        voxelA[i] = vA; voxelB[i] = vB;
        accN[i] = accT1[i] = accT2[i] = 0;
        return i;
    }

    /** Keep only contacts whose indices are flagged in {@code keep}, preserving order. */
    public void compact(boolean[] keep, int from) {
        int w = from;
        for (int r = from; r < count; r++) {
            if (!keep[r - from]) continue;
            if (w != r) copy(r, w);
            w++;
        }
        for (int i = w; i < count; i++) { bodyA[i] = null; bodyB[i] = null; }
        count = w;
    }

    private void copy(int s, int d) {
        bodyA[d] = bodyA[s]; bodyB[d] = bodyB[s];
        px[d] = px[s]; py[d] = py[s]; pz[d] = pz[s];
        nx[d] = nx[s]; ny[d] = ny[s]; nz[d] = nz[s];
        depth[d] = depth[s]; friction[d] = friction[s]; restitution[d] = restitution[s];
        voxelA[d] = voxelA[s]; voxelB[d] = voxelB[s];
        accN[d] = accN[s]; accT1[d] = accT1[s]; accT2[d] = accT2[s];
    }

    private void grow() {
        int n = px.length * 2;
        bodyA = Arrays.copyOf(bodyA, n); bodyB = Arrays.copyOf(bodyB, n);
        px = Arrays.copyOf(px, n); py = Arrays.copyOf(py, n); pz = Arrays.copyOf(pz, n);
        nx = Arrays.copyOf(nx, n); ny = Arrays.copyOf(ny, n); nz = Arrays.copyOf(nz, n);
        depth = Arrays.copyOf(depth, n);
        friction = Arrays.copyOf(friction, n); restitution = Arrays.copyOf(restitution, n);
        voxelA = Arrays.copyOf(voxelA, n); voxelB = Arrays.copyOf(voxelB, n);
        t1x = Arrays.copyOf(t1x, n); t1y = Arrays.copyOf(t1y, n); t1z = Arrays.copyOf(t1z, n);
        t2x = Arrays.copyOf(t2x, n); t2y = Arrays.copyOf(t2y, n); t2z = Arrays.copyOf(t2z, n);
        massN = Arrays.copyOf(massN, n); massT1 = Arrays.copyOf(massT1, n); massT2 = Arrays.copyOf(massT2, n);
        bias = Arrays.copyOf(bias, n);
        accN = Arrays.copyOf(accN, n); accT1 = Arrays.copyOf(accT1, n); accT2 = Arrays.copyOf(accT2, n);
        approach = Arrays.copyOf(approach, n);
    }
}
