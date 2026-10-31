package com.realweather.client.tornado;

import com.realweather.ModContent;
import com.realweather.ClientConfig;
import com.realweather.client.ClientWeather;
import com.realweather.net.TornadoSyncPayload;
import com.realweather.server.Tornado;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client copy of the tornadoes, with the shared funnel shape used by the renderer, particles and sounds. */
public final class ClientTornadoes {
    private static final Map<Integer, T> TORNADOES = new LinkedHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private ClientTornadoes() {}

    public static final class T {
        public final int id;
        public final int rating;
        public final long seed;
        public double x, z, px, pz;
        private double sx, sz;
        private int sinceSync;
        public float groundY;
        public float heading, speed;
        public int stage;
        public float contact, strength;
        /** Accumulated rotation of the vortex, in turns, for texture spinning. */
        public float spin, pSpin;
        public int age;
        final TornadoSounds sounds = new TornadoSounds();

        T(TornadoSyncPayload.Entry e) {
            this.id = e.id();
            this.rating = e.rating();
            this.seed = e.seed();
            this.x = this.px = this.sx = e.x();
            this.z = this.pz = this.sz = e.z();
            apply(e);
        }

        void apply(TornadoSyncPayload.Entry e) {
            sx = e.x();
            sz = e.z();
            sinceSync = 0;
            groundY = e.groundY();
            heading = e.heading();
            speed = e.speed();
            stage = e.stage();
            contact = e.contact();
            strength = e.strength();
        }

        public double x(float pt) {
            return Mth.lerp(pt, px, x);
        }

        public double z(float pt) {
            return Mth.lerp(pt, pz, z);
        }

        public float spin(float pt) {
            return Mth.lerp(pt, pSpin, spin);
        }

        public float coreRadius() {
            return Tornado.RADIUS[rating] * (0.35F + 0.65F * strength);
        }

        /** Height of the funnel, ground to cloud base: violent wedges hang from a much lower cloud base. */
        public float height() {
            return HEIGHTS[rating];
        }

        private static final float[] HEIGHTS = {84F, 84F, 80F, 76F, 70F, 64F};

        public float cloudBase() {
            return groundY + height();
        }

        /** Funnel radius at a height fraction f (0 = ground, 1 = cloud base): ropes, cones and wedges. */
        public float radiusAt(float f) {
            float bottom;
            float top;
            float expo;
            if (rating <= 1) {
                bottom = 0.45F;
                top = 2.8F;
                expo = 2.4F;
            } else if (rating <= 3) {
                bottom = 0.75F;
                top = 2.4F;
                expo = 1.9F;
            } else {
                bottom = 1.05F;
                top = 1.75F;
                expo = 2.8F;
            }
            if (stage == Tornado.DISSIPATING) {
                // Roping out: the column thins and twists before it lifts.
                bottom *= 0.3F + 0.7F * strength;
            }
            return coreRadius() * (bottom + (top - bottom) * (float) Math.pow(f, expo));
        }

        /** Sideways offset of the funnel centre at height fraction f; ropes snake, wedges barely lean. */
        public float offsetX(float f, float time) {
            return offset(f, time, 0F);
        }

        public float offsetZ(float f, float time) {
            return offset(f, time, 1.7F);
        }

        private float offset(float f, float time, float phase) {
            float rope = rating <= 1 ? 1F : rating <= 3 ? 0.5F : 0.2F;
            if (stage == Tornado.DISSIPATING) {
                rope += (1F - strength) * 1.5F;
            }
            float amp = (1.5F + 7F * rope) * (float) Math.pow(f, 1.3) + 0.6F;
            float s = (seed & 1023) * 0.01F;
            float wave = Mth.sin(time * 0.021F + f * 3.1F + s + phase) * amp + Mth.sin(time * 0.047F + f * 7.3F + s * 2 + phase) * amp * 0.25F;
            // The top runs ahead of the ground contact, which drags behind.
            float lean = f * (6F + rating * 2F) * (phase == 0F ? Mth.sin(heading) : -Mth.cos(heading));
            return wave + lean;
        }

        /** Wind speed around the vortex at distance r from the centre (blocks per tick). */
        public float windAt(float r) {
            float rc = Math.max(1.5F, coreRadius());
            float vmax = Tornado.WIND[rating] * strength * contact;
            return r < rc ? vmax * r / rc : vmax * (float) Math.pow(rc / r, 0.7);
        }
    }

