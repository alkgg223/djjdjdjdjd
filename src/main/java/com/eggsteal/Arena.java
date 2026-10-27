package com.eggsteal;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Arena layout: a hub in the middle and 6 walled bases (3 north, 3 south), each with a single front entrance. */
public class Arena {
    public static final int BASES = 6;
    static final Block[] COLORS = {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.GREEN_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.ORANGE_CONCRETE};
    public static final int WALL_TOP = 72;

    public static int cx(int i) { return (i % 3 - 1) * 50; }
    public static int cz(int i) { return i < 3 ? -32 : 32; }
    /** +1 when the hub is toward +z, -1 when toward -z. */
    public static int dir(int i) { return i < 3 ? 1 : -1; }
    public static int beltZ(int i) { return cz(i) + 9 * dir(i); }
    public static int gapZ(int i) { return cz(i) + 12 * dir(i); }
    public static Vec3 home(int i) { return new Vec3(cx(i) + 0.5, 64, cz(i) + 0.5); }
    public static BlockPos buttonPos(int i) { return new BlockPos(cx(i) + 4, 64, cz(i) + 11 * dir(i)); }
    public static Vec3 buttonStand(int i) { return new Vec3(cx(i) + 4.5, 64, cz(i) + 10 * dir(i) + 0.5); }

    /** idx 0..7 = ground slots, 8..11 = second-floor slots. */
    public static Vec3 slot(int base, int idx) {
        int j = idx >= 8 ? idx - 8 : idx;
        int col = j % 4, row = j / 4;
        return new Vec3(cx(base) - 6 + col * 4 + 0.5, idx >= 8 ? 68 : 64, cz(base) - dir(base) * (6 - row * 4) + 0.5);
    }

    public static int available(Economy.Stats s) { return Math.min(8, 3 + s.slots) + (s.floor2 ? 4 : 0); }

    public static int owned(ServerLevel sl, UUID owner) {
        return sl.getEntities(EntityTypeTest.forClass(CreatureEntity.class), c -> owner.equals(c.owner())).size();
    }

    static boolean free(ServerLevel sl, int base, int idx) {
        Vec3 p = slot(base, idx);
        AABB box = new AABB(p.x - 0.7, p.y - 0.5, p.z - 0.7, p.x + 0.7, p.y + 1.5, p.z + 0.7);
        return sl.getEntitiesOfClass(CreatureEntity.class, box).isEmpty();
    }

    public static int freeSlot(ServerLevel sl, int base, Economy.Stats st) {
        int g = Math.min(8, 3 + st.slots);
        for (int i = 0; i < g; i++) if (free(sl, base, i)) return i;
        if (st.floor2) for (int i = 8; i < 12; i++) if (free(sl, base, i)) return i;
        return -1;
    }

    public static void spawnCreature(ServerLevel sl, UUID owner, int variant, int mut, int base, int idx, int hatch) {
        Vec3 p = slot(base, idx);
        CreatureEntity ce = EggSteal.CREATURE.get().create(sl);
        if (ce == null) return;
        ce.moveTo(p.x, p.y, p.z, dir(base) > 0 ? 0f : 180f, 0f);
        ce.setVariant(variant);
        ce.setMutation(mut);
        ce.setOwner(owner);
        if (hatch > 0) ce.startHatch(hatch);
        sl.addFreshEntity(ce);
    }

    public static void spawnCreature(ServerLevel sl, UUID owner, int variant, int mut, int base, int idx) {
        spawnCreature(sl, owner, variant, mut, base, idx, CreatureEntity.HATCH_TIME);
    }

    /** Places a creature in the first free slot. Returns false when the base is full. */
    public static boolean place(ServerLevel sl, UUID owner, int variant, int mut, Economy.Stats st, int hatch) {
        if (st.base < 0) return false;
        int idx = freeSlot(sl, st.base, st);
        if (idx < 0) return false;
        spawnCreature(sl, owner, variant, mut, st.base, idx, hatch);
        return true;
    }

    public static boolean place(ServerLevel sl, UUID owner, int variant, Economy.Stats st, int hatch) {
        return place(sl, owner, variant, 0, st, hatch);
    }

    /** The base whose walls contain this point, or -1 when it is outside every base. */
    public static int baseAt(double x, double z) {
        for (int i = 0; i < BASES; i++) if (Math.abs(x - (cx(i) + 0.5)) < 12 && Math.abs(z - (cz(i) + 0.5)) < 12) return i;
        return -1;
    }

    /** A point just outside a base's entrance, used by bots to walk in and out through the door instead of into walls. */
    public static Vec3 gateOutside(int i) { return new Vec3(cx(i) + 0.5, 64, gapZ(i) + dir(i) * 2 + 0.5); }

