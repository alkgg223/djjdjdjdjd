package com.gunsmod.gun;

/** Ammunition families. {@code perItem} > 1 means the ammo item is a box whose durability holds the rounds. */
public enum AmmoType {
    PISTOL("ammo_pistol", 60),
    RIFLE("ammo_rifle", 90),
    SHOTGUN("ammo_shotgun", 24),
    SNIPER("ammo_sniper", 20),
    HEAVY("ammo_heavy", 200),
    ROCKET("rocket_ammo", 1),
    GRENADE("grenade_40mm", 1),
    FUEL("fuel_canister", 400);

    public final String itemId;
    public final int perItem;

    AmmoType(String itemId, int perItem) {
        this.itemId = itemId;
        this.perItem = perItem;
    }

    public boolean isBox() {
        return perItem > 1;
    }
}
