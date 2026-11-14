package com.realweather.vehicle;

/** What each vehicle is like: size, performance, how much wind it takes, its engine and its equipment. */
public enum VehicleType {
    SEDAN("sedan", VehicleGeometry.SEDAN, 1450F, 185F, 0.0115F, 0.026F, 34F, 0.05F, 0.48F, 60F, 4, false, false, Engine.PETROL,
            new int[]{0x9E1B1E, 0x1E4FA8, 0xE8E8E2, 0x2B2D30, 0x8A9096, 0x2E6B3A}),
    PICKUP("pickup", VehicleGeometry.PICKUP, 2400F, 165F, 0.0105F, 0.024F, 33F, 0.04F, 0.64F, 80F, 4, true, false, Engine.DIESEL,
            new int[]{0xF2F2EE, 0x23262A, 0xA02018, 0x2B4F7A, 0x6E7468}),
    INTERCEPT("intercept", VehicleGeometry.INTERCEPT, 6800F, 115F, 0.0072F, 0.02F, 30F, 0.025F, 0.82F, 160F, 2, true, true, Engine.DIESEL,
            new int[]{0x5C5F5C}),
    SUV("suv", VehicleGeometry.SUV, 2650F, 180F, 0.0112F, 0.025F, 32F, 0.04F, 0.68F, 90F, 4, false, false, Engine.V8,
            new int[]{0xF2F2F0, 0x16181B, 0x5A5E63, 0x7A1414, 0x1D2E4A}),
    COUPE("coupe", VehicleGeometry.COUPE, 1700F, 250F, 0.017F, 0.03F, 33F, 0.05F, 0.5F, 65F, 2, false, false, Engine.MUSCLE,
            new int[]{0xF2B705, 0xC4161C, 0x1A1C1F, 0x2F6FD6, 0xF2F2F0}),
    SPORT("sport", VehicleGeometry.SPORT, 1900F, 250F, 0.0175F, 0.032F, 33F, 0.05F, 0.5F, 70F, 4, false, false, Engine.SPORT,
            new int[]{0x17191C, 0x26478C, 0x6B7077, 0xE9E9E6, 0x3E4F3A});

    /** The engine decides the start-up, the running note and whether it crackles on the overrun. */
    public enum Engine {
        PETROL(false), DIESEL(false), V8(false), MUSCLE(true), SPORT(true);

        /** Pops and flames from the exhaust when the throttle snaps shut at high revs. */
        public final boolean backfire;

        Engine(boolean backfire) {
            this.backfire = backfire;
        }
    }

    public final String id;
    public final VehicleGeometry.Geo geo;
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
    public final Engine engine;
    public final float[][] wheels;
    public final float wheelRadius;
    public final float[][] seatPoints;
    public final float[] size;
    /** Door hinges, one row (front, rear) per entry; each row has a left and a right door. */
    public final float[][] doorRows;
    public final int[] colors;

    VehicleType(String id, VehicleGeometry.Geo geo, float mass, float topSpeedKmh, float accel, float brake, float maxSteerDeg, float windGrip,
                float liftWind, float health, int seats, boolean instruments, boolean spikes, Engine engine, int[] colors) {
        this.id = id;
        this.geo = geo;
        this.mass = mass;
        this.topSpeedKmh = topSpeedKmh;
        this.accel = accel;
        this.brake = brake;
        this.maxSteerDeg = maxSteerDeg;
        this.wheels = geo.wheels();
        this.wheelbase = Math.abs(wheels[2][2] - wheels[0][2]);
        this.track = Math.abs(wheels[1][0] - wheels[0][0]);
        this.windGrip = windGrip;
        this.liftWind = liftWind;
        this.health = health;
        this.seatPoints = geo.seats();
        this.seats = Math.min(seats, seatPoints.length);
        this.instruments = instruments;
        this.spikes = spikes;
        this.engine = engine;
        this.wheelRadius = geo.wheelR();
        this.size = geo.size();
        this.doorRows = geo.doors();
        this.colors = colors;
    }

    /** Top speed in blocks per tick. */
    public double maxSpeed() {
        return topSpeedKmh / 72.0;
    }
}
