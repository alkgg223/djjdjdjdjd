package com.vehicleworks.client;

import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;

/** Camera shake for the rider: runway rumble that grows with ground speed, and landing jolts. */
public final class GroundFx {
    private static float jolt;      // 0..1, decays
    private static long last = System.nanoTime();

    private GroundFx() {}

    public static void kick(float strength) {
        jolt = Math.max(jolt, Math.min(1f, strength));
    }

    /** Returns {pitch, yaw, roll} offsets in degrees. */
    public static float[] offsets(VehicleEntity v) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        jolt = Math.max(0, jolt - dt * 0.9f);
        VehicleTelemetry t = v.telemetry;
        float rumble = 0;
        if (t.onGround && t.wheelKeys.length > 0) rumble = Math.min(1f, Math.max(0, t.groundSpeed - 2) / 55f);
        double s = now / 1e9;
        float a = 0.7f * rumble + 5.5f * jolt * jolt + 1.2f * jolt;
        if (a < 1e-3f) return new float[]{0, 0, 0};
        // layered sines at unrelated frequencies make a cheap noise
        float p = (float) (Math.sin(s * 47) * 0.6 + Math.sin(s * 83 + 1.3) * 0.4);
        float y = (float) (Math.sin(s * 59 + 2.1) * 0.5 + Math.sin(s * 101) * 0.3);
        float r = (float) (Math.sin(s * 37 + 0.7) * 0.6 + Math.sin(s * 71 + 2.9) * 0.4);
        return new float[]{p * a, y * a * 0.5f, r * a * 0.8f};
    }
}
