package com.realweather.client.tornado;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A wisp of condensation spiralling round the upper funnel and the collar where it joins the wall cloud: it turns
 * with the vortex, drifts inward and down along the funnel wall, and dissolves.
 */
public class TornadoCloudParticle extends TextureSheetParticle {
    private final int tornado;
    private final float maxAlpha;
    private final float sink;
    private float radius;
    private float angle;

    public TornadoCloudParticle(ClientLevel level, double x, double y, double z, int tornado, SpriteSet sprites) {
        super(level, x, y, z);
        this.tornado = tornado;
        this.hasPhysics = false;
        this.gravity = 0F;
        this.lifetime = 60 + random.nextInt(70);
        this.alpha = 0F;
        this.maxAlpha = 0.35F + random.nextFloat() * 0.3F;
        this.sink = 0.02F + random.nextFloat() * 0.06F;
        this.roll = random.nextFloat() * 6.283F;
        this.oRoll = roll;
        ClientTornadoes.T t = ClientTornadoes.get(tornado);
        float shade = 0.42F + random.nextFloat() * 0.14F;
        if (t != null) {
            double dx = x - t.x;
            double dz = z - t.z;
            radius = (float) Math.sqrt(dx * dx + dz * dz);
            angle = (float) Math.atan2(dz, dx);
            this.quadSize = (2.2F + random.nextFloat() * 3F) * (0.7F + t.rating * 0.22F);
            shade += t.shape.tint() * 0.8F + (t.rating >= 4 ? -0.05F : 0F);
        } else {
            this.quadSize = 3F;
        }
        this.setColor(shade, shade * 1.01F, shade * 1.04F);
        if (sprites != null) {
            this.pickSprite(sprites);
        }
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float life = (float) age / lifetime;
        alpha = maxAlpha * Math.min(1F, life * 5F) * Math.min(1F, (1F - life) * 2.5F);
        ClientTornadoes.T t = ClientTornadoes.get(tornado);
        if (t == null) {
            alpha *= 0.9F;
            return;
        }
        alpha *= 0.4F + 0.6F * t.strength;
        float h = (float) (y - t.groundY);
        float f = Mth.clamp(h / t.height(), 0F, 1F);
        float edge = t.radiusAt(f) + 0.8F;
        // Turns with the wind at the funnel wall (a little slower than the debris), hugging the wall.
        float v = t.windAt(Math.max(1F, edge)) * 0.9F + 0.05F;
        angle -= v / Math.max(1.5F, radius);
        radius += (edge * 1.15F - radius) * 0.03F;
        double cx = t.x + t.offsetX(f, t.age);
        double cz = t.z + t.offsetZ(f, t.age);
        setPos(cx + Mth.cos(angle) * radius, y - sink, cz + Mth.sin(angle) * radius);
        roll += 0.015F;
        quadSize *= 1.003F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
            ClientTornadoes.setCloudSprites(sprites);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new TornadoCloudParticle(level, x, y, z, (int) xd, sprites);
        }
    }
}
