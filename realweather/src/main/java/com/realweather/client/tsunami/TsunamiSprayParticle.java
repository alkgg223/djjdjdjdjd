package com.realweather.client.tsunami;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Spray blown off the breaking crest of a tsunami: a big white cloud of mist that billows forward and fades. */
public class TsunamiSprayParticle extends TextureSheetParticle {
    private final float maxAlpha;

    protected TsunamiSprayParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.02F;
        this.friction = 0.97F;
        this.hasPhysics = false;
        this.lifetime = 30 + random.nextInt(30);
        this.quadSize = 1.6F + random.nextFloat() * 2.4F;
        this.maxAlpha = 0.35F + random.nextFloat() * 0.3F;
        this.alpha = 0F;
        float c = 0.9F + random.nextFloat() * 0.08F;
        this.setColor(c, c + 0.02F, c + 0.04F);
        this.roll = random.nextFloat() * 6.283F;
        this.oRoll = roll;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) age / lifetime;
        alpha = maxAlpha * Math.min(1F, life * 6F) * Math.min(1F, (1F - life) * 2F);
        quadSize *= 1.025F;
        oRoll = roll;
        roll += 0.02F;
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
            return new TsunamiSprayParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
