package com.vehicleworks.weapon;

import com.vehicleworks.component.ComponentType;
import com.vehicleworks.item.AmmoItem;

/** Fictional gameplay weapon characteristics. */
public enum WeaponSpec {
    MACHINE_GUN(ComponentType.MACHINE_GUN, AmmoItem.Kind.BULLET, 1, 600, 5.0f, 0.5, 250, 50, 0),
    CANNON(ComponentType.CANNON, AmmoItem.Kind.SHELL, 16, 320, 14.0f, 0.15, 8, 80, 2.2f),
    MISSILE_LAUNCHER(ComponentType.MISSILE_LAUNCHER, AmmoItem.Kind.MISSILE, 14, 60, 20.0f, 0.0, 2, 160, 3.2f),
    BOMB_BAY(ComponentType.BOMB_BAY, AmmoItem.Kind.BOMB, 6, 0, 30.0f, 0.0, 4, 120, 4.0f);

    public final ComponentType component;
    public final AmmoItem.Kind ammo;
    /** ticks between shots */
    public final int interval;
    /** muzzle velocity relative to the vehicle (m/s) */
    public final double muzzleVelocity;
    public final float damage;
    /** dispersion (degrees) */
    public final double spreadDeg;
    public final int magazine;
    /** reload duration (ticks) */
    public final int reloadTicks;
    public final float explosionPower;

    WeaponSpec(ComponentType c, AmmoItem.Kind ammo, int interval, double v, float damage, double spread, int magazine, int reload, float explosion) {
        this.component = c;
        this.ammo = ammo;
        this.interval = interval;
        this.muzzleVelocity = v;
        this.damage = damage;
        this.spreadDeg = spread;
        this.magazine = magazine;
        this.reloadTicks = reload;
        this.explosionPower = explosion;
    }

    public static WeaponSpec of(ComponentType t) {
        for (WeaponSpec s : values()) if (s.component == t) return s;
        return null;
    }

    public int defaultChannel() {
        return ordinal();
    }
}
