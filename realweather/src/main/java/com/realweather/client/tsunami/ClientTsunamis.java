package com.realweather.client.tsunami;

import com.realweather.ClientConfig;
import com.realweather.ModContent;
import com.realweather.client.LightningFx;
import com.realweather.net.TsunamiSyncPayload;
import com.realweather.server.Tsunami;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client copy of the tsunamis: their fronts run on between server updates, the roar of the wave and the rush of the
 * flood follow you, the wave crashes now and then, spray blows off the breaking crest, and the ground shakes as it
 * comes close.
 */
public final class ClientTsunamis {
    private static final Map<Integer, W> WAVES = new LinkedHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private ClientTsunamis() {}

    public static final class W {
        public final Tsunami t;
        float serverFront;
        Loop roar, rush;
        int crashTimer = 60;

        W(Tsunami t) {
            this.t = t;
        }
    }

    public static Collection<W> all() {
        return WAVES.values();
    }

    public static boolean isEmpty() {
        return WAVES.isEmpty();
    }

    public static void onSync(TsunamiSyncPayload p) {
        List<Integer> seen = new ArrayList<>();
        for (TsunamiSyncPayload.Entry e : p.waves()) {
            W w = WAVES.get(e.id());
            if (w == null) {
                Tsunami t = new Tsunami(e.id(), e.ox(), e.oz(), e.dirX(), e.dirZ(), e.baseY(), e.strength(), e.seed());
                t.front = e.front();
                w = new W(t);
                WAVES.put(e.id(), w);
            }
            w.serverFront = e.front();
            w.t.stage = e.stage();
            w.t.drained = e.drained();
            w.t.age = Math.max(w.t.age, e.age());
            seen.add(e.id());
        }
        WAVES.entrySet().removeIf(en -> {
            if (!seen.contains(en.getKey())) {
                stopSounds(en.getValue());
                return true;
            }
            return false;
        });
    }

    public static void clear() {
        WAVES.values().forEach(ClientTsunamis::stopSounds);
        WAVES.clear();
    }

    private static void stopSounds(W w) {
        if (w.roar != null) {
            w.roar.halt();
        }
        if (w.rush != null) {
            w.rush.halt();
        }
    }

    public static void tick(Minecraft mc, ClientLevel level) {
        if (WAVES.isEmpty() || mc.player == null) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (W w : WAVES.values()) {
            Tsunami t = w.t;
            t.age++;
            if (t.stage == Tsunami.RUNNING) {
                t.front += t.speed();
                t.front += (w.serverFront + t.speed() * 5 - t.front) * 0.05F;
            } else {
                t.drained = Math.min(1F, t.drained + 1F / 900F);
            }
            double sc = t.along(cam.x, cam.z);
            double lc = t.across(cam.x, cam.z);
            float h = t.height();
            boolean beside = Math.abs(lc) < t.width() + 40;
            double faceDist = beside ? Math.abs(sc - t.front) : 1.0E9;
            float flood = t.floodY();
            boolean inFlood = beside && sc < t.front && cam.y < flood + 3 && t.drained < 0.95F;
            // Ground shaking as the wall of water comes near.
            if (beside && faceDist < 160 && t.stage == Tsunami.RUNNING) {
                float k = (float) (1.0 - faceDist / 160.0);
                LightningFx.rumble(k * k * Mth.clamp(h / 14F, 0.15F, 1F) * 0.95F);
            }
            sounds(mc, w, cam, sc, lc, faceDist, h, inFlood);
            if (t.stage == Tsunami.RUNNING && h > 1.5F && beside && faceDist < 220) {
                spray(mc, level, t, lc, h, faceDist);
            }
        }
    }

