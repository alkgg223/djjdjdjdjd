package com.vehicleworks.physics;

import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * Accumulates point/box masses and produces total mass, center of mass and the
 * inertia tensor about the center of mass. Uses running second moments so that
 * elements can be added in any order in O(1) each.
 */
public final class MassAccumulator {
    private double mass;
    private double sx, sy, sz;               // sum m*c
    private double sxx, syy, szz, sxy, sxz, syz; // sum m*c_i*c_j
    private double ixx, iyy, izz, ixy, ixz, iyz; // sum of element self-inertia (about own centre)

    public void clear() {
        mass = sx = sy = sz = 0;
        sxx = syy = szz = sxy = sxz = syz = 0;
        ixx = iyy = izz = ixy = ixz = iyz = 0;
    }

    /** Add a solid box element of the given mass, center and full extents. */
    public void addBox(double m, double cx, double cy, double cz, double ex, double ey, double ez) {
        if (m <= 0) return;
        addPoint(m, cx, cy, cz);
        ixx += m * (ey * ey + ez * ez) / 12.0;
        iyy += m * (ex * ex + ez * ez) / 12.0;
        izz += m * (ex * ex + ey * ey) / 12.0;
    }

    public void addPoint(double m, double cx, double cy, double cz) {
        if (m <= 0) return;
        mass += m;
        sx += m * cx; sy += m * cy; sz += m * cz;
        sxx += m * cx * cx; syy += m * cy * cy; szz += m * cz * cz;
        sxy += m * cx * cy; sxz += m * cx * cz; syz += m * cy * cz;
    }

    public void add(MassAccumulator o) {
        mass += o.mass;
        sx += o.sx; sy += o.sy; sz += o.sz;
        sxx += o.sxx; syy += o.syy; szz += o.szz;
        sxy += o.sxy; sxz += o.sxz; syz += o.syz;
        ixx += o.ixx; iyy += o.iyy; izz += o.izz;
        ixy += o.ixy; ixz += o.ixz; iyz += o.iyz;
    }

    public void set(MassAccumulator o) {
        clear();
        add(o);
    }

    public double mass() {
        return mass;
    }

    public Vector3d centerOfMass(Vector3d out) {
        if (mass <= 0) return out.zero();
        return out.set(sx / mass, sy / mass, sz / mass);
    }

    /** Inertia tensor about the center of mass (local axes). */
    public Matrix3d inertia(Matrix3d out) {
        if (mass <= 0) return out.identity();
        double cx = sx / mass, cy = sy / mass, cz = sz / mass;
        // Second moments about COM
        double xx = sxx - mass * cx * cx;
        double yy = syy - mass * cy * cy;
        double zz = szz - mass * cz * cz;
        double xy = sxy - mass * cx * cy;
        double xz = sxz - mass * cx * cz;
        double yz = syz - mass * cy * cz;
        double Ixx = yy + zz + ixx;
        double Iyy = xx + zz + iyy;
        double Izz = xx + yy + izz;
        double Ixy = -xy - ixy;
        double Ixz = -xz - ixz;
        double Iyz = -yz - iyz;
        // symmetric; JOML column-major mColRow
        out.m00 = Ixx; out.m10 = Ixy; out.m20 = Ixz;
        out.m01 = Ixy; out.m11 = Iyy; out.m21 = Iyz;
        out.m02 = Ixz; out.m12 = Iyz; out.m22 = Izz;
        return out;
    }
}
