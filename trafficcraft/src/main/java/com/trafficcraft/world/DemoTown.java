package com.trafficcraft.world;

import com.trafficcraft.block.FacingBlock;
import com.trafficcraft.block.GenericBE;
import com.trafficcraft.block.StreetLampBlock;
import com.trafficcraft.registry.TCBlocks;
import com.trafficcraft.road.Lane;
import com.trafficcraft.road.NodeType;
import com.trafficcraft.road.Props;
import com.trafficcraft.road.RoadData;
import com.trafficcraft.road.RoadEdge;
import com.trafficcraft.road.RoadEditing;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.road.RoadPaver;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a complete demo district around an origin: 4-lane avenue, signalised crossroads with crosswalks,
 * T-junction with a stop sign, roundabout, parking lot, gas station and a border checkpoint, plus lamps and signs.
 */
public final class DemoTown {
    final ServerLevel level;
    final RoadNetwork net;
    final double ox, oy, oz;
    final Map<String, Integer> ids = new HashMap<>();

    DemoTown(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.net = RoadData.get(level);
        this.ox = origin.getX() + 0.5;
        this.oy = origin.getY();
        this.oz = origin.getZ() + 0.5;
    }

    public static Map<String, Integer> build(ServerLevel level, BlockPos origin) {
        DemoTown t = new DemoTown(level, origin);
        t.run();
        return t.ids;
    }

    RoadNode node(String name, double x, double z, NodeType type) {
        RoadNode n = net.addNode(ox + x, oy, oz + z, type);
        Props.defaults(type, n.cfg);
        ids.put(name, n.id);
        return n;
    }

    RoadEdge road(String a, String b, int lanes, boolean oneway, float width, float speed) {
        RoadEdge e = net.connect(ids.get(a), ids.get(b), false);
        e.lanes.clear();
        e.lanes.addAll(RoadEdge.defaultLanes(lanes, oneway));
        e.width = width;
        e.speed = speed;
        e.overtake = lanes == 2;
        return e;
    }

