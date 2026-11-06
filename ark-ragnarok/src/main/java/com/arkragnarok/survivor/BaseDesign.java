package com.arkragnarok.survivor;

import com.arkragnarok.building.PieceType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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
 * One of {@link #COUNT} base blueprints.  They are generated from 20 architectural archetypes (cabins, stone forts,
 * castles, mountain strongholds, forest villages, harbours, island bases, desert courtyards, underground bunkers, cave
 * bases, farms, breeding ranches, taming centres, military camps, research stations, industrial works, mining camps,
 * trade posts, advanced complexes and anti-giant fortresses), each in many variants that differ in layout - not
 * colour: room count and arrangement (row, block, L, ring around a courtyard, cluster of huts, cross, terraces),
 * storeys, what every room is for, interior doors, towers, perimeter walls and gates, dinosaur pens and crop fields.
 * Every design is checked to be different from all others.  Every design can grow into a full base: rooms the
 * progression needs (workshop, smelter, laboratory) are added as annexes when the archetype has none.
 *
 * Grid: rooms are 3x3 foundation cells on a 4-block pitch, so neighbouring rooms share the wall between them (a
 * wall or an interior doorway) and every room keeps its own ceiling.  Coordinates are relative to the anchor room
 * and unrotated (main entrance to the north); {@link #rotate} turns them for the site.
 */
public final class BaseDesign {
    public static final int COUNT = 25;
    public static final int PITCH = 4;

    public enum Archetype {
        CABIN("cabin", "flat"), STONE_FORT("stone fort", "flat"), CASTLE("castle", "flat"), MOUNTAIN("mountain stronghold", "mountain"),
        FOREST_VILLAGE("forest village", "forest"), HARBOUR("coastal harbour", "coast"), ISLAND("island base", "coast"),
        DESERT("desert courtyard", "desert"), UNDERGROUND("underground bunker", "any"), CAVE("cave base", "cave"),
        FARM("farmstead", "flat"), BREEDING("breeding ranch", "flat"), TAMING("taming centre", "flat"), MILITARY("military camp", "flat"),
        RESEARCH("research station", "any"), INDUSTRIAL("industrial works", "flat"), MINING("mining camp", "metal"),
        TRADE("trade post", "flat"), ADVANCED("advanced complex", "flat"), GIANT_DEFENSE("anti-giant fortress", "flat");

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
     * "fabricator"), a crop plot, or preparing ground (furniture "" = level the 5x5 plot of a room or tower,
     * "ramp" = dig the stairway of an underground base); phase 1 shelter .. 5 expansion; room = the room it belongs to.
     * A bed's offset is its head, its facing points from the head to the foot.
     */
    public record Step(Kind kind, @Nullable PieceType type, BlockPos offset, Direction facing, String furniture, int phase, @Nullable Room room) {
        public boolean door() {
            return kind == Kind.DOOR;
        }
    }

    record Cell(int x, int z, int floor, Room room) {}

    public final int id;
    public final Archetype archetype;
    public final int variant;
    public final String layout;
    public final int floors;
    final List<Cell> cells = new ArrayList<>();
    final Set<Long> doors = new HashSet<>();          // edges with a doorway: key(cell) * 4 + direction index
    final List<int[]> pens = new ArrayList<>();         // {x0, z0, w, d} in cells
    final List<int[]> fields = new ArrayList<>();       // {x0, z0, w, d} in cells, crop plots
    final List<int[]> towers = new ArrayList<>();       // {block x, block z, floors, 1 = corner tower of the wall ring}
    int ring = -1;                                      // perimeter wall margin in cells (-1 none)
    int ring2 = -1;                                     // second (outer) wall ring
    int depth;                                          // underground: anchor below the surface
    int annexes;                                        // rooms added so the base can progress
    private int cornerTowers;
    private List<Step> plan;

    private static final Direction[] H = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static final BaseDesign[] ALL = new BaseDesign[COUNT];

    static {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < COUNT; i++) {
            BaseDesign d = null;
            for (int attempt = 0; attempt < 40; attempt++) {
                d = new BaseDesign(i, attempt);
                if (seen.add(d.signature())) break;
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
        int f;
        String lay;
        switch (archetype) {
            case CABIN -> {
                lay = pick(v, "single", "pair", "L", "row3");
                block(lay, 0, 0, Room.SLEEP, Room.STORAGE, Room.KITCHEN);
                f = 1;
            }
            case STONE_FORT -> {
                lay = pick(v, "block2x2", "block3x2", "ring3", "T");
                block(lay, 0, 0, Room.HALL, Room.STORAGE, Room.SLEEP, Room.ARMORY, Room.WORKSHOP, Room.KITCHEN, Room.STORAGE, Room.SLEEP);
                f = 1 + v % 2;
                ring = 1;
                cornerTowers = 2 + v % 2;
            }
            case CASTLE -> {
                lay = pick(v, "block2x2", "block3x3", "block2x3", "cross");
                block(lay, 0, 0, Room.HALL, Room.SLEEP, Room.STORAGE, Room.ARMORY, Room.KITCHEN, Room.WORKSHOP, Room.SLEEP, Room.STORAGE, Room.LAB);
                f = 2 + v % 2;
                ring = 2;
                cornerTowers = 4;
                pens.add(new int[]{-3, 1, 1 + v % 2, 1});
            }
            case MOUNTAIN -> {
                lay = pick(v, "single", "pair", "terrace", "L");
                block(lay, 0, 0, Room.SLEEP, Room.STORAGE, Room.WORKSHOP, Room.SMELTER);
                f = 2 + v % 2;
                tower(lay.equals("single") ? 1 : -1, 0, f + 1);
            }
            case FOREST_VILLAGE -> {
                lay = pick(v, "huts3", "huts4", "huts5", "huts3+");
                int n = lay.equals("huts3") || lay.equals("huts3+") ? 3 : lay.equals("huts4") ? 4 : 5;
                Room[] rooms = {Room.SLEEP, Room.STORAGE, Room.KITCHEN, Room.WORKSHOP, Room.SLEEP};
                int[][] spots = {{0, 0}, {2, 0}, {0, 2}, {2, 2}, {-2, 1}, {1, -2}};
                for (int i = 0; i < n; i++) cells.add(new Cell(spots[(i + v) % spots.length][0], spots[(i + v) % spots.length][1], 0, rooms[i]));
                if (lay.equals("huts3+")) fields.add(new int[]{4, 0, 1, 2});
                f = 1;
            }
            case HARBOUR -> {
                lay = pick(v, "pier3", "pier4", "quay", "pierL");
                int n = lay.equals("pier3") ? 3 : lay.equals("pier4") ? 4 : 3;
                for (int i = 0; i < n; i++) cells.add(new Cell(0, i, 0, i == 0 ? Room.SLEEP : i == 1 ? Room.STORAGE : Room.MARKET));
                if (lay.equals("quay")) cells.add(new Cell(1, 0, 0, Room.KITCHEN));
                if (lay.equals("pierL")) cells.add(new Cell(1, n - 1, 0, Room.STORAGE));
                f = 1 + v % 2;
                tower(-1, n - 1, 2 + v % 2);
            }
            case ISLAND -> {
                lay = pick(v, "ring3", "block2x2", "cross", "pair");
                block(lay, 0, 0, Room.SLEEP, Room.STORAGE, Room.KITCHEN, Room.WORKSHOP, Room.STORAGE, Room.SLEEP, Room.LAB, Room.ARMORY);
                f = 1 + v % 2;
                cornerTowers = 1 + v % 3;
            }
            case DESERT -> {
                lay = pick(v, "ring3", "ringU", "block2x3", "ring3");
                block(lay, 0, 0, Room.SLEEP, Room.STORAGE, Room.KITCHEN, Room.WORKSHOP, Room.SLEEP, Room.STORAGE, Room.MARKET, Room.LAB);
                f = 1 + (v / 2) % 2;
                if (v % 2 == 1) ring = 1;
            }
            case UNDERGROUND -> {
                lay = pick(v, "row3", "block2x2", "L", "cross");
                block(lay, 0, 0, Room.HALL, Room.STORAGE, Room.SLEEP, Room.WORKSHOP, Room.SMELTER, Room.LAB);
                f = 1;
                depth = 4 + v % 3;
            }
            case CAVE -> {
                lay = pick(v, "pair", "row3", "L", "single");
                block(lay, 0, 0, Room.SLEEP, Room.STORAGE, Room.SMELTER, Room.WORKSHOP);
                f = 1;
            }
            case FARM -> {
                lay = pick(v, "pair", "L", "row3", "single");
                block(lay, 0, 0, Room.SLEEP, Room.BARN, Room.KITCHEN, Room.STORAGE);
                f = 1;
                fields.add(new int[]{2, 0, 2 + v % 2, 2 + (v / 2) % 2});
                if (v % 3 == 2) fields.add(new int[]{-3, 0, 2, 2});
                ring = v % 2 == 0 ? -1 : 2;
            }
            case BREEDING -> {
                lay = pick(v, "pair", "row3", "L", "pair");
                block(lay, 0, 0, Room.NURSERY, Room.STORAGE, Room.SLEEP, Room.KITCHEN);
                f = 1 + v % 2;
                pens.add(new int[]{0, 2, 2 + v % 2, 2});
                if (v >= 3) pens.add(new int[]{-3, 0, 2, 2 + v % 2});
            }
            case TAMING -> {
                lay = pick(v, "single", "pair", "L", "row3");
                block(lay, 0, 0, Room.KITCHEN, Room.STORAGE, Room.SLEEP, Room.WORKSHOP);
                f = 1;
                pens.add(new int[]{2, 0, 1 + v % 2, 2});
                tower(-1, -1, 2);
            }
            case MILITARY -> {
                lay = pick(v, "row3", "block3x2", "T", "row4");
                block(lay, 0, 0, Room.SLEEP, Room.SLEEP, Room.ARMORY, Room.STORAGE, Room.WORKSHOP, Room.KITCHEN, Room.SLEEP, Room.ARMORY);
                f = 2;
                ring = 1;
                if (v % 2 == 1) ring2 = 3;
                cornerTowers = 2 + v % 3;
            }
            case RESEARCH -> {
                lay = pick(v, "block2x2", "cross", "L", "row3");
                block(lay, 0, 0, Room.LAB, Room.LAB, Room.STORAGE, Room.SLEEP, Room.WORKSHOP, Room.KITCHEN);
                f = 1 + v % 3;
                tower(2, -1, 3);
            }
            case INDUSTRIAL -> {
                lay = pick(v, "block3x2", "row4", "block2x2", "L");
                block(lay, 0, 0, Room.SMELTER, Room.WORKSHOP, Room.STORAGE, Room.STORAGE, Room.SMELTER, Room.WORKSHOP, Room.SLEEP, Room.LAB);
                f = 1 + v % 2;
                tower(-1, 0, 3 + v % 2);         // chimney / lookout
            }
            case MINING -> {
                lay = pick(v, "pair", "row3", "L", "single");
                block(lay, 0, 0, Room.SMELTER, Room.STORAGE, Room.SLEEP, Room.WORKSHOP);
                f = 1;
                if (v % 2 == 0) tower(1, 1, 2);
            }
            case TRADE -> {
                lay = pick(v, "plaza", "plazaU", "row4", "plaza");
                if (lay.startsWith("plaza")) {
                    int[][] stalls = {{-1, -1}, {0, -1}, {1, -1}, {-1, 1}, {0, 1}, {1, 1}, {-1, 0}, {1, 0}};
                    int n = lay.equals("plaza") ? 6 + v % 3 : 5;
                    for (int i = 0; i < n; i++) cells.add(new Cell(stalls[i][0], stalls[i][1], 0, i < 2 ? (i == 0 ? Room.SLEEP : Room.STORAGE) : Room.MARKET));
                } else {
                    block(lay, 0, 0, Room.SLEEP, Room.MARKET, Room.MARKET, Room.STORAGE);
                }
                f = 1;
            }
            case ADVANCED -> {
                lay = pick(v, "block2x2", "block3x2", "cross", "ring3");
                block(lay, 0, 0, Room.LAB, Room.HALL, Room.STORAGE, Room.SMELTER, Room.WORKSHOP, Room.SLEEP, Room.ARMORY, Room.LAB);
                f = 2 + v % 2;
                ring = 2;
                cornerTowers = 2 + v % 3;
                pens.add(new int[]{-4, 0, 2, 2});
            }
            default -> {          // GIANT_DEFENSE
                lay = pick(v, "block2x2", "single", "T", "block2x2");
                block(lay, 0, 0, Room.HALL, Room.STORAGE, Room.SLEEP, Room.ARMORY, Room.KITCHEN);
                f = 1 + v % 2;
                ring = 1;
                ring2 = 3;
                cornerTowers = 4;
            }
        }
        this.layout = lay;
        ensure(Room.SLEEP);
        ensure(Room.WORKSHOP);
        ensure(Room.SMELTER);
        ensure(Room.LAB);
        shelterFirst();
        separate();
        if (cornerTowers > 0) corners(cornerTowers, r);
        // storeys: every room of the main block gets the same number (towers and huts stay single unless stated)
        this.floors = f;
        if (f > 1) {
            List<Cell> upper = new ArrayList<>();
            for (int fl = 1; fl < f; fl++) {
                for (Cell c : cells) {
                    if (archetype == Archetype.FOREST_VILLAGE || c.floor != 0) continue;
                    Room up = fl == 1 ? (c.room == Room.STORAGE ? Room.SLEEP : c.room == Room.HALL ? Room.LAB : Room.STORAGE) : Room.ARMORY;
                    upper.add(new Cell(c.x, c.z, fl, up));
                }
            }
            cells.addAll(upper);
        }
        // doors: the main entrance on the north of the first room, interior doorways linking neighbouring rooms
        Cell first = cells.get(0);
        doors.add(edgeKey(first.x, first.z, first.floor, Direction.NORTH));
        for (Cell a : cells) {
            for (Direction d : new Direction[]{Direction.EAST, Direction.SOUTH}) {
                Cell b = at(a.x + d.getStepX(), a.z + d.getStepZ(), a.floor);
                if (b != null && (r.nextInt(3) != 0 || a.room == Room.HALL)) addDoor(a, b, d);
            }
        }
        if (archetype == Archetype.FOREST_VILLAGE || archetype == Archetype.TRADE) {
            for (Cell c : cells) {
                if (c.floor != 0) continue;
                Direction d = archetype == Archetype.TRADE && c.z > 0 ? Direction.SOUTH : Direction.NORTH;
                if (at(c.x + d.getStepX(), c.z + d.getStepZ(), 0) == null) doors.add(edgeKey(c.x, c.z, 0, d));
            }
        }
        connect();
    }

    private static String pick(int v, String... options) {
        return options[Math.floorMod(v, options.length)];
    }

    private void tower(int cx, int cz, int fl) {
        towers.add(new int[]{cx * PITCH, cz * PITCH, fl, 0});
    }

    private static int cellOf(int block) {
        return Math.floorDiv(block + 2, PITCH);
    }

    private void block(String lay, int x0, int z0, Room... rooms) {
        int[][] shape = switch (lay) {
            case "single" -> new int[][]{{0, 0}};
            case "pair" -> new int[][]{{0, 0}, {1, 0}};
            case "L" -> new int[][]{{0, 0}, {1, 0}, {0, 1}};
            case "row3" -> new int[][]{{0, 0}, {1, 0}, {2, 0}};
            case "row4" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}};
            case "block2x2" -> new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}};
            case "block3x2" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}, {2, 1}};
            case "block2x3" -> new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}, {0, 2}, {1, 2}};
            case "block3x3" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}};
            case "ring3" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {0, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}};     // courtyard in the middle
            case "ringU" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {0, 1}, {2, 1}, {0, 2}, {2, 2}};
            case "T" -> new int[][]{{0, 0}, {1, 0}, {2, 0}, {1, 1}, {1, 2}};
            case "cross" -> new int[][]{{1, 0}, {0, 1}, {1, 1}, {2, 1}, {1, 2}};
            case "terrace" -> new int[][]{{0, 0}, {0, 1}};
            default -> new int[][]{{0, 0}};
        };
        for (int i = 0; i < shape.length; i++) cells.add(new Cell(x0 + shape[i][0], z0 + shape[i][1], 0, rooms[i % rooms.length]));
    }

    /** adds an annex room when the archetype has none of this kind (every base must be able to progress) */
    private void ensure(Room room) {
        for (Cell c : cells) if (c.room == room) return;
        Direction[] order = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
        for (int i = cells.size() - 1; i >= 0; i--) {
            Cell c = cells.get(i);
            if (c.floor != 0) continue;
            for (Direction d : order) {
                if (i == 0 && d == Direction.NORTH) continue;          // keep the main entrance free
                int x = c.x + d.getStepX(), z = c.z + d.getStepZ();
                if (freeCell(x, z)) {
                    cells.add(new Cell(x, z, 0, room));
                    annexes++;
                    return;
                }
            }
        }
    }

    private boolean freeCell(int x, int z) {
        if (at(x, z, 0) != null) return false;
        for (int[] t : towers) if (cellOf(t[0]) == x && cellOf(t[1]) == z) return false;
        return true;
    }

    /** the shelter (first room, next to the entrance, built first) is where it sleeps */
    private void shelterFirst() {
        Cell c0 = cells.get(0);
        if (c0.room == Room.SLEEP) return;
        for (int k = 1; k < cells.size(); k++) {
            Cell ck = cells.get(k);
            if (ck.room == Room.SLEEP && ck.floor == 0) {
                cells.set(0, new Cell(c0.x, c0.z, 0, Room.SLEEP));
                cells.set(k, new Cell(ck.x, ck.z, 0, c0.room));
                return;
            }
        }
    }

    /** pens and fields keep a free cell to the rooms and to each other, towers stand clear of rooms and pens */
    private void separate() {
        List<int[]> placed = new ArrayList<>();
        for (List<int[]> group : List.of(pens, fields)) {
            for (int[] p : group) {
                int sx = p[0] >= 0 ? 1 : -1;
                for (int k = 0; k < 14 && rectBlocked(p, placed); k++) {
                    if (k < 9) p[0] += sx;
                    else p[1] += 1;
                }
                placed.add(p);
            }
        }
        for (int[] t : towers) {
            for (int k = 0; k < 8 && towerBlocked(t, placed); k++) t[0] += t[0] >= 0 ? PITCH : -PITCH;
        }
    }

    private boolean rectBlocked(int[] p, List<int[]> placed) {
        for (int x = p[0] - 1; x <= p[0] + p[2]; x++) {
            for (int z = p[1] - 1; z <= p[1] + p[3]; z++) {
                if (at(x, z, 0) != null) return true;
                for (int[] t : towers) if (cellOf(t[0]) == x && cellOf(t[1]) == z) return true;
                for (int[] q : placed) if (x >= q[0] && x < q[0] + q[2] && z >= q[1] && z < q[1] + q[3]) return true;
            }
        }
        return false;
    }

    private boolean towerBlocked(int[] t, List<int[]> placed) {
        int x = cellOf(t[0]), z = cellOf(t[1]);
        if (at(x, z, 0) != null) return true;
        for (int[] q : placed) if (x >= q[0] - 1 && x <= q[0] + q[2] && z >= q[1] - 1 && z <= q[1] + q[3]) return true;
        return false;
    }

    /** watchtowers in the corners of the (inner) wall ring: NW, SE, NE, SW */
    private void corners(int n, Random r) {
        int[] rr = ringRect(ring >= 0 ? ring : 1);
        int[][] c = {{rr[0] + 2, rr[2] + 2}, {rr[1] - 2, rr[3] - 2}, {rr[1] - 2, rr[2] + 2}, {rr[0] + 2, rr[3] - 2}};
        for (int i = 0; i < Math.min(4, n); i++) towers.add(new int[]{c[i][0], c[i][1], 2 + r.nextInt(2), 1});
    }

    /** rooms, pens, fields and free-standing towers in cells: {x0, x1, z0, z1} */
    private int[] cellBounds() {
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
        for (int[] t : towers) {
            if (t[3] == 1) continue;
            x0 = Math.min(x0, cellOf(t[0]));
            x1 = Math.max(x1, cellOf(t[0]));
            z0 = Math.min(z0, cellOf(t[1]));
            z1 = Math.max(z1, cellOf(t[1]));
        }
        return new int[]{x0, x1, z0, z1};
    }

    /** a wall ring m cells outside everything, sized so its sides tile exactly with 3-wide wall pieces */
    private int[] ringRect(int m) {
        int[] b = cellBounds();
        int xW = (b[0] - m) * PITCH - 2, xE = (b[1] + m) * PITCH + 2, zN = (b[2] - m) * PITCH - 2, zS = (b[3] + m) * PITCH + 2;
        xE += Math.floorMod(2 - (xE - xW + 1), 3);
        zS += Math.floorMod(2 - (zS - zN + 1), 3);
        return new int[]{xW, xE, zN, zS};
    }

    Cell at(int x, int z, int floor) {
        for (Cell c : cells) if (c.x == x && c.z == z && c.floor == floor) return c;
        return null;
    }

    private static long edgeKey(int x, int z, int floor, Direction d) {
        // edges are stored from the west / north cell so both rooms see the same doorway
        if (d == Direction.WEST) return edgeKey(x - 1, z, floor, Direction.EAST);
        if (d == Direction.NORTH) return (((long) (x + 500) * 1000 + (z + 500)) * 10 + floor) * 4;
        if (d == Direction.SOUTH) return edgeKey(x, z + 1, floor, Direction.NORTH);
        return (((long) (x + 500) * 1000 + (z + 500)) * 10 + floor) * 4 + 1;
    }

    private boolean hasDoor(Cell c, Direction d) {
        return doors.contains(edgeKey(c.x, c.z, c.floor, d));
    }

    private int doorCount(Cell c) {
        int n = 0;
        for (Direction d : H) if (hasDoor(c, d)) n++;
        return n;
    }

    /** bedrooms keep at least one solid wall for the bed */
    private void addDoor(Cell a, Cell b, Direction d) {
        if ((a.room == Room.SLEEP && doorCount(a) >= 3) || (b.room == Room.SLEEP && doorCount(b) >= 3)) return;
        doors.add(edgeKey(a.x, a.z, a.floor, d));
    }

    /** every room of a storey can be reached from its first room */
    private void connect() {
        for (int fl = 0; fl < floors; fl++) {
            Cell start = null;
            for (Cell c : cells) if (c.floor == fl) {
                start = c;
                break;
            }
            if (start == null) continue;
            for (int guard = 0; guard < 40; guard++) {
                Set<Cell> reach = new HashSet<>();
                ArrayDeque<Cell> q = new ArrayDeque<>();
                q.add(start);
                while (!q.isEmpty()) {
                    Cell c = q.poll();
                    if (!reach.add(c)) continue;
                    for (Direction d : H) {
                        Cell n = at(c.x + d.getStepX(), c.z + d.getStepZ(), fl);
                        if (n != null && hasDoor(c, d)) q.add(n);
                    }
                }
                boolean added = false;
                for (Cell c : cells) {
                    if (c.floor != fl || reach.contains(c) || added) continue;
                    if (archetype == Archetype.FOREST_VILLAGE || archetype == Archetype.TRADE) {
                        if (fl == 0 && doorCount(c) > 0) continue;       // huts and stalls have their own doors
                    }
                    for (Direction d : H) {
                        Cell n = at(c.x + d.getStepX(), c.z + d.getStepZ(), fl);
                        if (n != null && reach.contains(n)) {
                            doors.add(edgeKey(c.x, c.z, fl, d));
                            added = true;
                            break;
                        }
                    }
                }
                if (!added) break;
            }
        }
    }

    public String signature() {
        StringBuilder b = new StringBuilder(archetype.name()).append('|').append(layout).append('|').append(floors).append('|');
        List<String> cs = new ArrayList<>();
        for (Cell c : cells) cs.add(c.x + "," + c.z + "," + c.floor + "," + c.room);
        Collections.sort(cs);
        b.append(cs).append(pens.size()).append(fields.size()).append(towers.size()).append(ring).append(ring2).append(depth);
        for (int[] p : pens) b.append(p[0]).append(p[1]).append(p[2]).append(p[3]);
        for (int[] p : fields) b.append(p[0]).append(p[1]).append(p[2]).append(p[3]);
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
        return towers.size();
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

    /** material demand: pieces of the whole plan */
    public int pieceCount() {
        int n = 0;
        for (Step s : plan()) if (s.kind == Kind.PIECE) n++;
        return n;
    }

    public String describe() {
        return archetype.label + " (" + layout + ", " + cells.size() + " rooms, " + floors + (floors == 1 ? " floor" : " floors")
                + (towers.isEmpty() ? "" : ", " + towers.size() + " towers") + (pens.isEmpty() ? "" : ", " + pens.size() + " pens")
                + (fields.isEmpty() ? "" : ", fields") + (ring >= 0 ? (ring2 >= 0 ? ", double walls" : ", walls") : "")
                + (depth > 0 ? ", underground" : "") + ")";
    }

    /** list of rooms and their use, for the blueprint viewer */
    public Map<String, Integer> roomSummary() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Cell c : cells) m.merge(c.room.name().toLowerCase(), 1, Integer::sum);
        return m;
    }

    /** top-down cells for the blueprint viewer: {x, z, floor, room ordinal} plus pens (-1), fields (-2), towers (-3) */
    public List<int[]> mapCells() {
        List<int[]> out = new ArrayList<>();
        for (Cell c : cells) out.add(new int[]{c.x, c.z, c.floor, c.room.ordinal()});
        for (int[] p : pens) for (int x = 0; x < p[2]; x++) for (int z = 0; z < p[3]; z++) out.add(new int[]{p[0] + x, p[1] + z, 0, -1});
        for (int[] p : fields) for (int x = 0; x < p[2]; x++) for (int z = 0; z < p[3]; z++) out.add(new int[]{p[0] + x, p[1] + z, 0, -2});
        for (int[] t : towers) out.add(new int[]{cellOf(t[0]), cellOf(t[1]), 0, -3});
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

    private static BlockPos cellPos(int x, int z, int floor) {
        return new BlockPos(x * PITCH, floor * 3, z * PITCH);
    }

    private int roomPhase(Cell c, int idx, int[] seen) {
        if (c.floor > 0) return 5;
        if (idx == 0) return 1;
        return switch (c.room) {
            case KITCHEN, WORKSHOP -> 2;
            case STORAGE -> seen[0]++ == 0 ? 2 : 4;
            case SMELTER -> 3;
            case LAB -> 5;
            default -> idx < 3 ? 2 : 4;
        };
    }

    private List<Step> buildPlan() {
        List<Step> out = new ArrayList<>();
        int y0 = -depth;
        // rooms, storey by storey; the first room (shelter) and its neighbours come first
        List<Cell> order = new ArrayList<>(cells);
        Cell first = cells.get(0);
        order.sort((a, b) -> a == b ? 0 : a.floor != b.floor ? Integer.compare(a.floor, b.floor)
                : a == first ? -1 : b == first ? 1
                : Integer.compare(Math.abs(a.x - first.x) + Math.abs(a.z - first.z), Math.abs(b.x - first.x) + Math.abs(b.z - first.z)));
        boolean kitchen = count(Room.KITCHEN) + count(Room.NURSERY) > 0;
        Set<Long> wallsDone = new HashSet<>();
        Set<BlockPos> masters = new HashSet<>();            // wall lines already planned (towers attached to rooms share them)
        int[] seen = new int[1];
        int idx = 0;
        for (Cell c : order) {
            int phase = roomPhase(c, idx, seen);
            idx++;
            BlockPos base = cellPos(c.x, c.z, c.floor).above(y0);
            if (c.floor == 0) {
                out.add(new Step(Kind.GROUND, null, base, Direction.NORTH, "", phase, c.room));
                if (c == first && depth > 0) {
                    out.add(new Step(Kind.GROUND, null, base.relative(Direction.NORTH, 2).above(1), Direction.NORTH, "ramp", 1, c.room));
                }
                out.add(new Step(Kind.PIECE, PieceType.FOUNDATION, base, Direction.NORTH, "", phase, c.room));
            }
            List<Step> doorSteps = new ArrayList<>();
            for (Direction d : H) {
                long key = edgeKey(c.x, c.z, c.floor, d);
                if (!wallsDone.add(key)) continue;          // shared wall already planned by the neighbour
                BlockPos m = base.relative(d, 2).above(1);
                masters.add(m);
                boolean door = doors.contains(key);
                out.add(new Step(Kind.PIECE, door ? PieceType.DOORFRAME : PieceType.WALL, m, d, "", phase, c.room));
                if (door) doorSteps.add(new Step(Kind.DOOR, PieceType.DOORFRAME, m, d, "", phase, c.room));
            }
            out.addAll(doorSteps);
            out.add(new Step(Kind.PIECE, PieceType.CEILING, base.above(3), Direction.NORTH, "", phase, c.room));
            furnish(out, c, base.above(1), phase, c == first && !kitchen);
        }
        // towers: stacked single rooms with a lookout on top
        for (int[] t : towers) {
            BlockPos base = new BlockPos(t[0], y0, t[1]);
            out.add(new Step(Kind.GROUND, null, base, Direction.NORTH, "", 3, null));
            out.add(new Step(Kind.PIECE, PieceType.FOUNDATION, base, Direction.NORTH, "", 3, null));
            for (int fl = 0; fl < t[2]; fl++) {
                for (Direction d : H) {
                    boolean door = fl == 0 && d == Direction.SOUTH;
                    BlockPos m = base.relative(d, 2).above(1 + fl * 3);
                    if (!masters.add(m)) continue;
                    out.add(new Step(Kind.PIECE, door ? PieceType.DOORFRAME : PieceType.WALL, m, d, "", 3, null));
                    if (door) out.add(new Step(Kind.DOOR, PieceType.DOORFRAME, m, d, "", 3, null));
                }
                out.add(new Step(Kind.PIECE, PieceType.CEILING, base.above(3 + fl * 3), Direction.NORTH, "", 3, null));
            }
        }
        // perimeter walls (corner towers fill their corners) with a gate in the north side
        if (ring >= 0) {
            int[] rr = ringRect(ring);
            boolean[] tw = new boolean[4];
            for (int[] t : towers) {
                if (t[3] != 1) continue;
                boolean w = t[0] == rr[0] + 2, n = t[1] == rr[2] + 2;
                tw[(n ? 0 : 2) + (w ? 0 : 1)] = true;
            }
            fence(out, rr, y0, 3, tw, first.x * PITCH, false);
        }
        if (ring2 >= 0) fence(out, ringRect(ring2), y0, 4, new boolean[4], first.x * PITCH, false);
        // dinosaur pens: fenced yards with a wide opening in the north side, a feed store inside
        for (int[] p : pens) {
            int[] rr = {p[0] * PITCH - 2, (p[0] + p[2] - 1) * PITCH + 2, p[1] * PITCH - 2, (p[1] + p[3] - 1) * PITCH + 2};
            rr[1] += Math.floorMod(2 - (rr[1] - rr[0] + 1), 3);
            rr[3] += Math.floorMod(2 - (rr[3] - rr[2] + 1), 3);
            fence(out, rr, y0, 4, new boolean[4], 0, true);
            out.add(new Step(Kind.FURNITURE, null, new BlockPos(rr[0] + 1, y0, rr[3] - 1), Direction.NORTH, "storage_box", 4, Room.BARN));
        }
        // crop fields: rows of crop plots with a path every third row
        for (int[] fdl : fields) {
            int x0 = fdl[0] * PITCH - 1, x1 = (fdl[0] + fdl[2] - 1) * PITCH + 1, z0 = fdl[1] * PITCH - 1, z1 = (fdl[1] + fdl[3] - 1) * PITCH + 1;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if ((z - z0) % 3 == 2) continue;
                    out.add(new Step(Kind.CROP, null, new BlockPos(x, y0, z), Direction.NORTH, "crop_plot", 2, null));
                }
            }
        }
        return out;
    }

    /**
     * a rectangle of wall pieces {xW, xE, zN, zS}; corners with a tower (tw = NW, NE, SW, SE) are left to the tower,
     * other corners stay as a closed post gap; a gate (doorframe + door) or, for pens, a wide opening in the north row
     */
    private void fence(List<Step> out, int[] rr, int y0, int phase, boolean[] tw, int gateX, boolean pen) {
        int xW = rr[0], xE = rr[1], zN = rr[2], zS = rr[3];
        int y = y0 + 1;
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
                BlockPos m = new BlockPos(xs.get(i), y, z);
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
                out.add(new Step(Kind.PIECE, PieceType.WALL, new BlockPos(x, y, z), west ? Direction.WEST : Direction.EAST, "", phase, null));
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

    /** furniture of a room in its corners (the middle and the doorways stay free); the bed lies along a solid wall */
    private void furnish(List<Step> out, Cell c, BlockPos floor, int phase, boolean campfireHere) {
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
                items.add("storage_box");
                items.add("large_storage_box");
            }
        }
        if (campfireHere) items.add(0, "campfire");
        List<BlockPos> corners = new ArrayList<>(List.of(floor.offset(-1, 0, 1), floor.offset(1, 0, 1), floor.offset(1, 0, -1), floor.offset(-1, 0, -1)));
        if (c.room == Room.SLEEP) {
            for (Direction d : new Direction[]{Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH}) {
                if (hasDoor(c, d)) continue;
                BlockPos head = floor.relative(d, 1).relative(d.getClockWise(), 1);
                out.add(new Step(Kind.FURNITURE, null, head, d.getCounterClockWise(), "bed", phase, c.room));
                corners.remove(head);
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

    /** where to stand inside at night: the first room */
    public BlockPos shelterOffset() {
        Cell c = cells.get(0);
        return cellPos(c.x, c.z, 0).above(1 - depth);
    }

    /** the main door (north side of the first room) */
    public BlockPos doorOffset() {
        Cell c = cells.get(0);
        return cellPos(c.x, c.z, 0).relative(Direction.NORTH, 2).above(1 - depth);
    }
}
