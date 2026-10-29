package com.realweather.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.realweather.ClientConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A realistic lightning bolt: a jagged, branching channel from the cloud base to the ground, drawn as a
 * white-hot core inside layers of blue-violet glow, lighting up the cloud base and the ground where it lands.
 * Return strokes follow the same channel and flicker, like real lightning.
 */
public class RealLightningRenderer extends EntityRenderer<LightningBolt> {
    private final LightningBoltRenderer vanilla;

    public RealLightningRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.vanilla = new LightningBoltRenderer(context);
    }

    private record Segment(Vector3f a, Vector3f b, float width, float alpha) {}

    @Override
    public void render(LightningBolt bolt, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!ClientConfig.REALISTIC_LIGHTNING.get()) {
            vanilla.render(bolt, yaw, partialTick, pose, buffers, light);
            return;
        }
        // The channel is the same for every return stroke of this bolt; only the brightness flickers.
        RandomSource rnd = RandomSource.create(bolt.getId() * 341873128712L + 132897987541L);
        float height = 110F + rnd.nextFloat() * 50F;
        List<Segment> segments = new ArrayList<>();
        Vector3f top = new Vector3f((rnd.nextFloat() - 0.5F) * 40F, height, (rnd.nextFloat() - 0.5F) * 40F);
        List<Vector3f> main = new ArrayList<>();
        main.add(new Vector3f(0, 0, 0));
        main.add(top);
        main = subdivide(main, rnd, 7, 0.22F);
        for (int i = 0; i + 1 < main.size(); i++) {
            segments.add(new Segment(main.get(i), main.get(i + 1), 1F, 1F));
        }
        // Branches fork off the upper part of the channel and reach down and outwards, fading as they go.
        int branches = 4 + rnd.nextInt(7);
        for (int b = 0; b < branches; b++) {
            int start = main.size() / 4 + rnd.nextInt(Math.max(1, main.size() * 3 / 4 - 1));
            Vector3f from = main.get(Math.min(start, main.size() - 1));
            float len = 12F + rnd.nextFloat() * 45F;
            Vector3f to = new Vector3f(from).add((rnd.nextFloat() - 0.5F) * len * 1.4F, -len * (0.5F + rnd.nextFloat() * 0.5F),
                    (rnd.nextFloat() - 0.5F) * len * 1.4F);
            if (to.y < 2F) {
                to.y = 2F;
            }
            List<Vector3f> path = new ArrayList<>();
            path.add(from);
            path.add(to);
            path = subdivide(path, rnd, 5, 0.28F);
            for (int i = 0; i + 1 < path.size(); i++) {
                float k = 1F - (float) i / path.size();
                segments.add(new Segment(path.get(i), path.get(i + 1), 0.45F * k + 0.1F, 0.85F * k));
            }
        }

        // Stroke brightness: the first stroke is brightest, later return strokes flicker.
        float flicker = bolt.tickCount < 2 ? 1F : ((bolt.seed >>> 7) & 3) == 0 ? 0.55F : 0.85F;
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        Vector3f camLocal = new Vector3f((float) (cam.x - bolt.getX()), (float) (cam.y - bolt.getY()), (float) (cam.z - bolt.getZ()));
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

        glow(vc, m, top.x, top.y, top.z, 70F, 0.55F, 0.62F, 1F, 0.55F * flicker);
        glow(vc, m, 0F, 0.3F, 0F, 12F, 0.75F, 0.8F, 1F, 0.5F * flicker);
        for (Segment s : segments) {
            ribbon(vc, m, camLocal, s.a, s.b, 4.0F * s.width, 0.35F, 0.42F, 1F, 0.1F * s.alpha * flicker);
            ribbon(vc, m, camLocal, s.a, s.b, 1.3F * s.width, 0.6F, 0.66F, 1F, 0.45F * s.alpha * flicker);
            ribbon(vc, m, camLocal, s.a, s.b, 0.24F * s.width + 0.06F, 1F, 1F, 1F, 1F * s.alpha * flicker);
            ribbon(vc, m, camLocal, s.a, s.b, 0.24F * s.width + 0.06F, 1F, 1F, 1F, 0.6F * s.alpha * flicker);
        }
    }

    /** Midpoint displacement: each pass splits every segment and kicks the midpoint sideways. */
    private static List<Vector3f> subdivide(List<Vector3f> points, RandomSource rnd, int passes, float roughness) {
        List<Vector3f> cur = points;
        for (int p = 0; p < passes; p++) {
            List<Vector3f> next = new ArrayList<>(cur.size() * 2);
            for (int i = 0; i + 1 < cur.size(); i++) {
                Vector3f a = cur.get(i);
                Vector3f b = cur.get(i + 1);
                float len = a.distance(b);
                Vector3f mid = new Vector3f(a).add(b).mul(0.5F);
                mid.add((rnd.nextFloat() - 0.5F) * len * roughness * 2F, (rnd.nextFloat() - 0.5F) * len * roughness * 0.5F,
                        (rnd.nextFloat() - 0.5F) * len * roughness * 2F);
                next.add(a);
                next.add(mid);
            }
            next.add(cur.get(cur.size() - 1));
            cur = next;
        }
        return cur;
    }

    /** A camera-facing strip along a segment. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vector3f cam, Vector3f a, Vector3f b, float width,
                               float r, float g, float bl, float alpha) {
        Vector3f dir = new Vector3f(b).sub(a);
        Vector3f mid = new Vector3f(a).add(b).mul(0.5F);
        Vector3f toCam = new Vector3f(cam).sub(mid);
        Vector3f side = dir.cross(toCam, new Vector3f());
        if (side.lengthSquared() < 1.0E-6F) {
            side.set(1F, 0F, 0F);
        }
        side.normalize().mul(width * 0.5F);
        vc.addVertex(m, a.x - side.x, a.y - side.y, a.z - side.z).setColor(r, g, bl, alpha);
        vc.addVertex(m, a.x + side.x, a.y + side.y, a.z + side.z).setColor(r, g, bl, alpha);
        vc.addVertex(m, b.x + side.x, b.y + side.y, b.z + side.z).setColor(r, g, bl, alpha);
        vc.addVertex(m, b.x - side.x, b.y - side.y, b.z - side.z).setColor(r, g, bl, alpha);
    }

    /** A soft horizontal disc of light (additive): the lit cloud base, or the flash on the ground. */
    private static void glow(VertexConsumer vc, Matrix4f m, float x, float y, float z, float radius, float r, float g, float b, float alpha) {
        int n = 24;
        for (int i = 0; i < n; i++) {
            float a0 = (float) (i * Math.PI * 2 / n);
            float a1 = (float) ((i + 1) * Math.PI * 2 / n);
            vc.addVertex(m, x, y, z).setColor(r, g, b, alpha);
            vc.addVertex(m, x, y, z).setColor(r, g, b, alpha);
            vc.addVertex(m, x + (float) Math.cos(a1) * radius, y, z + (float) Math.sin(a1) * radius).setColor(r, g, b, 0F);
            vc.addVertex(m, x + (float) Math.cos(a0) * radius, y, z + (float) Math.sin(a0) * radius).setColor(r, g, b, 0F);
        }
    }

    @Override
    public boolean shouldRender(LightningBolt bolt, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(LightningBolt bolt) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
