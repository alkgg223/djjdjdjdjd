package com.realweather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A raindrop crown splash: a short animated spray at the impact point. */
public class SplashParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final double baseY;

    protected SplashParticle(ClientLevel level, double x, double y, double z, float strength, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.lifetime = 4 + random.nextInt(3);
        this.quadSize = (0.05F + random.nextFloat() * 0.05F) * (0.75F + strength * 0.6F);
        this.baseY = y;
        this.setPos(x, y + quadSize * 0.85, z);
        this.xo = x;
        this.yo = this.y;
        this.zo = z;
        this.gravity = 0F;
        this.hasPhysics = false;
        this.xd = this.yd = this.zd = 0;
        float c = 0.82F + random.nextFloat() * 0.1F;
        this.setColor(c, c + 0.04F, c + 0.08F);
        this.alpha = 0.55F + random.nextFloat() * 0.25F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        this.setSpriteFromAge(sprites);
        this.quadSize *= 1.06F;
        this.setPos(x, baseY + quadSize * 0.85, z);
        this.alpha *= 0.9F;
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
            return new SplashParticle(level, x, y, z, (float) yd, sprites);
        }
    }
}
