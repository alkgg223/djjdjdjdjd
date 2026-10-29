package com.realweather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/** A single snowflake close to the camera, tumbling and swaying as it falls. */
public class FlakeParticle extends TextureSheetParticle {
    private final float phase;
    private final float sway;

    protected FlakeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = 120 + random.nextInt(60);
        this.quadSize = 0.025F + random.nextFloat() * 0.035F;
        this.gravity = 0F;
        this.friction = 1F;
        this.hasPhysics = false;
        this.phase = random.nextFloat() * 6.283F;
        this.sway = 0.01F + random.nextFloat() * 0.02F;
        this.alpha = 0F;
        this.roll = random.nextFloat() * 6.283F;
        this.oRoll = roll;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float t = age * 0.08F + phase;
        double nx = x + xd + Mth.sin(t) * sway;
        double ny = y + yd;
        double nz = z + zd + Mth.cos(t * 0.8F) * sway;
        roll += 0.03F;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(nx), Mth.floor(nz));
        if (ny <= top) {
            remove();
            return;
        }
        setPos(nx, ny, nz);
        alpha = Math.min(0.95F, alpha + 0.1F);
        if (age > lifetime - 10) {
            alpha *= 0.8F;
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
            return new FlakeParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
