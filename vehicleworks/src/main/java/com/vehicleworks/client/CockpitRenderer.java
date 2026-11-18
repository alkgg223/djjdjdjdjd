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
        boolean begun = false;
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
            if (!begun) { LightFx.begin(ps); begun = true; }
            float night = LightFx.night();
            float cx = bx + 0.5f, cy = by + 0.5f, cz = bz + 0.5f;
            float dist = LightFx.distance(cx, cy, cz);
            // a real lamp stays a visible point far away: the core never shrinks below ~1.5 px
            float minCore = dist * 0.0035f;
            switch (type) {
                case 1, 2, 3 -> {
                    if (!t.sw(VehicleControls.SW_NAV)) continue;
                    int c = type == 1 ? 0xFF2418 : (type == 2 ? 0x18FF50 : 0xFFF4E8);
                    LightFx.sprite(ps, buffers, LightFx.GLOW, cx, cy, cz, Math.max(0.14f, minCore), c, 1.2f);                 // lens
                    LightFx.sprite(ps, buffers, LightFx.GLOW, cx, cy, cz, 0.55f + 0.6f * night, c, 0.25f + 0.55f * night);    // halo
                    if (night > 0.3f) LightFx.sprite(ps, buffers, LightFx.FLARE, cx, cy, cz, 0.9f, c, 0.25f * night);
                }
                case 4 -> {
                    if (!t.sw(VehicleControls.SW_BEACON)) continue;
                    // rotating beacon: a smooth red pulse about once per second
                    double ph = time % 1.0;
                    float e = (float) Math.exp(-Math.pow((ph - 0.1) / 0.06, 2));
                    int c = 0xFF1C10;
                    LightFx.sprite(ps, buffers, LightFx.GLOW, cx, cy, cz, Math.max(0.16f, minCore), c, 0.25f + 1.1f * e);
                    LightFx.sprite(ps, buffers, LightFx.GLOW, cx, cy, cz, 1.0f + 1.2f * night, c, e * (0.35f + 0.7f * night));
                    LightFx.sprite(ps, buffers, LightFx.FLARE, cx, cy, cz, 1.4f + 1.4f * night, c, e * (0.2f + 0.5f * night));
                }
                case 5 -> {
                    if (!t.sw(VehicleControls.SW_STROBE)) continue;
                    // double white flash every 1.3 s, very short and very bright
                    double p = time % 1.3;
                    float e = (float) (Math.exp(-Math.pow((p - 0.025) / 0.022, 2)) + Math.exp(-Math.pow((p - 0.165) / 0.022, 2)));
                    if (e < 0.01f) continue;
                    int c = 0xF4F8FF;
                    LightFx.sprite(ps, buffers, LightFx.GLOW, cx, cy, cz, Math.max(0.2f, minCore * 1.5f), c, 1.4f * e);
                    LightFx.sprite(ps, buffers, LightFx.GLOW, cx, cy, cz, 1.6f + 2.4f * night, c, e * (0.4f + 0.8f * night));
                    LightFx.sprite(ps, buffers, LightFx.FLARE, cx, cy, cz, 2.6f + 2.4f * night, c, e * (0.35f + 0.6f * night));
                }
                case 6, 7, 8 -> {
                    boolean on = type == 6 ? t.sw(VehicleControls.SW_LANDING) : (type == 7 ? t.sw(VehicleControls.SW_TAXI) : t.sw(VehicleControls.SW_TURNOFF));
                    if (!on) continue;
                    int c = type == 6 ? 0xFFF2DC : 0xFFEAD0;
                    Direction f = VehicleBuilder.facing(st);
                    float dx = f.getStepX(), dz = f.getStepZ(), dy = type == 6 ? -0.1f : -0.22f;
                    float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    float lx = cx + dx * 0.32f, lz = cz + dz * 0.32f;
                    LightFx.sprite(ps, buffers, LightFx.GLOW, lx, cy, lz, Math.max(type == 6 ? 0.3f : 0.22f, minCore * 1.4f), c, 1.3f);
                    LightFx.sprite(ps, buffers, LightFx.GLOW, lx, cy, lz, 0.9f + 0.9f * night, c, 0.25f + 0.5f * night);
                    if (night > 0.3f) LightFx.sprite(ps, buffers, LightFx.FLARE, lx, cy, lz, 1.6f, c, 0.25f * night);
                    float len = type == 6 ? 30 : 16, r1 = type == 6 ? 3.4f : 2.6f;
                    // soft volumetric shaft (visible in the night air, faint by day)
                    LightFx.beam(ps, buffers, lx, cy, lz, dx / l, dy / l, dz / l, len, 0.35f, r1, c, 0.03f + 0.4f * night);
                }
                default -> {
                    if (t.cabinLight <= 0) continue;
                    LightFx.sprite(ps, buffers, LightFx.GLOW, cx, cy, cz, 0.08f, 0xFFD8A0, 0.6f + 0.3f * t.cabinLight);
                }
            }
        }
    }

    // ------------------------------------------------------------------ overhead switch panel

    /** control kinds */
    static final int TOGGLE = 0, GUARDED = 1, ROTARY = 2, PUSH = 3, LEVER = 4, MASTER = 5, GUARDED_PUSH = 6, PARK = 7;
    public static final int COLS = 10, ROWS = 4;
    static final float CW = 0.3f, CD = 0.5f;

    /** one control on the overhead panel: switch id, grid cell, kind, rotary positions, label */
    record Ctl(int id, int col, int row, int kind, int steps, String label) {}

    static final Ctl[] CONTROLS = {
            new Ctl(VehicleControls.SW_MASTER_CAUTION, 0, 0, MASTER, 0, "MASTER"),
            new Ctl(VehicleControls.SW_BATTERY, 0, 1, TOGGLE, 0, "BATT"),
            new Ctl(VehicleControls.SW_MASTER, 1, 1, TOGGLE, 0, "MASTER"),
            new Ctl(VehicleControls.SW_GENERATOR, 2, 1, TOGGLE, 0, "GEN"),
            new Ctl(VehicleControls.SW_AVIONICS, 3, 1, TOGGLE, 0, "AVIONICS"),
            new Ctl(VehicleControls.SW_FUEL_PUMP, 4, 1, TOGGLE, 0, "FUEL PUMP"),
            new Ctl(VehicleControls.SW_FUEL_SEL, 5, 1, ROTARY, 4, "FUEL SEL"),
            new Ctl(VehicleControls.SW_ENG_MASTER, 6, 1, GUARDED, 0, "ENG MASTER"),
            new Ctl(VehicleControls.SW_IGNITION, 7, 1, TOGGLE, 0, "IGN"),
            new Ctl(VehicleControls.SW_STARTER, 8, 1, GUARDED_PUSH, 0, "START"),
            new Ctl(VehicleControls.SW_TEST, 9, 1, PUSH, 0, "TEST"),
            new Ctl(VehicleControls.SW_NAV, 0, 2, TOGGLE, 0, "NAV"),
            new Ctl(VehicleControls.SW_BEACON, 1, 2, TOGGLE, 0, "BCN"),
            new Ctl(VehicleControls.SW_STROBE, 2, 2, TOGGLE, 0, "STROBE"),
            new Ctl(VehicleControls.SW_LANDING, 3, 2, TOGGLE, 0, "LAND"),
            new Ctl(VehicleControls.SW_TAXI, 4, 2, TOGGLE, 0, "TAXI"),
            new Ctl(VehicleControls.SW_TURNOFF, 5, 2, TOGGLE, 0, "RWY TURN"),
            new Ctl(VehicleControls.SW_AUTOPILOT, 6, 2, PUSH, 0, "A/P"),
            new Ctl(VehicleControls.SW_PARKING, 7, 2, PARK, 0, "PARK BRK"),
            new Ctl(VehicleControls.SW_GEAR, 8, 2, LEVER, 0, "GEAR"),
            new Ctl(VehicleControls.SW_CABIN, 0, 3, ROTARY, 3, "FLOOD"),
            new Ctl(VehicleControls.SW_PANEL, 1, 3, ROTARY, 3, "INSTR LT"),
            new Ctl(VehicleControls.SW_DIMMER, 2, 3, ROTARY, 5, "DIM"),
            new Ctl(VehicleControls.SW_DISPLAY_BRT, 3, 3, ROTARY, 5, "DISP BRT"),
            new Ctl(VehicleControls.SW_CONSOLE, 4, 3, TOGGLE, 0, "CONSOLE"),
            new Ctl(VehicleControls.SW_NIGHT, 5, 3, TOGGLE, 0, "NIGHT"),
    };

    static Ctl ctl(int id) {
        for (Ctl c : CONTROLS) if (c.id == id) return c;
        return null;
    }

    /** Control at a grid cell (the gear lever spans two cells), or null. */
    static Ctl at(int col, int row) {
        for (Ctl c : CONTROLS) if (c.row == row && (c.col == col || (c.kind == LEVER && c.col + 1 == col))) return c;
        return null;
    }

    static String[] sections() {
        return new String[]{"ELECTRICAL", "FUEL", "ENGINE", "EXTERIOR LIGHTS", "FLIGHT", "COCKPIT LIGHTS", "STATUS"};
    }

    /** kind of control for a switch id (sounds and the panel screen) */
    static int kind(int id) {
        Ctl c = ctl(id);
        return c == null ? TOGGLE : c.kind;
    }

    /** Target position (0..1) of a control from the real system state. */
    static float position(VehicleTelemetry t, int id) {
        return switch (id) {
            case VehicleControls.SW_FUEL_SEL -> t.fuelSelector / 3f;
            case VehicleControls.SW_CABIN -> t.cabinLight / 2f;
            case VehicleControls.SW_PANEL -> t.panelLight / 2f;
            case VehicleControls.SW_DIMMER -> t.dimmer / 4f;
            case VehicleControls.SW_DISPLAY_BRT -> (t.displayBrt - 1) / 4f;
            case VehicleControls.SW_STARTER -> t.sw(VehicleControls.BIT_STARTER) ? 1 : 0;
            case VehicleControls.SW_TEST -> t.emergencyTest ? 1 : 0;
            case VehicleControls.SW_MASTER_CAUTION -> 0;
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

    static final int RED = 0xFFFF2A20, AMBER = 0xFFFFA418, GREEN = 0xFF2CFF5A, BLUE = 0xFF38D3FF, WHITE_L = 0xFFF4F0E0;

    /** Indicator colour of a control (ARGB) or 0 for dark; used by the 3D panel and the panel screen. */
    public static int lamp(VehicleTelemetry t, int id) {
        boolean blink = (System.currentTimeMillis() / 300) % 2 == 0;
        if (t.emergencyTest) return id % 3 == 0 ? RED : (id % 3 == 1 ? AMBER : GREEN); // lamp test
        if (!t.busPowered) return 0;
        return switch (id) {
            case VehicleControls.SW_BATTERY -> t.sw(id) ? (t.batteryCharge < 0.2f ? (blink ? AMBER : 0) : GREEN) : 0;
            case VehicleControls.SW_GENERATOR -> t.sw(id) ? (t.generator ? GREEN : AMBER) : 0;
            case VehicleControls.SW_IGNITION -> t.sw(id) ? (running(t) ? GREEN : AMBER) : 0;
            case VehicleControls.SW_STARTER -> t.sw(VehicleControls.BIT_STARTER) ? (blink ? AMBER : 0) : (running(t) ? GREEN : 0);
            case VehicleControls.SW_FUEL_SEL -> t.fuelSelector == 3 ? RED : (t.wrongFuel ? (blink ? RED : 0) : GREEN);
            case VehicleControls.SW_ENG_MASTER -> t.sw(id) ? (running(t) ? GREEN : AMBER) : 0;
            case VehicleControls.SW_CABIN, VehicleControls.SW_PANEL, VehicleControls.SW_DIMMER, VehicleControls.SW_DISPLAY_BRT -> 0;
            case VehicleControls.SW_GEAR -> t.sw(id) ? GREEN : RED;
            case VehicleControls.SW_TEST -> 0;
            case VehicleControls.SW_AUTOPILOT -> t.autopilot ? GREEN : 0;
            case VehicleControls.SW_PARKING -> t.sw(id) ? AMBER : 0;
            case VehicleControls.SW_MASTER_CAUTION -> VoiceCallouts.warning ? (redActive() ? RED : AMBER) : 0;
            case VehicleControls.SW_NIGHT -> t.sw(id) ? BLUE : 0;
            default -> t.sw(id) ? GREEN : 0;
        };
    }

    static boolean redActive() {
        for (Warnings.W w : VoiceCallouts.active) if (w.red) return true;
        return false;
    }

    /** annunciator strip (row 0, columns 1..9): legend + colour when lit */
    record Ann(String legend, int color) {}

    static final String[] ANN = {"BATT DISCH", "GEN OFF", "FUEL PRESS", "LOW FUEL", "START VALVE", "ENG FIRE", "HYD PRESS", "GEAR UNSAFE", "AP DISC"};
    static final int[] ANN_COLOR = {AMBER, AMBER, AMBER, AMBER, AMBER, RED, AMBER, RED, RED};
    static final String[] STATUS = {"GEN", "AVIONICS", "ENG RUN", "GEAR"};

    static boolean annunciator(VehicleEntity v, VehicleTelemetry t, int i) {
        if (t.emergencyTest) return true;
        if (!t.busPowered) return false;
        boolean anyEngine = starting(t) || running(t);
        return switch (i) {
            case 0 -> t.sw(VehicleControls.SW_BATTERY) && !t.generator;
            case 1 -> !t.generator && (running(t) || t.sw(VehicleControls.SW_GENERATOR));
            case 2 -> anyEngine && !t.sw(VehicleControls.SW_FUEL_PUMP);
            case 3 -> VoiceCallouts.active.contains(Warnings.W.LOW_FUEL) || (t.fuelCapacity > 0 && t.fuel < 0.1f * t.fuelCapacity);
            case 4 -> t.sw(VehicleControls.BIT_STARTER);
            case 5 -> VoiceCallouts.active.contains(Warnings.W.ENGINE_FIRE);
            case 6 -> t.hydraulics < 0.75f;
            case 7 -> gearInTransit(t) || VoiceCallouts.active.contains(Warnings.W.GEAR);
            case 8 -> apDisconnectFlash(v);
            default -> false;
        };
    }

    static boolean status(VehicleEntity v, VehicleTelemetry t, int i) {
        if (t.emergencyTest) return true;
        if (!t.busPowered) return false;
        return switch (i) {
            case 0 -> t.generator;
            case 1 -> Avionics.stage(v) == Avionics.READY;
            case 2 -> running(t);
            case 3 -> t.gearDown && !gearInTransit(t);
            default -> false;
        };
    }

    static boolean gearInTransit(VehicleTelemetry t) {
        for (float e : t.wheelExt) if (e > 0.03f && e < 0.97f) return true;
        return false;
    }

    private static final java.util.WeakHashMap<VehicleEntity, long[]> AP_STATE = new java.util.WeakHashMap<>();

    /** red AP DISC flashes for 4 s after the autopilot disengages (and the cavalry-charge style tone plays) */
    static boolean apDisconnectFlash(VehicleEntity v) {
        long[] st = AP_STATE.computeIfAbsent(v, k -> new long[]{v.telemetry.autopilot ? 1 : 0, 0});
        long now = System.currentTimeMillis();
        boolean ap = v.telemetry.autopilot;
        if (st[0] == 1 && !ap) st[1] = now;
        st[0] = ap ? 1 : 0;
        return now - st[1] < 4000 && (now / 250) % 2 == 0;
    }

    /** model-space centre (blocks, relative to the block centre) of a grid cell */
    static float cellX(float col) { return 1.5f - (col + 0.5f) * CW; }
    static float cellZ(int row) { return -1f + (row + 0.5f) * CD; }

    static float facingRot(Direction f) {
        return switch (f) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
    }

    /** client-side animation of every control (smoothed toward the system state) */
    static final class Anim {
        final float[] pos = new float[VehicleControls.SWITCH_COUNT], guard = new float[VehicleControls.SWITCH_COUNT];
        final long[] press = new long[VehicleControls.SWITCH_COUNT];
        long last = System.currentTimeMillis();
        boolean init;
    }

    private static final java.util.WeakHashMap<VehicleEntity, Anim> ANIM = new java.util.WeakHashMap<>();

    static Anim anim(VehicleEntity v) {
        return ANIM.computeIfAbsent(v, k -> new Anim());
    }

    /** a control was pressed on this client: push buttons go in, the click is heard at once */
    public static void pressed(VehicleEntity v, int id) {
        if (id >= 0 && id < VehicleControls.SWITCH_COUNT) anim(v).press[id] = System.currentTimeMillis();
        var snd = switch (kind(id)) {
            case ROTARY -> com.vehicleworks.registry.ModSounds.KNOB.get();
            case PUSH, MASTER -> com.vehicleworks.registry.ModSounds.BUTTON.get();
            case GUARDED, GUARDED_PUSH -> com.vehicleworks.registry.ModSounds.GUARD.get();
            case LEVER, PARK -> com.vehicleworks.registry.ModSounds.LEVER.get();
            default -> id == VehicleControls.SW_BATTERY || id == VehicleControls.SW_MASTER ? com.vehicleworks.registry.ModSounds.RELAY.get() : com.vehicleworks.registry.ModSounds.TOGGLE.get();
        };
        float vol = com.vehicleworks.Config.CLIENT.cockpitVolume.get().floatValue();
        if (vol > 0 && Minecraft.getInstance().player != null) Minecraft.getInstance().player.playSound(snd, 0.8f * vol, 1f);
        if (id == VehicleControls.SW_MASTER_CAUTION) VoiceCallouts.acknowledge();
    }

    private static void step(Anim a, VehicleTelemetry t) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - a.last) / 1000f);
        a.last = now;
        for (Ctl c : CONTROLS) {
            float target = position(t, c.id);
            if (c.kind == ROTARY) target = Math.round(target * (c.steps - 1)) / (float) (c.steps - 1);
            float rate = switch (c.kind) { case LEVER -> 4f; case ROTARY -> 14f; case PARK -> 6f; default -> 22f; };
            if (!a.init) a.pos[c.id] = target;
            else a.pos[c.id] += (target - a.pos[c.id]) * Math.min(1, rate * dt);
            float g = (c.kind == GUARDED || c.kind == GUARDED_PUSH) && (target > 0.5f || now - a.press[c.id] < 900) ? 1 : 0;
            a.guard[c.id] += (g - a.guard[c.id]) * Math.min(1, 9 * dt);
        }
        a.init = true;
    }

    public static void renderPanels(VehicleEntity v, PoseStack ps, MultiBufferSource buffers, int light) {
        VehicleTelemetry t = v.telemetry;
        Font font = Minecraft.getInstance().font;
        boolean found = false;
        for (long k : v.structure.blockEntities().keySet()) if (VehicleBuilder.typeOf(v.structure.get(k)) == ComponentType.COCKPIT_PANEL) { found = true; break; }
        if (!found) return;
        Anim an = anim(v);
        step(an, t);
        boolean consoleOn = t.busPowered && t.sw(VehicleControls.SW_CONSOLE);
        // console / flood lighting lights the hardware (raised block light), dimmable, red-tinted at night
        if (consoleOn) light = CockpitParts.boost(light, 9 + 4 * Avionics.dimmer(t));
        // backlight of the labels / legends: bus power-up ramp x instrument light switch x dimmer
        float bl = Avionics.backlight(v) * Math.min(1, t.panelLight / 1.5f) * Avionics.dimmer(t);
        boolean night = t.sw(VehicleControls.SW_NIGHT);
        float annBright = (night ? 0.55f : 1f) * Math.max(0.35f, Math.min(1.2f, 0.5f + Avionics.dimmer(t))) ;
        TextureAtlasSprite metal = CockpitParts.sprite(CockpitParts.METAL), black = CockpitParts.sprite(CockpitParts.BLACK), knob = CockpitParts.sprite(CockpitParts.KNOB),
                glass = CockpitParts.sprite(CockpitParts.GLASS), guard = CockpitParts.sprite(CockpitParts.GUARD), bezel = CockpitParts.sprite(CockpitParts.BEZEL), white = CockpitParts.sprite(CockpitParts.WHITE);
        for (long k : v.structure.blockEntities().keySet()) {
            BlockState st = v.structure.get(k);
            if (VehicleBuilder.typeOf(st) != ComponentType.COCKPIT_PANEL) continue;
            ps.pushPose();
            ps.translate(BlockPos.getX(k) + 0.5, BlockPos.getY(k) + 14.5 / 16.0, BlockPos.getZ(k) + 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(-facingRot(VehicleBuilder.facing(st))));
            // painted section dividers (white lines on the plate)
            VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
            int paint = bl > 0.05f ? CockpitParts.scale(0xFFE8E2C8, 0.35f + 0.65f * bl) : 0xFFB8BCC0;
            int paintLight = bl > 0.05f ? FULL : light;
            for (int r = 1; r < ROWS; r++) CockpitParts.plate(ps, solid, white, -1.48f, -1f + r * CD - 0.004f, 1.48f, -1f + r * CD + 0.004f, -0.002f, paintLight, paint);
            CockpitParts.plate(ps, solid, white, cellX(4.5f) - 0.004f, -1f + CD, cellX(4.5f) + 0.004f, -1f + 2 * CD, -0.002f, paintLight, paint);
            CockpitParts.plate(ps, solid, white, cellX(5.5f) - 0.004f, -1f + CD, cellX(5.5f) + 0.004f, -1f + 2 * CD, -0.002f, paintLight, paint);
            CockpitParts.plate(ps, solid, white, cellX(5.5f) - 0.004f, -1f + 2 * CD, cellX(5.5f) + 0.004f, 1f, -0.002f, paintLight, paint);
            // section titles
            sectionLabel(ps, buffers, font, "ELECTRICAL / FUEL", cellX(2f), cellZ(1) - 0.235f, paint, paintLight);
            sectionLabel(ps, buffers, font, "ENGINE", cellX(7.5f), cellZ(1) - 0.235f, paint, paintLight);
            sectionLabel(ps, buffers, font, "EXTERIOR LIGHTS", cellX(2.5f), cellZ(2) - 0.235f, paint, paintLight);
            sectionLabel(ps, buffers, font, "FLIGHT", cellX(7.5f), cellZ(2) - 0.235f, paint, paintLight);
            sectionLabel(ps, buffers, font, "COCKPIT LIGHTS", cellX(2.5f), cellZ(3) - 0.235f, paint, paintLight);
            sectionLabel(ps, buffers, font, "SYSTEM STATUS", cellX(7.5f), cellZ(3) - 0.235f, paint, paintLight);
            // annunciator strip
            for (int i = 0; i < ANN.length; i++) {
                float x = cellX(i + 1), z = cellZ(0) + 0.04f;
                annunciator(ps, buffers, font, glass, bezel, x, z, ANN[i], ANN_COLOR[i], annunciator(v, t, i), annBright, light);
            }
            // system status lamps (green)
            for (int i = 0; i < STATUS.length; i++) {
                float x = cellX(6 + i), z = cellZ(3) + 0.06f;
                annunciator(ps, buffers, font, glass, bezel, x, z, STATUS[i], GREEN, status(v, t, i), annBright, light);
            }
            for (Ctl c : CONTROLS) {
                float x = c.kind == LEVER ? cellX(c.col + 0.5f) : cellX(c.col), z = cellZ(c.row) + 0.04f;
                float pos = an.pos[c.id];
                ps.pushPose();
                ps.translate(x, 0, z);
                solid = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
                int lc = lamp(t, c.id);
                switch (c.kind) {
                    case TOGGLE, GUARDED -> toggle(ps, solid, metal, black, pos, light);
                    case ROTARY -> rotary(ps, solid, knob, black, metal, pos, c.steps, light, bl);
                    case PUSH, GUARDED_PUSH -> {
                        boolean in = System.currentTimeMillis() - an.press[c.id] < 180 || (c.id == VehicleControls.SW_TEST && t.emergencyTest);
                        korry(ps, buffers, font, glass, bezel, in, c, lc, annBright, light);
                    }
                    case MASTER -> {
                        boolean in = System.currentTimeMillis() - an.press[c.id] < 180;
                        master(ps, buffers, font, glass, bezel, in, t, annBright, light);
                    }
                    case LEVER -> gearLever(ps, solid, metal, black, white, pos, t, light);
                    case PARK -> parkHandle(ps, solid, metal, black, pos, light);
                    default -> {}
                }
                if (c.kind == GUARDED || c.kind == GUARDED_PUSH) guardCover(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)), guard, an.guard[c.id], c.kind == GUARDED_PUSH, light);
                // status lamp: small round glass lens beside the control
                if (c.kind != PUSH && c.kind != GUARDED_PUSH && c.kind != MASTER && c.kind != ROTARY || c.id == VehicleControls.SW_FUEL_SEL) {
                    float lx = c.kind == LEVER ? 0.2f : 0.105f, lz = -0.11f;
                    lensLamp(ps, buffers, glass, bezel, lx, lz, lc, annBright, light);
                }
                // backlit / painted label on the plate, readable from the seat looking up
                String lbl = c.label;
                if (c.id == VehicleControls.SW_FUEL_SEL) lbl = new String[]{"FUEL BOTH", "FUEL L", "FUEL R", "FUEL OFF"}[Math.max(0, Math.min(3, t.fuelSelector))];
                if (c.kind == ROTARY && c.id != VehicleControls.SW_FUEL_SEL) {
                    int stp = Math.round(position(t, c.id) * (c.steps - 1));
                    lbl = c.label + (c.steps == 3 ? new String[]{" OFF", " DIM", " BRT"}[stp] : " " + (c.id == VehicleControls.SW_DISPLAY_BRT ? stp + 1 : stp));
                }
                label(ps, buffers, font, lbl, 0, -0.185f, paint, paintLight, 1 / 260f);
                ps.popPose();
            }
            // console lighting: thin light strips along the panel edges (they light the panel itself, see the light boost above)
            if (consoleOn) {
                int col = CockpitParts.scale(night ? 0xFFFF4A30 : 0xFFFFE0B0, Math.max(0.35f, Avionics.dimmer(t)));
                VertexConsumer vc2 = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
                Box.cuboid(ps, vc2, white, -1.48f, -0.012f, -0.995f, 1.48f, -0.004f, -0.985f, FULL, col);
                Box.cuboid(ps, vc2, white, -1.48f, -0.012f, 0.985f, 1.48f, -0.004f, 0.995f, FULL, col);
            }
            ps.popPose();
        }
    }

    static void sectionLabel(PoseStack ps, MultiBufferSource buf, Font font, String s, float x, float z, int color, int light) {
        ps.pushPose();
        ps.translate(x, 0, 0);
        label(ps, buf, font, s, 0, z, color, light, 1 / 300f);
        ps.popPose();
    }

    /** text printed on the underside of the plate, readable (not mirrored) for the seated pilot looking up */
    static void label(PoseStack ps, MultiBufferSource buf, Font font, String s, float x, float z, int color, int light, float scale) {
        ps.pushPose();
        ps.translate(x, -0.003f, z);
        ps.mulPose(Axis.XP.rotationDegrees(-90));
        ps.scale(-scale, -scale, scale);
        font.drawInBatch(s, -font.width(s) / 2f, -4, color, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, light);
        ps.popPose();
    }

    /** aircraft toggle switch: hex nut, threaded bushing and a tapered chrome bat lever with a ball tip */
    static void toggle(PoseStack ps, VertexConsumer vc, TextureAtlasSprite metal, TextureAtlasSprite black, float pos, int light) {
        CockpitParts.ring(ps, vc, metal, 0, 0, 0.022f, 0.05f, -0.002f, 12, light, 0xFF8E949A);           // washer
        CockpitParts.prism(ps, vc, metal, 0, 0, 0.036f, 0.036f, -0.002f, -0.018f, 6, light, 0xFFA8AEB4, 0xFF8E949A, true); // hex nut
        CockpitParts.prism(ps, vc, metal, 0, 0, 0.02f, 0.02f, -0.018f, -0.03f, 10, light, 0xFFC0C6CC, 0xFFB0B6BC, true);   // bushing
        ps.pushPose();
        ps.translate(0, -0.028f, 0);
        ps.mulPose(Axis.XP.rotationDegrees(-30 + 60 * pos)); // tilts toward the pilot when ON
        CockpitParts.prism(ps, vc, metal, 0, 0, 0.011f, 0.007f, 0, -0.105f, 8, light, 0xFFE6EAEE, 0xFFD0D4D8, false);
        CockpitParts.ball(ps, vc, metal, 0, -0.11f, 0, 0.016f, light, 0xFFF2F4F6);
        ps.popPose();
    }

    /** rotary selector: knurled black knob with a white pointer line, on a scale of detents */
    static void rotary(PoseStack ps, VertexConsumer vc, TextureAtlasSprite knob, TextureAtlasSprite black, TextureAtlasSprite metal, float pos, int steps, int light, float bl) {
        float a0 = -120, a1 = 120;
        CockpitParts.ring(ps, vc, black, 0, 0, 0.05f, 0.075f, -0.002f, 16, light, 0xFF26292D);
        // detent ticks on the plate
        for (int i = 0; i < steps; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / (steps - 1));
            float tx = (float) Math.sin(a) * 0.085f, tz = (float) -Math.cos(a) * 0.085f;
            int tc = bl > 0.05f ? CockpitParts.scale(0xFFE8E2C8, 0.4f + 0.6f * bl) : 0xFFB0B4B8;
            Box.cuboid(ps, vc, CockpitParts.sprite(CockpitParts.WHITE), tx - 0.006f, -0.003f, tz - 0.006f, tx + 0.006f, -0.001f, tz + 0.006f, bl > 0.05f ? FULL : light, tc);
        }
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-(a0 + (a1 - a0) * pos)));
        CockpitParts.prism(ps, vc, metal, 0, 0, 0.05f, 0.05f, -0.002f, -0.014f, 16, light, 0xFF3A3E43, 0xFF3A3E43, false);   // skirt
        CockpitParts.prism(ps, vc, knob, 0, 0, 0.04f, 0.036f, -0.014f, -0.062f, 16, light, 0xFF1E2023, 0xFF2C2F33, true);     // knurled grip
        Box.cuboid(ps, vc, CockpitParts.sprite(CockpitParts.WHITE), -0.005f, -0.0635f, -0.034f, 0.005f, -0.0615f, -0.004f, light, 0xFFF0F0F0); // pointer
        Box.cuboid(ps, vc, knob, -0.008f, -0.064f, -0.038f, 0.008f, -0.014f, -0.026f, light, 0xFF26292D);                    // pointer fin
        ps.popPose();
    }

    /** red flip-up guard over a critical switch; open while the switch is ON (or being pressed) */
    static void guardCover(PoseStack ps, VertexConsumer vc, TextureAtlasSprite guard, float open, boolean push, int light) {
        ps.pushPose();
        ps.translate(0, -0.004f, 0.07f); // hinge at the back
        ps.mulPose(Axis.XP.rotationDegrees(-100 * open));
        float h = push ? 0.05f : 0.12f;
        int c = 0xFFC8241A, c2 = 0xFFA01C14;
        Box.cuboid(ps, vc, guard, -0.058f, -h, -0.12f, -0.052f, 0, 0, light, c2);     // side wire
        Box.cuboid(ps, vc, guard, 0.052f, -h, -0.12f, 0.058f, 0, 0, light, c2);       // side wire
        Box.cuboid(ps, vc, guard, -0.058f, -h - 0.006f, -0.12f, 0.058f, -h, -0.1f, light, c);  // front bar
        Box.cuboid(ps, vc, guard, -0.058f, -h - 0.006f, -0.02f, 0.058f, -h, 0f, light, c);     // rear bar
        ps.popPose();
    }

    /** illuminated push button (korry style): square bezel, glass cap with a two-line legend */
    static void korry(PoseStack ps, MultiBufferSource buf, Font font, TextureAtlasSprite glass, TextureAtlasSprite bezel, boolean in, Ctl c, int lc, float bright, int light) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        float d = in ? 0.012f : 0.028f;
        Box.cuboid(ps, vc, bezel, -0.075f, -0.016f, -0.075f, 0.075f, 0, 0.075f, light, 0xFF2A2D31);
        Box.cuboid(ps, vc, bezel, -0.06f, -0.016f - d, -0.06f, 0.06f, -0.016f, 0.06f, light, 0xFF15171A);
        boolean lit = lc != 0;
        int face = lit ? CockpitParts.mix(CockpitParts.scale(lc, bright), 0xFF000000, 0.85f) : 0xFF141518;
        CockpitParts.plate(ps, vc, glass, -0.055f, -0.055f, 0.055f, 0.055f, -0.0165f - d, lit ? FULL : light, face);
        String top = switch (c.id) { case VehicleControls.SW_AUTOPILOT -> "AP"; case VehicleControls.SW_STARTER -> "START"; case VehicleControls.SW_TEST -> "TEST"; default -> c.label; };
        String bot = switch (c.id) { case VehicleControls.SW_AUTOPILOT -> lit ? "ENGD" : "OFF"; case VehicleControls.SW_STARTER -> lit && lc == AMBER ? "VALVE" : "ON"; default -> "SYS"; };
        int tc = lit ? CockpitParts.scale(lc, bright) : 0xFF5A5E64;
        label(ps, buf, font, top, 0, -0.022f, tc, lit ? FULL : light, 1 / 380f);
        label(ps, buf, font, bot, 0, 0.024f, tc, lit ? FULL : light, 1 / 380f);

    }

    /** MASTER WARNING / MASTER CAUTION: large square push light that flashes until acknowledged */
    static void master(PoseStack ps, MultiBufferSource buf, Font font, TextureAtlasSprite glass, TextureAtlasSprite bezel, boolean in, VehicleTelemetry t, float bright, int light) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        float d = in ? 0.012f : 0.03f;
        Box.cuboid(ps, vc, bezel, -0.13f, -0.016f, -0.1f, 0.13f, 0, 0.1f, light, 0xFF26292D);
        Box.cuboid(ps, vc, bezel, -0.115f, -0.016f - d, -0.085f, 0.115f, -0.016f, 0.085f, light, 0xFF141618);
        boolean any = t.busPowered && (VoiceCallouts.warning || t.emergencyTest);
        boolean red = t.emergencyTest || redActive();
        boolean flash = VoiceCallouts.unacknowledged() || t.emergencyTest;
        boolean on = any && (!flash || (System.currentTimeMillis() / 300) % 2 == 0);
        int wc = on && red ? CockpitParts.scale(RED, bright) : 0xFF3A1210, cc = on && (!red || t.emergencyTest) ? CockpitParts.scale(AMBER, bright) : 0xFF3A2A0E;
        CockpitParts.plate(ps, vc, glass, -0.11f, -0.08f, 0.11f, 0f, -0.0165f - d, on && red ? FULL : light, on && red ? CockpitParts.mix(wc, 0xFF000000, 0.8f) : 0xFF1A0A09);
        CockpitParts.plate(ps, vc, glass, -0.11f, 0f, 0.11f, 0.08f, -0.0165f - d, on && !red ? FULL : light, on && !red ? CockpitParts.mix(cc, 0xFF000000, 0.8f) : 0xFF1A1408);
        label(ps, buf, font, "MASTER WARN", 0, -0.04f, on && red ? wc : 0xFF7A3430, on && red ? FULL : light, 1 / 360f);
        label(ps, buf, font, "MASTER CAUT", 0, 0.04f, on && !red ? cc : 0xFF7A6030, on && !red ? FULL : light, 1 / 360f);
    }

    /** annunciator: rectangular tinted glass lens with an engraved legend, lit from behind */
    static void annunciator(PoseStack ps, MultiBufferSource buf, Font font, TextureAtlasSprite glass, TextureAtlasSprite bezel, float x, float z, String legend, int color, boolean on, float bright, int light) {
        ps.pushPose();
        ps.translate(x, 0, z);
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        // recessed bezel, dark smoked lens; when lit, the engraved legend glows in its colour
        Box.cuboid(ps, vc, bezel, -0.12f, -0.012f, -0.05f, 0.12f, 0, 0.05f, light, 0xFF1A1C1F);
        int lit = CockpitParts.scale(color, bright);
        CockpitParts.plate(ps, vc, glass, -0.11f, -0.04f, 0.11f, 0.04f, -0.0125f, on ? FULL : light, on ? CockpitParts.mix(lit, 0xFF000000, 0.86f) : 0xFF0E0F11);
        label(ps, buf, font, legend, 0, 0.0f, on ? lit : CockpitParts.mix(color, 0xFF000000, 0.78f), on ? FULL : light, 1 / 430f);
        ps.popPose();
    }

    /** small round indicator lamp with a chrome bezel and coloured glass */
    static void lensLamp(PoseStack ps, MultiBufferSource buf, TextureAtlasSprite glass, TextureAtlasSprite bezel, float x, float z, int lc, float bright, int light) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        CockpitParts.prism(ps, vc, bezel, x, z, 0.022f, 0.02f, -0.001f, -0.012f, 10, light, 0xFF9EA4AA, 0xFF8A9096, false);
        boolean on = lc != 0;
        int col = on ? CockpitParts.scale(lc, bright) : 0xFF2A2C2E;
        CockpitParts.prism(ps, vc, glass, x, z, 0.016f, 0.014f, -0.012f, -0.02f, 10, on ? FULL : light, col, col, true);
        if (on) {
            VertexConsumer glow = buf.getBuffer(RenderType.lightning());
            LightFx.begin(ps);
            LightFx.sprite(ps, buf, LightFx.GLOW, x, -0.021f, z, 0.03f, col & 0xFFFFFF, 0.55f);
        }
    }

    /** landing gear lever: slotted gate, metal arm and the wheel-shaped knob */
    static void gearLever(PoseStack ps, VertexConsumer vc, TextureAtlasSprite metal, TextureAtlasSprite black, TextureAtlasSprite white, float pos, VehicleTelemetry t, int light) {
        Box.cuboid(ps, vc, black, -0.08f, -0.02f, -0.16f, 0.08f, 0, 0.16f, light, 0xFF1A1C1F);
        Box.cuboid(ps, vc, black, -0.015f, -0.021f, -0.14f, 0.015f, -0.019f, 0.14f, light, 0xFF050505); // gate slot
        ps.pushPose();
        ps.translate(0, -0.01f, 0);
        ps.mulPose(Axis.XP.rotationDegrees(35 - 70 * pos)); // down = toward the pilot
        CockpitParts.prism(ps, vc, metal, 0, 0, 0.012f, 0.01f, 0, -0.2f, 8, light, 0xFFB8BEC4, 0xFFA8AEB4, false);
        // wheel knob: a short wide cylinder lying across the lever
        ps.pushPose();
        ps.translate(0, -0.225f, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(90));
        CockpitParts.prism(ps, vc, white, 0, 0, 0.042f, 0.042f, 0.028f, -0.028f, 14, light, 0xFFF2F2EE, 0xFFE0E0DA, true);
        CockpitParts.prism(ps, vc, black, 0, 0, 0.018f, 0.018f, 0.03f, -0.03f, 10, light, 0xFF2A2A2A, 0xFF2A2A2A, true);
        ps.popPose();
        ps.popPose();
        // three green / red gear lamps beside the lever
        boolean transit = gearInTransit(t);
        for (int i = -1; i <= 1; i++) {
            int lc = !t.busPowered ? 0 : (transit ? RED : (t.gearDown ? GREEN : 0));
            if (t.emergencyTest) lc = GREEN;
            lensLampRaw(ps, vc, metal, 0.12f, i * 0.06f, lc, light);
        }
    }

    private static void lensLampRaw(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float x, float z, int lc, int light) {
        boolean on = lc != 0;
        CockpitParts.prism(ps, vc, sp, x, z, 0.02f, 0.018f, -0.001f, -0.01f, 10, light, 0xFF9EA4AA, 0xFF8A9096, false);
        CockpitParts.prism(ps, vc, sp, x, z, 0.014f, 0.013f, -0.01f, -0.018f, 10, on ? FULL : light, on ? lc : 0xFF2A2C2E, on ? lc : 0xFF2A2C2E, true);
    }

    /** parking brake: T-handle pulled out of its housing when set */
    static void parkHandle(PoseStack ps, VertexConsumer vc, TextureAtlasSprite metal, TextureAtlasSprite black, float pos, int light) {
        CockpitParts.prism(ps, vc, black, 0, 0, 0.035f, 0.035f, -0.001f, -0.02f, 12, light, 0xFF1E2023, 0xFF1E2023, true);
        float out = 0.015f + 0.06f * pos;
        CockpitParts.prism(ps, vc, metal, 0, 0, 0.01f, 0.01f, -0.02f, -0.02f - out, 8, light, 0xFFC0C6CC, 0xFFB0B6BC, false);
        Box.cuboid(ps, vc, black, -0.055f, -0.04f - out, -0.012f, 0.055f, -0.02f - out, 0.012f, light, 0xFFC09018); // yellow T handle
    }
}