    // ------------------------------------------------------------------------------------ particles
    private static void spray(Minecraft mc, ClientLevel level, Tsunami t, double lc, float h, double faceDist) {
        int q = ClientConfig.quality();
        int n = Math.round(new int[]{1, 2, 4, 6}[q] * (faceDist < 90 ? 1F : 0.5F) * Math.min(1.5F, 0.4F + h / 12F));
        for (int i = 0; i < n; i++) {
            double l = Mth.clamp(lc + (RANDOM.nextDouble() - 0.5) * 140, -t.width(), t.width());
            double s = t.front + (TsunamiRenderer.LIP_DS - TsunamiRenderer.TOE_DS) * h + 1 + RANDOM.nextDouble() * h * 0.3;
            double x = t.ox + t.dirX * s - t.dirZ * l;
            double z = t.oz + t.dirZ * s + t.dirX * l;
            double y = t.baseY + h * (0.8 + RANDOM.nextDouble() * 0.25);
            double sp = t.speed();
            level.addParticle(ModContent.TSUNAMI_SPRAY.get(), true, x, y, z, t.dirX * sp * 1.1, 0.05 + RANDOM.nextDouble() * 0.08 + h * 0.004,
                    t.dirZ * sp * 1.1);
        }
        // Foam and droplets thrown up at the toe of the wave close by.
        if (faceDist < 40) {
            for (int i = 0; i < 6; i++) {
                double l = Mth.clamp(lc + (RANDOM.nextDouble() - 0.5) * 30, -t.width(), t.width());
                double s = t.front + RANDOM.nextDouble() * 1.5;
                double x = t.ox + t.dirX * s - t.dirZ * l;
                double z = t.oz + t.dirZ * s + t.dirX * l;
                level.addParticle(ModContent.SPLASH_DROP.get(), x, t.baseY + 0.4 + RANDOM.nextDouble() * 1.2, z,
                        t.dirX * 0.3 + (RANDOM.nextDouble() - 0.5) * 0.2, 0.15 + RANDOM.nextDouble() * 0.2, t.dirZ * 0.3 + (RANDOM.nextDouble() - 0.5) * 0.2);
            }
        }
    }

    // ------------------------------------------------------------------------------------ sounds
    private static void sounds(Minecraft mc, W w, Vec3 cam, double sc, double lc, double faceDist, float h, boolean inFlood) {
        if (!ClientConfig.SOUNDS.get()) {
            return;
        }
        Tsunami t = w.t;
        double l = Mth.clamp(lc, -t.width(), t.width());
        double fx = t.ox + t.dirX * t.front - t.dirZ * l;
        double fz = t.oz + t.dirZ * t.front + t.dirX * l;
        float roarVol = t.stage == Tsunami.RUNNING ? (float) Mth.clamp(1.0 - faceDist / 700.0, 0.0, 1.0) * Mth.clamp(0.35F + h / 18F, 0F, 1.3F) : 0F;
        if (w.roar == null && roarVol > 0.02F) {
            w.roar = new Loop(ModContent.TSUNAMI_ROAR.get());
            mc.getSoundManager().play(w.roar);
        }
        if (w.roar != null) {
            w.roar.target = roarVol;
            w.roar.place(fx, t.baseY + h * 0.5, fz);
            w.roar.pitch = Mth.clamp(1.15F - h / 80F, 0.75F, 1.15F);
        }
        float rushVol = inFlood ? 0.9F * (1F - t.drained) : (float) Mth.clamp(1.0 - faceDist / 60.0, 0.0, 1.0) * 0.7F;
        if (w.rush == null && rushVol > 0.02F) {
            w.rush = new Loop(ModContent.TSUNAMI_RUSH.get());
            mc.getSoundManager().play(w.rush);
        }
        if (w.rush != null) {
            w.rush.target = rushVol;
            w.rush.place(cam.x, cam.y, cam.z);
        }
        if (t.stage == Tsunami.RUNNING && faceDist < 260 && --w.crashTimer <= 0) {
            w.crashTimer = 30 + RANDOM.nextInt(90);
            float vol = (float) Mth.clamp(1.2 - faceDist / 260.0, 0.1, 1.0) * Mth.clamp(0.4F + h / 15F, 0F, 1.2F);
            mc.getSoundManager().play(new SimpleSoundInstance(ModContent.TSUNAMI_CRASH.get().getLocation(), SoundSource.WEATHER, vol,
                    0.85F + RANDOM.nextFloat() * 0.25F, RANDOM, false, 0, SoundInstance.Attenuation.NONE, fx, t.baseY + h * 0.6, fz, false));
        }
    }

    /** A looping sound that glides to its target volume and follows a point. */
    static final class Loop extends AbstractTickableSoundInstance {
        float target;

        Loop(SoundEvent event) {
            super(event, SoundSource.WEATHER, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.01F;
        }

        void place(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void tick() {
            volume += (target - volume) * 0.05F;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        void halt() {
            stop();
        }
    }
}
