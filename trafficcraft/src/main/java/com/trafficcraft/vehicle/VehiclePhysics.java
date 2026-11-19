package com.trafficcraft.vehicle;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared vehicle dynamics: kinematic bicycle model with drivetrain, brakes, resistance, gears and collisions. */
public final class VehiclePhysics {
    public static final float DT = 0.05f;
    private static final float[] G6 = {0.13f, 0.25f, 0.38f, 0.53f, 0.72f, 1.0f};
    private static final float[] G8 = {0.09f, 0.17f, 0.26f, 0.36f, 0.48f, 0.61f, 0.78f, 1.0f};

    public static float[] gears(VehicleSpec s) {
        return s.isHeavy() ? G8 : G6;
    }

    public static void step(VehicleEntity v, VehicleInput in) {
        VehicleSpec s = v.spec();
        boolean engine = v.has(VehicleEntity.F_ENGINE);
        float maxV = s.maxSpeedKmh / 3.6f;
        float maxRev = Math.min(8f, maxV * 0.25f);
        float spd = v.speed;
        float fwd = Mth.clamp(in.forward(), -1, 1);
        float want = 0, brake = 0;
        if (fwd > 0.01f) {
            if (spd < -0.4f) brake = fwd;
            else want = fwd;
        } else if (fwd < -0.01f) {
            if (spd > 0.4f) brake = -fwd;
            else want = fwd;
        }
        boolean manual = v.has(VehicleEntity.F_MANUAL);
        float[] G = gears(s);
        if (want < 0) v.gear = -1;
        else if (want > 0 && v.gear <= 0) v.gear = 1;
        if (!engine) want = 0;
        float target = Math.abs(want);
        v.throttle += (target - v.throttle) * (target > v.throttle ? 0.22f : 0.45f);

        float a = 0;
        if (want < 0 && v.gear < 0) {
            a = -s.accel * 0.6f * v.throttle * Math.max(0, 1 - (spd / maxRev) * (spd / maxRev));
        } else if (want > 0 && v.gear >= 1) {
            float frac = Math.max(0, spd) / maxV;
            float mul = manual ? 1.5f - 0.1f * v.gear : 1f;
            a = s.accel * v.throttle * Math.max(0, 1 - (float) Math.pow(frac, 2.2)) * mul;
            if (manual && spd > G[Math.min(v.gear, G.length) - 1] * maxV * 1.02f) a = Math.min(a, 0);
        }
        float sg = Math.signum(spd);
        float resist = 0.22f + 0.45f * (spd / maxV) * (spd / maxV) + (engine && want == 0 ? 0.5f : 0f);
        a -= sg * resist;
        a -= sg * brake * s.brake;
        if (in.handbrake()) a -= sg * s.brake * 0.55f;
        float ns = spd + a * DT;
        if (sg != 0 && Math.signum(ns) != sg && Math.signum(want) != Math.signum(ns)) ns = 0;
        if (Math.abs(ns) < 0.05f && want == 0) ns = 0;

        // steering: speed sensitive lock and rate
        float maxSteer = 36f / (1f + Math.abs(ns) / 9f);
        float steerTarget = Mth.clamp(in.left(), -1, 1) * maxSteer;
        float rate = (Math.abs(steerTarget) > Math.abs(v.steer) ? 110f : 170f) * DT;
        v.steer = approach(v.steer, steerTarget, rate);
        float yawRate = (float) Math.toDegrees(ns * Math.tan(Math.toRadians(v.steer)) / s.wheelbase);
        if (in.handbrake() && Math.abs(ns) > 4f) {
            yawRate *= 1.4f;
            ns *= 0.985f;
        }
        float yaw0 = v.getYRot();
        float yaw1 = yaw0 - yawRate * DT;

        // vertical
        float vy = v.onGround() ? -0.08f : Math.max(-2.5f, v.vy() - 0.06f);
        Vec3 pos = v.position();
        double r = Math.toRadians(yaw1);
        Vec3 disp = new Vec3(-Math.sin(r) * ns * DT, 0, Math.cos(r) * ns * DT);
        int dir = ns > 0.01f ? 1 : ns < -0.01f ? -1 : 0;
        if (dir != 0 && v.blockedAt(pos.add(disp), yaw1, dir)) {
            if (Math.abs(ns) > 6f) v.CLIENT_EVENT.accept(v, "crash");
            ns = 0;
            disp = Vec3.ZERO;
        }
        // other vehicles: never drive through them
        AABB area = v.getBoundingBox().inflate(s.length + 1);
        for (VehicleEntity o : v.level().getEntitiesOfClass(VehicleEntity.class, area, e -> e != v && !e.isRemoved())) {
            Vec3 np = pos.add(disp);
            if (v.overlaps(o, np, yaw1)) {
                boolean already = v.overlaps(o, pos, yaw0);
                boolean separating = already && np.distanceToSqr(o.position()) > pos.distanceToSqr(o.position());
                if (!separating) {
                    if (Math.abs(ns) > 5f) v.CLIENT_EVENT.accept(v, "crash");
                    ns *= -0.15f;
                    disp = Vec3.ZERO;
                    yaw1 = yaw0;
                    yawRate = 0;
                }
            }
        }
        v.setYRot(yaw1);
        v.moveBody(new Vec3(disp.x, vy, disp.z));
        if (v.horizontalCollision && Math.abs(ns) > 0.5f) {
            if (Math.abs(ns) > 6f) v.CLIENT_EVENT.accept(v, "crash");
            ns *= 0.3f;
        }
        v.setVy(v.onGround() ? 0 : vy);
        float acc = (ns - spd) / DT;
        v.speed = ns;

        // visual body motion
        float roll = Mth.clamp((float) (-ns * Math.toRadians(yawRate)) * 0.9f, -4f, 4f);
        float pitch = Mth.clamp(-acc * 0.35f, -3f, 3f);
        v.bodyRoll += (roll - v.bodyRoll) * 0.15f;
        v.bodyPitch += (pitch - v.bodyPitch) * 0.15f;

        // gears & rpm
        float idle = s.idleRpm, red = s.redRpm;
        float frac = Math.abs(ns) / maxV;
        if (!manual && v.gear >= 1) {
            int g = v.lastAutoGear();
            while (g < G.length && frac > G[g - 1] * 0.97f) g++;
            while (g > 1 && frac < G[g - 2] * 0.82f) g--;
            if (g != v.lastAutoGear()) v.CLIENT_EVENT.accept(v, "shift");
            v.setLastAutoGear(g);
            v.gear = g;
        }
        float rpmT;
        if (!engine) rpmT = 0;
        else if (v.gear < 0) rpmT = idle + Math.min(1, Math.abs(ns) / maxRev) * (red * 0.55f - idle) + v.throttle * 300;
        else if (Math.abs(ns) < 0.6f) rpmT = idle + v.throttle * (red * 0.4f - idle);
        else {
            int gi = Math.min(Math.max(v.gear, 1), G.length);
            float lo = gi == 1 ? 0 : G[gi - 2];
            float hi = G[gi - 1];
            float f = Mth.clamp((frac - lo * (manual ? 0 : 0.8f)) / (hi - lo * (manual ? 0 : 0.8f)), 0, 1.05f);
            rpmT = idle * 1.1f + f * (red * 0.92f - idle * 1.1f) * (0.8f + 0.2f * v.throttle);
        }
        if (v.has(VehicleEntity.F_STARTING)) rpmT = idle * 0.3f;
        v.rpm += (rpmT - v.rpm) * 0.25f;
        v.setFlag(VehicleEntity.F_BRAKE, brake > 0.05f || (in.handbrake() && v.getControllingPassenger() != null) || (Math.abs(ns) < 0.05f && fwd < 0));
        v.setFlag(VehicleEntity.F_REVERSE, v.gear < 0 && engine);
        v.setFlag(VehicleEntity.F_HANDBRAKE, in.handbrake());
    }

    public static float approach(float cur, float target, float step) {
        if (cur < target) return Math.min(target, cur + step);
        return Math.max(target, cur - step);
    }
}
