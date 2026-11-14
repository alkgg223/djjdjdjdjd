package com.realweather.server;

import com.realweather.device.EclipseGlasses;
import com.realweather.net.EclipseSyncPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Solar eclipses: now and then (on their own, a few times a year of game days) or by command, the moon passes in front
 * of the sun, partly or totally. Staring at the sun while it isn't completely covered hurts the eyes of anyone without
 * eclipse glasses (survival only).
 */
public final class EclipseManager {
    /** Length of a whole eclipse, first to last contact: five minutes. */
    public static final int DURATION = 6000;
    public static Eclipse current;
    private static final java.util.Map<java.util.UUID, Integer> STARE = new java.util.HashMap<>();

    private EclipseManager() {}

    public static void reset() {
        current = null;
        STARE.clear();
    }

    static void tick(ServerLevel level, WeatherData d, List<ServerPlayer> players, RandomSource rng) {
        long t = level.getGameTime();
        long day = level.getDayTime() % 24000L;
        // Natural eclipses: on about one day in forty, some time while the sun is high.
        if (current == null && d.natural && d.eclipseNatural && day == 1000 && rng.nextInt(40) == 0) {
            int delay = 2000 + rng.nextInt(4000);
            float u = rng.nextFloat();
            // Total, partial, annular (a ring of fire) or, rarely, hybrid.
            if (u < 0.22F) {
                start(level, t + delay, Eclipse.ANNULAR, rng);
            } else if (u < 0.3F) {
                start(level, t + delay, Eclipse.HYBRID, rng);
            } else {
                start(level, t + delay, u < 0.65F, rng);
            }
        }
        if (current == null) {
            return;
        }
        if (t > current.start + current.duration) {
            current = null;
            STARE.clear();
            sync(level);
            return;
        }
        if (!current.active(t) || t % 5 != 0) {
            return;
        }
        float cover = current.coverage(t);
        if (cover < 0.02F || cover >= 0.9995F) {
            return;
        }
        // Looking at the partly covered sun without glasses.
        Vec3 sun = sunDir(level);
        if (sun.y < 0.05) {
            return;
        }
        for (ServerPlayer p : players) {
            if (p.isCreative() || p.isSpectator() || EclipseGlasses.wearing(p) || !level.canSeeSky(p.blockPosition().above())
                    || level.isRainingAt(p.blockPosition().above()) || p.hasEffect(MobEffects.BLINDNESS)) {
                STARE.remove(p.getUUID());
                continue;
            }
            double dot = p.getLookAngle().dot(sun);
            if (dot > Math.cos(Math.toRadians(10))) {
                int s = STARE.merge(p.getUUID(), 1, Integer::sum);
                if (s >= 8) {
                    p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 140, 0, false, false, true));
                    p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, true));
                    p.displayClientMessage(Component.translatable("realweather.eclipse.eyes"), true);
                    STARE.remove(p.getUUID());
                }
            } else {
                STARE.computeIfPresent(p.getUUID(), (k, v) -> v > 1 ? v - 1 : null);
            }
        }
    }

    /** Direction of the sun (as the sky draws it). */
    public static Vec3 sunDir(ServerLevel level) {
        float a = level.getTimeOfDay(1F) * Mth.TWO_PI;
        return new Vec3(-Mth.sin(a), Mth.cos(a), 0);
    }

    public static boolean sunUp(ServerLevel level) {
        return sunDir(level).y > 0.12;
    }

    /** Starts an eclipse at game time `at`: total (the moon right over the sun) or partial (passing to one side). */
    public static void start(ServerLevel level, long at, boolean total, RandomSource rng) {
        float miss = total ? rng.nextFloat() * 0.06F : 0.55F + rng.nextFloat() * 0.9F;
        current = new Eclipse(at, DURATION, miss, rng.nextFloat() * Mth.TWO_PI);
        STARE.clear();
        sync(level);
    }

    /** An annular or hybrid eclipse: the moon passes nearly right over the sun's centre. */
    public static void start(ServerLevel level, long at, int kind, RandomSource rng) {
        current = new Eclipse(at, DURATION, rng.nextFloat() * 0.03F, rng.nextFloat() * Mth.TWO_PI, kind);
        STARE.clear();
        sync(level);
    }

    public static void stop(ServerLevel level) {
        current = null;
        STARE.clear();
        sync(level);
    }

    public static void sync(ServerLevel level) {
        PacketDistributor.sendToPlayersInDimension(level, payload());
    }

    public static EclipseSyncPayload payload() {
        Eclipse e = current;
        return e == null ? new EclipseSyncPayload(false, 0L, 0, 0F, 0F, 0)
                : new EclipseSyncPayload(true, e.start, e.duration, e.miss, e.angle, e.kind);
    }
}
