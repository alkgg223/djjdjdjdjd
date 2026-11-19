package com.trafficcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.trafficcraft.block.BEBlock;
import com.trafficcraft.block.BarrierGateBE;
import com.trafficcraft.block.FacingBlock;
import com.trafficcraft.block.GenericBE;
import com.trafficcraft.block.TrafficLightBE;
import com.trafficcraft.road.ClientRoads;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.road.TrafficLights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Renderers for animated / data-driven infrastructure blocks. */
public final class BlockRenderers {
    static final float WU = 2 / 256f, WV = 2 / 256f;
    static final int FULL = 0xF000F0;

    static VertexConsumer solid(MultiBufferSource b) {
        return b.getBuffer(RenderType.entityCutoutNoCull(VehicleRenderer.ATLAS));
    }

    /** Axis-aligned box in the current pose. */
    static void box(VertexConsumer vc, PoseStack ps, float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light) {
        PoseStack.Pose p = ps.last();
        float[][] f = {
                {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1}, {x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1},
                {x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0}, {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0},
                {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0}, {x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0}};
        for (float[] q : f) {
            for (int k = 0; k < 4; k++) {
                vc.addVertex(p, q[k * 3], q[k * 3 + 1], q[k * 3 + 2]).setColor(argb).setUv(WU, WV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(p, q[12], q[13], q[14]);
            }
        }
    }

    static void disc(VertexConsumer vc, PoseStack ps, float cx, float cy, float z, float r, int argb, int light) {
        PoseStack.Pose p = ps.last();
        int n = 10;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            float[] pts = {cx, cy, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, cx, cy};
            for (int k = 0; k < 4; k++) {
                vc.addVertex(p, pts[k * 2], pts[k * 2 + 1], z).setColor(argb).setUv(WU, WV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, -1);
            }
        }
    }

