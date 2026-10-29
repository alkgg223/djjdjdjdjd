package com.realweather.client;

import com.realweather.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Lightning flashes that flicker like real strokes, flashes hidden inside the clouds, and thunder shake. */
public final class LightningFx {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int MAX_PULSES = 8;
    private static final int[] pulseTick = new int[MAX_PULSES];
    private static final float[] pulseStrength = new float[MAX_PULSES];
    private static int shakeTick = -1;
    private static float shakePending;

    /** Current screen flash brightness 0..1. */
    public static float flash;
    /** Current camera shake amount 0..1. */
    public static float shake;
    /** How strongly the flash lights up the world right now (lightmap), 0..1. */
    public static float worldLight;
    private static int now;

    private LightningFx() {}

    static void flash(double dist, boolean bolt) {
        float strength = (float) Math.max(0.08, Math.min(1.0, 1.25 - dist / 420.0));
        if (!bolt) {
            strength *= 0.35F;
        }
        strength *= 0.35F + 0.65F * ClientWeather.exposure;
        flash = Math.max(flash, strength);
        worldLight = Math.max(worldLight, Math.min(1F, strength * 1.3F));
        // A real flash is several return strokes a few hundredths of a second apart.
        int extra = 1 + RANDOM.nextInt(3);
        int t = now;
        for (int i = 0; i < extra; i++) {
            t += 2 + RANDOM.nextInt(4);
            addPulse(t, strength * (0.5F + RANDOM.nextFloat() * 0.5F));
        }
    }

    private static void addPulse(int tick, float strength) {
        for (int i = 0; i < MAX_PULSES; i++) {
            if (pulseStrength[i] <= 0F) {
                pulseTick[i] = tick;
                pulseStrength[i] = strength;
                return;
            }
        }
    }

    /** Continuous ground shaking (a tornado nearby); call every tick. */
    public static void rumble(float amount) {
        shake = Math.max(shake, Math.min(1F, amount));
    }

    static void shakeLater(int delay, float amount) {
        shakeTick = now + delay;
        shakePending = Math.max(shakePending, amount);
    }

    static void tick(Minecraft mc, ClientLevel level) {
        now++;
        flash *= 0.55F;
        worldLight *= 0.68F;
        shake *= 0.85F;
        for (int i = 0; i < MAX_PULSES; i++) {
            if (pulseStrength[i] > 0F && pulseTick[i] <= now) {
                flash = Math.max(flash, pulseStrength[i]);
                worldLight = Math.max(worldLight, Math.min(1F, pulseStrength[i] * 1.3F));
                level.setSkyFlashTime(2);
                pulseStrength[i] = 0F;
            }
        }
        if (shakeTick >= 0 && now >= shakeTick) {
            shake = Math.max(shake, shakePending);
            shakePending = 0F;
            shakeTick = -1;
        }
        // Lightning inside the clouds: flashes without a bolt, followed by distant rumbling.
        float storm = ClientWeather.thunder * Math.max(ClientWeather.camRain, ClientWeather.camSnow * 0.5F) * level.getThunderLevel(1F);
        if (ClientWeather.active(level) && storm > 0.02F && RANDOM.nextFloat() < storm * storm * 0.008F) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            double ang = RANDOM.nextDouble() * Math.PI * 2;
            double d = 700 + RANDOM.nextDouble() * 3500;
            level.setSkyFlashTime(2);
            WeatherSounds.strike(cam.x + Math.cos(ang) * d, cam.y + 900, cam.z + Math.sin(ang) * d, false);
        }
        if (!ClientConfig.LIGHTNING_FLASH.get()) {
            flash = 0F;
            worldLight = 0F;
        }
    }
}
