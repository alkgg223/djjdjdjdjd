package com.trafficcraft.world;

import com.trafficcraft.block.BarrierGateBE;
import com.trafficcraft.block.FacingBlock;
import com.trafficcraft.block.StreetLampBlock;
import com.trafficcraft.npc.Workplaces;
import com.trafficcraft.registry.TCBlocks;
import com.trafficcraft.road.EdgeGeom;
import com.trafficcraft.road.NodeType;
import com.trafficcraft.road.Props;
import com.trafficcraft.road.RoadData;
import com.trafficcraft.road.RoadEdge;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.road.RoadPaver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "Build your own location" kits: place a complete, working gas station, parking lot, border checkpoint or bus stop
 * (blocks + road nodes + workers), oriented to the direction the player is looking and wired into the nearest road.
 * Everything placed is ordinary blocks / nodes, so it can be edited afterwards with the Road Planner.
 */
public final class Kits {
    public static final String[] KINDS = {"gas_station", "parking", "border", "bus_stop"};

    final ServerLevel level;
    final RoadNetwork net;
    final BlockPos o;
    final Direction fwd, right;

    Kits(ServerLevel level, BlockPos origin, Direction fwd) {
        this.level = level;
        this.net = RoadData.get(level);
        this.o = origin;
        this.fwd = fwd;
        this.right = fwd.getClockWise();
    }

    public static boolean place(ServerPlayer p, BlockPos clicked, String kind) {
        boolean ok = place(p.serverLevel(), clicked, p.getDirection(), kind, p);
        if (ok) p.displayClientMessage(Component.translatable("msg.trafficcraft.kit_placed", Component.translatable("item.trafficcraft.kit_" + kind)), true);
        return ok;
    }

    public static boolean place(ServerLevel level, BlockPos clicked, Direction facing, String kind, ServerPlayer p) {
        Kits k = new Kits(level, clicked.above(), facing);
        boolean ok = switch (kind) {
            case "gas_station" -> k.gasStation();
            case "parking" -> k.parking();
            case "border" -> k.border(p);
            case "bus_stop" -> k.busStop();
            default -> false;
        };
        if (ok) k.net.changed();
        return ok;
    }

    // ----------------------------------------------------------------------------------------- local frame helpers
    BlockPos at(double x, int y, double z) {
        return BlockPos.containing(o.getX() + 0.5 + right.getStepX() * x + fwd.getStepX() * z, o.getY() + y,
                o.getZ() + 0.5 + right.getStepZ() * x + fwd.getStepZ() * z);
    }

    Vec3 v(double x, double z) {
        return new Vec3(o.getX() + 0.5 + right.getStepX() * x + fwd.getStepX() * z, o.getY(), o.getZ() + 0.5 + right.getStepZ() * x + fwd.getStepZ() * z);
    }

    void set(BlockPos q, BlockState s) {
        level.setBlock(q, s, 3);
    }

    void put(String id, double x, int y, double z, Direction face) {
        BlockState st = TCBlocks.get(id).defaultBlockState();
        if (st.hasProperty(FacingBlock.FACING)) st = st.setValue(FacingBlock.FACING, face);
        set(at(x, y, z), st);
    }

