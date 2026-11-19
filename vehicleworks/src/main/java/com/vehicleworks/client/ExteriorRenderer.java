package com.vehicleworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.vehicle.VehicleBuilder;
import com.vehicleworks.vehicle.VehicleControls;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import com.vehicleworks.vehicle.WingGeometry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * Exterior parts that are bigger than their block: the one-block wing assembly (tapered, swept, with moving
 * flaps, ailerons, slats, spoilers, a sharklet and built-in lights), nose / tail fairing cones, the airliner
 * engine nacelle with its spinning fan, and landing gear bay doors.
 */
public final class ExteriorRenderer {
    private ExteriorRenderer() {}

    static final int SKIN = 0xFFEDEFF1, BELLY = 0xFFD2D6DA, EDGE = 0xFFB4BAC0, FLAP = 0xFFDCE0E4, SPOILER = 0xFFC6CBD0, LIVERY = 0xFF2B63B8;

    public static void render(VehicleEntity v, PoseStack ps, MultiBufferSource buf, int light) {
        VehicleTelemetry t = v.telemetry;
        double time = System.currentTimeMillis() / 1000.0;
        for (long k : v.structure.blockEntities().keySet()) {
            BlockState st = v.structure.get(k);
            ComponentType ct = VehicleBuilder.typeOf(st);
            if (ct == ComponentType.WING_ASSEMBLY) wing(v, ps, buf, t, k, st, light, time);
            else if (ct == ComponentType.FAIRING_CONE) cone(v, ps, buf, k, st, light);
            else if (ct == ComponentType.AIRLINER_ENGINE) engine(v, ps, buf, t, k, st, light);
        }
    }

    /** lights only (far level of detail) */
    public static void lights(VehicleEntity v, PoseStack ps, MultiBufferSource buf) {
        double time = System.currentTimeMillis() / 1000.0;
        for (long k : v.structure.blockEntities().keySet()) {
            BlockState st = v.structure.get(k);
            if (VehicleBuilder.typeOf(st) != ComponentType.WING_ASSEMBLY) continue;
            WingGeometry g = new WingGeometry(VehicleBuilder.config(v.structure, k));
            wingLights(v, ps, buf, v.telemetry, g, k, VehicleBuilder.facing(st), time);
        }
    }

    // ================================================================== geometry helpers

