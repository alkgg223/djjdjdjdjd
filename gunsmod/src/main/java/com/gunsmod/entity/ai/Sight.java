package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * What a soldier or a crew can see: only what lies in front of the eyes, the gun sight or the cockpit. Someone behind
 * a soldier stays unseen until the soldier turns its head; a tank only spots what its gun points near.
 */
public final class Sight {
    private Sight() {}

    /** half angles of view, degrees: a soldier's eyes, a turret's sights, a helicopter cockpit, a jet's nose */
    public static final float SOLDIER = 65f, TURRET = 35f, HELI = 75f, JET = 70f;
    /** this close a soldier notices anyone (footsteps, a touch) */
    public static final double TOUCH = 3.0;

    /** is {@code p} within {@code half} degrees either side of the heading {@code yaw}, or nearer than {@code near} */
    public static boolean inView(Vec3 eye, float yaw, float half, double near, Vec3 p) {
        double dx = p.x - eye.x, dz = p.z - eye.z;
        if (dx * dx + dz * dz < near * near) return true;
        float to = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        return Math.abs(Mth.wrapDegrees(to - yaw)) <= half;
    }

    /** where the soldier's head points */
    public static boolean soldier(BotEntity bot, Entity e) {
        return inView(bot.getEyePosition(), bot.getYHeadRot(), SOLDIER, TOUCH, e.position());
    }

    /** within {@code half} degrees of a direction in space (a jet's nose) */
    public static boolean inCone(Vec3 eye, Vec3 dir, float half, Vec3 p) {
        Vec3 to = p.subtract(eye);
        double len = to.length();
        if (len < 1e-3) return true;
        return to.scale(1 / len).dot(dir.normalize()) >= Math.cos(Math.toRadians(half));
    }
}
