package com.vehicleworks.client;

import com.vehicleworks.VehicleWorks;
import com.vehicleworks.vehicle.VehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundSource;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * Personal video on the passenger's seat screen (YouTube and other links), played through the optional
 * WATERMeDIA mod (VLC). Only the seated passenger's own screen shows it, so nothing has to be synchronised.
 * WATERMeDIA is looked up at run time: without it the mod works as before and the seat screen says so.
 * The video keeps playing at any speed; it stops only when the passenger leaves the seat.
 */
public final class SeatVideo {
    private SeatVideo() {}

    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(VehicleWorks.MODID, "dynamic/seat_video");
    private static final String[] PLAYER_CLASSES = {
            "org.watermedia.api.player.videolan.VideoPlayer",
            "me.srrapero720.watermedia.api.player.videolan.VideoPlayer",
            "me.srrapero720.watermedia.api.player.SyncVideoPlayer"};

    private static boolean resolved, ok;
    private static Class<?> playerClass;
    private static Object player;
    private static int vehicleId = -1;
    private static long seat;
    private static String url = "";
    private static String status = "";
    private static VideoTexture tex;
    private static int volumeTick;

    /** WATERMeDIA installed and its player API found */
    public static boolean available() {
        if (!resolved) {
            resolved = true;
            if (!ModList.get().isLoaded("watermedia")) { status = "vehicleworks.video.missing"; return false; }
            for (String n : PLAYER_CLASSES) {
                try { playerClass = Class.forName(n); break; } catch (Throwable ignored) {}
            }
            ok = playerClass != null;
            if (!ok) { status = "vehicleworks.video.api"; VehicleWorks.LOGGER.warn("WATERMeDIA is loaded but no video player class was found"); }
        }
        return ok;
    }

    public static String status() { return status; }
    public static String url() { return url; }

    public static boolean playingFor(VehicleEntity v, long seatKey) {
        return player != null && v.getId() == vehicleId && seat == seatKey;
    }

    /** start a video on the seat the local player sits in */
    public static void play(VehicleEntity v, long seatKey, String link) {
        stop();
        if (!available() || link == null || link.isBlank()) return;
        url = link.trim();
        try {
            player = create();
            URI uri = fixUrl(url);
            if (!call(player, "start", uri) && !call(player, "start", uri.toString())) throw new IllegalStateException("no start method");
            vehicleId = v.getId();
            seat = seatKey;
            status = "vehicleworks.video.loading";
            volumeTick = 0;
        } catch (Throwable t) {
            VehicleWorks.LOGGER.warn("Seat video failed to start: {}. Player methods: {}", t.toString(), methods());
            status = "vehicleworks.video.error";
            stop();
        }
    }

    public static void stop() {
        if (player != null) {
            Object p = player;
            player = null;
            try { call(p, "stop"); } catch (Throwable ignored) {}
            try { call(p, "release"); } catch (Throwable ignored) {}
        }
        vehicleId = -1;
        if (status.equals("vehicleworks.video.playing") || status.equals("vehicleworks.video.loading")) status = "";
    }

    public static void togglePause() {
        if (player == null) return;
        try {
            Object playing = invoke(player, "isPlaying");
            if (Boolean.TRUE.equals(playing)) call(player, "pause"); else call(player, "play");
        } catch (Throwable ignored) {}
    }

    /** every client tick: stop when the passenger is no longer in that seat; volume follows the game's sound settings */
    public static void tick() {
        if (player == null) return;
        Minecraft mc = Minecraft.getInstance();
        VehicleEntity v = mc.player == null ? null : ClientEvents.ridden();
        Long k = v == null ? null : v.seats.get(mc.player.getUUID());
        if (v == null || v.getId() != vehicleId || k == null || k != seat) { stop(); return; }
        if (++volumeTick % 10 == 1) {
            float vol = mc.options.getSoundSourceVolume(SoundSource.MASTER) * mc.options.getSoundSourceVolume(SoundSource.AMBIENT);
            try { call(player, "setVolume", Math.round(vol * 100)); } catch (Throwable ignored) {}
            Object ready = safe(() -> invoke(player, "isReady"));
            if (Boolean.TRUE.equals(ready) || Boolean.TRUE.equals(safe(() -> invoke(player, "isPlaying")))) status = "vehicleworks.video.playing";
        }
    }

