package com.vehicleworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.vehicle.CabinSeat;
import com.vehicleworks.vehicle.CockpitLayout;
import com.vehicleworks.vehicle.VehicleBuilder;
import com.vehicleworks.vehicle.VehicleControls;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Airliner fixtures drawn live from their state: centre pedestal (thrust, flaps, speed brake, gear,
 * engine masters, parking brake, two MCDUs), FCU on the glareshield, passenger / cargo doors,
 * lavatories (door, occupied sign, toilet, basin, mirror, light), galleys, trolleys, folding
 * cabin crew seats and the attendant panel.
 */
public final class FixtureRenderer {
    private FixtureRenderer() {}

    private static final int FULL = 0xF000F0;
    private static final java.util.WeakHashMap<VehicleEntity, it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<float[]>> ANIM = new java.util.WeakHashMap<>();
    private static long last = System.currentTimeMillis();

    static VertexConsumer S(MultiBufferSource b) {
        return b.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
    }

    /** smoothed value per fixture */
    private static float smooth(VehicleEntity v, long k, int slot, float target, float rate, float dt) {
        float[] a = ANIM.computeIfAbsent(v, x -> new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>()).computeIfAbsent(k, x -> new float[]{Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN});
        if (Float.isNaN(a[slot])) a[slot] = target;
        a[slot] += (target - a[slot]) * Math.min(1, rate * dt);
        return a[slot];
    }

