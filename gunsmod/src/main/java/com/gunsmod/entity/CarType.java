package com.gunsmod.entity;

import com.gunsmod.Reg;
import net.minecraft.world.item.Item;

/** The 8x8 wheeled fighting vehicles. */
public enum CarType {
    /** BTR-82A: amphibious armoured personnel carrier, 30 mm 2A72 cannon + PKTM, three soldiers ride on top */
    BTR(VehicleGeo.BTR82, 200, 0.12f, true, 62, 1.0f, 2.85, 4.5f, 3.0f, 120, 100),
    /** Pantsir-S1: air defence; search and tracking radars, 12 surface-to-air missiles, two twin 30 mm guns. Only fights aircraft. */
    PANTSIR(VehicleGeo.PANTSIR, 220, 0.25f, false, 52, 0.75f, 5.85, 8.0f, 5.0f, 12, 400);

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
        return this == PANTSIR ? VehicleKind.PANTSIR : VehicleKind.BTR;
    }

    public Item item(BotTeam team) {
        return Reg.vehicleItem(vehicle(), team);
    }

    public boolean antiAir() {
        return this == PANTSIR;
    }
}