    /** NORTH-frame point of a block (centre-relative) to structure-local render coordinates */
    private static float[] P(long k, Direction f, double[] n) {
        double[] l = WingGeometry.toLocal(f, n[0], n[2]);
        return new float[]{(float) (BlockPos.getX(k) + 0.5 + l[0]), (float) (BlockPos.getY(k) + 0.5 + n[1]), (float) (BlockPos.getZ(k) + 0.5 + l[1])};
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, float[] a, float[] b, float[] c, float[] d, int color, int light) {
        Vector3f e1 = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]), e2 = new Vector3f(d[0] - a[0], d[1] - a[1], d[2] - a[2]);
        Vector3f n = e1.cross(e2);
        if (n.lengthSquared() < 1e-12f) { e1.set(c[0] - b[0], c[1] - b[1], c[2] - b[2]); e2.set(a[0] - b[0], a[1] - b[1], a[2] - b[2]); n = e1.cross(e2); }
        if (n.lengthSquared() < 1e-12f) n.set(0, 1, 0); else n.normalize();
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        CockpitParts.vtx(vc, pose, a[0], a[1], a[2], u0, v0, color, light, n.x, n.y, n.z);
        CockpitParts.vtx(vc, pose, b[0], b[1], b[2], u1, v0, color, light, n.x, n.y, n.z);
        CockpitParts.vtx(vc, pose, c[0], c[1], c[2], u1, v1, color, light, n.x, n.y, n.z);
        CockpitParts.vtx(vc, pose, d[0], d[1], d[2], u0, v1, color, light, n.x, n.y, n.z);
    }

    private static VertexConsumer vc(MultiBufferSource b) {
        return FixtureRenderer.S(b);
    }

    /** a cross-section point (chord z, normal offset n) of a moving part rotated by delta (down positive) about a hinge */
    private static double[] rot(double hz, double hn, double dz, double dn, double delta) {
        double c = Math.cos(delta), s = Math.sin(delta);
        return new double[]{hz + dz * c + dn * s, hn + dn * c - dz * s};
    }

    // ================================================================== wing assembly

    static void wing(VehicleEntity v, PoseStack ps, MultiBufferSource buf, VehicleTelemetry t, long k, BlockState st, int light, double time) {
        WingGeometry g = new WingGeometry(VehicleBuilder.config(v.structure, k));
        Direction f = VehicleBuilder.facing(st);
        TextureAtlasSprite sp = CockpitParts.sprite(CockpitParts.WHITE);
        PoseStack.Pose pose = ps.last();
        float flaps = t.flaps;
        // surface deflections
        double flapDelta = Math.toRadians(35) * flaps, fowler = 0.14 * flaps;
        double ail = Math.toRadians(20) * Math.max(-1, Math.min(1, t.stickRoll)) * -g.side;
        double elev = Math.toRadians(25) * Math.max(-1, Math.min(1, t.stickPitch));
        double rud = Math.toRadians(25) * Math.max(-1, Math.min(1, t.stickYaw));
        double spoil = Math.toRadians(45) * Math.max(t.speedBrake, t.onGround && t.speedBrake > 0.05f ? 1 : 0);
        double slat = g.slats ? Math.min(1, flaps * 2.5) : 0;
        // span stations at every boundary of a moving part
        double[] fr = g.kind == 0 ? new double[]{-1, 0, WingGeometry.SLAT_START, WingGeometry.SPOIL_START, WingGeometry.SPOIL_END, WingGeometry.FLAP_END,
                WingGeometry.AIL_START, WingGeometry.AIL_END, 1} : (g.kind == 1 ? new double[]{-1, 0, 0.97, 1} : new double[]{-1, 0, 0.92, 1});
        for (int i = 0; i + 1 < fr.length; i++) {
            double s0 = fr[i] < 0 ? -1 : fr[i] * g.span, s1 = fr[i + 1] * g.span;
            double fm = (Math.max(0, s0) + s1) / 2 / g.span;
            var role = s0 < 0 ? null : g.controlAt(fm);
            double delta = 0, aft = 0;
            if (role != null) switch (role) {
                case FLAP -> { delta = flapDelta; aft = fowler; }
                case AILERON -> delta = ail;
                case ELEVATOR -> delta = -elev;
                case RUDDER -> delta = rud;
                default -> {}
            }
            boolean hasSlat = g.kind == 0 && g.slats && fm > WingGeometry.SLAT_START && fm < 0.97 && s0 >= 0;
            boolean hasSpoiler = g.kind == 0 && g.spoilers && fm > WingGeometry.SPOIL_START && fm < WingGeometry.SPOIL_END && s0 >= 0;
            int skin = g.kind == 2 ? SKIN : SKIN;
            section(vc(buf), pose, sp, g, k, f, s0, s1, delta, aft, hasSlat ? slat : -1, hasSpoiler ? spoil : -1, skin, light);
        }
        tipCap(vc(buf), pose, sp, g, k, f, light);
        if (g.kind == 0 && g.winglet > 0) winglet(vc(buf), pose, sp, g, k, f, light);
        if (g.kind == 2) {
            // livery band on the fin
            double s0 = g.span * 0.35, s1 = g.span * 0.75;
            for (int side = -1; side <= 1; side += 2) {
                double n0 = side * (g.thickness(s0) * 0.5 + 0.01), n1 = side * (g.thickness(s1) * 0.5 + 0.01);
                quad(vc(buf), pose, sp, P(k, f, g.point(s0, g.leZ(s0) + 0.1 * g.chord(s0), n0)), P(k, f, g.point(s1, g.leZ(s1) + 0.1 * g.chord(s1), n1)),
                        P(k, f, g.point(s1, g.leZ(s1) + 0.68 * g.chord(s1), n1)), P(k, f, g.point(s0, g.leZ(s0) + 0.68 * g.chord(s0), n0)), LIVERY, light);
            }
        }
        if (g.lights && t.busPowered) wingLights(v, ps, buf, t, g, k, f, time);
    }

    /** one span segment: fixed box, slat, spoiler and the trailing-edge part (flap / aileron / elevator / rudder) */
    private static void section(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, WingGeometry g, long k, Direction f,
                                double s0, double s1, double delta, double aft, double slat, double spoil, int skin, int light) {
        double[][][] prof = new double[2][][];
        double[][][] trail = new double[2][][];
        double[][][] slatP = new double[2][][];
        double[][][] spoilP = new double[2][][];
        for (int e = 0; e < 2; e++) {
            double s = e == 0 ? s0 : s1;
            double c = g.chord(s), zle = g.leZ(s), th = g.thickness(s);
            double zh = zle + (1 - g.ctlChord()) * c, zte = zle + c;
            double zfront = slat >= 0 ? zle + WingGeometry.SLAT_CHORD * c : zle;
            double zt1 = zle + 0.16 * c;
            prof[e] = new double[][]{{zfront, 0}, {zt1, th * 0.5}, {zh, th * 0.42}, {zh, -th * 0.28}, {zt1, -th * 0.4}};
            double hn = th * 0.07;
            double[] ht = rot(zh + aft * c, hn, 0, th * 0.35, delta), hb = rot(zh + aft * c, hn, 0, -th * 0.35, delta), te = rot(zh + aft * c, hn, zte - zh, 0, delta);
            trail[e] = new double[][]{ht, te, hb};
            if (slat >= 0) {
                double dz = -0.09 * c * slat, dn = -0.05 * c * slat;
                slatP[e] = new double[][]{{zle + dz, dn}, {zfront + 0.01 + dz, th * 0.46 + dn}, {zfront + dz, -th * 0.32 + dn}};
            }
            if (spoil >= 0) {
                double zf = zh - WingGeometry.SPOIL_CHORD * c, L = zh - zf, nf = th * 0.47 + 0.006;
                spoilP[e] = new double[][]{{zf, nf}, {zf + L * Math.cos(spoil), nf + L * Math.sin(spoil)}};
            }
        }
        // fixed box: nose, top skin, rear spar face, bottom skin
        int[] colors = {EDGE, skin, 0xFF9AA0A6, BELLY, EDGE};
        for (int i = 0; i < 5; i++) {
            int j = (i + 1) % 5;
            quad(vc, pose, sp, P(k, f, g.point(s0, prof[0][i][0], prof[0][i][1])), P(k, f, g.point(s1, prof[1][i][0], prof[1][i][1])),
                    P(k, f, g.point(s1, prof[1][j][0], prof[1][j][1])), P(k, f, g.point(s0, prof[0][j][0], prof[0][j][1])), colors[i], light);
        }
        // trailing part (moves)
        int[] tc = {FLAP, BELLY, 0xFF8E949A};
        for (int i = 0; i < 3; i++) {
            int j = (i + 1) % 3;
            quad(vc, pose, sp, P(k, f, g.point(s0, trail[0][i][0], trail[0][i][1])), P(k, f, g.point(s1, trail[1][i][0], trail[1][i][1])),
                    P(k, f, g.point(s1, trail[1][j][0], trail[1][j][1])), P(k, f, g.point(s0, trail[0][j][0], trail[0][j][1])), tc[i], light);
        }
        for (int e = 0; e < 2; e++) {
            double s = e == 0 ? s0 : s1;
            float[] a = P(k, f, g.point(s, trail[e][0][0], trail[e][0][1])), b = P(k, f, g.point(s, trail[e][1][0], trail[e][1][1])), c = P(k, f, g.point(s, trail[e][2][0], trail[e][2][1]));
            quad(vc, pose, sp, a, b, c, c, 0xFFB0B6BC, light);
        }
        if (slat >= 0) {
            for (int i = 0; i < 3; i++) {
                int j = (i + 1) % 3;
                quad(vc, pose, sp, P(k, f, g.point(s0, slatP[0][i][0], slatP[0][i][1])), P(k, f, g.point(s1, slatP[1][i][0], slatP[1][i][1])),
                        P(k, f, g.point(s1, slatP[1][j][0], slatP[1][j][1])), P(k, f, g.point(s0, slatP[0][j][0], slatP[0][j][1])), i == 0 ? skin : EDGE, light);
            }
        }
        if (spoil >= 0) {
            quad(vc, pose, sp, P(k, f, g.point(s0, spoilP[0][0][0], spoilP[0][0][1])), P(k, f, g.point(s1, spoilP[1][0][0], spoilP[1][0][1])),
                    P(k, f, g.point(s1, spoilP[1][1][0], spoilP[1][1][1])), P(k, f, g.point(s0, spoilP[0][1][0], spoilP[0][1][1])), SPOILER, light);
        }
    }

    private static void tipCap(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, WingGeometry g, long k, Direction f, int light) {
        double s = g.span, c = g.chord(s), zle = g.leZ(s), th = g.thickness(s);
        float[] n = P(k, f, g.point(s, zle, 0)), t1 = P(k, f, g.point(s, zle + 0.16 * c, th * 0.5)), b1 = P(k, f, g.point(s, zle + 0.16 * c, -th * 0.4));
        double zh = zle + (1 - g.ctlChord()) * c;
        float[] te = P(k, f, g.point(s, zle + c, 0)), t2 = P(k, f, g.point(s, zh, th * 0.42)), b2 = P(k, f, g.point(s, zh, -th * 0.28));
        quad(vc, pose, sp, n, t1, b1, b1, EDGE, light);
        quad(vc, pose, sp, t1, t2, b2, b1, SKIN, light);
        quad(vc, pose, sp, t2, te, b2, b2, SKIN, light);
    }

    /** sharklet (straight, canted) or blended winglet at the tip */
    private static void winglet(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, WingGeometry g, long k, Direction f, int light) {
        double s = g.span, c = g.chord(s), zle = g.leZ(s), y0 = g.y(s), x0 = g.side * (0.5 + s);
        double h = Math.max(0.8, 1.15 * g.tip), cant = Math.toRadians(g.winglet == 2 ? 20 : 8), sw = Math.tan(Math.toRadians(38));
        double za = zle + 0.08 * c, zb = zle + 0.95 * c, zat = za + h * sw, zbt = zat + 0.32 * c;
        double ox = g.side * h * Math.sin(cant), oy = h * Math.cos(cant), w = 0.05;
        double[][] base = {{x0, y0, za}, {x0, y0, zb}}, top = {{x0 + ox, y0 + oy, zat}, {x0 + ox, y0 + oy, zbt}};
        if (g.winglet == 2) {
            // blended: the first third curves outward before turning up
            double mx = x0 + g.side * 0.45 * h, my = y0 + 0.3 * h;
            double[][] mid = {{mx, my, za + 0.4 * h * sw}, {mx, my, zb - 0.1 * c}};
            slab(vc, pose, sp, k, f, base, mid, w, light);
            slab(vc, pose, sp, k, f, mid, top, w, light);
        } else slab(vc, pose, sp, k, f, base, top, w, light);
    }

    /** a thin surface between two lines (lower LE/TE and upper LE/TE), w thick */
    private static void slab(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, long k, Direction f, double[][] lo, double[][] hi, double w, int light) {
        for (int side = -1; side <= 1; side += 2) {
            double d = side * w / 2;
            quad(vc, pose, sp, P(k, f, new double[]{lo[0][0] + d, lo[0][1], lo[0][2]}), P(k, f, new double[]{lo[1][0] + d, lo[1][1], lo[1][2]}),
                    P(k, f, new double[]{hi[1][0] + d, hi[1][1], hi[1][2]}), P(k, f, new double[]{hi[0][0] + d, hi[0][1], hi[0][2]}), LIVERY, light);
        }
        quad(vc, pose, sp, P(k, f, new double[]{lo[0][0] - w / 2, lo[0][1], lo[0][2]}), P(k, f, new double[]{lo[0][0] + w / 2, lo[0][1], lo[0][2]}),
                P(k, f, new double[]{hi[0][0] + w / 2, hi[0][1], hi[0][2]}), P(k, f, new double[]{hi[0][0] - w / 2, hi[0][1], hi[0][2]}), EDGE, light);
    }

    /** lights built into the wing: navigation and strobe at the tip, landing and runway turn-off lights at the root */
    private static void wingLights(VehicleEntity v, PoseStack ps, MultiBufferSource buf, VehicleTelemetry t, WingGeometry g, long k, Direction f, double time) {
        if (g.kind != 0 || !g.lights || !t.busPowered) return;
        LightFx.begin(ps);
        double s = g.span, c = g.chord(s), zle = g.leZ(s);
        float[] nav = P(k, f, g.point(s + 0.04, zle + 0.22 * c, 0)), strobe = P(k, f, g.point(s + 0.04, zle + 0.55 * c, 0.02));
        CockpitRenderer.lamp(ps, buf, t, g.side < 0 ? 1 : 2, nav[0], nav[1], nav[2], 0, 0, time);
        CockpitRenderer.lamp(ps, buf, t, 5, strobe[0], strobe[1], strobe[2], 0, 0, time);
        // aft-facing white navigation light on the trailing edge of the tip
        if (t.sw(VehicleControls.SW_NAV)) {
            float[] aftL = P(k, f, g.point(s, zle + c, 0));
            LightFx.sprite(ps, buf, LightFx.GLOW, aftL[0], aftL[1], aftL[2], 0.12f, 0xFFF4E8, 0.9f);
        }
        double[] fw = WingGeometry.toLocal(f, 0, -1);
        double sl = Math.min(1.4, s * 0.12), so = Math.min(0.7, s * 0.06);
        float[] land = P(k, f, g.point(sl, g.leZ(sl) + 0.04, -g.thickness(sl) * 0.3)), turn = P(k, f, g.point(so, g.leZ(so) + 0.04, -g.thickness(so) * 0.3));
        CockpitRenderer.lamp(ps, buf, t, 6, land[0], land[1], land[2], (float) fw[0], (float) fw[1], time);
        double[] tw = WingGeometry.toLocal(f, g.side * 0.5, -0.87);
        CockpitRenderer.lamp(ps, buf, t, 8, turn[0], turn[1], turn[2], (float) tw[0], (float) tw[1], time);
    }

    // ================================================================== fairing cones

    static void cone(VehicleEntity v, PoseStack ps, MultiBufferSource buf, long k, BlockState st, int light) {
        ComponentType ct = ComponentType.FAIRING_CONE;
        CompoundTag c = VehicleBuilder.config(v.structure, k);
        Direction f = VehicleBuilder.facing(st);
        double w = ct.property("width").get(c), h = ct.property("height").get(c), len = ct.property("length").get(c);
        double tipOff = ct.property("tip_offset").get(c), cy = ct.property("center_y").get(c);
        boolean tail = ct.property("style").getInt(c) == 1;
        int color = switch (ct.property("color").getInt(c)) { case 1 -> 0xFFB8BEC4; case 2 -> 0xFF3A78C8; default -> SKIN; };
        TextureAtlasSprite sp = CockpitParts.sprite(CockpitParts.WHITE);
        PoseStack.Pose pose = ps.last();
        int rings = 14, seg = 28;
        double[][][] pts = new double[rings + 1][seg][];
        for (int r = 0; r <= rings; r++) {
            double u = len * r / rings, q = u / len;
            double kk = tail ? Math.pow(Math.max(0, 1 - q), 0.85) * (1 - 0.1 * q) : Math.sqrt(Math.max(0, 1 - q * q));
            kk = Math.max(r == rings ? 0 : 0.04, kk);
            double yc = cy + tipOff * h * 0.5 * Math.pow(q, 1.5);
            double pexp = 4.0 - 2.0 * Math.min(1, q * 1.6); // squarish at the fuselage, round toward the tip
            for (int i = 0; i < seg; i++) {
                double a = 2 * Math.PI * i / seg, ca = Math.cos(a), sa = Math.sin(a);
                double x = w / 2 * kk * Math.signum(ca) * Math.pow(Math.abs(ca), 2 / pexp);
                double y = h / 2 * kk * Math.signum(sa) * Math.pow(Math.abs(sa), 2 / pexp);
                pts[r][i] = new double[]{x, yc + y, -0.5 - u};
            }
        }
        for (int r = 0; r < rings; r++)
            for (int i = 0; i < seg; i++) {
                int j = (i + 1) % seg;
                int col = !tail && r >= rings - 2 ? CockpitParts.scale(color, 0.92f) : color;
                quad(vc(buf), pose, sp, P(k, f, pts[r][i]), P(k, f, pts[r][j]), P(k, f, pts[r + 1][j]), P(k, f, pts[r + 1][i]), col, light);
            }
        if (tail && v.telemetry.busPowered) {
            // white tail navigation light and strobe at the tip of the tail cone
            float[] tip = P(k, f, new double[]{0, cy + tipOff * h * 0.5, -0.5 - len + 0.05});
            LightFx.begin(ps);
            double time = System.currentTimeMillis() / 1000.0;
            CockpitRenderer.lamp(ps, buf, v.telemetry, 3, tip[0], tip[1], tip[2], 0, 0, time);
            CockpitRenderer.lamp(ps, buf, v.telemetry, 5, tip[0], tip[1] + 0.05f, tip[2], 0, 0, time);
        }
    }

    // ================================================================== airliner engine

    private static final java.util.Map<Long, double[]> FAN = new java.util.HashMap<>();

    static void engine(VehicleEntity v, PoseStack ps, MultiBufferSource buf, VehicleTelemetry t, long k, BlockState st, int light) {
        ComponentType ct = ComponentType.AIRLINER_ENGINE;
        CompoundTag c = VehicleBuilder.config(v.structure, k);
        Direction f = VehicleBuilder.facing(st);
        double R = ct.property("diameter").get(c) / 2, L = ct.property("length").get(c), py = ct.property("pylon").get(c);
        float spool = 0;
        for (int i = 0; i < t.engineKeys.length; i++) if (t.engineKeys[i] == k && i < t.engineSpool.length) spool = t.engineSpool[i];
        // fan phase
        long id = ((long) v.getId() << 40) ^ k;
        double[] ph = FAN.computeIfAbsent(id, x -> new double[]{0, System.nanoTime()});
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - ph[1]) / 1e9);
        ph[1] = now;
        ph[0] = (ph[0] + spool * 46.0 * dt) % (Math.PI * 2);
        TextureAtlasSprite sp = CockpitParts.sprite(CockpitParts.WHITE);
        PoseStack.Pose pose = ps.last();
        double ay = -py, zf = -0.6 * L, zr = 0.25 * L;
        // outer cowl, fan exhaust, core cowl and exhaust plug as surfaces of revolution {z, r, color}
        double[][] outer = {{zf, 0.9 * R}, {zf + 0.03 * L, R * 0.99}, {zf + 0.3 * L, R}, {zf + 0.62 * L, 0.95 * R}, {zr, 0.84 * R}};
        revolve(buf, pose, sp, k, f, ay, outer, 0xFFE4E7EA, light, true);
        revolve(buf, pose, sp, k, f, ay, new double[][]{{zf, 0.9 * R}, {zf - 0.005, 0.88 * R}, {zf, 0.84 * R}}, 0xFFBCC2C8, light, true);    // inlet lip
        revolve(buf, pose, sp, k, f, ay, new double[][]{{zf, 0.84 * R}, {zf + 0.16 * L, 0.82 * R}}, 0xFF3C4046, light, false);             // inlet duct
        revolve(buf, pose, sp, k, f, ay, new double[][]{{zr, 0.84 * R}, {zr - 0.02 * L, 0.6 * R}, {zr, 0.56 * R}}, 0xFF2E3236, light, false); // fan nozzle
        revolve(buf, pose, sp, k, f, ay, new double[][]{{zr - 0.05 * L, 0.56 * R}, {zr + 0.2 * L, 0.46 * R}}, 0xFF8C9298, light, true);      // core cowl
        revolve(buf, pose, sp, k, f, ay, new double[][]{{zr + 0.2 * L, 0.34 * R}, {zr + 0.36 * L, 0.03 * R}}, 0xFF4E5258, light, true);      // exhaust plug
        revolve(buf, pose, sp, k, f, ay, new double[][]{{zr + 0.2 * L, 0.46 * R}, {zr + 0.2 * L, 0.34 * R}}, 0xFF303438, light, true);
        // fan: dark disc, spinner and blades
        double zfan = zf + 0.16 * L;
        revolve(buf, pose, sp, k, f, ay, new double[][]{{zfan + 0.02, 0.83 * R}, {zfan + 0.02, 0.26 * R}}, 0xFF1A1C1F, light, true);
        revolve(buf, pose, sp, k, f, ay, new double[][]{{zfan, 0.27 * R}, {zfan - 0.08 * L, 0.16 * R}, {zfan - 0.14 * L, 0.01}}, 0xFFA8AEB4, light, true);
        int blades = 22;
        for (int i = 0; i < blades; i++) {
            double a = ph[0] + 2 * Math.PI * i / blades, a2 = a + 0.16;
            double r0 = 0.27 * R, r1 = 0.82 * R;
            double[] p0 = {r0 * Math.cos(a), ay + r0 * Math.sin(a), zfan - 0.03}, p1 = {r1 * Math.cos(a + 0.05), ay + r1 * Math.sin(a + 0.05), zfan - 0.01};
            double[] p2 = {r1 * Math.cos(a2 + 0.08), ay + r1 * Math.sin(a2 + 0.08), zfan + 0.03}, p3 = {r0 * Math.cos(a2), ay + r0 * Math.sin(a2), zfan + 0.03};
            quad(vc(buf), pose, sp, P(k, f, p0), P(k, f, p1), P(k, f, p2), P(k, f, p3), (i & 1) == 0 ? 0xFF9AA0A8 : 0xFF8A9098, light);
        }
        // pylon up to the wing
        if (py > 0.05) {
            double top = 0.5, bot = ay + 0.8 * R, w = Math.max(0.12, 0.11 * R);
            double za = -0.3 * L, zb = 0.3 * L, zat = -0.08 * L, zbt = 0.5 * L; // top runs back under the wing box
            for (int side = -1; side <= 1; side += 2) {
                double x = side * w;
                quad(vc(buf), pose, sp, P(k, f, new double[]{x, bot, za}), P(k, f, new double[]{x, bot, zb}), P(k, f, new double[]{x, top, zbt}), P(k, f, new double[]{x, top, zat}), 0xFFDDE0E4, light);
            }
            quad(vc(buf), pose, sp, P(k, f, new double[]{-w, bot, za}), P(k, f, new double[]{w, bot, za}), P(k, f, new double[]{w, top, zat}), P(k, f, new double[]{-w, top, zat}), EDGE, light);
            quad(vc(buf), pose, sp, P(k, f, new double[]{-w, bot, zb}), P(k, f, new double[]{w, bot, zb}), P(k, f, new double[]{w, top, zbt}), P(k, f, new double[]{-w, top, zbt}), EDGE, light);
        }
    }

    /** a surface of revolution about the engine axis (y = ay) through the profile points {z, r} */
    private static void revolve(MultiBufferSource buf, PoseStack.Pose pose, TextureAtlasSprite sp, long k, Direction f, double ay, double[][] prof, int color, int light, boolean outward) {
        int seg = 24;
        for (int p = 0; p + 1 < prof.length; p++)
            for (int i = 0; i < seg; i++) {
                double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
                double z0 = prof[p][0], r0 = prof[p][1], z1 = prof[p + 1][0], r1 = prof[p + 1][1];
                float[] A = P(k, f, new double[]{r0 * Math.cos(a0), ay + r0 * Math.sin(a0), z0}), B = P(k, f, new double[]{r0 * Math.cos(a1), ay + r0 * Math.sin(a1), z0});
                float[] C = P(k, f, new double[]{r1 * Math.cos(a1), ay + r1 * Math.sin(a1), z1}), D = P(k, f, new double[]{r1 * Math.cos(a0), ay + r1 * Math.sin(a0), z1});
                if (outward) quad(vc(buf), pose, sp, A, D, C, B, color, light);
                else quad(vc(buf), pose, sp, A, B, C, D, color, light);
            }
    }

    // ================================================================== landing gear bay doors

    /** two doors hinged at the sides of the gear bay: closed flush under the belly when the gear is up, open while it is down */
    public static void gearDoors(PoseStack ps, MultiBufferSource buf, long k, Direction f, float scale, float ext, int light) {
        TextureAtlasSprite sp = CockpitParts.sprite(CockpitParts.WHITE);
        PoseStack.Pose pose = ps.last();
        double W = 0.52 * scale, Ld = 0.62 * scale, top = 0.5 - 0.01;
        double a = Math.toRadians(88) * Math.min(1, ext * 1.6);
        for (int sd = -1; sd <= 1; sd += 2) {
            double hx = sd * W, ix = hx - sd * W * Math.cos(a), iy = top - W * Math.sin(a);
            float[] p0 = P(k, f, new double[]{hx, top, -Ld}), p1 = P(k, f, new double[]{hx, top, Ld});
            float[] p2 = P(k, f, new double[]{ix, iy, Ld}), p3 = P(k, f, new double[]{ix, iy, -Ld});
            quad(vc(buf), pose, sp, p0, p1, p2, p3, 0xFFD6DADE, light);
        }
    }
}
