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
    MAUS("maus", Group.TANK),
    OBJ279("object279", Group.TANK),
    COBRA("helicopter", Group.HELI),
    APACHE("apache", Group.HELI),
    HIND("hind", Group.HELI),
    KA52("ka52", Group.HELI),
    TOW_HUMVEE("tow_humvee", Group.JEEP),
    BTR("btr82", Group.CAR),
    PANTSIR("pantsir", Group.CAR),
    HIMARS("himars", Group.CAR),
    GRAD("grad", Group.CAR),
    CRAM("cram", Group.CAR),
    PICKUP("pickup", Group.CAR),
    SUV("suv", Group.CAR),
    F16("f16", Group.JET),
    B2("b2", Group.JET),
    F22("f22", Group.JET),
    F35("f35", Group.JET),
    SU35("su35", Group.JET),
    MIG29("mig29", Group.JET),
    A10("a10", Group.JET);

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
            case MAUS -> Reg.TANK_MAUS.get();
            case OBJ279 -> Reg.TANK_OBJ279.get();
            case COBRA -> Reg.HELICOPTER.get();
            case APACHE -> Reg.HELI_APACHE.get();
            case HIND -> Reg.HELI_HIND.get();
            case KA52 -> Reg.HELI_KA52.get();
            case TOW_HUMVEE -> Reg.JEEP_TOW.get();
            case BTR -> Reg.BTR.get();
            case PANTSIR -> Reg.PANTSIR.get();
            case HIMARS -> Reg.HIMARS.get();
            case GRAD -> Reg.GRAD.get();
            case CRAM -> Reg.CRAM.get();
            case PICKUP -> Reg.PICKUP.get();
            case SUV -> Reg.SUV.get();
            case F16 -> Reg.F16.get();
            case B2 -> Reg.B2.get();
            case F22 -> Reg.F22.get();
            case F35 -> Reg.F35.get();
            case SU35 -> Reg.SU35.get();
            case MIG29 -> Reg.MIG29.get();
            case A10 -> Reg.A10.get();
        };
    }

    /** the aircraft type of a jet kind */
    public JetType jet() {
        return switch (this) {
            case B2 -> JetType.B2;
            case F22 -> JetType.F22;
            case F35 -> JetType.F35;
            case SU35 -> JetType.SU35;
            case MIG29 -> JetType.MIG29;
            case A10 -> JetType.A10;
            default -> JetType.F16;
        };
    }

    public TankType tank() {
        return switch (this) {
            case LEOPARD -> TankType.LEOPARD;
            case T90 -> TankType.T90;
            case CHALLENGER -> TankType.CHALLENGER;
            case MAUS -> TankType.MAUS;
            case OBJ279 -> TankType.OBJ279;
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
        return switch (this) {
            case PANTSIR -> CarType.PANTSIR;
            case HIMARS -> CarType.HIMARS;
            case GRAD -> CarType.GRAD;
            case CRAM -> CarType.CRAM;
            case PICKUP -> CarType.PICKUP;
            case SUV -> CarType.SUV;
            default -> CarType.BTR;
        };
    }
}
