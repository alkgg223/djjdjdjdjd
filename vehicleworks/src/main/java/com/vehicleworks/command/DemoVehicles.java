package com.vehicleworks.command;

import com.vehicleworks.block.ComponentBlock;
import com.vehicleworks.block.ComponentBlockEntity;
import com.vehicleworks.block.WingOrientation;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.network.ServerActions;
import com.vehicleworks.registry.ModBlocks;
import com.vehicleworks.vehicle.VehicleStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Ready-made example vehicles, built block by block exactly as a player would. Local
 * coordinates: the core is at 0,0,0 facing north (forward = −Z, right = +X, up = +Y).
 */
public final class DemoVehicles {
    public enum Demo { TRAINER, FIGHTER, AIRLINER, CAR, BOAT, HELICOPTER, BALLOON, AIRSHIP, SUBMARINE;
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    private DemoVehicles() {}

    public static final class B {
        public final VehicleStructure s = new VehicleStructure();

        public B put(int x, int y, int z, BlockState st) {
            s.put(BlockPos.asLong(x, y, z), st, ServerActions.defaultBeTag(st));
            return this;
        }

        public B block(int x, int y, int z, net.minecraft.world.level.block.Block b) {
            return put(x, y, z, b.defaultBlockState());
        }

        public B c(int x, int y, int z, ComponentType t) {
            return c(x, y, z, t, null, null);
        }

        public B c(int x, int y, int z, ComponentType t, Direction facing) {
            return c(x, y, z, t, facing, null);
        }

        public B c(int x, int y, int z, ComponentType t, Direction facing, Consumer<CompoundTag> config) {
            BlockState st = ModBlocks.get(t).defaultBlockState();
            if (facing != null) {
                if (st.hasProperty(ComponentBlock.FACING)) st = st.setValue(ComponentBlock.FACING, facing);
                else if (st.hasProperty(ComponentBlock.HORIZONTAL_FACING) && facing.getAxis() != Direction.Axis.Y) st = st.setValue(ComponentBlock.HORIZONTAL_FACING, facing);
            }
            put(x, y, z, st);
            if (config != null) {
                CompoundTag be = s.blockEntity(BlockPos.asLong(x, y, z));
                CompoundTag cfg = be.getCompound(ComponentBlockEntity.CONFIG);
                config.accept(cfg);
                be.put(ComponentBlockEntity.CONFIG, cfg);
            }
            return this;
        }

        public B wing(int x, int y, int z, ComponentType t, WingOrientation o, Consumer<CompoundTag> config) {
            BlockState st = ModBlocks.get(t).defaultBlockState().setValue(ComponentBlock.PLANE, o);
            put(x, y, z, st);
            if (config != null) {
                CompoundTag be = s.blockEntity(BlockPos.asLong(x, y, z));
                CompoundTag cfg = be.getCompound(ComponentBlockEntity.CONFIG);
                config.accept(cfg);
                be.put(ComponentBlockEntity.CONFIG, cfg);
            }
            return this;
        }

        public B data(int x, int y, int z, Consumer<CompoundTag> data) {
            CompoundTag be = s.blockEntity(BlockPos.asLong(x, y, z));
            if (be == null) return this;
            CompoundTag d = be.getCompound(ComponentBlockEntity.DATA);
            data.accept(d);
            be.put(ComponentBlockEntity.DATA, d);
            return this;
        }

        public B fuel(int x, int y, int z) {
            c(x, y, z, ComponentType.FUEL_TANK);
            return data(x, y, z, d -> d.putDouble("Fuel", 300));
        }

        public B link(int rx, int ry, int rz, int tx, int ty, int tz, int channel) {
            return data(rx, ry, rz, d -> {
                ListTag l = d.getList("Links", 10);
                CompoundTag t = new CompoundTag();
                t.putIntArray("Off", new int[]{tx - rx, ty - ry, tz - rz});
                t.putInt("Ch", channel);
                t.putInt("Mode", 0);
                l.add(t);
                d.put("Links", l);
            });
        }
    }

