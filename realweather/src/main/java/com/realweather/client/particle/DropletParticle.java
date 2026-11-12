package com.realweather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A tiny droplet thrown up and out of a raindrop's splash; it arcs back down and is gone. */
public class DropletParticle extends TextureSheetParticle {
    protected DropletParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.9F;
        this.friction = 0.96F;
        this.hasPhysics = true;
        this.lifetime = 6 + random.nextInt(6);
        this.quadSize = 0.012F + random.nextFloat() * 0.014F;
        float c = 0.82F + random.nextFloat() * 0.12F;
        this.setColor(c, c + 0.03F, c + 0.07F);
        this.alpha = 0.55F + random.nextFloat() * 0.3F;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (onGround) {
            remove();
        }
        alpha *= 0.94F;
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
            return new DropletParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
