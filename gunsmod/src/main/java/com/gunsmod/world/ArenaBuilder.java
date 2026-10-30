package com.gunsmod.world;

import com.gunsmod.Reg;
import com.gunsmod.block.ArenaBlockEntity;
import com.gunsmod.block.FacingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a 50x50 battlefield: flattened ground, perimeter wall with barbed wire, a green and a red base, trenches,
 * sandbag nests, concrete barriers, a ruined two storey building, watch towers, crates and explosive barrels, and a
 * command console next to the green base.
 */
public final class ArenaBuilder {
    private ArenaBuilder() {}

    public static final int SIZE = 50;
    private static final int H = SIZE / 2;
    private static final int CLEAR = 22;

    private static ServerLevel level;
    private static BlockPos o;          // centre, ground level
    private static Direction fwd;       // from green base towards red base
    private static RandomSource rnd;

    /** local (x = right, z = forward towards red) -> world */
    private static BlockPos at(int x, int y, int z) {
        Direction right = fwd.getClockWise();
        return o.offset(right.getStepX() * x + fwd.getStepX() * z, y, right.getStepZ() * x + fwd.getStepZ() * z);
    }

    private static void set(int x, int y, int z, BlockState s) {
        level.setBlock(at(x, y, z), s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /** horizontal block facing a local direction: 0 = towards red, 1 = right, 2 = towards green, 3 = left */
    private static BlockState facing(Block b, int localDir) {
        Direction d = fwd;
        for (int i = 0; i < localDir; i++) d = d.getClockWise();
        return b.defaultBlockState().setValue(FacingBlock.FACING, d);
    }

    public static void build(ServerLevel sl, BlockPos center, Direction facing, Player player) {
        level = sl;
        o = center;
        fwd = facing;
        rnd = RandomSource.create(center.asLong());
        long t0 = System.currentTimeMillis();
        ground();
        walls();
        bases();
        trenches();
        cover();
        ruin();
        towers();
        lights();
        BlockPos console = at(-6, 1, -H + 4);
        level.setBlock(console, facing(Reg.ARENA_CONTROLLER.get(), 0), Block.UPDATE_ALL);
        if (level.getBlockEntity(console) instanceof ArenaBlockEntity be) {
            BlockPos a = at(-H, 0, -H), b = at(H - 1, CLEAR, H - 1);
            be.configure(at(0, 1, -H + 6), at(0, 1, H - 6),
                    new BlockPos(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ())),
                    new BlockPos(Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ())));
        }
        player.displayClientMessage(Component.translatable("gunsmod.msg.arena_done", System.currentTimeMillis() - t0), false);
        level = null;
    }

