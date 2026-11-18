package com.vehicleworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.vehicle.VehicleBuilder;
import com.vehicleworks.vehicle.VehicleControls;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Aircraft lights (navigation, beacon, strobe, landing / taxi / turn-off beams, cockpit) and the
 * overhead switch panel: 18 animated 3D switches whose levers, knobs and lamps follow the real
 * system state sent by the server.
 */
public final class CockpitRenderer {
    private CockpitRenderer() {}

    public static final int COLS = 6, ROWS = 3;
    private static final int FULL = 0xF000F0;

    // ------------------------------------------------------------------ glow primitives (additive, no texture)

    static void glowCube(Matrix4f m, VertexConsumer vc, float cx, float cy, float cz, float r, int argb) {
        float a = (argb >>> 24) / 255f, rr = ((argb >> 16) & 255) / 255f, gg = ((argb >> 8) & 255) / 255f, bb = (argb & 255) / 255f;
        float x0 = cx - r, x1 = cx + r, y0 = cy - r, y1 = cy + r, z0 = cz - r, z1 = cz + r;
        quad(m, vc, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, rr, gg, bb, a);
        quad(m, vc, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, rr, gg, bb, a);
        quad(m, vc, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, rr, gg, bb, a);
        quad(m, vc, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, rr, gg, bb, a);
        quad(m, vc, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, rr, gg, bb, a);
        quad(m, vc, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, rr, gg, bb, a);
    }

    private static void quad(Matrix4f m, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float r, float g, float b, float a) {
        vc.addVertex(m, ax, ay, az).setColor(r, g, b, a);
        vc.addVertex(m, bx, by, bz).setColor(r, g, b, a);
        vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
        vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
        // back side (additive glow is visible from both sides)
        vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
        vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
        vc.addVertex(m, bx, by, bz).setColor(r, g, b, a);
        vc.addVertex(m, ax, ay, az).setColor(r, g, b, a);
    }

