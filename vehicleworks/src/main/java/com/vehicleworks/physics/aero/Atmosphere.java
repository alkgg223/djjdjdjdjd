package com.vehicleworks.physics.aero;

/** International Standard Atmosphere (troposphere + lower stratosphere). */
public final class Atmosphere {
    public static final double RHO0 = 1.225;
    public static final double T0 = 288.15;
    public static final double P0 = 101325.0;
    public static final double LAPSE = 0.0065;
    public static final double R = 287.05;
    public static final double G = 9.80665;
    public static final double GAMMA = 1.4;
    public static final double WATER_DENSITY = 1000.0;
    public static final double LAVA_DENSITY = 3100.0;

    private Atmosphere() {}

    public static double temperature(double h) {
        h = Math.max(-1000, h);
        return h < 11000 ? T0 - LAPSE * h : 216.65;
    }

    public static double pressure(double h) {
        h = Math.max(-1000, h);
        if (h < 11000) return P0 * Math.pow(temperature(h) / T0, G / (LAPSE * R));
        double p11 = P0 * Math.pow(216.65 / T0, G / (LAPSE * R));
        return p11 * Math.exp(-G * (h - 11000) / (R * 216.65));
    }

    public static double density(double h) {
        return pressure(h) / (R * temperature(h));
    }

    public static double speedOfSound(double h) {
        return Math.sqrt(GAMMA * R * temperature(h));
    }
}