    private static void ground() {
        BlockState[] floor = {Blocks.COARSE_DIRT.defaultBlockState(), Blocks.DIRT_PATH.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
                Blocks.PACKED_MUD.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.ROOTED_DIRT.defaultBlockState()};
        for (int x = -H - 1; x <= H; x++) {
            for (int z = -H - 1; z <= H; z++) {
                for (int y = 1; y <= CLEAR; y++) {
                    BlockPos p = at(x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                for (int y = -4; y <= -1; y++) {
                    BlockPos p = at(x, y, z);
                    if (!level.getBlockState(p).isSolid()) level.setBlock(p, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                double n = Math.sin(x * 0.31) * Math.cos(z * 0.27) + rnd.nextDouble() * 0.9;
                int idx = n > 1.1 ? 2 : n > 0.75 ? 1 : n > 0.4 ? 0 : n > 0.0 ? 4 : n > -0.5 ? 3 : 5;
                set(x, 0, z, floor[idx]);
                if (idx == 4 && rnd.nextInt(6) == 0) set(x, 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                if (rnd.nextInt(90) == 0) set(x, 1, z, Blocks.DEAD_BUSH.defaultBlockState());
            }
        }
        // craters
        for (int i = 0; i < 6; i++) {
            int cx = rnd.nextInt(SIZE - 16) - H + 8, cz = rnd.nextInt(SIZE - 24) - H + 12;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dz * dz <= 5) {
                        set(cx + dx, 0, cz + dz, Blocks.AIR.defaultBlockState());
                        set(cx + dx, -1, cz + dz, dx * dx + dz * dz <= 1 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                    }
                }
            }
        }
    }

    private static void walls() {
        BlockState[] wall = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
                Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.ANDESITE.defaultBlockState()};
        for (int i = -H - 1; i <= H; i++) {
            for (int[] p : new int[][]{{i, -H - 1}, {i, H}, {-H - 1, i}, {H, i}}) {
                for (int y = 1; y <= 4; y++) set(p[0], y, p[1], wall[rnd.nextInt(wall.length)]);
                set(p[0], 5, p[1], Blocks.STONE_BRICK_WALL.defaultBlockState());
            }
        }
        // barbed wire line in front of the wall
        for (int i = -H + 1; i < H - 1; i += 1) {
            if (Math.abs(i) < 4) continue;
            if (rnd.nextInt(3) == 0) set(-H + 1, 1, i, facing(Reg.BARBED_WIRE.get(), 1));
            if (rnd.nextInt(3) == 0) set(H - 2, 1, i, facing(Reg.BARBED_WIRE.get(), 1));
        }
    }

    private static void bases() {
        for (int side = -1; side <= 1; side += 2) {
            Block concrete = side < 0 ? Blocks.GREEN_CONCRETE : Blocks.RED_CONCRETE;
            int zc = side * (H - 6);
            for (int x = -6; x <= 6; x++) {
                for (int z = -3; z <= 3; z++) {
                    boolean edge = Math.abs(x) == 6 || Math.abs(z) == 3;
                    set(x, 0, zc + z, edge ? concrete.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());
                }
            }
            // sandbag bunker in front of the base
            int front = zc - side * 5;
            for (int x = -7; x <= 7; x++) {
                if (Math.abs(x) <= 1) continue;
                set(x, 1, front, facing(Reg.SANDBAGS.get(), 0));
                if (Math.abs(x) >= 5) set(x, 2, front, facing(Reg.SANDBAGS_LOW.get(), 0));
            }
            // ammo supply + barrels
            set(side < 0 ? 5 : -5, 1, zc + side * 2, facing(Reg.AMMO_CRATE.get(), side < 0 ? 0 : 2));
            set(side < 0 ? -5 : 5, 1, zc + side * 2, facing(Reg.AMMO_CRATE.get(), side < 0 ? 0 : 2));
            // flag pole
            for (int y = 1; y <= 6; y++) set(0, y, zc + side * 3, Blocks.IRON_BARS.defaultBlockState());
            for (int y = 5; y <= 6; y++) {
                set(1, y, zc + side * 3, (side < 0 ? Blocks.GREEN_WOOL : Blocks.RED_WOOL).defaultBlockState());
                set(2, y, zc + side * 3, (side < 0 ? Blocks.GREEN_WOOL : Blocks.RED_WOOL).defaultBlockState());
            }
        }
    }

    private static void trenches() {
        for (int side = -1; side <= 1; side += 2) {
            int z = side * 9;
            for (int x = -16; x <= 16; x++) {
                if (Math.abs(x) >= 6 && Math.abs(x) <= 8) continue;
                set(x, 0, z, Blocks.AIR.defaultBlockState());
                set(x, -1, z, Blocks.MUD.defaultBlockState());
                if (rnd.nextInt(3) > 0) set(x, 1, z - side, facing(Reg.SANDBAGS_LOW.get(), side < 0 ? 0 : 2));
            }
        }
    }

    private static void cover() {
        // scattered sandbag nests, concrete barrier rows, crates and barrels
        int[][] nests = {{-16, -16}, {16, -16}, {-16, 16}, {16, 16}, {-20, 0}, {20, 0}, {0, -15}, {0, 15}};
        for (int[] n : nests) {
            for (int dx = -2; dx <= 2; dx++) set(n[0] + dx, 1, n[1] + (n[1] > 0 ? -2 : 2), facing(Reg.SANDBAGS.get(), n[1] > 0 ? 2 : 0));
            set(n[0] - 2, 1, n[1], facing(Reg.SANDBAGS.get(), 1));
            set(n[0] + 2, 1, n[1], facing(Reg.SANDBAGS.get(), 1));
        }
        for (int i = 0; i < 4; i++) {
            int x0 = -22 + i * 12, z = (i % 2 == 0 ? -4 : 4);
            for (int dx = 0; dx < 4; dx++) set(x0 + dx, 1, z, facing(Reg.CONCRETE_BARRIER.get(), 1));
        }
        for (int i = 0; i < 14; i++) {
            int x = rnd.nextInt(SIZE - 8) - H + 4, z = rnd.nextInt(SIZE - 26) - H + 13;
            if (Math.abs(x) < 6 && Math.abs(z) < 6) continue;
            int kind = rnd.nextInt(5);
            switch (kind) {
                case 0 -> set(x, 1, z, facing(Reg.EXPLOSIVE_BARREL.get(), 0));
                case 1 -> {
                    set(x, 1, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                    set(x + 1, 1, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                    set(x, 2, z, Blocks.BARREL.defaultBlockState());
                }
                case 2 -> {
                    for (int d = 0; d < 3; d++) set(x + d, 1, z, facing(Reg.CONCRETE_BARRIER.get(), 1));
                }
                default -> {
                    for (int d = 0; d < 3; d++) set(x + d, 1, z, facing(Reg.SANDBAGS.get(), 0));
                    set(x + 1, 2, z, facing(Reg.SANDBAGS_LOW.get(), 0));
                }
            }
        }
        // a burnt-out car wreck
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 2; dz++) set(10 + dx, 1, -2 + dz, Blocks.BLACKSTONE.defaultBlockState());
        }
        set(11, 2, -2, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
        set(12, 2, -1, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
    }

    private static void ruin() {
        // ruined two-storey building in the middle
        int x0 = -4, z0 = -4, w = 9, d = 9;
        BlockState wall = Blocks.BRICKS.defaultBlockState(), floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                boolean edge = x == x0 || x == x0 + w - 1 || z == z0 || z == z0 + d - 1;
                set(x, 0, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                if (edge) {
                    int top = 8 - ((x * 7 + z * 3) & 3) - (x > x0 + 5 && z > z0 + 4 ? 3 : 0);
                    for (int y = 1; y <= top; y++) {
                        boolean window = (y == 2 || y == 6) && ((x + z) % 3 == 0) && !(x == x0 && z == z0);
                        boolean door = y <= 2 && (x == x0 + 4) && (z == z0 || z == z0 + d - 1);
                        boolean doorSide = y <= 2 && (z == z0 + 4) && (x == x0 || x == x0 + w - 1);
                        if (door || doorSide) continue;
                        set(x, y, z, window ? Blocks.GLASS_PANE.defaultBlockState() : (rnd.nextInt(6) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : wall));
                    }
                } else if (!(x > x0 + 4 && z > z0 + 4)) {
                    set(x, 4, z, floor);
                }
            }
        }
        // stairs to the upper floor
        for (int i = 0; i < 3; i++) set(x0 + 1 + i, 1 + i, z0 + 6, Blocks.SPRUCE_PLANKS.defaultBlockState());
        set(x0 + 2, 5, z0 + 2, facing(Reg.SANDBAGS_LOW.get(), 0));
        set(x0 + 6, 5, z0 + 2, facing(Reg.SANDBAGS_LOW.get(), 0));
        // rubble
        for (int i = 0; i < 10; i++) {
            int x = x0 + rnd.nextInt(w + 4) - 2, z = z0 + rnd.nextInt(d + 4) - 2;
            if (level.getBlockState(at(x, 1, z)).isAir()) set(x, 1, z, rnd.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.BRICK_SLAB.defaultBlockState());
        }
    }

    private static void towers() {
        int[][] spots = {{-H + 3, -H + 3}, {H - 4, H - 4}};
        for (int[] s : spots) {
            for (int y = 1; y <= 6; y++) {
                for (int[] c : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}}) set(s[0] + c[0], y, s[1] + c[1], Blocks.SPRUCE_LOG.defaultBlockState());
            }
            for (int dx = -1; dx <= 3; dx++) {
                for (int dz = -1; dz <= 3; dz++) {
                    set(s[0] + dx, 7, s[1] + dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
                    boolean edge = dx == -1 || dx == 3 || dz == -1 || dz == 3;
                    if (edge) set(s[0] + dx, 8, s[1] + dz, facing(Reg.SANDBAGS_LOW.get(), 0));
                }
            }
            for (int y = 1; y <= 6; y++) set(s[0] + 1, y, s[1], Blocks.SPRUCE_PLANKS.defaultBlockState());
            for (int y = 1; y <= 7; y++) set(s[0] + 1, y, s[1] - 1, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, fwd.getOpposite()));
            set(s[0] + 1, 7, s[1] - 1, Blocks.AIR.defaultBlockState());
            set(s[0] + 1, 7, s[1] - 1, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, fwd.getOpposite()));
            for (int[] c : new int[][]{{-1, -1}, {3, -1}, {-1, 3}, {3, 3}}) {
                for (int y = 8; y <= 10; y++) set(s[0] + c[0], y, s[1] + c[1], Blocks.SPRUCE_FENCE.defaultBlockState());
            }
            for (int dx = -1; dx <= 3; dx++) {
                for (int dz = -1; dz <= 3; dz++) set(s[0] + dx, 11, s[1] + dz, Blocks.SPRUCE_SLAB.defaultBlockState());
            }
        }
    }

    private static void lights() {
        int[][] posts = {{-12, -H + 3}, {12, -H + 3}, {-12, H - 4}, {12, H - 4}, {-H + 2, 0}, {H - 3, 0}};
        for (int[] p : posts) {
            for (int y = 1; y <= 3; y++) set(p[0], y, p[1], Blocks.DARK_OAK_FENCE.defaultBlockState());
            set(p[0], 4, p[1], Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
    }
}
