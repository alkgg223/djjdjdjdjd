package com.vehicleworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vehicleworks.Config;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.vehicle.CabinSeat;
import com.vehicleworks.vehicle.VehicleBuilder;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The five passenger seat classes, animated from their synced state: seat slide, backrest
 * recline, leg rest, lie-flat bed, tray table, reading / ambient light, personal screen and the
 * sliding door of the first class suite. Screens show real data (altitude, speed, heading,
 * destination distance, moving map) or a small animated entertainment programme.
 */
public final class CabinRenderer {
    private CabinRenderer() {}

    private static final int FULL = 0xF000F0;

    /** solid consumer, fetched for every part (text and glow switch the buffer's render type) */
    static VertexConsumer S(MultiBufferSource b) {
        return b.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
    }
    /** smoothed animation per seat: slide, recline, leg, tray, door, screen power, light, screen-on time */
    static final class Anim {
        float slide = 2, recline, leg, tray, door, screen, light;
        long screenOn = Long.MIN_VALUE / 4, screenOff = Long.MIN_VALUE / 4;
        boolean screenWas;
        final long[] press = new long[CabinSeat.CONTROL_COUNT];
    }

    // ------------------------------------------------------------------ seat control buttons (on the left arm rest)

    static final String[] BTN = {"FWD", "BACK", "UP", "RECL", "LEG+", "LEG-", "BED", "TV", "TRAY", "LIGHT", "DOOR", "PAGE"};
    static final float ARM_H = 0.55f;

    static float halfWidth(int cls) {
        return switch (cls) { case 1 -> 0.4f; case 2 -> 0.44f; case 3 -> 0.45f; default -> 0.47f; };
    }

    /** the buttons a seat class has, in panel order */
    public static java.util.List<Integer> buttons(int cls) {
        java.util.List<Integer> l = new java.util.ArrayList<>();
        for (int c : SeatScreen.ORDER) if (CabinSeat.has(cls, c)) l.add(c);
        return l;
    }

    /** top centre of button i in the seat frame (x right, -z forward), before the seat slide */
    public static float[] buttonPos(int cls, int i) {
        float hw = halfWidth(cls);
        return new float[]{-hw - 0.022f + (i % 2) * 0.034f, ARM_H + 0.012f, -0.26f + (i / 2) * 0.042f};
    }

    /** seat slide offset (blocks along z) of a state */
    public static float slideOffset(int state) {
        return (CabinSeat.slide(state) - 2) * 0.06f;
    }

    /** a button was pressed on this client (it goes in at once) */
    public static void pressed(VehicleEntity v, long seat, int control) {
        Long2ObjectOpenHashMap<Anim> m = ANIM.get(v);
        if (m == null) return;
        Anim a = m.get(seat);
        if (a != null && control >= 0 && control < a.press.length) a.press[control] = System.currentTimeMillis();
    }

    /** is the function behind a button currently on (its legend is lit) */
    static boolean active(int control, int s) {
        return switch (control) {
            case CabinSeat.FLAT -> CabinSeat.flat(s);
            case CabinSeat.SCREEN -> CabinSeat.screen(s);
            case CabinSeat.TRAY -> CabinSeat.tray(s);
            case CabinSeat.LIGHT -> CabinSeat.light(s) > 0;
            case CabinSeat.DOOR -> CabinSeat.door(s);
            default -> false;
        };
    }

