package com.vehicleworks.vehicle;

import com.vehicleworks.component.ComponentType;
import com.vehicleworks.physics.sim.VehicleSimulation;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.Direction;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** Result of analysing a vehicle structure: the simulation plus an index of its components. */
public final class VehicleLayout {
    public final VehicleSimulation sim;
    public final int structureVersion;
    public ControlProfile detectedProfile = ControlProfile.CAR;
    public Direction forwardDir = Direction.NORTH;

    public final List<Seat> seats = new ArrayList<>();
    public final List<WeaponSlot> weapons = new ArrayList<>();
    public final Long2IntOpenHashMap thrusterIndex = new Long2IntOpenHashMap();
    public final Long2IntOpenHashMap rotorIndex = new Long2IntOpenHashMap();
    public final Long2IntOpenHashMap wheelIndex = new Long2IntOpenHashMap();
    public final Long2IntOpenHashMap tankIndex = new Long2IntOpenHashMap();
    public final Long2IntOpenHashMap wingStripOfBlock = new Long2IntOpenHashMap();
    public final LongArrayList ammoBoxes = new LongArrayList();
    /** aircraft light blocks */
    public final LongArrayList lights = new LongArrayList();
    public final LongArrayList rcus = new LongArrayList();
    public final LongArrayList dynamicBlocks = new LongArrayList();
    public long navComputer = Long.MIN_VALUE;
    public boolean instruments;

    // statistics
    public int blockCount;
    public double dryMass;
    public double wingArea;
    public double neutralPointFwd, comFwd, meanChord = 1, staticMargin;
    public double rotorOffset;
    public double maxStaticThrust;
    public double gasVolume;
    public double sizeX, sizeY, sizeZ;
    public double[] massGrid = new double[0];
    public int gridW, gridH;
    public double gridCell = 1;
    public double gridMinRight, gridMinFwd;
    public final Vector3d forward = new Vector3d(), right = new Vector3d();

    public VehicleLayout(VehicleSimulation sim, int structureVersion) {
        this.sim = sim;
        this.structureVersion = structureVersion;
        wingStripOfBlock.defaultReturnValue(-1);
        thrusterIndex.defaultReturnValue(-1);
        rotorIndex.defaultReturnValue(-1);
        wheelIndex.defaultReturnValue(-1);
        tankIndex.defaultReturnValue(-1);
    }

    public record Seat(long pos, Direction facing, boolean pilot, boolean gunner) {}

    public static final class WeaponSlot {
        public final long pos;
        public final ComponentType type;
        public final Direction facing;
        public final boolean mounted;
        public final long turretBase;
        public final int index;

        public WeaponSlot(long pos, ComponentType type, Direction facing, boolean mounted, long turretBase, int index) {
            this.pos = pos;
            this.type = type;
            this.facing = facing;
            this.mounted = mounted;
            this.turretBase = turretBase;
            this.index = index;
        }
    }

    public Seat pilotSeat() {
        for (Seat s : seats) if (s.pilot()) return s;
        return null;
    }

    public WeaponSlot weaponAt(long pos) {
        for (WeaponSlot w : weapons) if (w.pos == pos) return w;
        return null;
    }

    /** set of component types present (filled by the builder) */
    public final java.util.EnumSet<com.vehicleworks.component.ComponentType> types = java.util.EnumSet.noneOf(com.vehicleworks.component.ComponentType.class);

    public boolean structureHas(com.vehicleworks.component.ComponentType t) {
        return types.contains(t);
    }
}
