package com.eggsteal.brainrot;

import com.eggsteal.EggSteal;
import com.eggsteal.Hud;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The Brainrot Heist map: a long red carpet (x from -96 to 96) where brainrots walk out of a portal,
 * with 4 bases on each side facing it. Each base has 10 podiums (20 after the first rebirth), a gold collect pad
 * in front of every podium, and a lock button that closes the entrance with a laser for 60 seconds.
 */
public class BrMap {
    public static final int BASES = 8, PODIUMS = 20, ROAD_START = -94, ROAD_END = 95;
    static final Block[] COLORS = {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.LIME_CONCRETE, Blocks.YELLOW_CONCRETE,
            Blocks.PURPLE_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.PINK_CONCRETE};
    static final Block[] GLASS = {Blocks.RED_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS, Blocks.LIME_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS,
            Blocks.PURPLE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS, Blocks.PINK_STAINED_GLASS};
    public static final String[] COLOR_CODES = {"§c", "§9", "§a", "§e", "§5", "§6", "§3", "§d"};

    public static int cx(int i) { return -66 + (i % 4) * 44; }
    public static int cz(int i) { return i < 4 ? -21 : 21; }
    /** +1 when the carpet is toward +z (north bases), -1 for south bases. */
    public static int dir(int i) { return i < 4 ? 1 : -1; }
    /** z of the wall with the entrance (the side facing the carpet). */
    public static int nearZ(int i) { return cz(i) + 13 * dir(i); }
    public static Vec3 home(int i) { return new Vec3(cx(i) + 0.5, 64, cz(i) + 0.5); }
    public static Vec3 gateOutside(int i) { return new Vec3(cx(i) + 0.5, 64, nearZ(i) + dir(i) * 2 + 0.5); }
    public static BlockPos lockButton(int i) { return new BlockPos(cx(i) + 4, 64, cz(i) + 11 * dir(i)); }
    public static Vec3 lockStand(int i) { return new Vec3(cx(i) + 4.5, 64, cz(i) + 10 * dir(i) + 0.5); }

    public static int capacity(BrData.Stats s) { return s != null && s.rebirths >= 1 ? 20 : 10; }

    /** Podium centre (where the brainrot stands). 0-4 / 5-9: inner columns, 10-19: outer columns (after a rebirth). */
    public static Vec3 podium(int base, int idx) {
        int col = idx / 5, k = idx % 5;
        int x = cx(base) + (col == 0 ? -6 : col == 1 ? 6 : col == 2 ? -12 : 12);
        int z = cz(base) - 8 + k * 4;
        return new Vec3(x + 0.5, 65, z + 0.5);
    }

    /** The gold pad in front of a podium: stand on it to collect the money of that brainrot. */
    public static Vec3 pad(int base, int idx) {
        Vec3 p = podium(base, idx);
        int col = idx / 5;
        double dx = col == 0 ? 2 : col == 1 ? -2 : col == 2 ? 2 : -2;
        return new Vec3(p.x + dx, 64, p.z);
    }

    /** The base whose walls contain this point, or -1. */
    public static int baseAt(double x, double z) {
        for (int i = 0; i < BASES; i++) if (Math.abs(x - (cx(i) + 0.5)) < 16 && Math.abs(z - (cz(i) + 0.5)) < 13) return i;
        return -1;
    }

    public static boolean onRoad(double x, double z) { return Math.abs(z - 0.5) < 3 && x > ROAD_START - 4 && x < ROAD_END + 4; }

    public static boolean inMap(double x, double z) { return Math.abs(x) <= 108 && Math.abs(z) <= 44; }

    // ------------------------------------------------------------------ building