    public static VehicleStructure build(Demo demo) {
        B b = new B();
        switch (demo) {
            case TRAINER -> trainer(b);
            case FIGHTER -> fighter(b);
            case AIRLINER -> airliner(b);
            case CAR -> car(b);
            case BOAT -> boat(b);
            case HELICOPTER -> helicopter(b);
            case BALLOON -> balloon(b);
            case AIRSHIP -> airship(b);
            case SUBMARINE -> submarine(b);
        }
        return b.s;
    }

    private static Consumer<CompoundTag> chord(double c) {
        return t -> t.putDouble("chord", c);
    }

    private static void trainer(B b) {
        // fuselage (birch) along z = -4 … 6
        for (int z = -4; z <= 6; z++) if (z != 0) b.block(0, 0, z, Blocks.BIRCH_PLANKS);
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH);
        b.fuel(0, 0, -1);
        b.c(0, 0, -5, ComponentType.PISTON_ENGINE, Direction.NORTH, t -> t.putDouble("power_scale", 1.5));
        b.c(0, 0, -6, ComponentType.PROPELLER, Direction.NORTH, t -> t.putDouble("diameter", 2.8));
        b.c(0, 1, 0, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(0, 1, -1, ComponentType.FLIGHT_INSTRUMENTS, Direction.SOUTH);
        b.block(0, 1, -2, Blocks.GLASS);
        b.block(0, 1, 1, Blocks.BIRCH_PLANKS);
        // main wing: leading edge row z=-1, trailing row z=0 with flaps and ailerons
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 1; i <= 8; i++) {
                int x = side * i;
                b.wing(x, 0, -1, ComponentType.WING, WingOrientation.HORIZONTAL, chord(1.4));
                ComponentType trailing = i <= 3 ? ComponentType.FLAP : (i >= 6 ? ComponentType.AILERON : ComponentType.WING);
                b.wing(x, 0, 0, trailing, WingOrientation.HORIZONTAL, chord(1.2));
            }
            // horizontal tail
            for (int i = 1; i <= 2; i++) {
                b.wing(side * i, 0, 5, ComponentType.WING, WingOrientation.HORIZONTAL, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", -1.5); t.putDouble("chord", 1.4); });
                b.wing(side * i, 0, 6, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, t -> { t.putDouble("control_max", 25); t.putDouble("chord", 1.2); });
            }
        }
        // vertical fin
        b.wing(0, 1, 5, ComponentType.WING, WingOrientation.VERTICAL_X, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", 0); });
        b.wing(0, 1, 6, ComponentType.RUDDER, WingOrientation.VERTICAL_X, null);
        b.wing(0, 2, 6, ComponentType.RUDDER, WingOrientation.VERTICAL_X, null);
        // fixed tricycle gear
        b.c(0, -1, -3, ComponentType.LANDING_GEAR, Direction.NORTH, t -> t.putBoolean("retractable", false));
        b.c(-2, -1, 1, ComponentType.LANDING_GEAR, Direction.NORTH, t -> t.putBoolean("retractable", false));
        b.c(2, -1, 1, ComponentType.LANDING_GEAR, Direction.NORTH, t -> t.putBoolean("retractable", false));
        b.block(-2, 0, 1, Blocks.BIRCH_PLANKS);
        b.block(2, 0, 1, Blocks.BIRCH_PLANKS);
        b.block(0, 0, -3, Blocks.BIRCH_PLANKS);
    }