    public static void rules(MinecraftServer s) {
        s.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, s);
        s.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, s);
        s.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, s);
        s.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, s);
        s.overworld().setDayTime(6000);
    }

    /** Builds (or removes) the second floor of a base: a platform over the back half plus a staircase. */
    public static void setFloor2(ServerLevel sl, int i, boolean on) {
        int cx = cx(i), cz = cz(i), d = dir(i);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState plat = Blocks.SMOOTH_STONE.defaultBlockState();
        for (int x = cx - 11; x <= cx + 11; x++)
            for (int t = -11; t <= 0; t++) sl.setBlock(mp.set(x, 67, cz + d * t), on ? plat : air, 3);
        for (int j = 0; j < 4; j++) {
            Vec3 p = slot(i, 8 + j);
            int px = (int) Math.floor(p.x), pz = (int) Math.floor(p.z);
            for (int x = px - 1; x <= px + 1; x++)
                for (int z = pz - 1; z <= pz + 1; z++)
                    sl.setBlock(mp.set(x, 67, z), on ? (x == px && z == pz ? Blocks.GOLD_BLOCK : Blocks.WHITE_CONCRETE).defaultBlockState() : air, 3);
        }
        Direction face = d > 0 ? Direction.NORTH : Direction.SOUTH;
        for (int xo = 9; xo <= 10; xo++)
            for (int n = 1; n <= 4; n++) {
                int z = cz + d * (5 - n), y = 63 + n;
                for (int fy = 64; fy < y; fy++) sl.setBlock(mp.set(cx + xo, fy, z), on ? plat : air, 3);
                sl.setBlock(mp.set(cx + xo, y, z), on ? Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, face) : air, 3);
            }
    }

    /** (Re)builds the wall, trim and decorations of a base in the given skin. */
    public static void paintBase(ServerLevel sl, int i, int skinId) {
        Skin sk = Skin.ALL[Math.max(0, Math.min(skinId, Skin.ALL.length - 1))];
        boolean classic = sk.light == null;
        int cx = cx(i), cz = cz(i), gz = gapZ(i);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = cx - 12; x <= cx + 12; x++)
            for (int z = cz - 12; z <= cz + 12; z++) {
                boolean edge = Math.abs(x - cx) == 12 || Math.abs(z - cz) == 12;
                if (!edge) continue;
                for (int y = 73; y <= 82; y++) sl.setBlock(mp.set(x, y, z), air, 2);
                sl.setBlock(mp.set(x, 63, z), (classic ? COLORS[i] : sk.trim).defaultBlockState(), 2);
                for (int y = 64; y <= WALL_TOP; y++) {
                    BlockState st;
                    if (z == gz && Math.abs(x - cx) <= 1 && y <= 67) st = air;
                    else if (y == WALL_TOP) st = (classic ? COLORS[i] : sk.trim).defaultBlockState();
                    else if (!classic && y == 69 && (sk.style == 3 || (x + z) % 4 == 0)) st = sk.light.defaultBlockState();
                    else st = sk.wallAt(x, y, z).defaultBlockState();
                    sl.setBlock(mp.set(x, y, z), st, 2);
                }
                if (classic) continue;
                boolean corner = Math.abs(x - cx) == 12 && Math.abs(z - cz) == 12;
                int style = sk.style;
                if (style == 1 || style == 4) { if ((x + z) % 2 == 0) sl.setBlock(mp.set(x, 73, z), sk.trim.defaultBlockState(), 2); }
                if (corner) {
                    int h = style == 2 ? 8 : 5;
                    for (int y = 73; y < 73 + h; y++) sl.setBlock(mp.set(x, y, z), (style == 2 ? sk.wallAt(x, y, z) : sk.trim).defaultBlockState(), 2);
                    sl.setBlock(mp.set(x, 73 + h, z), sk.light.defaultBlockState(), 2);
                } else if (style == 2 && (x - cx) % 6 == 0 && (z - cz) % 6 == 0) {
                    sl.setBlock(mp.set(x, 73, z), sk.light.defaultBlockState(), 2);
                    sl.setBlock(mp.set(x, 74, z), sk.trim.defaultBlockState(), 2);
                }
            }
        // lanterns on both sides of the entrance
        int d = dir(i);
        for (int sx : new int[]{-3, 3}) {
            Block lamp = classic ? Blocks.SEA_LANTERN : sk.light;
            sl.setBlock(mp.set(cx + sx, 64, gz + d * 2), (classic ? Blocks.STONE_BRICKS : sk.trim).defaultBlockState(), 2);
            sl.setBlock(mp.set(cx + sx, 65, gz + d * 2), lamp.defaultBlockState(), 2);
        }
    }

    public static void build(ServerLevel sl) {
        MinecraftServer server = sl.getServer();
        Economy eco = Economy.get(server);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = -70; x <= 70; x++)
            for (int z = -50; z <= 50; z++) sl.setBlock(mp.set(x, 63, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++) if (x * x + z * z <= 81) sl.setBlock(mp.set(x, 63, z), (x * x + z * z <= 16 ? Blocks.GOLD_BLOCK : Blocks.SMOOTH_QUARTZ).defaultBlockState(), 2);
        for (int z = -50; z <= 50; z++) for (int x = -1; x <= 1; x++) if (x * x + z * z > 81) sl.setBlock(mp.set(x, 63, z), Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
        // the center belt for very rare eggs
        for (int x = -8; x <= 8; x++)
            for (int z = -1; z <= 1; z++)
                sl.setBlock(mp.set(x, 63, z), (z == 0 && x % 2 == 0 ? Blocks.MAGENTA_CONCRETE : Blocks.BLACK_CONCRETE).defaultBlockState(), 2);
        for (int i = 0; i < BASES; i++) {
            int cx = cx(i), cz = cz(i);
            for (int x = cx - 12; x <= cx + 12; x++)
                for (int z = cz - 12; z <= cz + 12; z++) {
                    boolean edge = Math.abs(x - cx) == 12 || Math.abs(z - cz) == 12;
                    sl.setBlock(mp.set(x, 63, z), (edge ? COLORS[i] : Blocks.POLISHED_ANDESITE).defaultBlockState(), 2);
                    if (!edge) for (int y = 64; y <= 72; y++) sl.setBlock(mp.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            for (int s = 0; s < 8; s++) {
                Vec3 p = slot(i, s);
                int px = (int) Math.floor(p.x), pz = (int) Math.floor(p.z);
                for (int x = px - 1; x <= px + 1; x++)
                    for (int z = pz - 1; z <= pz + 1; z++)
                        sl.setBlock(mp.set(x, 63, z), (x == px && z == pz ? Blocks.GOLD_BLOCK : Blocks.WHITE_CONCRETE).defaultBlockState(), 2);
            }
            int bz = beltZ(i);
            for (int x = cx - 10; x <= cx + 10; x++)
                for (int z = bz - 1; z <= bz + 1; z++)
                    sl.setBlock(mp.set(x, 63, z), (z == bz && x % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE).defaultBlockState(), 2);
            sl.setBlock(buttonPos(i), Blocks.REDSTONE_BLOCK.defaultBlockState(), 2);
            Laser.reset(sl, i);
            Economy.Stats owner = eco.baseOwner[i] == null ? null : eco.map.get(eco.baseOwner[i]);
            paintBase(sl, i, owner == null ? 0 : owner.skin);
            setFloor2(sl, i, owner != null && owner.floor2);
        }
        sl.setBlock(mp.set(0, 64, 6), EggSteal.STATION.get().defaultBlockState(), 2);
        sl.setBlock(mp.set(0, 64, -6), EggSteal.BUILDER.get().defaultBlockState(), 2);
        for (int cxk = -5; cxk <= 4; cxk++) for (int czk = -4; czk <= 3; czk++) sl.setChunkForced(cxk, czk, true);
        sl.getWorldBorder().setCenter(0, 0);
        sl.getWorldBorder().setSize(150);
        sl.setDefaultSpawnPos(new BlockPos(0, 64, 12), 0f);
        rules(server);
        Hud.cmd(server, "kill @e[type=minecraft:text_display,tag=eggsteal]");
        Hud.summon(server, "es_title", 0, 72, 0, 4f, "§6§lEGG STEAL\n§7Buy eggs, hatch them, steal from others!");
        Hud.summon(server, "es_lb1", -14, 68, 0, 1.6f, "Loading...");
        Hud.summon(server, "es_lb2", 14, 68, 0, 1.6f, "Loading...");
        Hud.summon(server, "es_shoplbl", 0.5, 67, 6.5, 1.5f, "§a§lSHOP\n§7Upgrades, items & skins");
        Hud.summon(server, "es_buildlbl", 0.5, 67, -5.5, 1.5f, "§b§lBASE BUILDER\n§7Build a 2nd floor");
        Hud.summon(server, "es_cbelt", 0.5, 66.5, 1.8, 1.8f, "§d§lRARE EGG BELT\n§7Pack creatures only appear here!");
        Hud.ensureBoards(server);
        for (int i = 0; i < BASES; i++) {
            Hud.summon(server, "es_b" + i, cx(i), 76, cz(i), 2f, "Free base");
            Hud.summon(server, "es_ls" + i, cx(i) + 0.5, 70, gapZ(i) + 0.6 * dir(i), 1.5f, "§aDoor open");
            Hud.summon(server, "es_bt" + i, buttonPos(i).getX() + 0.5, 66, buttonPos(i).getZ() + 0.5, 1.2f, "§c§lLASER BUTTON");
        }
        eco.arenaBuilt = true;
        eco.setDirty();
    }
}
