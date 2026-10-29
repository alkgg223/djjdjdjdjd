package com.realweather.client.tornado;

import com.realweather.server.Tornado;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A piece of the ground (a chunk of the block the tornado is passing over: grass, leaves, wood, stone)
 * swept up in the vortex, carried up and around, then flung out to fall back down.
 */
public class TornadoDebrisParticle extends TextureSheetParticle {
    private final int tornado;
    private final float uo;
    private final float vo;
    private float radius;
    private float angle;
    private boolean flung;

    public TornadoDebrisParticle(ClientLevel level, double x, double y, double z, int tornado, BlockState state) {
        super(level, x, y, z);
        this.tornado = tornado;
        this.setSprite(Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(state));
        this.hasPhysics = false;
        this.gravity = 0F;
        this.lifetime = 120 + random.nextInt(140);
        this.quadSize = 0.06F + random.nextFloat() * 0.18F;
        this.uo = random.nextFloat() * 3F;
        this.vo = random.nextFloat() * 3F;
        float c = 0.6F + random.nextFloat() * 0.3F;
        this.setColor(c, c, c);
        this.roll = random.nextFloat() * 6.283F;
        this.oRoll = roll;
        ClientTornadoes.T t = ClientTornadoes.get(tornado);
        if (t != null) {
            double dx = x - t.x;
            double dz = z - t.z;
            radius = (float) Math.sqrt(dx * dx + dz * dz);
            angle = (float) Math.atan2(dz, dx);
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
        ClientTornadoes.T t = ClientTornadoes.get(tornado);
        if (t == null || flung) {
            yd -= 0.04;
            xd *= 0.98;
            zd *= 0.98;
            move(xd, yd, zd);
            roll += 0.3F;
            if (onGround) {
                remove();
            }
            return;
        }
        float rc = Math.max(2F, t.coreRadius());
        float v = t.windAt(Math.max(0.5F, radius)) * 1.3F;
        float step = v / Math.max(0.6F, radius);
        angle -= step;
        float h = (float) (y - t.groundY);
        float lift = Math.max(6F, Tornado.LIFT[t.rating] * 1.4F);
        radius += (rc * 0.9F - radius) * 0.03F + h * 0.0015F;
        float f = Mth.clamp(h / t.height(), 0F, 1F);
        double cx = t.x + t.offsetX(f, t.age);
        double cz = t.z + t.offsetZ(f, t.age);
        double nx = cx + Mth.cos(angle) * radius;
        double nz = cz + Mth.sin(angle) * radius;
        double ny = y + (0.15 + t.strength * 0.35) * (t.rating + 1) / 6.0 + 0.03;
        roll += step * 3F;
        if (h > lift || radius > rc * 3F) {
            // Thrown out of the vortex, tangentially, and left to fall.
            flung = true;
            xd = nx - x;
            yd = 0.1;
            zd = nz - z;
            return;
        }
        setPos(nx, ny, nz);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }

    @Override
    protected float getU0() {
        return sprite.getU((uo + 1F) / 4F);
    }

    @Override
    protected float getU1() {
        return sprite.getU(uo / 4F);
    }

    @Override
    protected float getV0() {
        return sprite.getV(vo / 4F);
    }

    @Override
    protected float getV1() {
        return sprite.getV((vo + 1F) / 4F);
    }
}