    private static void fighter(B b) {
        var hull = Blocks.LIGHT_GRAY_CONCRETE;
        for (int z = -6; z <= 6; z++) if (z != 0) b.block(0, 0, z, hull);
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH);
        b.block(0, 0, -7, Blocks.GRAY_CONCRETE);
        b.fuel(0, 0, -1);
        b.fuel(0, 0, -2);
        b.fuel(0, 0, -3);
        b.c(0, 0, 1, ComponentType.FUEL_PUMP, Direction.NORTH);
        b.c(0, -1, 1, ComponentType.FUEL_PUMP, Direction.NORTH);
        b.c(0, -1, 0, ComponentType.HYDRAULIC_PUMP, Direction.NORTH);
        b.c(0, 0, 2, ComponentType.ENGINE_STARTER, Direction.NORTH);
        b.c(0, 0, 7, ComponentType.JET_ENGINE, Direction.NORTH);
        b.c(0, 1, -3, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(0, 1, -4, ComponentType.FLIGHT_INSTRUMENTS, Direction.SOUTH);
        b.c(0, 1, -2, ComponentType.REMOTE_CONTROL_UNIT, Direction.NORTH);
        b.c(0, 1, -1, ComponentType.NAVIGATION_COMPUTER, Direction.NORTH);
        b.block(0, 1, -5, Blocks.LIGHT_GRAY_STAINED_GLASS);
        b.block(-1, 1, -3, Blocks.LIGHT_GRAY_STAINED_GLASS);
        b.block(1, 1, -3, Blocks.LIGHT_GRAY_STAINED_GLASS);
        b.c(0, 1, 0, ComponentType.AMMO_BOX, Direction.NORTH);
        b.data(0, 1, 0, d -> { CompoundTag a = new CompoundTag(); a.putInt("BULLET", 1200); a.putInt("MISSILE", 4); d.put("Ammo", a); });
        // swept main wing: leading row z=0, trailing row z=1 (flaps inboard, ailerons outboard), strakes at z=-1
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 1; i <= 7; i++) {
                b.wing(side * i, 0, 0, ComponentType.SWEPT_WING, WingOrientation.HORIZONTAL, t -> { t.putDouble("chord", 1.5); t.putDouble("incidence", 3); t.putDouble("sweep", 25); });
                ComponentType tr = i <= 3 ? ComponentType.FLAP : (i >= 5 ? ComponentType.AILERON : ComponentType.SWEPT_WING);
                b.wing(side * i, 0, 1, tr, WingOrientation.HORIZONTAL, t -> { t.putDouble("chord", 1.3); t.putDouble("sweep", 25); });
                if (i <= 2) b.wing(side * i, 0, -1, ComponentType.SWEPT_WING, WingOrientation.HORIZONTAL, t -> { t.putDouble("chord", 1.2); t.putDouble("incidence", 3); t.putDouble("sweep", 25); });
            }
            // horizontal tail
            for (int i = 1; i <= 3; i++) {
                b.wing(side * i, 0, 5, ComponentType.WING, WingOrientation.HORIZONTAL, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", -1); t.putDouble("chord", 1.3); });
                b.wing(side * i, 0, 6, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, t -> { t.putDouble("chord", 1.2); t.putDouble("control_max", 25); });
            }
        }
        // fin
        for (int y = 1; y <= 2; y++) {
            b.wing(0, y, 5, ComponentType.WING, WingOrientation.VERTICAL_X, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", 0); t.putDouble("chord", 1.3); });
            b.wing(0, y, 6, ComponentType.RUDDER, WingOrientation.VERTICAL_X, t -> t.putDouble("chord", 1.2));
        }
        // guns in the nose, missiles under the wings
        for (int side = -1; side <= 1; side += 2) {
            b.c(side, 0, -5, ComponentType.WEAPON_MOUNT, Direction.NORTH);
            b.c(side, 0, -6, ComponentType.MACHINE_GUN, Direction.NORTH);
            b.data(side, 0, -6, d -> d.putInt("Mag", 250));
            b.c(side * 4, -1, 0, ComponentType.WEAPON_MOUNT, Direction.NORTH);
            b.c(side * 4, -1, -1, ComponentType.MISSILE_LAUNCHER, Direction.NORTH);
            b.data(side * 4, -1, -1, d -> d.putInt("Mag", 2));
        }
        b.link(0, 1, -2, -1, 0, -6, 0);
        b.link(0, 1, -2, 1, 0, -6, 0);
        b.link(0, 1, -2, -4, -1, -1, 1);
        b.link(0, 1, -2, 4, -1, -1, 1);
        // retractable tricycle gear, mains just behind the centre of mass
        b.c(0, -1, -4, ComponentType.LANDING_GEAR, Direction.NORTH);
        b.c(-2, -1, 2, ComponentType.LANDING_GEAR, Direction.NORTH);
        b.c(2, -1, 2, ComponentType.LANDING_GEAR, Direction.NORTH);
        b.block(-2, 0, 2, hull);
        b.block(2, 0, 2, hull);
    }

