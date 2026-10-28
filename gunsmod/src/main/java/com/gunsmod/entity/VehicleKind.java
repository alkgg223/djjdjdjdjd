package com.gunsmod.entity;

import com.gunsmod.Reg;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** Every vehicle that is placed with a team item (green_/red_ + item id) or by the vehicle spawner. */
public enum VehicleKind {
    ABRAMS("tank", Group.TANK),
    LEOPARD("leopard", Group.TANK),
    T90("t90", Group.TANK),
    CHALLENGER("challenger", Group.TANK),
    COBRA("helicopter", Group.HELI),
    APACHE("apache", Group.HELI),
    HIND("hind", Group.HELI),
    KA52("ka52", Group.HELI),
    TOW_HUMVEE("tow_humvee", Group.JEEP),
    BTR("btr82", Group.CAR),
    PANTSIR("pantsir", Group.CAR),
    F16("f16", Group.JET);

    public enum Group { TANK, HELI, JEEP, CAR, JET }

    public final String item;
    public final Group group;

    VehicleKind(String item, Group group) {
        this.item = item;
        this.group = group;
    }

    public EntityType<? extends Mob> type() {
        return switch (this) {
            case ABRAMS -> Reg.TANK.get();
            case LEOPARD -> Reg.TANK_LEOPARD.get();
            case T90 -> Reg.TANK_T90.get();
            case CHALLENGER -> Reg.TANK_CHALLENGER.get();
            case COBRA -> Reg.HELICOPTER.get();
            case APACHE -> Reg.HELI_APACHE.get();
            case HIND -> Reg.HELI_HIND.get();
            case KA52 -> Reg.HELI_KA52.get();
            case TOW_HUMVEE -> Reg.JEEP_TOW.get();
            case BTR -> Reg.BTR.get();
            case PANTSIR -> Reg.PANTSIR.get();
            case F16 -> Reg.F16.get();
        };
    }

    public TankType tank() {
        return switch (this) {
            case LEOPARD -> TankType.LEOPARD;
            case T90 -> TankType.T90;
            case CHALLENGER -> TankType.CHALLENGER;
            default -> TankType.ABRAMS;
        };
    }

    public HeliType heli() {
        return switch (this) {
            case APACHE -> HeliType.APACHE;
            case HIND -> HeliType.HIND;
            case KA52 -> HeliType.KA52;
            default -> HeliType.COBRA;
        };
    }

    public CarType car() {
        return this == PANTSIR ? CarType.PANTSIR : CarType.BTR;
    }
}
