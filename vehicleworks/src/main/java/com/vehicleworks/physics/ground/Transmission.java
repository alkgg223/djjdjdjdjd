package com.vehicleworks.physics.ground;

/** Gearbox with automatic or manual shifting and a final drive. */
public final class Transmission {
    public double[] ratios = {3.6, 2.3, 1.6, 1.2, 0.95, 0.78};
    public double reverseRatio = 3.4;
    public double finalDrive = 3.9;
    public double efficiency = 0.9;
    public boolean automatic = true;
    /** 0 = neutral, -1 = reverse, 1..n forward */
    public int gear = 1;
    public double shiftTimer;
    public double shiftUpRpm = 0.86;  // fraction of max rpm
    public double shiftDownRpm = 0.38;

    public double ratio() {
        if (gear == 0) return 0;
        if (gear < 0) return -reverseRatio * finalDrive;
        return ratios[Math.min(gear, ratios.length) - 1] * finalDrive;
    }

    public void shiftUp() {
        if (gear < ratios.length) { gear++; shiftTimer = 0.25; }
    }

    public void shiftDown() {
        if (gear > -1) { gear--; shiftTimer = 0.25; }
    }

    /** Automatic shift logic. throttleSign: +1 wants forward, -1 wants reverse. */
    public void autoShift(double rpmFraction, double forwardSpeed, int throttleSign, double dt) {
        if (shiftTimer > 0) { shiftTimer -= dt; return; }
        if (!automatic) return;
        if (throttleSign < 0 && forwardSpeed < 0.8 && gear > 0) { gear = -1; shiftTimer = 0.4; return; }
        if (throttleSign > 0 && forwardSpeed > -0.8 && gear <= 0) { gear = 1; shiftTimer = 0.4; return; }
        if (gear >= 1) {
            if (rpmFraction > shiftUpRpm && gear < ratios.length) shiftUp();
            else if (rpmFraction < shiftDownRpm && gear > 1) shiftDown();
        }
    }
}
