package com.gunsmod.entity;

import com.gunsmod.Reg;
import net.minecraft.world.item.Item;

/**
 * The four main battle tanks. They differ where real tanks differ: armour (health and how much small arms do),
 * gun (shell speed, damage, blast), loader (reload time), mobility (top speed, acceleration, turning) and turret drive.
 */
public enum TankType {
    /** M1A2 Abrams: fast turbine, balanced armour and gun, quick human loader */
    ABRAMS(VehicleGeo.ABRAMS, "abrams", 320, 0.04f, 36, 1.0f, 2.4f, 90, 4.5, 55f, 40f, 3.2f, 3.0f, -20f, 9f),
    /** Leopard 2A6: best mobility, long L55 gun with the fastest, flattest shot, fastest reload */
    LEOPARD(VehicleGeo.LEOPARD, "leopard", 300, 0.045f, 39, 1.15f, 2.6f, 82, 5.4, 58f, 42f, 3.0f, 3.3f, -20f, 9f),
    /** T-90A: 125 mm hits hardest, slow autoloader, poor gun depression, light and agile */
    T90(VehicleGeo.T90, "t90", 280, 0.05f, 34, 0.95f, 2.7f, 140, 4.3, 66f, 46f, 3.6f, 2.1f, -14f, 5f),
    /** Challenger 2: the heaviest armour, slowest, rifled gun firing HESH with a big blast */
    CHALLENGER(VehicleGeo.CHALLENGER, "challenger", 390, 0.03f, 30, 0.8f, 2.1f, 110, 4.4, 50f, 44f, 4.4f, 2.5f, -20f, 10f),
    /** Panzer VIII Maus: 188 tonnes, a 128 mm gun and armour nothing light gets through; crawls and turns slowly */
    MAUS(VehicleGeo.MAUS, "maus", 760, 0.012f, 20, 0.45f, 1.2f, 150, 4.2, 85f, 62f, 5.2f, 1.5f, -20f, 7f, 1.4f),
    /** Object 279: four tracks under a saucer hull built to survive a nuclear blast, 130 mm gun */
    OBJ279(VehicleGeo.OBJ279, "obj279", 640, 0.016f, 32, 0.6f, 1.6f, 130, 4.6, 78f, 56f, 4.6f, 2.0f, -18f, 6f, 1.4f),
    // ---- 3.0 main battle tanks
    /** M1A2 SEP v3: the Abrams with the Trophy protection, bar armour and a remote weapon station, more armour, a little slower */
    M1A2SEP(VehicleGeo.M1A2SEP, "m1a2sep", 360, 0.035f, 42, 1.0f, 2.4f, 85, 4.6, 60f, 42f, 3.3f, 3.2f, -20f, 9f),
    /** Leopard 2A7: the L55 with programmable rounds, wedge armour, the best fire control */
    LEOPARD2A7(VehicleGeo.LEOPARD2A7, "leopard2a7", 340, 0.035f, 40, 1.1f, 2.6f, 78, 5.5, 62f, 44f, 3.2f, 3.4f, -20f, 9f),
    /** T-14 Armata: an unmanned turret with a fast autoloader, the crew in an armoured capsule, Afganit protection */
    T14(VehicleGeo.T14, "t14", 360, 0.03f, 45, 1.2f, 2.8f, 70, 5.2, 70f, 48f, 3.8f, 3.6f, -20f, 6f),
    /** T-80BVM: the gas turbine makes it the quickest off the mark; Relikt reactive armour all over */
    T80BVM(VehicleGeo.T80BVM, "t80bvm", 300, 0.04f, 44, 1.3f, 2.9f, 120, 4.6, 66f, 46f, 3.6f, 2.6f, -14f, 5f),
    /** Merkava Mk 4: engine in front for the crew's sake, the heaviest protection, carries soldiers in the back */
    MERKAVA4(VehicleGeo.MERKAVA4, "merkava4", 400, 0.028f, 38, 0.95f, 2.3f, 95, 4.6, 60f, 46f, 3.6f, 2.8f, -20f, 7f, 1f,
            new double[][]{{5.0, 6.8, 18.6}, {11.0, 6.8, 18.6}}),
    /** K2 Black Panther: a fast autoloader and an adjustable suspension that lets it aim far down */
    K2(VehicleGeo.K2, "k2", 330, 0.035f, 44, 1.2f, 2.7f, 60, 5.4, 62f, 44f, 3.2f, 3.4f, -20f, 10f),
    /** Type 99A: 125 mm with an autoloader, reactive armour on the arrowhead turret and the glacis */
    TYPE99A(VehicleGeo.TYPE99A, "type99a", 330, 0.035f, 42, 1.1f, 2.6f, 90, 4.8, 66f, 46f, 3.6f, 2.8f, -18f, 6f),
    // ---- 3.0 infantry fighting vehicles: an autocannon (quick bursts, light shells), soldiers ride on the hull
    /** M2A3 Bradley: 25 mm Bushmaster and a TOW box, six soldiers */
    BRADLEY(VehicleGeo.BRADLEY, "bradley", 180, 0.12f, 40, 1.2f, 3.0f, 6, 6.5, 12f, 10f, 1.0f, 4.0f, -50f, 9f, 1f, new double[][]{{4.2, 7.6, 13.6}, {11.8, 7.6, 13.6}, {4.2, 7.6, 17.4}, {11.8, 7.6, 17.4}}),
    /** BMP-3: 100 mm gun-launcher with a 30 mm beside it, light and quick, swims */
    BMP3(VehicleGeo.BMP3, "bmp3", 170, 0.14f, 50, 1.3f, 3.2f, 6, 6.0, 12f, 11f, 1.1f, 4.0f, -50f, 6f, 1f, new double[][]{{4.2, 6.8, 12.0}, {11.8, 6.8, 12.0}, {4.2, 6.8, 16.6}, {11.8, 6.8, 16.6}}),
    /** CV90: the 40 mm Bofors, well protected, quick */
    CV90(VehicleGeo.CV90, "cv90", 200, 0.1f, 50, 1.25f, 3.0f, 9, 6.2, 16f, 12f, 1.4f, 3.8f, -50f, 8f, 1f, new double[][]{{4.2, 7.4, 14.4}, {11.8, 7.4, 14.4}, {4.2, 7.4, 18.2}, {11.8, 7.4, 18.2}}),
    /** BMD-4M: the airborne fighting vehicle, light enough to be dropped by parachute */
    BMD4(VehicleGeo.BMD4, "bmd4", 140, 0.18f, 55, 1.4f, 3.4f, 6, 6.0, 11f, 10f, 1.0f, 4.2f, -50f, 6f, 1f,
            new double[][]{{4.2, 5.8, 12.6}, {11.8, 5.8, 12.6}, {8.0, 5.8, 17.4}});

