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
    CHALLENGER(VehicleGeo.CHALLENGER, "challenger", 390, 0.03f, 30, 0.8f, 2.1f, 110, 4.4, 50f, 44f, 4.4f, 2.5f, -20f, 10f);

    public final VehicleGeo.Tank geo;
    public final String id;
    /** gun pitch limits as view pitch: gunMin = -elevation (up), gunMax = depression (down) */
    public final float health, bulletFactor, accel, turnRate, shellDamageArmour, shellDamageSoft, splash, traverse, gunMin, gunMax;
    public final double maxSpeed, shellSpeed;
    public final int reload;

    TankType(VehicleGeo.Tank geo, String id, float health, float bulletFactor, double kmh, float accel, float turnRate, int reload,
             double shellSpeed, float shellDamageArmour, float shellDamageSoft, float splash, float traverse, float gunMin, float gunMax) {
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
        };
    }

    public Item item(BotTeam team) {
        return Reg.vehicleItem(vehicle(), team);
    }
}
