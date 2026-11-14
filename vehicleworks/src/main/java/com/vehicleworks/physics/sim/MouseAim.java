package com.vehicleworks.physics.sim;

import com.vehicleworks.physics.RigidBody;
import org.joml.Vector3d;

/**
 * "Mouse aim" instructor: converts a desired world-space nose direction into pitch, roll and
 * yaw commands with bank-to-turn behaviour, like modern flight games. Touch friendly.
 */
public final class MouseAim {
    private final Vector3d fw = new Vector3d(), uw = new Vector3d(), rw = new Vector3d(), d = new Vector3d();

    /** Writes pitch/roll/yaw into out[0..2]. */
    public void compute(RigidBody body, Vector3d fwdL, Vector3d upL, Vector3d rightL, double ax, double ay, double az, double[] out) {
        body.localDirToWorld(fwdL.x, fwdL.y, fwdL.z, fw);
        body.localDirToWorld(upL.x, upL.y, upL.z, uw);
        body.localDirToWorld(rightL.x, rightL.y, rightL.z, rw);
        d.set(ax, ay, az);
        if (d.lengthSquared() < 1e-6) { out[0] = out[1] = out[2] = 0; return; }
        d.normalize();
        double df = d.dot(fw), du = d.dot(uw), dr = d.dot(rw);
        double pitchErr = Math.atan2(du, Math.max(1e-3, df));
        double yawErr = Math.atan2(dr, df);
        double bank = Math.atan2(-rw.y, uw.y);
        double p = body.angularVelocity.dot(fw);    // roll rate (+ = right)
        double q = body.angularVelocity.dot(rw);    // pitch rate (+ = nose up)
        double r = -body.angularVelocity.dot(uw);   // yaw rate (+ = nose right)
        double desiredBank = clamp(yawErr * 2.4, -Math.toRadians(75), Math.toRadians(75));
        if (Math.abs(yawErr) < Math.toRadians(4)) desiredBank *= Math.abs(yawErr) / Math.toRadians(4);
        double roll = clamp(2.2 * (desiredBank - bank) - 0.9 * p, -1, 1);
        // pitch in the banked frame: pull toward target projected on the up axis
        double pitch = clamp(3.0 * pitchErr - 1.1 * q, -1, 1);
        double yaw = clamp(2.0 * yawErr - 0.8 * r, -1, 1);
        out[0] = pitch;
        out[1] = roll;
        out[2] = yaw;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
