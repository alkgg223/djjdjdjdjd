package com.trafficcraft.road;

import com.trafficcraft.net.OpenEditorPayload;
import com.trafficcraft.registry.TCBlocks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side road editing: Road Planner clicks and editor screen operations. */
public final class RoadEditing {
    public static final int M_ROAD = 0, M_PATH = 1, M_EDIT = 2, M_DELETE = 3, M_CURVE = 4, M_ROUNDABOUT = 5, M_LIGHTS = 6, M_NODE = 7, M_TURN_L = 8, M_TURN_R = 9, M_T = 10, M_CROSS = 11, M_TURNS = 12, M_PRIORITY = 13;
    private static final Map<UUID, int[]> FIRST_EDGE = new HashMap<>();
    public static final String[] MODE_NAMES = {"road", "path", "edit", "delete", "curve", "roundabout", "lights", "node", "turn_left", "turn_right", "t_junction", "crossroads", "turns", "priority"};
    private static final Map<UUID, Integer> SELECTED = new HashMap<>();
    private static final Map<UUID, Integer> CURVE_EDGE = new HashMap<>();

    public static boolean canEdit(Player p) {
        return p.isCreative() || p.hasPermissions(2);
    }

    public static Integer selected(Player p) {
        return SELECTED.get(p.getUUID());
    }

    public static void clearSelection(Player p) {
        SELECTED.remove(p.getUUID());
        CURVE_EDGE.remove(p.getUUID());
    }

