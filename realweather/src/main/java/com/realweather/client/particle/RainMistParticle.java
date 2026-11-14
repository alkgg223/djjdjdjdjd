package com.realweather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** The fine spray a downpour throws up off the ground: a low, faint mist drifting with the wind. */
public class RainMistParticle extends TextureSheetParticle {
    private final float maxAlpha;

    protected RainMistParticle(ClientLevel level, double x, double y, double z, double xd, double zd, float strength, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = 0.002;
        this.zd = zd;
        this.gravity = 0F;
        this.friction = 1F;
        this.hasPhysics = false;
        this.lifetime = 40 + random.nextInt(40);
        this.quadSize = 0.9F + random.nextFloat() * 1.4F;
        this.maxAlpha = (0.05F + random.nextFloat() * 0.05F) * strength;
        this.alpha = 0F;
        float c = 0.78F + random.nextFloat() * 0.1F;
        this.setColor(c, c + 0.02F, c + 0.05F);
        this.roll = random.nextFloat() * 6.283F;
        this.oRoll = roll;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) age / lifetime;
        alpha = maxAlpha * Math.min(1F, life * 4F) * Math.min(1F, (1F - life) * 3F);
        quadSize *= 1.01F;
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
            return new RainMistParticle(level, x, y, z, xd, zd, (float) yd, sprites);
        }
    }
}
