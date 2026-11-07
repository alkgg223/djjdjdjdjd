package com.arkragnarok.survivor;

import com.arkragnarok.building.PieceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One of {@link #COUNT} base blueprints, generated from 20 architectural archetypes (cabins, stone forts, castles,
 * mountain strongholds, forest lodges, harbours, island bases, desert courtyards, underground bunkers, cave bases,
 * farmsteads, breeding ranches, taming centres, military camps, research stations, industrial works, mining camps,
 * trade halls, advanced complexes and anti-giant fortresses) in variants that differ in shape and purpose - not
 * colour: square, rectangle, long hall, L, T or cross; what each part of the house is for; wall rings with corner
 * watchtowers and a gate; towers rising from the house; dinosaur pens; crop fields.  Every design is checked to be
 * different from all others.
 *
 * Like an ARK base it is ONE connected building: 3x3 foundations laid edge to edge (flush with the ground), one
 * roof of ceilings over all of them, an open interior, walls standing on the ground all around the outside, and a
 * single main door.  Coordinates are relative to the anchor (the middle of the first module, at ground level), with
 * the main door to the north; {@link #rotate} turns them for the site.
 */
public final class BaseDesign {
    public static final int COUNT = 25;
    /** a module is one 3x3 foundation; modules lie edge to edge */
    public static final int PITCH = 3;
    /** bumped whenever the blueprints change shape: a tribe with an older plan settles again */
    public static final int PLAN_VERSION = 2;

    public enum Archetype {
        CABIN("cabin", "flat"), STONE_FORT("stone fort", "flat"), CASTLE("castle", "flat"), MOUNTAIN("mountain stronghold", "mountain"),
        FOREST_VILLAGE("forest lodge", "forest"), HARBOUR("coastal harbour", "coast"), ISLAND("island base", "coast"),
        DESERT("desert stronghold", "desert"), UNDERGROUND("underground bunker", "any"), CAVE("cave base", "cave"),
        FARM("farmstead", "flat"), BREEDING("breeding ranch", "flat"), TAMING("taming centre", "flat"), MILITARY("military camp", "flat"),
        RESEARCH("research station", "any"), INDUSTRIAL("industrial works", "flat"), MINING("mining camp", "metal"),
        TRADE("trade hall", "flat"), ADVANCED("advanced complex", "flat"), GIANT_DEFENSE("anti-giant fortress", "flat");

        public final String label, site;

        Archetype(String label, String site) {
            this.label = label;
            this.site = site;
        }
    }

    public enum Room { SLEEP, STORAGE, WORKSHOP, SMELTER, KITCHEN, LAB, ARMORY, BARN, MARKET, NURSERY, HALL }

    public enum Kind { PIECE, DOOR, FURNITURE, CROP, GROUND }

    /**
     * one build action: a structure piece (type + facing), hanging a door, placing a furniture block (furniture =
     * "bed", "storage_box", "large_storage_box", "campfire", "mortar_and_pestle", "smithy", "refining_forge",
     * "fabricator"), a crop plot, or preparing ground (furniture "" = level a module's plot, "ramp" = dig the
     * stairway of an underground base); phase 1 the house .. 5 expansion; room = the part of the house it belongs to.
     * A bed's offset is its head, its facing points from the head to the foot.
     */
    public record Step(Kind kind, @Nullable PieceType type, BlockPos offset, Direction facing, String furniture, int phase, @Nullable Room room) {
        public boolean door() {
            return kind == Kind.DOOR;
        }
    }

    record Cell(int x, int z, int floor, Room room) {}

    /** a straight run of outside wall along one side of the house: blocks lo..hi on a line, facing outwards */
    private record Seg(Direction out, int line, int lo, int hi) {}

    private static final Direction[] H = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public final int id;
    public final Archetype archetype;
    public final int variant;
    public final String layout;
    public final int floors = 1;
    final List<Cell> cells = new ArrayList<>();           // modules of the house (floor 0)
    final List<int[]> pens = new ArrayList<>();         // {x0, z0, w, d} in modules
    final List<int[]> fields = new ArrayList<>();       // {x0, z0, w, d} in modules
    final List<int[]> raised = new ArrayList<>();       // {module x, module z, levels}: towers rising out of the house
    final List<int[]> towers = new ArrayList<>();       // {block x, block z, levels}: watchtowers in the corners of the wall ring
    int ring = -1;                                      // wall ring margin in modules (-1 none)
    int ring2 = -1;                                     // second (outer) ring
    int depth;                                          // underground: how deep the house is dug in
    private int cornerTowers;
    private List<Step> plan;
    private boolean valid = true;

    private static final BaseDesign[] ALL = new BaseDesign[COUNT];

    static {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < COUNT; i++) {
            BaseDesign d = null;
            for (int attempt = 0; attempt < 40; attempt++) {
                d = new BaseDesign(i, attempt);
                if (d.valid && seen.add(d.signature())) break;
            }
            ALL[i] = d;
        }
    }

    public static BaseDesign get(int id) {
        return ALL[Math.floorMod(id, COUNT)];
    }

    public static List<BaseDesign> all() {
        List<BaseDesign> l = new ArrayList<>();
        Collections.addAll(l, ALL);
        return l;
    }

    // ============================================================================================ generation
    private BaseDesign(int id, int attempt) {
        this.id = id;
        Archetype[] at = Archetype.values();
        this.archetype = at[id % at.length];
        this.variant = id / at.length;
        Random r = new Random(id * 1000003L + attempt * 7919L + 17);
        int v = variant + attempt;
        String lay;
        Room[] rooms;
        switch (archetype) {
            case CABIN -> {
                lay = pick(v, "3x1", "L3", "2x2", "L4");
                rooms = new Room[]{Room.SLEEP, Room.WORKSHOP, Room.SMELTER, Room.KITCHEN};
            }
            case STONE_FORT -> {
                lay = pick(v, "2x2", "3x2", "3x3", "T4");
                rooms = new Room[]{Room.SLEEP, Room.STORAGE, Room.WORKSHOP, Room.ARMORY, Room.SMELTER, Room.KITCHEN, Room.STORAGE, Room.HALL, Room.LAB};
                ring = 1;
                cornerTowers = 2 + v % 3;
            }
            case CASTLE -> {
                lay = pick(v, "3x3", "4x3", "plus5", "T5");
                rooms = new Room[]{Room.SLEEP, Room.HALL, Room.STORAGE, Room.ARMORY, Room.KITCHEN, Room.WORKSHOP, Room.SMELTER, Room.STORAGE, Room.LAB,
                        Room.SLEEP, Room.ARMORY, Room.STORAGE};
                ring = 1;
                cornerTowers = 4;
                raisedCorners(2, 3);
                pens.add(new int[]{-4, 0, 2, 2});
            }
            case MOUNTAIN -> {
                lay = pick(v, "L3", "3x1", "2x2", "L4");
                rooms = new Room[]{Room.SLEEP, Room.STORAGE, Room.WORKSHOP, Room.SMELTER, Room.LAB};
                raisedCorners(1, 3 + v % 2);
            }
            case FOREST_VILLAGE -> {
                lay = pick(v, "L4", "T4", "4x1", "L5");
                rooms = new Room[]{Room.SLEEP, Room.KITCHEN, Room.STORAGE, Room.WORKSHOP, Room.SMELTER, Room.SLEEP};
                fields.add(new int[]{0, 3, 2, 1});
            }
            case HARBOUR -> {
                lay = pick(v, "4x1", "3x1", "L4", "3x2");
                rooms = new Room[]{Room.SLEEP, Room.STORAGE, Room.MARKET, Room.WORKSHOP, Room.SMELTER, Room.KITCHEN};
                raisedCorners(1, 3);
            }
            case ISLAND -> {
                lay = pick(v, "2x2", "plus5", "L3", "3x2");
                rooms = new Room[]{Room.SLEEP, Room.STORAGE, Room.KITCHEN, Room.WORKSHOP, Room.SMELTER, Room.LAB};
                cornerTowers = 1 + v % 3;
            }
            case DESERT -> {
                lay = pick(v, "3x3", "3x2", "2x2", "T5");
                rooms = new Room[]{Room.SLEEP, Room.STORAGE, Room.KITCHEN, Room.WORKSHOP, Room.SMELTER, Room.MARKET, Room.LAB, Room.STORAGE, Room.HALL};
                if (v % 2 == 1) ring = 1;
            }
            case UNDERGROUND -> {
                lay = pick(v, "3x1", "2x2", "L4", "plus5");
                rooms = new Room[]{Room.SLEEP, Room.STORAGE, Room.WORKSHOP, Room.SMELTER, Room.LAB, Room.HALL};
                depth = 3 + v % 2;
            }
            case CAVE -> {
                lay = pick(v, "3x1", "L3", "2x2", "4x1");
                rooms = new Room[]{Room.SLEEP, Room.STORAGE, Room.SMELTER, Room.WORKSHOP};
            }
            case FARM -> {
                lay = pick(v, "3x1", "L3", "L4", "2x2");
                rooms = new Room[]{Room.SLEEP, Room.BARN, Room.KITCHEN, Room.WORKSHOP, Room.SMELTER};
                fields.add(new int[]{0, 3, 2 + v % 2, 2});
                if (v % 3 == 2) fields.add(new int[]{-4, 0, 2, 2});
                if (v % 2 == 1) ring = 2;
            }
            case BREEDING -> {
                lay = pick(v, "3x1", "L3", "2x2", "L4");
                rooms = new Room[]{Room.SLEEP, Room.NURSERY, Room.STORAGE, Room.WORKSHOP, Room.SMELTER};
                pens.add(new int[]{0, 3, 2 + v % 2, 2});
                if (v >= 3) pens.add(new int[]{-5, 0, 2, 2});
            }
            case TAMING -> {
                lay = pick(v, "3x1", "L3", "2x2", "4x1");
                rooms = new Room[]{Room.SLEEP, Room.KITCHEN, Room.WORKSHOP, Room.SMELTER, Room.STORAGE};
                pens.add(new int[]{5, 0, 2, 2});
                raisedCorners(1, 3);
            }
            case MILITARY -> {
                lay = pick(v, "3x2", "4x2", "T4", "4x1");
                rooms = new Room[]{Room.SLEEP, Room.ARMORY, Room.STORAGE, Room.WORKSHOP, Room.SMELTER, Room.KITCHEN, Room.SLEEP, Room.ARMORY};
                ring = 1;
                if (v % 2 == 1) ring2 = 3;
                cornerTowers = 2 + v % 3;
            }
            case RESEARCH -> {
                lay = pick(v, "2x2", "plus5", "L4", "3x2");
                rooms = new Room[]{Room.SLEEP, Room.LAB, Room.STORAGE, Room.WORKSHOP, Room.SMELTER, Room.KITCHEN};
                raisedCorners(1, 3);
            }
            case INDUSTRIAL -> {
                lay = pick(v, "3x2", "4x1", "2x2", "L5");
                rooms = new Room[]{Room.SLEEP, Room.SMELTER, Room.WORKSHOP, Room.STORAGE, Room.STORAGE, Room.SMELTER, Room.LAB};
                raisedCorners(1, 3 + v % 2);                     // chimney / lookout
            }
            case MINING -> {
                lay = pick(v, "3x1", "L3", "2x2", "L4");
                rooms = new Room[]{Room.SLEEP, Room.SMELTER, Room.STORAGE, Room.WORKSHOP};
                if (v % 2 == 0) raisedCorners(1, 2);
            }
            case TRADE -> {
                lay = pick(v, "4x2", "3x2", "T5", "4x1");
                rooms = new Room[]{Room.SLEEP, Room.MARKET, Room.MARKET, Room.STORAGE, Room.WORKSHOP, Room.SMELTER, Room.MARKET, Room.KITCHEN};
            }
            case ADVANCED -> {
                lay = pick(v, "3x3", "4x3", "plus5", "3x2");
                rooms = new Room[]{Room.SLEEP, Room.LAB, Room.HALL, Room.STORAGE, Room.SMELTER, Room.WORKSHOP, Room.ARMORY, Room.LAB, Room.STORAGE};
                ring = 1;
                cornerTowers = 2 + v % 3;
                pens.add(new int[]{-5, 0, 2, 2});
            }
            default -> {          // GIANT_DEFENSE
                lay = pick(v, "2x2", "3x2", "3x3", "T4");
                rooms = new Room[]{Room.SLEEP, Room.STORAGE, Room.ARMORY, Room.WORKSHOP, Room.SMELTER, Room.KITCHEN, Room.HALL};
                ring = 1;
                ring2 = 3;
                cornerTowers = 4;
            }
        }
        this.layout = lay;
        shape(lay, rooms);
        separate();
        if (cornerTowers > 0) corners(cornerTowers, r);
        // the walls must close the house without two pieces in the same place
        if (segments() == null) valid = false;
    }

    private static String pick(int v, String... options) {
        return options[Math.floorMod(v, options.length)];
    }

    /** the modules of the house; the parts that keep the base going (bed, workshop, smelter) go where there is wall to stand against */
    private void shape(String lay, Room[] rooms) {
        int[][] s = switch (lay) {
            case "3x1" -> new int[][]{{0, 0}, {1, 0}, {2, 0}};
            case "4x1" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}};
            case "L3" -> new int[][]{{0, 0}, {1, 0}, {0, 1}};
            case "L4" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {0, 1}};
            case "L5" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {0, 1}, {0, 2}};
            case "2x2" -> new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}};
            case "3x2" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}, {2, 1}};
            case "4x2" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}, {0, 1}, {1, 1}, {2, 1}, {3, 1}};
            case "3x3" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}};
            case "4x3" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}, {0, 1}, {1, 1}, {2, 1}, {3, 1}, {0, 2}, {1, 2}, {2, 2}, {3, 2}};
            case "T4" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {1, 1}};
            case "T5" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {1, 1}, {1, 2}};
            case "plus5" -> new int[][]{{1, 0}, {0, 1}, {1, 1}, {2, 1}, {1, 2}};
            default -> new int[][]{{0, 0}, {1, 0}, {2, 0}};
        };
        // the entrance module first (its north side is outside wall), then the ones with the most outside wall
        List<int[]> order = new ArrayList<>(List.of(s));
        Set<Long> set = new HashSet<>();
        for (int[] c : s) set.add(key(c[0], c[1]));
        order.sort((a, b) -> {
            int ea = exteriorSides(set, a[0], a[1]), eb = exteriorSides(set, b[0], b[1]);
            boolean na = !set.contains(key(a[0], a[1] - 1)), nb = !set.contains(key(b[0], b[1] - 1));
            if (na != nb) return na ? -1 : 1;
            if (ea != eb) return Integer.compare(eb, ea);
            return Integer.compare(a[1] * 10 + a[0], b[1] * 10 + b[0]);
        });
        List<Room> list = new ArrayList<>(List.of(rooms));
        for (Room must : new Room[]{Room.SLEEP, Room.WORKSHOP, Room.SMELTER}) {
            if (!list.subList(0, Math.min(list.size(), order.size())).contains(must)) list.add(Math.min(list.size(), order.size()) - 1, must);
        }
        for (int i = 0; i < order.size(); i++) {
            Room room = i < list.size() ? list.get(i) : Room.STORAGE;
            if (exteriorSides(set, order.get(i)[0], order.get(i)[1]) == 0) room = Room.HALL;      // the middle of a big house stays open
            cells.add(new Cell(order.get(i)[0], order.get(i)[1], 0, room));
        }
        // the first module is the bedroom (shelter)
        if (cells.get(0).room != Room.SLEEP) cells.set(0, new Cell(cells.get(0).x, cells.get(0).z, 0, Room.SLEEP));
    }

    private static int exteriorSides(Set<Long> set, int x, int z) {
        int n = 0;
        for (Direction d : H) if (!set.contains(key(x + d.getStepX(), z + d.getStepZ()))) n++;
        return n;
    }

    private static long key(int x, int z) {
        return ((long) (x + 1000) << 20) | (z + 1000);
    }

    boolean isModule(int x, int z) {
        return at(x, z) != null;
    }

    Cell at(int x, int z) {
        for (Cell c : cells) if (c.x == x && c.z == z) return c;
        return null;
    }

    /** towers rising out of the house corners furthest from the entrance */
    private void raisedCorners(int n, int levels) {
        pendingRaised = n;
        pendingLevels = levels;
    }

    private int pendingRaised, pendingLevels;

    /** pens and fields keep their distance from the house and from each other */
    private void separate() {
        if (pendingRaised > 0) {
            List<Cell> cand = new ArrayList<>();
            Set<Long> set = new HashSet<>();
            for (Cell c : cells) set.add(key(c.x, c.z));
            for (Cell c : cells) if (exteriorSides(set, c.x, c.z) >= 2 && c != cells.get(0)) cand.add(c);
            Cell first = cells.get(0);
            cand.sort((a, b) -> Integer.compare(Math.abs(b.x - first.x) + Math.abs(b.z - first.z), Math.abs(a.x - first.x) + Math.abs(a.z - first.z)));
            for (int i = 0; i < Math.min(pendingRaised, cand.size()); i++) raised.add(new int[]{cand.get(i).x, cand.get(i).z, pendingLevels});
        }
        List<int[]> placed = new ArrayList<>();
        for (List<int[]> group : List.of(pens, fields)) {
            int gap = group == pens ? 2 : 1;
            for (int[] p : group) {
                int sx = p[0] >= 0 ? 1 : -1;
                for (int k = 0; k < 16 && rectBlocked(p, placed, gap); k++) {
                    if (k < 10) p[0] += sx;
                    else p[1] += 1;
                }
                placed.add(p);
            }
        }
    }

    private boolean rectBlocked(int[] p, List<int[]> placed, int gap) {
        for (int x = p[0] - gap; x < p[0] + p[2] + gap; x++) {
            for (int z = p[1] - gap; z < p[1] + p[3] + gap; z++) {
                if (isModule(x, z)) return true;
                for (int[] q : placed) if (x >= q[0] - 1 && x < q[0] + q[2] + 1 && z >= q[1] - 1 && z < q[1] + q[3] + 1) return true;
            }
        }
        return false;
    }

    /** watchtowers in the corners of the (inner) wall ring: NW, SE, NE, SW */
    private void corners(int n, Random r) {
        int[] rr = ringRect(ring >= 0 ? ring : 1);
        int[][] c = {{rr[0] + 2, rr[2] + 2}, {rr[1] - 2, rr[3] - 2}, {rr[1] - 2, rr[2] + 2}, {rr[0] + 2, rr[3] - 2}};
        for (int i = 0; i < Math.min(4, n); i++) towers.add(new int[]{c[i][0], c[i][1], 2 + r.nextInt(2)});
    }

    /** house, pens and fields in modules: {x0, x1, z0, z1} */
    private int[] moduleBounds() {
        int x0 = 99, x1 = -99, z0 = 99, z1 = -99;
        for (Cell c : cells) {
            x0 = Math.min(x0, c.x);
            x1 = Math.max(x1, c.x);
            z0 = Math.min(z0, c.z);
            z1 = Math.max(z1, c.z);
        }
        for (List<int[]> g : List.of(pens, fields)) {
            for (int[] p : g) {
                x0 = Math.min(x0, p[0]);
                x1 = Math.max(x1, p[0] + p[2] - 1);
                z0 = Math.min(z0, p[1]);
                z1 = Math.max(z1, p[1] + p[3] - 1);
            }
        }
        return new int[]{x0, x1, z0, z1};
    }

    /** a wall ring m modules outside everything, sized so its sides tile exactly with 3-wide wall pieces */
    private int[] ringRect(int m) {
        int[] b = moduleBounds();
        int g = m * PITCH + 4;                 // room for a 5x5 watchtower and a passage between it and the house
        int xW = b[0] * PITCH - 2 - g, xE = b[1] * PITCH + 2 + g, zN = b[2] * PITCH - 2 - g, zS = b[3] * PITCH + 2 + g;
        xE += Math.floorMod(2 - (xE - xW + 1), 3);
        zS += Math.floorMod(2 - (zS - zN + 1), 3);
        return new int[]{xW, xE, zN, zS};
    }

    // ============================================================================================ outside walls
    /**
     * The outside walls of the house as straight runs.  Each side of the house is one run of 3-wide pieces one block
     * outside the foundations.  Where two runs meet in an inner corner they would share a block: one of them moves
     * one block along its line, into the free outer corner at its other end.  Null if a shape cannot be closed.
     */
    @Nullable
    private List<Seg> segments() {
        // unit sides grouped into runs per outward direction and line
        Map<String, List<int[]>> runs = new LinkedHashMap<>();
        for (Cell c : cells) {
            for (Direction d : H) {
                if (isModule(c.x + d.getStepX(), c.z + d.getStepZ())) continue;
                boolean alongX = d.getAxis() == Direction.Axis.Z;
                int line = alongX ? c.z * PITCH + d.getStepZ() * 2 : c.x * PITCH + d.getStepX() * 2;
                int along = alongX ? c.x : c.z;
                runs.computeIfAbsent(d.name() + ":" + line, k -> new ArrayList<>()).add(new int[]{along});
            }
        }
        List<Seg> segs = new ArrayList<>();
        for (Map.Entry<String, List<int[]>> e : runs.entrySet()) {
            String[] k = e.getKey().split(":");
            Direction d = Direction.valueOf(k[0]);
            int line = Integer.parseInt(k[1]);
            List<Integer> a = new ArrayList<>();
            for (int[] v : e.getValue()) a.add(v[0]);
            Collections.sort(a);
            int start = a.get(0), prev = a.get(0);
            for (int i = 1; i <= a.size(); i++) {
                if (i < a.size() && a.get(i) == prev + 1) {
                    prev = a.get(i);
                    continue;
                }
                segs.add(new Seg(d, line, start * PITCH - 1, prev * PITCH + 1));
                if (i < a.size()) {
                    start = a.get(i);
                    prev = a.get(i);
                }
            }
        }
        // inner corners: two runs claiming the same block
        Map<Long, List<Integer>> claims = new HashMap<>();
        for (int i = 0; i < segs.size(); i++) {
            Seg s = segs.get(i);
            for (int t : new int[]{s.lo, s.hi}) claims.computeIfAbsent(blockKey(s, t), q -> new ArrayList<>()).add(i);
        }
        Seg[] fixed = segs.toArray(new Seg[0]);
        boolean[] moved = new boolean[fixed.length];
        for (Map.Entry<Long, List<Integer>> c : claims.entrySet()) {
            if (c.getValue().size() < 2) continue;
            if (c.getValue().size() > 2) return null;
            int a = c.getValue().get(0), b = c.getValue().get(1);
            int pick = !moved[a] && freeOtherEnd(fixed, a, c.getKey(), claims) ? a : !moved[b] && freeOtherEnd(fixed, b, c.getKey(), claims) ? b : -1;
            if (pick < 0) return null;
            Seg s = fixed[pick];
            boolean atLo = blockKey(s, s.lo) == c.getKey();
            fixed[pick] = atLo ? new Seg(s.out, s.line, s.lo + 1, s.hi + 1) : new Seg(s.out, s.line, s.lo - 1, s.hi - 1);
            moved[pick] = true;
        }
        // nothing may overlap after the moves
        Set<Long> used = new HashSet<>();
        for (Seg s : fixed) for (int t = s.lo; t <= s.hi; t++) if (!used.add(blockKey(s, t))) return null;
        return List.of(fixed);
    }

    /** the other end of the run is an outer corner nobody else claims */
    private static boolean freeOtherEnd(Seg[] segs, int i, long corner, Map<Long, List<Integer>> claims) {
        Seg s = segs[i];
        boolean atLo = blockKey(s, s.lo) == corner;
        long other = blockKey(s, atLo ? s.hi : s.lo);
        long beyond = blockKey(s, atLo ? s.hi + 1 : s.lo - 1);
        return claims.getOrDefault(other, List.of()).size() == 1 && !claims.containsKey(beyond);
    }

    private static long blockKey(Seg s, int t) {
        boolean alongX = s.out.getAxis() == Direction.Axis.Z;
        int x = alongX ? t : s.line, z = alongX ? s.line : t;
        return ((long) (x + 100000) << 20) | (z + 100000);
    }

    public String signature() {
        StringBuilder b = new StringBuilder(archetype.name()).append('|').append(layout).append('|');
        List<String> cs = new ArrayList<>();
        for (Cell c : cells) cs.add(c.x + "," + c.z + "," + c.room);
        Collections.sort(cs);
        b.append(cs).append(pens.size()).append(fields.size()).append(raised.size()).append(towers.size()).append(ring).append(ring2).append(depth);
        for (int[] p : pens) b.append(p[0]).append(p[1]).append(p[2]).append(p[3]);
        for (int[] p : fields) b.append(p[0]).append(p[1]).append(p[2]).append(p[3]);
        for (int[] t : raised) b.append(t[0]).append(t[1]).append(t[2]);
        for (int[] t : towers) b.append(t[0]).append(t[1]).append(t[2]);
        return b.toString();
    }

    // ============================================================================================ facts for the selector
    public int roomCount() {
        return cells.size();
    }

    public int count(Room r) {
        int n = 0;
        for (Cell c : cells) if (c.room == r) n++;
        return n;
    }

    public int penCells() {
        int n = 0;
        for (int[] p : pens) n += p[2] * p[3];
        return n;
    }

    public int fieldCells() {
        int n = 0;
        for (int[] p : fields) n += p[2] * p[3];
        return n;
    }

    public int towerCount() {
        return towers.size() + raised.size();
    }

    public boolean walled() {
        return ring >= 0;
    }

    public int depth() {
        return depth;
    }

    private int[] bounds;

    /** footprint relative to the anchor, unrotated (blocks): {minX, maxX, minZ, maxZ, maxY, minY} */
    public synchronized int[] bounds() {
        if (bounds == null) {
            int x0 = 0, x1 = 0, z0 = 0, z1 = 0, y0 = 0, y1 = 0;
            for (Step s : plan()) {
                BlockPos o = s.offset();
                x0 = Math.min(x0, o.getX() - 2);
                x1 = Math.max(x1, o.getX() + 2);
                z0 = Math.min(z0, o.getZ() - 2);
                z1 = Math.max(z1, o.getZ() + 2);
                y0 = Math.min(y0, o.getY() - 1);
                y1 = Math.max(y1, o.getY() + 3);
            }
            bounds = new int[]{x0, x1, z0, z1, y1, y0};
        }
        return bounds;
    }

    /** footprint turned for a site: {minX, maxX, minZ, maxZ, maxY, minY} */
    public int[] bounds(int rot) {
        int[] b = bounds();
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (int cx : new int[]{b[0], b[1]}) {
            for (int cz : new int[]{b[2], b[3]}) {
                BlockPos p = rotate(new BlockPos(cx, 0, cz), rot);
                x0 = Math.min(x0, p.getX());
                x1 = Math.max(x1, p.getX());
                z0 = Math.min(z0, p.getZ());
                z1 = Math.max(z1, p.getZ());
            }
        }
        return new int[]{x0, x1, z0, z1, b[4], b[5]};
    }

    public int pieceCount() {
        int n = 0;
        for (Step s : plan()) if (s.kind == Kind.PIECE) n++;
        return n;
    }

    public String describe() {
        return archetype.label + " (" + layout + ", " + cells.size() + " parts" + (towerCount() == 0 ? "" : ", " + towerCount() + " towers")
                + (pens.isEmpty() ? "" : ", " + pens.size() + " pens") + (fields.isEmpty() ? "" : ", fields")
                + (ring >= 0 ? (ring2 >= 0 ? ", double wall ring" : ", wall ring") : "") + (depth > 0 ? ", underground" : "") + ")";
    }

    /** the parts of the house and their use, for the blueprint viewer */
    public Map<String, Integer> roomSummary() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Cell c : cells) m.merge(c.room.name().toLowerCase(), 1, Integer::sum);
        return m;
    }

    /** top-down cells for the blueprint viewer: {x, z, storeys above, room ordinal} plus pens (-1), fields (-2), towers (-3) */
    public List<int[]> mapCells() {
        List<int[]> out = new ArrayList<>();
        for (Cell c : cells) {
            int up = 0;
            for (int[] t : raised) if (t[0] == c.x && t[1] == c.z) up = t[2] - 1;
            out.add(new int[]{c.x, c.z, 0, c.room.ordinal()});
            for (int k = 1; k <= up; k++) out.add(new int[]{c.x, c.z, k, c.room.ordinal()});
        }
        for (int[] p : pens) for (int x = 0; x < p[2]; x++) for (int z = 0; z < p[3]; z++) out.add(new int[]{p[0] + x, p[1] + z, 0, -1});
        for (int[] p : fields) for (int x = 0; x < p[2]; x++) for (int z = 0; z < p[3]; z++) out.add(new int[]{p[0] + x, p[1] + z, 0, -2});
        for (int[] t : towers) out.add(new int[]{Math.floorDiv(t[0] + 1, PITCH), Math.floorDiv(t[1] + 1, PITCH), 0, -3});
        return out;
    }

    // ============================================================================================ rotation
    /** quarter turns clockwise seen from above (north -> east -> south -> west) */
    public static BlockPos rotate(BlockPos p, int rot) {
        int x = p.getX(), z = p.getZ();
        for (int i = 0; i < (rot & 3); i++) {
            int t = x;
            x = -z;
            z = t;
        }
        return new BlockPos(x, p.getY(), z);
    }

    public static Direction rotate(Direction d, int rot) {
        if (!d.getAxis().isHorizontal()) return d;
        for (int i = 0; i < (rot & 3); i++) d = d.getClockWise();
        return d;
    }

    // ============================================================================================ the build plan
    public synchronized List<Step> plan() {
        if (plan == null) plan = Collections.unmodifiableList(buildPlan());
        return plan;
    }

    /** ground-level centre of a module (y = the air just above the ground; the foundation goes one lower, flush with the ground) */
    private BlockPos modulePos(int x, int z) {
        return new BlockPos(x * PITCH, -depth, z * PITCH);
    }

    private BlockPos doorMaster;
    private Direction doorFacing;

    private List<Step> buildPlan() {
        List<Step> out = new ArrayList<>();
        int y0 = -depth;
        Cell first = cells.get(0);
        List<Seg> segs = segments();
        if (segs == null) segs = List.of();
        // 1. the house: ground, foundations edge to edge, the outside walls with the main door, one roof of ceilings
        for (Cell c : cells) out.add(new Step(Kind.GROUND, null, modulePos(c.x, c.z), Direction.NORTH, "", 1, c.room));
        for (Cell c : cells) out.add(new Step(Kind.PIECE, PieceType.FOUNDATION, modulePos(c.x, c.z).below(), Direction.NORTH, "", 1, c.room));
        // the main door: in the run on the entrance module's north side (else any side of it), in the piece over its middle
        Direction doorSide = null;
        for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.WEST, Direction.SOUTH}) {
            if (!isModule(first.x + d.getStepX(), first.z + d.getStepZ())) {
                doorSide = d;
                break;
            }
        }
        BlockPos firstPos = modulePos(first.x, first.z);
        List<Step> walls = new ArrayList<>();
        for (Seg s : segs) {
            boolean alongX = s.out.getAxis() == Direction.Axis.Z;
            for (int t = s.lo + 1; t + 1 <= s.hi; t += 3) {
                BlockPos m = alongX ? new BlockPos(t, y0, s.line) : new BlockPos(s.line, y0, t);
                boolean door = false;
                if (doorMaster == null && s.out == doorSide) {
                    BlockPos mid = firstPos.relative(doorSide, 2);
                    int along = alongX ? mid.getX() : mid.getZ();
                    int line = alongX ? mid.getZ() : mid.getX();
                    if (line == s.line && Math.abs(along - t) <= 1) {
                        door = true;
                        doorMaster = m;
                        doorFacing = s.out;
                    }
                }
                Step w = new Step(Kind.PIECE, door ? PieceType.DOORFRAME : PieceType.WALL, m, s.out, "", 1, null);
                if (door) walls.add(0, w);
                else walls.add(w);
            }
        }
        out.addAll(walls);
        if (doorMaster != null) out.add(new Step(Kind.DOOR, PieceType.DOORFRAME, doorMaster, doorFacing, "", 1, Room.SLEEP));
        if (depth > 0 && doorMaster != null) out.add(new Step(Kind.GROUND, null, doorMaster, doorFacing, "ramp", 1, Room.SLEEP));
        for (Cell c : cells) out.add(new Step(Kind.PIECE, PieceType.CEILING, modulePos(c.x, c.z).above(2), Direction.NORTH, "", 1, c.room));
        // 2. furniture where each part of the house has wall to stand against
        Set<BlockPos> keepFree = new HashSet<>();
        if (doorMaster != null) {
            keepFree.add(doorMaster.relative(doorFacing.getOpposite()));
            keepFree.add(doorMaster.relative(doorFacing.getOpposite()).relative(doorFacing.getClockWise()));
            keepFree.add(doorMaster.relative(doorFacing.getOpposite()).relative(doorFacing.getCounterClockWise()));
        }
        boolean kitchen = count(Room.KITCHEN) + count(Room.NURSERY) > 0;
        boolean lab = count(Room.LAB) > 0;
        int[] seen = new int[1];
        int idx = 0;
        for (Cell c : cells) furnish(out, c, roomPhase(c, idx++, seen), c == first && !kitchen, c.room == Room.SMELTER && !lab, keepFree);
        // 3. towers rising out of the house: walls stacked on all four sides, a lookout roof on top
        for (int[] t : raised) {
            BlockPos base = modulePos(t[0], t[1]);
            for (int lv = 1; lv < t[2]; lv++) {
                for (Direction d : H) out.add(new Step(Kind.PIECE, PieceType.WALL, base.relative(d, 2).above(lv * 3), d, "", 3, null));
            }
            out.add(new Step(Kind.PIECE, PieceType.CEILING, base.above(2 + (t[2] - 1) * 3), Direction.NORTH, "", 3, null));
        }
        // 4. watchtowers in the corners of the wall ring
        for (int[] t : towers) {
            BlockPos base = new BlockPos(t[0], y0, t[1]);
            Direction in = t[1] < 0 ? Direction.SOUTH : Direction.NORTH;
            out.add(new Step(Kind.GROUND, null, base, Direction.NORTH, "", 3, null));
            out.add(new Step(Kind.PIECE, PieceType.FOUNDATION, base.below(), Direction.NORTH, "", 3, null));
            for (int lv = 0; lv < t[2]; lv++) {
                for (Direction d : H) {
                    boolean door = lv == 0 && d == in;
                    BlockPos m = base.relative(d, 2).above(lv * 3);
                    out.add(new Step(Kind.PIECE, door ? PieceType.DOORFRAME : PieceType.WALL, m, d, "", 3, null));
                    if (door) out.add(new Step(Kind.DOOR, PieceType.DOORFRAME, m, d, "", 3, null));
                }
            }
            out.add(new Step(Kind.PIECE, PieceType.CEILING, base.above(2 + (t[2] - 1) * 3), Direction.NORTH, "", 3, null));
        }
        // 5. the wall ring(s) with a gate towards the main door
        if (ring >= 0) {
            int[] rr = ringRect(ring);
            boolean[] tw = new boolean[4];
            for (int[] t : towers) {
                boolean w = t[0] == rr[0] + 2, n = t[1] == rr[2] + 2;
                tw[(n ? 0 : 2) + (w ? 0 : 1)] = true;
            }
            fence(out, rr, y0, 3, tw, first.x * PITCH, false);
        }
        if (ring2 >= 0) fence(out, ringRect(ring2), y0, 4, new boolean[4], first.x * PITCH, false);
        // 6. dinosaur pens: fenced yards with a wide opening, a feed store inside
        for (int[] p : pens) {
            int[] rr = {p[0] * PITCH - 2, (p[0] + p[2] - 1) * PITCH + 2, p[1] * PITCH - 2, (p[1] + p[3] - 1) * PITCH + 2};
            rr[1] += Math.floorMod(2 - (rr[1] - rr[0] + 1), 3);
            rr[3] += Math.floorMod(2 - (rr[3] - rr[2] + 1), 3);
            fence(out, rr, y0, 4, new boolean[4], 0, true);
            out.add(new Step(Kind.FURNITURE, null, new BlockPos(rr[0] + 1, y0, rr[3] - 1), Direction.NORTH, "storage_box", 4, Room.BARN));
        }
        // 7. crop fields: rows of crop plots with a path every third row
        for (int[] f : fields) {
            int x0 = f[0] * PITCH - 1, x1 = (f[0] + f[2] - 1) * PITCH + 1, z0 = f[1] * PITCH - 1, z1 = (f[1] + f[3] - 1) * PITCH + 1;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if ((z - z0) % 3 == 2) continue;
                    out.add(new Step(Kind.CROP, null, new BlockPos(x, y0, z), Direction.NORTH, "crop_plot", 2, null));
                }
            }
        }
        return out;
    }

    private int roomPhase(Cell c, int idx, int[] seen) {
        if (idx == 0) return 1;
        return switch (c.room) {
            case KITCHEN, WORKSHOP -> 2;
            case STORAGE -> seen[0]++ == 0 ? 2 : 4;
            case SMELTER -> 3;
            case LAB -> 5;
            default -> idx < 3 ? 2 : 4;
        };
    }

    /**
     * a rectangle of wall pieces {xW, xE, zN, zS} standing on the ground; corners with a tower (tw = NW, NE, SW, SE)
     * are left to the tower, other corners stay a closed post gap; a gate (doorframe + door) or, for pens, a wide opening
     */
    private void fence(List<Step> out, int[] rr, int y0, int phase, boolean[] tw, int gateX, boolean pen) {
        int xW = rr[0], xE = rr[1], zN = rr[2], zS = rr[3];
        for (int row = 0; row < 2; row++) {
            boolean north = row == 0;
            int z = north ? zN : zS;
            int[] span = span(xW, xE, tw[north ? 0 : 2], tw[north ? 1 : 3]);
            List<Integer> xs = new ArrayList<>();
            for (int x = span[0] + 1; x + 1 <= span[1]; x += 3) xs.add(x);
            int gate = -1, gate2 = -1;
            if (north && !xs.isEmpty()) {
                if (pen) {
                    gate = xs.size() / 2;
                    if (xs.size() >= 4) gate2 = gate - 1;
                } else {
                    int best = 0;
                    for (int i = 1; i < xs.size(); i++) if (Math.abs(xs.get(i) - gateX) < Math.abs(xs.get(best) - gateX)) best = i;
                    gate = best;
                }
            }
            for (int i = 0; i < xs.size(); i++) {
                BlockPos m = new BlockPos(xs.get(i), y0, z);
                Direction f = north ? Direction.NORTH : Direction.SOUTH;
                if (i == gate || i == gate2) {
                    if (pen) continue;
                    out.add(new Step(Kind.PIECE, PieceType.DOORFRAME, m, f, "", phase, null));
                    out.add(new Step(Kind.DOOR, PieceType.DOORFRAME, m, f, "", phase, null));
                } else {
                    out.add(new Step(Kind.PIECE, PieceType.WALL, m, f, "", phase, null));
                }
            }
        }
        for (int col = 0; col < 2; col++) {
            boolean west = col == 0;
            int x = west ? xW : xE;
            int[] span = span(zN, zS, tw[west ? 0 : 1], tw[west ? 2 : 3]);
            for (int z = span[0] + 1; z + 1 <= span[1]; z += 3) {
                out.add(new Step(Kind.PIECE, PieceType.WALL, new BlockPos(x, y0, z), west ? Direction.WEST : Direction.EAST, "", phase, null));
            }
        }
    }

    /** first and last block a side covers so it tiles exactly with 3-wide pieces */
    private static int[] span(int a, int b, boolean towerA, boolean towerB) {
        int[] starts = towerA ? new int[]{a + 4, a + 5} : new int[]{a + 1};
        int[] ends = towerB ? new int[]{b - 4, b - 5} : new int[]{b - 1};
        for (int s : starts) for (int e : ends) if (e >= s && (e - s + 1) % 3 == 0) return new int[]{s, e};
        int s = starts[0], e = ends[0];
        return new int[]{s, s + Math.max(0, (e - s + 1) / 3 * 3) - 1};
    }

    /**
     * the furniture of one part of the house, in the corners that touch an outside wall (the middle cells stay free
     * as walkways between the parts); the bed lies along an outside wall without a door
     */
    private void furnish(List<Step> out, Cell c, int phase, boolean campfireHere, boolean labHere, Set<BlockPos> keepFree) {
        List<String> items = new ArrayList<>();
        switch (c.room) {
            case SLEEP -> items.add("storage_box");
            case STORAGE -> {
                items.add("storage_box");
                items.add("large_storage_box");
                items.add("storage_box");
            }
            case KITCHEN -> {
                items.add("campfire");
                items.add("storage_box");
            }
            case WORKSHOP -> {
                items.add("smithy");
                items.add("mortar_and_pestle");
                items.add("storage_box");
            }
            case SMELTER -> {
                items.add("refining_forge");
                if (labHere) items.add("fabricator");
                items.add("storage_box");
            }
            case LAB -> {
                items.add("fabricator");
                items.add("large_storage_box");
            }
            case ARMORY -> {
                items.add("large_storage_box");
                items.add("smithy");
            }
            case BARN -> {
                items.add("large_storage_box");
                items.add("storage_box");
            }
            case MARKET -> {
                items.add("storage_box");
                items.add("storage_box");
            }
            case NURSERY -> {
                items.add("storage_box");
                items.add("campfire");
            }
            default -> {
                return;               // halls stay open
            }
        }
        if (campfireHere) items.add(0, "campfire");
        BlockPos floor = modulePos(c.x, c.z);
        boolean[] outside = new boolean[4];
        for (int i = 0; i < 4; i++) outside[i] = !isModule(c.x + H[i].getStepX(), c.z + H[i].getStepZ());
        List<BlockPos> corners = new ArrayList<>();
        if (c.room == Room.SLEEP) {
            boolean any = false;
            for (int i = 0; i < 4; i++) any |= outside[i];
            if (!any) return;
        }
        for (int[] q : new int[][]{{-1, 1}, {1, 1}, {1, -1}, {-1, -1}}) {
            boolean wallX = outside[q[0] > 0 ? 1 : 3], wallZ = outside[q[1] > 0 ? 2 : 0];
            BlockPos p = floor.offset(q[0], 0, q[1]);
            if ((wallX || wallZ) && !keepFree.contains(p)) corners.add(p);
        }
        if (c.room == Room.SLEEP) {
            for (Direction d : new Direction[]{Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH}) {
                int di = d == Direction.NORTH ? 0 : d == Direction.EAST ? 1 : d == Direction.SOUTH ? 2 : 3;
                BlockPos mid = floor.relative(d, 1);
                if (!outside[di] || keepFree.contains(mid)) continue;
                BlockPos head = null;
                Direction toFoot = null;
                for (Direction side : new Direction[]{d.getClockWise(), d.getCounterClockWise()}) {
                    BlockPos h = mid.relative(side, 1);
                    if (!keepFree.contains(h)) {
                        head = h;
                        toFoot = side.getOpposite();
                        break;
                    }
                }
                if (head == null) continue;
                out.add(new Step(Kind.FURNITURE, null, head, toFoot, "bed", phase, c.room));
                corners.remove(head);
                keepFree.add(mid);
                break;
            }
        }
        for (int i = 0; i < items.size() && i < corners.size(); i++) {
            String it = items.get(i);
            int ph = switch (it) {
                case "large_storage_box", "refining_forge" -> Math.max(phase, 3);
                case "fabricator" -> Math.max(phase, 5);
                case "smithy", "mortar_and_pestle" -> Math.max(phase, 2);
                default -> phase;
            };
            BlockPos p = corners.get(i);
            Direction f = p.getZ() > floor.getZ() ? Direction.NORTH : Direction.SOUTH;
            out.add(new Step(Kind.FURNITURE, null, p, f, it, ph, c.room));
        }
    }

    /** where to stand inside at night: the middle of the first module */
    public BlockPos shelterOffset() {
        Cell c = cells.get(0);
        return modulePos(c.x, c.z);
    }

    /** the main door */
    public BlockPos doorOffset() {
        plan();
        return doorMaster != null ? doorMaster : shelterOffset().relative(Direction.NORTH, 2);
    }
}