    static void rotateToFacing(PoseStack ps, Direction f) {
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-f.toYRot() + 180));
        ps.translate(-0.5, 0, -0.5);
    }

    // ------------------------------------------------------------------------------------------------ traffic lights
    public static class TrafficLightRenderer implements BlockEntityRenderer<TrafficLightBE> {
        final Font font;

        public TrafficLightRenderer(BlockEntityRendererProvider.Context c) {
            font = c.getFont();
        }

        @Override
        public boolean shouldRenderOffScreen(TrafficLightBE be) {
            return true;
        }

        @Override
        public AABB getRenderBoundingBox(TrafficLightBE be) {
            return new AABB(be.getBlockPos()).inflate(1, 3, 1);
        }

        @Override
        public void render(TrafficLightBE be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            var mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Direction f = be.getBlockState().getValue(FacingBlock.FACING);
            long t = mc.level.getGameTime();
            RoadNode n = ClientRoads.net.nodes.get(be.node);
            int main = TrafficLights.RED, left = -1, cd = 0;
            boolean hasLeft = false;
            int ped = TrafficLights.RED;
            if (n != null && ClientRoads.net.edges.containsKey(be.edge)) {
                if (be.ped) {
                    int[] s = TrafficLights.pedSignal(ClientRoads.net, n, be.edge, t);
                    ped = s[0];
                    cd = s[1];
                } else {
                    int[] s = TrafficLights.signal(ClientRoads.net, n, be.edge, TrafficLights.MOVE_STRAIGHT, t);
                    main = s[0];
                    cd = s[1];
                    hasLeft = n.cfg.getBoolean("leftPhase");
                    if (hasLeft) left = TrafficLights.signal(ClientRoads.net, n, be.edge, TrafficLights.MOVE_LEFT, t)[0];
                }
            } else {
                main = TrafficLights.FLASH;
            }
            boolean blink = (t / 10) % 2 == 0;
            ps.pushPose();
            rotateToFacing(ps, f);
            VertexConsumer vc = solid(buf);
            int pole = 0xFF2A2C30;
            box(vc, ps, 0.44f, 1f, 0.44f, 0.56f, be.ped ? 2.0f : 2.6f, 0.56f, pole, light);
            if (be.ped) {
                ps.translate(0.5, 2.0, 0.36);
                box(vc, ps, -0.17f, 0f, -0.08f, 0.17f, 0.7f, 0.1f, 0xFF1A1B1D, light);
                boolean walk = ped == TrafficLights.GREEN || (ped == TrafficLights.YELLOW && blink);
                boolean stop = ped == TrafficLights.RED || (ped == TrafficLights.FLASH && blink);
                VertexConsumer lit = buf.getBuffer(RenderType.entityTranslucentEmissive(VehicleRenderer.ATLAS));
                box(stop ? lit : vc, ps, -0.12f, 0.38f, -0.1f, 0.12f, 0.64f, -0.085f, stop ? 0xFFFF2A1A : 0xFF3A1210, stop ? FULL : light);
                box(walk ? lit : vc, ps, -0.12f, 0.06f, -0.1f, 0.12f, 0.32f, -0.085f, walk ? 0xFF30FF60 : 0xFF0F2A14, walk ? FULL : light);
                if (cd > 0 && walk) text(ps, buf, String.valueOf(cd), 0, 0.2f, -0.105f, 0xFFFFFFFF);
                ps.popPose();
                return;
            }
            // main head facing the oncoming traffic
            ps.translate(0.5, 2.6, 0.36);
            head(vc, buf, ps, light, main, blink, 0, true);
            if (hasLeft) {
                ps.pushPose();
                ps.translate(0.38, 0, 0);
                head(vc, buf, ps, light, left, blink, 1, false);
                ps.popPose();
            }
            // right filter arrow (green while the main signal is green)
            ps.pushPose();
            ps.translate(-0.33, 0.05, 0);
            box(vc, ps, -0.12f, 0f, -0.08f, 0.12f, 0.3f, 0.1f, 0xFF1A1B1D, light);
            boolean rOn = main == TrafficLights.GREEN;
            arrowLamp(buf, vc, ps, 0, 0.15f, -0.1f, rOn ? 0xFF30FF60 : 0xFF0F2A14, rOn ? FULL : light, -1, rOn);
            ps.popPose();
            // countdown display
            box(vc, ps, -0.16f, -0.28f, -0.08f, 0.16f, -0.04f, 0.1f, 0xFF0A0A0B, light);
            if (cd > 0 && main != TrafficLights.FLASH && main != TrafficLights.OFF) text(ps, buf, String.valueOf(Math.min(99, cd)), 0, -0.16f, -0.085f,
                    main == TrafficLights.GREEN ? 0xFF40FF70 : main == TrafficLights.YELLOW ? 0xFFFFD040 : 0xFFFF4030);
            ps.popPose();
        }

        /** 3-lamp head; kind 0 = round lamps, 1 = left arrows. */
        void head(VertexConsumer vc, MultiBufferSource buf, PoseStack ps, int light, int sig, boolean blink, int kind, boolean visors) {
            box(vc, ps, -0.15f, 0f, -0.08f, 0.15f, 0.92f, 0.12f, 0xFF1A1B1D, light);
            box(vc, ps, -0.2f, -0.02f, 0.06f, 0.2f, 0.94f, 0.08f, 0xFF101112, light);
            boolean red = sig == TrafficLights.RED || sig == TrafficLights.RED_YELLOW;
            boolean yel = sig == TrafficLights.YELLOW || sig == TrafficLights.RED_YELLOW || (sig == TrafficLights.FLASH && blink);
            boolean grn = sig == TrafficLights.GREEN;
            float[] ys = {0.76f, 0.46f, 0.16f};
            int[] on = {0xFFFF2A1A, 0xFFFFB020, 0xFF30FF60}, off = {0xFF3A1210, 0xFF3A2A0A, 0xFF0F2A14};
            boolean[] st = {red, yel, grn};
            VertexConsumer lit = buf.getBuffer(RenderType.entityTranslucentEmissive(VehicleRenderer.ATLAS));
            for (int i = 0; i < 3; i++) {
                if (kind == 1) arrowLamp(buf, vc, ps, 0, ys[i], -0.085f, st[i] ? on[i] : off[i], st[i] ? FULL : light, 1, st[i]);
                else disc(st[i] ? lit : vc, ps, 0, ys[i], -0.085f, 0.1f, st[i] ? on[i] : off[i], st[i] ? FULL : light);
                if (visors) box(vc, ps, -0.12f, ys[i] + 0.1f, -0.2f, 0.12f, ys[i] + 0.12f, -0.08f, 0xFF101112, light);
            }
        }

        /** Arrow shaped lamp (dir +1 = left, -1 = right, seen by the approaching driver). */
        void arrowLamp(MultiBufferSource buf, VertexConsumer vc, PoseStack ps, float cx, float cy, float z, int col, int light, int dir, boolean on) {
            VertexConsumer v = on ? buf.getBuffer(RenderType.entityTranslucentEmissive(VehicleRenderer.ATLAS)) : vc;
            float d = dir;
            box(v, ps, cx - 0.06f, cy - 0.02f, z, cx + 0.06f, cy + 0.02f, z + 0.005f, col, light);
            box(v, ps, cx + d * 0.03f, cy - 0.07f, z, cx + d * 0.07f, cy + 0.07f, z + 0.005f, col, light);
        }

        void text(PoseStack ps, MultiBufferSource buf, String s, float x, float y, float z, int col) {
            ps.pushPose();
            ps.translate(x, y, z);
            ps.scale(-0.012f, -0.012f, -0.012f);
            font.drawInBatch(s, -font.width(s) / 2f, -4, col, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, FULL);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------------------------------------ barrier gate
    public static class BarrierRenderer implements BlockEntityRenderer<BarrierGateBE> {
        public BarrierRenderer(BlockEntityRendererProvider.Context c) {
        }

        @Override
        public boolean shouldRenderOffScreen(BarrierGateBE be) {
            return true;
        }

        @Override
        public AABB getRenderBoundingBox(BarrierGateBE be) {
            return new AABB(be.getBlockPos()).inflate(be.length + 1, 2, be.length + 1);
        }

        @Override
        public void render(BarrierGateBE be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            float a = be.animO + (be.anim - be.animO) * pt;
            Direction arm = be.armDir();
            ps.pushPose();
            ps.translate(0.5, 1.12, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(-arm.toYRot() + 90));
            // arm points along local +X; raise around Z axis
            ps.mulPose(Axis.ZP.rotationDegrees(a * 84f));
            VertexConsumer vc = solid(buf);
            float len = be.length + 0.4f;
            box(vc, ps, -0.25f, -0.09f, -0.09f, 0.3f, 0.09f, 0.09f, 0xFF2A2C30, light);
            int seg = Math.max(2, Math.round(len / 0.5f));
            for (int i = 0; i < seg; i++) {
                float x0 = 0.3f + i * (len - 0.3f) / seg, x1 = 0.3f + (i + 1) * (len - 0.3f) / seg;
                box(vc, ps, x0, -0.055f, -0.04f, x1, 0.055f, 0.04f, i % 2 == 0 ? 0xFFE02020 : 0xFFF2F2F2, light);
            }
            box(vc, ps, len - 0.1f, -0.25f, -0.03f, len, 0.06f, 0.03f, 0xFFF2F2F2, light);
            ps.popPose();
            // warning lamp on the post (blinks while moving or open)
            boolean moving = Math.abs(be.anim - be.animO) > 0.002f || be.open;
            var mc = Minecraft.getInstance();
            boolean on = moving && mc.level != null && (mc.level.getGameTime() / 6) % 2 == 0;
            ps.pushPose();
            VertexConsumer v2 = on ? buf.getBuffer(RenderType.entityTranslucentEmissive(VehicleRenderer.ATLAS)) : vc;
            box(v2, ps, 0.38f, 1.25f, 0.38f, 0.62f, 1.4f, 0.62f, on ? 0xFFFF9010 : 0xFF6A4010, on ? FULL : light);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------------------------------------ generic
    public static class GenericRenderer implements BlockEntityRenderer<GenericBE> {
        final Font font;

        public GenericRenderer(BlockEntityRendererProvider.Context c) {
            font = c.getFont();
        }

        @Override
        public AABB getRenderBoundingBox(GenericBE be) {
            return new AABB(be.getBlockPos()).inflate(8, 3, 8);
        }

        @Override
        public void render(GenericBE be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            if (!(be.getBlockState().getBlock() instanceof BEBlock b)) return;
            Direction f = be.getBlockState().getValue(FacingBlock.FACING);
            var mc = Minecraft.getInstance();
            switch (b.kind) {
                case "text_sign" -> {
                    ps.pushPose();
                    rotateToFacing(ps, f);
                    for (int side = 0; side < 2; side++) {
                        ps.pushPose();
                        if (side == 1) {
                            ps.translate(0.5, 0, 0.5);
                            ps.mulPose(Axis.YP.rotationDegrees(180));
                            ps.translate(-0.5, 0, -0.5);
                        }
                        float z = side == 0 ? 6.6f / 16 - 0.003f : (16 - 7.2f) / 16 - 0.003f;
                        for (int i = 0; i < 3; i++) {
                            String s = be.data.getString("line" + i);
                            if (s.isEmpty()) continue;
                            ps.pushPose();
                            ps.translate(0.5, 1.8 - i * 0.2, z);
                            ps.scale(-0.011f, -0.011f, -0.011f);
                            font.drawInBatch(s, -font.width(s) / 2f, 0, 0xFFFFFFFF, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, light);
                            ps.popPose();
                        }
                        ps.popPose();
                    }
                    ps.popPose();
                }
                case "camera" -> {
                    float t = (mc.level.getGameTime() + pt) * 0.02f + be.getBlockPos().hashCode() * 0.1f;
                    ps.pushPose();
                    rotateToFacing(ps, f);
                    ps.translate(0.5, 0.62, 0.55);
                    ps.mulPose(Axis.YP.rotationDegrees((float) Math.sin(t) * 40f));
                    ps.mulPose(Axis.XP.rotationDegrees(18));
                    VertexConsumer vc = solid(buf);
                    box(vc, ps, -0.09f, -0.08f, -0.32f, 0.09f, 0.08f, 0.05f, 0xFFE8E8E4, light);
                    box(vc, ps, -0.11f, 0.08f, -0.36f, 0.11f, 0.1f, 0.06f, 0xFFD0D0CC, light);
                    disc(vc, ps, 0, 0, -0.321f, 0.055f, 0xFF101418, light);
                    boolean rec = (mc.level.getGameTime() / 15) % 2 == 0;
                    box(rec ? buf.getBuffer(RenderType.entityTranslucentEmissive(VehicleRenderer.ATLAS)) : vc, ps, 0.05f, 0.03f, -0.325f, 0.07f, 0.05f, -0.32f,
                            rec ? 0xFFFF2020 : 0xFF401010, rec ? FULL : light);
                    ps.popPose();
                }
                case "fuel_pump" -> pump(be, f, ps, buf, light, pt);
                default -> { }
            }
        }

        void pump(GenericBE be, Direction f, PoseStack ps, MultiBufferSource buf, int light, float pt) {
            var mc = Minecraft.getInstance();
            ps.pushPose();
            rotateToFacing(ps, f);
            String price = String.format("%.2f", com.trafficcraft.station.FuelStations.price(be.data) * 20) + "/20L";
            String litres = String.format("%.1f L", be.data.hasUUID("holder") ? be.data.getFloat("litres") : be.data.getFloat("lastLitres"));
            for (int side = 0; side < 2; side++) {
                ps.pushPose();
                if (side == 1) {
                    ps.translate(0.5, 0, 0.5);
                    ps.mulPose(Axis.YP.rotationDegrees(180));
                    ps.translate(-0.5, 0, -0.5);
                }
                ps.translate(0.5, 1.28, 4f / 16 - 0.004);
                ps.scale(-0.0075f, -0.0075f, -0.0075f);
                font.drawInBatch(price, -font.width(price) / 2f, 0, 0xFFFF6040, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, FULL);
                font.drawInBatch(litres, -font.width(litres) / 2f, 26, 0xFF70F070, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, FULL);
                ps.popPose();
            }
            ps.popPose();
            // hose + nozzle
            Vec3 base = Vec3.atLowerCornerOf(be.getBlockPos());
            Vec3 outlet = side(f, 0.82, 1.05);
            Player holder = be.data.hasUUID("holder") && mc.level != null ? mc.level.getPlayerByUUID(be.data.getUUID("holder")) : null;
            Vec3 end;
            if (holder != null) {
                float yaw = holder.getViewYRot(pt) * (float) Math.PI / 180f;
                Vec3 hp = holder.getPosition(pt).add(-Math.cos(yaw) * 0.38 - Math.sin(yaw) * 0.3, holder.getBbHeight() * 0.55, -Math.sin(yaw) * 0.38 + Math.cos(yaw) * 0.3);
                int vid = be.data.getInt("vehicle");
                if (vid > 0 && mc.level.getEntity(vid) instanceof com.trafficcraft.vehicle.VehicleEntity v) {
                    float[] fl = v.spec().filler;
                    hp = v.position().add(com.trafficcraft.vehicle.VehicleEntity.toWorldOffset(fl[0], fl[1], fl[2], v.getYRot()));
                }
                end = hp.subtract(base);
            } else {
                end = side(f, 0.86, 0.75);
            }
            VertexConsumer vc = solid(buf);
            int n = 16;
            Vec3 prev = outlet;
            double sag = holder == null ? 0.25 : 0.6;
            for (int i = 1; i <= n; i++) {
                double tt = (double) i / n;
                Vec3 p = outlet.add(end.subtract(outlet).scale(tt)).add(0, -Math.sin(Math.PI * tt) * sag, 0);
                segment(vc, ps, prev, p, 0.025f, 0xFF161616, light);
                prev = p;
            }
            ps.pushPose();
            ps.translate(end.x, end.y, end.z);
            box(vc, ps, -0.04f, -0.05f, -0.04f, 0.04f, 0.05f, 0.04f, 0xFF1C1C1C, light);
            box(vc, ps, -0.025f, -0.02f, 0.04f, 0.025f, 0.02f, 0.18f, 0xFFB0B4B8, light);
            box(vc, ps, -0.03f, -0.12f, -0.02f, 0.03f, -0.05f, 0.03f, 0xFFD02020, light);
            ps.popPose();
        }

        /** Point on the pump's left side panel (in block coordinates). */
        static Vec3 side(Direction f, double y, double out) {
            Vec3 l = switch (f) {
                case NORTH -> new Vec3(1, 0, 0);
                case SOUTH -> new Vec3(-1, 0, 0);
                case EAST -> new Vec3(0, 0, 1);
                default -> new Vec3(0, 0, -1);
            };
            return new Vec3(0.5, y, 0.5).add(l.scale(out * 0.5));
        }

        static void segment(VertexConsumer vc, PoseStack ps, Vec3 a, Vec3 b, float r, int col, int light) {
            ps.pushPose();
            Vec3 m = a.add(b).scale(0.5);
            ps.translate(m.x, m.y, m.z);
            Vec3 d = b.subtract(a);
            float len = (float) d.length();
            float yaw = (float) Math.atan2(d.x, d.z);
            float pitch = (float) Math.atan2(-d.y, Math.hypot(d.x, d.z));
            ps.mulPose(Axis.YP.rotation(yaw));
            ps.mulPose(Axis.XP.rotation(pitch));
            box(vc, ps, -r, -r, -len / 2 - 0.01f, r, r, len / 2 + 0.01f, col, light);
            ps.popPose();
        }
    }
}
