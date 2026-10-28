package com.gunsmod.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Particles for big explosions: fireballs, billowing dust and smoke, a ground shockwave ring, embers and the initial
 * flash. The providers keep their sprite sets so {@code ClientFx} can build particles with custom size, colour and life.
 */
public final class BlastParticles {
    private BlastParticles() {}

    public static SpriteSet fireballSprites, dustSprites, shockwaveSprites, emberSprites, flashSprites;

    // ------------------------------------------------------------------------------------ fireball
    /** rolling fire that burns out into soot; size in blocks */
    public static class Fireball extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final float startSize, endSize;

        public Fireball(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s, float size, int life) {
            super(l, x, y, z);
            sprites = s;
            xd = dx;
            yd = dy;
            zd = dz;
            lifetime = life;
            startSize = size * 0.5f;
            endSize = size;
            quadSize = startSize;
            friction = 0.86f;
            gravity = -0.01f;
            hasPhysics = false;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            setSpriteFromAge(s);
        }

        @Override
        public void tick() {
            super.tick();
            if (!isAlive()) return;
            setSpriteFromAge(sprites);
            float f = (float) age / lifetime;
            quadSize = Mth.lerp(Math.min(1f, f * 2.5f), startSize, endSize);
            alpha = f < 0.7f ? 1f : 1f - (f - 0.7f) / 0.3f;
            oRoll = roll;
            roll += 0.02f;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public AABB getRenderBoundingBox(float pt) {
            return getBoundingBox().inflate(quadSize);
        }

        @Override
        protected int getLightColor(float pt) {
            float f = (age + pt) / lifetime;
            return f < 0.55f ? LightTexture.FULL_BRIGHT : super.getLightColor(pt);
        }
    }

    // ------------------------------------------------------------------------------------ dust / smoke cloud
    /** a slowly growing, drifting cloud. Tinted (earth, soot, white smoke); fades in, lingers, fades out */
    public static class DustCloud extends TextureSheetParticle {
        private final float grow;
        private final float maxAlpha;

        public DustCloud(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s,
                         float size, int life, float r, float g, float b, float grow, float rise) {
            super(l, x, y, z);
            pickSprite(s);
            xd = dx;
            yd = dy;
            zd = dz;
            lifetime = life;
            quadSize = size;
            this.grow = grow;
            rCol = r;
            gCol = g;
            bCol = b;
            maxAlpha = 0.6f + random.nextFloat() * 0.3f;
            alpha = 0;
            friction = 0.93f;
            gravity = -rise;
            hasPhysics = false;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        }

        @Override
        public void tick() {
            super.tick();
            if (!isAlive()) return;
            float f = (float) age / lifetime;
            quadSize += grow * (1f - f);
            alpha = maxAlpha * Math.min(1f, f * 8f) * (f < 0.55f ? 1f : 1f - (f - 0.55f) / 0.45f);
            oRoll = roll;
            roll += 0.004f;
        }

