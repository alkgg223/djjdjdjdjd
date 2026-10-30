package com.gunsmod.entity;

/** Helicopters and jets: what air defence radars and air-to-air missiles look for. */
public interface Aircraft {
    boolean isAirborne();

    /** a homing missile is on its way to this aircraft */
    void missileWarning(GuidedMissileEntity m);

    /** a fire control radar is tracking this aircraft */
    void radarWarning();
}
