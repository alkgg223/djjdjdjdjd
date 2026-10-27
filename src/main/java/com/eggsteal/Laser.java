package com.eggsteal;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * The red laser door: pressing a base's button seals its entrance for 30 seconds.
 * Everybody except the base's owner is pushed back; the owner (player or bot) walks straight through.
 */
public class Laser {
    public static final int DURATION = 600;      // 30 s
    public static final int COOLDOWN = 1800;     // 90 s after pressing
    static final long[] until = new long[Arena.BASES], cool = new long[Arena.BASES], ended = new long[Arena.BASES];
    static final boolean[] on = new boolean[Arena.BASES];
    static final String[] last = new String[Arena.BASES];

    public static boolean active(int b) { return on[b]; }
    public static long endedAt(int b) { return ended[b]; }
    public static boolean ready(MinecraftServer s, int b) { return !on[b] && s.getTickCount() >= cool[b]; }
    public static int coolSeconds(MinecraftServer s, int b) { return (int) Math.max(0, (cool[b] - s.getTickCount()) / 20); }

    /** Clears any leftover glass from older versions of the mod. */
    static void clearGap(ServerLevel sl, int b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = Arena.cx(b) - 1; x <= Arena.cx(b) + 1; x++)
            for (int y = 64; y <= 67; y++) if (sl.getBlockState(mp.set(x, y, Arena.gapZ(b))).is(Blocks.RED_STAINED_GLASS)) sl.setBlock(mp, air, 3);
    }

    public static void reset(ServerLevel sl, int b) {
        on[b] = false;
        until[b] = 0;
        cool[b] = 0;
        last[b] = null;
        clearGap(sl, b);
    }

    public static boolean press(ServerLevel sl, int b) {
        MinecraftServer s = sl.getServer();
        if (!ready(s, b)) return false;
        long n = s.getTickCount();
        on[b] = true;
        until[b] = n + DURATION;
        cool[b] = n + COOLDOWN;
        sl.playSound(null, Arena.cx(b), 65, Arena.gapZ(b), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2f, 0.8f);
        return true;
    }

    public static void tick(ServerLevel sl) {
        MinecraftServer s = sl.getServer();
        Economy eco = Economy.get(s);
        long n = s.getTickCount();
        for (int b = 0; b < Arena.BASES; b++) {
            int cx = Arena.cx(b), gz = Arena.gapZ(b), d = Arena.dir(b);
            if (on[b]) {
                if (n >= until[b]) {
                    on[b] = false;
                    ended[b] = n;
                    sl.playSound(null, cx, 65, gz, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2f, 1f);
                } else {
                    if (n % 3 == 0) sl.sendParticles(new DustParticleOptions(new Vector3f(1f, 0f, 0f), 1.4f), cx + 0.5, 66, gz + 0.5, 16, 1.4, 1.6, 0.05, 0);
                    UUID owner = eco.baseOwner[b];
                    AABB box = new AABB(cx - 1.4, 63.5, gz - 0.6, cx + 2.4, 69, gz + 1.6);
                    for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, box)) {
                        if (le instanceof CreatureEntity || le.getUUID().equals(owner)) continue;
                        if (le instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator())) continue;
                        double side = (le.getZ() - (gz + 0.5)) * d;
                        double nz = gz + 0.5 + d * (side >= 0 ? 1.5 : -1.5);
                        if (le instanceof ServerPlayer sp) {
                            sp.connection.teleport(le.getX(), le.getY(), nz, sp.getYRot(), sp.getXRot());
                            sp.displayClientMessage(net.minecraft.network.chat.Component.literal("§c⚠ The laser door is closed!"), true);
                        } else {
                            le.setPos(le.getX(), le.getY(), nz);
                            le.setDeltaMovement(0, le.getDeltaMovement().y, 0);
                        }
                    }
                }
            } else if (n % 100 == 0) clearGap(sl, b);
            if (n % 20 == 0) {
                String t = on[b] ? "§c§l⚠ LASER ON §f" + Math.max(0, (until[b] - n) / 20) + "s"
                        : n < cool[b] ? "§7Laser recharging " + ((cool[b] - n) / 20) + "s" : "§aDoor open";
                if (!t.equals(last[b])) {
                    last[b] = t;
                    Hud.setText(s, "es_ls" + b, t);
                }
            }
        }
    }
}
