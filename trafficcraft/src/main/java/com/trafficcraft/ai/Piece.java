package com.trafficcraft.ai;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** A drivable polyline: part of a lane on an edge, or a connector through a junction. */
public final class Piece {
    public final double[] x, y, z, s;
    public final double length;
    public final int edge;      // edge id (edge piece) or -1
    public final int dir;       // +1 A->B, -1 B->A
    public final int lane;
    public final int node;      // junction node (connector) or end node (edge piece)
    public final boolean connector;
    public final float speedLimit; // m/s
    public int movement = -1;   // for connectors
    public int nextEdge = -1;   // for connectors: exit edge
    public int fromEdge = -1;   // for connectors: arrival edge
    public double stopS = -1;   // along this piece: stop line position (edge pieces ending at a junction)
    public float endOffset;     // lateral offset at the end (for lane continuity)

    Piece(List<Vec3> pts, int edge, int dir, int lane, int node, boolean connector, float speedLimit) {
        int n = pts.size();
        x = new double[n];
        y = new double[n];
        z = new double[n];
        s = new double[n];
        for (int i = 0; i < n; i++) {
            Vec3 p = pts.get(i);
            x[i] = p.x;
            y[i] = p.y;
            z[i] = p.z;
            if (i > 0) s[i] = s[i - 1] + Math.sqrt((x[i] - x[i - 1]) * (x[i] - x[i - 1]) + (z[i] - z[i - 1]) * (z[i] - z[i - 1]) + (y[i] - y[i - 1]) * (y[i] - y[i - 1]));
        }
        length = n > 0 ? s[n - 1] : 0;
        this.edge = edge;
        this.dir = dir;
        this.lane = lane;
        this.node = node;
        this.connector = connector;
        this.speedLimit = speedLimit;
    }

    public Vec3 at(double sv) {
        if (x.length == 1) return new Vec3(x[0], y[0], z[0]);
        sv = Math.max(0, Math.min(length, sv));
        int i = idx(sv);
        double f = (sv - s[i]) / Math.max(1e-9, s[i + 1] - s[i]);
        return new Vec3(x[i] + (x[i + 1] - x[i]) * f, y[i] + (y[i + 1] - y[i]) * f, z[i] + (z[i + 1] - z[i]) * f);
    }

    public Vec3 tangent(double sv) {
        if (x.length < 2) return new Vec3(0, 0, 1);
        int i = idx(Math.max(0, Math.min(length, sv)));
        double dx = x[i + 1] - x[i], dz = z[i + 1] - z[i];
        double l = Math.hypot(dx, dz);
        return l < 1e-9 ? new Vec3(0, 0, 1) : new Vec3(dx / l, 0, dz / l);
    }

    private int idx(double sv) {
        int lo = 0, hi = s.length - 2;
        while (lo < hi) {
            int m = (lo + hi + 1) >>> 1;
            if (s[m] <= sv) lo = m;
            else hi = m - 1;
        }
        return lo;
    }

    /** Arc length of the closest point to p, searching near a hint. */
    public double project(Vec3 p, double hint) {
        double best = Double.MAX_VALUE, bs = hint;
        int start = Math.max(0, idx(Math.max(0, hint - 6))), end = Math.min(s.length - 1, idx(Math.min(length, hint + 12)) + 1);
        for (int i = start; i < end; i++) {
            double ax = x[i], az = z[i], bx = x[i + 1], bz = z[i + 1];
            double vx = bx - ax, vz = bz - az;
            double l2 = vx * vx + vz * vz;
            double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((p.x - ax) * vx + (p.z - az) * vz) / l2));
            double qx = ax + vx * t, qz = az + vz * t;
            double d = (p.x - qx) * (p.x - qx) + (p.z - qz) * (p.z - qz);
            if (d < best) {
                best = d;
                bs = s[i] + t * (s[i + 1] - s[i]);
            }
        }
        return bs;
    }

    public static List<Vec3> list() {
        return new ArrayList<>();
    }
}
