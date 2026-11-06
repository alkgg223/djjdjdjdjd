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
    CRAM(VehicleGeo.CRAM, 140, 0.35f, false, 0, 0f, 1.0, 16f, 12f, 1550, 300),
    /** civilian double-cab pickup: soldiers ride it from place to place (two in the cab, two in the bed), no weapons */
    PICKUP(VehicleGeo.PICKUP, 70, 1.0f, false, 115, 1.5f, 3.2, 0f, 0f, 0, 0),
    /** civilian SUV: four soldiers, quick on the road, no weapons */
    SUV(VehicleGeo.SUV, 65, 1.0f, false, 125, 1.6f, 2.8, 0f, 0f, 0, 0),
    // ---- 3.0
    /** Stryker with the 30 mm turret: quick on roads, soldiers ride on the roof */
    STRYKER(VehicleGeo.STRYKER, 210, 0.12f, true, 100, 1.1f, 2.9, 5.0f, 3.5f, 160, 120),
    /** Boxer with the RCT30 turret: the heaviest protection of the wheeled fighting vehicles */
    BOXER(VehicleGeo.BOXER, 240, 0.1f, true, 100, 1.0f, 3.0, 5.0f, 3.5f, 160, 120),
    /** MaxxPro MRAP: the V hull shrugs off mines and bullets; carries six */
    MRAP(VehicleGeo.MRAP, 170, 0.15f, true, 105, 1.2f, 3.4, 0f, 0f, 0, 0),
    /** JLTV: the light tactical vehicle, quick and protected, carries four */
    JLTV(VehicleGeo.JLTV, 120, 0.25f, true, 110, 1.5f, 3.0, 0f, 0f, 0, 0),
    /** 6x6 cargo truck: two in the cab, six soldiers under the canvas */
    TRUCK(VehicleGeo.TRUCK, 90, 0.6f, false, 90, 1.0f, 4.0, 0f, 0f, 0, 0),
    /** fuel tanker: goes up in a fireball when it is destroyed */
    FUEL_TRUCK(VehicleGeo.FUEL_TRUCK, 80, 0.7f, false, 85, 0.9f, 4.0, 0f, 0f, 0, 0),
    /** ammunition carrier: hands out magazines to the side's players round it */
    AMMO_TRUCK(VehicleGeo.AMMO_TRUCK, 90, 0.6f, false, 85, 0.9f, 4.0, 0f, 0f, 0, 0),
    /** command post: its masts spot the enemies round it for the side */
    COMMAND_TRUCK(VehicleGeo.COMMAND_TRUCK, 110, 0.5f, false, 85, 0.9f, 4.0, 0f, 0f, 0, 0),
    /** recovery truck: repairs the side's damaged vehicles round it */
    RECOVERY_TRUCK(VehicleGeo.RECOVERY_TRUCK, 120, 0.5f, false, 80, 0.85f, 4.0, 0f, 0f, 0, 0);

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
            case PICKUP -> VehicleKind.PICKUP;
            case SUV -> VehicleKind.SUV;
            case STRYKER -> VehicleKind.STRYKER;
            case BOXER -> VehicleKind.BOXER;
            case MRAP -> VehicleKind.MRAP;
            case JLTV -> VehicleKind.JLTV;
            case TRUCK -> VehicleKind.TRUCK;
            case FUEL_TRUCK -> VehicleKind.FUEL_TRUCK;
            case AMMO_TRUCK -> VehicleKind.AMMO_TRUCK;
            case COMMAND_TRUCK -> VehicleKind.COMMAND_TRUCK;
            case RECOVERY_TRUCK -> VehicleKind.RECOVERY_TRUCK;
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

    /** civilian cars: transport only, they carry no weapons */
    public boolean civilian() {
        return this == PICKUP || this == SUV || transport();
    }

    /** 3.0: military transports and support trucks: unarmed like the civilian cars, but in the side's paint */
    public boolean transport() {
        return this == MRAP || this == JLTV || this == TRUCK || this == FUEL_TRUCK || this == AMMO_TRUCK || this == COMMAND_TRUCK || this == RECOVERY_TRUCK;
    }

    /** the paint of a civilian car (the team shows on a stripe along the doors) */
    public float[] civColor() {
        return this == PICKUP ? new float[]{0.92f, 0.92f, 0.89f} : new float[]{0.24f, 0.31f, 0.43f};
    }

    /** the commander's seat turns with the turret (the launchers are worked from the cab) */
    public boolean turretSeat() {
        return this == BTR || this == PANTSIR;
    }
}
