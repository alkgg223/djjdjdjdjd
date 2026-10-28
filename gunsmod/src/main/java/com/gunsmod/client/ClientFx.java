package com.gunsmod.client;

import com.gunsmod.Reg;
import com.gunsmod.client.particle.BlastParticles;
import com.gunsmod.world.Blast;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side explosion effects: flash, fireball, ground dust ring, shockwave, smoke column, block debris, embers,
 * lingering crater smoke and the Titan's mushroom cloud, plus camera shake, screen flash and sound that arrives late
 * over distance.
 */
public final class ClientFx {
    private ClientFx() {}

    /** an effect that keeps emitting for a while; returns false when finished */
    private interface Emitter {
        boolean tick(ClientLevel level, int age);
    }

    private static final List<Emitter> EMITTERS = new ArrayList<>();
    private static final List<int[]> AGES = new ArrayList<>();
    /** camera shake strength (degrees) and white screen flash (0..1), both decay every tick */
    public static float shake, flash;
    /** recent blasts (decays each tick): a salvo of many missiles thins each blast's particles so phones keep up */
    private static float load;
    private static final int MAX_EMITTERS = 48;

    private static float quality(Minecraft mc) {
        ParticleStatus s = mc.options.particles().get();
        return s == ParticleStatus.MINIMAL ? 0.3f : s == ParticleStatus.DECREASED ? 0.55f : 1f;
    }

    private static void add(Particle p) {
        Minecraft.getInstance().particleEngine.add(p);
    }

    private static void emit(Emitter e) {
        if (EMITTERS.size() >= MAX_EMITTERS) return;
        EMITTERS.add(e);
        AGES.add(new int[1]);
    }