    // ================================================================== tool clicks
    public static void click(ServerPlayer p, Vec3 pos, CompoundTag tool) {
        if (!canEdit(p)) {
            msg(p, "msg.trafficcraft.no_permission");
            return;
        }
        ServerLevel level = p.serverLevel();
        RoadNetwork net = RoadData.get(level);
        int mode = tool.getInt("mode");
        switch (mode) {
            case M_ROAD, M_PATH -> {
                boolean ped = mode == M_PATH;
                RoadNode n = net.nearestNode(pos, 1.6, ped);
                boolean created = false;
                if (n == null) {
                    n = net.addNode(pos.x, pos.y, pos.z, ped ? NodeType.PED : NodeType.ROAD);
                    created = true;
                }
                Integer sel = SELECTED.get(p.getUUID());
                if (sel != null && sel != n.id && net.nodes.containsKey(sel) && net.nodes.get(sel).type.ped == ped) {
                    RoadEdge e = net.edgeBetween(sel, n.id);
                    if (e == null) {
                        e = net.connect(sel, n.id, ped);
                        applyToolToEdge(e, tool, ped);
                        net.changed();
                        if (tool.getBoolean("pave") || !tool.contains("pave")) RoadPaver.paveEdge(level, net, e, tool);
                        msg(p, ped ? "msg.trafficcraft.path_created" : "msg.trafficcraft.road_created", Math.round(net.geom(e).length));
                    }
                } else if (created) {
                    msg(p, "msg.trafficcraft.node_created", n.id);
                }
                SELECTED.put(p.getUUID(), n.id);
            }
            case M_EDIT -> {
                RoadNode n = net.nearestNode(pos, 1.6, null);
                if (n != null) {
                    PacketDistributor.sendToPlayer(p, new OpenEditorPayload("node", n.save()));
                    return;
                }
                RoadEdge e = net.nearestEdge(pos, 0.5, null);
                if (e != null) PacketDistributor.sendToPlayer(p, new OpenEditorPayload("edge", e.save()));
                else msg(p, "msg.trafficcraft.nothing_here");
            }
            case M_DELETE -> {
                RoadNode n = net.nearestNode(pos, 1.6, null);
                if (n != null) {
                    net.removeNode(n.id);
                    msg(p, "msg.trafficcraft.node_deleted");
                    return;
                }
                RoadEdge e = net.nearestEdge(pos, 0.5, null);
                if (e != null) {
                    net.removeEdge(e.id);
                    msg(p, "msg.trafficcraft.edge_deleted");
                }
            }
            case M_CURVE -> {
                Integer ce = CURVE_EDGE.get(p.getUUID());
                if (ce == null || !net.edges.containsKey(ce)) {
                    RoadEdge e = net.nearestEdge(pos, 0.5, null);
                    if (e == null) {
                        msg(p, "msg.trafficcraft.curve_pick");
                        return;
                    }
                    if (p.isShiftKeyDown()) {
                        e.ctrl = null;
                        net.changed();
                        msg(p, "msg.trafficcraft.curve_reset");
                        return;
                    }
                    CURVE_EDGE.put(p.getUUID(), e.id);
                    msg(p, "msg.trafficcraft.curve_selected");
                } else {
                    RoadEdge e = net.edges.get(ce);
                    e.ctrl = pos;
                    net.changed();
                    CURVE_EDGE.remove(p.getUUID());
                    if (tool.getBoolean("pave") || !tool.contains("pave")) RoadPaver.paveEdge(level, net, e, tool);
                    msg(p, "msg.trafficcraft.curve_set");
                }
            }
            case M_TURNS, M_PRIORITY -> {
                // pick a road end at a junction: first click = from / priority road, second click = to / road that gives way
                RoadEdge e = net.nearestEdge(pos, 1.0, false);
                if (e == null) {
                    msg(p, "msg.trafficcraft.nothing_here");
                    return;
                }
                RoadNode na = net.nodes.get(e.a), nb = net.nodes.get(e.b);
                int node = na.pos().distanceToSqr(pos) < nb.pos().distanceToSqr(pos) ? e.a : e.b;
                int[] first = FIRST_EDGE.get(p.getUUID());
                if (first == null || first[2] != mode || first[1] == e.id || !(e.a == first[0] || e.b == first[0])) {
                    if (!net.isJunction(node)) {
                        msg(p, "msg.trafficcraft.pick_junction_road");
                        return;
                    }
                    FIRST_EDGE.put(p.getUUID(), new int[]{node, e.id, mode});
                    msg(p, mode == M_TURNS ? "msg.trafficcraft.turn_from" : "msg.trafficcraft.prio_first", e.id);
                    return;
                }
                FIRST_EDGE.remove(p.getUUID());
                RoadNode n = net.nodes.get(first[0]);
                int k = com.trafficcraft.ai.RoutePlanner.turnKey(first[1], e.id);
                if (mode == M_TURNS) {
                    RoadEdge in = net.edges.get(first[1]);
                    boolean allowed = com.trafficcraft.ai.RoutePlanner.turnAllowed(net, n.id, in, e);
                    removeKey(n, "turnBan", k);
                    removeKey(n, "turnAllow", k);
                    if (allowed) addKey(n, "turnBan", k);
                    else if (!com.trafficcraft.ai.RoutePlanner.turnAllowed(net, n.id, in, e)) addKey(n, "turnAllow", k);
                    boolean now = com.trafficcraft.ai.RoutePlanner.turnAllowed(net, n.id, in, e);
                    msg(p, now ? "msg.trafficcraft.turn_allowed" : "msg.trafficcraft.turn_banned", first[1], e.id);
                } else {
                    int rev = com.trafficcraft.ai.RoutePlanner.turnKey(e.id, first[1]);
                    removeKey(n, "prio", rev);
                    boolean had = removeKey(n, "prio", k);
                    if (!had) addKey(n, "prio", k);
                    msg(p, had ? "msg.trafficcraft.prio_removed" : "msg.trafficcraft.prio_set", first[1], e.id);
                }
                net.changed();
            }
            case M_TURN_L, M_TURN_R, M_T, M_CROSS -> {
                int kind = mode - M_TURN_L;
                if (RoadPieces.build(p, pos, kind, tool)) {
                    SELECTED.remove(p.getUUID());
                    msg(p, "msg.trafficcraft.piece_created", Component.translatable("mode.trafficcraft." + MODE_NAMES[mode]));
                }
            }
            case M_ROUNDABOUT -> {
                int r = Math.max(6, tool.contains("radius") ? tool.getInt("radius") : 10);
                buildRoundabout(level, net, pos, r, tool);
                msg(p, "msg.trafficcraft.roundabout_created", r);
            }
            case M_LIGHTS -> {
                RoadNode n = net.nearestNode(pos, 2.0, false);
                if (n == null) {
                    msg(p, "msg.trafficcraft.nothing_here");
                    return;
                }
                n.flags ^= RoadNode.F_LIGHTS;
                if (n.has(RoadNode.F_LIGHTS)) n.flags |= RoadNode.F_CROSSWALK;
                net.changed();
                msg(p, n.has(RoadNode.F_LIGHTS) ? "msg.trafficcraft.lights_on" : "msg.trafficcraft.lights_off");
            }
            case M_NODE -> {
                NodeType t = NodeType.byName(tool.getString("nodeType"));
                RoadNode n = net.nearestNode(pos, 1.6, null);
                if (n == null) n = net.addNode(pos.x, pos.y, pos.z, t);
                else n.type = t;
                Props.defaults(n.type, n.cfg);
                net.changed();
                PacketDistributor.sendToPlayer(p, new OpenEditorPayload("node", n.save()));
            }
            default -> { }
        }
    }

