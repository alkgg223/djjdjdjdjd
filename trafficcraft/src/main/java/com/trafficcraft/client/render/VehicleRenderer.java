package com.trafficcraft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.trafficcraft.TCConfig;
import com.trafficcraft.TrafficCraft;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehicleSpec;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class VehicleRenderer extends EntityRenderer<VehicleEntity> {
    public static final ResourceLocation ATLAS = TrafficCraft.id("textures/vehicle/atlas.png");
    public static final ResourceLocation DASH = TrafficCraft.id("textures/vehicle/dash.png");
    private static final float[] MATCAP = {64 / 256f, 128 / 256f, 128 / 256f, 192 / 256f};
    private static final float WHITE_U = 2 / 256f, WHITE_V = 2 / 256f;

    private final net.minecraft.client.model.PlayerModel<net.minecraft.world.entity.LivingEntity> driverWide, driverSlim;
    private static final ResourceLocation OFFICER_SKIN = TrafficCraft.id("textures/entity/ped/officer.png");

    public VehicleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        driverWide = new net.minecraft.client.model.PlayerModel<>(ctx.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false);
        driverSlim = new net.minecraft.client.model.PlayerModel<>(ctx.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM), true);
        this.shadowRadius = 1.1f;
        this.shadowStrength = 0.6f;
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleEntity e) {
        return tex(e.spec());
    }

    private static final java.util.Map<String, ResourceLocation> TEX = new java.util.HashMap<>();

    /** The vehicle's own texture (Blockbench pipeline) or the shared atlas (older generated vehicles). */
    static ResourceLocation tex(VehicleSpec s) {
        if (s.texture == null) return ATLAS;
        return TEX.computeIfAbsent(s.texture, t -> TrafficCraft.id("textures/vehicle/" + t + ".png"));
    }

    @Override
    public boolean shouldRender(VehicleEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        double max = TCConfig.RENDER_DISTANCE.get();
        if (e.distanceToSqr(x, y, z) > max * max) return false;
        return f.isVisible(e.getBoundingBox().inflate(e.spec().length / 2));
    }

    @Override
    public void render(VehicleEntity v, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        VehicleSpec s = v.spec();
        Minecraft mc = Minecraft.getInstance();
        double dist = Math.sqrt(v.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()));
        boolean inside = mc.player != null && mc.player.getVehicle() == v;
        int detail = TCConfig.INTERIOR_DISTANCE.get();
        int lod = (dist < detail || inside) ? 0 : 1;
        VehicleMesh mesh = VehicleMesh.get(v.getModel(), lod);
        if (mesh.failed && lod == 1) mesh = VehicleMesh.get(v.getModel(), 0);
        if (mesh.failed) return;
        float ry = Mth.rotLerp(pt, v.yRotO, v.getYRot());
        smoke(v, ps, buffers, pt, light);
        sparks(v, ps, buffers, pt);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-ry + Mth.lerp(pt, v.driftVisO, v.driftVis)));
        if (s.bike) ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, v.leanO, v.lean))); // lean about the tyre contact line
        PoseStack.Pose base = ps.last();
        float roll = Mth.lerp(pt, v.bodyRollO, v.bodyRoll), pitch = Mth.lerp(pt, v.bodyPitchO, v.bodyPitch);
        ps.pushPose();
        ps.translate(0, 0.45, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.translate(0, -0.45, 0);
        PoseStack.Pose bodyPose = ps.last();
        LampState lamps = new LampState(v);

        ShaderInstance sh = VehicleShaders.vehicle;
        if (sh != null) {
            drawGpu(v, mesh, sh, base, bodyPose, light, lamps, lod, inside, ps);
        }
        if (dist < 40 || inside) overlays(v, s, ps, buffers, light, lamps, dist, inside);
        if (v.hasAiDriver() && (dist < 64 || inside) && !s.seats.isEmpty()) drawDriver(v, s, ps, buffers, light);
        if (!v.bfx.isEmpty() && !s.exhaustsFor(v.cfg("ex")).isEmpty() && dist < 96) flames(v, s, ps, buffers, pt);
        if (!s.popups.isEmpty()) {
            float target = lamps.on(VehicleMesh.HEAD) || lamps.on(VehicleMesh.HIGH) ? 1f : 0f;
            v.popupVis += (target - v.popupVis) * Math.min(1f, mc.getTimer().getRealtimeDeltaTicks() * 0.12f);
        }
        ps.popPose();
        if (TCConfig.HEADLIGHT_BEAMS.get() && dist < 96) beams(v, s, ps, buffers, lamps);
        ps.popPose();
        super.render(v, yaw, pt, ps, buffers, light);
    }

    // ---------------------------------------------------------------------------------------- AI chauffeur
    /** The AI driver: a seated player-shaped NPC with a vanilla default skin (police cars: an officer), hands following the wheel. */
    private void drawDriver(VehicleEntity v, VehicleSpec s, PoseStack ps, MultiBufferSource buf, int light) {
        ResourceLocation tex;
        boolean slim;
        if (s.siren) {
            tex = OFFICER_SKIN;
            slim = false;
        } else {
            net.minecraft.client.resources.PlayerSkin skin = net.minecraft.client.resources.DefaultPlayerSkin.get(v.getUUID());
            tex = skin.texture();
            slim = skin.model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        }
        var m = slim ? driverSlim : driverWide;
        if (s.bike) {
            BikeRiders.render(m, tex, v, 0, ps, buf, light);
            return;
        }
        for (var part : new net.minecraft.client.model.geom.ModelPart[]{m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) part.resetPose();
        float k = Mth.clamp(v.steerVis / 30f, -1, 1);
        float t = v.tickCount * 0.05f + v.getId();
        // hands on the wheel, turning with it
        m.rightArm.xRot = -1.22f - k * 0.38f;
        m.leftArm.xRot = -1.22f + k * 0.38f;
        m.rightArm.yRot = -0.32f;
        m.leftArm.yRot = 0.32f;
        m.rightArm.zRot = 0.05f;
        m.leftArm.zRot = -0.05f;
        // seated legs
        m.rightLeg.xRot = -1.4137f;
        m.rightLeg.yRot = 0.12f;
        m.leftLeg.xRot = -1.4137f;
        m.leftLeg.yRot = -0.12f;
        // looks into the turn, glances around now and then
        m.head.yRot = -k * 0.45f + Mth.sin(t * 0.37f) * Mth.sin(t * 0.11f) * 0.25f;
        m.head.xRot = 0.05f;
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
        m.young = false;
        m.riding = true;
        var seat = s.seats.get(0);
        float sc = TCConfig.SEATED_SCALE.get().floatValue();
        ps.pushPose();
        ps.translate(seat.x, seat.y - 0.6f * sc, seat.z);
        ps.scale(sc, sc, sc);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        ps.scale(-1, -1, 1);
        ps.translate(0, -1.501f, 0);
        m.renderToBuffer(ps, buf.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    // ---------------------------------------------------------------------------------------- backfire flames
    private static final ResourceLocation FLAME = TrafficCraft.id("textures/vehicle/flame.png");
    private static final ResourceLocation SMOKE = TrafficCraft.id("textures/vehicle/smoke.png");

    /** Exhaust sparks: tiny hot billboards in world space, cooling from yellow-white to red. */
    private void sparks(VehicleEntity v, PoseStack ps, MultiBufferSource buf, float pt) {
        if (v.sparks.isEmpty()) return;
        var cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        double ex = Mth.lerp(pt, v.xo, v.getX()), ey = Mth.lerp(pt, v.yo, v.getY()), ez = Mth.lerp(pt, v.zo, v.getZ());
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        for (float[] p : v.sparks) {
            float life = Math.min(1f, (p[6] + pt) / p[7]);
            int rgb = life < 0.3f ? 0xFFF2C0 : life < 0.65f ? 0xFFB040 : 0xE04010;
            int a = (int) (255 * (1 - life * 0.8f));
            float sz = 0.016f * (1 - life * 0.5f);
            ps.pushPose();
            ps.translate(p[0] + p[3] * pt - ex, p[1] + p[4] * pt - ey, p[2] + p[5] * pt - ez);
            ps.mulPose(cam);
            PoseStack.Pose pose = ps.last();
            vert(vc, pose, -sz, -sz, 0, rgb, a);
            vert(vc, pose, sz, -sz, 0, rgb, a);
            vert(vc, pose, sz, sz, 0, rgb, a);
            vert(vc, pose, -sz, sz, 0, rgb, a);
            ps.popPose();
        }
    }

    /** Billowing tyre smoke (camera-facing textured puffs in world space). ps is at the entity's interpolated position. */
    private void smoke(VehicleEntity v, PoseStack ps, MultiBufferSource buf, float pt, int light) {
        if (v.smoke.isEmpty()) return;
        var cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        double ex = Mth.lerp(pt, v.xo, v.getX()), ey = Mth.lerp(pt, v.yo, v.getY()), ez = Mth.lerp(pt, v.zo, v.getZ());
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(SMOKE));
        for (float[] p : v.smoke) {
            float life = p[6] / p[7];
            float a = (life < 0.1f ? life / 0.1f : 1 - (life - 0.1f) / 0.9f) * 0.8f;
            int frame = Math.min(3, (int) (life * 4));
            float s = p[8];
            ps.pushPose();
            ps.translate(p[0] - ex, p[1] - ey, p[2] - ez);
            ps.mulPose(cam);
            ps.mulPose(Axis.ZP.rotationDegrees(p[6] * 3 + (p[7] * 37) % 360));
            PoseStack.Pose pose = ps.last();
            float u0 = frame / 4f, u1 = (frame + 1) / 4f;
            int g = p.length > 9 ? (int) (p[9] * 230) : 230;
            int c = ((int) (a * (p.length > 9 ? 0.9f : 1f) * 255) << 24) | g << 16 | g << 8 | g;
            vc.addVertex(pose, -s, -s, 0).setColor(c).setUv(u0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            vc.addVertex(pose, s, -s, 0).setColor(c).setUv(u1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            vc.addVertex(pose, s, s, 0).setColor(c).setUv(u1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            vc.addVertex(pose, -s, s, 0).setColor(c).setUv(u0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
            ps.popPose();
        }
    }

    private static final ResourceLocation FIREBALL = TrafficCraft.id("textures/vehicle/fireball.png");

    /**
     * Backfire effects at every tailpipe: white-hot flash and glow on the bumper, a turbulent flame tongue (4 growth frames),
     * a string of small flickers for a crackle, and a rolling fireball for a big bang. Sparks and the smoke puff are world-space.
     */
    private void flames(VehicleEntity v, VehicleSpec s, PoseStack ps, MultiBufferSource buf, float pt) {
        var exhausts = s.exhaustsFor(v.cfg("ex"));
        PoseStack.Pose pose = ps.last();
        for (float[] ev : v.bfx) {
            float age = v.tickCount - ev[0] + pt;
            int kind = (int) ev[1];
            float seed = ev[2];
            for (float[] e : exhausts) {
                Vector3f p = new Vector3f(e[0], e[1], e[2]);
                Vector3f d = new Vector3f(e[3], e[4], e[5]).normalize();
                if (kind == 1) {
                    for (int k = 0; k < (int) ev[3]; k++) {
                        float a = age - ev[4 + k];
                        if (a < 0 || a > 3.5f) continue;
                        float h = (float) Math.abs(Math.sin((seed + k) * 12.9898f) * 43758.5453f) % 1f;
                        tongue(buf, pose, p, d, a / 3.5f, 0.35f + 0.3f * h, 0.19f + 0.07f * h);
                        glowAt(buf, pose, ps, p, d, 0.22f, (int) (110 * (1 - a / 3.5f)));
                    }
                } else if (kind == 0) {
                    if (age < 5f) tongue(buf, pose, p, d, age / 5f, 0.55f + 0.25f * seed, 0.26f);
                    if (age < 2.5f) glowAt(buf, pose, ps, p, d, 0.32f, (int) (130 * (1 - age / 2.5f)));
                } else {
                    if (age < 4.5f) tongue(buf, pose, p, d, age / 4.5f, 1.05f + 0.3f * seed, 0.38f);
                    if (age < 3.5f) glowAt(buf, pose, ps, p, d, 0.6f, (int) (160 * (1 - age / 3.5f)));
                    if (age < 9f) fireball(buf, ps, p, d, age / 9f);
                }
            }
        }
    }

    private void tongue(MultiBufferSource buf, PoseStack.Pose pose, Vector3f p, Vector3f d, float life, float len, float w) {
        int frame = Mth.clamp((int) (life * 4), 0, 3);
        float grow = life < 0.25f ? 0.55f + life * 1.8f : 1f - (life - 0.25f) * 0.5f;
        float L = len * grow;
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(FLAME));
        float u0 = frame / 4f, u1 = (frame + 1) / 4f;
        Vector3f n1 = new Vector3f(d).cross(0, 1, 0);
        if (n1.lengthSquared() < 1e-4) n1.set(1, 0, 0);
        n1.normalize();
        Vector3f n2 = new Vector3f(d).cross(n1).normalize();
        for (Vector3f n : new Vector3f[]{n1, n2, new Vector3f(n1).add(n2).normalize(), new Vector3f(n1).sub(n2).normalize()}) {
            Vector3f a = new Vector3f(p).add(new Vector3f(n).mul(w / 2)), b = new Vector3f(p).add(new Vector3f(n).mul(-w / 2));
            Vector3f c = new Vector3f(b).add(new Vector3f(d).mul(L)), dd = new Vector3f(a).add(new Vector3f(d).mul(L));
            c.add(new Vector3f(n).mul(-w * 0.35f));
            dd.add(new Vector3f(n).mul(w * 0.35f));
            flameVertex(vc, pose, a, u0, 0f);
            flameVertex(vc, pose, b, u1, 0f);
            flameVertex(vc, pose, c, u1, 1f);
            flameVertex(vc, pose, dd, u0, 1f);
        }
    }

    private void glowAt(MultiBufferSource buf, PoseStack.Pose pose, PoseStack ps, Vector3f p, Vector3f d, float r, int a) {
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        glow(vc, pose, ps, p.x + d.x * 0.08f, p.y + d.y * 0.08f, p.z + d.z * 0.08f, r, 0xFFD890, a);
        glow(vc, pose, ps, p.x + d.x * 0.05f, p.y + d.y * 0.05f, p.z + d.z * 0.05f, r * 0.35f, 0xFFFFFF, Math.min(255, a + 40));
    }

    private void fireball(MultiBufferSource buf, PoseStack ps, Vector3f p, Vector3f d, float life) {
        var cam = entityRenderDispatcher.cameraOrientation();
        int frame = Mth.clamp((int) (life * 4), 0, 3);
        float size = 0.18f + 0.38f * (float) Math.sqrt(life);
        Vector3f c = new Vector3f(p).add(new Vector3f(d).mul(0.25f + life * 0.6f)).add(0, life * 0.35f, 0);
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(FIREBALL));
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        Matrix3f inv = new Matrix3f(ps.last().normal()).invert();
        Vector3f r = new Vector3f(1, 0, 0).rotate(cam).mul(inv).mul(size), u = new Vector3f(0, 1, 0).rotate(cam).mul(inv).mul(size);
        PoseStack.Pose pose = ps.last();
        float u0 = frame / 4f, u1 = (frame + 1) / 4f;
        flameVertex(vc, pose, new Vector3f(r).add(u).negate(), u0, 1f);
        flameVertex(vc, pose, new Vector3f(r).sub(u), u1, 1f);
        flameVertex(vc, pose, new Vector3f(r).add(u), u1, 0f);
        flameVertex(vc, pose, new Vector3f(u).sub(r), u0, 0f);
        ps.popPose();
    }

    private static void flameVertex(VertexConsumer vc, PoseStack.Pose pose, Vector3f p, float u, float vv) {
        vc.addVertex(pose, p.x, p.y, p.z).setColor(255, 255, 255, 255).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                .setNormal(pose, 0, 1, 0);
    }

    // ---------------------------------------------------------------------------------------- GPU path
    private void drawGpu(VehicleEntity v, VehicleMesh mesh, ShaderInstance sh, PoseStack.Pose base, PoseStack.Pose body, int light, LampState lamps, int lod,
                         boolean inside, PoseStack ps) {
        mesh.ensureUploaded();
        VehicleSpec s = v.spec();
        Minecraft mc = Minecraft.getInstance();
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        ResourceLocation texture = tex(s);
        RenderSystem.setShaderTexture(0, texture);
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        mc.gameRenderer.overlayTexture().setupOverlayColor();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        int lu = light & 0xFFFF, lv = (light >> 16) & 0xFFFF;
        set(sh, "LightUV", lu, lv);
        float[] mcp = s.matcap != null ? s.matcap : MATCAP;
        set4(sh, "MatcapRect", mcp[0], mcp[1], mcp[2], mcp[3]);
        // livery drawn by the shader on the paint (Halloween decor brings the pumpkin livery with it)
        int livery = v.cfg("lv");
        if (livery == 0 && v.cfg("dc") == 2) livery = 7;
        set1(sh, "Livery", livery);
        set4(sh, "LiveryDims", s.length / 2, s.width / 2, s.height, v.getId() % 97);
        float steerVis = v.steerVis;
        float spin = v.wheelSpin;
        for (int pass = 0; pass < 2; pass++) {
            boolean translucent = pass == 1;
            if (translucent) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.depthMask(false);
            }
            for (var en : mesh.parts.entrySet()) {
                String part = en.getKey();
                if (part.equals("interior") && lod > 0) continue;
                int ci = part.indexOf(':');
                if (ci > 0) {
                    String slot = part.substring(0, ci);
                    int idx;
                    try { idx = Integer.parseInt(part.substring(ci + 1)); } catch (NumberFormatException ex) { idx = 0; }
                    if (v.cfg(slot.equals("wheel") ? "wh" : slot) != idx) continue;
                }
                List<VehicleMesh.Group> groups = en.getValue();
                boolean wheelPart = part.equals("wheel") || part.startsWith("wheel:");
                if (wheelPart || part.equals("caliper")) {
                    for (VehicleSpec.Wheel w : s.wheels) {
                        Matrix4f m = new Matrix4f(base.pose()).translate(w.x(), w.y(), w.z());
                        Matrix3f nm = new Matrix3f(base.normal());
                        if (w.steer()) {
                            m.rotateY((float) Math.toRadians(steerVis));
                            nm.rotateY((float) Math.toRadians(steerVis));
                        }
                        if (wheelPart) {
                            float a = (float) Math.toRadians(spin);
                            m.rotateX(a);
                            nm.rotateX(a);
                        }
                        if (w.mirror()) {
                            m.scale(-1, 1, 1);
                            nm.scale(-1, 1, 1);
                        }
                        drawGroups(sh, groups, view, proj, m, nm, light, lamps, v, translucent);
                    }
                    continue;
                }
                Matrix4f m = new Matrix4f(body.pose());
                Matrix3f nm = new Matrix3f(body.normal());
                if (part.equals("steer")) {
                    float[] p = s.steerPivot, ax = s.steerAxis;
                    float ang = (float) Math.toRadians(s.bike ? steerVis : -steerVis * 13f);
                    m.translate(p[0], p[1], p[2]).rotate(ang, ax[0], ax[1], ax[2]).translate(-p[0], -p[1], -p[2]);
                    nm.rotate(ang, ax[0], ax[1], ax[2]);
                } else if (part.equals("shifter") && s.shifterPivot != null) {
                    float[] p = s.shifterPivot;
                    float[] ang = shifterAngles(s.shifterType, v.visGear());
                    m.translate(p[0], p[1], p[2]).rotateX((float) Math.toRadians(ang[0])).rotateZ((float) Math.toRadians(ang[1])).translate(-p[0], -p[1], -p[2]);
                    nm.rotateX((float) Math.toRadians(ang[0])).rotateZ((float) Math.toRadians(ang[1]));
                } else if (part.startsWith("popup_") && s.popups.containsKey(part)) {
                    float[] pp = s.popups.get(part);
                    float a = (float) Math.toRadians(pp[2] * v.popupVis);
                    m.translate(0, pp[0], pp[1]).rotateX(a).translate(0, -pp[0], -pp[1]);
                    nm.rotateX(a);
                } else if (part.startsWith("door_")) {
                    VehicleSpec.Door d = door(s, part);
                    if (d != null) {
                        float open = doorOpen(v, d);
                        if (open > 0.001f) {
                            if (d.kind().equals("slide")) {
                                m.translate(d.side() * 0.12f * Math.min(1, open * 3), 0, -open * 0.9f);
                            } else {
                                float ang = (float) Math.toRadians(open * 62f * d.side());
                                m.translate(d.hingeX(), 0, d.hingeZ()).rotateY(ang).translate(-d.hingeX(), 0, -d.hingeZ());
                                nm.rotateY(ang);
                            }
                        }
                    }
                }
                drawGroups(sh, groups, view, proj, m, nm, light, lamps, v, translucent);
            }
        }
        VertexBufferHelper.unbind();
        RenderSystem.setShaderTexture(0, ATLAS);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        set1(sh, "Emissive", 0);
        set1(sh, "Gloss", 0);
        set1(sh, "PaintMask", 0);
        set1(sh, "Livery", 0);
    }

    private void drawGroups(ShaderInstance sh, List<VehicleMesh.Group> groups, Matrix4f view, Matrix4f proj, Matrix4f model, Matrix3f nm, int light,
                            LampState lamps, VehicleEntity v, boolean translucent) {
        Matrix4f mv = new Matrix4f(view).mul(model);
        Uniform3 u = new Uniform3(sh);
        u.normal(nm);
        for (VehicleMesh.Group g : groups) {
            if (g.vbo == null) continue;
            boolean isGlass = g.mat == VehicleMesh.GLASS;
            if (isGlass != translucent) continue;
            float r = 1, gg = 1, b = 1, a = 1, gloss = 0;
            boolean emissive = false;
            set1(sh, "PaintMask", (g.mat == VehicleMesh.PAINT || g.mat == VehicleMesh.PAINT2) && v.spec().texture != null ? 1 : 0);
            switch (g.mat) {
                case VehicleMesh.PAINT, VehicleMesh.PAINT2 -> {
                    int c = g.mat == VehicleMesh.PAINT ? v.getColor() : v.getColor2();
                    r = (c >> 16 & 255) / 255f;
                    gg = (c >> 8 & 255) / 255f;
                    b = (c & 255) / 255f;
                    gloss = 0.32f + 0.25f * (1 - Math.max(r, Math.max(gg, b)));
                }
                case VehicleMesh.GLASS -> gloss = 0.18f;
                case VehicleMesh.SOLID -> gloss = 0.0f;
                case VehicleMesh.SCREEN -> {
                    if (v.spec().texture == null) RenderSystem.setShaderTexture(0, DASH);
                    emissive = v.has(VehicleEntity.F_ENGINE) || v.has(VehicleEntity.F_STARTING);
                    if (!emissive) {
                        r = gg = b = 0.12f;
                    }
                }
                default -> {
                    float lvl = lamps.level(g.mat);
                    emissive = lvl > 0.01f;
                    if (emissive) {
                        r = gg = b = Math.min(1f, 0.55f + 0.45f * lvl);
                        gloss = 0.15f * lvl;
                    } else {
                        r = gg = b = 0.78f;
                        gloss = 0.35f;
                    }
                }
            }
            RenderSystem.setShaderColor(r, gg, b, a);
            set1(sh, "Emissive", emissive ? 1 : 0);
            set1(sh, "Gloss", gloss);
            g.vbo.bind();
            g.vbo.drawWithShader(mv, proj, sh);
            if (g.mat == VehicleMesh.SCREEN) RenderSystem.setShaderTexture(0, tex(v.spec()));
        }
    }

    /** Gear lever tilt (forward/back about X, left/right about Z) for the selected gear: H-pattern or automatic gate. */
    static float[] shifterAngles(String type, int gear) {
        if (type.equals("auto")) {
            // P R N D along the gate (driving = D)
            float a = gear < 0 ? 8f : gear == 0 ? 0f : -12f;
            return new float[]{a, 0};
        }
        if (gear == 0) return new float[]{0, 0};
        if (gear < 0) return new float[]{-14f, -14f};          // reverse: right and back
        int col = (Math.min(gear, 6) - 1) / 2;                 // 1-2 left, 3-4 centre, 5-6 right
        boolean fwd = gear % 2 == 1;
        return new float[]{fwd ? 14f : -14f, (1 - col) * 12f};
    }

    private static VehicleSpec.Door door(VehicleSpec s, String part) {
        for (VehicleSpec.Door d : s.doors) if (d.part().equals(part)) return d;
        return null;
    }

    /** 0..1 door opening, animated for ~1.4 s after a passenger got in or out on that side. */
    private static float doorOpen(VehicleEntity v, VehicleSpec.Door d) {
        boolean left = d.side() > 0;
        int t = v.tickCount - v.doorTick(left);
        if (t < 0 || t > 28) return 0;
        // only the door nearest to the seat used (front doors for row 0)
        if (!d.part().startsWith("door_f") && !d.part().startsWith("door_s")) return 0;
        float x = t / 28f;
        return (float) Math.sin(Math.PI * x) * (x < 0.5f ? 1 : 1);
    }

    // ---------------------------------------------------------------------------------------- overlays
    private void overlays(VehicleEntity v, VehicleSpec s, PoseStack ps, MultiBufferSource buf, int light, LampState lamps, double dist, boolean inside) {
        Font font = Minecraft.getInstance().font;
        // licence plates
        if (dist < 16) {
            for (float[] p : s.plates) {
                ps.pushPose();
                ps.translate(p[0], p[1], p[2]);
                float yaw = (float) Math.toDegrees(Math.atan2(p[3], p[5]));
                ps.mulPose(Axis.YP.rotationDegrees(yaw + 180));
                float sc = p[7] * 0.0105f;
                ps.scale(-sc, -sc, -sc);
                String t = v.getPlate();
                int w = font.width(t);
                ps.translate(-w / 2f + 3, -4, 0);
                font.drawInBatch(t, 0, 0, 0xFF1A1A1E, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, lamps.on(VehicleMesh.PLATE_LIGHT) ? 0xF000F0 : light);
                ps.popPose();
            }
        }
        if (s.screen != null && (inside || dist < 8)) com.trafficcraft.client.gui.CarScreen.render(v, s, ps, buf);
        // instrument cluster needles + digital readouts
        if (s.cluster != null && (inside || dist < 5) && (v.has(VehicleEntity.F_ENGINE) || v.has(VehicleEntity.F_STARTING))) {
            float[] c = s.cluster;
            Vector3f ctr = new Vector3f(c[0], c[1], c[2]), rt = new Vector3f(c[3], c[4], c[5]), up = new Vector3f(c[6], c[7], c[8]);
            float cw = c[9], ch = c[10];
            Vector3f nrm = new Vector3f(rt).cross(up).normalize();
            Vector3f origin = new Vector3f(ctr).sub(new Vector3f(rt).mul(cw / 2)).add(new Vector3f(up).mul(ch / 2)).sub(new Vector3f(nrm).mul(0.003f));
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(ATLAS));
            float kmh = Math.abs(v.visSpeed()) * 3.6f;
            float rpm = v.visRpm();
            float fuel = v.getFuel() / Math.max(1, s.fuelCapacity);
            for (VehicleSpec.Dial d : s.dials) {
                float val = switch (d.kind()) {
                    case "speed", "speed_arc" -> kmh / d.max();
                    case "rpm", "rpm_arc" -> rpm / d.max();
                    case "fuel" -> fuel;
                    case "temp" -> v.has(VehicleEntity.F_ENGINE) ? 0.5f : 0.1f;
                    case "air" -> 0.75f;
                    default -> -1;
                };
                if (d.kind().endsWith("_bar")) {
                    float frac = d.kind().startsWith("fuel") ? fuel : Mth.clamp(rpm / Math.max(1, s.redRpm * 1.1f), 0, 1);
                    quadOnPanel(vc, ps, origin, rt, up, d.x() * cw, d.y() * ch, d.w() * cw * frac, d.h() * ch, frac < 0.15f ? 0xFFFF4030 : 0xFF60D070);
                    continue;
                }
                if (val >= 0) {
                    val = Mth.clamp(val, 0, 1.05f);
                    float a = (float) Math.toRadians(d.a0() + (d.a1() - d.a0()) * val);
                    float cx = d.cx() * cw, cy = d.cy() * ch;
                    float rr = d.r() * cw;
                    int col = 0xFF000000 | d.color();
                    needle(vc, ps, origin, rt, up, cx, cy, (float) Math.cos(a) * rr * 0.86f, (float) Math.sin(a) * rr * 0.86f, rr * 0.035f, col);
                }
            }
            // digital speed / gear text
            for (VehicleSpec.Dial d : s.dials) {
                if (!d.kind().equals("digital_speed") && !d.kind().equals("gear")) continue;
                String t = d.kind().equals("gear") ? gearText(v) : String.valueOf(Math.round(kmh));
                ps.pushPose();
                Vector3f p = new Vector3f(origin).add(new Vector3f(rt).mul(d.x() * cw)).sub(new Vector3f(up).mul(d.y() * ch)).sub(new Vector3f(nrm).mul(0.002f));
                ps.translate(p.x, p.y, p.z);
                Matrix3f basis = new Matrix3f(rt.x, rt.y, rt.z, -up.x, -up.y, -up.z, -nrm.x, -nrm.y, -nrm.z);
                ps.last().pose().mul(new Matrix4f(basis));
                float sc = d.size() * ch / 9f;
                ps.scale(sc, sc, sc);
                font.drawInBatch(t, -font.width(t) / 2f, -4, 0xFFFFFFFF, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, 0xF000F0);
                ps.popPose();
            }
        }
    }

    private static String gearText(VehicleEntity v) {
        int g = v.visGear();
        if (g < 0) return "R";
        if (Math.abs(v.visSpeed()) < 0.1f && v.has(VehicleEntity.F_HANDBRAKE)) return "P";
        return (v.has(VehicleEntity.F_MANUAL) ? "M" : "D") + g;
    }

    private static void quadOnPanel(VertexConsumer vc, PoseStack ps, Vector3f o, Vector3f rt, Vector3f up, float x, float y, float w, float h, int col) {
        Vector3f p0 = new Vector3f(o).add(new Vector3f(rt).mul(x)).sub(new Vector3f(up).mul(y));
        Vector3f p1 = new Vector3f(p0).add(new Vector3f(rt).mul(w));
        Vector3f p2 = new Vector3f(p1).sub(new Vector3f(up).mul(h));
        Vector3f p3 = new Vector3f(p0).sub(new Vector3f(up).mul(h));
        emit(vc, ps, p0, p1, p2, p3, col);
    }

    private static void needle(VertexConsumer vc, PoseStack ps, Vector3f o, Vector3f rt, Vector3f up, float cx, float cy, float dx, float dy, float w, int col) {
        Vector3f c = new Vector3f(o).add(new Vector3f(rt).mul(cx)).sub(new Vector3f(up).mul(cy));
        Vector3f dir = new Vector3f(rt).mul(dx).add(new Vector3f(up).mul(dy));
        Vector3f perp = new Vector3f(rt).mul(-dy).add(new Vector3f(up).mul(dx)).normalize().mul(w);
        Vector3f tip = new Vector3f(c).add(dir);
        Vector3f back = new Vector3f(c).sub(new Vector3f(dir).mul(0.15f));
        emit(vc, ps, new Vector3f(back).add(perp), new Vector3f(tip).add(new Vector3f(perp).mul(0.3f)), new Vector3f(tip).sub(new Vector3f(perp).mul(0.3f)),
                new Vector3f(back).sub(perp), col);
    }

    private static void emit(VertexConsumer vc, PoseStack ps, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int col) {
        PoseStack.Pose p = ps.last();
        for (Vector3f q : new Vector3f[]{a, b, c, d}) {
            vc.addVertex(p, q.x, q.y, q.z).setColor(col).setUv(WHITE_U, WHITE_V).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(p, 0, 1, 0);
        }
    }

    // ---------------------------------------------------------------------------------------- light beams & glows
    private void beams(VehicleEntity v, VehicleSpec s, PoseStack ps, MultiBufferSource buf, LampState lamps) {
        Minecraft mc = Minecraft.getInstance();
        float dark = 1f - Mth.clamp((mc.level.getSkyDarken(1f) - 0.3f) / 0.5f, 0, 1);
        float night = Mth.clamp(1.2f - mc.level.getSkyDarken(1f) * 1.2f, 0, 1);
        float vis = Math.max(night, 0.15f);
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        PoseStack.Pose p = ps.last();
        if (lamps.on(VehicleMesh.HEAD)) {
            boolean high = lamps.on(VehicleMesh.HIGH);
            float len = high ? 18f : 11f;
            for (var b : s.beams) {
                float x = (float) b.x, y = (float) b.y, z = (float) b.z + 0.05f;
                cone(vc, p, x, y, z, 3.2f, 0.32f, -0.05f, 0xFFF4E8, (int) (26 * vis), 0);
                glow(vc, p, ps, x, y, z + 0.02f, 0.32f, 0xFFF2DC, (int) (150 * vis));
            }
            // light pool on the road surface ahead
            float z0 = s.length / 2 + 0.6f;
            pool(vc, p, 0, 0.04f, z0 + len * 0.45f, s.width * 1.4f, len * 0.55f, 0xFFF0D8, (int) (70 * vis));
        }
        // brake / indicator / reverse / beacon glows (small halos)
        float hl = s.length / 2;
        if (lamps.level(VehicleMesh.BRAKE) > 0.8f) for (int sx : new int[]{1, -1}) glow(vc, p, ps, sx * s.width * 0.34f, 0.85f, -hl - 0.05f, 0.3f, 0xFF2010, (int) (110 * vis + 30));
        boolean blink = v.blinkOn();
        boolean hz = v.has(VehicleEntity.F_HAZARD);
        if (blink && (v.has(VehicleEntity.F_IND_L) || hz)) {
            glow(vc, p, ps, s.width * 0.42f, 0.75f, hl + 0.05f, 0.22f, 0xFF9010, (int) (110 * vis + 40));
            glow(vc, p, ps, s.width * 0.42f, 0.85f, -hl - 0.05f, 0.22f, 0xFF9010, (int) (110 * vis + 40));
        }
        if (blink && (v.has(VehicleEntity.F_IND_R) || hz)) {
            glow(vc, p, ps, -s.width * 0.42f, 0.75f, hl + 0.05f, 0.22f, 0xFF9010, (int) (110 * vis + 40));
            glow(vc, p, ps, -s.width * 0.42f, 0.85f, -hl - 0.05f, 0.22f, 0xFF9010, (int) (110 * vis + 40));
        }
        if (v.has(VehicleEntity.F_BEACONS)) {
            boolean ph = (v.tickCount / 3) % 2 == 0;
            glow(vc, p, ps, 0.35f, s.height + 0.15f, 0.0f, 0.6f, ph ? 0xFF2020 : 0x2040FF, 170);
            glow(vc, p, ps, -0.35f, s.height + 0.15f, 0.0f, 0.6f, ph ? 0x2040FF : 0xFF2020, 170);
        }
    }

    private static void pool(VertexConsumer vc, PoseStack.Pose p, float cx, float y, float cz, float rx, float rz, int rgb, int a) {
        int n = 18;
        for (int i = 0; i < n; i++) {
            double t0 = 2 * Math.PI * i / n, t1 = 2 * Math.PI * (i + 1) / n;
            vert(vc, p, cx, y, cz, rgb, a);
            vert(vc, p, cx + (float) Math.cos(t0) * rx, y, cz + (float) Math.sin(t0) * rz, rgb, 0);
            vert(vc, p, cx + (float) Math.cos(t1) * rx, y, cz + (float) Math.sin(t1) * rz, rgb, 0);
            vert(vc, p, cx, y, cz, rgb, a);
        }
    }

    private static void cone(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float len, float spread, float drop, int rgb, int a0, int a1) {
        int seg = 10;
        float r0 = 0.12f;
        float r1 = len * spread;
        for (int i = 0; i < seg; i++) {
            double t0 = 2 * Math.PI * i / seg, t1 = 2 * Math.PI * (i + 1) / seg;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            vert(vc, p, x + c0 * r0, y + s0 * r0 * 0.6f, z, rgb, a0);
            vert(vc, p, x + c0 * r1, y + s0 * r1 * 0.45f + len * drop, z + len, rgb, a1);
            vert(vc, p, x + c1 * r1, y + s1 * r1 * 0.45f + len * drop, z + len, rgb, a1);
            vert(vc, p, x + c1 * r0, y + s1 * r0 * 0.6f, z, rgb, a0);
        }
    }

    private void glow(VertexConsumer vc, PoseStack.Pose p, PoseStack ps, float x, float y, float z, float size, int rgb, int alpha) {
        // camera facing star: two crossed quads + one facing the camera-ish (cheap halo)
        var cam = entityRenderDispatcher.cameraOrientation();
        Vector3f r = new Vector3f(size, 0, 0), u = new Vector3f(0, size, 0);
        Matrix3f inv = new Matrix3f(p.normal()).invert();
        r = new Vector3f(1, 0, 0).rotate(cam).mul(size);
        u = new Vector3f(0, 1, 0).rotate(cam).mul(size);
        r.mul(inv);
        u.mul(inv);
        int n = 8;
        for (int i = 0; i < n; i++) {
            double t0 = 2 * Math.PI * i / n, t1 = 2 * Math.PI * (i + 1) / n;
            float ax = (float) Math.cos(t0), ay = (float) Math.sin(t0), bx = (float) Math.cos(t1), by = (float) Math.sin(t1);
            vert(vc, p, x, y, z, rgb, alpha);
            vert(vc, p, x + r.x * ax + u.x * ay, y + r.y * ax + u.y * ay, z + r.z * ax + u.z * ay, rgb, 0);
            vert(vc, p, x + r.x * bx + u.x * by, y + r.y * bx + u.y * by, z + r.z * bx + u.z * by, rgb, 0);
            vert(vc, p, x, y, z, rgb, alpha);
        }
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, int rgb, int a) {
        vc.addVertex(p, x, y, z).setColor((rgb >> 16 & 255), (rgb >> 8 & 255), rgb & 255, Mth.clamp(a, 0, 255));
    }

    // ---------------------------------------------------------------------------------------- uniforms
    static void set(ShaderInstance sh, String n, float a, float b) {
        var u = sh.getUniform(n);
        if (u != null) u.set(a, b);
    }

    static void set1(ShaderInstance sh, String n, float a) {
        var u = sh.getUniform(n);
        if (u != null) u.set(a);
    }

    static void set4(ShaderInstance sh, String n, float a, float b, float c, float d) {
        var u = sh.getUniform(n);
        if (u != null) u.set(a, b, c, d);
    }

    record Uniform3(ShaderInstance sh) {
        void normal(Matrix3f m) {
            var u = sh.getUniform("NormalMat");
            if (u != null) u.set(m);
        }
    }

    /** Which lamps are lit (0..1 intensity per material). */
    static final class LampState {
        final VehicleEntity v;
        final boolean blink, engine;

        LampState(VehicleEntity v) {
            this.v = v;
            this.blink = v.blinkOn();
            this.engine = v.has(VehicleEntity.F_ENGINE);
        }

        boolean on(int mat) {
            return level(mat) > 0.01f;
        }

        float level(int mat) {
            boolean low = v.has(VehicleEntity.F_LOW);
            boolean hz = v.has(VehicleEntity.F_HAZARD);
            return switch (mat) {
                case VehicleMesh.HEAD -> low ? 1f : 0f;
                case VehicleMesh.HIGH -> v.has(VehicleEntity.F_HIGH) ? 1f : 0f;
                case VehicleMesh.DRL -> engine ? (low ? 0.7f : 1f) : 0f;
                case VehicleMesh.TAIL, VehicleMesh.MARKER -> low || engine ? (low ? 0.9f : 0.5f) : 0f;
                case VehicleMesh.BRAKE -> v.has(VehicleEntity.F_BRAKE) && (engine || v.getControllingPassenger() != null) ? 1f : low ? 0.45f : 0f;
                case VehicleMesh.IND_L -> blink && (v.has(VehicleEntity.F_IND_L) || hz) ? 1f : 0f;
                case VehicleMesh.IND_R -> blink && (v.has(VehicleEntity.F_IND_R) || hz) ? 1f : 0f;
                case VehicleMesh.REVERSE -> v.has(VehicleEntity.F_REVERSE) && v.visSpeed() < 0.3f ? 1f : 0f;
                case VehicleMesh.INTERIOR_LIGHT -> v.has(VehicleEntity.F_INTERIOR) || v.tickCount - Math.max(v.doorTick(true), v.doorTick(false)) < 60 ? 1f : 0f;
                case VehicleMesh.PLATE_LIGHT -> low ? 1f : 0f;
                case VehicleMesh.BEACON_A -> v.has(VehicleEntity.F_BEACONS) && (v.tickCount / 3) % 2 == 0 ? 1f : 0f;
                case VehicleMesh.BEACON_B -> v.has(VehicleEntity.F_BEACONS) && (v.tickCount / 3) % 2 == 1 ? 1f : 0f;
                case VehicleMesh.TAXI_SIGN -> engine ? 1f : 0f;
                case VehicleMesh.DECO_A -> (v.tickCount / 8) % 2 == 0 ? 1f : 0.12f;
                case VehicleMesh.DECO_B -> (v.tickCount / 8) % 2 == 1 ? 1f : 0.12f;
                case VehicleMesh.DECO_Y -> 0.8f + 0.2f * (float) Math.abs(Math.sin(v.tickCount * 0.37 + v.getId()));
                default -> 0f;
            };
        }
    }
}
