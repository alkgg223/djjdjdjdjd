package com.realweather.client.hail;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realweather.ModContent;
import com.realweather.RealWeather;
import com.realweather.server.CoverBlock;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Footprints pressed into hail-snow and snow by players and animals; they slowly fill in again. */
public final class Footprints {
    private static final ResourceLocation TEXTURE = RealWeather.id("textures/environment/footprints.png");
    private static final int MAX = 700;
    private static final int LIFE = 20 * 150;

    private record Print(double x, double y, double z, float yaw, boolean paw, boolean left, int born, int light) {}

    private static final ArrayDeque<Print> PRINTS = new ArrayDeque<>();
    private static final Int2ObjectOpenHashMap<double[]> LAST = new Int2ObjectOpenHashMap<>();
    private static int now;

    private Footprints() {}

    public static void clear() {
        PRINTS.clear();
        LAST.clear();
    }

    public static void tick(Minecraft mc, ClientLevel level) {
        now++;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || !e.onGround() || e.isPassenger() || e.distanceToSqr(cam) > 40 * 40 || e.isSpectator()) {
                continue;
            }
            double surface = surface(level, e.blockPosition());
            if (Double.isNaN(surface)) {
                continue;
            }
            double[] last = LAST.get(e.getId());
            boolean player = e instanceof Player;
            double stride = player ? 0.72 : 0.35 + e.getBbWidth() * 0.45;
            if (last == null) {
                LAST.put(e.getId(), new double[]{e.getX(), e.getZ(), 0});
                continue;
            }
            double dx = e.getX() - last[0];
            double dz = e.getZ() - last[1];
            if (dx * dx + dz * dz < stride * stride) {
                continue;
            }
            boolean left = last[2] == 0;
            float yaw = player ? e.getYRot() : living.yBodyRot;
            double side = (left ? -1 : 1) * (player ? 0.13 : Math.max(0.1, e.getBbWidth() * 0.25));
            double rad = Math.toRadians(yaw);
            double px = e.getX() + Math.cos(rad) * side;
            double pz = e.getZ() + Math.sin(rad) * side;
            BlockPos at = BlockPos.containing(px, e.getY(), pz);
            PRINTS.addLast(new Print(px, surface + 0.003, pz, yaw, !player, left, now, LevelRenderer.getLightColor(level, at.above())));
            if (!player && e.getBbWidth() > 0.7F) {
                // Four-legged animals leave a second pair behind.
                double back = e.getBbWidth() * 0.7;
                PRINTS.addLast(new Print(px + Math.sin(rad) * back, surface + 0.003, pz - Math.cos(rad) * back, yaw, true, !left, now,
                        LevelRenderer.getLightColor(level, at.above())));
            }
            while (PRINTS.size() > MAX) {
                PRINTS.removeFirst();
            }
            last[0] = e.getX();
            last[1] = e.getZ();
            last[2] = left ? 1 : 0;
        }
        if (now % 40 == 0) {
            Iterator<Print> it = PRINTS.iterator();
            while (it.hasNext()) {
                Print p = it.next();
                if (now - p.born > LIFE || Double.isNaN(surface(level, BlockPos.containing(p.x, p.y - 0.05, p.z)))) {
                    it.remove();
                }
            }
            LAST.keySet().removeIf(id -> level.getEntity(id) == null);
        }
    }

    /** Top of the soft cover the entity stands in, or NaN when it is not on hail, hail-snow or snow. */
    private static double surface(ClientLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof CoverBlock || s.is(Blocks.SNOW)) {
            return pos.getY() + s.getShape(level, pos).max(Direction.Axis.Y);
        }
        BlockState below = level.getBlockState(pos.below());
        if (below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.POWDER_SNOW) || below.getBlock() instanceof CoverBlock c && !c.pellets) {
            return pos.getY();
        }
        return Double.NaN;
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || PRINTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        LightTexture light = mc.gameRenderer.lightTexture();
        light.turnOnLightLayer();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1F, -10F);
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        for (Print p : PRINTS) {
            double dx = p.x - cam.x;
            double dz = p.z - cam.z;
            if (dx * dx + dz * dz > 48 * 48) {
                continue;
            }
            float age = (now - p.born) / (float) LIFE;
            float a = 0.75F * Mth.clamp((1F - age) * 3F, 0F, 1F);
            float w = p.paw ? 0.13F : 0.11F;
            float l = p.paw ? 0.13F : 0.27F;
            float rad = (float) Math.toRadians(p.yaw);
            // Forward (direction of travel) and right vectors in the ground plane.
            float fx = -Mth.sin(rad), fz = Mth.cos(rad);
            float rx = -fz, rz = fx;
            float cx = (float) dx, cy = (float) (p.y - cam.y), cz = (float) dz;
            float u0 = p.paw ? 0.5F : 0F;
            float u1 = u0 + 0.5F;
            float v0 = p.left ? 0F : 1F;
            float v1 = p.left ? 1F : 0F;
            float c = 0.78F;
            b.addVertex(cx - rx * w - fx * l, cy, cz - rz * w - fz * l).setUv(u0, v1).setColor(c, c, c + 0.04F, a).setLight(p.light);
            b.addVertex(cx + rx * w - fx * l, cy, cz + rz * w - fz * l).setUv(u1, v1).setColor(c, c, c + 0.04F, a).setLight(p.light);
            b.addVertex(cx + rx * w + fx * l, cy, cz + rz * w + fz * l).setUv(u1, v0).setColor(c, c, c + 0.04F, a).setLight(p.light);
            b.addVertex(cx - rx * w + fx * l, cy, cz - rz * w + fz * l).setUv(u0, v0).setColor(c, c, c + 0.04F, a).setLight(p.light);
        }
        MeshData mesh = b.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.polygonOffset(0F, 0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        light.turnOffLightLayer();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}
