package com.gunsmod.entity;

/**
 * The fixed-wing aircraft and how each one flies and fights. Speeds are blocks per tick (1 block = 1 m; real speeds are
 * scaled down like everything else in the game). Thrust and reheat are multipliers on the F-16's engine, chosen so
 * each type tops out at its own speed. Agility multiplies the turn rates.
 */
public enum JetType {
    //    model    geo              unit   stall rotate max   patrol thrust reheat agility hp   bullet sign  cram  role
    F16("f16", VehicleGeo.F16, 0.375, 0.556, 0.972, 2.50, 0.694, 1.00, 1.00, 1.00, 200, 0.20f, 1.00f, 0.60f, Role.MULTIROLE,
            Gun.M61, Ground.MAVERICK, Air.AIM9, 0b001111, 0b110000, 0b111111, 60, true),
    F22("f22", VehicleGeo.F22, 0.375, 0.55, 1.00, 2.90, 0.85, 1.55, 1.04, 1.15, 220, 0.18f, 0.25f, 0.45f, Role.FIGHTER,
            Gun.M61, Ground.JDAM, Air.AIM120, 0b000011, 0b111100, 0, 60, true),
    F35("f35", VehicleGeo.F35, 0.375, 0.55, 0.95, 2.40, 0.75, 0.97, 0.85, 1.00, 230, 0.18f, 0.35f, 0.55f, Role.MULTIROLE,
            Gun.GAU22, Ground.JDAM, Air.AIM120, 0b000011, 0b111100, 0b110000, 60, true),
    SU35("su35", VehicleGeo.SU35, 0.375, 0.50, 0.95, 2.70, 0.80, 1.08, 1.30, 1.35, 260, 0.20f, 1.00f, 0.55f, Role.FIGHTER,
            Gun.GSH30, Ground.MAVERICK, Air.R77, 0b001100, 0b110011, 0b111111, 64, true),
    MIG29("mig29", VehicleGeo.MIG29, 0.375, 0.55, 0.95, 2.60, 0.80, 0.97, 1.24, 1.25, 200, 0.22f, 0.90f, 0.70f, Role.FIGHTER,
            Gun.GSH30, Ground.JDAM, Air.R73, 0b001100, 0b110011, 0b111111, 60, true),
    A10("a10", VehicleGeo.A10, 0.375, 0.40, 0.62, 1.70, 0.60, 0.62, 0.90, 0.85, 420, 0.05f, 1.10f, 0.35f, Role.STRIKE,
            Gun.GAU8, Ground.MAVERICK, Air.AIM9, 0b001111, 0b110000, 0b111111, 96, true),
    B2("b2", VehicleGeo.B2, 1.0, 0.50, 0.85, 1.90, 1.00, 1.30, 0.00, 0.45, 500, 0.00f, 0.00f, 0.00f, Role.BOMBER,
            Gun.NONE, Ground.NUKE, Air.NONE, 0b111111, 0, 0, 30, false),
    // ---- 3.0
    FA18("fa18", VehicleGeo.FA18, 0.375, 0.50, 0.95, 2.45, 0.78, 1.00, 1.05, 1.20, 240, 0.20f, 0.85f, 0.55f, Role.MULTIROLE,
            Gun.M61, Ground.MAVERICK, Air.AIM120, 0b001100, 0b110011, 0b111111, 60, true),
    F15E("f15e", VehicleGeo.F15E, 0.375, 0.55, 0.95, 2.75, 0.85, 1.15, 1.20, 1.05, 280, 0.18f, 1.00f, 0.55f, Role.STRIKE,
            Gun.M61, Ground.JDAM, Air.AIM120, 0b001100, 0b110011, 0b111111, 64, true),
    SU57("su57", VehicleGeo.SU57, 0.375, 0.50, 1.00, 2.85, 0.85, 1.50, 1.15, 1.40, 250, 0.18f, 0.40f, 0.45f, Role.FIGHTER,
            Gun.GSH30, Ground.MAVERICK, Air.R77, 0b001100, 0b110011, 0b111111, 60, true),
    TYPHOON("typhoon", VehicleGeo.TYPHOON, 0.375, 0.50, 1.00, 2.80, 0.85, 1.20, 1.15, 1.30, 220, 0.20f, 0.80f, 0.55f, Role.FIGHTER,
            Gun.GSH30, Ground.JDAM, Air.AIM120, 0b001100, 0b110011, 0b111111, 60, true),
    RAFALE("rafale", VehicleGeo.RAFALE, 0.375, 0.48, 1.00, 2.70, 0.82, 1.10, 1.10, 1.30, 220, 0.20f, 0.75f, 0.55f, Role.MULTIROLE,
            Gun.GSH30, Ground.MAVERICK, Air.AIM120, 0b001100, 0b110011, 0b111111, 60, true),
    GRIPEN("gripen", VehicleGeo.GRIPEN, 0.375, 0.48, 1.00, 2.50, 0.78, 0.95, 1.10, 1.25, 180, 0.22f, 0.70f, 0.60f, Role.MULTIROLE,
            Gun.GSH30, Ground.JDAM, Air.AIM120, 0b001100, 0b110011, 0b111111, 56, true);

