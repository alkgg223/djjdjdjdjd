package com.realweather.vehicle;

/** What each vehicle is like: size, performance, how much wind it takes, and its equipment. */
public enum VehicleType {
    SEDAN("sedan", 1400F, 150F, 0.0105F, 0.026F, 34F, 2.75F, 1.56F, 0.05F, 0.48F, 60F, 4, false, false,
            VehicleGeometry.SEDAN_WHEELS, VehicleGeometry.SEDAN_WHEEL_R, VehicleGeometry.SEDAN_SEATS, VehicleGeometry.SEDAN_SIZE,
            new int[]{0xB3261E, 0x1E4FA8, 0xE8E8E2, 0x2B2D30, 0x8A9096, 0x2E6B3A}),
    PICKUP("pickup", 2300F, 140F, 0.0095F, 0.024F, 33F, 3.3F, 1.72F, 0.04F, 0.64F, 80F, 2, true, false,
            VehicleGeometry.PICKUP_WHEELS, VehicleGeometry.PICKUP_WHEEL_R, VehicleGeometry.PICKUP_SEATS, VehicleGeometry.PICKUP_SIZE,
            new int[]{0xF2F2EE, 0x23262A, 0xA02018, 0x2B4F7A, 0x6E7468}),
    INTERCEPT("intercept", 6800F, 115F, 0.0072F, 0.02F, 30F, 3.6F, 2.0F, 0.025F, 0.82F, 160F, 2, true, true,
            VehicleGeometry.INTERCEPT_WHEELS, VehicleGeometry.INTERCEPT_WHEEL_R, VehicleGeometry.INTERCEPT_SEATS, VehicleGeometry.INTERCEPT_SIZE,
            new int[]{0x5C5F5C});

    public final String id;
    public final float mass;
    /** Top speed in km/h. */
    public final float topSpeedKmh;
    /** Engine acceleration from rest, blocks per tick per tick. */
    public final float accel;
    /** Braking deceleration, blocks per tick per tick. */
    public final float brake;
    public final float maxSteerDeg;
    public final float wheelbase;
    public final float track;
    /** How readily the wind drags it along (per tick fraction). */
    public final float windGrip;
    /** Tornado wind (blocks per tick) above which it is lifted off the ground. */
    public final float liftWind;
    public final float health;
    public final int seats;
    /** Instruments that measure a tornado (pickup: mobile Doppler and anemometer; intercept: in-situ probe). */
    public final boolean instruments;
    /** Hydraulic ground spikes and armour skirts. */
    public final boolean spikes;
    public final float[][] wheels;
    public final float wheelRadius;
    public final float[][] seatPoints;
    public final float[] size;
    public final int[] colors;

    VehicleType(String id, float mass, float topSpeedKmh, float accel, float brake, float maxSteerDeg, float wheelbase, float track, float windGrip,
                float liftWind, float health, int seats, boolean instruments, boolean spikes, float[][] wheels, float wheelRadius, float[][] seatPoints,
                float[] size, int[] colors) {
        this.id = id;
        this.mass = mass;
        this.topSpeedKmh = topSpeedKmh;
        this.accel = accel;
        this.brake = brake;
        this.maxSteerDeg = maxSteerDeg;
        this.wheelbase = wheelbase;
        this.track = track;
        this.windGrip = windGrip;
        this.liftWind = liftWind;
        this.health = health;
        this.seats = Math.min(seats, seatPoints.length);
        this.instruments = instruments;
        this.spikes = spikes;
        this.wheels = wheels;
        this.wheelRadius = wheelRadius;
        this.seatPoints = seatPoints;
        this.size = size;
        this.colors = colors;
    }

    /** Top speed in blocks per tick. */
    public double maxSpeed() {
        return topSpeedKmh / 72.0;
    }
}