    public static Collection<T> all() {
        return TORNADOES.values();
    }

    public static T get(int id) {
        return TORNADOES.get(id);
    }

    public static boolean isEmpty() {
        return TORNADOES.isEmpty();
    }

    public static void onSync(TornadoSyncPayload payload) {
        List<Integer> seen = new ArrayList<>();
        for (TornadoSyncPayload.Entry e : payload.tornadoes()) {
            T t = TORNADOES.get(e.id());
            if (t == null) {
                TORNADOES.put(e.id(), new T(e));
            } else {
                t.apply(e);
            }
            seen.add(e.id());
        }
        TORNADOES.entrySet().removeIf(en -> {
            if (!seen.contains(en.getKey())) {
                en.getValue().sounds.stop();
                return true;
            }
            return false;
        });
    }

    public static void clear() {
        TORNADOES.values().forEach(t -> t.sounds.stop());
        TORNADOES.clear();
    }

    /** Client tick: move, spin, spawn debris and update the sounds. */
    public static void tick(Minecraft mc, ClientLevel level) {
        if (TORNADOES.isEmpty()) {
            return;
        }
        if (!ClientWeather.active(level)) {
            TORNADOES.values().forEach(t -> t.sounds.stop());
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (T t : TORNADOES.values()) {
            t.age++;
            t.px = t.x;
            t.pz = t.z;
            t.sinceSync++;
            // Dead reckoning between server updates, gently pulled back to the server position.
            double ex = t.sx + Math.sin(t.heading) * t.speed * t.sinceSync;
            double ez = t.sz - Math.cos(t.heading) * t.speed * t.sinceSync;
            t.x += Math.sin(t.heading) * t.speed + (ex - t.x) * 0.2;
            t.z += -Math.cos(t.heading) * t.speed + (ez - t.z) * 0.2;
            t.pSpin = t.spin;
            // Visual rotation: one turn takes 2*pi*r/v ticks at the core edge (sped up a bit to read well).
            float rc = Math.max(2F, t.coreRadius());
            t.spin += Math.max(0.004F, t.windAt(rc)) / (2F * (float) Math.PI * rc) * 2.5F;

            double dx = cam.x - t.x;
            double dz = cam.z - t.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            t.sounds.tick(mc, t, dist, cam.y);
            if (dist < 420) {
                spawnDebris(mc, level, t, dist);
            }
            // The ground shakes near a big one.
            float shake = t.contact * t.strength * (t.rating + 1) / 6F * (float) Mth.clamp(1.0 - dist / (rc * 5 + 25), 0.0, 1.0);
            com.realweather.client.LightningFx.rumble(shake * 0.5F);
        }
    }

    private static void spawnDebris(Minecraft mc, ClientLevel level, T t, double dist) {
        int q = ClientConfig.tornadoQuality();
        float amount = (0.5F + t.rating * 0.18F) * t.contact * (0.3F + 0.7F * t.strength);
        if (dist > 220) {
            amount *= 0.5F;
        }
        int dust = Math.round(new int[]{1, 3, 6, 10}[q] * amount);
        int chunks = Math.round(new int[]{0, 3, 7, 14}[q] * amount);
        float rc = Math.max(2F, t.coreRadius());
        for (int i = 0; i < dust; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = rc * (0.6 + RANDOM.nextDouble() * 1.4);
            double x = t.x + Math.cos(a) * r;
            double z = t.z + Math.sin(a) * r;
            double y = t.groundY + RANDOM.nextDouble() * (2 + t.rating * 1.5);
            level.addParticle(ModContent.TORNADO_DUST.get(), x, y, z, t.id, 0, 0);
        }
        for (int i = 0; i < chunks; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = rc * Math.sqrt(RANDOM.nextDouble()) * 1.3;
            int bx = Mth.floor(t.x + Math.cos(a) * r);
            int bz = Mth.floor(t.z + Math.sin(a) * r);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            BlockState state = level.getBlockState(new BlockPos(bx, top - 1, bz));
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                state = Blocks.DIRT.defaultBlockState();
            }
            mc.particleEngine.add(new TornadoDebrisParticle(level, bx + 0.5, top + 0.2, bz + 0.5, t.id, state));
        }
    }
}