    /** Light beam: a fading truncated pyramid from (ox,oy,oz) along (dx,dy,dz). */
    static void beam(Matrix4f m, VertexConsumer vc, float ox, float oy, float oz, float dx, float dy, float dz, float len, float r0, float r1, int rgb, float alpha) {
        // build an orthonormal frame around the beam axis
        float ux = 0, uy = 1, uz = 0;
        if (Math.abs(dy) > 0.9f) { ux = 1; uy = 0; }
        float sx = dy * uz - dz * uy, sy = dz * ux - dx * uz, sz = dx * uy - dy * ux;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz); sx /= sl; sy /= sl; sz /= sl;
        float tx = sy * dz - sz * dy, ty = sz * dx - sx * dz, tz = sx * dy - sy * dx;
        float ex = ox + dx * len, ey = oy + dy * len, ez = oz + dz * len;
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float[][] c0 = new float[4][], c1 = new float[4][];
        for (int i = 0; i < 4; i++) {
            float ca = i == 0 || i == 3 ? -1 : 1, cb = i < 2 ? -1 : 1;
            c0[i] = new float[]{ox + (sx * ca + tx * cb) * r0, oy + (sy * ca + ty * cb) * r0, oz + (sz * ca + tz * cb) * r0};
            c1[i] = new float[]{ex + (sx * ca + tx * cb) * r1, ey + (sy * ca + ty * cb) * r1, ez + (sz * ca + tz * cb) * r1};
        }
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            for (int side = 0; side < 2; side++) {
                float[][] order = side == 0 ? new float[][]{c0[i], c0[j], c1[j], c1[i]} : new float[][]{c1[i], c1[j], c0[j], c0[i]};
                for (int k = 0; k < 4; k++) {
                    boolean far = order[k] == c1[i] || order[k] == c1[j];
                    vc.addVertex(m, order[k][0], order[k][1], order[k][2]).setColor(r, g, b, far ? 0f : alpha);
                }
            }
        }
    }

    // ------------------------------------------------------------------ aircraft lights

    /** 0 auto_nav,1 red,2 green,3 white,4 beacon,5 strobe,6 landing,7 taxi,8 turnoff,9 cockpit */
    public static int lightType(VehicleEntity v, long k) {
        return ComponentType.AIRCRAFT_LIGHT.property("light_type").getInt(VehicleBuilder.config(v.structure, k));
    }

    public static void renderLights(VehicleEntity v, PoseStack ps, MultiBufferSource buffers, int light, boolean housings) {
        VehicleTelemetry t = v.telemetry;
        boolean power = t.busPowered;
        double time = System.currentTimeMillis() / 1000.0;
        VertexConsumer glow = null;
        var brd = Minecraft.getInstance().getBlockRenderer();
        for (long k : v.structure.blockEntities().keySet()) {
            BlockState st = v.structure.get(k);
            if (VehicleBuilder.typeOf(st) != ComponentType.AIRCRAFT_LIGHT) continue;
            int bx = BlockPos.getX(k), by = BlockPos.getY(k), bz = BlockPos.getZ(k);
            if (housings) {
                ps.pushPose();
                ps.translate(bx, by, bz);
                brd.renderSingleBlock(st, ps, buffers, light, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            if (!power) continue;
            int type = lightType(v, k);
            if (type == 0) type = bx < 0 ? 1 : (bx > 0 ? 2 : 3);
            int color = 0;
            float size = 0.09f, halo = 0.45f;
            boolean on;
            switch (type) {
                case 1 -> { on = t.sw(VehicleControls.SW_NAV); color = 0xFF2020; }
                case 2 -> { on = t.sw(VehicleControls.SW_NAV); color = 0x20FF40; }
                case 3 -> { on = t.sw(VehicleControls.SW_NAV); color = 0xFFFFFF; }
                case 4 -> { on = t.sw(VehicleControls.SW_BEACON) && (time % 1.0) < 0.18; color = 0xFF1A10; halo = 0.9f; }
                case 5 -> { double p = time % 1.3; on = t.sw(VehicleControls.SW_STROBE) && (p < 0.05 || (p > 0.14 && p < 0.19)); color = 0xFFFFFF; halo = 1.4f; }
                case 6 -> { on = t.sw(VehicleControls.SW_LANDING); color = 0xFFF6E0; size = 0.12f; halo = 0.7f; }
                case 7 -> { on = t.sw(VehicleControls.SW_TAXI); color = 0xFFF0D0; size = 0.1f; halo = 0.5f; }
                case 8 -> { on = t.sw(VehicleControls.SW_TURNOFF); color = 0xFFF0D0; size = 0.1f; halo = 0.5f; }
                default -> { on = t.cabinLight > 0; color = 0xFFD8A0; size = 0.07f; halo = 0.3f * t.cabinLight; }
            }
            if (!on) continue;
            glow = buffers.getBuffer(RenderType.lightning());
            Matrix4f m = ps.last().pose();
            float cx = bx + 0.5f, cy = by + 0.5f, cz = bz + 0.5f;
            glowCube(m, glow, cx, cy, cz, size, 0xFF000000 | color);
            glowCube(m, glow, cx, cy, cz, halo, 0x30000000 | color);
            glowCube(m, glow, cx, cy, cz, halo * 0.5f, 0x40000000 | color);
            if (type >= 6 && type <= 8) {
                Direction f = VehicleBuilder.facing(st);
                float dx = f.getStepX(), dz = f.getStepZ(), dy = type == 6 ? -0.12f : -0.28f;
                float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                beam(m, glow, cx + dx * 0.3f, cy, cz + dz * 0.3f, dx / l, dy / l, dz / l, type == 6 ? 26 : 14, 0.12f, type == 6 ? 3.2f : 2.4f, color, type == 6 ? 0.22f : 0.16f);
            }
        }
    }

    // ------------------------------------------------------------------ overhead switch panel

    static final String[] LABELS = {"BATT", "AVION", "F PUMP", "IGN", "START", "FUEL", "NAV", "BCN", "STRB", "LAND", "TAXI", "TURN", "CABIN", "PANEL", "GEAR", "TEST", "A/P", "PARK"};

    /** 0 toggle, 1 push button, 2 rotary, 3 gear lever */
    static int kind(int id) {
        return switch (id) {
            case VehicleControls.SW_STARTER, VehicleControls.SW_TEST, VehicleControls.SW_AUTOPILOT -> 1;
            case VehicleControls.SW_FUEL_SEL, VehicleControls.SW_CABIN, VehicleControls.SW_PANEL -> 2;
            case VehicleControls.SW_GEAR -> 3;
            default -> 0;
        };
    }

    /** Position (0..1) of a switch: toggles on/off, rotaries step, buttons pressed. */
    static float position(VehicleTelemetry t, int id) {
        return switch (id) {
            case VehicleControls.SW_FUEL_SEL -> t.fuelSelector / 3f;
            case VehicleControls.SW_CABIN -> t.cabinLight / 2f;
            case VehicleControls.SW_PANEL -> t.panelLight / 2f;
            case VehicleControls.SW_STARTER -> starting(t) ? 1 : 0;
            case VehicleControls.SW_TEST -> t.emergencyTest ? 1 : 0;
            default -> t.sw(id) ? 1 : 0;
        };
    }

    static boolean starting(VehicleTelemetry t) {
        for (byte s : t.engineState) if (s == 1) return true;
        return false;
    }

    static boolean running(VehicleTelemetry t) {
        for (byte s : t.engineState) if (s == 2) return true;
        return false;
    }

    /** Indicator lamp colour (ARGB) or 0 for dark. */
    public static int lamp(VehicleTelemetry t, int id) {
        boolean blink = (System.currentTimeMillis() / 300) % 2 == 0;
        if (t.emergencyTest) return id % 3 == 0 ? 0xFFFF2A2A : (id % 3 == 1 ? 0xFFFFB020 : 0xFF30FF50); // lamp test
        if (!t.busPowered) return 0;
        return switch (id) {
            case VehicleControls.SW_BATTERY -> t.batteryCharge < 0.2f ? (blink ? 0xFFFFB020 : 0) : 0xFF30FF50;
            case VehicleControls.SW_IGNITION -> t.sw(id) ? (running(t) ? 0xFF30FF50 : 0xFFFFB020) : 0;
            case VehicleControls.SW_STARTER -> starting(t) ? (blink ? 0xFFFFB020 : 0) : (running(t) ? 0xFF30FF50 : 0);
            case VehicleControls.SW_FUEL_SEL -> t.fuelSelector == 3 ? 0xFFFF2A2A : (t.wrongFuel ? (blink ? 0xFFFF2A2A : 0) : 0xFF30FF50);
            case VehicleControls.SW_CABIN, VehicleControls.SW_PANEL -> position(t, id) > 0 ? 0xFFFFE080 : 0;
            case VehicleControls.SW_GEAR -> t.sw(id) ? 0xFF30FF50 : 0xFFFF2A2A;
            case VehicleControls.SW_TEST -> 0;
            case VehicleControls.SW_AUTOPILOT -> t.autopilot ? 0xFF38D3FF : 0;
            case VehicleControls.SW_PARKING -> t.sw(id) ? 0xFFFFB020 : 0;
            default -> t.sw(id) ? 0xFF30FF50 : 0;
        };
    }

    /** model-space centre (blocks, relative to the block centre) of switch cell id */
    static float cellX(int id) { int c = id % COLS; return (32 - 8 * (c + 0.5f)) / 16f - 0.5f; }
    static float cellZ(int id) { int r = id / COLS; return (-8 + (32f / ROWS) * (r + 0.5f)) / 16f - 0.5f; }

    static float facingRot(Direction f) {
        return switch (f) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
    }

    public static void renderPanels(VehicleEntity v, PoseStack ps, MultiBufferSource buffers, int light) {
        VehicleTelemetry t = v.telemetry;
        TextureAtlasSprite sp = null;
        VertexConsumer solid = null, glow = null;
        Font font = Minecraft.getInstance().font;
        int backlight = t.busPowered ? t.panelLight : 0;
        int labelLight = backlight > 0 ? FULL : light;
        int labelColor = backlight == 2 ? 0xFFFFF0C8 : (backlight == 1 ? 0xFFC8B890 : 0xFF9A9A9A);
        for (long k : v.structure.blockEntities().keySet()) {
            BlockState st = v.structure.get(k);
            if (VehicleBuilder.typeOf(st) != ComponentType.COCKPIT_PANEL) continue;
            if (sp == null) sp = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.withDefaultNamespace("block/white_concrete"));
            ps.pushPose();
            ps.translate(BlockPos.getX(k) + 0.5, BlockPos.getY(k) + 14.5 / 16.0, BlockPos.getZ(k) + 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(-facingRot(VehicleBuilder.facing(st))));
            for (int id = 0; id < VehicleControls.SWITCH_COUNT; id++) {
                float x = cellX(id), z = cellZ(id);
                float pos = position(t, id);
                ps.pushPose();
                ps.translate(x, 0, z + 0.06f);
                solid = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
                switch (kind(id)) {
                    case 0 -> {
                        // toggle: base + lever tilted toward / away from the pilot
                        Box.cuboid(ps, solid, sp, -0.06f, -0.03f, -0.05f, 0.06f, 0f, 0.05f, light, 0xFF2A2E33);
                        ps.pushPose();
                        ps.mulPose(Axis.XP.rotationDegrees(pos > 0.5f ? 32 : -32));
                        Box.cuboid(ps, solid, sp, -0.018f, -0.17f, -0.018f, 0.018f, -0.02f, 0.018f, light, 0xFFD8DCE0);
                        Box.cuboid(ps, solid, sp, -0.03f, -0.2f, -0.03f, 0.03f, -0.16f, 0.03f, light, 0xFFEFF2F4);
                        ps.popPose();
                    }
                    case 1 -> {
                        int col = id == VehicleControls.SW_TEST ? 0xFFC02020 : (id == VehicleControls.SW_STARTER ? 0xFF2A2E33 : 0xFF2050A0);
                        float depth = pos > 0.5f ? 0.03f : 0.07f;
                        Box.cuboid(ps, solid, sp, -0.07f, -0.02f, -0.07f, 0.07f, 0f, 0.07f, light, 0xFF1A1C1F);
                        Box.cuboid(ps, solid, sp, -0.05f, -depth, -0.05f, 0.05f, -0.02f, 0.05f, light, col);
                    }
                    case 2 -> {
                        int steps = id == VehicleControls.SW_FUEL_SEL ? 4 : 3;
                        float ang = -90 + 180f * Math.round(pos * (steps - 1)) / (steps - 1);
                        Box.cuboid(ps, solid, sp, -0.07f, -0.02f, -0.07f, 0.07f, 0f, 0.07f, light, 0xFF1A1C1F);
                        ps.pushPose();
                        ps.mulPose(Axis.YP.rotationDegrees(ang));
                        Box.cuboid(ps, solid, sp, -0.055f, -0.08f, -0.055f, 0.055f, -0.02f, 0.055f, light, 0xFF34383D);
                        Box.cuboid(ps, solid, sp, -0.012f, -0.085f, -0.07f, 0.012f, -0.075f, 0f, light, 0xFFFFFFFF);
                        ps.popPose();
                    }
                    default -> {
                        // gear lever with a wheel-shaped knob
                        Box.cuboid(ps, solid, sp, -0.05f, -0.03f, -0.09f, 0.05f, 0f, 0.09f, light, 0xFF1A1C1F);
                        ps.pushPose();
                        ps.mulPose(Axis.XP.rotationDegrees(pos > 0.5f ? -35 : 35));
                        Box.cuboid(ps, solid, sp, -0.015f, -0.2f, -0.015f, 0.015f, -0.02f, 0.015f, light, 0xFFB0B4B8);
                        Box.cuboid(ps, solid, sp, -0.05f, -0.25f, -0.05f, 0.05f, -0.19f, 0.05f, light, 0xFFEFEFEF);
                        ps.popPose();
                    }
                }
                // indicator lamp beside the switch
                int lc = lamp(t, id);
                if (lc != 0) {
                    glow = buffers.getBuffer(RenderType.lightning());
                    glowCube(ps.last().pose(), glow, 0.13f, -0.015f, -0.1f, 0.022f, lc);
                    glowCube(ps.last().pose(), glow, 0.13f, -0.015f, -0.1f, 0.05f, 0x50FFFFFF & lc | 0x30000000);
                } else Box.cuboid(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)), sp, 0.11f, -0.03f, -0.12f, 0.15f, 0f, -0.08f, light, 0xFF30302C);
                // label, written on the underside of the plate, readable from the seat
                ps.pushPose();
                ps.translate(0, -0.002f, -0.165f);
                ps.mulPose(Axis.XP.rotationDegrees(-90)); // text plane horizontal; the mirrored scale below makes it face down
                float s = 1 / 230f;
                ps.scale(-s, -s, s); // readable (not mirrored) for the seated pilot looking up
                String lbl = LABELS[id];
                if (id == VehicleControls.SW_FUEL_SEL) lbl = new String[]{"FUEL BOTH", "FUEL L", "FUEL R", "FUEL OFF"}[Math.max(0, Math.min(3, t.fuelSelector))];
                font.drawInBatch(lbl, -font.width(lbl) / 2f, 0, labelColor, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, labelLight);
                ps.popPose();
                ps.popPose();
            }
            ps.popPose();
        }
    }
}
