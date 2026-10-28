package com.gunsmod.entity;

import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/** A launcher that steers its missiles along the operator's line of sight (Vikhr, Shturm, TOW). */
public interface MissileGuider {
    /** {origin, unit direction} of the guidance beam, or null when the operator has stopped guiding */
    @Nullable
    Vec3[] beam();
}
