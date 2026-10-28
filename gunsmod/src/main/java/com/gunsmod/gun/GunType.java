package com.gunsmod.gun;

/** The 25 guns and their stats. Damage is in health points (2 = one heart). Times are in ticks. */
public enum GunType {
    //  id               category           ammo             mag  dmg  delay mode            hip  ads  recoil reload range pellets kind                 sound
    M1911("m1911", Category.PISTOL, AmmoType.PISTOL, 7, 7.0f, 4, FireMode.SEMI, 2.2f, 0.7f, 4.0f, 32, 50, 1, Kind.BULLET, "shot_pistol"),
    P9("p9", Category.PISTOL, AmmoType.PISTOL, 17, 5.5f, 3, FireMode.SEMI, 2.0f, 0.6f, 3.0f, 30, 50, 1, Kind.BULLET, "shot_pistol"),
    MAGNUM50("magnum50", Category.PISTOL, AmmoType.PISTOL, 7, 12.0f, 8, FireMode.SEMI, 2.5f, 0.5f, 9.0f, 38, 60, 1, Kind.BULLET, "shot_magnum"),
    REVOLVER("revolver", Category.PISTOL, AmmoType.PISTOL, 6, 10.0f, 7, FireMode.SEMI, 2.0f, 0.4f, 7.0f, 50, 60, 1, Kind.BULLET, "shot_magnum"),
    SMG5("smg5", Category.SMG, AmmoType.PISTOL, 30, 4.5f, 2, FireMode.AUTO, 3.0f, 1.2f, 1.6f, 40, 45, 1, Kind.BULLET, "shot_smg"),
    MICRO_SMG("micro_smg", Category.SMG, AmmoType.PISTOL, 32, 4.0f, 2, FireMode.AUTO, 4.5f, 1.8f, 1.8f, 36, 40, 1, Kind.BULLET, "shot_smg"),
    VORTEX("vortex", Category.SMG, AmmoType.PISTOL, 25, 4.2f, 1, FireMode.AUTO, 3.2f, 1.2f, 1.2f, 38, 45, 1, Kind.BULLET, "shot_smg"),
    PDW("pdw", Category.SMG, AmmoType.PISTOL, 50, 4.5f, 2, FireMode.AUTO, 3.0f, 1.2f, 1.3f, 45, 50, 1, Kind.BULLET, "shot_smg"),
    AK47("ak47", Category.RIFLE, AmmoType.RIFLE, 30, 7.5f, 2, FireMode.AUTO, 3.0f, 0.8f, 3.0f, 45, 90, 1, Kind.BULLET, "shot_rifle"),
    M4A1("m4a1", Category.RIFLE, AmmoType.RIFLE, 30, 6.5f, 2, FireMode.AUTO, 2.5f, 0.6f, 2.2f, 42, 90, 1, Kind.BULLET, "shot_carbine"),
    M16A4("m16a4", Category.RIFLE, AmmoType.RIFLE, 30, 7.0f, 2, FireMode.BURST, 2.0f, 0.4f, 2.4f, 42, 100, 1, Kind.BULLET, "shot_carbine"),
    BR17("br17", Category.RIFLE, AmmoType.RIFLE, 20, 8.5f, 3, FireMode.AUTO, 2.5f, 0.5f, 3.5f, 46, 100, 1, Kind.BULLET, "shot_rifle"),
    BR77("br77", Category.RIFLE, AmmoType.RIFLE, 30, 6.5f, 2, FireMode.AUTO, 2.4f, 0.5f, 2.0f, 44, 95, 1, Kind.BULLET, "shot_carbine"),
    TACTICAL_RIFLE("tactical_rifle", Category.RIFLE, AmmoType.RIFLE, 30, 6.5f, 2, FireMode.AUTO, 2.4f, 0.6f, 2.1f, 42, 90, 1, Kind.BULLET, "shot_carbine"),
    PUMP_SHOTGUN("pump_shotgun", Category.SHOTGUN, AmmoType.SHOTGUN, 6, 3.2f, 16, FireMode.SEMI, 7.0f, 5.0f, 8.0f, 50, 30, 9, Kind.BULLET, "shot_shotgun"),
    AUTO_SHOTGUN("auto_shotgun", Category.SHOTGUN, AmmoType.SHOTGUN, 20, 2.6f, 4, FireMode.AUTO, 8.0f, 6.0f, 5.0f, 60, 28, 8, Kind.BULLET, "shot_shotgun"),
    DOUBLE_BARREL("double_barrel", Category.SHOTGUN, AmmoType.SHOTGUN, 2, 3.6f, 5, FireMode.SEMI, 8.0f, 6.0f, 10.0f, 40, 25, 10, Kind.BULLET, "shot_shotgun"),
    M24("m24", Category.SNIPER, AmmoType.SNIPER, 5, 20.0f, 30, FireMode.SEMI, 3.0f, 0.05f, 10.0f, 60, 220, 1, Kind.BULLET, "shot_sniper"),
    SVD("svd", Category.SNIPER, AmmoType.SNIPER, 10, 13.0f, 6, FireMode.SEMI, 2.5f, 0.15f, 6.0f, 50, 180, 1, Kind.BULLET, "shot_sniper"),
    M107("m107", Category.SNIPER, AmmoType.SNIPER, 10, 28.0f, 12, FireMode.SEMI, 4.0f, 0.1f, 14.0f, 70, 260, 1, Kind.BULLET, "shot_heavy"),
    M249("m249", Category.HEAVY, AmmoType.HEAVY, 100, 6.5f, 2, FireMode.AUTO, 4.0f, 1.5f, 2.4f, 90, 90, 1, Kind.BULLET, "shot_lmg"),
    MINIGUN("minigun", Category.HEAVY, AmmoType.HEAVY, 300, 5.0f, 1, FireMode.AUTO, 4.5f, 3.0f, 0.8f, 110, 80, 1, Kind.BULLET, "shot_minigun"),
    RPG7("rpg7", Category.EXPLOSIVE, AmmoType.ROCKET, 1, 0.0f, 20, FireMode.SEMI, 1.5f, 0.3f, 8.0f, 60, 200, 1, Kind.ROCKET, "rocket_launch"),
    M79("m79", Category.EXPLOSIVE, AmmoType.GRENADE, 1, 0.0f, 20, FireMode.SEMI, 2.0f, 0.6f, 6.0f, 40, 120, 1, Kind.GRENADE, "shot_grenade_launcher"),
    FLAMETHROWER("flamethrower", Category.SPECIAL, AmmoType.FUEL, 200, 2.0f, 2, FireMode.AUTO, 6.0f, 6.0f, 0.3f, 60, 9, 1, Kind.FLAME, "flamethrower_loop");

