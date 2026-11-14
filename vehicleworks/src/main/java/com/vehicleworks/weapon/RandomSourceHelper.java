package com.vehicleworks.weapon;

import net.minecraft.util.RandomSource;
import org.joml.Vector3d;

final class RandomSourceHelper {
    private RandomSourceHelper() {}

    /** Rotate a unit vector randomly within a cone of the given half-angle. */
    static void jitter(Vector3d dir, double halfAngle, RandomSource r) {
        if (halfAngle <= 0) return;
        Vector3d a = Math.abs(dir.y) < 0.9 ? new Vector3d(0, 1, 0) : new Vector3d(1, 0, 0);
        Vector3d u = new Vector3d(dir).cross(a).normalize();
        Vector3d v = new Vector3d(dir).cross(u).normalize();
        double ang = r.nextDouble() * Math.PI * 2;
        double rad = halfAngle * Math.sqrt(r.nextDouble());
        dir.add(u.mul(Math.cos(ang) * Math.tan(rad))).add(v.mul(Math.sin(ang) * Math.tan(rad))).normalize();
    }
}