    public static void render(VehicleEntity v, PoseStack ps, MultiBufferSource buf, int light, double camDist) {
        if (camDist > 72) return;
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, Math.max(0.001f, (now - last) / 1000f));
        last = now;
        VehicleTelemetry t = v.telemetry;
        for (long k : v.structure.blockEntities().keySet()) {
            BlockState st = v.structure.get(k);
            ComponentType ct = VehicleBuilder.typeOf(st);
            if (ct == null) continue;
            switch (ct) {
                case CENTER_PEDESTAL, FCU_PANEL, AIRCRAFT_DOOR, LAVATORY, GALLEY, TROLLEY, JUMPSEAT, CREW_PANEL -> {}
                default -> { continue; }
            }
            ps.pushPose();
            ps.translate(BlockPos.getX(k) + 0.5, BlockPos.getY(k), BlockPos.getZ(k) + 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(-CockpitRenderer.facingRot(VehicleBuilder.facing(st))));
            int s = v.cabin.containsKey(k) ? v.cabin.get(k) : 0;
            switch (ct) {
                case CENTER_PEDESTAL -> pedestal(ps, buf, v, t, k, light, dt);
                case FCU_PANEL -> fcu(ps, buf, t, light);
                case AIRCRAFT_DOOR -> door(ps, buf, v, t, k, smooth(v, k, 0, (s & 1), 1.6f, dt), light);
                case LAVATORY -> lavatory(ps, buf, v, t, k, smooth(v, k, 0, (s & 1), 4f, dt), light);
                case GALLEY -> galley(ps, buf, t, s, smooth(v, k, 0, s == 1 ? 1 : 0, 5f, dt), light);
                case TROLLEY -> trolley(ps, buf, smooth(v, k, 0, (byte) (s & 255), 1.4f, dt), light);
                case JUMPSEAT -> jumpseat(ps, buf, smooth(v, k, 0, occupied(v, k) ? 1 : 0, 5f, dt), light);
                case CREW_PANEL -> crewPanel(ps, buf, v, t, light);
                default -> {}
            }
            ps.popPose();
        }
    }

    static boolean occupied(VehicleEntity v, long k) {
        return v.seats.containsValue(k);
    }

    // ---------------------------------------------------------------- small helpers

    static TextureAtlasSprite sp(String n) { return CockpitParts.sprite(n); }

    /** text lying on a horizontal surface, readable from behind (+z) looking down */
    static void upLabel(PoseStack ps, MultiBufferSource buf, String s, float x, float y, float z, float scale, int color, int light) {
        Font font = Minecraft.getInstance().font;
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.XP.rotationDegrees(-90));
        ps.scale(scale, -scale, scale);
        font.drawInBatch(s, -font.width(s) / 2f, -4, color, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, light);
        ps.popPose();
    }

    /** text on a vertical face looking toward +z */
    static void faceLabel(PoseStack ps, MultiBufferSource buf, String s, float x, float y, float z, float scale, int color, int light) {
        Font font = Minecraft.getInstance().font;
        ps.pushPose();
        ps.translate(x, y, z);
        ps.scale(scale, -scale, scale);
        font.drawInBatch(s, -font.width(s) / 2f, -4, color, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, light);
        ps.popPose();
    }

    /** a lever on a horizontal console: pivot at (x, y, z), tilts about x by angle (deg, + = aft) */
    static void lever(PoseStack ps, MultiBufferSource buf, float x, float y, float z, float angle, float len, int handle, int light) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        ps.mulPose(Axis.XP.rotationDegrees(180)); // prisms grow toward -y: point the lever up
        CockpitParts.prism(ps, S(buf), sp(CockpitParts.METAL), 0, 0, 0.012f, 0.01f, 0, -len, 8, light, 0xFFB8BEC4, 0xFFA8AEB4, false);
        ps.popPose();
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        Box.cuboid(ps, S(buf), sp(CockpitParts.BLACK), -0.035f, len - 0.01f, -0.025f, 0.035f, len + 0.04f, 0.025f, light, handle);
        ps.popPose();
    }

    // ---------------------------------------------------------------- centre pedestal

    static void pedestal(PoseStack ps, MultiBufferSource buf, VehicleEntity v, VehicleTelemetry t, long k, int light, float dt) {
        TextureAtlasSprite black = sp(CockpitParts.BLACK), metal = sp(CockpitParts.METAL), white = sp(CockpitParts.WHITE);
        float top = CockpitLayout.PED_TOP;
        boolean power = t.busPowered;
        int lit = power ? FULL : light;
        int paint = power ? 0xFFE8E2C8 : 0xFFB8BCC0;
        // body: front console under the panel, the lever box between the seats, the aft radio panels
        Box.cuboid(ps, S(buf), black, -0.34f, 0, -0.5f, 0.34f, top, 1.0f, light, 0xFF2E3136);
        Box.cuboid(ps, S(buf), black, -0.35f, top - 0.005f, -0.5f, 0.35f, top + 0.005f, 1.0f, light, 0xFF24272B);
        for (float z : new float[]{-0.22f, 0.1f, 0.3f, 0.5f}) Box.cuboid(ps, S(buf), white, -0.34f, top + 0.0055f, z - 0.002f, 0.34f, top + 0.006f, z + 0.002f, lit, paint);
        // two MCDUs: screen + key pad, sloping toward the pilots
        for (int m = 0; m < 2; m++) {
            float cx = m == 0 ? -0.17f : 0.17f;
            ps.pushPose();
            ps.translate(cx, top, -0.36f);
            ps.mulPose(Axis.XP.rotationDegrees(-25));
            Box.cuboid(ps, S(buf), black, -0.15f, 0, -0.13f, 0.15f, 0.04f, 0.13f, light, 0xFF1C1E21);
            Box.cuboid(ps, S(buf), black, -0.12f, 0.0405f, -0.12f, 0.12f, 0.042f, -0.02f, power ? FULL : light, power ? 0xFF05080C : 0xFF0A0B0D);
            for (int r = 0; r < 4; r++) for (int c = 0; c < 6; c++)
                Box.cuboid(ps, S(buf), black, -0.12f + c * 0.04f, 0.04f, 0.0f + r * 0.03f, -0.092f + c * 0.04f, 0.048f, 0.022f + r * 0.03f, light, 0xFF5A5E64);
            if (power) mcdu(ps, buf, m == 0 ? t.mcdu1 : t.mcdu2, t, m);
            ps.popPose();
        }
        // thrust levers with the detent gate (IDLE, CL, FLX/MCT, TOGA)
        float thr = smooth(v, k, 0, t.throttle, 6f, dt);
        for (float x : new float[]{-0.07f, 0.07f}) lever(ps, buf, x, top, -0.06f, -38 + 76 * (1 - thr), 0.17f, 0xFF202326, light);
        String[] det = {"TOGA", "FLX", "CL", "IDLE"};
        for (int i = 0; i < 4; i++) upLabel(ps, buf, det[i], 0.0f, top + 0.0065f, -0.2f + i * 0.07f, 1 / 900f, paint, lit);
        // speed brake (left) and flaps (right)
        float sb = smooth(v, k, 1, t.speedBrake, 4f, dt), fl = smooth(v, k, 2, t.flaps, 4f, dt);
        lever(ps, buf, -0.24f, top, -0.06f, -25 + 50 * sb, 0.12f, 0xFF9AA0A6, light);
        lever(ps, buf, 0.24f, top, -0.06f, -25 + 50 * fl, 0.12f, 0xFFF0F0EC, light);
        upLabel(ps, buf, "SPD BRK", -0.24f, top + 0.0065f, 0.07f, 1 / 900f, paint, lit);
        upLabel(ps, buf, "FLAPS", 0.24f, top + 0.0065f, 0.07f, 1 / 900f, paint, lit);
        // engine master switches (lift and flip) and the mode selector
        float master = smooth(v, k, 3, t.sw(VehicleControls.SW_ENG_MASTER) ? 1 : 0, 18f, dt);
        for (float x : new float[]{-0.24f, -0.1f}) {
            ps.pushPose();
            ps.translate(x, top + 0.005f, 0.2f);
            ps.mulPose(Axis.XP.rotationDegrees(180));
            CockpitRenderer.toggle(ps, S(buf), metal, black, master, light);
            ps.popPose();
        }
        upLabel(ps, buf, "ENG 1   ENG 2", -0.17f, top + 0.0065f, 0.27f, 1 / 900f, paint, lit);
        ps.pushPose();
        ps.translate(0.17f, top + 0.005f, 0.2f);
        ps.mulPose(Axis.XP.rotationDegrees(180));
        CockpitRenderer.rotary(ps, S(buf), sp(CockpitParts.KNOB), black, metal, t.sw(VehicleControls.BIT_STARTER) ? 1 : 0.5f, 3, light, power ? 1 : 0);
        ps.popPose();
        upLabel(ps, buf, "MODE  IGN/START", 0.17f, top + 0.0065f, 0.28f, 1 / 900f, paint, lit);
        // parking brake knob and the landing gear lever (with its three lamps)
        ps.pushPose();
        ps.translate(-0.17f, top + 0.005f, 0.4f);
        ps.mulPose(Axis.XP.rotationDegrees(180));
        CockpitRenderer.parkHandle(ps, S(buf), metal, black, t.parkingBrake ? 1 : 0, light);
        ps.popPose();
        upLabel(ps, buf, "PARK BRK", -0.17f, top + 0.0065f, 0.48f, 1 / 900f, paint, lit);
        float gear = smooth(v, k, 4, t.gearDown ? 1 : 0, 3.5f, dt);
        ps.pushPose();
        ps.translate(0.17f, top + 0.005f, 0.4f);
        ps.mulPose(Axis.XP.rotationDegrees(180));
        CockpitRenderer.gearLever(ps, S(buf), metal, black, sp(CockpitParts.WHITE), gear, t, light);
        int gc = t.emergencyTest ? CockpitRenderer.GREEN : (!power ? 0 : (CockpitRenderer.gearInTransit(t) ? CockpitRenderer.RED : (t.gearDown ? CockpitRenderer.GREEN : 0)));
        if (gc != 0) {
            LightFx.begin(ps);
            for (int gi = -1; gi <= 1; gi++) LightFx.sprite(ps, buf, LightFx.GLOW, 0.12f, -0.02f, gi * 0.06f, 0.035f, gc & 0xFFFFFF, 0.5f);
        }
        ps.popPose();
        upLabel(ps, buf, "L/G  UP / DN", 0.17f, top + 0.0065f, 0.49f, 1 / 900f, paint, lit);
        // aft section: radio management panels and a cup holder
        for (int r = 0; r < 2; r++) {
            Box.cuboid(ps, S(buf), black, -0.3f, top + 0.001f, 0.55f + r * 0.2f, 0.3f, top + 0.01f, 0.72f + r * 0.2f, light, 0xFF1E2023);
            Box.cuboid(ps, S(buf), black, -0.2f, top + 0.0105f, 0.58f + r * 0.2f, 0.2f, top + 0.012f, 0.64f + r * 0.2f, lit, power ? 0xFF3A2808 : 0xFF0E0E0E);
            if (power) upLabel(ps, buf, r == 0 ? "118.700   121.500" : "XPDR 2000", 0, top + 0.0125f, 0.61f + r * 0.2f, 1 / 900f, 0xFFFFB040, FULL);
        }
    }

    static void mcdu(PoseStack ps, MultiBufferSource buf, int page, VehicleTelemetry t, int unit) {
        Font font = Minecraft.getInstance().font;
        String[][] pages = {
                {"INIT", "CO RTE  VW1320", "FROM/TO  VWX/VWY", "FLT NBR  VW" + (unit + 1) + "0", "CRZ FL  FL350"},
                {"PERF TAKE OFF", "V1 140  VR 145", "V2 150  FLX 52", "FLAPS/THS 1/UP1.0", String.format("THR RED %.0f", 450f)},
                {"FUEL PRED", String.format("FOB  %.1f", t.fuel / 1000f), String.format("FF   %.2f", t.fuelFlow), "RESERVE  1.5", "EXTRA   2.0"},
                {"PROG", String.format("ALT  %.0f", t.altitude), String.format("GS   %.0f KT", t.groundSpeed * 1.944f), String.format("HDG  %03.0f", t.heading), t.navActive ? String.format("DIST %.1f", t.navDistance / 1000f) : "DIST ---"}};
        String[] p = pages[Math.max(0, Math.min(3, page))];
        for (int i = 0; i < p.length; i++) {
            ps.pushPose();
            ps.translate(-0.11f, 0.0425f, -0.11f + i * 0.019f);
            ps.mulPose(Axis.XP.rotationDegrees(90));
            float k = 1 / 1000f;
            ps.scale(k, k, k);
            font.drawInBatch(p[i], 0, 0, i == 0 ? 0xFFFFFFFF : 0xFF40FF70, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, FULL);
            ps.popPose();
        }
    }

    // ---------------------------------------------------------------- FCU

    static void fcu(PoseStack ps, MultiBufferSource buf, VehicleTelemetry t, int light) {
        TextureAtlasSprite black = sp(CockpitParts.BLACK), knob = sp(CockpitParts.KNOB), metal = sp(CockpitParts.METAL);
        float z0 = -0.5f, z1 = CockpitLayout.FCU_FACE_Z, y0 = CockpitLayout.FCU_Y0, y1 = CockpitLayout.FCU_Y1;
        boolean power = t.busPowered;
        Box.cuboid(ps, S(buf), black, -1.48f, y0, z0, 1.48f, y1, z1, light, 0xFF34383D);              // glareshield housing
        Box.cuboid(ps, S(buf), black, -1.5f, y1, z0 - 0.02f, 1.5f, y1 + 0.02f, z1 + 0.03f, light, 0xFF1E2023); // top lip
        String[] names = {"SPD", "HDG", "ALT", "V/S"};
        float[] cx = {-1.1f, -0.45f, 0.7f, 1.25f};
        String[] vals = power ? new String[]{t.autothrust ? String.format("%03.0f", t.apSpeed * 1.944f) : "---", String.format("%03.0f", t.apHeading),
                String.format("%05.0f", Math.max(0, t.apAltitude)), String.format("%+05.0f", t.verticalSpeed * 196.85f)} : new String[]{"", "", "", ""};
        for (int i = 0; i < 4; i++) {
            // LCD window
            Box.cuboid(ps, S(buf), black, cx[i] - 0.13f, 0.075f, z1, cx[i] + 0.13f, 0.125f, z1 + 0.002f, power ? FULL : light, power ? 0xFF0A0904 : 0xFF0C0C0C);
            if (power) faceLabel(ps, buf, vals[i], cx[i], 0.1f, z1 + 0.003f, 1 / 420f, 0xFFFFB040, FULL);
            faceLabel(ps, buf, names[i], cx[i], 0.137f, z1 + 0.003f, 1 / 700f, 0xFFDDDDDD, power ? FULL : light);
            // selector knob below the window
            ps.pushPose();
            ps.translate(cx[i], 0.035f, z1);
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            CockpitParts.prism(ps, S(buf), knob, 0, 0, 0.03f, 0.027f, 0, -0.04f, 16, light, 0xFF1C1E21, 0xFF2A2D31, true);
            ps.popPose();
            if (i < 3) faceLabel(ps, buf, "-    +", cx[i], 0.035f, z1 + 0.042f, 1 / 500f, 0xFF9AA0A6, power ? FULL : light);
        }
        // AP1 and A/THR push buttons with green bars
        boolean[] on = {t.autopilot, t.autothrust};
        String[] bn = {"AP1", "A/THR"};
        float[] bx = {0f, 0.2f};
        for (int i = 0; i < 2; i++) {
            Box.cuboid(ps, S(buf), black, bx[i] - 0.08f, 0.02f, z1, bx[i] + 0.08f, 0.13f, z1 + 0.02f, light, 0xFF1A1C1F);
            faceLabel(ps, buf, bn[i], bx[i], 0.06f, z1 + 0.021f, 1 / 600f, 0xFFDDDDDD, power ? FULL : light);
            if (on[i] && power) {
                Box.cuboid(ps, S(buf), sp(CockpitParts.WHITE), bx[i] - 0.05f, 0.1f, z1 + 0.02f, bx[i] + 0.05f, 0.112f, z1 + 0.022f, FULL, 0xFF30FF60);
                LightFx.begin(ps);
                LightFx.sprite(ps, buf, LightFx.GLOW, bx[i], 0.106f, z1 + 0.03f, 0.05f, 0x30FF60, 0.35f);
            }
        }
    }

    // ---------------------------------------------------------------- doors

    /** passenger / cargo door in the fuselage wall (-z = outside): opens outward and slides forward along the fuselage */
    static void door(PoseStack ps, MultiBufferSource buf, VehicleEntity v, VehicleTelemetry t, long k, float open, int light) {
        boolean cargo = ComponentType.AIRCRAFT_DOOR.property("door_kind").getInt(VehicleBuilder.config(v.structure, k)) == 1;
        float h = cargo ? 1f : 2f;
        ps.pushPose();
        ps.translate(-0.95f * open, 0, -0.42f * Math.min(1, open * 2));
        TextureAtlasSprite shell = sp("cabin_shell"), glass = sp(CockpitParts.GLASS), metal = sp(CockpitParts.METAL);
        Box.cuboid(ps, S(buf), shell, -0.48f, 0.02f, -0.08f, 0.48f, h - 0.02f, 0.08f, light, 0xFFF2F2EE);       // door panel
        Box.cuboid(ps, S(buf), shell, -0.4f, 0.1f, 0.08f, 0.4f, h - 0.1f, 0.1f, light, 0xFFE4E0D6);            // inner lining
        if (!cargo) {
            CockpitParts.plate(ps, S(buf), glass, -0.12f, 1.35f, 0.12f, 1.6f, -0.081f, light, 0xFF8CA8C8);      // porthole (outside)
            Box.cuboid(ps, S(buf), glass, -0.12f, 1.35f, 0.1f, 0.12f, 1.6f, 0.102f, light, 0xFF8CA8C8);         // porthole (inside)
            Box.cuboid(ps, S(buf), metal, -0.3f, 0.95f, 0.1f, 0.15f, 1.0f, 0.14f, light, 0xFFC8CCD0);           // handle
            Box.cuboid(ps, S(buf), shell, -0.38f, 0.1f, 0.1f, 0.38f, 0.42f, 0.2f, light, 0xFFC8C4BA);           // escape slide bustle
        }
        ps.popPose();
        // EXIT sign above the door inside, door status light
        if (!cargo && t.busPowered) {
            Box.cuboid(ps, S(buf), sp(CockpitParts.WHITE), -0.14f, 2.0f, 0.16f, 0.14f, 2.1f, 0.2f, FULL, 0xFF20C040);
            faceLabel(ps, buf, "EXIT", 0, 2.05f, 0.201f, 1 / 250f, 0xFFFFFFFF, FULL);
            LightFx.begin(ps);
            LightFx.sprite(ps, buf, LightFx.GLOW, 0, 2.05f, 0.25f, 0.2f, 0x30FF60, 0.15f);
        }
    }

    // ---------------------------------------------------------------- lavatory

    static void lavatory(PoseStack ps, MultiBufferSource buf, VehicleEntity v, VehicleTelemetry t, long k, float open, int light) {
        TextureAtlasSprite shell = sp("cabin_shell"), white = sp(CockpitParts.WHITE), metal = sp(CockpitParts.METAL), glass = sp(CockpitParts.GLASS);
        float h = 1.98f;
        boolean power = t.busPowered;
        boolean occ = occupant(v, k);
        int in = power ? CockpitParts.boost(light, 12) : light;
        Box.cuboid(ps, S(buf), shell, -0.5f, 0, 0.44f, 0.5f, h, 0.5f, in, 0xFFEAE6DC);     // back wall
        Box.cuboid(ps, S(buf), shell, -0.5f, 0, -0.5f, -0.44f, h, 0.44f, in, 0xFFEAE6DC);  // side walls
        Box.cuboid(ps, S(buf), shell, 0.44f, 0, -0.5f, 0.5f, h, 0.44f, in, 0xFFEAE6DC);
        Box.cuboid(ps, S(buf), shell, -0.5f, h - 0.04f, -0.5f, 0.5f, h, 0.5f, in, 0xFFE0DCD2); // ceiling
        // toilet with lid, basin cabinet, tap, mirror, soap dispenser, light
        Box.cuboid(ps, S(buf), white, -0.16f, 0, 0.1f, 0.16f, 0.32f, 0.42f, in, 0xFFF4F4F4);
        Box.cuboid(ps, S(buf), white, -0.17f, 0.32f, 0.08f, 0.17f, 0.35f, 0.42f, in, 0xFFE8E8E8);
        Box.cuboid(ps, S(buf), white, -0.17f, 0.35f, 0.38f, 0.17f, 0.7f, 0.42f, in, 0xFFE8E8E8);
        Box.cuboid(ps, S(buf), shell, 0.1f, 0, -0.42f, 0.43f, 0.78f, 0.05f, in, 0xFFD8D2C4);
        Box.cuboid(ps, S(buf), white, 0.16f, 0.78f, -0.32f, 0.38f, 0.8f, -0.06f, in, 0xFFF4F4F4);
        Box.cuboid(ps, S(buf), metal, 0.39f, 0.8f, -0.21f, 0.42f, 0.92f, -0.17f, in, 0xFFD8DCE0);
        Box.cuboid(ps, S(buf), metal, 0.33f, 0.9f, -0.2f, 0.42f, 0.92f, -0.18f, in, 0xFFD8DCE0);
        Box.cuboid(ps, S(buf), glass, 0.435f, 0.95f, -0.4f, 0.44f, 1.5f, 0f, in, 0xFFB8D0E0);       // mirror
        Box.cuboid(ps, S(buf), white, 0.4f, 1.0f, 0.1f, 0.44f, 1.15f, 0.18f, in, 0xFFE0F0FF);        // soap
        if (power) {
            Box.cuboid(ps, S(buf), white, 0.42f, 1.55f, -0.4f, 0.44f, 1.58f, 0f, FULL, 0xFFFFF4E0);  // light over the mirror
            LightFx.begin(ps);
            LightFx.sprite(ps, buf, LightFx.GLOW, 0.4f, 1.565f, -0.2f, 0.25f, 0xFFF4E0, 0.2f);
        }
        // bi-fold door on the aisle side (hinged left), opens outward
        ps.pushPose();
        ps.translate(-0.44f, 0, -0.47f);
        ps.mulPose(Axis.YP.rotationDegrees(95 * open));
        Box.cuboid(ps, S(buf), shell, 0, 0.02f, -0.025f, 0.88f, h - 0.08f, 0.025f, light, 0xFFE4E0D6);
        Box.cuboid(ps, S(buf), metal, 0.74f, 0.95f, -0.05f, 0.8f, 1.05f, -0.025f, light, 0xFFC8CCD0);
        ps.popPose();
        // occupied / vacant sign above the door outside
        if (power) {
            boolean busy = occ && open < 0.1f;
            Box.cuboid(ps, S(buf), white, -0.2f, h - 0.16f, -0.53f, 0.2f, h - 0.06f, -0.5f, FULL, busy ? 0xFFC02020 : 0xFF208040);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180));
            faceLabel(ps, buf, busy ? "OCCUPIED" : "VACANT", 0, h - 0.11f, 0.531f, 1 / 450f, 0xFFFFFFFF, FULL);
            ps.popPose();
        }
    }

    /** someone (a player) stands inside the lavatory cell */
    static boolean occupant(VehicleEntity v, long k) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        for (var p : mc.level.players()) {
            org.joml.Vector3d l = v.worldToLocal(p.getX(), p.getY(), p.getZ(), new org.joml.Vector3d());
            if (Math.floor(l.x) == BlockPos.getX(k) && Math.floor(l.z) == BlockPos.getZ(k) && l.y > BlockPos.getY(k) - 0.5 && l.y < BlockPos.getY(k) + 1.5) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- galley

    static void galley(PoseStack ps, MultiBufferSource buf, VehicleTelemetry t, int st, float ovenOpen, int light) {
        TextureAtlasSprite metal = sp(CockpitParts.METAL), black = sp(CockpitParts.BLACK), shell = sp("cabin_shell");
        boolean power = t.busPowered;
        Box.cuboid(ps, S(buf), metal, -0.5f, 0, -0.1f, 0.5f, 2.0f, 0.5f, light, 0xFFC4C8CC);                 // unit
        Box.cuboid(ps, S(buf), black, -0.46f, 0.02f, -0.11f, 0.46f, 0.84f, -0.09f, light, 0xFF2A2C30);      // trolley bay
        Box.cuboid(ps, S(buf), metal, -0.5f, 0.84f, -0.3f, 0.5f, 0.88f, 0.5f, light, 0xFFD8DCE0);             // work top
        for (int i = 0; i < 2; i++) {
            float x0 = -0.45f + i * 0.47f;
            // ovens: black glass doors that open downward, red lamp when on
            ps.pushPose();
            ps.translate(x0, 1.0f, -0.11f);
            ps.mulPose(Axis.XP.rotationDegrees(-85 * (i == 0 ? ovenOpen : 0)));
            Box.cuboid(ps, S(buf), black, 0, 0, -0.02f, 0.42f, 0.32f, 0, light, 0xFF141618);
            Box.cuboid(ps, S(buf), metal, 0.05f, 0.27f, -0.04f, 0.37f, 0.29f, -0.02f, light, 0xFFD0D4D8);
            ps.popPose();
            if (power) Box.cuboid(ps, S(buf), sp(CockpitParts.WHITE), x0 + 0.36f, 1.36f, -0.115f, x0 + 0.4f, 1.39f, -0.11f, FULL, st == 1 && i == 0 ? 0xFFFF3020 : 0xFF402020);
            // upper cabinets with latches
            Box.cuboid(ps, S(buf), shell, x0, 1.45f, -0.115f, x0 + 0.42f, 1.95f, -0.1f, light, st == 3 ? 0xFFB8B4AA : 0xFFE4E0D6);
            Box.cuboid(ps, S(buf), metal, x0 + 0.18f, 1.62f, -0.125f, x0 + 0.24f, 1.68f, -0.115f, light, 0xFFC8CCD0);
        }
        // coffee makers on the work top
        for (float x : new float[]{-0.3f, 0.15f}) {
            Box.cuboid(ps, S(buf), black, x, 0.88f, 0.05f, x + 0.18f, 0.98f, 0.3f, light, 0xFF202226);
            Box.cuboid(ps, S(buf), metal, x + 0.05f, 0.88f, -0.05f, x + 0.13f, 0.95f, 0.05f, light, 0xFFE0E0E0);
            if (power && st == 2) Box.cuboid(ps, S(buf), sp(CockpitParts.WHITE), x + 0.02f, 0.96f, 0.049f, x + 0.05f, 0.975f, 0.05f, FULL, 0xFF30FF60);
        }
    }

    // ---------------------------------------------------------------- trolley

    static void trolley(PoseStack ps, MultiBufferSource buf, float offset, int light) {
        TextureAtlasSprite metal = sp(CockpitParts.METAL), black = sp(CockpitParts.BLACK), white = sp(CockpitParts.WHITE);
        ps.pushPose();
        ps.translate(0, 0, -offset); // pushed along the facing (-z)
        Box.cuboid(ps, S(buf), metal, -0.17f, 0.06f, -0.38f, 0.17f, 0.95f, 0.38f, light, 0xFFB8BCC0);
        for (int i = 0; i < 4; i++) Box.cuboid(ps, S(buf), black, -0.171f, 0.15f + i * 0.2f, -0.3f, 0.171f, 0.16f + i * 0.2f, 0.3f, light, 0xFF5A5E64);
        Box.cuboid(ps, S(buf), black, -0.15f, 0.95f, 0.3f, 0.15f, 0.99f, 0.38f, light, 0xFF303236); // handle
        for (float x : new float[]{-0.12f, 0.12f}) for (float z : new float[]{-0.3f, 0.3f})
            CockpitParts.prism(ps, S(buf), black, x, z, 0.035f, 0.035f, 0.06f, 0, 8, light, 0xFF202020, 0xFF202020, true);       // wheels
        // cups and bottles on top
        for (int i = 0; i < 4; i++) Box.cuboid(ps, S(buf), white, -0.12f + i * 0.07f, 0.95f, -0.25f, -0.08f + i * 0.07f, 1.0f, -0.21f, light, 0xFFF4F4F4);
        Box.cuboid(ps, S(buf), white, 0.05f, 0.95f, -0.05f, 0.09f, 1.12f, -0.01f, light, 0xFF3A8040);
        Box.cuboid(ps, S(buf), white, -0.09f, 0.95f, 0.02f, -0.05f, 1.1f, 0.06f, light, 0xFFD8A030);
        ps.popPose();
    }

    // ---------------------------------------------------------------- cabin crew seat

    /** folding jump seat on a wall (+z = wall): the pan folds up when nobody sits */
    static void jumpseat(PoseStack ps, MultiBufferSource buf, float unfold, int light) {
        TextureAtlasSprite fab = sp("seat_pilot2"), metal = sp(CockpitParts.METAL), harness = sp("seat_harness");
        Box.cuboid(ps, S(buf), metal, -0.3f, 0.2f, 0.4f, 0.3f, 1.25f, 0.5f, light, 0xFF8E949A);    // wall mount
        Box.cuboid(ps, S(buf), fab, -0.27f, 0.55f, 0.34f, 0.27f, 1.15f, 0.4f, light, 0xFFFFFFFF);   // back cushion
        Box.cuboid(ps, S(buf), fab, -0.2f, 1.17f, 0.34f, 0.2f, 1.35f, 0.4f, light, 0xFFFFFFFF);     // headrest
        Box.cuboid(ps, S(buf), harness, -0.18f, 0.6f, 0.335f, -0.12f, 1.1f, 0.34f, light, 0xFFFFFFFF);
        Box.cuboid(ps, S(buf), harness, 0.12f, 0.6f, 0.335f, 0.18f, 1.1f, 0.34f, light, 0xFFFFFFFF);
        ps.pushPose();
        ps.translate(0, 0.5f, 0.36f);
        ps.mulPose(Axis.XP.rotationDegrees(-90 * (1 - unfold))); // folded up against the wall
        Box.cuboid(ps, S(buf), fab, -0.27f, -0.04f, -0.45f, 0.27f, 0.04f, 0, light, 0xFFFFFFFF);
        ps.popPose();
        // interphone handset beside the seat
        Box.cuboid(ps, S(buf), sp(CockpitParts.BLACK), 0.34f, 0.95f, 0.42f, 0.42f, 1.2f, 0.48f, light, 0xFF2A2C30);
    }

    // ---------------------------------------------------------------- attendant panel

    static void crewPanel(PoseStack ps, MultiBufferSource buf, VehicleEntity v, VehicleTelemetry t, int light) {
        TextureAtlasSprite shell = sp("cabin_shell"), black = sp(CockpitParts.BLACK);
        boolean power = t.busPowered;
        Box.cuboid(ps, S(buf), shell, -0.5f, 0, -0.3f, 0.5f, 0.95f, 0.5f, light, 0xFFE4E0D6);
        ps.pushPose();
        ps.translate(0, 0.95f, -0.1f);
        ps.mulPose(Axis.XP.rotationDegrees(-60));
        Box.cuboid(ps, S(buf), black, -0.45f, 0, -0.18f, 0.45f, 0.04f, 0.25f, light, 0xFF202226);
        Box.cuboid(ps, S(buf), black, -0.38f, 0.041f, -0.14f, 0.38f, 0.043f, 0.18f, power ? FULL : light, power ? 0xFF061018 : 0xFF0A0B0C);
        if (power) {
            int calls = 0;
            for (var e : v.cabin.long2IntEntrySet()) if (CabinSeat.call(e.getIntValue())) calls++;
            String[] lines = {"FAP  CABIN", "LIGHTS " + new String[]{"OFF", "DIM", "BRT", "NIGHT"}[Math.max(0, Math.min(3, t.cabinLights))],
                    "SIGNS  " + (t.seatbelt ? "ON" : "OFF"), "DOORS  " + (t.doors != 0 ? "OPEN" : "CLOSED"), "CALLS  " + calls};
            Font font = Minecraft.getInstance().font;
            for (int i = 0; i < lines.length; i++) {
                ps.pushPose();
                ps.translate(-0.34f, 0.0435f, -0.12f + i * 0.06f);
                ps.mulPose(Axis.XP.rotationDegrees(90));
                float k = 1 / 500f;
                ps.scale(k, k, k);
                font.drawInBatch(lines[i], 0, 0, i == 0 ? 0xFFFFFFFF : (i == 3 && t.doors != 0 ? 0xFFFFB020 : (i == 4 && calls > 0 ? 0xFF38D3FF : 0xFF40FF70)), false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, FULL);
                ps.popPose();
            }
            LightFx.begin(ps);
            LightFx.sprite(ps, buf, LightFx.GLOW, 0, 0.06f, 0.02f, 0.4f, 0x4080C0, 0.05f);
        }
        ps.popPose();
        // handset
        Box.cuboid(ps, S(buf), black, 0.38f, 0.6f, -0.32f, 0.46f, 0.88f, -0.26f, light, 0xFF2A2C30);
    }
}