    public static void rules(MinecraftServer s) {
        s.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, s);
        s.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, s);
        s.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, s);
        s.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, s);
        s.overworld().setDayTime(6000);
    }

    public static void build(ServerLevel sl) {
        MinecraftServer server = sl.getServer();
        BrData data = BrData.get(server);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int x = -106; x <= 106; x++)
            for (int z = -42; z <= 42; z++) {
                sl.setBlock(mp.set(x, 62, z), Blocks.DIRT.defaultBlockState(), 2);
                sl.setBlock(mp.set(x, 63, z), grass, 2);
                if (((x * 7 + z * 13) & 31) == 0 && Math.abs(z) > 8 && baseAt(x, z) < 0)
                    sl.setBlock(mp.set(x, 64, z), (((x + z) & 1) == 0 ? Blocks.POPPY : Blocks.DANDELION).defaultBlockState(), 2);
            }
        // the red carpet with gold edges, and sidewalks
        for (int x = ROAD_START - 4; x <= ROAD_END + 4; x++)
            for (int z = -7; z <= 7; z++) {
                BlockState st = Math.abs(z) <= 1 ? Blocks.RED_WOOL.defaultBlockState() : Math.abs(z) == 2 ? Blocks.YELLOW_CONCRETE.defaultBlockState()
                        : (x + z) % 3 == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
                sl.setBlock(mp.set(x, 63, z), st, 2);
            }
        for (int x = ROAD_START; x <= ROAD_END; x += 12)
            for (int z : new int[]{-6, 6}) {
                for (int y = 64; y <= 66; y++) sl.setBlock(mp.set(x, y, z), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2);
                sl.setBlock(mp.set(x, 67, z), Blocks.SEA_LANTERN.defaultBlockState(), 2);
            }
        arch(sl, ROAD_START - 2, Blocks.CRYING_OBSIDIAN, Blocks.PURPLE_STAINED_GLASS);
        arch(sl, ROAD_END + 2, Blocks.PRISMARINE_BRICKS, Blocks.LIGHT_BLUE_STAINED_GLASS);
        for (int i = 0; i < BASES; i++) {
            UUID o = data.baseOwner[i];
            buildBase(sl, i, o == null ? null : data.map.get(o));
            Lock.reset(i);
        }
        // shop (north middle) and guide (south middle)
        sl.setBlock(mp.set(0, 64, -14), EggSteal.BR_SHOP.get().defaultBlockState(), 2);
        sl.setBlock(mp.set(0, 64, 14), EggSteal.BR_GUIDE.get().defaultBlockState(), 2);
        for (int x = -2; x <= 2; x++)
            for (int z = 11; z <= 17; z++) sl.setBlock(mp.set(x, 63, z), (z == 14 && x == 0 ? Blocks.GOLD_BLOCK : Blocks.QUARTZ_BRICKS).defaultBlockState(), 2);
        for (int x = -2; x <= 2; x++)
            for (int z = -17; z <= -11; z++) sl.setBlock(mp.set(x, 63, z), (z == -14 && x == 0 ? Blocks.EMERALD_BLOCK : Blocks.QUARTZ_BRICKS).defaultBlockState(), 2);
        for (int cxk = -7; cxk <= 6; cxk++) for (int czk = -3; czk <= 2; czk++) sl.setChunkForced(cxk, czk, true);
        sl.getWorldBorder().setCenter(0, 0);
        sl.getWorldBorder().setSize(214);
        sl.setDefaultSpawnPos(new BlockPos(0, 64, 9), 0f);
        rules(server);
        Hud.cmd(server, "kill @e[type=minecraft:text_display,tag=eggsteal]");
        Hud.summon(server, "br_title", 0.5, 74, 0.5, 4f, "§e§lBRAINROT HEIST\n§fBuy brainrots on the carpet, steal from other bases!");
        Hud.summon(server, "br_shoplbl", 0.5, 66.8, -13.5, 1.4f, "§a§lSHOP\n§7Speed, items & rebirth");
        Hud.summon(server, "br_guidelbl", 0.5, 66.8, 14.5, 1.4f, "§b§lHOW TO PLAY\n§7Brainrots, mutations & chances");
        Hud.summon(server, "br_lb1", -43.5, 69, -14, 1.5f, "Loading...");
        Hud.summon(server, "br_lb2", 44.5, 69, -14, 1.5f, "Loading...");
        Hud.summon(server, "br_lb3", -43.5, 69, 15, 1.5f, "Loading...");
        Hud.summon(server, "br_lb4", 44.5, 69, 15, 1.5f, "Loading...");
        Hud.summon(server, "br_timer", ROAD_START - 1.5, 75, 0.5, 2.4f, "§dRare brainrot soon");
        Hud.summon(server, "es_event", 0.5, 79, 0.5, 2.2f, "");
        for (int i = 0; i < BASES; i++) {
            Hud.summon(server, "br_b" + i, cx(i) + 0.5, 70, nearZ(i) + 0.5, 1.8f, "Free base");
            Hud.summon(server, "br_lk" + i, lockButton(i).getX() + 0.5, 66, lockButton(i).getZ() + 0.5, 1.1f, "§c§lLOCK BASE");
        }
        data.built = true;
        data.setDirty();
    }

    static void arch(ServerLevel sl, int x, Block frame, Block glass) {
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int z = -5; z <= 5; z++)
            for (int y = 64; y <= 73; y++) {
                boolean edge = Math.abs(z) == 5 || y == 73;
                boolean inner = Math.abs(z) <= 4 && y <= 72;
                if (edge) sl.setBlock(mp.set(x, y, z), frame.defaultBlockState(), 2);
                else if (inner && (Math.abs(z) >= 3 || y >= 70)) sl.setBlock(mp.set(x, y, z), glass.defaultBlockState(), 2);
            }
        for (int z : new int[]{-5, 5}) sl.setBlock(mp.set(x, 74, z), Blocks.SEA_LANTERN.defaultBlockState(), 2);
    }

    /** Walls, floor, entrance, podiums and pads of one base (the outer podiums only after a rebirth). */
    public static void buildBase(ServerLevel sl, int i, BrData.Stats owner) {
        int cx = cx(i), cz = cz(i), d = dir(i), nz = nearZ(i);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = cx - 16; x <= cx + 16; x++)
            for (int z = cz - 13; z <= cz + 13; z++) {
                boolean edge = Math.abs(x - cx) == 16 || Math.abs(z - cz) == 13;
                boolean door = z == nz && Math.abs(x - cx) <= 2;
                sl.setBlock(mp.set(x, 63, z), (edge ? COLORS[i] : ((x + z) & 1) == 0 ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState(), 2);
                for (int y = 64; y <= 70; y++) {
                    BlockState st = air;
                    if (edge && !(door && y <= 67)) {
                        if (y == 64 || y == 67) st = COLORS[i].defaultBlockState();
                        else if (y <= 66) st = GLASS[i].defaultBlockState();
                        else if (y == 68 && ((x + z) & 3) == 0) st = Blocks.SEA_LANTERN.defaultBlockState();
                    }
                    sl.setBlock(mp.set(x, y, z), st, 2);
                }
            }
        // the walkway from the door to the back
        for (int z = cz - 12; z <= cz + 12; z++)
            for (int x = cx - 1; x <= cx + 1; x++) sl.setBlock(mp.set(x, 63, z), COLORS[i].defaultBlockState(), 2);
        sl.setBlock(lockButton(i), Blocks.REDSTONE_BLOCK.defaultBlockState(), 2);
        setPodiums(sl, i, capacity(owner));
    }

    /** Builds podiums 0..count-1 and removes the others. */
    public static void setPodiums(ServerLevel sl, int i, int count) {
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int idx = 0; idx < PODIUMS; idx++) {
            boolean on = idx < count;
            Vec3 p = podium(i, idx), pd = pad(i, idx);
            int px = (int) Math.floor(p.x), pz = (int) Math.floor(p.z);
            for (int x = px - 1; x <= px + 1; x++)
                for (int z = pz - 1; z <= pz + 1; z++)
                    sl.setBlock(mp.set(x, 64, z), on ? (x == px && z == pz ? COLORS[i] : Blocks.SMOOTH_QUARTZ).defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            int dx = (int) Math.floor(pd.x), dz = (int) Math.floor(pd.z);
            sl.setBlock(mp.set(dx, 63, dz), on ? Blocks.GOLD_BLOCK.defaultBlockState() : (((dx + dz) & 1) == 0 ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState(), 2);
        }
    }

    // ------------------------------------------------------------------ the lock (laser door)

    /** Pressing the lock button closes a base's entrance for 60 s; only its owner can pass. It recharges 30 s after it opens. */
    public static class Lock {
        public static final int DURATION = 1200, RECHARGE = 600;
        static final long[] until = new long[BASES], cool = new long[BASES];
        static final boolean[] on = new boolean[BASES];
        static final String[] last = new String[BASES];

        public static void reset(int b) { on[b] = false; until[b] = 0; cool[b] = 0; last[b] = null; }
        public static void resetAll() { for (int b = 0; b < BASES; b++) reset(b); }
        public static boolean active(int b) { return b >= 0 && on[b]; }
        public static boolean ready(MinecraftServer s, int b) { return !on[b] && s.getTickCount() >= cool[b]; }
        public static int seconds(MinecraftServer s, int b) { return (int) Math.max(0, ((on[b] ? until[b] : cool[b]) - s.getTickCount()) / 20); }

        public static boolean press(ServerLevel sl, int b) {
            MinecraftServer s = sl.getServer();
            if (!ready(s, b)) return false;
            long n = s.getTickCount();
            on[b] = true;
            until[b] = n + DURATION;
            cool[b] = until[b] + RECHARGE;
            sl.playSound(null, cx(b), 65, nearZ(b), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2f, 0.8f);
            return true;
        }

        public static void tick(ServerLevel sl, BrData data) {
            MinecraftServer s = sl.getServer();
            long n = s.getTickCount();
            for (int b = 0; b < BASES; b++) {
                int cx = cx(b), gz = nearZ(b), d = dir(b);
                if (on[b]) {
                    if (n >= until[b]) {
                        on[b] = false;
                        sl.playSound(null, cx, 65, gz, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2f, 1f);
                    } else {
                        if (n % 3 == 0) sl.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.1f, 0.1f), 1.5f), cx + 0.5, 65.5, gz + 0.5, 20, 2.2, 1.4, 0.05, 0);
                        UUID owner = data.baseOwner[b];
                        AABB box = new AABB(cx - 2.4, 63.5, gz - 0.6, cx + 3.4, 69, gz + 1.6);
                        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, box)) {
                            if (le.getUUID().equals(owner)) continue;
                            if (le instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator())) continue;
                            double side = (le.getZ() - (gz + 0.5)) * d;
                            double nz = gz + 0.5 + d * (side >= 0 ? 1.6 : -1.6);
                            if (le instanceof ServerPlayer sp) {
                                sp.connection.teleport(le.getX(), le.getY(), nz, sp.getYRot(), sp.getXRot());
                                sp.displayClientMessage(net.minecraft.network.chat.Component.literal("§c⚠ This base is locked!"), true);
                            } else {
                                le.setPos(le.getX(), le.getY(), nz);
                                le.setDeltaMovement(0, le.getDeltaMovement().y, 0);
                            }
                        }
                    }
                }
                if (n % 20 == 0) {
                    String t = on[b] ? "§c§l⛔ LOCKED §f" + seconds(s, b) + "s" : n < cool[b] ? "§7Lock recharging " + seconds(s, b) + "s" : "§a§lLOCK BASE §7(click)";
                    if (!t.equals(last[b])) { last[b] = t; Hud.setText(s, "br_lk" + b, t); }
                }
            }
        }
    }
}