    public final VehicleGeo.Tank geo;
    public final String id;
    /** gun pitch limits as view pitch: gunMin = -elevation (up), gunMax = depression (down) */
    public final float health, bulletFactor, accel, turnRate, shellDamageArmour, shellDamageSoft, splash, traverse, gunMin, gunMax;
    public final double maxSpeed, shellSpeed;
    public final int reload;
    /** how much bigger than the standard tank the model is drawn and the crew points are placed */
    public final float scale;
    /** 3.0: places on the hull for riding soldiers (model units), empty for most tanks */
    public final double[][] riders;

    TankType(VehicleGeo.Tank geo, String id, float health, float bulletFactor, double kmh, float accel, float turnRate, int reload,
             double shellSpeed, float shellDamageArmour, float shellDamageSoft, float splash, float traverse, float gunMin, float gunMax) {
        this(geo, id, health, bulletFactor, kmh, accel, turnRate, reload, shellSpeed, shellDamageArmour, shellDamageSoft, splash, traverse, gunMin,
                gunMax, 1f);
    }

    TankType(VehicleGeo.Tank geo, String id, float health, float bulletFactor, double kmh, float accel, float turnRate, int reload,
             double shellSpeed, float shellDamageArmour, float shellDamageSoft, float splash, float traverse, float gunMin, float gunMax, float scale) {
        this(geo, id, health, bulletFactor, kmh, accel, turnRate, reload, shellSpeed, shellDamageArmour, shellDamageSoft, splash, traverse, gunMin, gunMax,
                scale, new double[0][]);
    }

    TankType(VehicleGeo.Tank geo, String id, float health, float bulletFactor, double kmh, float accel, float turnRate, int reload,
             double shellSpeed, float shellDamageArmour, float shellDamageSoft, float splash, float traverse, float gunMin, float gunMax, float scale,
             double[][] riders) {
        this.riders = riders;
        this.scale = scale;
        this.geo = geo;
        this.id = id;
        this.health = health;
        this.bulletFactor = bulletFactor;
        this.maxSpeed = kmh / 3.6 / 20.0;
        this.accel = accel;
        this.turnRate = turnRate;
        this.reload = reload;
        this.shellSpeed = shellSpeed;
        this.shellDamageArmour = shellDamageArmour;
        this.shellDamageSoft = shellDamageSoft;
        this.splash = splash;
        this.traverse = traverse;
        this.gunMin = gunMin;
        this.gunMax = gunMax;
    }

    public VehicleKind vehicle() {
        return switch (this) {
            case ABRAMS -> VehicleKind.ABRAMS;
            case LEOPARD -> VehicleKind.LEOPARD;
            case T90 -> VehicleKind.T90;
            case CHALLENGER -> VehicleKind.CHALLENGER;
            case MAUS -> VehicleKind.MAUS;
            case OBJ279 -> VehicleKind.OBJ279;
            case M1A2SEP -> VehicleKind.M1A2SEP;
            case LEOPARD2A7 -> VehicleKind.LEOPARD2A7;
            case T14 -> VehicleKind.T14;
            case T80BVM -> VehicleKind.T80BVM;
            case MERKAVA4 -> VehicleKind.MERKAVA4;
            case K2 -> VehicleKind.K2;
            case TYPE99A -> VehicleKind.TYPE99A;
            case BRADLEY -> VehicleKind.BRADLEY;
            case BMP3 -> VehicleKind.BMP3;
            case CV90 -> VehicleKind.CV90;
            case BMD4 -> VehicleKind.BMD4;
        };
    }

    /** an infantry fighting vehicle: an autocannon instead of a tank gun */
    public boolean ifv() {
        return this == BRADLEY || this == BMP3 || this == CV90 || this == BMD4;
    }

    public Item item(BotTeam team) {
        return Reg.vehicleItem(vehicle(), team);
    }
}
