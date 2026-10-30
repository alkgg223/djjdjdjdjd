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
import net.minecraft.world.phys.Vec3;

/** Particle implementations for muzzle flashes, tracers, smoke, flames, sparks, shell casings and holograms. */
public final class GunParticles {
    private GunParticles() {}

    // ------------------------------------------------------------------------------------ muzzle flash
    public static class Flash extends TextureSheetParticle {
        Flash(ClientLevel l, double x, double y, double z, SpriteSet s) {
            super(l, x, y, z);
            pickSprite(s);
            lifetime = 2;
            quadSize = 0.32f + random.nextFloat() * 0.15f;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            hasPhysics = false;
            gravity = 0;
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
        public float getQuadSize(float pt) {
            return quadSize * (1f - (age + pt) / (lifetime + 1f) * 0.4f);
        }
    }

    // ------------------------------------------------------------------------------------ tracer
    /** a bright streak flying from the muzzle to the impact point. (xd, yd, zd) is the full path. */
    public static class Tracer extends TextureSheetParticle {
        private final Vec3 start;
        private final Vec3 path;
        private final double length;

        Tracer(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s) {
            super(l, x, y, z);
            pickSprite(s);
            start = new Vec3(x, y, z);
            path = new Vec3(dx, dy, dz);
            length = path.length();
            lifetime = Math.max(2, (int) Math.ceil(length / 22.0));
            hasPhysics = false;
            gravity = 0;
            xd = yd = zd = 0;
            alpha = 0.95f;
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
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) remove();
        }

        @Override
        public void render(VertexConsumer vc, Camera cam, float pt) {
            if (length < 0.5) return;
            double f0 = Mth.clamp((age + pt - 1.2) / lifetime, 0, 1);
            double f1 = Mth.clamp((age + pt) / lifetime, 0, 1);
            double streak = Math.min(6.0, length * 0.4);
            Vec3 dir = path.scale(1.0 / length);
            Vec3 head = start.add(path.scale(f1));
            Vec3 tail = head.subtract(dir.scale(Math.min(streak, length * f1)));
            if (f0 >= 1) return;
            Vec3 c = cam.getPosition();
            Vec3 a = tail.subtract(c), b = head.subtract(c);
            Vec3 toCam = a.add(b).scale(-0.5).normalize();
            Vec3 perp = dir.cross(toCam).normalize();
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            int light = getLightColor(pt);
            // glowing halo, then the white-hot core
            Vec3 halo = perp.scale(0.11);
            vertex(vc, a.subtract(halo), u0, v1, light, 0.0f, 1f, 0.55f, 0.15f);
            vertex(vc, a.add(halo), u0, v0, light, 0.0f, 1f, 0.55f, 0.15f);
            vertex(vc, b.add(halo), u1, v0, light, alpha * 0.35f, 1f, 0.55f, 0.15f);
            vertex(vc, b.subtract(halo), u1, v1, light, alpha * 0.35f, 1f, 0.55f, 0.15f);
            Vec3 side = perp.scale(0.04);
            vertex(vc, a.subtract(side), u0, v1, light, 0.0f, 1f, 0.95f, 0.75f);
            vertex(vc, a.add(side), u0, v0, light, 0.0f, 1f, 0.95f, 0.75f);
            vertex(vc, b.add(side), u1, v0, light, alpha, 1f, 0.95f, 0.75f);
            vertex(vc, b.subtract(side), u1, v1, light, alpha, 1f, 0.95f, 0.75f);
        }

