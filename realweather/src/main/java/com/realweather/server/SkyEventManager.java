package com.realweather.server;

import com.realweather.net.SkyEventsPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The sky events going on (or coming tonight). On their own, each evening may bring one or two (a satellite pass, a
 * meteor shower, the aurora...; rarer ones like a comet or a supernova only now and then), and each morning one at the
 * sun (a transit, a coronal mass ejection); or they are started by command.
 */
public final class SkyEventManager {
    public static final List<SkyEvent> EVENTS = new ArrayList<>();

    private SkyEventManager() {}

    public static void reset() {
        EVENTS.clear();
    }

    /** Whether it is dark enough for night-sky events. */
    public static boolean night(ServerLevel level) {
        long day = level.getDayTime() % 24000L;
        return day >= 13000L && day <= 23000L;
    }

    static void tick(ServerLevel level, WeatherData d, List<ServerPlayer> players, RandomSource rng) {
        long t = level.getGameTime();
        if (EVENTS.removeIf(e -> t > e.start + e.duration)) {
            sync(level);
        }
        if (!d.natural || !d.skyNatural) {
            return;
        }
        long day = level.getDayTime() % 24000L;
        if (day == 12600L) {
            // This evening's sky.
            for (int type = 0; type < SkyEvent.NAMES.length; type++) {
                int chance = SkyEvent.CHANCE[type];
                if (SkyEvent.WHEN[type] == 1 || chance <= 0 || rng.nextInt(chance) != 0 || has(type)) {
                    continue;
                }
                // Short ones (passes, flares, a falling piece of a rocket) come at some time in the night.
                long at = t + 400 + (SkyEvent.DURATION[type] < 4000 ? rng.nextInt(9000) : rng.nextInt(600));
                add(type, at, rng);
            }
            if (level.isThundering() && rng.nextInt(3) == 0) {
                add(SkyEvent.UPPER_LIGHTNING, t + 600 + rng.nextInt(3000), rng);
            }
            sync(level);
        } else if (day == 1200L) {
            for (int type = 0; type < SkyEvent.NAMES.length; type++) {
                int chance = SkyEvent.CHANCE[type];
                if (SkyEvent.WHEN[type] != 1 || chance <= 0 || rng.nextInt(chance) != 0 || has(type)) {
                    continue;
                }
                add(type, t + 600 + rng.nextInt(5000), rng);
            }
            sync(level);
        }
    }

    public static boolean has(int type) {
        for (SkyEvent e : EVENTS) {
            if (e.type == type) {
                return true;
            }
        }
        return false;
    }

    private static void add(int type, long at, RandomSource rng) {
        EVENTS.removeIf(e -> e.type == type);
        EVENTS.add(new SkyEvent(type, at, SkyEvent.DURATION[type], rng.nextLong()));
    }

    /**
     * Starts an event: now, or (for a night-sky event asked for in the daytime) at nightfall. Returns the delay in
     * ticks.
     */
    public static long start(ServerLevel level, int type, RandomSource rng) {
        long t = level.getGameTime();
        long delay = 20;
        long day = level.getDayTime() % 24000L;
        if (SkyEvent.WHEN[type] == 0 && !night(level)) {
            delay = Math.floorMod(13000L - day, 24000L);
        }
        add(type, t + delay, rng);
        sync(level);
        return delay;
    }

    public static void stop(ServerLevel level, int type) {
        if (type < 0) {
            EVENTS.clear();
        } else {
            EVENTS.removeIf(e -> e.type == type);
        }
        sync(level);
    }

    public static void sync(ServerLevel level) {
        PacketDistributor.sendToPlayersInDimension(level, payload());
    }

    public static SkyEventsPayload payload() {
        List<SkyEventsPayload.Entry> list = new ArrayList<>();
        for (SkyEvent e : EVENTS) {
            list.add(new SkyEventsPayload.Entry(e.type, e.start, e.duration, e.seed));
        }
        return new SkyEventsPayload(list);
    }
}