    static void seatButtons(PoseStack ps, VertexConsumer vc, MultiBufferSource buf, int cls, Anim a, int s, TextureAtlasSprite black, TextureAtlasSprite white, int light, VehicleEntity v, long now) {
        java.util.List<Integer> l = buttons(cls);
        float hw = halfWidth(cls);
        boolean power = v.telemetry.busPowered;
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        int rows = (l.size() + 1) / 2;
        // control plate set into the arm rest
        Box.cuboid(ps, S(buf), black, -hw - 0.042f, ARM_H, -0.285f, -hw + 0.032f, ARM_H + 0.004f, -0.26f + (rows - 1) * 0.042f + 0.025f, light, 0xFF1E2023);
        for (int i = 0; i < l.size(); i++) {
            int c = l.get(i);
            float[] p = buttonPos(cls, i);
            boolean in = now - a.press[c] < 200;
            boolean on = power && active(c, s);
            float top = p[1] - (in ? 0.005f : 0);
            int cap = on ? 0xFF3A6EA8 : 0xFF3A3D42;
            Box.cuboid(ps, S(buf), black, p[0] - 0.014f, ARM_H + 0.004f, p[2] - 0.016f, p[0] + 0.014f, top, p[2] + 0.016f, light, cap);
            // backlit legend, readable from the seat looking down
            ps.pushPose();
            ps.translate(p[0], top + 0.0006f, p[2]);
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            float k = 1 / 900f;
            ps.scale(k, -k, k);
            String txt = BTN[c];
            int col = !power ? 0xFF8A8E94 : (on ? 0xFFFFFFFF : 0xFFB8D4F0);
            font.drawInBatch(txt, -font.width(txt) / 2f, -4, col, false, ps.last().pose(), buf, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, power ? FULL : light);
            ps.popPose();
        }
    }

    private static final java.util.WeakHashMap<VehicleEntity, Long2ObjectOpenHashMap<Anim>> ANIM = new java.util.WeakHashMap<>();
    private static long lastFrame = System.currentTimeMillis();

