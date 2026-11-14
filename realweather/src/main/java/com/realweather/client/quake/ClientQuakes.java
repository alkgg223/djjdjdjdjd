package com.realweather.client.quake;

import com.realweather.ClientConfig;
import com.realweather.ModContent;
import com.realweather.client.LightningFx;
import com.realweather.net.QuakeSyncPayload;
import com.realweather.server.Earthquake;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client copy of the earthquakes: the same cracks as the server (rebuilt from the seed), the ground shaking under the
 * camera, the deep rumble and the cracking of the ground, dust falling from ceilings and puffing up along the cracks
 * as they open.
 */
public final class ClientQuakes {
    private static final Map<Integer, Q> QUAKES = new LinkedHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private ClientQuakes() {}

    public static final class Q {
        public final Earthquake q;
        /** Ground height under each crack point, looked up now and then (the ground opens under them). */
        float[][] ground;
        int groundAge = -1000;
        Rumble rumble;

        Q(Earthquake q) {
            this.q = q;
        }
    }

    public static Collection<Q> all() {
        return QUAKES.values();
    }

    public static boolean isEmpty() {
        return QUAKES.isEmpty();
    }

    public static void onSync(QuakeSyncPayload p) {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (QuakeSyncPayload.Entry e : p.quakes()) {
            Q w = QUAKES.get(e.id());
            if (w == null) {
                w = new Q(new Earthquake(e.id(), e.x(), e.z(), e.magnitude(), e.seed(), e.fault()));
                QUAKES.put(e.id(), w);
            }
            if (Math.abs(w.q.age - e.age()) > 10) {
                w.q.age = e.age();
            }
            seen.add(e.id());
        }
        QUAKES.entrySet().removeIf(en -> {
            if (!seen.contains(en.getKey())) {
                if (en.getValue().rumble != null) {
                    en.getValue().rumble.halt();
                }
                return true;
            }
            return false;
        });
    }

    public static void clear() {
        for (Q w : QUAKES.values()) {
            if (w.rumble != null) {
                w.rumble.halt();
            }
        }
        QUAKES.clear();
    }

