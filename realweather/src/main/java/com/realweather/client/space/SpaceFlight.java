package com.realweather.client.space;

import com.realweather.RealWeather;
import com.realweather.device.LightDrive;
import com.realweather.net.TravelPayload;
import com.realweather.space.Sites;
import com.realweather.space.Worlds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.realweather.client.space.Space3D.ADD;

/**
 * Really being out in space. Beside a star, pulsar, black hole, nebula or comet (taken there by the planet map) you
 * float near it and fly round it: look where to go, the movement keys to move. Between the planets (left behind at
 * the speed of light with the light drive) the whole solar system is round you to scale: the Sun, the planets and
 * their moons where they really are, the stars bunching ahead and turning blue at high speed; head for a world and,
 * once close, you arrive above it and come down through its sky. Time runs faster out between the worlds (or the
 * trips would take hours even at the speed of light).
 */
public final class SpaceFlight {
    /** How much faster time runs between the planets. */
    static final double WARP = 200;
    private static final ResourceLocation STAR_MAP = RealWeather.id("textures/environment/space3d/star_surface.png");
    private static final ResourceLocation EARTH_CLOUDS = RealWeather.id("textures/environment/space3d/earth_clouds.png");
    private static final ResourceLocation EARTH_LIGHTS = RealWeather.id("textures/environment/space3d/earth_lights.png");
    private static String place;
    /** Where you are: km from the Sun between the planets, or radii from the object at a site. */
    static final double[] pos = new double[3], vel = new double[3];
    /** The world's axes in space (column-major, world to space). */
    private static double[] frame = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    private static double[] pendingPos, pendingFrame;
    private static float fwd, strafe, lift;
    private static boolean fast;
    private static long last;
    private static double clock;
    private static boolean sent;
    private static int cooldown;
    private static boolean saidNoRoute;
    /** For the screen: names of what is round you, where they are on it, and what you are doing. */
    private static final List<Object[]> LABELS = new ArrayList<>();
    private static String nearName = "";
    private static double nearDist, speedNow;
    private static int drivePct;
    private static boolean driveOn;

    private SpaceFlight() {}

    // ------------------------------------------------------------------------------------------- where

