package com.gunsmod.entity;

import com.gunsmod.Reg;
import net.minecraft.world.item.Item;

/**
 * The four attack helicopters. Each has its own armour, speed and handling, its own gun and its own way of attacking:
 * the Cobra fires Hydra rockets, the Apache locks Hellfires on targets, the Hind drops bombs, fires S-8 salvos and
 * guides Shturm missiles, the Ka-52 rides Vikhr missiles on its sight beam.
 */
public enum HeliType {
    /** AH-1 Cobra: light and nimble, 20 mm three-barrel chin gun, Hydra 70 rockets */
    COBRA(VehicleGeo.COBRA, 180, 0.3f, 1.35, 0.034, 3.4f, 0.32, 30, Gun.M197, Weapon.HYDRA),
    /** AH-64 Apache: 30 mm chain gun, Hellfire missiles that lock on and fly to the target on their own, Hydras */
    APACHE(VehicleGeo.APACHE, 230, 0.22f, 1.4, 0.034, 3.2f, 0.32, 30, Gun.M230, Weapon.HELLFIRE, Weapon.HYDRA),
    /** Mi-24 Hind: flying tank, fast but heavy to turn; 12.7 mm gatling, S-8 rocket salvos, FAB-250 bombs, Shturm missiles */
    HIND(VehicleGeo.HIND, 320, 0.15f, 1.5, 0.028, 2.4f, 0.28, 48, Gun.YAKB, Weapon.S8, Weapon.BOMB, Weapon.SHTURM),
    /** Ka-52 Alligator: coaxial rotors, turns fastest and climbs best; fixed 30 mm cannon, laser-beam Vikhr missiles, S-8 */
    KA52(VehicleGeo.KA52, 210, 0.2f, 1.45, 0.036, 4.2f, 0.38, 40, Gun.SHIPUNOV, Weapon.VIKHR, Weapon.S8);

    /** what each fire button does: machine gun / cannon, and the stores under the wings */
    public enum Gun {
        M197(3, 11f, 0f, 0.9f, -3, 110f, -15f, 60f),
        M230(5, 15f, 1.3f, 0.7f, -4, 100f, -10f, 60f),
        YAKB(1, 6f, 0f, 1.3f, -5, 60f, -10f, 60f),
        SHIPUNOV(4, 16f, 1.3f, 0.5f, -4, 9f, -5f, 13f);

        public final int cooldown, fx;
        public final float damage, splash, spread, yawLimit, pitchMin, pitchMax;

        Gun(int cooldown, float damage, float splash, float spread, int fx, float yawLimit, float pitchMin, float pitchMax) {
            this.cooldown = cooldown;
            this.damage = damage;
            this.splash = splash;
            this.spread = spread;
            this.fx = fx;
            this.yawLimit = yawLimit;
            this.pitchMin = pitchMin;
            this.pitchMax = pitchMax;
        }

        /** the gun sits in a turret under the nose (otherwise fixed to the side of the fuselage) */
        public boolean turret() {
            return this != SHIPUNOV;
        }
    }

    public enum Guide { NONE, LOCK, BEAM }

    public enum Mount { PODS, RAILS, BOMBS }

    public enum Weapon {
        /** 70 mm unguided rockets, ripple fired */
        HYDRA("hydra", Mount.PODS, Guide.NONE, 16, 4, 160, 16f, 14f, 2.6f),
        /** 80 mm rockets in big salvos */
        S8("s8", Mount.PODS, Guide.NONE, 40, 2, 240, 18f, 15f, 2.8f),
        /** lock on for a second, then fire and forget */
        HELLFIRE("hellfire", Mount.RAILS, Guide.LOCK, 8, 20, 320, 70f, 50f, 3.4f),
        /** flies along the gunner's line of sight while the target is kept in the sight */
        VIKHR("vikhr", Mount.RAILS, Guide.BEAM, 12, 14, 320, 65f, 45f, 3.0f),
        /** older and slower radio command missile, same idea */
        SHTURM("shturm", Mount.RAILS, Guide.BEAM, 4, 30, 340, 62f, 42f, 3.0f),
        /** FAB-250 free-fall bombs */
        BOMB("bomb", Mount.BOMBS, Guide.NONE, 4, 12, 420, 80f, 60f, 5.0f);

        public final String id;
        public final Mount mount;
        public final Guide guide;
        public final int ammo, cooldown, reload;
        public final float hitArmour, hitSoft, blast;

        Weapon(String id, Mount mount, Guide guide, int ammo, int cooldown, int reload, float hitArmour, float hitSoft, float blast) {
            this.id = id;
            this.mount = mount;
            this.guide = guide;
            this.ammo = ammo;
            this.cooldown = cooldown;
            this.reload = reload;
            this.hitArmour = hitArmour;
            this.hitSoft = hitSoft;
            this.blast = blast;
        }
    }

    public final VehicleGeo.Heli geo;
    public final float health, bulletFactor, yawRate;
    public final double maxSpeed, accel, climb;
    public final int flares;
    public final Gun gun;
    public final Weapon[] weapons;

    HeliType(VehicleGeo.Heli geo, float health, float bulletFactor, double maxSpeed, double accel, float yawRate, double climb, int flares, Gun gun,
             Weapon... weapons) {
        this.geo = geo;
        this.health = health;
        this.bulletFactor = bulletFactor;
        this.maxSpeed = maxSpeed;
        this.accel = accel;
        this.yawRate = yawRate;
        this.climb = climb;
        this.flares = flares;
        this.gun = gun;
        this.weapons = weapons;
    }

    public VehicleKind vehicle() {
        return switch (this) {
            case COBRA -> VehicleKind.COBRA;
            case APACHE -> VehicleKind.APACHE;
            case HIND -> VehicleKind.HIND;
            case KA52 -> VehicleKind.KA52;
        };
    }

    public Item item(BotTeam team) {
        return Reg.vehicleItem(vehicle(), team);
    }

    public int seats() {
        return geo.seats().length;
    }
}
