package com.vehicleworks.client;

import com.vehicleworks.vehicle.VehicleEntity;
import net.minecraft.util.Mth;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side data of a vehicle: a jitter buffer of server snapshots interpolated with a
 * small playback delay, the cached render mesh and animation phases.
 */
public final class VehicleClientState {
    public static final double DELAY_TICKS = 2.0;

    private static final class Snap {
        long tick;
        final Vector3d pos = new Vector3d();
        final Quaterniond rot = new Quaterniond();
        final Vector3d vel = new Vector3d();
        final Vector3d ang = new Vector3d();
    }

    private final ArrayDeque<Snap> snaps = new ArrayDeque<>();
    private double playback = Double.NaN;
    private long latest = Long.MIN_VALUE;
    public VehicleMesh mesh;
    public final Map<Integer, Long> seatOf = new HashMap<>();
    /** per-engine / rotor / wheel animation phases */
    public float[] propPhase = new float[0], rotorPhase = new float[0], wheelPhase = new float[0];
    public float[] wheelSteer = new float[0], wheelComp = new float[0], wheelExt = new float[0];
    public float[] turretYaw = new float[0], turretPitch = new float[0];
    public double lastYaw = Double.NaN;
    public int telemetryAge;
    /** landing detection */
    public boolean wasGround = true;
    /** smoothed yoke animation */
    public float yokePitch, yokeRoll;
    public float lastVs;
    public Object sounds;

    public void addSnapshot(long tick, double x, double y, double z, float qx, float qy, float qz, float qw,
                            float vx, float vy, float vz, float wx, float wy, float wz) {
        if (tick <= latest) return;
        latest = tick;
        Snap s = new Snap();
        s.tick = tick;
        s.pos.set(x, y, z);
        s.rot.set(qx, qy, qz, qw).normalize();
        s.vel.set(vx, vy, vz);
        s.ang.set(wx, wy, wz);
        snaps.addLast(s);
        while (snaps.size() > 30) snaps.removeFirst();
        if (Double.isNaN(playback)) playback = tick - DELAY_TICKS;
    }

    /** Advance playback by one client tick and write the interpolated pose into the entity. */
    public void advance(VehicleEntity v) {
        if (snaps.isEmpty()) return;
        playback += 1.0;
        double target = latest - DELAY_TICKS;
        if (Math.abs(playback - target) > 8) playback = target;
        else playback += (target - playback) * 0.08;
        Snap a = null, b = null;
        for (Snap s : snaps) {
            if (s.tick <= playback) a = s;
            else { b = s; break; }
        }
        Vector3d pos = new Vector3d();
        Quaterniond rot = new Quaterniond();
        if (a == null) {
            Snap f = snaps.peekFirst();
            pos.set(f.pos);
            rot.set(f.rot);
            v.linearVelocity.set(f.vel);
            v.angularVelocity.set(f.ang);
        } else if (b == null) {
            // extrapolate a little (at most 3 ticks)
            double dt = Math.min(3, playback - a.tick) * 0.05;
            pos.set(a.vel).mul(dt).add(a.pos);
            rot.set(a.rot);
            double w = a.ang.length();
            if (w > 1e-6) rot.premul(new Quaterniond().fromAxisAngleRad(a.ang.x / w, a.ang.y / w, a.ang.z / w, w * dt));
            v.linearVelocity.set(a.vel);
            v.angularVelocity.set(a.ang);
        } else {
            double t = (playback - a.tick) / (double) (b.tick - a.tick);
            t = Mth.clamp(t, 0, 1);
            // cubic Hermite position interpolation using velocities
            double dt = (b.tick - a.tick) * 0.05;
            double t2 = t * t, t3 = t2 * t;
            double h00 = 2 * t3 - 3 * t2 + 1, h10 = t3 - 2 * t2 + t, h01 = -2 * t3 + 3 * t2, h11 = t3 - t2;
            pos.set(a.pos).mul(h00).fma(h10 * dt, a.vel).fma(h01, b.pos).fma(h11 * dt, b.vel);
            a.rot.slerp(b.rot, t, rot);
            v.linearVelocity.set(a.vel).lerp(b.vel, t);
            v.angularVelocity.set(a.ang).lerp(b.ang, t);
        }
        while (snaps.size() > 3 && snaps.peekFirst().tick < playback - 4) snaps.removeFirst();
        v.orientation.set(rot);
        v.setPos(pos.x, pos.y, pos.z);
    }

    public void dispose() {
        if (mesh != null) mesh.close();
        mesh = null;
    }
}
