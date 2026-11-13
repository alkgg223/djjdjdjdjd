package com.realweather.client.sky;

import com.realweather.LightningKind;
import com.realweather.ModContent;
import com.realweather.client.ClientStorms;
import com.realweather.net.SkyEventsPayload;
import com.realweather.server.SkyEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The sky events as the client knows them, and the bits of them that happen tick by tick: the light of a fireball on
 * the land and its sonic boom a little later, and the red sprites and blue jets flashing high over thunderstorms
 * (with the lightning in the storm below them).
 */
public final class ClientSkyEvents {
    static final List<SkyEvent> EVENTS = new ArrayList<>();
    /** Sprites, jets and elves above storms: x, y, z (world), kind (0 sprite, 1 jet, 2 elve), born tick, size. */
    static final List<double[]> FLASHES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long lastTick = Long.MIN_VALUE;

    private ClientSkyEvents() {}

    public static void onSync(SkyEventsPayload p) {
        EVENTS.clear();
        for (SkyEventsPayload.Entry e : p.events()) {
            EVENTS.add(new SkyEvent(e.type(), e.start(), e.duration(), e.seed()));
        }
    }

    public static void clear() {
        EVENTS.clear();
        FLASHES.clear();
    }

    public static boolean isEmpty() {
        return EVENTS.isEmpty();
    }

    /** The event of this type going on at this time, or null. */
    static SkyEvent get(int type, double t) {
        for (SkyEvent e : EVENTS) {
            if (e.type == type && e.active(t)) {
                return e;
            }
        }
        return null;
    }

    public static void tick(Minecraft mc, ClientLevel level) {
        long now = level.getGameTime();
        FLASHES.removeIf(f -> now - f[4] > 12);
        if (EVENTS.isEmpty()) {
            lastTick = now;
            return;
        }
        long prev = lastTick == Long.MIN_VALUE || now - lastTick > 40 ? now - 1 : lastTick;
        lastTick = now;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float night = Math.min(1F, level.getStarBrightness(1F) * 2.2F);
        float light = 0F;
        for (SkyEvent e : EVENTS) {
            if (!e.active(now)) {
                continue;
            }
            if (e.type == SkyEvent.FIREBALLS || e.type == SkyEvent.DEBRIS_REENTRY) {
                // The land lights up under a bright fireball; a big one booms a while after it has gone.
                light = Math.max(light, SpaceRenderer.fireballLight(e, now));
                for (long t = prev + 1; t <= now; t++) {
                    if (SpaceRenderer.fireballEndsAt(e, t)) {
                        boom(mc, 50 + RANDOM.nextInt(80));
                    }
                }
            } else if (e.type == SkyEvent.UPPER_LIGHTNING && night > 0.3F && RANDOM.nextFloat() < 0.035F) {
                upperFlash(level, cam);
            }
        }
        if (light > 0.02F) {
            com.realweather.client.LightningFx.skyGlow(light * Math.max(0.35F, night));
        }
    }

    private static void boom(Minecraft mc, int delay) {
        if (!com.realweather.ClientConfig.SOUNDS.get()) {
            return;
        }
        SimpleSoundInstance s = new SimpleSoundInstance(ModContent.THUNDER_FAR.get().getLocation(), SoundSource.WEATHER, 0.6F,
                0.5F + RANDOM.nextFloat() * 0.1F, RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        mc.getSoundManager().playDelayed(s, delay);
    }

    /** A red sprite, a blue jet or an elve high over a thunderstorm (or far off in the dark if none is near). */
    public static void upperFlash(ClientLevel level, Vec3 cam) {
        double x, z;
        List<ClientStorms.Cell> storms = new ArrayList<>();
        for (ClientStorms.Cell c : ClientStorms.cells()) {
            double d = Math.hypot(c.x - cam.x, c.z - cam.z);
            if (c.cstrength() > 0.25F && d > 250 && d < 4000) {
                storms.add(c);
            }
        }
        if (!storms.isEmpty()) {
            ClientStorms.Cell c = storms.get(RANDOM.nextInt(storms.size()));
            x = c.x + (RANDOM.nextDouble() - 0.5) * c.radius;
            z = c.z + (RANDOM.nextDouble() - 0.5) * c.radius;
        } else {
            double a = RANDOM.nextDouble() * Math.PI * 2, d = 1200 + RANDOM.nextDouble() * 1800;
            x = cam.x + Math.cos(a) * d;
            z = cam.z + Math.sin(a) * d;
        }
        float u = RANDOM.nextFloat();
        int kind = u < 0.6F ? 0 : u < 0.85F ? 1 : 2;
        double deck = StormClouds.deckY(level);
        FLASHES.add(new double[]{x, deck, z, kind, level.getGameTime(), 0.7 + RANDOM.nextDouble() * 0.6});
        // The lightning in the storm under it, in the colours of the strongest kinds.
        CloudFlashes.add(x, deck + 30, z, 1.4F, RANDOM.nextFloat() < 0.5F, RANDOM.nextBoolean() ? LightningKind.PURPLE : LightningKind.WHITE);
    }
}