    public static void render(VehicleEntity v, PoseStack ps, MultiBufferSource buffers, int light, double camDist) {
        if (camDist > 64) return;
        Long2ObjectOpenHashMap<Anim> anims = ANIM.computeIfAbsent(v, k -> new Long2ObjectOpenHashMap<>());
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, Math.max(0.001f, (now - lastFrame) / 1000f));
        lastFrame = now;
        TextureAtlasSprite frame = null, wood = null, shell = null, metal = null, black = null, white = null, glass = null;
        VertexConsumer vc = null;
        for (long k : v.structure.blockEntities().keySet()) {
            BlockState st = v.structure.get(k);
            ComponentType ct = VehicleBuilder.typeOf(st);
            if (ct == null || ct.seatClass() == 0) continue;
            int cls = ct.seatClass();
            if (vc == null) {
                vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
                frame = CockpitParts.sprite("seat_frame"); wood = CockpitParts.sprite("cabin_wood"); shell = CockpitParts.sprite("cabin_shell");
                metal = CockpitParts.sprite(CockpitParts.METAL); black = CockpitParts.sprite(CockpitParts.BLACK); white = CockpitParts.sprite(CockpitParts.WHITE);
                glass = CockpitParts.sprite(CockpitParts.GLASS);
            }
            TextureAtlasSprite fabric = CockpitParts.sprite(ct.id + "_fabric");
            int s = v.cabin.get(k);
            Anim a = anims.computeIfAbsent(k, kk -> new Anim());
            float kf = Math.min(1, dt * 3.5f); // seat motors move slowly
            a.slide += (CabinSeat.slide(s) - a.slide) * kf;
            a.recline += ((float) CabinSeat.recline(s) / Math.max(1, CabinSeat.maxRecline(cls)) - a.recline) * kf;
            a.leg += (CabinSeat.maxLeg(cls) > 0 ? (float) CabinSeat.leg(s) / CabinSeat.maxLeg(cls) - a.leg : -a.leg) * kf;
            a.tray += ((CabinSeat.tray(s) ? 1 : 0) - a.tray) * Math.min(1, dt * 6);
            a.door += ((CabinSeat.door(s) ? 1 : 0) - a.door) * Math.min(1, dt * 2.5f);
            a.light += (CabinSeat.light(s) - a.light) * Math.min(1, dt * 8);
            boolean scr = CabinSeat.screen(s) && v.telemetry.busPowered;
            if (scr && !a.screenWas) a.screenOn = now;
            if (!scr && a.screenWas) a.screenOff = now;
            a.screenWas = scr;
            ps.pushPose();
            ps.translate(BlockPos.getX(k) + 0.5, BlockPos.getY(k), BlockPos.getZ(k) + 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(-CockpitRenderer.facingRot(VehicleBuilder.facing(st))));
            seat(ps, vc, buffers, cls, a, s, fabric, frame, wood, shell, metal, black, white, glass, light, v, now);
            ps.popPose();
        }
    }

    static void seat(PoseStack ps, VertexConsumer vc, MultiBufferSource buf, int cls, Anim a, int s, TextureAtlasSprite fabric, TextureAtlasSprite frame,
                     TextureAtlasSprite wood, TextureAtlasSprite shell, TextureAtlasSprite metal, TextureAtlasSprite black, TextureAtlasSprite white,
                     TextureAtlasSprite glass, int light, VehicleEntity v, long now) {
        float hw = halfWidth(cls);   // half width
        float maxRecl = switch (cls) { case 1 -> 18; case 2 -> 22; case 3 -> 32; default -> 82; };          // deg at full recline
        float backH = switch (cls) { case 1 -> 0.62f; case 2 -> 0.66f; case 3 -> 0.7f; case 4 -> 0.74f; default -> 0.78f; };
        float slideZ = (a.slide - 2) * 0.06f;
        int fab = 0xFFFFFFFF, frm = 0xFFB8BEC4;
        boolean power = v.telemetry.busPowered;
        int lvl = power ? CabinSeat.light(s) : 0;
        float lk = Math.min(1, a.light) * (power ? 1 : 0);
        // the reading light / ambient light really lights the seat (raised block light), like a lamp in the world
        light = CockpitParts.boost(light, lvl == 1 ? 14 * lk : (lvl == 2 ? 10 * lk : 0));
        if (cls == 5) {
            suite(ps, vc, buf, a, s, wood, shell, metal, black, white, glass, light, v, now, lvl, lk);
        }
        ps.pushPose();
        ps.translate(0, 0, slideZ);
        // frame: legs and side rails (business / first: shell base)
        if (cls <= 3) {
            for (float x : new float[]{-hw + 0.06f, hw - 0.06f}) {
                Box.cuboid(ps, S(buf), frame, x - 0.03f, 0, -0.3f, x + 0.03f, 0.27f, -0.24f, light, frm);
                Box.cuboid(ps, S(buf), frame, x - 0.03f, 0, 0.18f, x + 0.03f, 0.27f, 0.24f, light, frm);
                Box.cuboid(ps, S(buf), frame, x - 0.03f, 0.22f, -0.3f, x + 0.03f, 0.27f, 0.24f, light, frm);
            }
        } else {
            Box.cuboid(ps, S(buf), shell, -hw, 0, -0.36f, hw, 0.26f, 0.38f, light, cls == 5 ? 0xFFE8E2D4 : 0xFF5A5048);
        }
        // seat pan (cushion)
        Box.cuboid(ps, S(buf), fabric, -hw + 0.03f, 0.27f, -0.36f, hw - 0.03f, 0.39f, 0.3f, light, fab);
        // seat belt: two straps and a metal buckle
        Box.cuboid(ps, S(buf), black, -0.2f, 0.391f, -0.02f, -0.02f, 0.395f, 0.03f, light, 0xFF3A3E44);
        Box.cuboid(ps, S(buf), black, 0.02f, 0.391f, -0.02f, 0.2f, 0.395f, 0.03f, light, 0xFF3A3E44);
        Box.cuboid(ps, S(buf), metal, -0.035f, 0.39f, -0.025f, 0.035f, 0.4f, 0.035f, light, 0xFFD8DCE0);
        // backrest, hinged at the back of the cushion
        ps.pushPose();
        ps.translate(0, 0.39f, 0.3f);
        ps.mulPose(Axis.XP.rotationDegrees(8 + maxRecl * a.recline)); // positive = top of the backrest goes back (+z)
        Box.cuboid(ps, S(buf), fabric, -hw + 0.03f, 0, -0.02f, hw - 0.03f, backH, 0.09f, light, fab);
        Box.cuboid(ps, S(buf), frame, -hw + 0.02f, 0.05f, 0.09f, hw - 0.02f, backH - 0.02f, 0.11f, light, cls >= 4 ? 0xFF4A423A : 0xFF8E949A); // back shell
        // headrest (premium +: adjustable wings)
        Box.cuboid(ps, S(buf), white, -hw + 0.08f, backH - 0.12f, -0.035f, hw - 0.08f, backH + 0.06f, 0.0f, light, 0xFFEDEBE4);
        if (cls >= 3) {
            Box.cuboid(ps, S(buf), fabric, -hw + 0.04f, backH - 0.12f, -0.07f, -hw + 0.1f, backH + 0.05f, 0.02f, light, fab);
            Box.cuboid(ps, S(buf), fabric, hw - 0.1f, backH - 0.12f, -0.07f, hw - 0.04f, backH + 0.05f, 0.02f, light, fab);
        }
        // reading light pod on top
        if (cls >= 2) {
            Box.cuboid(ps, S(buf), black, -0.04f, backH + 0.06f, -0.04f, 0.04f, backH + 0.09f, 0.02f, light, 0xFF2A2C30);
            // small frosted lens under the pod: the only visible "glow"
            CockpitParts.plate(ps, S(buf), white, -0.025f, -0.032f, 0.025f, 0.012f, backH + 0.0595f, lvl == 1 ? FULL : light, lvl == 1 ? CockpitParts.mix(0xFF000000, 0xFFFFF2DC, lk) : 0xFF6A6C70);
        }
        ps.popPose();
        // arm rests (business: wide side console on the right)
        float armH = 0.55f;
        Box.cuboid(ps, S(buf), cls >= 4 ? wood : frame, -hw - 0.04f, 0.26f, -0.3f, -hw + 0.03f, armH, 0.28f, light, cls >= 4 ? 0xFFFFFFFF : 0xFF6A7076);
        if (cls == 4) {
            Box.cuboid(ps, S(buf), shell, hw - 0.03f, 0, -0.36f, hw + 0.12f, armH + 0.04f, 0.38f, light, 0xFF5A5048);   // side console
            Box.cuboid(ps, S(buf), wood, hw - 0.03f, armH + 0.04f, -0.36f, hw + 0.12f, armH + 0.06f, 0.38f, light, 0xFFFFFFFF);
            // seat controls on the console (lit buttons)
            for (int i = 0; i < 3; i++) Box.cuboid(ps, S(buf), white, hw + 0.02f, armH + 0.061f, 0.05f + i * 0.06f, hw + 0.07f, armH + 0.066f, 0.09f + i * 0.06f, FULL,
                    v.telemetry.busPowered ? 0xFF7A9AC0 : 0xFF404448);
        } else {
            Box.cuboid(ps, S(buf), cls == 5 ? wood : frame, hw - 0.03f, 0.26f, -0.3f, hw + 0.04f, armH, 0.28f, light, cls == 5 ? 0xFFFFFFFF : 0xFF6A7076);
        }
        // seat control buttons on the arm rest: right-click them from the seat
        seatButtons(ps, vc, buf, cls, a, s, black, white, light, v, now);
        vc = buf.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        // leg rest / foot rest, hinged at the front of the cushion
        if (CabinSeat.maxLeg(cls) > 0) {
            ps.pushPose();
            ps.translate(0, 0.33f, -0.36f);
            ps.mulPose(Axis.XP.rotationDegrees(-(80 - 80 * a.leg)));   // hangs down -> raised horizontal
            float len = cls == 2 ? 0.16f : (cls == 3 ? 0.3f : 0.38f);
            Box.cuboid(ps, S(buf), fabric, -hw + 0.06f, -0.06f, -len, hw - 0.06f, 0.04f, 0, light, fab);
            if (cls == 2) Box.cuboid(ps, S(buf), frame, -hw + 0.06f, -0.08f, -len - 0.02f, hw - 0.06f, -0.04f, -len, light, frm); // foot bar
            ps.popPose();
        }
        // tray / work table: swings out of the arm rest
        ps.pushPose();
        float tw = cls >= 4 ? 0.3f : 0.24f;
        ps.translate(hw + 0.01f, armH - 0.02f, -0.3f);
        ps.mulPose(Axis.ZP.rotationDegrees(90 - 90 * a.tray)); // stowed vertical in the arm rest, open = horizontal
        Box.cuboid(ps, S(buf), cls >= 4 ? wood : shell, -2 * tw, 0, -0.24f, 0.0f, 0.02f, 0.0f, light, cls >= 4 ? 0xFFFFFFFF : 0xFFD8D4CC);
        ps.popPose();
        ps.popPose();
        // personal screen (premium: arm mounted, business: on the shell ahead, first: on the suite wall)
        if (cls >= 3 && cls <= 4) {
            float sw = cls == 3 ? 0.13f : 0.18f, sh = cls == 3 ? 0.085f : 0.11f;
            float sx = cls == 3 ? -hw + 0.08f : 0, sy = cls == 3 ? 0.78f : 0.82f, sz = cls == 3 ? -0.5f : -0.55f;
            if (cls == 3) Box.cuboid(ps, S(buf), metal, -hw - 0.02f, armH, -0.32f, -hw + 0.0f, sy - 0.05f, -0.3f, light, 0xFF9AA0A6); // arm
            else Box.cuboid(ps, S(buf), shell, -0.28f, 0, sz - 0.08f, 0.28f, sy + sh + 0.04f, sz - 0.02f, light, 0xFF5A5048);           // monitor stand / shell (behind the screen)
            screen(ps, buf, black, glass, sx, sy, sz, sw, sh, a, s, light, v, now);
        }
        // ambient mode: a thin LED line under the arm rests (lights the floor, no glowing blobs)
        if (lvl == 2 && cls <= 4) {
            int led = CockpitParts.mix(0xFF000000, cls >= 4 ? 0xFFFFC890 : 0xFF9AB8FF, lk);
            ps.pushPose();
            ps.translate(0, 0, slideZ);
            Box.cuboid(ps, S(buf), white, -hw - 0.04f, 0.25f, -0.28f, -hw - 0.035f, 0.258f, 0.26f, FULL, led);
            Box.cuboid(ps, S(buf), white, hw + 0.035f, 0.25f, -0.28f, hw + 0.04f, 0.258f, 0.26f, FULL, led);
            ps.popPose();
        }
    }

    /** first class suite: walls, wood trim, sliding door, ottoman / bed extension, big screen, table and private lighting */
    static void suite(PoseStack ps, VertexConsumer vc, MultiBufferSource buf, Anim a, int s, TextureAtlasSprite wood, TextureAtlasSprite shell, TextureAtlasSprite metal,
                      TextureAtlasSprite black, TextureAtlasSprite white, TextureAtlasSprite glass, int light, VehicleEntity v, long now, int lvl, float lk) {
        // footprint: x -0.5 .. 1.5 (seat block + the one to its right), z -1.5 .. 0.5 (seat block + the one ahead)
        float wh = 1.2f;
        int sc = 0xFFF0ECE2, wc = 0xFFFFFFFF;
        Box.cuboid(ps, S(buf), shell, -0.5f, 0, 0.44f, 1.5f, wh, 0.5f, light, sc);          // back wall
        Box.cuboid(ps, S(buf), shell, -0.5f, 0, -1.5f, -0.44f, wh, 0.44f, light, sc);       // window-side wall
        Box.cuboid(ps, S(buf), shell, -0.5f, 0, -1.5f, 1.5f, 1.55f, -1.44f, light, sc);     // front wall with the screen, at eye height
        Box.cuboid(ps, S(buf), shell, 1.44f, 0, 0.0f, 1.5f, wh, 0.44f, light, sc);           // aisle wall, fixed part
        for (float[] tr : new float[][]{{-0.5f, 0.44f, 1.5f, 0.5f}, {-0.5f, -1.5f, -0.44f, 0.44f}, {-0.5f, -1.5f, 1.5f, -1.44f}}) {
            float top = tr[1] == -1.5f && tr[3] == -1.44f ? 1.55f : wh;
            Box.cuboid(ps, S(buf), wood, tr[0], top, tr[1], tr[2], top + 0.04f, tr[3], light, wc);   // wood cap rails
        }
        // sliding door on the aisle side: slides forward along the wall into a pocket
        float dz = -a.door * 1.0f;
        Box.cuboid(ps, S(buf), wood, 1.45f, 0.02f, -1.0f + dz, 1.49f, wh - 0.05f, 0.0f + dz, light, wc);
        Box.cuboid(ps, S(buf), metal, 1.43f, 0.55f, -0.12f + dz, 1.45f, 0.75f, -0.08f + dz, light, 0xFFD8C890);   // handle
        // ottoman in front (the bed extends onto it when flat) and a side table with a lamp
        Box.cuboid(ps, S(buf), shell, -0.4f, 0, -1.4f, 0.4f, 0.33f, -0.85f, light, 0xFFD8CCB4);
        Box.cuboid(ps, S(buf), CockpitParts.sprite("seat_first_fabric"), -0.38f, 0.33f, -1.38f, 0.38f, 0.39f, -0.87f, light, 0xFFFFFFFF);
        Box.cuboid(ps, S(buf), wood, 0.7f, 0, -0.6f, 1.3f, 0.62f, -0.1f, light, wc);       // side cabinet / table
        // table lamp: metal stem and a fabric shade that glows from inside when the private light is on
        Box.cuboid(ps, S(buf), metal, 0.985f, 0.62f, -0.365f, 1.015f, 0.72f, -0.335f, light, 0xFFC8B890);
        Box.cuboid(ps, S(buf), white, 0.94f, 0.72f, -0.4f, 1.06f, 0.8f, -0.3f, lvl > 0 ? FULL : light, lvl > 0 ? CockpitParts.mix(0xFF8A8070, 0xFFFFE6C0, lk) : 0xFFD8D0C0);
        // concealed LED strip along the inside of the cap rails (thin emissive line, its colour per mode)
        if (lvl > 0) {
            int led = CockpitParts.mix(0xFF000000, lvl == 2 ? 0xFFFFB070 : 0xFFFFE8C8, lk);
            Box.cuboid(ps, S(buf), white, -0.43f, wh - 0.03f, 0.425f, 1.43f, wh - 0.018f, 0.44f, FULL, led);
            Box.cuboid(ps, S(buf), white, -0.44f, wh - 0.03f, -1.43f, -0.425f, wh - 0.018f, 0.43f, FULL, led);
        }
        // the big screen on the front wall
        screen(ps, buf, black, glass, 0.5f, 1.12f, -1.435f, 0.36f, 0.21f, a, s, light, v, now);
    }

    /**
     * Entertainment screen facing the passenger (+z): bezel, boot animation, then the chosen page:
     * flight information, moving map or entertainment. Dark without power or when switched off.
     */
    static void screen(PoseStack ps, MultiBufferSource buf, TextureAtlasSprite black, TextureAtlasSprite glass, float cx, float cy, float cz, float hw, float hh,
                       Anim a, int s, int light, VehicleEntity v, long now) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        Box.cuboid(ps, S(buf), black, cx - hw - 0.015f, cy - hh - 0.015f, cz - 0.02f, cx + hw + 0.015f, cy + hh + 0.015f, cz, light, 0xFF1A1C1F); // bezel
        boolean on = a.screenWas;
        float env;
        if (on) env = Mth.clamp((now - a.screenOn) / 600f, 0, 1);
        else { float d = (now - a.screenOff) / 400f; env = d >= 1 ? 0 : (1 - d) * (1 - d); }
        if (env <= 0.01f) return;
        float bright = env * Config.CLIENT.passengerScreenBrightness.get().floatValue();
        ps.pushPose();
        ps.translate(cx, cy, cz + 0.001f);
        ps.scale(hh, hh, 1);
        float ar = hw / hh; // half width in screen units
        InstrumentRenderer.BRIGHT = bright;
        try {
            InstrumentRenderer.rect(ps, buf, -ar, -1, ar, 1, 0.0005f, 0xFF05080E);
            float tOn = (now - a.screenOn) / 1000f;
            VehicleTelemetry t = v.telemetry;
            if (on && tOn < 2.2f) {
                // boot: logo, then a loading bar
                InstrumentRenderer.rect(ps, buf, -ar, -1, ar, 1, 0.0008f, 0xFF0A1830);
                InstrumentRenderer.poly(ps, buf, new float[][]{{-0.25f, 0.35f}, {0, -0.05f}, {0.25f, 0.35f}, {0.12f, 0.35f}, {0, 0.15f}, {-0.12f, 0.35f}}, 6, 0.001f, 0xFF38D3FF);
                InstrumentRenderer.text(ps, buf, "VW SKY", 0, -0.3f, 0.22f, 0xFFFFFFFF, true);
                float k = Mth.clamp((tOn - 0.6f) / 1.4f, 0, 1);
                InstrumentRenderer.rect(ps, buf, -ar * 0.6f, -0.72f, ar * 0.6f, -0.62f, 0.001f, 0xFF1A2A40);
                InstrumentRenderer.rect(ps, buf, -ar * 0.6f, -0.72f, -ar * 0.6f + 1.2f * ar * k, -0.62f, 0.0012f, 0xFF38D3FF);
            } else switch (CabinSeat.page(s)) {
                case 1 -> map(ps, buf, ar, t, v);
                case 2 -> entertainment(ps, buf, ar, now);
                default -> flightInfo(ps, buf, ar, t, now);
            }
        } finally {
            InstrumentRenderer.BRIGHT = 1;
            ps.popPose();
        }
    }

    static void flightInfo(PoseStack ps, MultiBufferSource buf, float ar, VehicleTelemetry t, long now) {
        final int W = 0xFFFFFFFF, C = 0xFF7FD6FF, G = 0xFF9AA8B8;
        InstrumentRenderer.rect(ps, buf, -ar, 0.72f, ar, 1, 0.0008f, 0xFF10305A);
        InstrumentRenderer.text(ps, buf, "FLIGHT INFORMATION", 0, 0.86f, 0.15f, W, true);
        float x0 = -ar + 0.12f, x1 = 0.15f;
        InstrumentRenderer.text(ps, buf, "ALTITUDE", x0, 0.5f, 0.12f, G, false);
        InstrumentRenderer.text(ps, buf, String.format("%.0f m", t.altitude), x0, 0.32f, 0.2f, W, false);
        InstrumentRenderer.text(ps, buf, "GROUND SPEED", x1, 0.5f, 0.12f, G, false);
        InstrumentRenderer.text(ps, buf, String.format("%.0f km/h", t.groundSpeed * 3.6f), x1, 0.32f, 0.2f, W, false);
        InstrumentRenderer.text(ps, buf, "HEADING", x0, 0.05f, 0.12f, G, false);
        InstrumentRenderer.text(ps, buf, String.format("%03.0f", t.heading), x0, -0.13f, 0.2f, W, false);
        InstrumentRenderer.text(ps, buf, "OUTSIDE", x1, 0.05f, 0.12f, G, false);
        InstrumentRenderer.text(ps, buf, String.format("%.0f C", 15 - t.altitude * 0.0065f), x1, -0.13f, 0.2f, W, false);
        if (t.navActive) {
            InstrumentRenderer.text(ps, buf, String.format("DESTINATION  %.1f km", t.navDistance / 1000f), 0, -0.45f, 0.13f, C, true);
            float eta = t.groundSpeed > 1 ? t.navDistance / t.groundSpeed / 60f : 0;
            InstrumentRenderer.text(ps, buf, t.groundSpeed > 1 ? String.format("TIME TO GO  %.0f min", eta) : "TIME TO GO  --", 0, -0.62f, 0.12f, G, true);
        } else InstrumentRenderer.text(ps, buf, "DESTINATION  --", 0, -0.5f, 0.13f, C, true);
        InstrumentRenderer.text(ps, buf, t.onGround ? "ON GROUND" : "IN FLIGHT", 0, -0.86f, 0.11f, t.onGround ? G : 0xFF8CFF9A, true);
    }

    static void map(PoseStack ps, MultiBufferSource buf, float ar, VehicleTelemetry t, VehicleEntity v) {
        InstrumentRenderer.rect(ps, buf, -ar, -1, ar, 1, 0.0007f, 0xFF0A2A52);
        // grid of "land" patches drifting with the aircraft position (north up)
        double scale = 1 / 400.0, ox = v.getX() * scale, oz = v.getZ() * scale;
        for (int i = -3; i <= 3; i++)
            for (int j = -2; j <= 2; j++) {
                double cx = Math.floor(ox) + i, cz = Math.floor(oz) + j;
                long h = (long) (cx * 73856093L) ^ (long) (cz * 19349663L);
                if ((h & 3) == 0) continue;
                float x = (float) ((cx - ox) * 0.6), y = (float) (-(cz - oz) * 0.6);
                if (Math.abs(x) > ar - 0.1f || Math.abs(y) > 0.9f) continue;
                InstrumentRenderer.rect(ps, buf, x - 0.22f, y - 0.2f, x + 0.22f, y + 0.2f, 0.0009f, (h & 4) == 0 ? 0xFF2E6A3A : 0xFF4E7A3A);
            }
        if (t.navActive) {
            double a = Math.toRadians(t.navBearing);
            float r = Math.min(0.8f, t.navDistance / 4000f * 0.8f + 0.1f);
            float x = (float) Math.sin(a) * r, y = (float) Math.cos(a) * r;
            InstrumentRenderer.line(ps, buf, 0, 0, x, y, 0.025f, 0.0012f, 0xFFFFFFFF);
            InstrumentRenderer.rect(ps, buf, x - 0.05f, y - 0.05f, x + 0.05f, y + 0.05f, 0.0013f, 0xFFFF55FF);
        }
        // aircraft symbol rotated to the heading
        double hdg = Math.toRadians(t.heading);
        float s = (float) Math.sin(hdg), c = (float) Math.cos(hdg);
        float[][] plane = {{0, 0.12f}, {0.09f, -0.08f}, {0, -0.03f}, {-0.09f, -0.08f}};
        for (float[] p : plane) { float x = p[0] * c + p[1] * s, y = -p[0] * s + p[1] * c; p[0] = x; p[1] = y; }
        InstrumentRenderer.poly(ps, buf, plane, 4, 0.0014f, 0xFFFFD040);
        InstrumentRenderer.text(ps, buf, String.format("%.0f m   %.0f km/h", t.altitude, t.groundSpeed * 3.6f), 0, -0.86f, 0.11f, 0xFFFFFFFF, true);
        InstrumentRenderer.text(ps, buf, "N", ar - 0.12f, 0.82f, 0.12f, 0xFFFFFFFF, true);
    }

    static void entertainment(PoseStack ps, MultiBufferSource buf, float ar, long now) {
        // a calm little animation: sunset over the sea, gulls, rolling waves
        float tt = (now % 60000) / 1000f;
        InstrumentRenderer.rect(ps, buf, -ar, 0, ar, 1, 0.0007f, 0xFF3A4A9A);
        InstrumentRenderer.rect(ps, buf, -ar, 0, ar, 0.35f, 0.0008f, 0xFF9A5A8A);
        float sy = 0.25f + 0.15f * (float) Math.sin(tt * 0.1);
        InstrumentRenderer.rect(ps, buf, -0.18f, sy - 0.18f, 0.18f, sy + 0.18f, 0.001f, 0xFFFFB050);
        InstrumentRenderer.rect(ps, buf, -ar, -1, ar, 0, 0.0009f, 0xFF10306A);
        for (int i = 0; i < 6; i++) {
            float y = -0.1f - i * 0.15f, ph = tt * (0.6f + i * 0.2f) + i;
            for (float x = -ar; x < ar; x += 0.18f) {
                float yy = y + 0.03f * (float) Math.sin(x * 6 + ph);
                InstrumentRenderer.line(ps, buf, x, yy, x + 0.12f, y + 0.03f * (float) Math.sin((x + 0.12f) * 6 + ph), 0.02f, 0.001f, 0xFF4A80C0);
            }
        }
        for (int g = 0; g < 3; g++) {
            float gx = ((tt * 0.15f + g * 0.7f) % 2f) * ar - ar * 0.8f, gy = 0.55f + 0.08f * g + 0.04f * (float) Math.sin(tt * 2 + g);
            float flap = 0.04f * (float) Math.sin(tt * 8 + g);
            InstrumentRenderer.line(ps, buf, gx - 0.07f, gy + flap, gx, gy, 0.015f, 0.0012f, 0xFF202020);
            InstrumentRenderer.line(ps, buf, gx, gy, gx + 0.07f, gy + flap, 0.015f, 0.0012f, 0xFF202020);
        }
        InstrumentRenderer.text(ps, buf, "NOW SHOWING: OCEAN SUNSET", 0, -0.86f, 0.1f, 0xFFFFFFFF, true);
    }
}