        private void vertex(VertexConsumer vc, Vec3 p, float u, float v, int light, float a, float r, float g, float b) {
            vc.addVertex((float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(r, g, b, a).setLight(light);
        }
    }

    // ------------------------------------------------------------------------------------ smoke
    public static class Smoke extends TextureSheetParticle {
        private final float grow;

        Smoke(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s, boolean missile) {
            super(l, x, y, z, dx, dy, dz);
            pickSprite(s);
            xd = dx;
            yd = dy;
            zd = dz;
            lifetime = missile ? 60 + random.nextInt(40) : 25 + random.nextInt(20);
            quadSize = missile ? 0.5f + random.nextFloat() * 0.3f : 0.18f + random.nextFloat() * 0.15f;
            grow = missile ? 0.05f : 0.02f;
            float g = missile ? 0.85f + random.nextFloat() * 0.1f : 0.55f + random.nextFloat() * 0.2f;
            rCol = gCol = bCol = g;
            alpha = missile ? 0.75f : 0.55f;
            friction = 0.92f;
            gravity = -0.004f;
            hasPhysics = false;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        }

        @Override
        public void tick() {
            super.tick();
            quadSize += grow;
            alpha = Math.max(0, alpha * (1f - 1f / lifetime) - 0.004f);
            oRoll = roll;
            roll += 0.01f;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------------------------------ flame
    public static class Flame extends TextureSheetParticle {
        private final SpriteSet sprites;

        Flame(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s) {
            super(l, x, y, z, dx, dy, dz);
            sprites = s;
            xd = dx;
            yd = dy;
            zd = dz;
            lifetime = 14 + random.nextInt(8);
            quadSize = 0.15f + random.nextFloat() * 0.1f;
            friction = 0.9f;
            gravity = -0.015f;
            hasPhysics = true;
            setSpriteFromAge(s);
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
            quadSize *= 1.09f;
            oRoll = roll;
            roll += 0.05f;
            alpha = 1f - (float) age / lifetime * 0.6f;
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

    // ------------------------------------------------------------------------------------ spark
    public static class Spark extends TextureSheetParticle {
        Spark(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s) {
            super(l, x, y, z, dx, dy, dz);
            pickSprite(s);
            xd = dx;
            yd = dy;
            zd = dz;
            lifetime = 5 + random.nextInt(6);
            quadSize = 0.035f + random.nextFloat() * 0.03f;
            gravity = 1.0f;
            friction = 0.9f;
            hasPhysics = true;
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

    // ------------------------------------------------------------------------------------ shell casing
    public static class Shell extends TextureSheetParticle {
        Shell(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s) {
            super(l, x, y, z, dx, dy, dz);
            pickSprite(s);
            xd = dx;
            yd = dy;
            zd = dz;
            lifetime = 30 + random.nextInt(20);
            quadSize = 0.045f;
            gravity = 0.9f;
            friction = 0.95f;
            hasPhysics = true;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        }

        @Override
        public void tick() {
            super.tick();
            oRoll = roll;
            if (!onGround) roll += 0.6f;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }
    }

    // ------------------------------------------------------------------------------------ hologram
    public static class Holo extends TextureSheetParticle {
        Holo(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s) {
            super(l, x, y, z, dx, dy, dz);
            pickSprite(s);
            xd = dx;
            yd = dy;
            zd = dz;
            lifetime = 25;
            quadSize = 0.12f;
            gravity = -0.01f;
            hasPhysics = false;
            alpha = 0.8f;
        }

        @Override
        public void tick() {
            super.tick();
            alpha = 0.8f * (1f - (float) age / lifetime);
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

    // ------------------------------------------------------------------------------------ decoy flare: white-hot burning core
    public static class FlareGlow extends TextureSheetParticle {
        FlareGlow(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s) {
            super(l, x, y, z, dx, dy, dz);
            pickSprite(s);
            xd = dx;
            yd = dy;
            zd = dz;
            lifetime = 3;
            quadSize = 0.5f + random.nextFloat() * 0.35f;
            rCol = 1f;
            gCol = 0.93f + random.nextFloat() * 0.07f;
            bCol = 0.75f + random.nextFloat() * 0.2f;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            hasPhysics = false;
            gravity = 0;
            friction = 0.8f;
        }

        @Override
        public void tick() {
            super.tick();
            quadSize *= 0.8f;
            alpha = 1f - (float) age / lifetime * 0.5f;
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

    // ------------------------------------------------------------------------------------ providers
    public record FlareProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new FlareGlow(l, x, y, z, dx, dy, dz, s);
        }
    }

    public record FlashProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Flash(l, x, y, z, s);
        }
    }

    public record TracerProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Tracer(l, x, y, z, dx, dy, dz, s);
        }
    }

    public record SmokeProvider(SpriteSet s, boolean missile) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Smoke(l, x, y, z, dx, dy, dz, s, missile);
        }
    }

    public record FlameProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Flame(l, x, y, z, dx, dy, dz, s);
        }
    }

    public record SparkProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Spark(l, x, y, z, dx, dy, dz, s);
        }
    }

    public record ShellProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Shell(l, x, y, z, dx, dy, dz, s);
        }
    }

    public record HoloProvider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Holo(l, x, y, z, dx, dy, dz, s);
        }
    }
}
