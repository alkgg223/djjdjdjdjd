package com.realweather.client.tornado;

import com.realweather.ClientConfig;
import com.realweather.ModContent;
import com.realweather.client.ClientWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * The voice of one tornado: a freight-train roar close by, the crack and clatter of debris when it is right
 * on top of you, and a deep rumble from far away. Each comes from the tornado's direction.
 */
final class TornadoSounds {
    private Loop roar;
    private Loop debris;
    private Loop far;

    void tick(Minecraft mc, ClientTornadoes.T t, double dist, double camY) {
        if (!ClientConfig.SOUNDS.get()) {
            stop();
            return;
        }
        float rc = Math.max(2F, t.coreRadius());
        float power = t.contact * (0.4F + 0.6F * t.strength);
        float base = 0.55F + t.rating * 0.09F;
        float roarV = base * power * (float) Math.pow(Mth.clamp(1.0 - dist / (260 + t.rating * 45), 0.0, 1.0), 1.6);
        float debrisV = power * (0.4F + 0.12F * t.rating) * (float) Math.pow(Mth.clamp(1.0 - dist / (40 + rc * 2.5), 0.0, 1.0), 1.2);
        float farV = (0.45F + 0.1F * t.rating) * Math.max(power, t.strength * 0.5F) * smooth(70F, 200F, (float) dist)
                * (float) Mth.clamp(1.0 - dist / 1000.0, 0.0, 1.0);
        float muffle = ClientWeather.underwater ? 0.2F : ClientWeather.covered ? 0.7F : 1F;
        double y = t.groundY + 4;
        roar = ensure(mc, roar, ModContent.TORNADO_ROAR.get(), roarV * muffle, 1.06F - t.rating * 0.04F, t.x, y, t.z);
        debris = ensure(mc, debris, ModContent.TORNADO_DEBRIS.get(), debrisV * muffle, 1F, t.x, y, t.z);
        far = ensure(mc, far, ModContent.TORNADO_FAR.get(), farV * muffle, 1F - t.rating * 0.03F, t.x, y, t.z);
    }

    private static Loop ensure(Minecraft mc, Loop loop, SoundEvent event, float volume, float pitch, double x, double y, double z) {
        volume = Mth.clamp(volume, 0F, 1F);
        if (loop != null && (loop.isStopped() || !mc.getSoundManager().isActive(loop)) && !loop.justStarted()) {
            loop = null;
        }
        if (loop == null) {
            if (volume < 0.01F) {
                return null;
            }
            loop = new Loop(event, x, y, z);
            mc.getSoundManager().play(loop);
        }
        loop.target = volume;
        loop.targetPitch = pitch;
        loop.move(x, y, z);
        return loop;
    }

    void stop() {
        if (roar != null) {
            roar.halt();
        }
        if (debris != null) {
            debris.halt();
        }
        if (far != null) {
            far.halt();
        }
        roar = debris = far = null;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    /** Positional loop without distance roll-off: the volume is set from the distance above. */
    static final class Loop extends AbstractTickableSoundInstance {
        float target;
        float targetPitch = 1F;
        private int age;
        private int silent;

        Loop(SoundEvent event, double x, double y, double z) {
            super(event, SoundSource.WEATHER, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = false;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001F;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        boolean justStarted() {
            return age < 5;
        }

        void move(double nx, double ny, double nz) {
            this.x = nx;
            this.y = ny;
            this.z = nz;
        }

        @Override
        public void tick() {
            age++;
            volume += (target - volume) * 0.08F;
            pitch += (targetPitch - pitch) * 0.05F;
            if (target < 0.005F && volume < 0.01F) {
                if (++silent > 100) {
                    stop();
                }
            } else {
                silent = 0;
            }
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
