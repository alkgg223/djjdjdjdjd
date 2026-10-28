package com.gunsmod.world;

import com.gunsmod.Reg;
import com.gunsmod.block.FacingBlock;
import com.gunsmod.block.SpawnerBlockEntity;
import com.gunsmod.block.VehicleSpawnerBlockEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.VehicleKind;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The orchard battlefield, 300 x 500: open country of orchards with scattered fruit trees and grass, dirt tracks,
 * a few ruined farmhouses, and at each end a team base with soldier deployment pads, two tanks, an attack helicopter,
 * a Pantsir air defence system and an F-16 on its own runway, all kept in service by vehicle spawners.
 * Far too big to build in one go: the job does a few rows every server tick. The soldiers deployed here know the
 * field (see {@link BattleMap}): the way to the enemy base and the roads that lead there.
 */
public final class OrchardBuilder {
    private OrchardBuilder() {}

    public static final int W = 300, L = 500;
    private static final int HW = W / 2, HL = L / 2, CLEAR = 24;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final List<Job> JOBS = new ArrayList<>();

    public static void start(ServerLevel level, BlockPos center, Direction fwd, ServerPlayer player) {
        JOBS.add(new Job(level, center, fwd, player.getUUID(), false));
    }

    /** the whole world is the battlefield (the world type picked when the world was created) */
    public static void startWorld(ServerLevel level, BlockPos center, Direction fwd) {
        JOBS.add(new Job(level, center, fwd, null, true));
    }

    public static boolean busy() {
        return !JOBS.isEmpty();
    }

    /** server tick: advance the jobs (a job left over from a world that was closed meanwhile is dropped) */
    public static void tick(MinecraftServer server) {
        JOBS.removeIf(j -> j.level.getServer() != server);
        if (JOBS.isEmpty()) return;
        Job j = JOBS.get(0);
        if (j.step()) JOBS.remove(0);
    }

    private static final class Job {
        final ServerLevel level;
        final BlockPos o;
        final Direction fwd;
        @Nullable final UUID player;
        final boolean world;
        final BattleMap map;
        final RandomSource rnd;
        final long t0 = System.currentTimeMillis();
        int stage, row = -HL;

        Job(ServerLevel level, BlockPos o, Direction fwd, @Nullable UUID player, boolean world) {
            this.level = level;
            this.o = o;
            this.fwd = fwd;
            this.player = player;
            this.world = world;
            this.map = new BattleMap(o, fwd);
            this.rnd = RandomSource.create(o.asLong());
        }

        /** the player who built it, or everybody when the field is the world itself */
        void tell(Component c, boolean bar) {
            if (player == null) {
                for (ServerPlayer p : level.players()) p.displayClientMessage(c, bar);
                return;
            }
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(player);
            if (p != null) p.displayClientMessage(c, bar);
        }

        /** local (x = right, z = towards the red base) -> world */
        BlockPos at(int x, int y, int z) {
            Direction right = fwd.getClockWise();
            return o.offset(right.getStepX() * x + fwd.getStepX() * z, y, right.getStepZ() * x + fwd.getStepZ() * z);
        }

        void set(int x, int y, int z, BlockState s) {
            level.setBlock(at(x, y, z), s, FLAGS);
        }

        /** true when finished */
        boolean step() {
            switch (stage) {
                case 0 -> {
                    for (int i = 0; i < 2 && row < HL; i++, row++) groundRow(row);
                    progress(row + HL, L * 2);
                    if (row >= HL) {
                        stage = 1;
                        row = -HL;
                    }
                }
                case 1 -> {
                    for (int i = 0; i < 4 && row < HL; i++, row++) detailRow(row);
                    progress(L + row + HL, L * 2);
                    if (row >= HL) stage = 2;
                }
                default -> {
                    perimeter();
                    farmhouses();
                    base(-1);
                    base(1);
                    keepLoaded(-1);
                    keepLoaded(1);
                    if (world) OrchardWorld.built(level, map);
                    tell(Component.translatable("gunsmod.msg.orchard_done", (System.currentTimeMillis() - t0) / 1000), false);
                    return true;
                }
            }
            return false;
        }

