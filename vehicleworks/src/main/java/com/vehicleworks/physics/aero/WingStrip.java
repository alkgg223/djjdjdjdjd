package com.vehicleworks.physics.aero;

import com.vehicleworks.physics.RigidBody;
import org.joml.Vector3d;

/**
 * A spanwise strip of a wing panel (one or more chordwise blocks). Computes lift and
 * drag from the local relative airflow, including the body's rotation (v + ω × r),
 * and applies them at the strip's aerodynamic centre.
 */
public final class WingStrip {
    /** Structure-local centre of the strip. */
    public final Vector3d localCenter = new Vector3d();
    /** Unit chord direction (leading edge points along +forward). */
    public final Vector3d forward = new Vector3d();
    /** Unit normal (positive lift direction at zero α). */
    public final Vector3d normal = new Vector3d();
    public final WingParams params;
    public double area;
    public double aspectRatio;
    public double span;
    /** Number of blocks in this strip (for stress distribution). */
    public int blocks;
    /** Packed local voxel keys belonging to this strip. */
    public long[] voxels = new long[0];
    /** Panel id this strip belongs to. */
    public int panel;

    // runtime state
    public double controlDeflection;   // rad
    public double flapDeflection;      // rad
    public double spoiler;             // 0..1
    public double currentSweepRad;
    public double lastAlpha, lastLift, lastDrag, lastCl, lastCd, lastDynamicPressure;
    public boolean lastStalled;
    public double groundHeight = 1000;
    /** 0..1 effectiveness reduced by structural damage (set by the game layer). */
    public double damageFactor = 1.0;
    /** Unit control moment axis u = (r × n)/|r × n| in local frame, cached. */
    private final Vector3d controlAxis = new Vector3d();

    private final AeroModel model = new AeroModel();
    private final Vector3d tmp = new Vector3d();
    private final Vector3d tmp2 = new Vector3d();
    private final Vector3d flow = new Vector3d();

    public WingStrip(WingParams params) {
        this.params = params;
        this.currentSweepRad = Math.toRadians(params.sweepDeg);
    }

    /** Recompute the control axis after mass properties change. */
    public void updateControlAxis(Vector3d comLocal) {
        tmp.set(localCenter).sub(comLocal);
        tmp.cross(normal, controlAxis);
        double l = controlAxis.length();
        if (l > 1e-6) controlAxis.div(l);
        else controlAxis.zero();
    }

    /**
     * Update control surface deflections toward commanded values.
     *
     * @param pitch    +1 nose up
     * @param roll     +1 roll right
     * @param yaw      +1 nose right
     * @param flaps    0..1 flap lever
     * @param spoilers 0..1
     * @param localRight vehicle-local right axis
     * @param localFwd   vehicle-local forward axis
     * @param localUp    vehicle-local up axis
     * @param authority 0..1 (hydraulics / damage)
     */
    public void updateControls(double pitch, double roll, double yaw, double flaps, double spoilers,
                               Vector3d localRight, Vector3d localFwd, Vector3d localUp,
                               double machOrSpeedFactor, double authority, double dt) {
        ControlRole role = params.role;
        double cmd = 0;
        if (role.isControlSurface()) {
            // desired moment M = pitch*R + roll*F - yaw*U ; deflection sign from geometry
            double m = 0;
            if (role.pitch) m += pitch * controlAxis.dot(localRight);
            if (role.roll) m += roll * controlAxis.dot(localFwd);
            if (role.yaw) m += -yaw * controlAxis.dot(localUp);
            if (role == ControlRole.ALL_AXES) {
                // all-axes surfaces only react along their dominant axis to avoid cross coupling
                m = pitch * controlAxis.dot(localRight) + roll * controlAxis.dot(localFwd) - yaw * controlAxis.dot(localUp);
            }
            cmd = Math.max(-1, Math.min(1, m * 1.6));
            if (params.invert) cmd = -cmd;
        }
        double target = cmd * Math.toRadians(params.controlMaxDeg);
        double rate = Math.toRadians(70.0) * params.responsiveness * Math.max(0.15, authority);
        controlDeflection = approach(controlDeflection, target * Math.max(0.2, authority), rate * dt);
        double flapTarget = role.flap ? flaps * Math.toRadians(params.flapMaxDeg) : 0.0;
        flapDeflection = approach(flapDeflection, flapTarget, Math.toRadians(12.0) * Math.max(0.15, authority) * dt);
        double spoilerTarget = role.spoiler ? spoilers * Math.max(params.spoilerMax, 0.0) : 0.0;
        spoiler = approach(spoiler, spoilerTarget, 1.5 * dt);
        if (params.type == WingType.VARIABLE_SWEEP) {
            // automatic sweep schedule by speed factor (0 = slow, 1 = fast)
            double t = Math.max(0, Math.min(1, machOrSpeedFactor));
            double target2 = Math.toRadians(params.sweepDeg + (params.sweepMaxDeg - params.sweepDeg) * t);
            currentSweepRad = approach(currentSweepRad, target2, Math.toRadians(8) * dt);
        } else {
            currentSweepRad = Math.toRadians(params.sweepDeg);
        }
    }