    /** Twin-engine narrow-body airliner (A320-style layout): walk-in cabin, swept wing, two turbofans. */
    private static void airliner(B b) {
        var floor = Blocks.WHITE_CONCRETE;
        var skin = Blocks.WHITE_WOOL;
        var stripe = Blocks.LIGHT_BLUE_WOOL;
        // fuselage z = -12 (nose) … 14 (tail): concrete floor, wool skin, walk-in cabin 1 x 2 high
        for (int z = -12; z <= 14; z++) {
            for (int x = -1; x <= 1; x++) if (!(x == 0 && z == 0)) b.block(x, 0, z, floor);
            b.block(-1, 1, z, z % 3 == 0 ? Blocks.GLASS : stripe);
            b.block(1, 1, z, z % 3 == 0 ? Blocks.GLASS : stripe);
            b.block(-1, 2, z, skin);
            b.block(1, 2, z, skin);
            for (int x = -1; x <= 1; x++) b.block(x, 3, z, skin);
        }
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH);
        for (int y = 1; y <= 2; y++) b.block(0, y, -12, Blocks.WHITE_STAINED_GLASS);
        b.block(0, 1, 14, skin);
        b.block(0, 2, 14, skin);
        b.block(0, 0, -13, Blocks.LIGHT_GRAY_CONCRETE);
        b.block(0, 1, -13, Blocks.LIGHT_GRAY_CONCRETE);
        // cockpit
        b.c(0, 1, -10, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(0, 1, -11, ComponentType.FLIGHT_INSTRUMENTS, Direction.SOUTH);
        b.c(0, 2, -11, ComponentType.NAVIGATION_COMPUTER, Direction.SOUTH);
        for (int z = -7; z <= 9; z += 2) b.c(0, 1, z, ComponentType.PASSENGER_SEAT, Direction.NORTH);
        // systems in the floor
        for (int z = -2; z <= 3; z++) if (z != 0) b.fuel(0, 0, z);
        b.c(0, -1, -1, ComponentType.FUEL_PUMP, Direction.NORTH);
        b.c(0, -1, 1, ComponentType.FUEL_PUMP, Direction.NORTH);
        b.c(0, -1, 3, ComponentType.HYDRAULIC_PUMP, Direction.NORTH);
        b.c(0, -1, 5, ComponentType.ENGINE_STARTER, Direction.NORTH);
        // swept wing, tapered: 4 chord rows at the root, 2 at the tip
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 2; i <= 14; i++) {
                int rows = i <= 6 ? 4 : (i <= 10 ? 3 : 2);
                int zLead = -1 + (i - 2) / 4; // leading edge moves aft = sweep
                for (int r = 0; r < rows; r++) {
                    int z = zLead + r;
                    boolean trailing = r == rows - 1;
                    ComponentType t = trailing ? (i <= 7 ? ComponentType.FLAP : (i >= 11 ? ComponentType.AILERON : ComponentType.SWEPT_WING)) : ComponentType.SWEPT_WING;
                    b.wing(side * i, 0, z, t, WingOrientation.HORIZONTAL, c -> { c.putDouble("sweep", 25); c.putDouble("chord", 1.2); c.putDouble("incidence", 3); c.putInt("airfoil", 4); });
                }
            }
            // turbofans under the wing
            b.c(side * 5, -1, 0, ComponentType.TURBOFAN_ENGINE, Direction.NORTH, c -> c.putDouble("power_scale", 1.5));
            // horizontal tail
            for (int i = 2; i <= 6; i++) {
                b.wing(side * i, 1, 12, ComponentType.WING, WingOrientation.HORIZONTAL, c -> { c.putInt("airfoil", 1); c.putDouble("incidence", -1.5); c.putDouble("chord", 1.3); });
                b.wing(side * i, 1, 13, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, c -> { c.putDouble("chord", 1.2); c.putDouble("control_max", 25); });
            }
        }
        // vertical fin
        for (int y = 4; y <= 8; y++) {
            b.wing(0, y, 12, ComponentType.WING, WingOrientation.VERTICAL_X, c -> { c.putInt("airfoil", 1); c.putDouble("incidence", 0); c.putDouble("chord", 1.3); });
            b.wing(0, y, 13, ComponentType.RUDDER, WingOrientation.VERTICAL_X, c -> c.putDouble("chord", 1.2));
        }
        // retractable gear: nose and two mains just behind the centre of mass
        b.c(0, -1, -9, ComponentType.LANDING_GEAR, Direction.NORTH);
        b.c(-2, -1, 3, ComponentType.LANDING_GEAR, Direction.NORTH, c -> c.putDouble("radius", 0.55));
        b.c(2, -1, 3, ComponentType.LANDING_GEAR, Direction.NORTH, c -> c.putDouble("radius", 0.55));
        b.block(-2, 0, 3, floor); // connects the main gear to the fuselage
        b.block(2, 0, 3, floor);
    }

    private static void car(B b) {
        var body = Blocks.RED_CONCRETE;
        for (int x = -1; x <= 1; x++)
            for (int z = -2; z <= 2; z++) if (!(x == 0 && z == 0)) b.block(x, 0, z, body);
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, t -> t.putInt("profile", 3));
        b.c(0, 0, -2, ComponentType.PISTON_ENGINE, Direction.NORTH);
        b.fuel(0, 0, 2);
        b.c(-1, 1, 0, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(1, 1, 0, ComponentType.PASSENGER_SEAT, Direction.NORTH);
        b.block(-1, 1, -1, Blocks.GLASS_PANE);
        b.block(1, 1, -1, Blocks.GLASS_PANE);
        b.block(0, 1, -1, Blocks.GLASS_PANE);
        b.block(0, 1, 1, Blocks.RED_CONCRETE);
        b.block(-1, 1, 2, Blocks.RED_CONCRETE);
        b.block(1, 1, 2, Blocks.RED_CONCRETE);
        for (int side = -1; side <= 1; side += 2) {
            b.c(side * 2, 0, -2, ComponentType.WHEEL, Direction.NORTH);
            b.c(side * 2, 0, 2, ComponentType.WHEEL, Direction.NORTH);
        }
    }

    private static void boat(B b) {
        var hull = Blocks.SPRUCE_PLANKS;
        for (int x = -2; x <= 2; x++)
            for (int z = -4; z <= 4; z++) {
                if (!(x == 0 && z == 0) && !(x == 0 && z == 4)) b.block(x, 0, z, hull);
                if (Math.abs(x) == 2 || Math.abs(z) == 4) b.block(x, 1, z, hull);
            }
        b.block(0, 0, -5, hull);
        b.block(0, 1, -5, hull);
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, t -> t.putInt("profile", 4));
        b.c(0, 0, 4, ComponentType.PISTON_ENGINE, Direction.NORTH);
        b.c(0, 0, 5, ComponentType.MARINE_PROPELLER, Direction.NORTH);
        b.wing(0, -1, 4, ComponentType.RUDDER, WingOrientation.VERTICAL_X, t -> t.putDouble("control_max", 30));
        b.c(0, 1, 2, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.fuel(1, 1, 3);
        b.c(-1, 1, -2, ComponentType.PASSENGER_SEAT, Direction.NORTH);
        b.c(1, 1, -2, ComponentType.PASSENGER_SEAT, Direction.NORTH);
    }

    private static void helicopter(B b) {
        var body = Blocks.YELLOW_WOOL;
        for (int x = -1; x <= 1; x++)
            for (int z = -2; z <= 2; z++) {
                if (!(x == 0 && z == 0)) b.block(x, 0, z, body);
                if (Math.abs(x) == 1 && Math.abs(z) <= 2 && z != -2) b.block(x, 1, z, body);
            }
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH);
        for (int x = -1; x <= 1; x++) b.block(x, 1, -2, Blocks.GLASS_PANE);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 2; z++) b.block(x, 2, z, body);
        b.c(0, 1, -1, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(0, 1, 1, ComponentType.PASSENGER_SEAT, Direction.NORTH);
        b.c(0, 3, 0, ComponentType.MAIN_ROTOR, Direction.UP);
        b.c(0, 3, 1, ComponentType.TURBOPROP_ENGINE, Direction.NORTH);
        b.c(-1, 3, 1, ComponentType.ENGINE_STARTER, Direction.NORTH);
        b.fuel(0, 1, 0);
        b.fuel(0, 0, -1);
        b.c(1, 3, 1, ComponentType.FUEL_PUMP, Direction.NORTH);
        // tail boom
        for (int z = 3; z <= 8; z++) b.block(0, 2, z, body);
        b.c(1, 2, 8, ComponentType.TAIL_ROTOR, Direction.EAST);
        b.wing(0, 3, 8, ComponentType.WING, WingOrientation.VERTICAL_X, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", 0); });
        b.wing(-1, 2, 7, ComponentType.WING, WingOrientation.HORIZONTAL, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", 0); });
        b.wing(1, 2, 7, ComponentType.WING, WingOrientation.HORIZONTAL, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", 0); });
        // skids
        for (int side = -1; side <= 1; side += 2) {
            b.c(side * 2, 0, -1, ComponentType.LANDING_GEAR, Direction.NORTH, t -> { t.putBoolean("retractable", false); t.putInt("steering", 2); });
            b.c(side * 2, 0, 1, ComponentType.LANDING_GEAR, Direction.NORTH, t -> { t.putBoolean("retractable", false); t.putInt("steering", 2); });
        }
    }

    private static void balloon(B b) {
        // basket
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (!(x == 0 && z == 0)) b.block(x, 0, z, Blocks.OAK_SLAB);
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, t -> t.putInt("profile", 6));
        b.c(0, 1, 1, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.fuel(-1, 1, -1);
        // ropes
        for (int y = 1; y <= 4; y++) { b.block(-1, y, 0, Blocks.OAK_FENCE); b.block(1, y, 0, Blocks.OAK_FENCE); }
        b.c(0, 4, 0, ComponentType.BURNER);
        b.block(-1, 5, 0, Blocks.OAK_FENCE);
        b.block(1, 5, 0, Blocks.OAK_FENCE);
        // envelope sphere radius 7 centred at y = 12 with a mouth at the bottom
        int r = 7, cy = 12;
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d > r + 0.5 || d < r - 0.5) continue;
                    if (y < -r + 2 && Math.abs(x) <= 1 && Math.abs(z) <= 1) continue; // mouth
                    b.c(x, cy + y, z, ComponentType.BALLOON_ENVELOPE);
                }
        // connect the ropes to the envelope
        for (int y = 6; y <= 6; y++) { b.block(-1, y, 0, Blocks.OAK_FENCE); b.block(1, y, 0, Blocks.OAK_FENCE); }
    }

    private static void airship(B b) {
        // gondola
        for (int z = -3; z <= 3; z++) for (int x = -1; x <= 1; x++) if (!(x == 0 && z == 0)) b.block(x, 0, z, Blocks.DARK_OAK_SLAB);
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, t -> t.putInt("profile", 6));
        b.c(0, 1, -2, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(0, 1, -3, ComponentType.FLIGHT_INSTRUMENTS, Direction.SOUTH);
        b.fuel(0, 1, 2);
        for (int side = -1; side <= 1; side += 2) {
            b.c(side * 2, 0, 1, ComponentType.PISTON_ENGINE, Direction.NORTH, t -> t.putDouble("power_scale", 0.6));
            b.c(side * 2, 0, 0, ComponentType.PROPELLER, Direction.NORTH, t -> t.putDouble("diameter", 2.0));
        }
        for (int y = 1; y <= 2; y++) { b.block(-1, y, 0, Blocks.DARK_OAK_FENCE); b.block(1, y, 0, Blocks.DARK_OAK_FENCE); }
        // envelope: sealed box 7 x 6 x 22 (helium)
        for (int x = -3; x <= 3; x++)
            for (int y = 3; y <= 8; y++)
                for (int z = -10; z <= 11; z++) {
                    boolean shell = Math.abs(x) == 3 || y == 3 || y == 8 || z == -10 || z == 11;
                    if (shell) b.c(x, y, z, ComponentType.BALLOON_ENVELOPE);
                }
        // tail fins
        for (int i = 1; i <= 2; i++) {
            b.wing(0, 8 + i, 11, ComponentType.RUDDER, WingOrientation.VERTICAL_X, t -> t.putDouble("chord", 2.0));
            b.wing(3 + i, 5, 11, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, t -> t.putDouble("chord", 2.0));
            b.wing(-3 - i, 5, 11, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, t -> t.putDouble("chord", 2.0));
        }
    }

    private static void submarine(B b) {
        // sealed hull 3x3x13 of copper and iron with an internal air passage
        for (int z = -6; z <= 6; z++)
            for (int x = -1; x <= 1; x++)
                for (int y = -1; y <= 1; y++) {
                    if (x == 0 && y == 0 && z > -6 && z < 6) continue;
                    b.block(x, y, z, (z + x + y) % 3 == 0 ? Blocks.IRON_BLOCK : Blocks.CUT_COPPER);
                }
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, t -> t.putInt("profile", 5));
        b.c(0, 0, -1, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(0, 0, -2, ComponentType.FLIGHT_INSTRUMENTS, Direction.SOUTH);
        b.c(0, 0, 4, ComponentType.ELECTRIC_MOTOR, Direction.NORTH);
        b.c(0, 0, 7, ComponentType.MARINE_PROPELLER, Direction.NORTH);
        b.c(0, 0, 6, ComponentType.ELECTRIC_MOTOR, Direction.NORTH);
        for (int z : new int[]{-4, -3, 2, 3}) b.c(0, 0, z, ComponentType.BALLAST_TANK);
        b.block(0, 0, 1, Blocks.AIR);
        b.block(0, 0, 5, Blocks.AIR);
        b.block(0, 2, -1, Blocks.CUT_COPPER);
        b.block(0, 3, -1, Blocks.IRON_TRAPDOOR);
        // dive planes and rudder
        b.wing(-2, 0, -4, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, null);
        b.wing(2, 0, -4, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, null);
        b.wing(-2, 0, 5, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, null);
        b.wing(2, 0, 5, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, null);
        b.wing(0, 2, 6, ComponentType.RUDDER, WingOrientation.VERTICAL_X, null);
        b.wing(0, -2, 6, ComponentType.RUDDER, WingOrientation.VERTICAL_X, null);
    }
}