    void flatten(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            BlockPos g = BlockPos.containing(ox + x, oy - 1, oz + z);
            if (level.getBlockState(g).isAir()) level.setBlock(g, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            for (int dy = 0; dy < 6; dy++) {
                BlockPos q = g.above(dy + 1);
                if (!level.getBlockState(q).isAir()) level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    void run() {
        flatten(-75, -60, 115, 70);
        // ---------------------------------------------------------------- nodes
        node("W", -70, 0, NodeType.SPAWN);
        node("A1", -30, 0, NodeType.ROAD);
        node("C", 0, 0, NodeType.ROAD);
        node("A2", 30, 0, NodeType.ROAD);
        node("E", 60, 0, NodeType.ROAD);
        node("N", 0, -55, NodeType.SPAWN);
        node("S1", 0, 22, NodeType.ROAD);
        node("BORDER", 0, 38, NodeType.BORDER);
        node("S", 0, 65, NodeType.SPAWN);
        node("T", -30, 30, NodeType.ROAD);
        node("P", -30, 40, NodeType.PARKING);
        node("G", 30, -22, NodeType.GAS_STATION);
        node("EE", 112, 0, NodeType.SPAWN);
        RoadNode c = net.nodes.get(ids.get("C"));
        c.flags |= RoadNode.F_LIGHTS | RoadNode.F_CROSSWALK;
        c.cfg.putBoolean("leftPhase", false);
        net.nodes.get(ids.get("A1")).cfg.putIntArray("stopEdges", new int[0]);
        // spawner settings: a mix like "10 sedans, 5 SUVs, 3 trucks, 2 buses" scaled to the demo
        for (String sp : new String[]{"W", "N", "S", "EE"}) {
            RoadNode n = net.nodes.get(ids.get(sp));
            n.cfg.putInt("max", 6);
            n.cfg.putInt("interval", 6);
            n.cfg.putInt("m_sedan", 3);
            n.cfg.putInt("m_hatchback", 2);
            n.cfg.putInt("m_suv", 2);
            n.cfg.putInt("m_taxi", 1);
            n.cfg.putInt("m_pickup_double", 1);
            n.cfg.putInt("m_delivery_van", 1);
            n.cfg.putInt("m_box_truck", 1);
            n.cfg.putInt("m_police", sp.equals("W") ? 1 : 0);
            n.cfg.putInt("m_sports", 1);
        }
        RoadNode p = net.nodes.get(ids.get("P"));
        p.cfg.putInt("spaces", 10);
        p.cfg.putString("layout", "perpendicular");
        p.cfg.putString("side", "both");
        p.cfg.putInt("disabled", 1);
        p.cfg.putInt("reserved", 1);
        p.cfg.putFloat("rowYaw", 0);
        p.cfg.putInt("parkChance", 60);
        p.cfg.putInt("stayMin", 20);
        p.cfg.putInt("stayMax", 60);
        // ---------------------------------------------------------------- edges
        road("W", "A1", 4, false, 12, 60);
        road("A1", "C", 4, false, 12, 50);
        road("C", "A2", 4, false, 12, 50);
        road("A2", "E", 4, false, 12, 60);
        road("N", "C", 2, false, 7, 50);
        road("C", "S1", 2, false, 7, 50);
        road("S1", "BORDER", 2, false, 7, 30);
        road("BORDER", "S", 2, false, 7, 50);
        RoadEdge side = road("A1", "T", 2, false, 6, 30);
        road("T", "P", 2, false, 6, 15);
        road("A2", "G", 2, false, 6, 20);
        // stop sign for vehicles coming out of the side street at A1
        net.nodes.get(ids.get("A1")).cfg.putIntArray("stopEdges", new int[]{side.id});
        // roundabout east of E
        Vec3 rc = new Vec3(ox + 78, oy, oz);
        int before = net.nextNode;
        CompoundTag tool = new CompoundTag();
        tool.putBoolean("pave", true);
        tool.putBoolean("clear", true);
        RoadEditing.buildRoundabout(level, net, rc, 12, tool);
        // connect E to the west ring node and EE to the east ring node
        RoadNode west = null, east = null;
        for (int id = before; id < net.nextNode; id++) {
            RoadNode n = net.nodes.get(id);
            if (n == null) continue;
            if (west == null || n.x < west.x) west = n;
            if (east == null || n.x > east.x) east = n;
        }
        ids.put("RW", west.id);
        ids.put("RE", east.id);
        road("E", "RW", 2, false, 7, 40);
        road("RE", "EE", 2, false, 7, 50);
        net.changed();
        // ---------------------------------------------------------------- paving
        CompoundTag pave = new CompoundTag();
        pave.putBoolean("clear", true);
        pave.putInt("sidewalk", 2);
        for (RoadEdge e : net.edges.values()) if (!e.ped) RoadPaver.paveEdge(level, net, e, pave, RoadPaver.WALKS);
        for (RoadEdge e : net.edges.values()) if (!e.ped) RoadPaver.paveEdge(level, net, e, pave, RoadPaver.ROAD);
        // parking lot surface
        paveRect(-48, 34, -12, 58);
        // gas station forecourt
        paveRect(20, -40, 46, -14);
        // ---------------------------------------------------------------- traffic lights at C
        light(-8, 8, Direction.WEST);
        light(8, -8, Direction.EAST);
        light(-5, -8, Direction.NORTH);
        light(5, 8, Direction.SOUTH);
        pedSignal(-9, -6, Direction.NORTH);
        pedSignal(9, 6, Direction.SOUTH);
        // ---------------------------------------------------------------- signs
        sign("sign_speed_60", -64, 8, Direction.WEST);
        sign("sign_speed_50", -24, 8, Direction.WEST);
        sign("sign_traffic_lights", -18, 8, Direction.WEST);
        sign("sign_stop", -26, 7, Direction.SOUTH);
        sign("sign_roundabout", 52, 8, Direction.WEST);
        sign("sign_parking", -26, 26, Direction.SOUTH);
        sign("sign_fuel", 26, -8, Direction.EAST);
        sign("sign_border", 5, 30, Direction.NORTH);
        sign("sign_speed_30", -5, 28, Direction.NORTH);
        // ---------------------------------------------------------------- street lamps along the avenue
        for (int x = -64; x <= 56; x += 16) {
            if (Math.abs(x) < 10) continue;
            lamp(x, -9, Direction.SOUTH);
            lamp(x + 8, 9, Direction.NORTH);
        }
        for (int z = -48; z <= 60; z += 16) if (Math.abs(z) > 10) lamp(6, z, Direction.WEST);
        // ---------------------------------------------------------------- gas station: pumps, tank, canopy, shop
        place("fuel_tank", 40, -18, Direction.NORTH);
        for (int i = 0; i < 2; i++) {
            place("fuel_pump", 26 + i * 8, -30, Direction.NORTH);
        }
        for (int x = 22; x <= 40; x++) for (int z = -36; z <= -24; z++) {
            BlockPos q = at(x, 6, z);
            level.setBlock(q, ((x + z) % 4 == 0 ? TCBlocks.get("canopy_light") : TCBlocks.get("canopy_roof")).defaultBlockState(), 2);
        }
        for (int[] pillar : new int[][]{{22, -36}, {40, -36}, {22, -24}, {40, -24}}) for (int y = 0; y < 6; y++)
            level.setBlock(at(pillar[0], y, pillar[1]), Blocks.WHITE_CONCRETE.defaultBlockState(), 2);
        building(24, -50, 40, -41, 5);
        place("cashier_counter", 30, -46, Direction.SOUTH);
        place("store_shelf", 26, -48, Direction.EAST);
        place("store_shelf", 36, -48, Direction.WEST);
        place("payment_terminal", 31, -40, Direction.SOUTH);
        place("shop_sign_block", 32, 5, Direction.SOUTH, -41);
        // ---------------------------------------------------------------- border checkpoint
        gate(-4, 37, Direction.SOUTH, 3);
        gate(4, 39, Direction.NORTH, 3);
        building(-12, 34, -7, 42, 4);
        place("booth_desk", -8, 38, Direction.EAST);
        place("warning_light", -4, 1, Direction.NORTH, 36);
        place("security_camera", -7, 4, Direction.EAST, 34);
        // ---------------------------------------------------------------- parking lot extras
        gate(-36, 32, Direction.SOUTH, 4);
        place("parking_meter", -20, 35, Direction.WEST);
        place("payment_terminal", -40, 34, Direction.SOUTH);
        for (int x = -46; x <= -14; x += 10) lamp(x, 58, Direction.NORTH);
        // ---------------------------------------------------------------- street furniture
        place("bus_stop_sign", -40, 9, Direction.NORTH);
        place("bench", -38, 10, Direction.NORTH);
        place("trash_bin", -36, 10, Direction.NORTH);
        place("bus_shelter", 14, 9, Direction.NORTH);
        place("emergency_call_box", 50, 9, Direction.NORTH);
        place("traffic_control_panel", 10, 12, Direction.NORTH);
        for (int x = -60; x <= 50; x += 22) place("roadside_tree", x, -12, Direction.NORTH);
        // roadworks closing the kerb lane of the eastbound avenue: traffic must change lane
        place("road_cone", -55, 5, Direction.NORTH);
        place("road_cone", -52, 4, Direction.NORTH);
        place("roadworks_light", -49, 4, Direction.NORTH);
        place("construction_sign", -52, 8, Direction.WEST);
        pedestrians();
        net.changed();
    }

    void ped(String name, double x, double z, NodeType t) {
        node(name, x, z, t);
    }

    void walk(String... chain) {
        for (int i = 0; i + 1 < chain.length; i++) net.connect(ids.get(chain[i]), ids.get(chain[i + 1]), true);
    }

    /** Pedestrian network: sidewalks, the four signalised crossings at C, an unsignalised crossing of the side street, spawns, destinations, workers. */
    void pedestrians() {
        double sw = 7.35, sn = 4.85, cx = 8.6;
        ped("pNWa", -cx, -sw, NodeType.PED);
        ped("pSWa", -cx, sw, NodeType.PED);
        ped("pNEa", cx, -sw, NodeType.PED);
        ped("pSEa", cx, sw, NodeType.PED);
        ped("pNWn", -sn, -cx, NodeType.PED);
        ped("pNEn", sn, -cx, NodeType.PED);
        ped("pSWs", -sn, cx, NodeType.PED);
        ped("pSEs", sn, cx, NodeType.PED);
        for (String k : new String[]{"pNWa", "pSWa", "pNEa", "pSEa", "pNWn", "pNEn", "pSWs", "pSEs"}) net.nodes.get(ids.get(k)).cfg.putBoolean("wait", true);
        walk("pNWa", "pSWa");   // crossing west arm
        walk("pNEa", "pSEa");   // crossing east arm
        walk("pNWn", "pNEn");   // crossing north arm
        walk("pSWs", "pSEs");   // crossing south arm
        walk("pNWa", "pNWn");
        walk("pNEa", "pNEn");
        walk("pSWa", "pSWs");
        walk("pSEa", "pSEs");
        // west
        ped("pW1", -30, -sw, NodeType.PED);
        ped("spawnW", -60, -sw, NodeType.PED_SPAWN);
        walk("pNWa", "pW1", "spawnW");
        ped("pW2", -24, sw, NodeType.PED);
        ped("pW3", -36, sw, NodeType.PED);
        ped("busStop", -40, sw + 0.5, NodeType.PED_DEST);
        ped("spawnSW", -60, sw, NodeType.PED_SPAWN);
        walk("pSWa", "pW2", "pW3", "busStop", "spawnSW");
        net.nodes.get(ids.get("busStop")).cfg.putString("name", "Bus stop");
        net.nodes.get(ids.get("pW2")).cfg.putBoolean("wait", true);
        net.nodes.get(ids.get("pW3")).cfg.putBoolean("wait", true);
        // north
        ped("pN1", -sn, -30, NodeType.PED);
        ped("spawnN", -sn, -50, NodeType.PED_SPAWN);
        walk("pNWn", "pN1", "spawnN");
        ped("parkN", sn, -45, NodeType.PED_DEST);
        walk("pNEn", "parkN");
        // east: along the avenue then up the west side of the forecourt to the shop door
        ped("pE1", 20.5, -sw, NodeType.PED);
        ped("pE2", 20.5, -39, NodeType.PED);
        ped("shop", 32, -39.5, NodeType.PED_DEST);
        walk("pNEa", "pE1", "pE2", "shop");
        net.nodes.get(ids.get("shop")).cfg.putString("name", "Fuel shop");
        ped("shelter", 14, sw, NodeType.PED);
        net.nodes.get(ids.get("shelter")).cfg.putBoolean("dest", true);
        ped("eastEnd", 40, sw, NodeType.PED_DEST);
        walk("pSEa", "shelter", "eastEnd");
        // south towards the border
        ped("southW", -sn, 25, NodeType.PED_DEST);
        ped("southE", sn, 25, NodeType.PED_DEST);
        walk("pSWs", "southW");
        walk("pSEs", "southE");
        for (String k : new String[]{"spawnW", "spawnSW", "spawnN"}) {
            RoadNode n = net.nodes.get(ids.get(k));
            n.cfg.putInt("max", 6);
            n.cfg.putInt("interval", 7);
        }
        // workers
        com.trafficcraft.npc.Workplaces.ensureWorker(level, at(30, 0, -46), "cashier");
        com.trafficcraft.npc.Workplaces.ensureWorker(level, at(26, 0, -30), "attendant");
        com.trafficcraft.npc.Workplaces.ensureWorker(level, at(-8, 0, 38), "officer");
    }

    BlockPos at(int x, int y, int z) {
        return BlockPos.containing(ox + x, oy + y, oz + z);
    }

    void paveRect(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) level.setBlock(at(x, -1, z), TCBlocks.get("asphalt").defaultBlockState(), 2);
    }

    void building(int x0, int z0, int x1, int z1, int h) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            level.setBlock(at(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            for (int y = 0; y < h; y++) {
                Block b = wall ? (y == 1 || y == 2) && (x + z) % 3 != 0 ? Blocks.GLASS : TCBlocks.get("booth_wall") : Blocks.AIR;
                if (wall && y < 2 && (x == (x0 + x1) / 2) && z == z1) b = Blocks.AIR; // door
                level.setBlock(at(x, y, z), b.defaultBlockState(), 2);
            }
            level.setBlock(at(x, h, z), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 2);
            if (!wall && (x + z) % 4 == 0) level.setBlock(at(x, h - 1, z), TCBlocks.get("ceiling_light").defaultBlockState(), 2);
        }
    }

    void place(String id, int x, int z, Direction f) {
        place(id, x, 0, f, z);
    }

    /** True if (x,z) lies on a carriageway (keeps decoration off the roads). */
    boolean onRoad(int x, int z) {
        Vec3 p = new Vec3(ox + x, oy, oz + z);
        for (RoadEdge e : net.edges.values()) {
            if (e.ped) continue;
            var g = net.geom(e);
            double[] out = new double[2];
            g.project(p, out);
            if (out[1] < e.width / 2 + 0.6) return true;
        }
        for (RoadNode n : net.nodes.values()) if (net.isJunction(n.id) && n.pos().distanceTo(p) < net.junctionRadius(n.id) + 0.6) return true;
        return false;
    }

    void place(String id, int x, int y, Direction f, int z) {
        if (y == 0 && !id.equals("road_cone") && !id.equals("roadworks_light") && onRoad(x, z)) return;
        Block b = TCBlocks.get(id);
        BlockState st = b.defaultBlockState();
        if (st.hasProperty(FacingBlock.FACING)) st = st.setValue(FacingBlock.FACING, f);
        level.setBlock(at(x, y, z), st, 3);
    }

    void sign(String id, int x, int z, Direction f) {
        place(id, x, z, f);
    }

    void light(int x, int z, Direction f) {
        place("traffic_light", x, z, f);
    }

    void pedSignal(int x, int z, Direction f) {
        place("pedestrian_signal", x, z, f);
    }

    void gate(int x, int z, Direction f, int len) {
        place("barrier_gate", x, z, f);
        if (level.getBlockEntity(at(x, 0, z)) instanceof com.trafficcraft.block.BarrierGateBE g) {
            g.length = len;
            g.sync();
        }
    }

    void lamp(int x, int z, Direction f) {
        if (onRoad(x, z)) return;
        BlockState base = TCBlocks.get("street_lamp").defaultBlockState().setValue(StreetLampBlock.FACING, f);
        for (int i = 0; i < StreetLampBlock.HEIGHT; i++) {
            StreetLampBlock.Part part = i == 0 ? StreetLampBlock.Part.BASE : i == StreetLampBlock.HEIGHT - 1 ? StreetLampBlock.Part.HEAD : StreetLampBlock.Part.POLE;
            level.setBlock(at(x, i, z), base.setValue(StreetLampBlock.PART, part), 3);
        }
        level.scheduleTick(at(x, StreetLampBlock.HEIGHT - 1, z), TCBlocks.get("street_lamp"), 5);
    }
}
