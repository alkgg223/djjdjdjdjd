package com.gunsmod.block.airdef;

import com.gunsmod.entity.Aircraft;
import com.gunsmod.entity.HelicopterEntity;
import com.gunsmod.entity.JetEntity;
import com.gunsmod.entity.VehicleEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * What the air defence works with (server side): every aircraft that has ticked lately, and where the radars and the
 * launchers stand (so they can find each other without searching the world).
 */
public final class AirTracker {
    private AirTracker() {}

    private static final Set<VehicleEntity> AIRCRAFT = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<RadarBlockEntity> RADARS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<DefenseBlockEntity> LAUNCHERS = Collections.newSetFromMap(new WeakHashMap<>());

    public static void add(VehicleEntity v) {
        AIRCRAFT.add(v);
    }

    /** airborne aircraft of this level */
    public static List<VehicleEntity> flying(Level level) {
        List<VehicleEntity> l = new ArrayList<>();
        for (VehicleEntity v : AIRCRAFT) {
            if (v.level() == level && v.isAlive() && !v.isRemoved() && v instanceof Aircraft a && a.isAirborne()) l.add(v);
        }
        return l;
    }

    /** how strongly an aircraft shows on radar */
    public static float signature(VehicleEntity v) {
        if (v instanceof Aircraft a && a.stealth()) return 0f;
        if (v instanceof JetEntity j) return j.type.signature;
        if (v instanceof com.gunsmod.entity.DroneEntity d) return d.fpv() ? 0.15f : 0.45f;
        return v instanceof HelicopterEntity ? 0.8f : 1f;
    }

    static void radar(RadarBlockEntity r, boolean on) {
        if (on) RADARS.add(r);
        else RADARS.remove(r);
    }

    static void launcher(DefenseBlockEntity d, boolean on) {
        if (on) LAUNCHERS.add(d);
        else LAUNCHERS.remove(d);
    }

    public static List<RadarBlockEntity> radarsNear(Level level, BlockPos at, double radius) {
        List<RadarBlockEntity> l = new ArrayList<>();
        for (RadarBlockEntity r : RADARS) {
            if (r.getLevel() == level && !r.isRemoved() && r.getBlockPos().distSqr(at) <= radius * radius) l.add(r);
        }
        l.sort((a, b) -> Double.compare(a.getBlockPos().distSqr(at), b.getBlockPos().distSqr(at)));
        return l;
    }

    public static List<DefenseBlockEntity> launchersNear(Level level, BlockPos at, double radius) {
        List<DefenseBlockEntity> l = new ArrayList<>();
        for (DefenseBlockEntity d : LAUNCHERS) {
            if (d.getLevel() == level && !d.isRemoved() && d.getBlockPos().distSqr(at) <= radius * radius) l.add(d);
        }
        return l;
    }
}
