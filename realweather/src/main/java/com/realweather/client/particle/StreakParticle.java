package com.realweather.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.realweather.ModContent;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * A fast particle drawn as a thin streak along its motion (motion blur): falling raindrops,
 * wind-driven blizzard snow and blown sand grains.
 */
public class StreakParticle extends TextureSheetParticle {
    private final float width;
    private final float length;
    private final boolean drop;

    protected StreakParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites,
                             float width, float length, boolean drop) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.width = width;
        this.length = length;
        this.drop = drop;
        this.quadSize = length * 0.5F;
        this.gravity = 0F;
        this.friction = 1F;
        this.hasPhysics = false;
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
        double nx = x + xd;
        double ny = y + yd;
        double nz = z + zd;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(nx), Mth.floor(nz));
        if (ny <= top) {
            if (drop) {
                land(nx, top, nz);
            }
            this.remove();
            return;
        }
        if (!drop) {
            // Gusts make snow and sand swirl a little.
            xd += (random.nextDouble() - 0.5) * 0.02;
            yd += (random.nextDouble() - 0.5) * 0.01;
            zd += (random.nextDouble() - 0.5) * 0.02;
        }
        this.setPos(nx, ny, nz);
        if (age > lifetime - 4) {
            alpha *= 0.7F;
        }
    }

    private void land(double x, int top, double z) {
        BlockPos below = BlockPos.containing(x, top - 1, z);
        FluidState fluid = level.getFluidState(below);
        if (!fluid.isEmpty() && fluid.is(FluidTags.WATER)) {
            level.addParticle(ModContent.RIPPLE.get(), x, below.getY() + fluid.getHeight(level, below) + 0.01, z, 0, 0, 0);
        } else if (fluid.isEmpty()) {
            double h = level.getBlockState(below).getShape(level, below).max(net.minecraft.core.Direction.Axis.Y);
            level.addParticle(ModContent.SPLASH.get(), x, below.getY() + h, z, 0, 0.6, 0);
        }
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, xo, x) - cam.x);
        float py = (float) (Mth.lerp(partialTicks, yo, y) - cam.y);
        float pz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z);
        // Plain float maths (no objects made per drop per frame: thousands of drops would keep the garbage collector busy).
        float dx = (float) xd, dy = (float) yd, dz = (float) zd;
        float dl = dx * dx + dy * dy + dz * dz;
        if (dl < 1.0E-8F) {
            dx = 0F;
            dy = -1F;
            dz = 0F;
        } else {
            float inv = 1F / (float) Math.sqrt(dl);
            dx *= inv;
            dy *= inv;
            dz *= inv;
        }
        float tx = -px, ty = -py, tz = -pz;
        float tl = tx * tx + ty * ty + tz * tz;
        if (tl < 1.0E-6F) {
            return;
        }
        float tinv = 1F / (float) Math.sqrt(tl);
        tx *= tinv;
        ty *= tinv;
        tz *= tinv;
        // side = dir x toCam
        float sx = dy * tz - dz * ty, sy = dz * tx - dx * tz, sz = dx * ty - dy * tx;
        float sl = sx * sx + sy * sy + sz * sz;
        if (sl < 1.0E-8F) {
            sx = 1F;
            sy = 0F;
            sz = 0F;
            sl = 1F;
        }
        float sk = width * 0.5F / (float) Math.sqrt(sl);
        sx *= sk;
        sy *= sk;
        sz *= sk;
        float hk = length * 0.5F;
        float hx = dx * hk, hy = dy * hk, hz = dz * hk;
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTicks);
        // v0 is the head of the streak (leading end), v1 the tail.
        vertex(consumer, px + hx - sx, py + hy - sy, pz + hz - sz, u0, v0, light);
        vertex(consumer, px + hx + sx, py + hy + sy, pz + hz + sz, u1, v0, light);
        vertex(consumer, px - hx + sx, py - hy + sy, pz - hz + sz, u1, v1, light);
        vertex(consumer, px - hx - sx, py - hy - sy, pz - hz - sz, u0, v1, light);
    }

    private void vertex(VertexConsumer c, float x, float y, float z, float u, float v, int light) {
        c.addVertex(x, y, z).setUv(u, v).setColor(rCol, gCol, bCol, alpha).setLight(light);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class DropProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public DropProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            // Thin, slightly blurred streaks: small drops shorter and fainter than the big ones.
            float size = (float) Math.random();
            StreakParticle p = new StreakParticle(level, x, y, z, xd, yd, zd, sprites, 0.012F + size * 0.01F, 0.45F + size * 0.4F, true);
            p.lifetime = 30;
            float c = 0.84F + (float) Math.random() * 0.1F;
            p.setColor(c, c + 0.04F, Math.min(1F, c + 0.1F));
            p.alpha = 0.3F + size * 0.3F;
            return p;
        }
    }

    public static class BlizzardProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public BlizzardProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            StreakParticle p = new StreakParticle(level, x, y, z, xd, yd, zd, sprites, 0.05F, 0.25F + (float) Math.random() * 0.3F, false);
            p.lifetime = 30 + (int) (Math.random() * 20);
            p.setColor(0.97F, 0.98F, 1F);
            p.alpha = 0.7F + (float) Math.random() * 0.3F;
            return p;
        }
    }

    public static class GrainProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public GrainProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            StreakParticle p = new StreakParticle(level, x, y, z, xd, yd, zd, sprites, 0.025F, 0.18F + (float) Math.random() * 0.2F, false);
            p.lifetime = 25 + (int) (Math.random() * 15);
            float c = 0.8F + (float) Math.random() * 0.15F;
            p.setColor(c, c * 0.8F, c * 0.58F);
            p.alpha = 0.6F + (float) Math.random() * 0.3F;
            return p;
        }
    }
}