    /** The place out in space the player is in (FLIGHT, a site's id), or null. */
    public static String here() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != Worlds.DIMENSION) {
            return null;
        }
        return Sites.at(mc.player.getX());
    }

    public static boolean active() {
        return place != null;
    }

    /** The light drive the player holds, switched on (null if none). */
    private static ItemStack drive(LocalPlayer p) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.getItem() instanceof LightDrive) {
                return s;
            }
        }
        return null;
    }

    private static void enter(String now) {
        place = now;
        sent = false;
        vel[0] = vel[1] = vel[2] = 0;
        if (now == null) {
            return;
        }
        if (Sites.FLIGHT.equals(now)) {
            if (pendingPos != null) {
                System.arraycopy(pendingPos, 0, pos, 0, 3);
                frame = pendingFrame;
                pendingPos = null;
            } else {
                // Back out into space after a break: above Earth.
                SolarSystem.Body e = SolarSystem.byId("earth");
                SolarSystem.position(e, days(), pos);
                pos[2] += e.radius() * 4;
                frame = new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
            }
            return;
        }
        Catalog.Target t = Catalog.byId(now);
        double start = t == null ? 4 : CloseViews.start(t.body);
        pos[0] = 0;
        pos[1] = start * 0.12;
        pos[2] = start;
        frame = new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
    }

    private static double days() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getDayTime() / 24000.0;
    }

    // ------------------------------------------------------------------------------------------- input

    /** Out in space the movement keys fly you (the body itself stays put on its platform). */
    public static void onInput(MovementInputUpdateEvent event) {
        if (place == null) {
            return;
        }
        Input in = event.getInput();
        fwd = in.forwardImpulse;
        strafe = in.leftImpulse;
        lift = (in.jumping ? 1F : 0F) - (in.shiftKeyDown ? 1F : 0F);
        fast = Minecraft.getInstance().options.keySprint.isDown();
        in.forwardImpulse = 0F;
        in.leftImpulse = 0F;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            place = null;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        String now = here();
        if (!java.util.Objects.equals(now, place)) {
            enter(now);
        }
        ItemStack d = drive(mc.player);
        driveOn = d != null && LightDrive.on(d);
        drivePct = d == null ? 0 : LightDrive.percent(d);
        if (now == null) {
            launch(mc);
        }
    }

    /** Holding the drive switched on, looking up into the sky and walking forward: off you go. */
    private static void launch(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (!driveOn || mc.screen != null || cooldown > 0 || !mc.options.keyUp.isDown() || p.getXRot() > -25F) {
            return;
        }
        ClientLevel level = mc.level;
        SolarSystem.Body origin = null;
        if (level.dimension() == Level.OVERWORLD) {
            origin = SolarSystem.byId("earth");
        } else if (level.dimension() == Worlds.DIMENSION) {
            Worlds.World w = Worlds.at(p.getX());
            if (w != null) {
                origin = SolarSystem.byId(w.id());
            }
        }
        if (origin == null) {
            if (!saidNoRoute) {
                p.displayClientMessage(Component.translatable("realweather.drive.no_route"), true);
                saidNoRoute = true;
            }
            cooldown = 40;
            return;
        }
        saidNoRoute = false;
        double day = days();
        double th = Space3D.timeOfDay(level, 0F) * Math.PI * 2;
        double[] fr = SolarSystem.frame(origin, day, th);
        double[] bp = new double[3];
        SolarSystem.position(origin, day, bp);
        double alt = origin.radius() * 1.35;
        pendingPos = new double[]{bp[0] + fr[3] * alt, bp[1] + fr[4] * alt, bp[2] + fr[5] * alt};
        pendingFrame = fr;
        cooldown = 100;
        PacketDistributor.sendToServer(new TravelPayload("", TravelPayload.LAUNCH, 0, 0));
        p.displayClientMessage(Component.translatable("realweather.drive.launch", Component.translatable("realweather.target." + origin.id()),
                drivePct), true);
    }

    // ------------------------------------------------------------------------------------------- moving

    private static void move(double dt, Vector3f look, Vector3f up, Vector3f left) {
        double wx = look.x() * fwd + left.x() * strafe + up.x() * lift, wy = look.y() * fwd + left.y() * strafe + up.y() * lift,
                wz = look.z() * fwd + left.z() * strafe + up.z() * lift;
        double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
        double[] dir = new double[3];
        if (wl > 1.0E-4) {
            Space3D.apply(frame, wx / wl, wy / wl, wz / wl, dir);
        }
        boolean solar = Sites.FLIGHT.equals(place);
        double speed;
        if (solar) {
            speed = driveOn ? drivePct / 100.0 * SolarSystem.C * WARP : 4000 * (fast ? 6 : 1);
        } else {
            speed = driveOn ? 0.05 + drivePct / 100.0 * 2.0 : 0.12 * (fast ? 3 : 1);
        }
        // Slowing down on the way in to whatever is ahead (so as not to fly straight through it).
        double brake = Double.MAX_VALUE;
        if (solar) {
            double day = days();
            double[] bp = new double[3];
            for (SolarSystem.Body b : SolarSystem.ALL) {
                SolarSystem.position(b, day, bp);
                double rx = bp[0] - pos[0], ry = bp[1] - pos[1], rz = bp[2] - pos[2];
                double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
                double towards = (rx * dir[0] + ry * dir[1] + rz * dir[2]) / Math.max(1.0E-9, dist);
                if (towards > 0.2) {
                    double room = dist - b.radius() * (b.kind() == SolarSystem.SUN ? 2.5 : 1.6);
                    brake = Math.min(brake, Math.max(300, room * 0.9));
                }
            }
        } else {
            Catalog.Target t = Catalog.byId(place);
            double dist = Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]);
            double towards = -(pos[0] * dir[0] + pos[1] * dir[1] + pos[2] * dir[2]) / Math.max(1.0E-9, dist);
            if (t != null && towards > 0.2) {
                brake = Math.max(0.02, (dist - CloseViews.closest(t.body)) * 0.9);
            }
        }
        speed = Math.min(speed, brake);
        speedNow = wl > 1.0E-4 ? speed : Math.sqrt(vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2]);
        double k = 1 - Math.exp(-dt * 2.5);
        for (int i = 0; i < 3; i++) {
            vel[i] += (dir[i] * (wl > 1.0E-4 ? speed : 0) - vel[i]) * k;
            pos[i] += vel[i] * dt;
        }
    }

    // ------------------------------------------------------------------------------------------- drawing

    /** Draws the space round you (called from the sky, with the 3D renderer open). */
    static void render(RenderLevelStageEvent event, ClientLevel level, float pt) {
        long now = System.nanoTime();
        double dt = last == 0 ? 0.016 : Mth.clamp((now - last) / 1.0E9, 0, 0.1);
        last = now;
        clock += dt;
        if (place == null) {
            enter(here());
            if (place == null) {
                return;
            }
        }
        var camera = event.getCamera();
        move(dt, camera.getLookVector(), camera.getUpVector(), camera.getLeftVector());
        // Space to view: the camera's own turn, then the world's axes into space.
        double[] ft = {frame[0], frame[3], frame[6], frame[1], frame[4], frame[7], frame[2], frame[5], frame[8]};
        double[] m = new double[9];
        Space3D.mul(Space3D.VIEW, ft, m);
        System.arraycopy(m, 0, Space3D.SKYVIEW, 0, 9);
        LABELS.clear();
        Space3D.range(5F, 80000F);
        Space3D.fullScreen(0F, 0F, 0.003F, 1F);
        if (Sites.FLIGHT.equals(place)) {
            solar(m, level, pt);
        } else {
            site(m);
        }
    }

    private static void site(double[] m) {
        Catalog.Target t = Catalog.byId(place);
        if (t == null) {
            return;
        }
        Catalog.Body body = t.body;
        TelescopeView.brightStars(1F);
        double R = CloseViews.radius(body);
        double[] cam = {pos[0] * R, pos[1] * R, pos[2] * R};
        double dist = Math.sqrt(cam[0] * cam[0] + cam[1] * cam[1] + cam[2] * cam[2]);
        double[] f = {-m[2], -m[5], -m[8]}, right = {m[0], m[3], m[6]}, up = {m[1], m[4], m[7]};
        int h = Math.max(1, Minecraft.getInstance().getWindow().getHeight());
        double px = 2 * dist / (Space3D.projY() * h);
        CloseViews.object(body, m, cam, R, f, right, up, px, 1 / Space3D.projX(), 1 / Space3D.projY(), clock);
        nearName = "realweather.target." + body.id;
        nearDist = dist / R;
        // Too close: the star's fire, the pulsar's radiation, the black hole's pull.
        double deadly = CloseViews.deadly(body);
        if (deadly > 0 && nearDist < deadly) {
            if (!sent) {
                sent = true;
                PacketDistributor.sendToServer(new TravelPayload(body.id, TravelPayload.HAZARD, CloseViews.hazard(body), 0));
            }
            double l = Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]) + 1.0E-9;
            double k = (deadly + 0.3) / l;
            for (int i = 0; i < 3; i++) {
                pos[i] *= k;
                vel[i] = 0;
            }
        } else if (nearDist > deadly + 0.6) {
            sent = false;
        }
    }

    /** The solar system round you, to scale. */
    private static void solar(double[] m, ClientLevel level, float pt) {
        double day = (level.getDayTime() + pt) / 24000.0;
        double vl = Math.sqrt(vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2]);
        double beta = Math.min(0.995, vl / SolarSystem.C / WARP);
        double[] vdir = vl > 1.0E-6 ? new double[]{vel[0] / vl, vel[1] / vl, vel[2] / vl} : new double[]{0, 0, 1};
        stars(m, beta, vdir);
        int h = Math.max(1, Minecraft.getInstance().getWindow().getHeight());
        double pixAng = 2 / (Space3D.projY() * h);
        // All the bodies, the farthest first.
        List<double[]> order = new ArrayList<>();
        double[] bp = new double[3];
        double best = Double.MAX_VALUE;
        String bestId = "";
        for (int i = 0; i < SolarSystem.ALL.size(); i++) {
            SolarSystem.Body b = SolarSystem.ALL.get(i);
            SolarSystem.position(b, day, bp);
            double rx = bp[0] - pos[0], ry = bp[1] - pos[1], rz = bp[2] - pos[2];
            double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
            order.add(new double[]{dist, i, rx, ry, rz, bp[0], bp[1], bp[2]});
            double gap = dist - b.radius();
            if (gap < best) {
                best = gap;
                bestId = b.id();
            }
        }
        nearName = "realweather.target." + bestId;
        nearDist = best;
        order.sort((a, b) -> Double.compare(b[0], a[0]));
        for (double[] o : order) {
            SolarSystem.Body b = SolarSystem.ALL.get((int) o[1]);
            body(b, o, m, day, pixAng);
        }
        arrive(day);
    }

    /** Stars all round: at high speed bunched towards where you are going and bluer there, redder behind. */
    private static void stars(double[] m, double beta, double[] v) {
        float[] field = TelescopeView.field();
        int h = Math.max(1, Minecraft.getInstance().getWindow().getHeight());
        double px = 2.0 / (Space3D.projY() * h) * 2000;
        double gamma = 1 / Math.sqrt(1 - beta * beta);
        double[] q = new double[3];
        for (int i = 0; i < 6000; i++) {
            double sx = field[i * 5], sy = field[i * 5 + 1], sz = field[i * 5 + 2];
            float bright = field[i * 5 + 3], tint = field[i * 5 + 4];
            float r = tint < 0.3F ? 1F : tint < 0.6F ? 0.95F : 0.8F, g = tint < 0.3F ? 0.82F : tint < 0.6F ? 0.95F : 0.88F, b = tint < 0.3F ? 0.65F : 1F;
            float boost = 1F;
            if (beta > 0.02) {
                double cos = sx * v[0] + sy * v[1] + sz * v[2];
                double c2 = (cos + beta) / (1 + beta * cos);
                double px0 = sx - v[0] * cos, py0 = sy - v[1] * cos, pz0 = sz - v[2] * cos;
                double pl = Math.sqrt(px0 * px0 + py0 * py0 + pz0 * pz0);
                double s2 = Math.sqrt(Math.max(0, 1 - c2 * c2));
                if (pl > 1.0E-6) {
                    sx = v[0] * c2 + px0 / pl * s2;
                    sy = v[1] * c2 + py0 / pl * s2;
                    sz = v[2] * c2 + pz0 / pl * s2;
                }
                double dop = gamma * (1 + beta * c2);
                boost = (float) Math.min(3, dop * dop);
                float shift = (float) Mth.clamp(Math.log(dop), -1, 1);
                r *= 1 - 0.45F * Math.max(0, shift) + 0.2F * Math.max(0, -shift);
                g *= 1 - 0.2F * Math.abs(shift);
                b *= 1 + 0.3F * Math.max(0, shift) - 0.6F * Math.max(0, -shift);
            }
            Space3D.apply(m, sx * 2000, sy * 2000, sz * 2000, q);
            if (q[2] > -1) {
                continue;
            }
            Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], px * (1.6 + 3.5 * bright) * Math.min(1.8, Math.sqrt(boost)), 0F, Math.min(1F, r),
                    Math.min(1F, g), Math.min(1F, b), Math.min(1F, (0.45F + 0.55F * bright) * boost));
        }
    }

    private static final double D = 1000;

    private static void body(SolarSystem.Body b, double[] o, double[] m, double day, double pixAng) {
        double dist = o[0];
        double[] rel = {o[2], o[3], o[4]};
        double[] v = new double[3];
        Space3D.apply(m, rel[0] / dist, rel[1] / dist, rel[2] / dist, v);
        double ang = b.radius() / dist;
        double sizePx = ang / pixAng;
        // Where the Sun is from it (for its lit side and a comet's tails).
        double sl = Math.sqrt(o[5] * o[5] + o[6] * o[6] + o[7] * o[7]) + 1.0E-9;
        double[] sun = new double[3];
        Space3D.apply(m, -o[5] / sl, -o[6] / sl, -o[7] / sl, sun);
        boolean planet = b.parent() == null && b.kind() != SolarSystem.SUN;
        if (v[2] < 0 && (planet || b.kind() == SolarSystem.COMET || dist < 2.0E7)) {
            double ndx = Space3D.projX() * v[0] / -v[2], ndy = Space3D.projY() * v[1] / -v[2];
            if (Math.abs(ndx) < 1.05 && Math.abs(ndy) < 1.05) {
                LABELS.add(new Object[]{ndx, ndy, "realweather.target." + b.id(), distance(dist - b.radius())});
            }
        }
        if (b.kind() == SolarSystem.COMET) {
            comet(b, dist, v, sun, sl, day);
            return;
        }
        if (b.kind() == SolarSystem.SUN) {
            sunBody(dist, v, sizePx);
            return;
        }
        if (v[2] > 0 && ang < 0.5) {
            return;
        }
        if (sizePx < 1.5) {
            // A point of light: brighter the bigger and nearer to the Sun it is.
            double lum = (ang * ang) * 1.0E13 / Math.max(0.05, Math.pow(sl / SolarSystem.AU, 2));
            float bright = (float) Mth.clamp(0.2 + 0.11 * Math.log10(lum + 1), 0.08, 1);
            double s = 5000;
            Space3D.billboard(Space3D.GLOW, ADD, v[0] * s, v[1] * s, v[2] * s, pixAng * s * (1.5 + 2.5 * bright), 0F, 1F, 0.95F, 0.85F, bright);
            return;
        }
        double k = D / dist;
        double[] c = {v[0] * D, v[1] * D, v[2] * D};
        double r = b.radius() * k;
        Catalog.Target t = Catalog.byId(b.id());
        Space3D.clearDepth();
        double reach = r * (t != null && t.body.rings ? 2.4 : 1.3);
        Space3D.range((float) Math.max(0.05, D - reach * 1.2), (float) (D + reach * 1.2));
        if (t == null) {
            Space3D.billboard(Space3D.GLOW, ADD, c[0], c[1], c[2], r * 2, 0F, 0.9F, 0.9F, 0.9F, 0.6F);
            return;
        }
        // Its axis (tilted as it really is) and its spin.
        double tilt = Math.toRadians(switch (b.id()) {
            case "earth" -> 23.4;
            case "mars" -> 25.2;
            case "saturn" -> 26.7;
            case "uranus" -> 97.8;
            case "neptune" -> 28.3;
            default -> 3;
        });
        double[] axis = new double[3];
        Space3D.apply(m, Math.sin(tilt), Math.cos(tilt), 0, axis);
        double spinDays = switch (b.id()) {
            case "jupiter" -> 0.41;
            case "saturn" -> 0.44;
            case "uranus" -> 0.72;
            case "neptune" -> 0.67;
            case "earth" -> 1;
            case "mars" -> 1.03;
            default -> 10;
        };
        double spin = (day / spinDays) % 1.0;
        double[] rot = Space3D.orient(axis[0], axis[1], axis[2], 0);
        double px = r / Math.max(1.0E-9, sizePx);
        float lr = 1F, lg = 0.97F, lb = 0.92F;
        CloseViews.planet(t.body, c, r, rot, sun, lr, lg, lb, clock, px, 1F, (float) spin);
        if ("earth".equals(b.id())) {
            Space3D.sphereMode(ADD);
            Space3D.nightSide(true);
            Space3D.sphere(EARTH_LIGHTS, c[0], c[1], c[2], r * 1.001, rot, sun[0], sun[1], sun[2], 0F, 1F, 0.85F, 0.6F, 0F, 0.9F, false, (float) spin, 72);
            Space3D.nightSide(false);
            Space3D.sphereMode(Space3D.NORMAL);
            Space3D.sphere(EARTH_CLOUDS, c[0], c[1], c[2], r * 1.012, rot, sun[0], sun[1], sun[2], 0.01F, 1F, 1F, 1F, 0F, 0.9F, false,
                    (float) ((spin + clock * 0.0005) % 1.0), 72);
        }
    }

    private static void sunBody(double dist, double[] v, double sizePx) {
        double s = 5000;
        double ang = 696340 / dist;
        float near = (float) Mth.clamp(1.2 - Math.log10(dist / SolarSystem.AU + 1.0E-3) * 0.25, 0.3, 1.6);
        if (v[2] < 0) {
            Space3D.billboard(Space3D.GLOW, ADD, v[0] * s, v[1] * s, v[2] * s, Math.max(s * 0.02, s * ang * 7), 0F, 1F, 0.92F, 0.78F, 0.6F * near);
            Space3D.billboard(Space3D.STAR, ADD, v[0] * s, v[1] * s, v[2] * s, Math.max(s * 0.04, s * ang * 14), 0F, 1F, 0.95F, 0.85F, 0.5F * near);
        }
        if (sizePx < 3) {
            if (v[2] < 0) {
                Space3D.billboard(Space3D.GLOW, ADD, v[0] * s, v[1] * s, v[2] * s, s * 0.004, 0F, 1F, 1F, 1F, 1F);
            }
            return;
        }
        double[] c = {v[0] * D, v[1] * D, v[2] * D};
        double r = 696340 * D / dist;
        Space3D.clearDepth();
        Space3D.range((float) Math.max(0.05, D - r * 4), (float) (D + r * 4));
        Catalog.Target t = Catalog.byId("sun");
        if (t != null) {
            CloseViews.star(t.body, c, Space3D.SKYVIEW, r, 4242, clock, r / Math.max(1.0E-9, sizePx), 1F);
        }
        // Burning up if you fly into it.
        if (dist < 696340 * 2.0 && !sent) {
            sent = true;
            PacketDistributor.sendToServer(new TravelPayload("sun", TravelPayload.HAZARD, 0, 0));
        }
    }

    private static void comet(SolarSystem.Body b, double dist, double[] v, double[] sun, double fromSun, double day) {
        double au = fromSun / SolarSystem.AU;
        // Tails grow as it nears the Sun: tens of millions of kilometres.
        double tail = 2.5e7 / Math.pow(Math.max(0.4, au), 1.5), coma = 1.2e5 / Math.max(0.5, au);
        double k = D / dist;
        double[] c = {v[0] * D, v[1] * D, v[2] * D};
        double[] vel = new double[3], vv = new double[3];
        SolarSystem.velocity(b, day, vel);
        double[] m = Space3D.SKYVIEW;
        Space3D.apply(m, vel[0], vel[1], vel[2], vv);
        Space3D.clearDepth();
        double reach = Math.max(coma, tail) * k;
        Space3D.range((float) Math.max(0.05, D - reach * 1.3), (float) (D + reach * 1.3));
        int h = Math.max(1, Minecraft.getInstance().getWindow().getHeight());
        double px = 2 * D / (Space3D.projY() * h);
        Comets.draw(c[0], c[1], c[2], coma * k, sun, vv, tail * k, b.id().hashCode(), clock, px, 1F);
    }

    /** Close enough to a world: you arrive high above it. */
    private static void arrive(double day) {
        if (sent) {
            return;
        }
        double[] bp = new double[3];
        for (SolarSystem.Body b : SolarSystem.ALL) {
            if (b.kind() == SolarSystem.SUN || b.kind() == SolarSystem.COMET) {
                continue;
            }
            boolean home = "earth".equals(b.id());
            if (!home && Worlds.byId(b.id()) == null) {
                continue;
            }
            SolarSystem.position(b, day, bp);
            double rx = pos[0] - bp[0], ry = pos[1] - bp[1], rz = pos[2] - bp[2];
            double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (dist < b.radius() * (b.kind() == SolarSystem.GAS ? 2.4 : 2.0) + 500) {
                sent = true;
                double lon = Math.atan2(rz, rx), lat = Math.asin(Mth.clamp(ry / dist, -1, 1)) * 0.8;
                PacketDistributor.sendToServer(home ? new TravelPayload("", TravelPayload.HOME, 0, 0)
                        : new TravelPayload(b.id(), TravelPayload.ORBIT, lon, lat));
                return;
            }
        }
    }

    private static String distance(double km) {
        if (km < 1.0E6) {
            return String.format(Locale.ROOT, "%,.0f km", Math.max(0, km));
        }
        return String.format(Locale.ROOT, "%.2f AU", km / SolarSystem.AU);
    }

    // ------------------------------------------------------------------------------------------- the screen

    /** Names on what is round you, and your speed and where you are going. */
    public static void hud(GuiGraphics g, DeltaTracker delta) {
        if (place == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }
        var font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        for (Object[] l : LABELS) {
            int x = (int) (((double) l[0] + 1) * 0.5 * w), y = (int) ((1 - (double) l[1]) * 0.5 * h);
            Component name = Component.translatable((String) l[2]);
            g.drawString(font, name, x + 4, y - 4, 0xFFB8D8FF, true);
            g.drawString(font, (String) l[3], x + 4, y + 5, 0xFF8090A8, true);
            g.fill(x - 1, y - 1, x + 1, y + 1, 0xFFB8D8FF);
        }
        boolean solar = Sites.FLIGHT.equals(place);
        int y = 6;
        g.drawString(font, solar ? Component.translatable("realweather.flight.between") : Component.translatable("realweather.flight.at",
                Component.translatable("realweather.target." + place)), 6, y, 0xFFFFE080, true);
        y += 11;
        if (driveOn) {
            g.drawString(font, Component.translatable(solar ? "realweather.flight.drive" : "realweather.flight.drive_site", drivePct,
                    String.format(Locale.ROOT, "%.0f", WARP)), 6, y, 0xFF80D0FF, true);
        } else {
            g.drawString(font, Component.translatable("realweather.flight.thrusters"), 6, y, 0xFFC0C0C0, true);
        }
        y += 11;
        if (solar) {
            g.drawString(font, Component.translatable("realweather.flight.nearest", Component.translatable(nearName), distance(nearDist)), 6, y,
                    0xFFE0E0E0, true);
        } else {
            g.drawString(font, Component.translatable("realweather.flight.distance", String.format(Locale.ROOT, "%.2f", nearDist)), 6, y, 0xFFE0E0E0,
                    true);
            Catalog.Target t = Catalog.byId(place);
            if (t != null && CloseViews.deadly(t.body) > 0 && nearDist < CloseViews.deadly(t.body) * 1.6) {
                g.drawString(font, Component.translatable("realweather.flight.danger"), 6, y + 11, 0xFFFF6040, true);
            }
        }
        g.drawString(font, Component.translatable(solar ? "realweather.flight.hint" : "realweather.flight.hint_site"), 6, h - 52, 0xFFA0A0A0, true);
    }
}