    static void applyToolToEdge(RoadEdge e, CompoundTag tool, boolean ped) {
        if (ped) {
            e.width = tool.contains("pathWidth") ? tool.getFloat("pathWidth") : 2f;
            e.lanes.clear();
            return;
        }
        int lanes = Math.max(1, tool.contains("lanes") ? tool.getInt("lanes") : 2);
        boolean oneway = tool.getBoolean("oneway");
        e.lanes.clear();
        e.lanes.addAll(RoadEdge.defaultLanes(lanes, oneway));
        e.width = tool.contains("width") ? tool.getFloat("width") : 5f;
        e.speed = tool.contains("speed") ? tool.getFloat("speed") : 50f;
        e.overtake = !tool.contains("overtake") || tool.getBoolean("overtake");
    }

    public static void buildRoundabout(ServerLevel level, RoadNetwork net, Vec3 c, int r, CompoundTag tool) {
        int n = r >= 14 ? 12 : 8;
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            RoadNode node = net.addNode(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, NodeType.ROAD);
            node.flags |= RoadNode.F_RING | RoadNode.F_PRIORITY;
            ids[i] = node.id;
        }
        for (int i = 0; i < n; i++) {
            int from = ids[i], to = ids[(i - 1 + n) % n]; // counter-clockwise seen from above (right-hand traffic)
            RoadEdge e = net.connect(from, to, false);
            e.lanes.clear();
            int lanes = r >= 14 ? 2 : 1;
            for (int k = 0; k < lanes; k++) e.lanes.add(new Lane(1, Lane.NORMAL));
            e.width = lanes * 3.5f + 1;
            e.speed = 30;
            e.overtake = false;
            double am = 2 * Math.PI * (i - 0.5) / n;
            double half = Math.PI / n;
            double rc = r / Math.cos(half);
            e.ctrl = new Vec3(c.x + Math.cos(am) * rc, c.y, c.z + Math.sin(am) * rc);
        }
        net.changed();
        // island: grass + low curb, paving of the ring
        for (RoadEdge e : net.edges.values()) for (int id : ids) if (e.a == id) RoadPaver.paveEdge(level, net, e, tool);
        int ir = r - 3;
        for (int dx = -ir; dx <= ir; dx++) for (int dz = -ir; dz <= ir; dz++) {
            if (dx * dx + dz * dz > ir * ir) continue;
            BlockPos bp = BlockPos.containing(c.x + dx, c.y - 1, c.z + dz);
            boolean edge = dx * dx + dz * dz > (ir - 1) * (ir - 1);
            level.setBlock(bp, edge ? TCBlocks.get("concrete_block").defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            if (edge) level.setBlock(bp.above(), TCBlocks.get("sidewalk_slab").defaultBlockState(), 2);
        }
        level.setBlock(BlockPos.containing(c.x, c.y, c.z), TCBlocks.get("roadside_tree").defaultBlockState(), 2);
    }