    public static void tick(Minecraft mc, ClientLevel level) {
        if (QUAKES.isEmpty() || mc.player == null) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Q w : QUAKES.values()) {
            Earthquake q = w.q;
            int before = q.age;
            q.age++;
            float i = q.intensityAt(cam.x, cam.z);
            if (q.shaking() && i > 1.2F) {
                // The ground moving under you: weak quakes a tremble, great ones throw the view about.
                LightningFx.rumble(Mth.clamp((i - 1.2F) / 7.5F, 0F, 1F));
            }
            sounds(mc, w, cam, i);
            if (q.shaking()) {
                dust(mc, level, w, cam, i, before);
            }
            if (q.age - w.groundAge > 30 || w.ground == null) {
                w.groundAge = q.age;
                heights(level, w);
            }
        }
    }

    /** Ground height under every crack point (drawn just above it). */
    private static void heights(ClientLevel level, Q w) {
        java.util.List<Earthquake.Crack> cracks = w.q.cracks();
        if (w.ground == null) {
            w.ground = new float[cracks.size()][];
        }
        for (int c = 0; c < cracks.size(); c++) {
            Earthquake.Crack cr = cracks.get(c);
            if (w.ground[c] == null) {
                w.ground[c] = new float[cr.n];
            }
            for (int i = 0; i < cr.n; i++) {
                int x = Mth.floor(cr.xs[i]), z = Mth.floor(cr.zs[i]);
                w.ground[c][i] = level.hasChunk(x >> 4, z >> 4) ? com.realweather.server.TsunamiGround.top(level, x, z) : Float.NaN;
            }
        }
    }

    private static void sounds(Minecraft mc, Q w, Vec3 cam, float i) {
        if (!ClientConfig.SOUNDS.get()) {
            return;
        }
        Earthquake q = w.q;
        float vol = q.shaking() ? Mth.clamp((i - 0.8F) / 6F, 0F, 1.4F) : 0F;
        if (w.rumble == null && vol > 0.02F) {
            w.rumble = new Rumble();
            mc.getSoundManager().play(w.rumble);
        }
        if (w.rumble != null) {
            w.rumble.target = vol;
            w.rumble.place(cam.x, cam.y - 2, cam.z);
            w.rumble.pitch(Mth.clamp(1.1F - q.magnitude * 0.05F, 0.6F, 1.1F));
        }
        // The ground splitting nearby.
        if (q.shaking() && q.magnitude >= 4.5F && RANDOM.nextFloat() < Math.min(0.12F, (i - 3F) * 0.02F)) {
            double a = RANDOM.nextDouble() * Math.PI * 2, d = 6 + RANDOM.nextDouble() * 30;
            mc.getSoundManager().play(new SimpleSoundInstance(ModContent.QUAKE_CRACK.get(), SoundSource.WEATHER, Math.min(1F, (i - 3F) / 5F),
                    0.75F + RANDOM.nextFloat() * 0.5F, RANDOM, cam.x + Math.cos(a) * d, cam.y - 1, cam.z + Math.sin(a) * d));
        }
    }

    /** Dust and grit shaken down from ceilings, and puffs where the cracks open. */
    private static void dust(Minecraft mc, ClientLevel level, Q w, Vec3 cam, float i, int before) {
        int quality = ClientConfig.quality();
        if (i > 3.5F) {
            int n = Math.round(Math.min(10F, (i - 3.5F) * 1.5F) * new float[]{0.3F, 0.6F, 1F, 1.4F}[quality]);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int k = 0; k < n; k++) {
                int x = Mth.floor(cam.x + RANDOM.nextGaussian() * 6), z = Mth.floor(cam.z + RANDOM.nextGaussian() * 6);
                for (int y = Mth.floor(cam.y) + 1; y < cam.y + 8; y++) {
                    BlockState s = level.getBlockState(m.set(x, y, z));
                    if (!s.isAir()) {
                        if (s.isSolidRender(level, m) && level.getBlockState(m.set(x, y - 1, z)).isAir()) {
                            level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, s), x + RANDOM.nextDouble(), y - 0.05,
                                    z + RANDOM.nextDouble(), 0, 0, 0);
                        }
                        break;
                    }
                }
            }
        }
        // Along the cracks that just opened near the camera.
        java.util.List<Earthquake.Crack> cracks = w.q.cracks();
        if (w.ground == null) {
            return;
        }
        for (int c = 0; c < cracks.size() && c < w.ground.length; c++) {
            Earthquake.Crack cr = cracks.get(c);
            for (int k = 0; k < cr.n; k += 2) {
                if (cr.openAt[k] > before && cr.openAt[k] <= w.q.age && cr.width[k] > 0.12F && w.ground[c] != null) {
                    double dx = cr.xs[k] - cam.x, dz = cr.zs[k] - cam.z;
                    if (dx * dx + dz * dz > 80 * 80 || Float.isNaN(w.ground[c][k])) {
                        continue;
                    }
                    BlockState s = level.getBlockState(BlockPos.containing(cr.xs[k], w.ground[c][k] - 1, cr.zs[k]));
                    if (s.isAir()) {
                        continue;
                    }
                    int n = cr.width[k] > 0.5F ? 4 : 1;
                    for (int j = 0; j < n; j++) {
                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, s), cr.xs[k] + RANDOM.nextGaussian() * 0.4, w.ground[c][k] + 0.1,
                                cr.zs[k] + RANDOM.nextGaussian() * 0.4, 0, 0.15 + RANDOM.nextDouble() * 0.2, 0);
                    }
                    if (cr.width[k] > 0.5F) {
                        level.addParticle(ModContent.TORNADO_DUST.get(), true, cr.xs[k], w.ground[c][k] + 0.3, cr.zs[k], (RANDOM.nextDouble() - 0.5) * 0.05,
                                0.03, (RANDOM.nextDouble() - 0.5) * 0.05);
                    }
                }
            }
        }
    }

    /** The rumble: a loop that follows the camera and swells with the shaking. */
    static final class Rumble extends AbstractTickableSoundInstance {
        float target;

        Rumble() {
            super(ModContent.QUAKE_RUMBLE.get(), SoundSource.WEATHER, SoundInstance.createUnseededRandom());
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

        void pitch(float p) {
            this.pitch = p;
        }

        @Override
        public void tick() {
            volume += (target - volume) * 0.08F;
            if (target <= 0F && volume < 0.01F) {
                stop();
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
