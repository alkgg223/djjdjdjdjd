package com.vehicleworks.physics.sim;

/** Pilot commands applied to a vehicle simulation (server authoritative). */
public final class ControlInput {
    /** +1 nose up */
    public double pitch;
    /** +1 roll right */
    public double roll;
    /** +1 nose right */
    public double yaw;
    /** master throttle 0..1 */
    public double throttle;
    /** helicopter collective 0..1 */
    public double collective = 0.0;
    /** helicopter climb command −1..1 (assist: vertical-speed hold) */
    public double climb;
    /** ground vehicle drive −1 (reverse/brake) .. +1 (accelerate) */
    public double drive;
    /** ground vehicle steering −1 left .. +1 right */
    public double steer;
    public boolean brake;
    public boolean handbrake;
    /** B held: maximum braking (stronger brakes + direct ground deceleration) */
    public boolean hardBrake;
    /** flap lever 0..1 */
    public double flaps;
    public double spoilers;
    public boolean gearDown = true;
    /** −1 blow ballast .. +1 flood */
    public double ballast;
    /** −1 vent .. +1 burner / increase lift */
    public double lift;
    public boolean afterburner;
    /** marine propellers run astern */
    public boolean reverseThrust;
    /** stability augmentation / flight assist */
    public boolean assist = true;
    /** mouse-aim steering active; aim vector in world space */
    public boolean mouseAim;
    public double aimX, aimY, aimZ = 1;
    /** throttle per group (1, 2); negative = follow master */
    public double throttleGroupA = -1, throttleGroupB = -1;
    /** manual gear requests (consumed each tick) */
    public int gearShiftRequest;

    public void neutral() {
        pitch = roll = yaw = 0;
        drive = steer = 0;
        brake = false;
        handbrake = false;
        hardBrake = false;
        ballast = 0;
        lift = 0;
        mouseAim = false;
    }

    public double groupThrottle(int group) {
        if (group == 1 && throttleGroupA >= 0) return throttleGroupA;
        if (group == 2 && throttleGroupB >= 0) return throttleGroupB;
        return throttle;
    }
}
