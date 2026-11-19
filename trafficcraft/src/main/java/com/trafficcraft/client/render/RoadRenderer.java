package com.trafficcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trafficcraft.TrafficCraft;
import com.trafficcraft.road.ClientRoads;
import com.trafficcraft.road.EdgeGeom;
import com.trafficcraft.road.Lane;
import com.trafficcraft.road.NodeType;
import com.trafficcraft.road.RoadEdge;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.road.RoadToolItem;
import com.trafficcraft.road.TrafficLights;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Draws curved road markings (cached) and the Road Planner editing overlay. */
@EventBusSubscriber(modid = TrafficCraft.MODID, value = Dist.CLIENT)
public final class RoadRenderer {
    private static final int WHITE = 0xFFEDEDE6, YELLOW = 0xFFF0C020, BLUE = 0xFF3A70D0;
    private static final float WU = 2 / 256f, WV = 2 / 256f;

    /** One cached marking quad. */
    record Quad(float[] p, int color, int light) {}

    record EdgeCache(double cx, double cz, double r, List<Quad> quads) {}

    private static final List<EdgeCache> CACHE = new ArrayList<>();
    private static int cachedVersion = -1;
    private static long cachedTime;

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RoadNetwork net = ClientRoads.net;
        if (!ClientRoads.dimension.equals(mc.level.dimension().location().toString())) return;
        long now = mc.level.getGameTime();
        if (net.version != cachedVersion || now - cachedTime > 200) {
            rebuild(net);
            cachedVersion = net.version;
            cachedTime = now;
        }
        Vec3 cam = e.getCamera().getPosition();
        // this stage runs before the level renderer applies the view matrix to RenderSystem's model-view stack
        PoseStack ps = new PoseStack();
        ps.mulPose(e.getModelViewMatrix());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        RenderType rt = RenderType.entityCutoutNoCull(VehicleRenderer.ATLAS);
        VertexConsumer vc = buf.getBuffer(rt);
        PoseStack.Pose pose = ps.last();
        double max = 160;
        for (EdgeCache c : CACHE) {
            double dx = c.cx - cam.x, dz = c.cz - cam.z;
            if (dx * dx + dz * dz > (max + c.r) * (max + c.r)) continue;
            for (Quad q : c.quads) {
                float[] p = q.p;
                for (int k = 0; k < 4; k++) {
                    vc.addVertex(pose, p[k * 3], p[k * 3 + 1], p[k * 3 + 2]).setColor(q.color).setUv(WU, WV).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(q.light).setNormal(pose, 0, 1, 0);
                }
            }
        }
        buf.endBatch(rt);
        if (holdingTool(mc)) overlay(net, ps, buf, cam);
        ps.popPose();
    }

    static boolean holdingTool(Minecraft mc) {
        return mc.player != null && (mc.player.getMainHandItem().getItem() instanceof RoadToolItem || mc.player.getOffhandItem().getItem() instanceof RoadToolItem);
    }

    // ------------------------------------------------------------------------------------ marking geometry
    static void rebuild(RoadNetwork net) {
        CACHE.clear();
        Minecraft mc = Minecraft.getInstance();
        for (RoadEdge e : net.edges.values()) {
            if (e.ped || e.lanes.isEmpty()) continue;
            EdgeGeom g = net.geom(e);
            if (g == null) continue;
            List<Quad> qs = new ArrayList<>();
            double s0 = g.trimA, s1 = g.length - g.trimB;
            double W = e.width;
            RoadNode na = net.nodes.get(e.a), nb = net.nodes.get(e.b);
            boolean jA = net.isJunction(e.a), jB = net.isJunction(e.b);
            double cwA = jA && na.has(RoadNode.F_CROSSWALK) ? 3.6 : 0, cwB = jB && nb.has(RoadNode.F_CROSSWALK) ? 3.6 : 0;
            double ls0 = s0, ls1 = s1;
            // edge lines
            ribbon(qs, g, ls0, ls1, W / 2 - 0.25, 0.14, WHITE, 0, 0);
            ribbon(qs, g, ls0, ls1, -(W / 2 - 0.25), 0.14, WHITE, 0, 0);
            // lane separators / centre line
            float lw = e.laneWidth();
            double stopA = s0 + cwA + 0.4, stopB = s1 - cwB - 0.4;
            for (int i = 0; i + 1 < e.lanes.size(); i++) {
                double off = W / 2 - (i + 1) * lw;
                boolean opposite = e.lanes.get(i).dir() != e.lanes.get(i + 1).dir();
                double a = jA ? stopA : ls0, b = jB ? stopB : ls1;
                if (opposite) {
                    if (e.overtake) ribbon(qs, g, a, b, off, 0.14, WHITE, 3.0, 4.5);
                    else {
                        ribbon(qs, g, a, b, off + 0.12, 0.11, WHITE, 0, 0);
                        ribbon(qs, g, a, b, off - 0.12, 0.11, WHITE, 0, 0);
                    }
                } else {
                    // solid near junctions (no lane changes), dashed elsewhere
                    ribbon(qs, g, a + (jA ? 12 : 0), b - (jB ? 12 : 0), off, 0.12, WHITE, 2.0, 4.0);
                    if (jB) ribbon(qs, g, Math.max(a, b - 12), b, off, 0.12, WHITE, 0, 0);
                    if (jA) ribbon(qs, g, a, Math.min(b, a + 12), off, 0.12, WHITE, 0, 0);
                }
            }
            // stop lines, crosswalks and arrows at junction ends
            for (int end = 0; end < 2; end++) {
                boolean atB = end == 1;
                int node = atB ? e.b : e.a;
                if (!(atB ? jB : jA)) continue;
                int dirInto = atB ? 1 : -1;
                double sEnd = atB ? s1 : s0;
                double sign = atB ? -1 : 1;
                double cw = atB ? cwB : cwA;
                double stop = sEnd + sign * (cw + 0.4);
                for (int i = 0; i < e.lanes.size(); i++) {
                    Lane l = e.lanes.get(i);
                    if (l.dir() != dirInto) continue;
                    double off = e.laneOffset(i);
                    transverse(qs, g, stop, off, lw * 0.96, 0.4, WHITE);
                    arrow(qs, net, e, node, i, g, stop + sign * 6.5, off, atB);
                }
                if (cw > 0) {
                    double from = Math.min(sEnd, sEnd + sign * cw), to = Math.max(sEnd, sEnd + sign * cw);
                    for (double o = -W / 2 + 0.5; o <= W / 2 - 0.4; o += 1.0) ribbon(qs, g, from + 0.2, to - 0.2, o, 0.5, WHITE, 0, 0);
                }
            }
            // parking markings for parking nodes at either end are generated by the parking system cache
            Vec3 mid = g.point(g.length / 2, 0);
            CACHE.add(new EdgeCache(mid.x, mid.z, g.length / 2 + W, light(mc, qs)));
        }
        ParkingMarkings.build(net, CACHE_ADDER);
    }

    static final java.util.function.BiConsumer<Vec3, List<float[]>> CACHE_ADDER = (center, quads) -> {
        List<Quad> qs = new ArrayList<>();
        for (float[] q : quads) qs.add(new Quad(java.util.Arrays.copyOf(q, 12), Float.floatToRawIntBits(q[12]), 0));
        CACHE.add(new EdgeCache(center.x, center.z, 24, light(Minecraft.getInstance(), qs)));
    };

    static List<Quad> light(Minecraft mc, List<Quad> qs) {
        List<Quad> out = new ArrayList<>(qs.size());
        for (Quad q : qs) {
            float[] p = q.p;
            BlockPos bp = BlockPos.containing(p[0], p[1] + 0.2, p[2]);
            int l = mc.level != null ? LevelRenderer.getLightColor(mc.level, bp) : 0xF000F0;
            out.add(new Quad(p, q.color, l));
        }
        return out;
    }

    /** Longitudinal line at lateral offset; dash/gap = 0 for solid. */
    static void ribbon(List<Quad> qs, EdgeGeom g, double a, double b, double off, double w, int col, double dash, double gap) {
        if (b - a < 0.05) return;
        double step = 1.0;
        double s = a;
        boolean on = true;
        double phase = 0;
        while (s < b - 1e-6) {
            double segEnd;
            if (dash <= 0) segEnd = Math.min(b, s + step);
            else {
                double len = on ? dash : gap;
                segEnd = Math.min(b, s + Math.min(step, len - phase));
            }
            if (dash <= 0 || on) {
                Vec3 p0 = g.point(s, off + w / 2), p1 = g.point(segEnd, off + w / 2), p2 = g.point(segEnd, off - w / 2), p3 = g.point(s, off - w / 2);
                qs.add(quad(p0, p1, p2, p3, col));
            }
            if (dash > 0) {
                phase += segEnd - s;
                if (phase >= (on ? dash : gap) - 1e-6) {
                    on = !on;
                    phase = 0;
                }
            }
            s = segEnd;
        }
    }

    static void transverse(List<Quad> qs, EdgeGeom g, double s, double off, double width, double depth, int col) {
        Vec3 p0 = g.point(s - depth / 2, off + width / 2), p1 = g.point(s + depth / 2, off + width / 2), p2 = g.point(s + depth / 2, off - width / 2),
                p3 = g.point(s - depth / 2, off - width / 2);
        qs.add(quad(p0, p1, p2, p3, col));
    }

    static Quad quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int col) {
        float y = 0.018f;
        return new Quad(new float[]{(float) a.x, (float) a.y + y, (float) a.z, (float) b.x, (float) b.y + y, (float) b.z, (float) c.x, (float) c.y + y, (float) c.z,
                (float) d.x, (float) d.y + y, (float) d.z}, col, 0);
    }

    /** Turn arrow painted on lane i approaching node, showing the movements possible from that lane. */
    static void arrow(List<Quad> qs, RoadNetwork net, RoadEdge e, int node, int lane, EdgeGeom g, double sTip, double off, boolean atB) {
        Vec3 in = TrafficLights.arrivalDir(net, e, node);
        boolean left = false, right = false, straight = false;
        for (RoadEdge o : net.roadEdgesOf(node)) {
            if (o == e || !o.allows(node)) continue;
            int m = TrafficLights.movement(in, TrafficLights.departDir(net, o, node));
            if (m == TrafficLights.MOVE_LEFT) left = true;
            else if (m == TrafficLights.MOVE_RIGHT) right = true;
            else if (m == TrafficLights.MOVE_STRAIGHT) straight = true;
        }
        List<Integer> lanes = e.lanesFrom(e.other(node)); // right-most first
        int pos = lanes.indexOf(lane);
        int count = lanes.size();
        int type = e.lanes.get(lane).type();
        if (count > 1) {
            if (pos == 0) left = false;              // kerb lane: straight / right
            else if (pos == count - 1) right = false; // inner lane: straight / left
            else {
                left = false;
                right = false;
            }
        }
        if (type == Lane.LEFT_ONLY) {
            right = false;
            straight = false;
            left = true;
        }
        if (type == Lane.RIGHT_ONLY) {
            left = false;
            straight = false;
            right = true;
        }
        if (!left && !right && !straight) return;
        double dir = atB ? 1 : -1; // arrow points toward the node
        double tail = sTip - dir * 3.2;
        // shaft
        Vec3 a0 = g.point(tail, off + 0.1), a1 = g.point(sTip - dir * 1.0, off + 0.1), a2 = g.point(sTip - dir * 1.0, off - 0.1), a3 = g.point(tail, off - 0.1);
        qs.add(atB ? quad(a0, a1, a2, a3, WHITE) : quad(a3, a2, a1, a0, WHITE));
        if (straight) head(qs, g, sTip, off, dir, 0);
        double lSign = atB ? 1 : -1; // positive offset = left of A->B
        if (left) head(qs, g, sTip - dir * 1.6, off + lSign * 0.55, dir, lSign);
        if (right) head(qs, g, sTip - dir * 1.6, off - lSign * 0.55, dir, -lSign);
    }

    static void head(List<Quad> qs, EdgeGeom g, double s, double off, double dir, double side) {
        if (side == 0) {
            Vec3 t = g.point(s, off), l = g.point(s - dir * 1.0, off + 0.45), r = g.point(s - dir * 1.0, off - 0.45), c = g.point(s - dir * 0.75, off);
            qs.add(quad(t, l, c, r, WHITE));
        } else {
            // sideways arrow head + connector
            Vec3 c0 = g.point(s - dir * 0.15, off - side * 0.45), c1 = g.point(s + dir * 0.15, off - side * 0.45), c2 = g.point(s + dir * 0.15, off),
                    c3 = g.point(s - dir * 0.15, off);
            qs.add(quad(c0, c1, c2, c3, WHITE));
            Vec3 tip = g.point(s, off + side * 0.4), a = g.point(s - dir * 0.5, off), b = g.point(s + dir * 0.5, off), m = g.point(s, off + side * 0.15);
            qs.add(quad(tip, a, m, b, WHITE));
        }
    }

    // ------------------------------------------------------------------------------------ editor overlay
    static void overlay(RoadNetwork net, PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam) {
        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        PoseStack.Pose pose = ps.last();
        for (RoadNode n : net.nodes.values()) {
            if (n.pos().distanceToSqr(cam) > 128 * 128) continue;
            int c = n.type.color;
            float r = (c >> 16 & 255) / 255f, gg = (c >> 8 & 255) / 255f, b = (c & 255) / 255f;
            double h = n.type.ped ? 0.25 : 0.4;
            AABB box = new AABB(n.x - h, n.y, n.z - h, n.x + h, n.y + h * 2, n.z + h);
            if (ClientEditor.selected != null && ClientEditor.selected == n.id) box = box.inflate(0.12);
            LevelRenderer.renderLineBox(ps, lines, box, r, gg, b, 1f);
            if (n.has(RoadNode.F_LIGHTS)) LevelRenderer.renderLineBox(ps, lines, box.inflate(0.2).move(0, 0.6, 0), 1f, 0.3f, 0.2f, 1f);
            if (net.isJunction(n.id) && !n.type.ped) circle(lines, pose, n.pos(), net.junctionRadius(n.id), 0.9f, 0.9f, 0.3f);
        }
        for (RoadEdge e : net.edges.values()) {
            EdgeGeom g = net.geom(e);
            if (g == null) continue;
            Vec3 mid = g.point(g.length / 2, 0);
            if (mid.distanceToSqr(cam) > (128 + g.length) * (128 + g.length)) continue;
            float r = e.ped ? 0.4f : 1f, gg = e.ped ? 1f : 0.85f, b = e.ped ? 0.4f : 0.2f;
            for (double s = 0; s < g.length; s += 1.0) {
                Vec3 p0 = g.point(s, 0).add(0, 0.25, 0), p1 = g.point(Math.min(g.length, s + 0.6), 0).add(0, 0.25, 0);
                line(lines, pose, p0, p1, r, gg, b);
            }
            if (!e.ped) {
                for (int i = 0; i < e.lanes.size(); i++) {
                    double off = e.laneOffset(i);
                    int dir = e.lanes.get(i).dir();
                    for (double s = 2; s < g.length - 1; s += 4) {
                        Vec3 c = g.point(s, off).add(0, 0.12, 0);
                        Vec3 t = g.tangent(s).scale(dir);
                        Vec3 l = new Vec3(t.z, 0, -t.x);
                        Vec3 tip = c.add(t.scale(0.6));
                        line(lines, pose, c.add(l.scale(0.35)), tip, 0.3f, 0.8f, 1f);
                        line(lines, pose, c.add(l.scale(-0.35)), tip, 0.3f, 0.8f, 1f);
                    }
                }
            }
            if (e.ctrl != null) LevelRenderer.renderLineBox(ps, lines, new AABB(e.ctrl.x - 0.15, e.ctrl.y, e.ctrl.z - 0.15, e.ctrl.x + 0.15, e.ctrl.y + 0.3, e.ctrl.z + 0.15),
                    1f, 0.4f, 1f, 1f);
        }
        buf.endBatch(RenderType.lines());
    }

    static void line(VertexConsumer vc, PoseStack.Pose p, Vec3 a, Vec3 b, float r, float g, float bl) {
        Vec3 n = b.subtract(a).normalize();
        vc.addVertex(p, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, 1f).setNormal(p, (float) n.x, (float) n.y, (float) n.z);
        vc.addVertex(p, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, 1f).setNormal(p, (float) n.x, (float) n.y, (float) n.z);
    }

    static void circle(VertexConsumer vc, PoseStack.Pose p, Vec3 c, double r, float cr, float cg, float cb) {
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            line(vc, p, c.add(Math.cos(a0) * r, 0.1, Math.sin(a0) * r), c.add(Math.cos(a1) * r, 0.1, Math.sin(a1) * r), cr, cg, cb);
        }
    }
}