        void progress(int done, int total) {
            if (level.getGameTime() % 20 != 0) return;
            tell(Component.translatable("gunsmod.msg.orchard_progress", done * 100 / total), true);
        }

        // ------------------------------------------------------------------------------------ terrain
        void groundRow(int z) {
            BlockState air = Blocks.AIR.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
            for (int x = -HW; x < HW; x++) {
                for (int y = 1; y <= CLEAR; y++) {
                    BlockPos p = at(x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, air, FLAGS);
                }
                for (int y = -3; y <= -1; y++) {
                    BlockPos p = at(x, y, z);
                    if (!level.getBlockState(p).isSolid()) level.setBlock(p, dirt, FLAGS);
                }
                set(x, 0, z, grass);
            }
        }

        boolean baseZone(int z) {
            return Math.abs(z) > HL - 80;
        }

        void detailRow(int z) {
            boolean base = baseZone(z);
            for (int x = -HW; x < HW; x++) {
                long h = mix(x, z);
                int r = (int) (h & 1023);
                // dirt tracks (the road network of the map): worn coarse dirt along the edges
                if (BattleMap.isRoad(x, z)) {
                    boolean edge = !BattleMap.isRoad(x + 1, z) || !BattleMap.isRoad(x - 1, z) || !BattleMap.isRoad(x, z + 1) || !BattleMap.isRoad(x, z - 1);
                    set(x, 0, z, edge && (r & 1) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
                    continue;
                }
                if (base || x <= -HW + 2 || x >= HW - 3) continue;
                // orchard rows: a tree on some of the grid spots only, so the field stays open; none close to a road
                if (Math.floorMod(x, 9) == 4 && Math.floorMod(z, 8) == 3 && r < 330 && !BattleMap.nearRoad(x, z, 4)) {
                    tree(x, z, h);
                    continue;
                }
                if (level.getBlockState(at(x, 1, z)).isAir()) {
                    if (r < 250) set(x, 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    else if (r < 285) set(x, 1, z, Blocks.FERN.defaultBlockState());
                    else if (r < 296) set(x, 1, z, flower(h));
                    else if (r < 298 && (h >> 12 & 7) == 0) hay(x, z);
                }
            }
        }

        BlockState flower(long h) {
            return switch ((int) (h >> 20 & 3)) {
                case 0 -> Blocks.DANDELION.defaultBlockState();
                case 1 -> Blocks.POPPY.defaultBlockState();
                case 2 -> Blocks.OXEYE_DAISY.defaultBlockState();
                default -> Blocks.CORNFLOWER.defaultBlockState();
            };
        }

        void hay(int x, int z) {
            set(x, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
            set(x + 1, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
            if (rnd.nextBoolean()) set(x, 2, z, Blocks.HAY_BLOCK.defaultBlockState());
        }

        /** a small fruit tree: short trunk, round crown, some of them in blossom */
        void tree(int x, int z, long h) {
            boolean birch = (h >> 14 & 7) == 0;
            BlockState log = (birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG).defaultBlockState();
            BlockState leaves = leaves(birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES);
            BlockState blossom = leaves(Blocks.FLOWERING_AZALEA_LEAVES);
            boolean blooming = (h >> 17 & 3) == 0;
            int trunk = 3 + (int) (h >> 22 & 1);
            for (int y = 1; y <= trunk; y++) set(x, y, z, log);
            for (int dy = trunk - 1; dy <= trunk + 2; dy++) {
                int rr = dy == trunk + 2 ? 1 : 2;
                for (int dx = -rr; dx <= rr; dx++) {
                    for (int dz = -rr; dz <= rr; dz++) {
                        if (dx * dx + dz * dz > rr * rr + 1) continue;
                        if (dx == 0 && dz == 0 && dy <= trunk) continue;
                        BlockPos p = at(x + dx, dy, z + dz);
                        if (!level.getBlockState(p).isAir() && !level.getBlockState(p).is(Blocks.SHORT_GRASS)) continue;
                        boolean b = blooming && ((dx * 31 + dz * 17 + dy * 7 + (int) h) & 3) == 0;
                        level.setBlock(p, b ? blossom : leaves, FLAGS);
                    }
                }
            }
        }

        BlockState leaves(Block b) {
            return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        }

        long mix(int x, int z) {
            long h = (long) x * 0x9E3779B97F4A7C15L + (long) z * 0xC2B2AE3D27D4EB4FL + o.asLong();
            h ^= h >>> 29;
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 32;
            return h & Long.MAX_VALUE;
        }

        // ------------------------------------------------------------------------------------ structures
        void perimeter() {
            BlockState wall = Blocks.COBBLESTONE_WALL.defaultBlockState(), stone = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            for (int x = -HW; x < HW; x++) {
                for (int z : new int[]{-HL, HL - 1}) {
                    set(x, 1, z, stone);
                    set(x, 2, z, wall);
                }
            }
            for (int z = -HL; z < HL; z++) {
                for (int x : new int[]{-HW, HW - 1}) {
                    set(x, 1, z, stone);
                    set(x, 2, z, wall);
                }
            }
        }

        void farmhouses() {
            int[][] spots = {{-60, -50}, {60, 50}, {90, -136}, {-70, 130}, {-110, 10}, {110, -10}};
            for (int[] s : spots) house(s[0], s[1]);
        }

        /** a ruined stone farmhouse with a half collapsed roof, good cover for soldiers */
        void house(int x0, int z0) {
            BlockState wall = Blocks.COBBLESTONE.defaultBlockState(), mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            BlockState plank = Blocks.OAK_PLANKS.defaultBlockState(), slab = Blocks.OAK_SLAB.defaultBlockState();
            int w = 9, d = 7;
            for (int x = x0 - 1; x <= x0 + w; x++) {
                for (int z = z0 - 1; z <= z0 + d; z++) {
                    for (int y = 1; y <= 7; y++) set(x, y, z, Blocks.AIR.defaultBlockState());
                    set(x, 0, z, Blocks.COARSE_DIRT.defaultBlockState());
                }
            }
            for (int x = x0; x < x0 + w; x++) {
                for (int z = z0; z < z0 + d; z++) {
                    boolean edge = x == x0 || x == x0 + w - 1 || z == z0 || z == z0 + d - 1;
                    set(x, 0, z, plank);
                    if (!edge) continue;
                    int top = 4 - ((x * 5 + z * 3) & 1) - (x > x0 + 5 ? 2 : 0);
                    for (int y = 1; y <= top; y++) {
                        boolean door = y <= 2 && x == x0 + 4 && (z == z0 || z == z0 + d - 1);
                        boolean window = y == 2 && (x == x0 + 2 || x == x0 + 6 || z == z0 + 3) && !door;
                        if (door) continue;
                        set(x, y, z, window ? Blocks.AIR.defaultBlockState() : rnd.nextInt(4) == 0 ? mossy : wall);
                    }
                }
            }
            for (int x = x0; x < x0 + 5; x++) {
                for (int z = z0; z < z0 + d; z++) set(x, 5, z, slab);
            }
            set(x0 + w + 1, 1, z0 + 1, Blocks.HAY_BLOCK.defaultBlockState());
            set(x0 + w + 1, 1, z0 + 2, Blocks.HAY_BLOCK.defaultBlockState());
            set(x0 + w + 1, 2, z0 + 1, Blocks.HAY_BLOCK.defaultBlockState());
        }

        /** one team's base; s = -1 green (near end), +1 red (far end, mirrored) */
        void base(int s) {
            BotTeam team = s < 0 ? BotTeam.GREEN : BotTeam.RED;
            Block concrete = s < 0 ? Blocks.GREEN_CONCRETE : Blocks.RED_CONCRETE;
            Direction toEnemy = s < 0 ? fwd : fwd.getOpposite();
            Direction runwayDir = s < 0 ? fwd.getClockWise() : fwd.getCounterClockWise();
            // yard
            for (int x = -40; x <= 40; x++) {
                for (int d = 4; d <= 50; d++) {
                    boolean edge = Math.abs(x) == 40 || d == 4 || d == 50;
                    put(s, x, 0, d, edge ? concrete.defaultBlockState() : (x + d) % 7 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                            : Blocks.GRAY_CONCRETE.defaultBlockState());
                }
            }
            // helipad marking
            for (int x = 24; x <= 36; x++) {
                for (int d = 8; d <= 20; d++) {
                    boolean ring = Math.abs(Math.hypot(x - 30, d - 14) - 5.5) < 0.6;
                    boolean letter = (x == 28 || x == 32) && d >= 11 && d <= 17 || d == 14 && x > 28 && x < 32;
                    if (ring || letter) put(s, x, 0, d, Blocks.YELLOW_CONCRETE.defaultBlockState());
                }
            }
            // sandbag line towards the enemy with gaps for the vehicles
            for (int x = -46; x <= 46; x++) {
                if (Math.abs(x) <= 6 || Math.abs(Math.abs(x) - 30) <= 4) continue;
                put(s, x, 1, 56, facingFor(Reg.SANDBAGS.get(), toEnemy));
                if (Math.floorMod(x, 5) == 0) put(s, x, 2, 56, facingFor(Reg.SANDBAGS_LOW.get(), toEnemy));
            }
            // flag, ammo crates, lights
            for (int y = 1; y <= 8; y++) put(s, 0, y, 8, Blocks.IRON_BARS.defaultBlockState());
            for (int y = 6; y <= 8; y++) {
                for (int x = 1; x <= 3; x++) put(s, x, y, 8, (s < 0 ? Blocks.GREEN_WOOL : Blocks.RED_WOOL).defaultBlockState());
            }
            put(s, -6, 1, 18, facingFor(Reg.AMMO_CRATE.get(), toEnemy));
            put(s, 6, 1, 18, facingFor(Reg.AMMO_CRATE.get(), toEnemy));
            put(s, -6, 1, 34, facingFor(Reg.AMMO_CRATE.get(), toEnemy));
            for (int[] l : new int[][]{{-40, 4}, {40, 4}, {-40, 50}, {40, 50}, {8, 42}}) lamp(s, l[0], l[1]);
            // soldiers: near the armour, near the helicopter and the air defence, and pilots by the runway
            soldiers(s, -4, 24, team, 6);
            soldiers(s, 14, 14, team, 5);
            soldiers(s, -118, 56, team, 2);
            // vehicles, all facing the enemy; the jet faces down its runway
            VehicleKind tankA = s < 0 ? VehicleKind.ABRAMS : VehicleKind.T90;
            VehicleKind tankB = s < 0 ? VehicleKind.LEOPARD : VehicleKind.CHALLENGER;
            VehicleKind heli = s < 0 ? VehicleKind.APACHE : VehicleKind.KA52;
            vehiclePad(s, -28, 30, tankA, team, toEnemy);
            vehiclePad(s, -16, 30, tankB, team, toEnemy);
            vehiclePad(s, 18, 30, VehicleKind.PANTSIR, team, toEnemy);
            vehiclePad(s, 30, 14, heli, team, toEnemy);
            runway(s);
            vehiclePad(s, -132, 66, VehicleKind.F16, team, runwayDir);
        }

        /** a base point: d = distance from the team's own end of the field, x mirrored for red */
        void put(int s, int x, int y, int d, BlockState st) {
            set(s < 0 ? x : -x, y, s < 0 ? d - HL : HL - 1 - d, st);
        }

        BlockPos posOf(int s, int x, int y, int d) {
            return at(s < 0 ? x : -x, y, s < 0 ? d - HL : HL - 1 - d);
        }

        BlockState facingFor(Block b, Direction d) {
            return b.defaultBlockState().setValue(FacingBlock.FACING, d);
        }

        void lamp(int s, int x, int d) {
            for (int y = 1; y <= 3; y++) put(s, x, y, d, Blocks.DARK_OAK_FENCE.defaultBlockState());
            put(s, x, 4, d, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }

        /** runway along the base: 180 x 9, grey with white centre line and threshold bars, edge lights */
        void runway(int s) {
            for (int x = -142; x <= 40; x++) {
                for (int d = 62; d <= 70; d++) {
                    for (int y = 1; y <= 6; y++) put(s, x, y, d, Blocks.AIR.defaultBlockState());
                    boolean edge = d == 62 || d == 70;
                    boolean centre = d == 66 && Math.floorMod(x, 8) < 4;
                    boolean threshold = (x <= -138 || x >= 36) && (d % 2 == 1) && !edge;
                    BlockState st = edge ? Blocks.SMOOTH_STONE.defaultBlockState() : centre || threshold ? Blocks.WHITE_CONCRETE.defaultBlockState()
                            : Blocks.GRAY_CONCRETE.defaultBlockState();
                    put(s, x, 0, d, st);
                }
                if (Math.floorMod(x, 16) == 0) {
                    put(s, x, 1, 61, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
                    put(s, x, 1, 71, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
                }
            }
        }

        void soldiers(int s, int x, int d, BotTeam team, int count) {
            BlockPos p = posOf(s, x, 1, d);
            level.setBlock(p, Reg.SPAWNER.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(p) instanceof SpawnerBlockEntity be) {
                be.inventory.setItem(0, dogTag(team));
                be.count = count;
                be.delay = 10;
                be.active = true;
                be.battle = map;
                be.setChanged();
                level.sendBlockUpdated(p, level.getBlockState(p), level.getBlockState(p), 3);
            }
        }

        /** a dog tag that deploys soldiers of the team with a random gun and attachments each time */
        ItemStack dogTag(BotTeam team) {
            ItemStack tag = new ItemStack(Reg.DOG_TAG.get());
            CompoundTag d = new CompoundTag();
            d.putInt("team", team.ordinal());
            d.putInt("skin", 0);
            d.putInt("grenades", 2);
            d.putBoolean("random", true);
            d.put("equipment", new CompoundTag());
            CompoundTag root = new CompoundTag();
            root.put("gunsmod_bot", d);
            tag.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
            return tag;
        }

        /**
         * The base stays loaded even with nobody near it, so both sides keep deploying soldiers and vehicles: the yard
         * and the runway. Further out the battle only runs where players are, as everywhere in Minecraft.
         */
        void keepLoaded(int s) {
            forceArea(posOf(s, -46, 0, 0), posOf(s, 46, 0, 60));
            forceArea(posOf(s, -146, 0, 50), posOf(s, 44, 0, 74));
        }

        void forceArea(BlockPos a, BlockPos b) {
            int x0 = Math.min(a.getX(), b.getX()) >> 4, x1 = Math.max(a.getX(), b.getX()) >> 4;
            int z0 = Math.min(a.getZ(), b.getZ()) >> 4, z1 = Math.max(a.getZ(), b.getZ()) >> 4;
            for (int cx = x0; cx <= x1; cx++) {
                for (int cz = z0; cz <= z1; cz++) level.setChunkForced(cx, cz, true);
            }
        }

        void vehiclePad(int s, int x, int d, VehicleKind kind, BotTeam team, Direction face) {
            BlockPos p = posOf(s, x, 1, d);
            // the spawned vehicle looks the opposite way of the pad's facing
            level.setBlock(p, Reg.VEHICLE_SPAWNER.get().defaultBlockState().setValue(FacingBlock.FACING, face.getOpposite()), Block.UPDATE_ALL);
            if (level.getBlockEntity(p) instanceof VehicleSpawnerBlockEntity be) be.setVehicle(new ItemStack(Reg.vehicleItem(kind, team)));
        }
    }
}
