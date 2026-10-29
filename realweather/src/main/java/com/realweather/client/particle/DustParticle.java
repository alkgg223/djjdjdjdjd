package com.realweather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A big soft cloud of dust rolling along the ground in a sandstorm. */
public class DustParticle extends TextureSheetParticle {
    private final float maxAlpha;

    protected DustParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = 50 + random.nextInt(50);
        this.quadSize = 1.2F + random.nextFloat() * 2.2F;
        this.gravity = 0F;
        this.friction = 0.99F;
        this.hasPhysics = false;
        this.maxAlpha = 0.12F + random.nextFloat() * 0.16F;
        this.alpha = 0F;
        float c = 0.85F + random.nextFloat() * 0.1F;
        this.setColor(c, c * 0.78F, c * 0.55F);
        this.roll = random.nextFloat() * 6.283F;
        this.oRoll = roll;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += 0.01F;
        quadSize *= 1.006F;
        float t = (float) age / lifetime;
        alpha = maxAlpha * Math.min(1F, t * 5F) * Math.min(1F, (1F - t) * 3F);
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
            return new DustParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
