package com.gunsmod.entity;

/**
 * The warships (models from tools/ships_mesh.py, model units 0.25 block). Sizes are scaled down like everything else:
 * a destroyer is 36 blocks long. waterline: the model height that sits at the water surface; gun: the main gun mount's
 * pivot (model units) and how far in front of it the muzzle is.
 */
public enum ShipType {
    //        model             len  beam  wl   hp    bullet speed accel turn   gun pivot          muzzle reload shell dmgA dmgS splash range sam ciws asm torp
    PATROL("ship_patrol", 48, 13, 5.0, 160, 0.30f, 0.95, 0.030, 3.0f, 8.0, 9.0, -10.0, -5.4, 0.7, 8, 6.0, 14f, 12f, 1.2f, 120, 0, false, 0, 0,
            new double[][]{{6.0, 8.2, 2.0}, {10.0, 8.2, 2.0}, {5.0, 8.2, 14.0}, {11.0, 8.2, 14.0}}),
    FRIGATE("ship_frigate", 120, 20, 7.0, 700, 0.10f, 0.62, 0.012, 1.4f, 8.0, 13.0, -34.0, -9.0, 1.0, 30, 5.0, 40f, 30f, 2.6f, 200, 16, true, 4, 0,
            new double[][]{{8.0, 22.0, -11.0}, {5.0, 12.2, 34.0}, {11.0, 12.2, 34.0}, {5.0, 12.2, 40.0}, {11.0, 12.2, 40.0}}),
    DESTROYER("ship_destroyer", 144, 24, 8.0, 900, 0.08f, 0.66, 0.011, 1.2f, 8.0, 15.0, -44.0, -11.4, 1.2, 45, 5.2, 60f, 40f, 3.4f, 240, 32, true, 8, 0,
            new double[][]{{8.0, 29.0, -15.0}, {5.0, 14.2, 44.0}, {11.0, 14.2, 44.0}, {5.0, 14.2, 50.0}, {11.0, 14.2, 50.0}}),
    LANDING("ship_landing", 56, 16, 2.5, 220, 0.25f, 0.5, 0.016, 2.0f, 8.0, 15.0, 22.0, -3.2, 0.4, 3, 7.0, 6f, 7f, 0f, 90, 0, false, 0, 0,
            new double[][]{{8.0, 13.2, 23.0}, {4.0, 6.2, -18.0}, {8.0, 6.2, -18.0}, {12.0, 6.2, -18.0}, {4.0, 6.2, -10.0}, {8.0, 6.2, -10.0},
                    {12.0, 6.2, -10.0}, {4.0, 6.2, -2.0}, {8.0, 6.2, -2.0}, {12.0, 6.2, -2.0}}),
    SUBMARINE("ship_sub", 112, 13, 11.0, 400, 0.08f, 0.5, 0.010, 1.5f, 8.0, 16.0, -14.0, 0, 0, 0, 0, 0f, 0f, 0f, 0, 0, false, 0, 12,
            new double[][]{{8.0, 16.2, -15.0}, {8.0, 9.0, 0.0}}),
    CARRIER("ship_carrier", 280, 48, 15.0, 2200, 0.05f, 0.42, 0.006, 0.6f, 28.0, 29.0, 60.0, 0, 0, 0, 0, 0f, 0f, 0f, 0, 16, true, 0, 0,
            new double[][]{{27.6, 46.2, 32.0}, {8.0, 28.2, 0.0}, {12.0, 28.2, 0.0}, {4.0, 28.2, 20.0}, {12.0, 28.2, 20.0}});

    public final String model;
    public final double length, beam, waterline, maxSpeed, accel;
    public final float health, bulletFactor, turnRate;
    public final double gunX, gunY, gunZ, muzzle, gunRaise;
    public final int reload;
    public final double shellSpeed, gunRange;
    public final float shellArmour, shellSoft, splash;
    /** surface-to-air missiles in the cells, a close-in gun, anti-ship missiles, torpedoes */
    public final int sam, asm, torpedoes;
    public final boolean ciws;
    public final double[][] seats;

    ShipType(String model, double length, double beam, double waterline, float health, float bulletFactor, double maxSpeed, double accel, float turnRate,
             double gunX, double gunY, double gunZ, double muzzle, double gunRaise, int reload, double shellSpeed, float shellArmour, float shellSoft, float splash,
             double gunRange, int sam, boolean ciws, int asm, int torpedoes, double[][] seats) {
        this.model = model;
        this.length = length;
        this.beam = beam;
        this.waterline = waterline;
        this.health = health;
        this.bulletFactor = bulletFactor;
        this.maxSpeed = maxSpeed;
        this.accel = accel;
        this.turnRate = turnRate;
        this.gunX = gunX;
        this.gunY = gunY;
        this.gunZ = gunZ;
        this.muzzle = muzzle;
        this.gunRaise = gunRaise;
        this.reload = reload;
        this.shellSpeed = shellSpeed;
        this.shellArmour = shellArmour;
        this.shellSoft = shellSoft;
        this.splash = splash;
        this.gunRange = gunRange;
        this.sam = sam;
        this.ciws = ciws;
        this.asm = asm;
        this.torpedoes = torpedoes;
        this.seats = seats;
    }

    public boolean hasGun() {
        return reload > 0;
    }

    public boolean submarine() {
        return this == SUBMARINE;
    }

    public boolean carrier() {
        return this == CARRIER;
    }

    /** hitbox: the beam (and a height), in blocks */
    public float width() {
        return (float) Math.min(12.0, beam * 0.25 * 0.95);
    }

    public float height() {
        return switch (this) {
            case PATROL, LANDING -> 2.6f;
            case SUBMARINE -> 3.4f;
            case CARRIER -> 8.0f;
            default -> 4.4f;
        };
    }

    public VehicleKind vehicle() {
        return switch (this) {
            case PATROL -> VehicleKind.PATROL_BOAT;
            case FRIGATE -> VehicleKind.FRIGATE;
            case DESTROYER -> VehicleKind.DESTROYER;
            case LANDING -> VehicleKind.LANDING_CRAFT;
            case SUBMARINE -> VehicleKind.SUBMARINE;
            case CARRIER -> VehicleKind.CARRIER;
        };
    }
}