    public static void tick() {
        shake *= 0.86f;
        if (shake < 0.02f) shake = 0;
        flash = Math.max(0, flash - 0.035f);
        load *= 0.95f;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            EMITTERS.clear();
            AGES.clear();
            return;
        }
        Iterator<Emitter> it = EMITTERS.iterator();
        Iterator<int[]> ages = AGES.iterator();
        while (it.hasNext()) {
            Emitter e = it.next();
            int[] age = ages.next();
            if (!e.tick(level, age[0]++)) {
                it.remove();
                ages.remove();
            }
        }
    }

    // ------------------------------------------------------------------------------------ blast
    public static void blast(Vec3 pos, int style, float size) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || BlastParticles.fireballSprites == null) return;
        RandomSource r = level.random;
        float q = quality(mc) / (1f + Math.max(0f, load - 3f) * 0.35f);
        load += 1f;
        boolean fire = style == Blast.FX_FIRE, titan = style == Blast.FX_TITAN, eruption = style == Blast.FX_ERUPTION;
        double x = pos.x, y = pos.y, z = pos.z;
        int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        double ground = gy > y + 3 || gy < y - 6 ? y : gy;
        BlockState soil = level.getBlockState(BlockPos.containing(x, ground - 1, z));
        if (soil.isAir()) soil = Blocks.DIRT.defaultBlockState();
        int mapCol = soil.getMapColor(level, BlockPos.containing(x, ground - 1, z)).col;
        float sr = ((mapCol >> 16) & 255) / 255f, sg = ((mapCol >> 8) & 255) / 255f, sb = (mapCol & 255) / 255f;
        // earthy dust: ground colour pulled towards grey-brown
        float dr = Mth.lerp(0.55f, sr, 0.55f), dg = Mth.lerp(0.55f, sg, 0.5f), db = Mth.lerp(0.55f, sb, 0.44f);

        // flash
        add(new BlastParticles.Flash(level, x, y + 0.5, z, BlastParticles.flashSprites, 2.5f * size, titan ? 10 : 4));
        // fireball
        int nFire = (int) (8 * size * size * q * (fire ? 1.6f : 1f)) + 3;
        for (int i = 0; i < nFire; i++) {
            Vec3 d = randomDir(r, eruption ? 0.9 : 0.35);
            double sp = (0.05 + r.nextDouble() * 0.22) * size;
            add(new BlastParticles.Fireball(level, x + d.x * 0.3 * size, y + d.y * 0.3 * size + 0.3, z + d.z * 0.3 * size,
                    d.x * sp, d.y * sp * (eruption ? 1.8 : 1), d.z * sp, BlastParticles.fireballSprites,
                    (0.7f + r.nextFloat() * 1.1f) * size, 12 + r.nextInt(14) + (int) (size * 3)));
        }
        // ground dust ring rolling outward
        int nRing = (int) ((10 + 16 * size) * q);
        for (int i = 0; i < nRing; i++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            double sp = (0.25 + r.nextDouble() * 0.5) * Math.sqrt(size);
            float g = 0.85f + r.nextFloat() * 0.25f;
            add(new BlastParticles.DustCloud(level, x + Math.cos(a) * 0.6 * size, ground + 0.4 + r.nextDouble() * 0.6 * size, z + Math.sin(a) * 0.6 * size,
                    Math.cos(a) * sp, 0.01 + r.nextDouble() * 0.04, Math.sin(a) * sp, BlastParticles.dustSprites,
                    (0.9f + r.nextFloat()) * size, (int) ((70 + r.nextInt(70)) * (0.6f + 0.25f * size)), dr * g, dg * g, db * g, 0.035f * size, 0.002f));
        }
        // shockwave ring
        add(new BlastParticles.Shockwave(level, x, ground + 0.12, z, BlastParticles.shockwaveSprites, 6f * size, 9 + (int) (2 * size)));
        // dark smoke column
        int nCol = (int) ((4 + 6 * size) * q);
        for (int i = 0; i < nCol; i++) {
            float g = fire ? 0.12f + r.nextFloat() * 0.1f : 0.22f + r.nextFloat() * 0.15f;
            add(new BlastParticles.DustCloud(level, x + (r.nextDouble() - 0.5) * size, y + 0.5, z + (r.nextDouble() - 0.5) * size,
                    (r.nextDouble() - 0.5) * 0.15, (0.12 + r.nextDouble() * 0.3) * Math.sqrt(size), (r.nextDouble() - 0.5) * 0.15,
                    BlastParticles.dustSprites, (1.0f + r.nextFloat()) * size, 120 + r.nextInt(100), g, g * 0.95f, g * 0.9f, 0.04f * size, 0.006f));
        }
        // block debris thrown out of the crater
        int nDeb = (int) (22 * size * q * (eruption ? 2f : 1f));
        for (int i = 0; i < nDeb; i++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            double h = (0.1 + r.nextDouble() * 0.5) * Math.sqrt(size) * (eruption ? 0.5 : 1);
            double up = (0.35 + r.nextDouble() * 0.8) * Math.sqrt(size) * (eruption ? 1.5 : 1);
            BlockPos bp = BlockPos.containing(x, ground - 1, z);
            TerrainParticle p = new TerrainParticle(level, x, ground + 0.3, z, 0, 0, 0, soil).updateSprite(soil, bp);
            p.setParticleSpeed(Math.cos(a) * h, up, Math.sin(a) * h);
            p.scale(1.5f + r.nextFloat() * 2.5f);
            p.setLifetime(40 + r.nextInt(50));
            add(p);
        }
        // embers
        int nEmb = (int) (14 * size * q * (fire ? 2f : 1f));
        for (int i = 0; i < nEmb; i++) {
            Vec3 d = randomDir(r, 0.5);
            double sp = (0.2 + r.nextDouble() * 0.6) * Math.sqrt(size);
            add(new BlastParticles.Ember(level, x, y + 0.5, z, d.x * sp, d.y * sp + 0.2, d.z * sp, BlastParticles.emberSprites));
        }
        // lingering crater smoke
        if (size >= 1.2f) {
            int dur = (int) (60 * size);
            float gq = q;
            double cx = x, cz = z, cy = ground;
            emit((lvl, age) -> {
                if (age % 3 == 0 && lvl.random.nextFloat() < gq) {
                    RandomSource rr = lvl.random;
                    float g = 0.16f + rr.nextFloat() * 0.12f;
                    add(new BlastParticles.DustCloud(lvl, cx + (rr.nextDouble() - 0.5) * size * 2, cy + 0.3, cz + (rr.nextDouble() - 0.5) * size * 2,
                            (rr.nextDouble() - 0.5) * 0.04, 0.05 + rr.nextDouble() * 0.06, (rr.nextDouble() - 0.5) * 0.04,
                            BlastParticles.dustSprites, (0.8f + rr.nextFloat()) * size * 0.7f, 140 + rr.nextInt(80), g, g, g * 0.95f, 0.03f * size, 0.004f));
                }
                return age < dur;
            });
        }
        if (titan) mushroom(x, ground, z, size, q);
        // camera shake, flash and sound arrive with distance
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dist = cam.distanceTo(pos);
        if (dist < 90 * size) shake = Math.max(shake, Math.min(4f, (float) (size * 1.3 / (1 + dist / (9 * size)))));
        if (titan && dist < 260) flash = Math.max(flash, (float) (1 - dist / 300));
        int delay = (int) (dist / 17.0);
        if (dist < 32 * size) {
            mc.getSoundManager().playDelayed(new SimpleSoundInstance(Reg.sound("explosion_big"), SoundSource.BLOCKS, Math.max(1f, size * 2.2f),
                    0.85f + r.nextFloat() * 0.2f - (titan ? 0.25f : 0f), r, x, y, z), delay);
        } else if (dist < 220 * size) {
            float vol = (float) Mth.clamp(1.2 - dist / (220 * size), 0.15, 1.0);
            mc.getSoundManager().playDelayed(new SimpleSoundInstance(Reg.sound("explosion_far"), SoundSource.BLOCKS, vol,
                    0.8f + r.nextFloat() * 0.2f, r, cam.x, cam.y, cam.z), delay);
        }
    }

    /** a tank gun or rocket pod firing: fireball along the barrel, a smoke ring, dust kicked up off the ground */
    public static void muzzleBlast(Vec3 pos, Vec3 dir, float size) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || BlastParticles.fireballSprites == null) return;
        RandomSource r = level.random;
        float q = quality(mc);
        Vec3 d = dir.lengthSqr() > 1e-6 ? dir.normalize() : new Vec3(0, 0, 1);
        add(new BlastParticles.Flash(level, pos.x, pos.y, pos.z, BlastParticles.flashSprites, 1.4f * size, 3));
        int nFire = (int) (10 * size * q) + 2;
        for (int i = 0; i < nFire; i++) {
            double sp = (0.15 + r.nextDouble() * 0.55) * size;
            add(new BlastParticles.Fireball(level, pos.x, pos.y, pos.z, d.x * sp + r.nextGaussian() * 0.05, d.y * sp + r.nextGaussian() * 0.05,
                    d.z * sp + r.nextGaussian() * 0.05, BlastParticles.fireballSprites, (0.4f + r.nextFloat() * 0.5f) * size, 4 + r.nextInt(5)));
        }
        // smoke ring around the muzzle, drifting forward
        Vec3 side = Math.abs(d.y) < 0.95 ? d.cross(new Vec3(0, 1, 0)).normalize() : new Vec3(1, 0, 0);
        Vec3 up = side.cross(d).normalize();
        int nRing = (int) (14 * size * q) + 3;
        for (int i = 0; i < nRing; i++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            Vec3 out = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            double sp = 0.12 * size;
            float g = 0.55f + r.nextFloat() * 0.15f;
            add(new BlastParticles.DustCloud(level, pos.x, pos.y, pos.z, out.x * sp + d.x * 0.18 * size, out.y * sp + d.y * 0.18 * size + 0.01,
                    out.z * sp + d.z * 0.18 * size, BlastParticles.dustSprites, (0.6f + r.nextFloat() * 0.5f) * size, 50 + r.nextInt(40),
                    g, g * 0.97f, g * 0.92f, 0.02f * size, 0.001f));
        }
        if (size >= 1f) {
            // the pressure wave lifts dust off the ground below the gun
            int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
            if (pos.y - gy < 4) {
                BlockState soil = level.getBlockState(BlockPos.containing(pos.x, gy - 1, pos.z));
                int col = soil.isAir() ? 0x8a7a5a : soil.getMapColor(level, BlockPos.containing(pos.x, gy - 1, pos.z)).col;
                float sr = ((col >> 16) & 255) / 255f, sg = ((col >> 8) & 255) / 255f, sb = (col & 255) / 255f;
                int nDust = (int) (16 * size * q);
                for (int i = 0; i < nDust; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double sp = 0.2 + r.nextDouble() * 0.35;
                    float g = 0.75f + r.nextFloat() * 0.2f;
                    add(new BlastParticles.DustCloud(level, pos.x + Math.cos(a) * 1.5 + d.x * 2, gy + 0.3, pos.z + Math.sin(a) * 1.5 + d.z * 2,
                            Math.cos(a) * sp + d.x * 0.3, 0.02, Math.sin(a) * sp + d.z * 0.3, BlastParticles.dustSprites,
                            (0.9f + r.nextFloat()) * size, 60 + r.nextInt(50), Mth.lerp(0.5f, sr, 0.55f) * g, Mth.lerp(0.5f, sg, 0.5f) * g,
                            Mth.lerp(0.5f, sb, 0.44f) * g, 0.03f * size, 0.002f));
                }
            }
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            double dist = cam.distanceTo(pos);
            if (dist < 30) shake = Math.max(shake, (float) (size * 1.2 * (1 - dist / 30)));
        }
    }

    /** the Titan's mushroom cloud: a rising fire column that spreads into a cap and slowly stains dark */
    private static void mushroom(double x, double ground, double z, float size, float q) {
        double top = ground + 18 + size * 5;
        emit((lvl, age) -> {
            RandomSource r = lvl.random;
            if (age < 70 && r.nextFloat() < q) {
                // stem: fire low down, dirty smoke higher up
                for (int i = 0; i < 3; i++) {
                    double h = Math.min(1, age / 50.0) * (top - ground) * r.nextDouble();
                    double rad = 1.5 + h * 0.08;
                    double a = r.nextDouble() * Mth.TWO_PI;
                    float g = 0.3f + r.nextFloat() * 0.15f;
                    if (age < 30 && h < (top - ground) * 0.6) {
                        add(new BlastParticles.Fireball(lvl, x + Math.cos(a) * rad, ground + h, z + Math.sin(a) * rad, 0, 0.25, 0,
                                BlastParticles.fireballSprites, 3f + r.nextFloat() * 2f, 30 + r.nextInt(20)));
                    } else {
                        add(new BlastParticles.DustCloud(lvl, x + Math.cos(a) * rad, ground + h, z + Math.sin(a) * rad, 0, 0.12, 0,
                                BlastParticles.dustSprites, 3.5f + r.nextFloat() * 2f, 260 + r.nextInt(120), g * 1.1f, g, g * 0.9f, 0.03f, 0.0f));
                    }
                }
            }
            if (age >= 25 && age < 110 && r.nextFloat() < q) {
                // cap: a doughnut rolling outward at the top of the column
                double grow = Math.min(1, (age - 25) / 60.0);
                for (int i = 0; i < 3; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rad = 3 + grow * (10 + size * 2.5) * (0.6 + r.nextDouble() * 0.4);
                    double h = top + (r.nextDouble() - 0.3) * 6 * (1 - grow * 0.3);
                    float g = age < 50 ? 0.55f : 0.32f + r.nextFloat() * 0.12f;
                    boolean hot = age < 45 && r.nextInt(3) == 0;
                    if (hot) {
                        add(new BlastParticles.Fireball(lvl, x + Math.cos(a) * rad, h, z + Math.sin(a) * rad, Math.cos(a) * 0.1, 0.05, Math.sin(a) * 0.1,
                                BlastParticles.fireballSprites, 4f + r.nextFloat() * 3f, 40 + r.nextInt(20)));
                    } else {
                        add(new BlastParticles.DustCloud(lvl, x + Math.cos(a) * rad, h, z + Math.sin(a) * rad, Math.cos(a) * 0.08, 0.02, Math.sin(a) * 0.08,
                                BlastParticles.dustSprites, 5f + r.nextFloat() * 4f, 320 + r.nextInt(160), g * 1.1f, g, g * 0.92f, 0.03f, 0.0f));
                    }
                }
            }
            return age < 110;
        });
    }

    private static Vec3 randomDir(RandomSource r, double upBias) {
        double a = r.nextDouble() * Mth.TWO_PI;
        double y = r.nextDouble() * (1 + upBias) - 1 + upBias;
        y = Mth.clamp(y, -0.3, 1);
        double h = Math.sqrt(Math.max(0, 1 - y * y));
        return new Vec3(Math.cos(a) * h, y, Math.sin(a) * h);
    }
}
