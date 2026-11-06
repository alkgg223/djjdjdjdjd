package com.arkragnarok.survivor;

import com.arkragnarok.building.PieceType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One of {@link #COUNT} base designs.  A design is a set of 3x3 foundation cells (square, rectangle, L, T, plus,
 * tower or two-block compound), 1 to 3 storeys, and one or two doors on the ground floor.  {@link #plan()} turns it
 * into the ordered list of pieces a survivor places one by one: foundations, ground floor walls / doorframes / doors,
 * the ceiling over every cell (which is the floor above), then the walls and ceilings of every upper storey.
 */
public final class BaseDesign {
    public static final int COUNT = 150;

    /** one piece: type (null = a door for the doorframe at this position), master offset from the base anchor, facing */
    public record Step(PieceType type, BlockPos offset, Direction facing) {
        public boolean door() {
            return type == null;
        }
    }

    public final int id;
    public final String shape;
    public final int floors;
    final Set<Long> cells = new HashSet<>();
    final List<long[]> doorEdges = new ArrayList<>();   // {cell key, direction ordinal}

    private BaseDesign(int id) {
        this.id = id;
        Random r = new Random(id * 7919L + 13);
        int kind = id % 7;
        int a = 1 + r.nextInt(3), b = 1 + r.nextInt(3);
        switch (kind) {
            case 0 -> {
                shape = "square";
                rect(0, 0, a, a);
            }
            case 1 -> {
                shape = "long hall";
                rect(0, 0, Math.max(2, a + 1), b);
            }
            case 2 -> {
                shape = "L";
                int n = 2 + r.nextInt(2);
                rect(0, 0, n, 1);
                rect(0, 1, 1, n - 1 + r.nextInt(2));
            }
            case 3 -> {
                shape = "T";
                int n = 3;
                rect(0, 0, n, 1);
                rect(1, 1, 1, 1 + r.nextInt(2));
            }
            case 4 -> {
                shape = "plus";
                rect(1, 0, 1, 3);
                rect(0, 1, 3, 1);
            }
            case 5 -> {
                shape = "tower";
                rect(0, 0, 1 + (a > 2 ? 1 : 0), 1 + (b > 2 ? 1 : 0));
            }
            default -> {
                shape = "compound";
                rect(0, 0, 2, 2);
                rect(2, 1, 1 + r.nextInt(2), 1);
            }
        }
        int maxFloors = cells.size() <= 1 ? 3 : cells.size() <= 4 ? 2 + (r.nextBoolean() ? 1 : 0) : 2;
        floors = kind == 5 ? 2 + r.nextInt(2) : 1 + r.nextInt(maxFloors);
        // doors: on the outer edges of the ground floor, the first one preferably facing north (the approach side)
        List<long[]> outer = outerEdges();
        long[] north = outer.stream().filter(e -> e[1] == Direction.NORTH.ordinal()).findFirst().orElse(outer.get(0));
        doorEdges.add(north);
        if (outer.size() > 6 && r.nextInt(3) == 0) doorEdges.add(outer.get(r.nextInt(outer.size())));
    }

    private static final BaseDesign[] ALL = new BaseDesign[COUNT];

    static {
        for (int i = 0; i < COUNT; i++) ALL[i] = new BaseDesign(i);
    }

    public static BaseDesign get(int id) {
        return ALL[Math.floorMod(id, COUNT)];
    }

    private void rect(int x0, int z0, int w, int d) {
        for (int x = x0; x < x0 + w; x++) for (int z = z0; z < z0 + d; z++) cells.add(key(x, z));
    }

    static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    static int kx(long k) {
        return (int) (k >> 32);
    }

    static int kz(long k) {
        return (int) k;
    }

    private static final Direction[] H = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private List<long[]> outerEdges() {
        List<long[]> out = new ArrayList<>();
        List<Long> sorted = new ArrayList<>(cells);
        sorted.sort(Long::compare);
        for (long c : sorted) {
            for (Direction d : H) {
                if (!cells.contains(key(kx(c) + d.getStepX(), kz(c) + d.getStepZ()))) out.add(new long[]{c, d.ordinal()});
            }
        }
        return out;
    }

    public int cellCount() {
        return cells.size();
    }

    /** half size of the footprint (blocks, around the anchor cell) for the room check */
    public int[] bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (long c : cells) {
            minX = Math.min(minX, kx(c));
            maxX = Math.max(maxX, kx(c));
            minZ = Math.min(minZ, kz(c));
            maxZ = Math.max(maxZ, kz(c));
        }
        return new int[]{minX * 3 - 2, maxX * 3 + 2, minZ * 3 - 2, maxZ * 3 + 2, floors * 3 + 1};
    }

    public List<Step> plan() {
        List<Step> out = new ArrayList<>();
        List<Long> sorted = new ArrayList<>(cells);
        sorted.sort(Long::compare);
        for (long c : sorted) out.add(new Step(PieceType.FOUNDATION, new BlockPos(kx(c) * 3, 0, kz(c) * 3), Direction.NORTH));
        for (int f = 0; f < floors; f++) {
            int y = 1 + 3 * f;
            List<Step> doors = new ArrayList<>();
            for (long[] e : outerEdges()) {
                long c = e[0];
                Direction d = Direction.values()[(int) e[1]];
                BlockPos m = new BlockPos(kx(c) * 3 + d.getStepX() * 2, y, kz(c) * 3 + d.getStepZ() * 2);
                boolean isDoor = f == 0 && doorEdges.stream().anyMatch(de -> de[0] == c && de[1] == e[1]);
                out.add(new Step(isDoor ? PieceType.DOORFRAME : PieceType.WALL, m, d));
                if (isDoor) doors.add(new Step(null, m, d));
            }
            out.addAll(doors);
            for (long c : sorted) out.add(new Step(PieceType.CEILING, new BlockPos(kx(c) * 3, y + 2, kz(c) * 3), Direction.NORTH));
        }
        return out;
    }

    /** where to stand inside at night: just inside the first door */
    public BlockPos shelterOffset() {
        long[] e = doorEdges.get(0);
        return new BlockPos(kx(e[0]) * 3, 1, kz(e[0]) * 3);
    }

    public BlockPos doorOffset() {
        long[] e = doorEdges.get(0);
        Direction d = Direction.values()[(int) e[1]];
        return new BlockPos(kx(e[0]) * 3 + d.getStepX() * 2, 1, kz(e[0]) * 3 + d.getStepZ() * 2);
    }

    public String describe() {
        return shape + ", " + cells.size() + " rooms, " + floors + (floors == 1 ? " floor" : " floors");
    }
}
