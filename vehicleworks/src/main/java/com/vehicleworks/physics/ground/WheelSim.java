package com.vehicleworks.physics.ground;

import com.vehicleworks.physics.RigidBody;
import com.vehicleworks.physics.TerrainBoxes;
import org.joml.Vector3d;

/**
 * Ray-cast suspension wheel with spring-damper, tire slip dynamics, drive and brake
 * torque. Used for cars (driven/steered wheels) and aircraft landing gear.
 */
public final class WheelSim {
    public final WheelParams params;
    /** Structure-local mount point (suspension top). */
    public final Vector3d mountLocal = new Vector3d();
    /** Local axis pointing "down" along the suspension (usually -up). */
    public final Vector3d downLocal = new Vector3d(0, -1, 0);
    /** Local forward rolling direction at zero steer. */
    public final Vector3d forwardLocal = new Vector3d(0, 0, -1);
    public long voxel;

    // state
    public double omega;          // wheel spin rad/s
    public double spinAngle;      // for rendering
    public double steerAngle;     // rad
    public double compression;    // m
    public double lastCompression;
    public boolean grounded;
    public double load;           // N normal
    public double slipRatio, slipAngle;
    public double driveTorque, brakeInput;
    public float surfaceFriction = 0.9f;
    public boolean retracted;
    public double extension = 1.0; // 1 = deployed, 0 = retracted (animated)
    public boolean skidding;
    /** number of wheels sharing the vehicle mass (set by the simulation) */
    public int wheelCount = 4;

    private final Vector3d tmp = new Vector3d();
    private final Vector3d mountW = new Vector3d();
    private final Vector3d downW = new Vector3d();
    private final Vector3d fwdW = new Vector3d();
    private final Vector3d sideW = new Vector3d();
    private final Vector3d vel = new Vector3d();
    private final double[] hitNormal = new double[3];

    public WheelSim(WheelParams params) {
        this.params = params;
    }

