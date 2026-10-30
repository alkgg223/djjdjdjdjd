package com.gunsmod.entity;

import com.gunsmod.Reg;
import net.minecraft.world.item.Item;

/** The 8x8 wheeled fighting vehicles. */
public enum CarType {
    /** BTR-82A: amphibious armoured personnel carrier, 30 mm 2A72 cannon + PKTM, three soldiers ride on top */
    BTR(VehicleGeo.BTR82, 200, 0.12f, true, 62, 1.0f, 2.85, 4.5f, 3.0f, 120, 100),
    /** Pantsir-S1: air defence; search and tracking radars, 12 surface-to-air missiles, two twin 30 mm guns. Only fights aircraft. */
    PANTSIR(VehicleGeo.PANTSIR, 220, 0.25f, false, 52, 0.75f, 5.85, 8.0f, 5.0f, 12, 400),
    /** M142 HIMARS: six guided GMLRS rockets with a heavy warhead, long reach, tight grouping; reloads a whole pod */
    HIMARS(VehicleGeo.HIMARS, 170, 0.3f, true, 85, 0.9f, 3.6, 3.0f, 2.0f, 6, 360),
    /** BM-21 Grad: forty 122 mm rockets rippled off in seconds over a wide area */
    GRAD(VehicleGeo.GRAD, 150, 0.35f, false, 75, 0.8f, 4.0, 2.4f, 1.8f, 40, 480),
    /** C-RAM: a Phalanx 20 mm gatling on a trailer that shoots down rockets, shells, missiles and aircraft on its own */
    CRAM(VehicleGeo.CRAM, 140, 0.35f, false, 0, 0f, 1.0, 16f, 12f, 1550, 300);

    public final VehicleGeo.Car geo;
    public final float health, bulletFactor, accel, traverse, elevate;
    public final boolean armored;
    public final double maxSpeed, wheelbase;
    /** main weapon: rounds in the belt / missiles in the containers, and the time to reload all of them */
    public final int ammo, reload;

    CarType(VehicleGeo.Car geo, float health, float bulletFactor, boolean armored, double kmh, float accel, double wheelbase, float traverse,
            float elevate, int ammo, int reload) {
        this.geo = geo;
        this.health = health;
        this.bulletFactor = bulletFactor;
        this.armored = armored;
        this.maxSpeed = kmh / 3.6 / 20.0;
        this.accel = accel;
        this.wheelbase = wheelbase;
        this.traverse = traverse;
        this.elevate = elevate;
        this.ammo = ammo;
        this.reload = reload;
    }

    public VehicleKind vehicle() {
        return switch (this) {
            case BTR -> VehicleKind.BTR;
            case PANTSIR -> VehicleKind.PANTSIR;
            case HIMARS -> VehicleKind.HIMARS;
            case GRAD -> VehicleKind.GRAD;
            case CRAM -> VehicleKind.CRAM;
        };
    }

    public Item item(BotTeam team) {
        return Reg.vehicleItem(vehicle(), team);
    }

    public boolean antiAir() {
        return this == PANTSIR;
    }

    /** rocket artillery: fires salvos at points far away */
    public boolean artillery() {
        return this == HIMARS || this == GRAD;
    }

    public boolean cram() {
        return this == CRAM;
    }

    /** the commander's seat turns with the turret (the launchers are worked from the cab) */
    public boolean turretSeat() {
        return this == BTR || this == PANTSIR;
    }
}