    void rect(int x0, int z0, int x1, int z1, Block ground, int clearH) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            set(at(x, -1, z), ground.defaultBlockState());
            for (int y = 0; y < clearH; y++) if (!level.getBlockState(at(x, y, z)).isAir()) set(at(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }

    void lamp(double x, double z, Direction face) {
        BlockState base = TCBlocks.get("street_lamp").defaultBlockState().setValue(StreetLampBlock.FACING, face);
        for (int i = 0; i < StreetLampBlock.HEIGHT; i++) {
            StreetLampBlock.Part part = i == 0 ? StreetLampBlock.Part.BASE : i == StreetLampBlock.HEIGHT - 1 ? StreetLampBlock.Part.HEAD : StreetLampBlock.Part.POLE;
            set(at(x, i, z), base.setValue(StreetLampBlock.PART, part));
        }
        level.scheduleTick(at(x, StreetLampBlock.HEIGHT - 1, z), TCBlocks.get("street_lamp"), 5);
    }

    void gate(double x, double z, Direction face, int len) {
        put("barrier_gate", x, 0, z, face);
        if (level.getBlockEntity(at(x, 0, z)) instanceof BarrierGateBE g) {
            g.length = len;
            g.sync();
        }
    }

    /** Small building with glass windows; the door is in the wall nearest the road (z0 side). */
    void building(int x0, int z0, int x1, int z1, int h) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            set(at(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            for (int y = 0; y < h; y++) {
                Block b = wall ? (y == 1 || y == 2) && Math.floorMod(x + z, 3) != 0 ? Blocks.GLASS : TCBlocks.get("booth_wall") : Blocks.AIR;
                if (wall && y < 2 && x == (x0 + x1) / 2 && z == z0) b = Blocks.AIR; // door
                set(at(x, y, z), b.defaultBlockState());
            }
            set(at(x, h, z), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            if (!wall && Math.floorMod(x + z, 4) == 0) set(at(x, h - 1, z), TCBlocks.get("ceiling_light").defaultBlockState());
        }
    }

    RoadNode node(double x, double z, NodeType t) {
        Vec3 p = v(x, z);
        RoadNode n = net.addNode(p.x, p.y, p.z, t);
        Props.defaults(t, n.cfg);
        return n;
    }

    /** Joins a node to the road network: splits the nearest road (within 40 m) and builds an access road. */
    boolean link(RoadNode n, int lanes, float width, float speed) {
        RoadEdge best = null;
        double bd = 40, bs = 0;
        double[] out = new double[2];
        for (RoadEdge e : net.edges.values()) {
            if (e.ped) continue;
            EdgeGeom g = net.geom(e);
            double sv = g.project(n.pos(), out);
            double d = out[1];
            if (d < bd && sv > 6 && sv < g.length - 6) {
                bd = d;
                best = e;
                bs = sv;
            }
        }
        RoadNode join;
        if (best != null) join = net.splitEdge(best, bs, NodeType.ROAD);
        else {
            RoadNode near = net.nearestNode(n.pos(), 40, false);
            if (near == null || near.id == n.id) return false;
            join = near;
        }
        RoadEdge e = net.connect(join.id, n.id, false);
        e.lanes.clear();
        e.lanes.addAll(RoadEdge.defaultLanes(lanes, false));
        e.width = width;
        e.speed = speed;
        e.overtake = false;
        net.invalidateGeometry();
        CompoundTag pave = new CompoundTag();
        pave.putBoolean("clear", true);
        pave.putInt("sidewalk", 0);
        RoadPaver.paveEdge(level, net, e, pave, RoadPaver.BOTH);
        return true;
    }

    // ----------------------------------------------------------------------------------------- kits
    boolean gasStation() {
        Direction back = fwd.getOpposite();
        rect(-12, 0, 12, 21, TCBlocks.get("asphalt"), 8);
        put("fuel_pump", -4, 0, 10, fwd);
        put("fuel_pump", 4, 0, 10, fwd);
        put("fuel_tank", 10, 0, 3, fwd);
        for (int x = -8; x <= 8; x++) for (int z = 6; z <= 14; z++)
            set(at(x, 6, z), ((x + z) % 4 == 0 ? TCBlocks.get("canopy_light") : TCBlocks.get("canopy_roof")).defaultBlockState());
        for (int[] pl : new int[][]{{-8, 6}, {8, 6}, {-8, 14}, {8, 14}}) for (int y = 0; y < 6; y++) set(at(pl[0], y, pl[1]), Blocks.WHITE_CONCRETE.defaultBlockState());
        building(-8, 18, 8, 26, 5);
        put("cashier_counter", 0, 0, 23, back);
        put("store_shelf", -6, 0, 24, right);
        put("store_shelf", 6, 0, 24, right.getOpposite());
        put("payment_terminal", 3, 0, 17, back);
        put("shop_sign_block", 0, 5, 18, back);
        put("sign_fuel", -11, 0, 0, back);
        lamp(-12, 1, fwd);
        lamp(12, 1, fwd);
        RoadNode n = node(0, 4, NodeType.GAS_STATION);
        link(n, 2, 6, 20);
        Workplaces.ensureWorker(level, at(0, 0, 23), "cashier");
        Workplaces.ensureWorker(level, at(-4, 0, 10), "attendant");
        return true;
    }

    boolean parking() {
        rect(-16, 0, 16, 26, TCBlocks.get("asphalt"), 6);
        RoadNode n = node(0, 6, NodeType.PARKING);
        n.cfg.putInt("spaces", 12);
        n.cfg.putString("layout", "perpendicular");
        n.cfg.putString("side", "both");
        n.cfg.putInt("disabled", 1);
        n.cfg.putInt("reserved", 1);
        n.cfg.putFloat("rowYaw", fwd.toYRot());
        n.cfg.putInt("parkChance", 50);
        gate(4, 1, fwd, 4);
        put("payment_terminal", 7, 0, 1, fwd.getOpposite());
        put("parking_meter", -6, 0, 1, fwd.getOpposite());
        put("sign_parking", -5, 0, 0, fwd.getOpposite());
        put("security_camera", 8, 3, 1, fwd);
        set(at(8, 0, 1), Blocks.STONE_BRICK_WALL.defaultBlockState());
        set(at(8, 1, 1), Blocks.STONE_BRICK_WALL.defaultBlockState());
        set(at(8, 2, 1), Blocks.STONE_BRICK_WALL.defaultBlockState());
        lamp(-16, 13, right);
        lamp(16, 13, right.getOpposite());
        link(n, 2, 6, 15);
        return true;
    }

    /** Border checkpoint: must be used on an existing road; the road is split by a BORDER node. */
    boolean border(ServerPlayer p) {
        Vec3 click = Vec3.atBottomCenterOf(o);
        RoadEdge best = null;
        double bd = 6, bs = 0;
        double[] out = new double[2];
        for (RoadEdge e : net.edges.values()) {
            if (e.ped) continue;
            EdgeGeom g = net.geom(e);
            double sv = g.project(click, out);
            if (out[1] < Math.max(bd, e.width / 2 + 1) && out[1] < bd + e.width / 2 && sv > 8 && sv < g.length - 8) {
                bd = out[1];
                best = e;
                bs = sv;
            }
        }
        if (best == null) {
            if (p != null) p.displayClientMessage(Component.translatable("msg.trafficcraft.kit_needs_road"), true);
            return false;
        }
        EdgeGeom g = net.geom(best);
        Vec3 t = g.tangent(bs);
        Direction d = Direction.getNearest(t.x, 0, t.z);
        float w = best.width;
        RoadNode n = net.splitEdge(best, bs, NodeType.BORDER);
        Kits k = new Kits(level, BlockPos.containing(n.pos()), d);
        int half = (int) Math.ceil(w / 2) + 1;
        // barrier gates for both directions; arms reach to the centre line
        k.gate(-half, -1, d.getOpposite(), half);
        k.gate(half, 1, d, half);
        // inspection booth on the right side
        k.building(half + 2, -4, half + 7, 4, 4);
        k.put("booth_desk", half + 3, 0, 0, d.getCounterClockWise());
        k.put("warning_light", -half, 1, -2, d.getOpposite());
        k.put("security_camera", half + 2, 4, -4, d.getCounterClockWise());
        k.put("sign_border", half, 0, -14, d.getOpposite());
        k.put("sign_speed_30", half, 0, -22, d.getOpposite());
        k.lamp(-half - 1, -6, d.getClockWise());
        k.lamp(half + 1, 6, d.getCounterClockWise());
        Workplaces.ensureWorker(level, k.at(half + 3, 0, 0), "officer");
        return true;
    }

    boolean busStop() {
        put("bus_shelter", 0, 0, 0, fwd.getOpposite());
        put("bus_stop_sign", -3, 0, 0, fwd.getOpposite());
        put("bench", 2, 0, 1, fwd.getOpposite());
        put("trash_bin", 3, 0, 0, fwd.getOpposite());
        RoadNode dest = node(0, -1, NodeType.PED_DEST);
        dest.cfg.putString("name", "Bus stop");
        RoadNode near = null;
        double bd = 24;
        for (RoadNode q : net.nodes.values()) {
            if (!q.type.ped || q.id == dest.id) continue;
            double dd = q.pos().distanceTo(dest.pos());
            if (dd < bd) {
                bd = dd;
                near = q;
            }
        }
        if (near != null) net.connect(near.id, dest.id, true);
        return true;
    }
}
