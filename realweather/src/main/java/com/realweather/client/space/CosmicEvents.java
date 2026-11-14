package com.realweather.client.space;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cosmic events out beyond Earth, coming and going: supernovae, kilonovae, mergers, bursts, flares, captures,
 * collisions, eruptions, collapses, galaxies colliding. Each is a simulation (not a prediction of anything real):
 * scheduled through game time the same for everyone, at a place in the sky and a distance, upcoming, then active
 * for a few minutes, then over. They can be tracked (with a message when one begins), watched through the
 * telescope or up close in the planet map, paused, sped up, slowed down and replayed.
 */
public final class CosmicEvents {
    public static final int SUPERNOVA = 0, HYPERNOVA = 1, KILONOVA = 2, NS_MERGER = 3, BH_MERGER = 4, NS_COLLISION = 5, GRB = 6, FRB = 7, TDE = 8,
            STELLAR_FLARE = 9, CME = 10, PROMINENCE = 11, MAGNETAR_FLARE = 12, PULSAR_GLITCH = 13, ROGUE_CAPTURE = 14, COMET_BREAKUP = 15,
            ASTEROID_COLLISION = 16, RING_DISRUPTION = 17, EXOMOON_ERUPTION = 18, ATMOSPHERE_COLLAPSE = 19, FIELD_REVERSAL = 20, DISK_FORMATION = 21,
            STARBURST = 22, GALAXY_COLLISION = 23, GALAXY_MERGER = 24, TYPES = 25;
    /** How long each kind lasts (game ticks; a twentieth of a second each), at the normal setting. */
    private static final int[] LENGTH = {6000, 6000, 4800, 4800, 4800, 3600, 2400, 1800, 4800, 2400, 4800, 4800, 3600, 2400, 7200, 6000, 3600, 6000,
            4800, 7200, 7200, 9600, 7200, 9600, 9600};
    /** How the distance reads: in the solar neighbourhood, the galaxy, or other galaxies. */
    private static final String[] DIST = {"12 000 ly", "2.1 billion ly", "130 million ly", "130 million ly", "1.3 billion ly", "40 million ly",
            "4.5 billion ly", "3 billion ly", "250 million ly", "40 ly", "1 AU", "1 AU", "30 000 ly", "6 000 ly", "300 ly", "1.2 AU", "2.6 AU", "9.5 AU",
            "200 ly", "90 ly", "150 ly", "450 ly", "12 million ly", "300 million ly", "500 million ly"};
    private static final int SLOT = 6000;
    private static final Set<String> TRACKED = new HashSet<>();
    private static final Set<String> ANNOUNCED = new HashSet<>();
    private static final Map<String, Catalog.Target> CACHE = new HashMap<>();
    /** Viewing controls: paused, how fast, and a replay. */
    public static boolean paused;
    public static double rate = 1;
    private static final Map<String, double[]> REPLAY = new HashMap<>();
    private static double pausedAt = -1;

    private CosmicEvents() {}

    /** One event: its kind, place (sky frame), when, how long, where, and the target to point at. */
    public record Event(String id, int type, double[] dir, long start, long length, String host, Catalog.Target target) {}

    private static float h(long seed, long k) {
        return Space3D.h(seed, k);
    }

    private static double rateSetting() {
        return com.realweather.ClientConfig.EVENT_RATE.get();
    }

    private static double lengthSetting() {
        return com.realweather.ClientConfig.EVENT_LENGTH.get();
    }

    /** The events of one slot of time (the same on every machine). */
    private static List<Event> slot(long slot) {
        List<Event> out = new ArrayList<>();
        long seed = slot * 0x9E3779B97F4A7C15L + 1987;
        int n = (int) Math.floor(rateSetting() / 4.0 + h(seed, 0));
        for (int k = 0; k < n; k++) {
            int type = (int) (h(seed, k * 11L + 1) * TYPES) % TYPES;
            long start = slot * SLOT + (long) (h(seed, k * 11L + 2) * SLOT);
            long len = (long) (LENGTH[type] * lengthSetting());
            double x = h(seed, k * 11L + 3) * 2 - 1, z = h(seed, k * 11L + 4) * 2 - 1, y = -0.3 - h(seed, k * 11L + 5);
            double l = Math.sqrt(x * x + y * y + z * z);
            double[] dir = {x / l, y / l, z / l};
            String id = "event_" + slot + "_" + k;
            String host = host(type, seed, k);
            Event e = new Event(id, type, dir, start, len, host, target(id, type, dir, start, len, host));
            out.add(e);
        }
        return out;
    }

    /** Where it happens: a real galaxy for the far ones, a simulated star system otherwise. */
    private static String host(int type, long seed, int k) {
        String[] galaxies = {"andromeda", "whirlpool", "pinwheel", "sombrero", "bodes", "centaurus_a", "sculptor", "black_eye"};
        if (type == SUPERNOVA && h(seed, k * 11L + 7) < 0.5 || type == HYPERNOVA || type == GRB || type == KILONOVA || type == NS_MERGER
                || type == BH_MERGER || type == TDE || type == STARBURST) {
            return "realweather.target." + galaxies[(int) (h(seed, k * 11L + 8) * galaxies.length) % galaxies.length];
        }
        if (type == GALAXY_COLLISION || type == GALAXY_MERGER) {
            return "realweather.event.host.galaxy_pair";
        }
        return "realweather.event.host.system";
    }