    /**
     * Simulate one substep. Terrain ray cast, suspension and tire forces are applied to the body.
     *
     * @param frictionAt callback giving the friction of the block under a world point (may be null)
     */
    public void step(RigidBody body, Vector3d comLocal, TerrainBoxes terrain, double dt, FrictionLookup frictionAt) {
        WheelParams p = params;
        double ext = extension;
        if (retracted || ext < 0.95) {
            grounded = false;
            load = 0;
            omega *= Math.max(0, 1 - 2 * dt);
            spinAngle += omega * dt;
            return;
        }
        body.localToWorld(mountLocal.x - comLocal.x, mountLocal.y - comLocal.y, mountLocal.z - comLocal.z, mountW);
        body.localDirToWorld(downLocal.x, downLocal.y, downLocal.z, downW);
        // cast from half a block above the mount so the ray never starts inside the ground
        final double lift = 0.5;
        double ox = mountW.x - downW.x * lift, oy = mountW.y - downW.y * lift, oz = mountW.z - downW.z * lift;
        double maxLen = p.restLength + p.radius;
        double hitRaw = terrain.isEmpty() ? -1 : terrain.raycast(ox, oy, oz, downW.x, downW.y, downW.z, maxLen + lift, hitNormal);
        double hit = hitRaw < 0 ? -1 : Math.max(0, hitRaw - lift);
        lastCompression = compression;
        if (hit < 0) {
            grounded = false;
            compression = 0;
            load = 0;
            skidding = false;
            double inertia = p.inertia();
            omega += (driveTorque - Math.signum(omega) * brakeInput * p.brakeTorque) / inertia * dt;
            omega *= Math.max(0, 1 - 0.5 * dt);
            spinAngle += omega * dt;
            return;
        }
        grounded = true;
        compression = maxLen - hit;
        double compVel = (compression - lastCompression) / dt;
        double springF = p.stiffness * compression + p.damping * compVel;
        if (springF < 0) springF = 0;
        // progressive bump stop, bounded so a hard landing cannot launch the vehicle
        if (compression > p.restLength) springF += p.stiffness * 3 * (compression - p.restLength);
        double staticLoad = body.mass * 9.81 / Math.max(1, wheelCount);
        springF = Math.min(springF, staticLoad * 6.0);
        load = springF;
        // contact point
        double cx = mountW.x + downW.x * hit, cy = mountW.y + downW.y * hit, cz = mountW.z + downW.z * hit;
        // suspension force along -down (body up)
        body.applyForceAtPoint(-downW.x * springF, -downW.y * springF, -downW.z * springF, cx, cy, cz);

        // wheel axes (apply steering about suspension axis)
        double cs = Math.cos(steerAngle), sn = Math.sin(steerAngle);
        // local forward rotated about local up (-down)
        double ux = -downLocal.x, uy = -downLocal.y, uz = -downLocal.z;
        // Rodrigues rotation of forwardLocal around u by steerAngle (positive = turn right => rotate by -angle about up)
        double fx = forwardLocal.x, fy = forwardLocal.y, fz = forwardLocal.z;
        double a = -steerAngle;
        double ca = Math.cos(a), sa = Math.sin(a);
        double dot = ux * fx + uy * fy + uz * fz;
        double crx = uy * fz - uz * fy, cry = uz * fx - ux * fz, crz = ux * fy - uy * fx;
        double rfx = fx * ca + crx * sa + ux * dot * (1 - ca);
        double rfy = fy * ca + cry * sa + uy * dot * (1 - ca);
        double rfz = fz * ca + crz * sa + uz * dot * (1 - ca);
        body.localDirToWorld(rfx, rfy, rfz, fwdW);
        // project forward onto ground plane (normal from hit)
        double gnx = hitNormal[0], gny = hitNormal[1], gnz = hitNormal[2];
        if (gnx == 0 && gny == 0 && gnz == 0) { gnx = -downW.x; gny = -downW.y; gnz = -downW.z; }
        double fd = fwdW.x * gnx + fwdW.y * gny + fwdW.z * gnz;
        fwdW.sub(gnx * fd, gny * fd, gnz * fd);
        if (fwdW.lengthSquared() < 1e-8) return;
        fwdW.normalize();
        // side = ground normal × forward (points left); we want right = forward × normal
        sideW.set(fwdW).cross(gnx, gny, gnz).normalize(); // right-hand: forward x up = right

        body.velocityAtPoint(cx, cy, cz, vel);
        double vLong = vel.dot(fwdW);
        double vLat = vel.dot(sideW);

        double mu = surfaceFriction * p.gripScale;
        if (frictionAt != null) mu = frictionAt.friction(cx, cy - 0.05, cz) * p.gripScale;
        double fzN = Math.min(load, 4.0 * body.mass * 9.81);

        double r = p.radius;
        double inertia = p.inertia();
        double denom = Math.max(Math.abs(vLong), 3.0);
        slipRatio = (omega * r - vLong) / denom;
        slipAngle = Math.atan2(vLat, Math.max(Math.abs(vLong), 1.5));

        double fxN = TireModel.longitudinal(slipRatio);
        double fyN = -TireModel.lateral(slipAngle);
        double scale = TireModel.frictionCircleScale(fxN, fyN);
        double fLong = fxN * scale * mu * fzN;
        double fLat = fyN * scale * mu * fzN;
        // low-speed lateral damping to avoid creep
        double speed = Math.hypot(vLong, vLat);
        if (speed < 2.0) {
            double massShare = body.mass * 0.25;
            double damp = -vLat * massShare / dt * 0.5;
            double lim = mu * fzN;
            fLat = Math.max(-lim, Math.min(lim, fLat * (speed / 2.0) + damp * (1 - speed / 2.0)));
        }
        // never apply more lateral force than needed to cancel the wheel's share of lateral motion
        // in one substep (prevents explicit-integration overshoot of the very stiff tire)
        double share = body.mass / Math.max(1, wheelCount);
        double maxLat = share * Math.abs(vLat) / dt * 0.8;
        if (Math.abs(fLat) > maxLat) fLat = Math.signum(fLat) * maxLat;
        skidding = Math.abs(slipRatio) > 0.25 || Math.abs(slipAngle) > 0.18;

        // wheel spin dynamics — linearised implicit update (tire torque is very stiff)
        double brakeT = p.braked ? brakeInput * p.brakeTorque : 0;
        double eps = 1e-3;
        double slope = Math.max(0.0, (TireModel.longitudinal(slipRatio + eps) - TireModel.longitudinal(slipRatio - eps)) / (2 * eps));
        double kF = slope * scale * mu * fzN * r / denom; // dF/dω
        double newOmega = omega + dt / inertia * (driveTorque - fLong * r) / (1.0 + dt * r * kF / inertia);
        fLong += kF * (newOmega - omega);
        double lim0 = mu * fzN;
        fLong = Math.max(-lim0, Math.min(lim0, fLong));
        // brakes oppose rotation but cannot reverse it
        double brakeDelta = brakeT / inertia * dt;
        if (Math.abs(newOmega) <= brakeDelta) newOmega = 0;
        else newOmega -= Math.signum(newOmega) * brakeDelta;
        omega = newOmega;
        if (brakeInput > 0.95 && Math.abs(vLong) < 0.3 && p.braked) {
            // parking: hold the vehicle with static friction
            omega = 0;
            double hold = -vLong * body.mass * 0.25 / dt * 0.5;
            double lim = mu * fzN;
            fLong = Math.max(-lim, Math.min(lim, hold));
        }
        spinAngle += omega * dt;

        // rolling resistance (much higher on rough ground unless the tyre is made for it)
        groundRoughness = frictionAt != null ? frictionAt.roughness(cx, cy - 0.05, cz) : 0;
        double crr = p.rollingResistance * (1 + 6.0 * groundRoughness * p.roughSensitivity);
        double fRoll = crr * fzN;
        double stopAll = share * Math.abs(vLong) / dt * 0.5;
        fRoll = -Math.signum(vLong) * Math.min(fRoll, stopAll);
        fLong += fRoll;

        body.applyForceAtPoint(fwdW.x * fLong + sideW.x * fLat, fwdW.y * fLong + sideW.y * fLat, fwdW.z * fLong + sideW.z * fLat, cx, cy, cz);
    }

    @FunctionalInterface
    public interface FrictionLookup {
        float friction(double x, double y, double z);

        /** 0 = smooth runway … ~2.5 = deep sand / snow */
        default float roughness(double x, double y, double z) { return 0; }
    }

    /** roughness of the ground under the tyre (last step) */
    public float groundRoughness;
}