    /** how a soldier pilot fights it */
    public enum Role { MULTIROLE, FIGHTER, STRIKE, BOMBER }

    /** the cannon: damage a round, rounds carried, shells that burst where they hit, how far they reach */
    public enum Gun {
        NONE(0, 0, false, 0, "jet_cannon"),
        M61(8f, 510, false, 220, "jet_cannon"),
        GAU22(11f, 180, true, 230, "gun_gau22"),
        GSH30(14f, 150, true, 230, "gun_gsh30"),
        GAU8(24f, 1150, true, 280, "gun_gau8");

        public final float damage;
        public final int rounds;
        public final boolean explosive;
        public final double range;
        public final String sound;

        Gun(float damage, int rounds, boolean explosive, double range, String sound) {
            this.damage = damage;
            this.rounds = rounds;
            this.explosive = explosive;
            this.range = range;
            this.sound = sound;
        }
    }

    /** what goes on the ground stores */
    public enum Ground { MAVERICK, JDAM, NUKE }

    /** air-to-air missile: guided missile kind, top speed and agility (0 = the kind's own), lock range, seeker cone */
    public enum Air {
        NONE(GuidedMissileEntity.AIM9, 0, 0, 0, 0),
        AIM9(GuidedMissileEntity.AIM9, 0, 0, 260, 14),
        AIM120(GuidedMissileEntity.AIM9, 7.6, 9.5, 330, 14),
        R77(GuidedMissileEntity.AIM9, 7.2, 10.0, 310, 16),
        R73(GuidedMissileEntity.AIM9, 6.2, 16.0, 220, 34);

        public final int kind;
        public final double speed, agility, range, cone;

        Air(int kind, double speed, double agility, double range, double cone) {
            this.kind = kind;
            this.speed = speed;
            this.agility = agility;
            this.range = range;
            this.cone = cone;
        }
    }

    public final String model;
    public final VehicleGeo.Jet geo;
    /** blocks per model unit */
    public final double unit;
    public final double stall, rotate, maxSpeed, patrol, thrust, reheat, agility;
    public final float health, bulletFactor;
    /** how strongly it shows on a radar (1 = an ordinary fighter, 0 = invisible) */
    public final float signature;
    /** how much of a C-RAM burst's damage it takes */
    public final float cramFactor;
    public final Role role;
    public final Gun gun;
    public final Ground ground;
    public final Air air;
    /** store bits: ground weapons, air weapons, the ones hanging outside (drawn) */
    public final int groundMask, airMask, drawMask;
    public final int flares;
    public final boolean afterburner;