    public enum Category { PISTOL, SMG, RIFLE, SHOTGUN, SNIPER, HEAVY, EXPLOSIVE, SPECIAL }

    public enum FireMode { SEMI, AUTO, BURST }

    public enum Kind { BULLET, ROCKET, GRENADE, FLAME }

    public final String id;
    public final Category category;
    public final AmmoType ammo;
    public final int magSize;
    public final float damage;
    public final int fireDelay;
    public final FireMode mode;
    public final float hipSpread;
    public final float adsSpread;
    public final float recoil;
    public final int reloadTicks;
    public final int range;
    public final int pellets;
    public final Kind kind;
    public final String sound;

    GunType(String id, Category category, AmmoType ammo, int magSize, float damage, int fireDelay, FireMode mode, float hipSpread,
            float adsSpread, float recoil, int reloadTicks, int range, int pellets, Kind kind, String sound) {
        this.id = id;
        this.category = category;
        this.ammo = ammo;
        this.magSize = magSize;
        this.damage = damage;
        this.fireDelay = fireDelay;
        this.mode = mode;
        this.hipSpread = hipSpread;
        this.adsSpread = adsSpread;
        this.recoil = recoil;
        this.reloadTicks = reloadTicks;
        this.range = range;
        this.pellets = pellets;
        this.kind = kind;
        this.sound = sound;
    }

    public float headshotMultiplier() {
        return category == Category.SNIPER ? 2.2f : category == Category.SHOTGUN ? 1.3f : 1.6f;
    }

    /** built-in optic zoom (only the bullpup rifle has one) */
    public float builtInZoom() {
        return this == BR77 ? 1.6f : 1.0f;
    }

    /** min / max engagement distance used by the bots */
    public float preferredMin() {
        return switch (category) {
            case PISTOL -> 6;
            case SMG -> 5;
            case RIFLE, HEAVY -> 10;
            case SHOTGUN -> 2;
            case SNIPER -> 22;
            case EXPLOSIVE -> 14;
            case SPECIAL -> 2;
        };
    }

    public float preferredMax() {
        return switch (category) {
            case PISTOL -> 18;
            case SMG -> 16;
            case RIFLE, HEAVY -> 32;
            case SHOTGUN -> 8;
            case SNIPER -> 60;
            case EXPLOSIVE -> 40;
            case SPECIAL -> 7;
        };
    }

    public boolean supports(Attachment.Slot slot) {
        if (kind != Kind.BULLET) {
            return slot == Attachment.Slot.SCOPE && this != FLAMETHROWER;
        }
        return switch (slot) {
            case SCOPE -> this != MINIGUN && this != DOUBLE_BARREL;
            case MUZZLE -> category != Category.SHOTGUN && this != MINIGUN;
            case GRIP -> category != Category.PISTOL && this != MICRO_SMG;
            case LASER -> true;
            case MAG -> this != REVOLVER && this != DOUBLE_BARREL && this != PUMP_SHOTGUN;
        };
    }

    public static GunType byId(int ordinal) {
        GunType[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : AK47;
    }
}
