package com.realweather.client.tornado;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A billow of dust and debris whirling around the base of a tornado and climbing into it. */
public class TornadoDustParticle extends TextureSheetParticle {
    private final int tornado;
    private final float maxAlpha;
    private float radius;
    private float angle;
    private final float climb;

    protected TornadoDustParticle(ClientLevel level, double x, double y, double z, int tornado, SpriteSet sprites) {
        super(level, x, y, z);
        this.tornado = tornado;
        this.hasPhysics = false;
        this.gravity = 0F;
        this.lifetime = 70 + random.nextInt(80);
        this.quadSize = 1.4F + random.nextFloat() * 2.6F;
        this.maxAlpha = 0.18F + random.nextFloat() * 0.22F;
        this.alpha = 0F;
        this.climb = 0.04F + random.nextFloat() * 0.12F;
        float c = 0.42F + random.nextFloat() * 0.18F;
        // Dust picked up from the ground: grey-brown.
        this.setColor(c * 1.08F, c, c * 0.84F);
        this.roll = random.nextFloat() * 6.283F;
        this.oRoll = roll;
        ClientTornadoes.T t = ClientTornadoes.get(tornado);
        if (t != null) {
            double dx = x - t.x;
            double dz = z - t.z;
            radius = (float) Math.sqrt(dx * dx + dz * dz);
            angle = (float) Math.atan2(dz, dx);
            quadSize *= 0.7F + t.rating * 0.15F;
        }
        this.pickSprite(sprites);
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
        ClientTornadoes.T t = ClientTornadoes.get(tornado);
        float life = (float) age / lifetime;
        alpha = maxAlpha * Math.min(1F, life * 6F) * Math.min(1F, (1F - life) * 3F);
        if (t == null) {
            alpha *= 0.9F;
            move(xd, yd, zd);
            return;
        }
        float rc = Math.max(2F, t.coreRadius());
        float v = t.windAt(Math.max(0.5F, radius));
        // Counter-clockwise (seen from above), drifting inward near the ground and up the funnel.
        angle -= v / Math.max(0.8F, radius) * 1.6F;
        float h = (float) (y - t.groundY);
        if (h < rc * 0.8F) {
            radius += (rc * 1.1F - radius) * 0.02F;
        } else {
            radius += 0.03F + h * 0.002F;
        }
        float f = Mth.clamp(h / t.height(), 0F, 1F);
        double cx = t.x + t.offsetX(f, t.age);
        double cz = t.z + t.offsetZ(f, t.age);
        double nx = cx + Mth.cos(angle) * radius;
        double nz = cz + Mth.sin(angle) * radius;
        double ny = y + climb * (0.3F + t.strength) * (radius < rc * 1.6F ? 1.6F : 0.4F);
        setPos(nx, ny, nz);
        roll += 0.02F;
        quadSize *= 1.004F;
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
            return new TornadoDustParticle(level, x, y, z, (int) xd, sprites);
        }
    }
}
