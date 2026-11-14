package com.vehicleworks.component;

import com.vehicleworks.physics.aero.AirfoilProfile;
import com.vehicleworks.physics.aero.ControlRole;
import com.vehicleworks.physics.aero.WingParams;
import com.vehicleworks.physics.aero.WingType;
import net.minecraft.nbt.CompoundTag;

/** Converts a wing component's configuration tag into aerodynamic parameters. */
public final class WingConfig {
    private WingConfig() {}

    public static WingParams toParams(ComponentType type, CompoundTag c) {
        WingParams p = new WingParams();
        p.type = WingType.byIndex(type.property("wing_type").getInt(c));
        p.profile = AirfoilProfile.byIndex(type.property("airfoil").getInt(c));
        p.role = ControlRole.byIndex(type.property("role").getInt(c));
        p.liftScale = type.property("lift_scale").get(c);
        p.chord = type.property("chord").get(c);
        p.incidenceDeg = type.property("incidence").get(c);
        p.dragScale = type.property("drag_scale").get(c);
        double stall = type.property("stall_angle").get(c);
        p.stallDeg = stall <= 0.01 ? -1 : stall;
        p.flapMaxDeg = type.property("flap_max").get(c);
        p.controlMaxDeg = type.property("control_max").get(c);
        p.spoilerMax = type.property("spoiler_max").get(c);
        p.sweepDeg = type.property("sweep").get(c);
        p.sweepMaxDeg = type.property("sweep_max").get(c);
        p.responsiveness = type.property("responsiveness").get(c);
        p.invert = type.property("invert").getBool(c);
        return p.sanitize();
    }
}