    // ================================================================== editor operations
    public static void handle(ServerPlayer p, String op, CompoundTag d) {
        ServerLevel level = p.serverLevel();
        RoadNetwork net = RoadData.get(level);
        if (op.equals("tool")) {
            ItemStack st = p.getMainHandItem();
            if (st.getItem() instanceof RoadToolItem) RoadToolItem.setSettings(st, d);
            return;
        }
        if (op.equals("workplace")) {
            com.trafficcraft.npc.Workplaces.action(p, d);
            return;
        }
        if (!canEdit(p)) {
            msg(p, "msg.trafficcraft.no_permission");
            return;
        }
        switch (op) {
            case "node" -> {
                RoadNode n = net.nodes.get(d.getInt("id"));
                if (n == null) return;
                n.type = NodeType.byName(d.getString("type"));
                n.flags = d.getInt("flags");
                n.cfg = d.getCompound("cfg");
                Props.defaults(n.type, n.cfg);
                net.changed();
                msg(p, "msg.trafficcraft.saved");
            }
            case "edge" -> {
                RoadEdge e = net.edges.get(d.getInt("id"));
                if (e == null) return;
                RoadEdge src = RoadEdge.load(d);
                e.width = Math.max(1, Math.min(30, src.width));
                e.speed = Math.max(5, Math.min(200, src.speed));
                e.lanes.clear();
                e.lanes.addAll(src.lanes);
                e.overtake = src.overtake;
                e.restrict = src.restrict;
                e.name = src.name;
                e.ped = src.ped;
                net.changed();
                if (d.getBoolean("repave")) RoadPaver.paveEdge(level, net, e, d);
                msg(p, "msg.trafficcraft.saved");
            }
            case "edge_reverse" -> {
                RoadEdge e = net.edges.get(d.getInt("id"));
                if (e == null) return;
                List<Lane> copy = List.copyOf(e.lanes);
                e.lanes.clear();
                for (Lane l : copy) e.lanes.add(new Lane(-l.dir(), l.type()));
                net.changed();
            }
            case "delete_node" -> net.removeNode(d.getInt("id"));
            case "delete_edge" -> net.removeEdge(d.getInt("id"));
            case "light" -> TrafficLights.command(net, d.getInt("id"), d.getString("cmd"), level.getGameTime());
            case "sign_text" -> {
                BlockPos bp = BlockPos.of(d.getLong("pos"));
                if (bp.distSqr(p.blockPosition()) < 64 && level.getBlockEntity(bp) instanceof com.trafficcraft.block.GenericBE be) {
                    for (int i = 0; i < 3; i++) be.data.putString("line" + i, d.getString("line" + i));
                    be.sync();
                }
            }
            default -> { }
        }
    }

    static void addKey(RoadNode n, String key, int v) {
        int[] a = n.cfg.getIntArray(key);
        for (int x : a) if (x == v) return;
        int[] b = java.util.Arrays.copyOf(a, a.length + 1);
        b[a.length] = v;
        n.cfg.putIntArray(key, b);
    }

    static boolean removeKey(RoadNode n, String key, int v) {
        int[] a = n.cfg.getIntArray(key);
        int[] b = java.util.Arrays.stream(a).filter(x -> x != v).toArray();
        if (b.length == a.length) return false;
        n.cfg.putIntArray(key, b);
        return true;
    }

    static void msg(Player p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args), true);
    }

    static boolean replaceable(BlockState s) {
        return s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty();
    }
}
