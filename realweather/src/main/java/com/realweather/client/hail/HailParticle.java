package com.realweather.client.hail;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A hailstone: falls fast, bounces off the ground a couple of times, rolls and settles. */
public class HailParticle extends TextureSheetParticle {
    private int bounces;

    protected HailParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, float size, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.9F;
        this.friction = 0.995F;
        this.hasPhysics = true;
        this.quadSize = size;
        this.lifetime = 50 + random.nextInt(50);
        float c = 0.88F + random.nextFloat() * 0.1F;
        this.setColor(c, c, c + 0.02F);
        this.alpha = 0.95F;
        this.setSize(size, size);
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        double prevYd = yd;
        super.tick();
        if (onGround && prevYd < -0.12 && bounces < 3) {
            // Ice on ground: a lively bounce that dies away quickly.
            yd = -prevYd * (0.38 - bounces * 0.1);
            xd += (random.nextDouble() - 0.5) * 0.08;
            zd += (random.nextDouble() - 0.5) * 0.08;
            bounces++;
        }
        if (age > lifetime - 12) {
            alpha *= 0.85F;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            // yd carries the size of the stones.
            float size = (float) Math.max(0.03, Math.min(0.14, yd));
            return new HailParticle(level, x, y, z, xd, -1.0 - Math.random() * 0.3, zd, size, sprites);
        }
    }
}