        @Override
        public AABB getRenderBoundingBox(float pt) {
            return getBoundingBox().inflate(quadSize);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------------------------------ shockwave ring
    /** flat ring racing outward along the ground */
    public static class Shockwave extends TextureSheetParticle {
        private final float maxRadius;

        public Shockwave(ClientLevel l, double x, double y, double z, SpriteSet s, float radius, int life) {
            super(l, x, y, z);
            pickSprite(s);
            lifetime = life;
            maxRadius = radius;
            hasPhysics = false;
            gravity = 0;
            xd = yd = zd = 0;
            alpha = 0.9f;
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) remove();
        }

        @Override
        public void render(VertexConsumer vc, Camera cam, float pt) {
            float f = Mth.clamp((age + pt) / lifetime, 0f, 1f);
            float r = maxRadius * (float) Math.sqrt(f) + 0.5f;
            float a = alpha * (1f - f) * (1f - f);
            Vec3 c = cam.getPosition();
            float px = (float) (x - c.x), py = (float) (y - c.y), pz = (float) (z - c.z);
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            int light = LightTexture.FULL_BRIGHT;
            // both windings so it is visible from above and below
            vertex(vc, px - r, py, pz - r, u0, v0, a, light);
            vertex(vc, px - r, py, pz + r, u0, v1, a, light);
            vertex(vc, px + r, py, pz + r, u1, v1, a, light);
            vertex(vc, px + r, py, pz - r, u1, v0, a, light);
            vertex(vc, px + r, py, pz - r, u1, v0, a, light);
            vertex(vc, px + r, py, pz + r, u1, v1, a, light);
            vertex(vc, px - r, py, pz + r, u0, v1, a, light);
            vertex(vc, px - r, py, pz - r, u0, v0, a, light);
        }

        private void vertex(VertexConsumer vc, float x, float y, float z, float u, float v, float a, int light) {
            vc.addVertex(x, y, z).setUv(u, v).setColor(1f, 0.92f, 0.8f, a).setLight(light);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public AABB getRenderBoundingBox(float pt) {
            return new AABB(x - maxRadius, y - 1, z - maxRadius, x + maxRadius, y + 1, z + maxRadius);
        }
    }

    // ------------------------------------------------------------------------------------ ember
    /** glowing chunk flung out of the blast, falls and bounces */
    public static class Ember extends TextureSheetParticle {
        public Ember(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s) {
            super(l, x, y, z);
            pickSprite(s);
            xd = dx;
            yd = dy;
            zd = dz;
            lifetime = 30 + random.nextInt(50);
            quadSize = 0.05f + random.nextFloat() * 0.07f;
            gravity = 0.6f;
            friction = 0.97f;
            hasPhysics = true;
        }

        @Override
        public void tick() {
            super.tick();
            alpha = age > lifetime - 10 ? (lifetime - age) / 10f : (random.nextInt(6) == 0 ? 0.6f : 1f);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float pt) {
            return LightTexture.FULL_BRIGHT;
        }
    }

    // ------------------------------------------------------------------------------------ flash
    public static class Flash extends TextureSheetParticle {
        private final float size;

        public Flash(ClientLevel l, double x, double y, double z, SpriteSet s, float size, int life) {
            super(l, x, y, z);
            pickSprite(s);
            this.size = size;
            lifetime = life;
            quadSize = size;
            hasPhysics = false;
            gravity = 0;
            xd = yd = zd = 0;
        }

        @Override
        public float getQuadSize(float pt) {
            float f = (age + pt) / lifetime;
            return size * (0.7f + f * 0.6f);
        }

        @Override
        public void tick() {
            super.tick();
            alpha = 1f - (float) age / lifetime;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float pt) {
            return LightTexture.FULL_BRIGHT;
        }

        @Override
        public AABB getRenderBoundingBox(float pt) {
            return getBoundingBox().inflate(size * 1.5);
        }
    }

    // ------------------------------------------------------------------------------------ providers (defaults for level.addParticle)
    public static class FireballProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet s;

        public FireballProvider(SpriteSet s) {
            this.s = s;
            fireballSprites = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Fireball(l, x, y, z, dx, dy, dz, s, 0.9f + l.random.nextFloat() * 0.6f, 12 + l.random.nextInt(8));
        }
    }

    public static class DustProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet s;

        public DustProvider(SpriteSet s) {
            this.s = s;
            dustSprites = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            float g = 0.62f + l.random.nextFloat() * 0.12f;
            return new DustCloud(l, x, y, z, dx, dy, dz, s, 0.8f + l.random.nextFloat() * 0.8f, 60 + l.random.nextInt(50),
                    g, g * 0.97f, g * 0.93f, 0.04f, 0.004f);
        }
    }

    public static class ShockwaveProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet s;

        public ShockwaveProvider(SpriteSet s) {
            this.s = s;
            shockwaveSprites = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Shockwave(l, x, y, z, s, (float) Math.max(2.0, dx), 12);
        }
    }

    public static class EmberProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet s;

        public EmberProvider(SpriteSet s) {
            this.s = s;
            emberSprites = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Ember(l, x, y, z, dx, dy, dz, s);
        }
    }

    public static class FlashProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet s;

        public FlashProvider(SpriteSet s) {
            this.s = s;
            flashSprites = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Flash(l, x, y, z, s, (float) Math.max(1.0, dx), 4);
        }
    }
}
