package com.realweather.client;

import com.realweather.ClientConfig;
import com.realweather.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.function.Supplier;

/**
 * Ambient weather audio: rain (light and heavy, muffled on a roof when indoors), wind, blizzard and
 * sandstorm loops that fade with intensity and shelter; thunder that arrives after the flash, delayed
 * by the speed of sound, sounding sharp up close and as a low rumble far away.
 */
public final class WeatherSounds {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final ResourceLocation VANILLA_THUNDER = SoundEvents.LIGHTNING_BOLT_THUNDER.getLocation();
    private static final ResourceLocation VANILLA_IMPACT = SoundEvents.LIGHTNING_BOLT_IMPACT.getLocation();

    private static Loop rainLight, rainHeavy, rainRoof, wind, blizzard, sandstorm;
    private static final float[] levels = new float[6];

    private WeatherSounds() {}

    static void tick(Minecraft mc, ClientLevel level) {
        if (!ClientConfig.SOUNDS.get() || !ClientWeather.active(level)) {
            stopAll();
            return;
        }
        float rain = ClientWeather.camRain;
        float heavy = smooth(0.35F, 0.85F, ClientWeather.rain);
        float open = ClientWeather.exposure;
        float near = ClientWeather.groundNear;
        float outside = rain * (0.2F + 0.8F * open) * near;
        float roof = ClientWeather.covered ? rain * (1F - open * 0.5F) : rain * (1F - open) * 0.4F;
        float windLevel = (float) Math.pow(smooth(0.12F, 1F, ClientWeather.wind), 1.2) * (0.25F + 0.75F * open)
                * (1F - ClientWeather.camBlizzard) * (1F - ClientWeather.camSand * 0.8F);
        float bliz = ClientWeather.camBlizzard * (0.3F + 0.7F * open);
        float sand = ClientWeather.camSand * (0.3F + 0.7F * open);
        if (ClientWeather.underwater) {
            // Under water the storm above is only a dull murmur.
            outside *= 0.12F;
            roof *= 0.12F;
            windLevel *= 0.1F;
            bliz *= 0.1F;
            sand *= 0.1F;
        }

        levels[0] = outside * (1F - heavy) * 0.85F;
        levels[1] = outside * heavy;
        levels[2] = roof * (0.6F + 0.4F * heavy);
        levels[3] = windLevel * 0.75F;
        levels[4] = bliz;
        levels[5] = sand;
        rainLight = ensure(mc, rainLight, ModContent.RAIN_LIGHT, outside * (1F - heavy) * 0.85F);
        rainHeavy = ensure(mc, rainHeavy, ModContent.RAIN_HEAVY, outside * heavy);
        rainRoof = ensure(mc, rainRoof, ModContent.RAIN_ROOF, roof * (0.6F + 0.4F * heavy));
        wind = ensure(mc, wind, ModContent.WIND, windLevel * 0.75F);
        blizzard = ensure(mc, blizzard, ModContent.BLIZZARD, bliz);
        sandstorm = ensure(mc, sandstorm, ModContent.SANDSTORM, sand);
    }

    private static Loop ensure(Minecraft mc, Loop loop, Supplier<SoundEvent> event, float volume) {
        volume = Mth.clamp(volume, 0F, 1F);
        if (loop != null && (loop.isStopped() || !mc.getSoundManager().isActive(loop))) {
            if (!loop.justStarted()) {
                loop = null;
            }
        }
        if (loop == null) {
            if (volume < 0.01F) {
                return null;
            }
            loop = new Loop(event.get());
            mc.getSoundManager().play(loop);
        }
        loop.target = volume;
        return loop;
    }

    /** Target volumes of the loops, for diagnostics. */
    public static String debug() {
        return String.format("rainLight=%.2f rainHeavy=%.2f roof=%.2f wind=%.2f blizzard=%.2f sandstorm=%.2f",
                levels[0], levels[1], levels[2], levels[3], levels[4], levels[5]);
    }

    public static void stopAll() {
        rainLight = halt(rainLight);
        rainHeavy = halt(rainHeavy);
        rainRoof = halt(rainRoof);
        wind = halt(wind);
        blizzard = halt(blizzard);
        sandstorm = halt(sandstorm);
    }

    private static Loop halt(Loop loop) {
        if (loop != null) {
            loop.halt();
        }
        return null;
    }

    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || !ClientConfig.SOUNDS.get()) {
            return;
        }
        ResourceLocation id = sound.getLocation();
        // Our own thunder (delayed by distance) replaces the vanilla one; it is started when the bolt appears.
        if (VANILLA_THUNDER.equals(id) || VANILLA_IMPACT.equals(id)) {
            event.setSound(null);
        }
    }

    /** A lightning bolt appeared: flash now, thunder when the sound gets here. */
    public static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.LightningBolt bolt) {
            strike(bolt.getX(), bolt.getY(), bolt.getZ(), true);
        }
    }

    /** A lightning strike (or a flash inside the clouds when `bolt` is false) at the given point. */
    static void strike(double x, double y, double z, boolean bolt) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dist = Math.sqrt(cam.distanceToSqr(x, y, z));
        LightningFx.flash(dist, bolt);
        if (!ClientConfig.SOUNDS.get()) {
            return;
        }
        int delay = (int) Math.round(dist / 17.15);
        SoundEvent event;
        float volume;
        if (dist < 60 && bolt) {
            // A strike right next to you: an explosive crack with no gap before the boom.
            event = ModContent.THUNDER_STRIKE.get();
            volume = 1F;
            LightningFx.shakeLater(delay, 1F);
        } else if (dist < 150) {
            event = ModContent.THUNDER_CLOSE.get();
            volume = 1F;
            LightningFx.shakeLater(delay, (float) (1.0 - dist / 150.0));
        } else if (dist < 650) {
            event = ModContent.THUNDER_MID.get();
            volume = (float) (0.95 - (dist - 150) / 500 * 0.3);
        } else {
            event = ModContent.THUNDER_FAR.get();
            volume = (float) Math.max(0.22, 0.7 - (dist - 650) / 5000);
        }
        float pitch = 0.88F + RANDOM.nextFloat() * 0.2F;
        SimpleSoundInstance inst = new SimpleSoundInstance(event.getLocation(), SoundSource.WEATHER, volume, pitch, RANDOM,
                false, 0, SoundInstance.Attenuation.NONE, x, y, z, false);
        if (delay <= 0) {
            mc.getSoundManager().play(inst);
        } else {
            mc.getSoundManager().playDelayed(inst, delay);
        }
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    /** An ambient loop that follows the listener and glides to its target volume. */
    static final class Loop extends AbstractTickableSoundInstance {
        float target;
        private int age;
        private int silent;

        Loop(SoundEvent event) {
            super(event, SoundSource.WEATHER, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001F;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }

        boolean justStarted() {
            return age < 5;
        }

        @Override
        public void tick() {
            age++;
            volume += (target - volume) * 0.06F;
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