    JetType(String model, VehicleGeo.Jet geo, double unit, double stall, double rotate, double maxSpeed, double patrol, double thrust, double reheat,
            double agility, float health, float bulletFactor, float signature, float cramFactor, Role role, Gun gun, Ground ground, Air air,
            int groundMask, int airMask, int drawMask, int flares, boolean afterburner) {
        this.model = model;
        this.geo = geo;
        this.unit = unit;
        this.stall = stall;
        this.rotate = rotate;
        this.maxSpeed = maxSpeed;
        this.patrol = patrol;
        this.thrust = thrust;
        this.reheat = reheat;
        this.agility = agility;
        this.health = health;
        this.bulletFactor = bulletFactor;
        this.signature = signature;
        this.cramFactor = cramFactor;
        this.role = role;
        this.gun = gun;
        this.ground = ground;
        this.air = air;
        this.groundMask = groundMask;
        this.airMask = airMask;
        this.drawMask = drawMask;
        this.flares = flares;
        this.afterburner = afterburner;
    }

    /** lang key of the weapon on stores 0 (ground) or 1 (air) */
    public String weaponKey(int w) {
        if (w == 0) {
            return switch (ground) {
                case MAVERICK -> this == SU35 ? "gunsmod.weapon.kh29" : "gunsmod.weapon.maverick";
                case JDAM -> this == MIG29 ? "gunsmod.weapon.kab500" : "gunsmod.weapon.jdam";
                case NUKE -> "gunsmod.weapon.b2bomb";
            };
        }
        return switch (air) {
            case AIM120 -> "gunsmod.weapon.aim120";
            case R77 -> "gunsmod.weapon.r77";
            case R73 -> "gunsmod.weapon.r73";
            default -> "gunsmod.weapon.aim9";
        };
    }

    public int allStores() {
        return groundMask | airMask;
    }

    /** the B-2: no radar or seeker finds it, only an RPG's lucky hit (one in a hundred) hurts it, five bring it down */
    public boolean rpgOnly() {
        return this == B2;
    }

    /** the engine sound set: <prefix>_idle, <prefix>_engine, <prefix>_burner */
    public String soundPrefix() {
        return this == F16 ? "jet" : model;
    }

    /** render scale of the baked model (16 model units per model block) */
    public float renderScale() {
        return (float) (16 * unit);
    }

    /** attitude pivot along the body, model units */
    public double pivotZ() {
        return this == B2 ? 8.0 : 4.0;
    }

    /** base paint colour before the team tint */
    public float[] paint() {
        return switch (this) {
            case F22 -> new float[]{0.62f, 0.64f, 0.66f};
            case F35 -> new float[]{0.47f, 0.49f, 0.52f};
            case SU35 -> new float[]{0.56f, 0.64f, 0.72f};
            case MIG29 -> new float[]{0.60f, 0.62f, 0.60f};
            case A10 -> new float[]{0.44f, 0.47f, 0.45f};
            case B2 -> new float[]{0.28f, 0.29f, 0.31f};
            case FA18 -> new float[]{0.56f, 0.58f, 0.62f};
            case F15E -> new float[]{0.38f, 0.40f, 0.42f};
            case SU57 -> new float[]{0.48f, 0.55f, 0.62f};
            case TYPHOON -> new float[]{0.55f, 0.57f, 0.60f};
            case RAFALE -> new float[]{0.50f, 0.52f, 0.56f};
            case GRIPEN -> new float[]{0.52f, 0.54f, 0.56f};
            default -> new float[]{0.74f, 0.76f, 0.78f};
        };
    }

    public VehicleKind kind() {
        return switch (this) {
            case F22 -> VehicleKind.F22;
            case F35 -> VehicleKind.F35;
            case SU35 -> VehicleKind.SU35;
            case MIG29 -> VehicleKind.MIG29;
            case A10 -> VehicleKind.A10;
            case B2 -> VehicleKind.B2;
            case FA18 -> VehicleKind.FA18;
            case F15E -> VehicleKind.F15E;
            case SU57 -> VehicleKind.SU57;
            case TYPHOON -> VehicleKind.TYPHOON;
            case RAFALE -> VehicleKind.RAFALE;
            case GRIPEN -> VehicleKind.GRIPEN;
            default -> VehicleKind.F16;
        };
    }
}
