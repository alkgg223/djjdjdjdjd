package com.realweather.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** An expanding ring lying flat on a water surface where a drop landed. */
public class RippleParticle extends TextureSheetParticle {
    private final float maxSize;

    protected RippleParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.lifetime = 12 + random.nextInt(8);
        this.maxSize = 0.22F + random.nextFloat() * 0.22F;
        this.quadSize = 0.02F;
        this.gravity = 0F;
        this.hasPhysics = false;
        this.xd = this.yd = this.zd = 0;
        this.setColor(0.92F, 0.95F, 1F);
        this.alpha = 0.75F;
        this.pickSprite(sprites);
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
        float t = (float) age / lifetime;
        this.quadSize = maxSize * (1F - (1F - t) * (1F - t));
        this.alpha = 0.75F * (1F - t);
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, xo, this.x) - cam.x);
        float y = (float) (Mth.lerp(partialTicks, yo, this.y) - cam.y);
        float z = (float) (Mth.lerp(partialTicks, zo, this.z) - cam.z);
        float s = getQuadSize(partialTicks);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTicks);
        // Both windings so it shows from above and below the surface.
        consumer.addVertex(x - s, y, z - s).setUv(u0, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        consumer.addVertex(x - s, y, z + s).setUv(u0, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        consumer.addVertex(x + s, y, z + s).setUv(u1, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        consumer.addVertex(x + s, y, z - s).setUv(u1, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        consumer.addVertex(x - s, y, z - s).setUv(u0, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        consumer.addVertex(x + s, y, z - s).setUv(u1, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        consumer.addVertex(x + s, y, z + s).setUv(u1, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
        consumer.addVertex(x - s, y, z + s).setUv(u0, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
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
            return new RippleParticle(level, x, y, z, sprites);
        }
    }
}