    private static double approach(double v, double target, double maxDelta) {
        if (v < target) return Math.min(target, v + maxDelta);
        return Math.max(target, v - maxDelta);
    }

    /**
     * Compute and apply the aerodynamic force.
     *
     * @param density  fluid density at the strip (air or water)
     * @param windX    world wind velocity (m/s)
     * @param mach     flight Mach number
     * @param damageFactor 0..1 effectiveness (damaged wings lift less)
     */
    public void applyForces(RigidBody body, Vector3d comLocal, double density, double windX, double windY, double windZ,
                            double mach, double damageFactor) {
        // world position of the strip centre
        double lx = localCenter.x - comLocal.x, ly = localCenter.y - comLocal.y, lz = localCenter.z - comLocal.z;
        body.localToWorld(lx, ly, lz, tmp);
        double wx = tmp.x, wy = tmp.y, wz = tmp.z;
        body.velocityAtPoint(wx, wy, wz, flow);
        flow.sub(windX, windY, windZ); // velocity of strip through the air (world)
        body.worldDirToLocal(flow.x, flow.y, flow.z, flow); // local
        double vf = flow.dot(forward);
        double vn = flow.dot(normal);
        double v2 = vf * vf + vn * vn;
        if (v2 < 1e-4 || area <= 0) {
            lastLift = lastDrag = 0;
            lastAlpha = 0;
            return;
        }
        double v = Math.sqrt(v2);
        double alphaFlow = Math.atan2(-vn, vf);
        double alpha = wrap(alphaFlow + Math.toRadians(params.incidenceDeg));
        if (params.type.dihedralDeg != 0 && Math.abs(normal.y) > 0.7) {
            // effective dihedral: the wing moving into a sideslip sees more angle of attack (restores wings level)
            double rx = forward.y * normal.z - forward.z * normal.y, ry = forward.z * normal.x - forward.x * normal.z, rz = forward.x * normal.y - forward.y * normal.x;
            double side = Math.signum(lx * rx + ly * ry + lz * rz);
            double vr = flow.x * rx + flow.y * ry + flow.z * rz;
            alpha += Math.toRadians(params.type.dihedralDeg) * (vr / Math.max(1, Math.abs(vf))) * side;
        }
        double hOverB = span > 0 ? groundHeight / Math.max(1.0, span) : 99;
        model.compute(params, alpha, controlDeflection, flapDeflection, spoiler, aspectRatio, currentSweepRad, mach, hOverB);
        double q = 0.5 * density * v2;
        double lift = q * area * model.cl * damageFactor;
        double drag = q * area * model.cd * (0.5 + 0.5 * damageFactor);
        // unit flow direction in (forward, normal) plane: d = (vf, vn)/v ; drag along -d ; lift along d rotated +90° toward normal
        double dfx = vf / v, dnx = vn / v;
        // lift dir in (f,n) basis = (-dn, df) where d = (df, dn)
        double liftF = -dnx, liftN = dfx;
        double fF = lift * liftF - drag * dfx;
        double fN = lift * liftN - drag * dnx;
        double fx = forward.x * fF + normal.x * fN;
        double fy = forward.y * fF + normal.y * fN;
        double fz = forward.z * fF + normal.z * fN;
        body.localDirToWorld(fx, fy, fz, tmp2);
        body.applyForceAtPoint(tmp2.x, tmp2.y, tmp2.z, wx, wy, wz);
        lastAlpha = alpha;
        lastLift = lift;
        lastDrag = drag;
        lastCl = model.cl;
        lastCd = model.cd;
        lastStalled = model.stalled && v > 8;
        lastDynamicPressure = q;
    }

    private static double wrap(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}