    private static Catalog.Target target(String id, int type, double[] dir, long start, long len, String host) {
        Catalog.Target t = CACHE.get(id);
        if (t != null) {
            return t;
        }
        Catalog.Body b = Catalog.body(id, Catalog.EVENT, radius(type), 1F, 0.9F, 0.8F);
        b.eventType = type;
        b.eventStart = start;
        b.eventLength = len;
        t = Catalog.dynamic(id, Catalog.EVENTS, dir, DIST[type], b, Component.translatable("realweather.event." + type));
        CACHE.put(id, t);
        if (CACHE.size() > 400) {
            CACHE.clear();
        }
        return t;
    }

    /** Its size on the sky (radians), so the telescope must zoom in to see it. */
    private static double radius(int type) {
        return switch (type) {
            case GALAXY_COLLISION, GALAXY_MERGER -> 0.004;
            case STARBURST, DISK_FORMATION -> 0.0015;
            case CME, PROMINENCE, COMET_BREAKUP, ASTEROID_COLLISION, RING_DISRUPTION -> 0.0008;
            default -> 0.0005;
        };
    }

    private static long now() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    /** The events from a while ago to a while ahead, the soonest first. */
    public static List<Event> window() {
        long now = now(), cur = Math.floorDiv(now, SLOT);
        List<Event> out = new ArrayList<>();
        for (long s = cur - 6; s <= cur + 8; s++) {
            out.addAll(slot(s));
        }
        out.removeIf(e -> e.start + e.length < now - SLOT * 6L);
        out.sort((a, b) -> Long.compare(a.start, b.start));
        return out;
    }

    /** Those going on now (and a little before or after, still to see). */
    public static List<Event> active() {
        long now = now();
        List<Event> out = new ArrayList<>();
        for (long s = Math.floorDiv(now, SLOT) - 3; s <= Math.floorDiv(now, SLOT); s++) {
            for (Event e : slot(s)) {
                if (REPLAY.containsKey(e.id) || now >= e.start - 600 && now <= e.start + e.length + 1200) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    public static final int UPCOMING = 0, ACTIVE = 1, OVER = 2;

    public static int status(Event e) {
        long now = now();
        return now < e.start ? UPCOMING : now <= e.start + e.length ? ACTIVE : OVER;
    }

    /** How far through it is now (0..1; below 0 not begun), with the viewing controls (pause, speed, replay). */
    public static double progress(Catalog.Body b) {
        double now = now();
        if (paused) {
            if (pausedAt < 0) {
                pausedAt = now;
            }
            now = pausedAt;
        } else {
            pausedAt = -1;
        }
        double[] r = REPLAY.get(b.id);
        if (r != null) {
            double p = (now - r[0]) * rate / b.eventLength;
            if (p > 1.15) {
                REPLAY.remove(b.id);
            }
            return p;
        }
        return (now - b.eventStart) / (double) b.eventLength;
    }

    public static void replay(Event e) {
        REPLAY.put(e.id, new double[]{now()});
    }

    public static boolean tracked(Event e) {
        return TRACKED.contains(e.id);
    }

    public static void track(Event e) {
        if (!TRACKED.remove(e.id)) {
            TRACKED.add(e.id);
        }
    }

    /** Messages as tracked events (and every new upcoming supernova, an unstable star found) begin. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.level.getGameTime() % 20 != 0) {
            return;
        }
        for (Event e : window()) {
            int st = status(e);
            String key = e.id + ":" + st;
            if (ANNOUNCED.contains(key)) {
                continue;
            }
            if (st == ACTIVE && TRACKED.contains(e.id)) {
                ANNOUNCED.add(key);
                mc.player.displayClientMessage(Component.translatable("realweather.event.started", Component.translatable("realweather.event." + e.type)),
                        false);
            } else if (st == UPCOMING && e.type == SUPERNOVA && e.start - now() < 2400 && TRACKED.contains(e.id)) {
                ANNOUNCED.add(key);
                mc.player.displayClientMessage(Component.translatable("realweather.event.soon", Component.translatable("realweather.event." + e.type)), false);
            }
        }
        if (ANNOUNCED.size() > 500) {
            ANNOUNCED.clear();
        }
    }

    /** Right ascension and declination of a sky-frame direction, as text. */
    public static String coordinates(double[] d) {
        double ra = Math.toDegrees(Math.atan2(d[2], d[0]));
        if (ra < 0) {
            ra += 360;
        }
        double dec = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -d[1]))));
        int h = (int) (ra / 15), m = (int) ((ra / 15 - h) * 60);
        return String.format(java.util.Locale.ROOT, "RA %02dh %02dm  Dec %+.1f", h, m, dec);
    }

    public static String distance(int type) {
        return DIST[type];
    }
}