    /** GL texture of the current frame (registered as {@link #TEXTURE}), or -1 when no frame is ready */
    public static int frame() {
        if (player == null) return -1;
        Object id = safe(() -> {
            for (String m : new String[]{"preRender", "texture", "getGlTexture", "getTexture"}) {
                Method mm = find(player.getClass(), m, 0);
                if (mm != null) { Object r = mm.invoke(player); if (r instanceof Integer) return r; }
            }
            return null;
        });
        if (!(id instanceof Integer i) || i <= 0) return -1;
        if (tex == null) {
            tex = new VideoTexture();
            Minecraft.getInstance().getTextureManager().register(TEXTURE, tex);
        }
        tex.set(i);
        return i;
    }

    /** video width / height, 16:9 until known */
    public static float aspect() {
        Object w = safe(() -> invoke(player, "width")), h = safe(() -> invoke(player, "height"));
        if (!(w instanceof Integer)) w = safe(() -> invoke(player, "getWidth"));
        if (!(h instanceof Integer)) h = safe(() -> invoke(player, "getHeight"));
        if (w instanceof Integer wi && h instanceof Integer hi && wi > 0 && hi > 0) return wi / (float) hi;
        return 16 / 9f;
    }

    // ------------------------------------------------------------------ reflection helpers

    private static Object create() throws Exception {
        for (Constructor<?> c : playerClass.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 1 && p[0].isAssignableFrom(Minecraft.class)) return c.newInstance(Minecraft.getInstance());
            if (p.length == 1 && p[0] == Executor.class) return c.newInstance((Executor) Minecraft.getInstance());
        }
        for (Constructor<?> c : playerClass.getConstructors()) if (c.getParameterCount() == 0) return c.newInstance();
        throw new IllegalStateException("no usable constructor: " + Arrays.toString(playerClass.getConstructors()));
    }

    /** WATERMeDIA's link fixers turn a YouTube page link into a playable stream; fall back to the link itself */
    private static URI fixUrl(String link) {
        String[][] fixers = {{"me.srrapero720.watermedia.api.url.UrlAPI", "fixURL"}, {"org.watermedia.api.url.UrlAPI", "fixURL"},
                {"me.srrapero720.watermedia.api.WaterMediaAPI", "url_fixURL"}};
        for (String[] f : fixers) {
            try {
                Class<?> c = Class.forName(f[0]);
                Method m = c.getMethod(f[1], String.class);
                Object r = m.invoke(null, link);
                if (r == null) continue;
                for (String field : new String[]{"url", "uri"}) {
                    try {
                        Object u = r.getClass().getField(field).get(r);
                        if (u != null) return URI.create(u.toString());
                    } catch (NoSuchFieldException ignored) {}
                }
                if (r instanceof java.net.URL u) return u.toURI();
            } catch (Throwable ignored) {}
        }
        return URI.create(link);
    }

    private static Method find(Class<?> c, String name, int params) {
        for (Method m : c.getMethods()) if (m.getName().equals(name) && m.getParameterCount() == params) return m;
        return null;
    }

    private static Object invoke(Object target, String name) throws Exception {
        Method m = find(target.getClass(), name, 0);
        return m == null ? null : m.invoke(target);
    }

    /** call a method by name whose single parameter accepts the argument (or a no-arg method); true when one was found */
    private static boolean call(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] p = m.getParameterTypes();
            boolean fits = true;
            for (int i = 0; i < p.length; i++) {
                Class<?> want = p[i].isPrimitive() ? (p[i] == int.class ? Integer.class : p[i] == boolean.class ? Boolean.class : p[i] == long.class ? Long.class : p[i]) : p[i];
                if (!want.isInstance(args[i]) && !(p[i] == CharSequence.class && args[i] instanceof String)) fits = false;
            }
            if (!fits) continue;
            m.invoke(target, args);
            return true;
        }
        return false;
    }

    private interface Sup { Object get() throws Exception; }

    private static Object safe(Sup s) {
        try { return s.get(); } catch (Throwable t) { return null; }
    }

    private static String methods() {
        if (playerClass == null) return "-";
        StringBuilder b = new StringBuilder();
        for (Method m : playerClass.getMethods()) b.append(m.getName()).append(Arrays.toString(m.getParameterTypes())).append(' ');
        return b.toString();
    }

    /** wraps WATERMeDIA's GL texture so it can be drawn like any other texture (never deletes it) */
    static final class VideoTexture extends AbstractTexture {
        void set(int glId) { this.id = glId; }

        @Override
        public void load(ResourceManager rm) {}

        @Override
        public void releaseId() { this.id = -1; }
    }
}
