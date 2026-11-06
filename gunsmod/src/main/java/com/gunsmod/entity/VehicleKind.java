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
    A10("a10", Group.JET),
    // 3.0
    M1A2SEP("m1a2sep", Group.TANK),
    LEOPARD2A7("leopard2a7", Group.TANK),
    T14("t14", Group.TANK),
    T80BVM("t80bvm", Group.TANK),
    MERKAVA4("merkava4", Group.TANK),
    K2("k2", Group.TANK),
    TYPE99A("type99a", Group.TANK),
    BRADLEY("bradley", Group.TANK),
    BMP3("bmp3", Group.TANK),
    CV90("cv90", Group.TANK),
    BMD4("bmd4", Group.TANK),
    STRYKER("stryker", Group.CAR),
    BOXER("boxer", Group.CAR),
    MRAP("mrap", Group.CAR),
    JLTV("jltv", Group.CAR),
    TRUCK("military_truck", Group.CAR),
    FUEL_TRUCK("fuel_truck", Group.CAR),
    AMMO_TRUCK("ammo_truck", Group.CAR),
    COMMAND_TRUCK("command_truck", Group.CAR),
    RECOVERY_TRUCK("recovery_truck", Group.CAR),
    FA18("fa18", Group.JET),
    F15E("f15e", Group.JET),
    SU57("su57", Group.JET),
    TYPHOON("typhoon", Group.JET),
    RAFALE("rafale", Group.JET),
    GRIPEN("gripen", Group.JET),
    UH60("uh60", Group.HELI),
    CH47("ch47", Group.HELI),
    PATROL_BOAT("patrol_boat", Group.SHIP),
    FRIGATE("frigate", Group.SHIP),
    DESTROYER("destroyer", Group.SHIP),
    LANDING_CRAFT("landing_craft", Group.SHIP),
    SUBMARINE("submarine", Group.SHIP),
    CARRIER("carrier", Group.SHIP);

    public enum Group { TANK, HELI, JEEP, CAR, JET, SHIP }

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
            case M1A2SEP -> Reg.TANK_M1A2SEP.get();
            case LEOPARD2A7 -> Reg.TANK_LEOPARD2A7.get();
            case T14 -> Reg.TANK_T14.get();
            case T80BVM -> Reg.TANK_T80BVM.get();
            case MERKAVA4 -> Reg.TANK_MERKAVA4.get();
            case K2 -> Reg.TANK_K2.get();
            case TYPE99A -> Reg.TANK_TYPE99A.get();
            case BRADLEY -> Reg.TANK_BRADLEY.get();
            case BMP3 -> Reg.TANK_BMP3.get();
            case CV90 -> Reg.TANK_CV90.get();
            case BMD4 -> Reg.TANK_BMD4.get();
            case STRYKER -> Reg.CAR_STRYKER.get();
            case BOXER -> Reg.CAR_BOXER.get();
            case MRAP -> Reg.CAR_MRAP.get();
            case JLTV -> Reg.CAR_JLTV.get();
            case TRUCK -> Reg.CAR_TRUCK.get();
            case FUEL_TRUCK -> Reg.CAR_FUEL_TRUCK.get();
            case AMMO_TRUCK -> Reg.CAR_AMMO_TRUCK.get();
            case COMMAND_TRUCK -> Reg.CAR_COMMAND_TRUCK.get();
            case RECOVERY_TRUCK -> Reg.CAR_RECOVERY_TRUCK.get();
            case FA18 -> Reg.JET_FA18.get();
            case F15E -> Reg.JET_F15E.get();
            case SU57 -> Reg.JET_SU57.get();
            case TYPHOON -> Reg.JET_TYPHOON.get();
            case RAFALE -> Reg.JET_RAFALE.get();
            case GRIPEN -> Reg.JET_GRIPEN.get();
            case UH60 -> Reg.HELI_UH60.get();
            case CH47 -> Reg.HELI_CH47.get();
            case PATROL_BOAT -> Reg.SHIP_PATROL.get();
            case FRIGATE -> Reg.SHIP_FRIGATE.get();
            case DESTROYER -> Reg.SHIP_DESTROYER.get();
            case LANDING_CRAFT -> Reg.SHIP_LANDING.get();
            case SUBMARINE -> Reg.SHIP_SUBMARINE.get();
            case CARRIER -> Reg.SHIP_CARRIER.get();
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
            case FA18 -> JetType.FA18;
            case F15E -> JetType.F15E;
            case SU57 -> JetType.SU57;
            case TYPHOON -> JetType.TYPHOON;
            case RAFALE -> JetType.RAFALE;
            case GRIPEN -> JetType.GRIPEN;
            default -> JetType.F16;
        };
    }

    public ShipType ship() {
        return switch (this) {
            case PATROL_BOAT -> ShipType.PATROL;
            case FRIGATE -> ShipType.FRIGATE;
            case DESTROYER -> ShipType.DESTROYER;
            case LANDING_CRAFT -> ShipType.LANDING;
            case SUBMARINE -> ShipType.SUBMARINE;
            case CARRIER -> ShipType.CARRIER;
            default -> ShipType.PATROL;
        };
    }

    public TankType tank() {
        return switch (this) {
            case LEOPARD -> TankType.LEOPARD;
            case T90 -> TankType.T90;
            case CHALLENGER -> TankType.CHALLENGER;
            case MAUS -> TankType.MAUS;
            case OBJ279 -> TankType.OBJ279;
            case M1A2SEP -> TankType.M1A2SEP;
            case LEOPARD2A7 -> TankType.LEOPARD2A7;
            case T14 -> TankType.T14;
            case T80BVM -> TankType.T80BVM;
            case MERKAVA4 -> TankType.MERKAVA4;
            case K2 -> TankType.K2;
            case TYPE99A -> TankType.TYPE99A;
            case BRADLEY -> TankType.BRADLEY;
            case BMP3 -> TankType.BMP3;
            case CV90 -> TankType.CV90;
            case BMD4 -> TankType.BMD4;
            default -> TankType.ABRAMS;
        };
    }

    public HeliType heli() {
        return switch (this) {
            case APACHE -> HeliType.APACHE;
            case HIND -> HeliType.HIND;
            case KA52 -> HeliType.KA52;
            case UH60 -> HeliType.UH60;
            case CH47 -> HeliType.CH47;
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
            case STRYKER -> CarType.STRYKER;
            case BOXER -> CarType.BOXER;
            case MRAP -> CarType.MRAP;
            case JLTV -> CarType.JLTV;
            case TRUCK -> CarType.TRUCK;
            case FUEL_TRUCK -> CarType.FUEL_TRUCK;
            case AMMO_TRUCK -> CarType.AMMO_TRUCK;
            case COMMAND_TRUCK -> CarType.COMMAND_TRUCK;
            case RECOVERY_TRUCK -> CarType.RECOVERY_TRUCK;
            default -> CarType.BTR;
        };
    }
}
