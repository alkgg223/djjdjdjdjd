package com.gunsmod.client;

import com.gunsmod.Reg;
import com.gunsmod.entity.ArmoredCarEntity;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.HeliType;
import com.gunsmod.entity.HelicopterEntity;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.JetEntity;
import com.gunsmod.entity.TankEntity;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.gun.Attachment;
import com.gunsmod.gun.GunAttachments;
import com.gunsmod.gun.GunLogic;
import com.gunsmod.gun.GunType;
import com.gunsmod.item.GunItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** HUD layers: ammo counter, dynamic crosshair, hit marker, sniper scope, vehicle speedometer, soldier info. */
public final class Hud {
    private Hud() {}

    private static final ResourceLocation SCOPE = Reg.id("textures/gui/scope.png");
    private static final ResourceLocation SCOPE_RED_DOT = Reg.id("textures/gui/scope_red_dot.png");
    private static final ResourceLocation SCOPE_HOLO = Reg.id("textures/gui/scope_holo.png");
    private static final ResourceLocation SCOPE_ACOG = Reg.id("textures/gui/scope_acog.png");
    private static final ResourceLocation GAUGE = Reg.id("textures/gui/speedometer.png");
    private static final ResourceLocation PANEL = Reg.id("panel_dark");

    private static boolean hidden(Minecraft mc) {
        return mc.player == null || mc.options.hideGui || mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
    }

    // ------------------------------------------------------------------------------------ blast flash
    /** white-out after a nearby strategic missile */
    public static void flash(GuiGraphics g, DeltaTracker dt) {
        float f = ClientFx.flash;
        if (f <= 0.01f) return;
        int a = (int) (Mth.clamp(f, 0f, 1f) * 235);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0xFFF6E8);
    }

    // ------------------------------------------------------------------------------------ scope overlay
    public static void scope(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (hidden(mc) || !GunClient.scoped()) return;
        Attachment optic = GunItem.attachments(mc.player.getMainHandItem()).get(Attachment.Slot.SCOPE);
        int w = g.guiWidth(), h = g.guiHeight();
        int s = Math.min(w, h);
        ResourceLocation tex = optic == Attachment.RED_DOT ? SCOPE_RED_DOT : optic == Attachment.HOLO_SIGHT ? SCOPE_HOLO
                : optic == Attachment.ACOG ? SCOPE_ACOG : SCOPE;
        // reflex sights: a short tube / window at arm's length, the world stays visible round it
        boolean reflex = optic == Attachment.RED_DOT || optic == Attachment.HOLO_SIGHT;
        if (reflex) s = (int) (Math.min(w, h) * 1.15f);
        int x = (w - s) / 2, y = (h - s) / 2;
        RenderSystem.enableBlend();
        g.blit(tex, x, y, 0, 0, s, s, s, s);
        if (!reflex) {
            g.fill(0, 0, x, h, 0xFF000000);
            g.fill(x + s, 0, w, h, 0xFF000000);
            g.fill(x, 0, x + s, y, 0xFF000000);
            g.fill(x, y + s, x + s, h, 0xFF000000);
        }
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------------------------ crosshair + hit marker
    public static void crosshair(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (hidden(mc) || !mc.options.getCameraType().isFirstPerson()) return;
        LocalPlayer p = mc.player;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        if (p.getVehicle() instanceof TankEntity tank && tank.getControllingPassenger() == p) {
            tankReticle(g, mc, p, tank, cx, cy, dt.getGameTimeDeltaPartialTick(true));
            hitMarker(g, cx, cy);
            return;
        }
        float pt = dt.getGameTimeDeltaPartialTick(true);
        if (p.getVehicle() instanceof ArmoredCarEntity car && car.getControllingPassenger() == p) {
            carReticle(g, mc, p, car, cx, cy, pt);
            hitMarker(g, cx, cy);
            return;
        }
        if (p.getVehicle() instanceof JetEntity jet) {
            jetReticle(g, mc, p, jet, cx, cy, pt);
            hitMarker(g, cx, cy);
            return;
        }
        if (p.getVehicle() instanceof HelicopterEntity heli && heli.firesFor(p)) {
            heliReticle(g, mc, p, heli, cx, cy, pt);
            hitMarker(g, cx, cy);
            return;
        }
        if (p.getVehicle() instanceof JeepEntity jeep && jeep.isGunner(p) && jeep.tow) {
            int col = jeep.towReloadFraction() > 0 ? 0xE0FFA030 : 0xE040FF60;
            g.fill(cx - 20, cy, cx - 5, cy + 1, col);
            g.fill(cx + 6, cy, cx + 21, cy + 1, col);
            g.fill(cx, cy - 12, cx + 1, cy - 4, col);
            g.fill(cx, cy + 5, cx + 1, cy + 13, col);
            g.fill(cx - 20, cy - 8, cx - 19, cy + 9, col);
            g.fill(cx + 20, cy - 8, cx + 21, cy + 9, col);
            hitMarker(g, cx, cy);
            return;
        }
        if (p.getVehicle() instanceof JeepEntity jeep && jeep.isGunner(p)) {
            int col = jeep.isOverheated() ? 0xFFFF4040 : 0xE0FFFFFF;
            g.fill(cx - 1, cy - 9, cx + 1, cy - 3, col);
            g.fill(cx - 1, cy + 3, cx + 1, cy + 9, col);
            g.fill(cx - 9, cy - 1, cx - 3, cy + 1, col);
            g.fill(cx + 3, cy - 1, cx + 9, cy + 1, col);
            g.fill(cx - 12, cy - 12, cx - 7, cy - 11, col);
            g.fill(cx + 7, cy - 12, cx + 12, cy - 11, col);
            g.fill(cx - 12, cy + 11, cx - 7, cy + 12, col);
            g.fill(cx + 7, cy + 11, cx + 12, cy + 12, col);
            hitMarker(g, cx, cy);
            return;
        }
        ItemStack st = p.getMainHandItem();
        if (!(st.getItem() instanceof GunItem gun)) return;
        float a = GunClient.aim(dt.getGameTimeDeltaPartialTick(true));
        if (a < 0.6f && !GunClient.scoped()) {
            GunAttachments att = GunItem.attachments(st);
            float spread = GunLogic.spread(gun.type, att, false, p) * (1f - a);
            float k = GunClient.kick(dt.getGameTimeDeltaPartialTick(true));
            int gap = 3 + Math.round(spread * 2.2f + k * 4f);
            int len = 5;
            int alpha = (int) (220 * (1f - a / 0.6f));
            int col = (alpha << 24) | 0xFFFFFF;
            if (gun.type.category == GunType.Category.SHOTGUN || gun.type.kind == GunType.Kind.FLAME) {
                // circle for spread weapons
                for (int i = 0; i < 24; i++) {
                    double ang = i * Math.PI * 2 / 24;
                    int x = cx + (int) Math.round(Math.cos(ang) * (gap + 3)), y = cy + (int) Math.round(Math.sin(ang) * (gap + 3));
                    g.fill(x, y, x + 1, y + 1, col);
                }
                g.fill(cx, cy, cx + 1, cy + 1, col);
            } else {
                g.fill(cx, cy - gap - len, cx + 1, cy - gap, col);
                g.fill(cx, cy + gap + 1, cx + 1, cy + gap + len + 1, col);
                g.fill(cx - gap - len, cy, cx - gap, cy + 1, col);
                g.fill(cx + gap + 1, cy, cx + gap + len + 1, cy + 1, col);
                g.fill(cx, cy, cx + 1, cy + 1, col);
            }
        }
        hitMarker(g, cx, cy);
    }

    /** where the gun really points (the turret lags behind the view) and whether it is loaded */
    private static void tankReticle(GuiGraphics g, Minecraft mc, LocalPlayer p, TankEntity tank, int cx, int cy, float pt) {
        float gunYaw = Mth.rotLerp(pt, tank.yRotO, tank.getYRot()) + Mth.rotLerp(pt, tank.prevTurret, tank.getTurretYaw());
        float dyaw = Mth.wrapDegrees(gunYaw - p.getViewYRot(pt));
        float dpitch = Mth.lerp(pt, tank.prevGun, tank.getGunPitch()) - p.getViewXRot(pt);
        double f = (g.guiHeight() / 2.0) / Math.tan(Math.toRadians(mc.options.fov().get() / 2.0));
        boolean ok = Math.abs(dyaw) < 80 && Math.abs(dpitch) < 80;
        int gx = cx + (ok ? (int) Math.round(Math.tan(Math.toRadians(dyaw)) * f) : 0);
        int gy = cy + (ok ? (int) Math.round(Math.tan(Math.toRadians(dpitch)) * f) : 0);
        boolean loaded = tank.getReload() <= 0;
        int col = loaded ? 0xE040FF60 : 0xE0FFA030;
        // view centre: small dot
        g.fill(cx, cy, cx + 1, cy + 1, 0x90FFFFFF);
        // gun: open cross with a ranging bar
        g.fill(gx - 14, gy, gx - 4, gy + 1, col);
        g.fill(gx + 5, gy, gx + 15, gy + 1, col);
        g.fill(gx, gy + 5, gx + 1, gy + 13, col);
        g.fill(gx - 3, gy + 8, gx + 4, gy + 9, col);
        g.fill(gx - 1, gy - 1, gx + 2, gy + 2, col);
        if (!loaded) {
            float k = 1f - tank.getReload() / (float) tank.kind.reload;
            g.fill(gx - 15, gy + 16, gx + 16, gy + 18, 0x80000000);
            g.fill(gx - 15, gy + 16, gx - 15 + (int) (31 * k), gy + 18, 0xFFFFA030);
        }
    }

    /** screen position of a world point (small angle projection around the view centre), or null when behind */
    private static int[] project(Minecraft mc, LocalPlayer p, Vec3 at, int cx, int cy, float pt, double guiH) {
        Vec3 eye = p.getEyePosition(pt);
        Vec3 d = at.subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        float dyaw = Mth.wrapDegrees(yaw - p.getViewYRot(pt)), dpitch = pitch - p.getViewXRot(pt);
        if (Math.abs(dyaw) > 75 || Math.abs(dpitch) > 75) return null;
        double f = (guiH / 2.0) / Math.tan(Math.toRadians(mc.options.fov().get() / 2.0));
        return new int[]{cx + (int) Math.round(Math.tan(Math.toRadians(dyaw)) * f), cy + (int) Math.round(Math.tan(Math.toRadians(dpitch)) * f)};
    }

    /** square brackets round a target: dashed while the seeker works, solid red when locked */
    private static void lockBox(GuiGraphics g, int x, int y, float progress, boolean blink) {
        int r = 14 - (int) (progress * 4);
        int col = progress >= 1f ? 0xFFFF3030 : 0xE0FFD040;
        if (progress < 1f && blink) col = 0x80FFD040;
        g.fill(x - r, y - r, x - r + 6, y - r + 1, col);
        g.fill(x - r, y - r, x - r + 1, y - r + 6, col);
        g.fill(x + r - 5, y - r, x + r + 1, y - r + 1, col);
        g.fill(x + r, y - r, x + r + 1, y - r + 6, col);
        g.fill(x - r, y + r, x - r + 6, y + r + 1, col);
        g.fill(x - r, y + r - 5, x - r + 1, y + r + 1, col);
        g.fill(x + r - 5, y + r, x + r + 1, y + r + 1, col);
        g.fill(x + r, y + r - 5, x + r + 1, y + r + 1, col);
        if (progress >= 1f) g.fill(x - 1, y - 1, x + 2, y + 2, col);
    }

    private static void heliReticle(GuiGraphics g, Minecraft mc, LocalPlayer p, HelicopterEntity heli, int cx, int cy, float pt) {
        // where the gun points
        float gunYaw = Mth.rotLerp(pt, heli.yRotO, heli.getYRot()) + Mth.lerp(pt, heli.prevChinYaw, heli.getChinYaw());
        float dyaw = Mth.wrapDegrees(gunYaw - p.getViewYRot(pt));
        float dpitch = Mth.lerp(pt, heli.prevChinPitch, heli.getChinPitch()) - p.getViewXRot(pt);
        double f = (g.guiHeight() / 2.0) / Math.tan(Math.toRadians(mc.options.fov().get() / 2.0));
        if (Math.abs(dyaw) < 70 && Math.abs(dpitch) < 70) {
            int gx = cx + (int) Math.round(Math.tan(Math.toRadians(dyaw)) * f), gy = cy + (int) Math.round(Math.tan(Math.toRadians(dpitch)) * f);
            g.fill(gx - 6, gy, gx - 2, gy + 1, 0xE0A0FFA0);
            g.fill(gx + 3, gy, gx + 7, gy + 1, 0xE0A0FFA0);
            g.fill(gx, gy - 6, gx + 1, gy - 2, 0xE0A0FFA0);
            g.fill(gx, gy + 3, gx + 1, gy + 7, 0xE0A0FFA0);
        }
        // pilot's sight: a circle with a centre dot
        int col = 0xC0E0FFE0;
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            int x = cx + (int) Math.round(Math.cos(a) * 18), y = cy + (int) Math.round(Math.sin(a) * 18);
            g.fill(x, y, x + 1, y + 1, col);
        }
        g.fill(cx, cy, cx + 1, cy + 1, col);
        int lock = heli.getLockTarget();
        if (lock >= 0 && heli.getWeapon().guide == com.gunsmod.entity.HeliType.Guide.LOCK) {
            Entity t = mc.level.getEntity(lock);
            if (t != null) {
                int[] s = project(mc, p, t.getPosition(pt).add(0, t.getBbHeight() * 0.5, 0), cx, cy, pt, g.guiHeight());
                if (s != null) lockBox(g, s[0], s[1], heli.getLockProgress() / (float) HelicopterEntity.LOCK_TICKS, (p.tickCount / 3) % 2 == 0);
            }
        }
    }

    /** F-16 head-up display: pitch ladder, flight path marker / gun pipper, heading tape, missile lock box */
    private static void jetReticle(GuiGraphics g, Minecraft mc, LocalPlayer p, JetEntity jet, int cx, int cy, float pt) {
        int col = 0xD040FF70;
        double f = (g.guiHeight() / 2.0) / Math.tan(Math.toRadians(mc.options.fov().get() / 2.0));
        float viewX = p.getViewXRot(pt), viewY = p.getViewYRot(pt);
        // pitch ladder every 10 degrees (dashed below the horizon)
        for (int k = -60; k <= 60; k += 10) {
            float dp = -k - viewX;
            if (Math.abs(dp) > 40) continue;
            int y = cy + (int) Math.round(Math.tan(Math.toRadians(dp)) * f);
            int w = k == 0 ? 70 : 34, gap = k == 0 ? 14 : 22;
            if (k < 0) {
                for (int x = gap; x < gap + w; x += 6) {
                    g.fill(cx - x - 3, y, cx - x, y + 1, col);
                    g.fill(cx + x, y, cx + x + 3, y + 1, col);
                }
            } else {
                g.fill(cx - gap - w, y, cx - gap, y + 1, col);
                g.fill(cx + gap, y, cx + gap + w, y + 1, col);
            }
            if (k != 0) {
                String lbl = String.valueOf(Math.abs(k));
                g.drawString(mc.font, lbl, cx + gap + w + 3, y - 4, col, false);
            }
        }
        // flight path marker where the nose points: the gun shoots there
        float dyaw = Mth.wrapDegrees(Mth.rotLerp(pt, jet.yRotO, jet.getYRot()) - viewY);
        float dpitch = -Mth.lerp(pt, jet.prevPitch, jet.visPitch) - viewX;
        if (Math.abs(dyaw) < 70 && Math.abs(dpitch) < 70) {
            int gx = cx + (int) Math.round(Math.tan(Math.toRadians(dyaw)) * f), gy = cy + (int) Math.round(Math.tan(Math.toRadians(dpitch)) * f);
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI * 2 / 16;
                int x = gx + (int) Math.round(Math.cos(a) * 5), y = gy + (int) Math.round(Math.sin(a) * 5);
                g.fill(x, y, x + 1, y + 1, col);
            }
            g.fill(gx - 13, gy, gx - 5, gy + 1, col);
            g.fill(gx + 6, gy, gx + 14, gy + 1, col);
            g.fill(gx, gy - 10, gx + 1, gy - 5, col);
            g.fill(gx, gy, gx + 1, gy + 1, 0xFFFFFFFF);
        }
        // heading tape
        int hy = 22;
        float hdg = Mth.wrapDegrees(viewY + 180f);
        for (int d = -30; d <= 30; d += 5) {
            float h = hdg + d;
            int x = cx + (int) (d * 3.2f);
            int mark = Math.round(h);
            boolean big = Math.floorMod(Math.round(h), 10) == 0;
            g.fill(x, hy, x + 1, hy + (big ? 6 : 3), col);
            if (big) {
                String t = String.format("%03d", Math.floorMod(mark, 360));
                g.drawString(mc.font, t, x - mc.font.width(t) / 2, hy + 8, col, false);
            }
        }
        g.fill(cx, hy - 4, cx + 1, hy, 0xFFFFFFFF);
        int lock = jet.getLockTarget();
        if (lock >= 0) {
            Entity t = mc.level.getEntity(lock);
            if (t != null) {
                int[] sp = project(mc, p, t.getPosition(pt).add(0, t.getBbHeight() * 0.5, 0), cx, cy, pt, g.guiHeight());
                if (sp != null) lockBox(g, sp[0], sp[1], jet.getLockProgress() / (float) JetEntity.LOCK_TICKS, (p.tickCount / 3) % 2 == 0);
            }
        }
    }

    private static void carReticle(GuiGraphics g, Minecraft mc, LocalPlayer p, ArmoredCarEntity car, int cx, int cy, float pt) {
        float gunYaw = Mth.rotLerp(pt, car.yRotO, car.getYRot()) + Mth.rotLerp(pt, car.prevTurret, car.getTurretYaw());
        float dyaw = Mth.wrapDegrees(gunYaw - p.getViewYRot(pt));
        float dpitch = Mth.lerp(pt, car.prevGun, car.getGunPitch()) - p.getViewXRot(pt);
        double f = (g.guiHeight() / 2.0) / Math.tan(Math.toRadians(mc.options.fov().get() / 2.0));
        boolean ok = Math.abs(dyaw) < 80 && Math.abs(dpitch) < 80;
        int gx = cx + (ok ? (int) Math.round(Math.tan(Math.toRadians(dyaw)) * f) : 0);
        int gy = cy + (ok ? (int) Math.round(Math.tan(Math.toRadians(dpitch)) * f) : 0);
        int col = car.getReload() > 0 ? 0xE0FFA030 : 0xE040FF60;
        g.fill(cx, cy, cx + 1, cy + 1, 0x90FFFFFF);
        if (car.kind.antiAir()) {
            // anti-aircraft sight: ring with lead marks
            for (int i = 0; i < 40; i++) {
                double a = i * Math.PI * 2 / 40;
                int x = gx + (int) Math.round(Math.cos(a) * 22), y = gy + (int) Math.round(Math.sin(a) * 22);
                g.fill(x, y, x + 1, y + 1, col);
            }
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2;
                int x = gx + (int) Math.round(Math.cos(a) * 11), y = gy + (int) Math.round(Math.sin(a) * 11);
                g.fill(x - 1, y - 1, x + 1, y + 1, col);
            }
            int tr = car.getTrack();
            if (tr >= 0) {
                Entity t = mc.level.getEntity(tr);
                if (t != null) {
                    int[] s = project(mc, p, t.getPosition(pt).add(0, t.getBbHeight() * 0.5, 0), cx, cy, pt, g.guiHeight());
                    if (s != null) lockBox(g, s[0], s[1], car.getLockProgress() / (float) ArmoredCarEntity.LOCK_TICKS, (p.tickCount / 3) % 2 == 0);
                }
            }
        } else {
            g.fill(gx - 14, gy, gx - 4, gy + 1, col);
            g.fill(gx + 5, gy, gx + 15, gy + 1, col);
            g.fill(gx, gy + 5, gx + 1, gy + 13, col);
            for (int i = 1; i <= 3; i++) g.fill(gx - 2 - i, gy + 4 + i * 3, gx + 3 + i, gy + 5 + i * 3, col);
            g.fill(gx - 1, gy - 1, gx + 2, gy + 2, col);
        }
    }

    private static void hitMarker(GuiGraphics g, int cx, int cy) {
        if (GunClient.hitMarker <= 0) return;
        int col = GunClient.hitKill ? 0xFFFF3030 : 0xFFFFFFFF;
        int a = Math.min(255, GunClient.hitMarker * 40);
        col = (a << 24) | (col & 0xFFFFFF);
        g.pose().pushPose();
        g.pose().translate(cx + 0.5f, cy + 0.5f, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        g.fill(-1, -9, 1, -4, col);
        g.fill(-1, 4, 1, 9, col);
        g.fill(-9, -1, -4, 1, col);
        g.fill(4, -1, 9, 1, col);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------------------------ ammo panel
    public static void ammo(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (hidden(mc)) return;
        LocalPlayer p = mc.player;
        if (p.getVehicle() instanceof JeepEntity || p.getVehicle() instanceof VehicleEntity v && v.getControllingPassenger() == p) return;
        ItemStack st = p.getMainHandItem();
        if (!(st.getItem() instanceof GunItem gun)) return;
        Font f = mc.font;
        GunType t = gun.type;
        int ammo = GunItem.ammo(st);
        int mag = GunItem.attachments(st).magSize(t);
        int reserve = p.getAbilities().instabuild || BattleHud.team() >= 0 ? -1 : GunLogic.countAmmo(p, t.ammo);
        int w = 118, h = 46;
        int x = g.guiWidth() - w - 6, y = g.guiHeight() - h - 6;
        g.blitSprite(PANEL, x, y, w, h);
        g.drawString(f, st.getHoverName(), x + 6, y + 5, 0xFFE8D9A0, true);
        String big = String.valueOf(ammo);
        g.pose().pushPose();
        g.pose().translate(x + 6, y + 18, 0);
        g.pose().scale(2f, 2f, 1f);
        int col = ammo == 0 ? 0xFFFF4A3A : ammo <= mag / 4 ? 0xFFFFC040 : 0xFFFFFFFF;
        g.drawString(f, big, 0, 0, col, true);
        g.pose().popPose();
        String res = "/ " + (reserve < 0 ? "∞" : String.valueOf(reserve));
        g.drawString(f, res, x + 10 + f.width(big) * 2, y + 26, 0xFFB0B8A8, true);
        Component mode = Component.translatable("gunsmod.mode." + t.mode.name().toLowerCase());
        g.drawString(f, mode, x + w - 6 - f.width(mode), y + 26, 0xFF8FD18F, true);
        // bullets strip
        int bars = Math.min(mag, 30);
        int bw = Math.max(1, (w - 12) / Math.max(1, bars) - 1);
        int filled = (int) Math.ceil(ammo / (float) mag * bars);
        for (int i = 0; i < bars; i++) {
            int bx = x + 6 + i * (bw + 1);
            g.fill(bx, y + 38, bx + bw, y + 41, i < filled ? 0xFFE0B040 : 0x60FFFFFF);
        }
        Long end = st.get(Reg.RELOAD.get());
        if (end != null && mc.level != null) {
            float total = GunItem.attachments(st).reloadTicks(t);
            float prog = Mth.clamp(1f - (end - mc.level.getGameTime() - dt.getGameTimeDeltaPartialTick(true)) / total, 0f, 1f);
            int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2 + 18;
            g.fill(cx - 30, cy, cx + 30, cy + 4, 0xA0000000);
            g.fill(cx - 30, cy, cx - 30 + (int) (60 * prog), cy + 4, 0xFFE0B040);
            Component rl = Component.translatable("gunsmod.hud.reloading");
            g.drawString(f, rl, cx - f.width(rl) / 2, cy + 6, 0xFFFFFFFF, true);
        } else if (ammo == 0) {
            Component rl = reserve == 0 ? Component.translatable("gunsmod.hud.no_ammo") : Component.translatable("gunsmod.hud.reload", GunClient.RELOAD.getTranslatedKeyMessage());
            int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2 + 18;
            if ((GunClient.ticks / 8) % 2 == 0) g.drawString(f, rl, cx - f.width(rl) / 2, cy, 0xFFFF5040, true);
        }
        Attachment sc = GunItem.attachments(st).get(Attachment.Slot.SCOPE);
        if (sc != null && GunClient.aim(dt.getGameTimeDeltaPartialTick(true)) > 0.6f && !sc.overlayScope()) {
            // red dot / holographic reticle drawn at the centre
            int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
            if (sc == Attachment.RED_DOT) g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFF2020);
            else if (sc == Attachment.HOLO_SIGHT) {
                for (int i = 0; i < 20; i++) {
                    double ang = i * Math.PI * 2 / 20;
                    g.fill(cx + (int) Math.round(Math.cos(ang) * 7), cy + (int) Math.round(Math.sin(ang) * 7), cx + (int) Math.round(Math.cos(ang) * 7) + 1, cy + (int) Math.round(Math.sin(ang) * 7) + 1, 0xC0FF3030);
                }
                g.fill(cx, cy, cx + 1, cy + 1, 0xFFFF3030);
            } else if (sc == Attachment.ACOG) {
                g.fill(cx, cy - 1, cx + 1, cy + 6, 0xFFFF3030);
                g.fill(cx - 3, cy + 2, cx + 4, cy + 3, 0xFFFF3030);
            }
        }
    }

    // ------------------------------------------------------------------------------------ vehicle
    public static void vehicle(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (hidden(mc)) return;
        if (mc.player.getVehicle() instanceof TankEntity tank) {
            tankHud(g, mc, tank);
            return;
        }
        if (mc.player.getVehicle() instanceof HelicopterEntity heli) {
            heliHud(g, mc, heli);
            return;
        }
        if (mc.player.getVehicle() instanceof ArmoredCarEntity car) {
            carHud(g, mc, car);
            return;
        }
        if (mc.player.getVehicle() instanceof JetEntity jet) {
            jetHud(g, mc, jet);
            return;
        }
        if (!(mc.player.getVehicle() instanceof JeepEntity jeep)) return;
        Font f = mc.font;
        int s = 92;
        int x = g.guiWidth() / 2 - 91 - s - 8, y = g.guiHeight() - s - 4;
        if (x < 4) {
            x = 4;
            y = g.guiHeight() - s - 44;
        }
        double kmh = Math.abs(jeep.getDisplaySpeed()) * 20 * 3.6;
        RenderSystem.enableBlend();
        g.blit(GAUGE, x, y, 0, 0, s, s, s, s);
        // needle: 0..60 km/h over 240 degrees starting at 150 degrees
        float ang = 150f + (float) Math.min(62, kmh) * 4f;
        g.pose().pushPose();
        g.pose().translate(x + s / 2f, y + s / 2f, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(ang));
        g.fill(-2, -1, (int) (s * 0.40f), 1, 0xFFFF3A2A);
        g.fill(-4, -3, 4, 3, 0xFF202020);
        g.pose().popPose();
        String sp = String.format("%d", Math.round(kmh));
        g.pose().pushPose();
        g.pose().translate(x + s / 2f - f.width(sp), y + s * 0.62f, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(f, sp, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        String gear = jeep.getDisplaySpeed() < -0.005 ? "R" : Math.abs(jeep.getDisplaySpeed()) < 0.005 ? "N" : "D";
        g.drawString(f, gear, x + s - 14, y + s - 14, gear.equals("R") ? 0xFFFF6040 : 0xFF8FE08F, true);
        RenderSystem.disableBlend();
        // health + seat
        int bx = x + s + 6, by = g.guiHeight() - 30;
        int bw = 80;
        float hp = jeep.getHealth() / jeep.getMaxHealth();
        g.blitSprite(PANEL, bx - 3, by - 16, bw + 6, 30);
        g.drawString(f, Component.translatable("gunsmod.seat." + jeep.seatOf(mc.player)), bx, by - 12, 0xFFE8D9A0, true);
        g.fill(bx, by, bx + bw, by + 4, 0x80000000);
        g.fill(bx, by, bx + (int) (bw * hp), by + 4, hp > 0.5f ? 0xFF60D060 : hp > 0.25f ? 0xFFE0B040 : 0xFFE04030);
        if (jeep.isGunner(mc.player) && jeep.tow) {
            float k = jeep.towReloadFraction();
            int hx = g.guiWidth() / 2 - 40, hy = g.guiHeight() / 2 + 22;
            g.fill(hx, hy, hx + 80, hy + 3, 0x80000000);
            g.fill(hx, hy, hx + (int) (80 * (1f - k)), hy + 3, k > 0 ? 0xFFFFA030 : 0xFF60FF80);
            Component lbl = Component.translatable(k > 0 ? "gunsmod.hud.tow_loading" : "gunsmod.hud.tow_ready");
            g.drawString(f, lbl, g.guiWidth() / 2 - f.width(lbl) / 2, hy + 5, k > 0 ? 0xFFFFA030 : 0xFF60FF80, true);
        } else if (jeep.isGunner(mc.player)) {
            float heat = jeep.getHeat() / 100f;
            int hx = g.guiWidth() / 2 - 40, hy = g.guiHeight() / 2 + 22;
            g.fill(hx, hy, hx + 80, hy + 3, 0x80000000);
            g.fill(hx, hy, hx + (int) (80 * heat), hy + 3, jeep.isOverheated() ? 0xFFFF3020 : 0xFFFFA030);
            Component lbl = Component.translatable(jeep.isOverheated() ? "gunsmod.hud.overheat" : "gunsmod.hud.heat");
            g.drawString(f, lbl, g.guiWidth() / 2 - f.width(lbl) / 2, hy + 5, jeep.isOverheated() ? 0xFFFF5040 : 0xFFD0D0D0, true);
        }
        Component hint = Component.translatable(jeep.isDriver(mc.player) ? "gunsmod.hud.drive_hint" : "gunsmod.hud.seat_hint",
                GunClient.SEAT.getTranslatedKeyMessage(), GunClient.HORN.getTranslatedKeyMessage());
        g.drawString(f, hint, g.guiWidth() / 2 - f.width(hint) / 2, 6, 0xA0FFFFFF, true);
    }

    private static int gauge(GuiGraphics g, Minecraft mc, double kmh, double maxKmh, String gear) {
        Font f = mc.font;
        int s = 92;
        int x = g.guiWidth() / 2 - 91 - s - 8, y = g.guiHeight() - s - 4;
        if (x < 4) {
            x = 4;
            y = g.guiHeight() - s - 44;
        }
        RenderSystem.enableBlend();
        g.blit(GAUGE, x, y, 0, 0, s, s, s, s);
        // the dial reads 0..60; faster vehicles scale onto it
        float ang = 150f + (float) Math.min(62, kmh * 60.0 / maxKmh) * 4f;
        g.pose().pushPose();
        g.pose().translate(x + s / 2f, y + s / 2f, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(ang));
        g.fill(-2, -1, (int) (s * 0.40f), 1, 0xFFFF3A2A);
        g.fill(-4, -3, 4, 3, 0xFF202020);
        g.pose().popPose();
        String sp = String.format("%d", Math.round(kmh));
        g.pose().pushPose();
        g.pose().translate(x + s / 2f - f.width(sp), y + s * 0.62f, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(f, sp, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        if (gear != null) g.drawString(f, gear, x + s - 14, y + s - 14, gear.equals("R") ? 0xFFFF6040 : 0xFF8FE08F, true);
        RenderSystem.disableBlend();
        return x + s + 6;
    }

    private static void panel(GuiGraphics g, Minecraft mc, int bx, Component title, float hp, Component extra, int extraCol) {
        Font f = mc.font;
        int by = g.guiHeight() - 30, bw = 96;
        g.blitSprite(PANEL, bx - 3, by - 28, bw + 6, 42);
        g.drawString(f, title, bx, by - 24, 0xFFE8D9A0, true);
        if (extra != null) g.drawString(f, extra, bx, by - 12, extraCol, true);
        g.fill(bx, by, bx + bw, by + 4, 0x80000000);
        g.fill(bx, by, bx + (int) (bw * hp), by + 4, hp > 0.5f ? 0xFF60D060 : hp > 0.25f ? 0xFFE0B040 : 0xFFE04030);
    }

    private static void tankHud(GuiGraphics g, Minecraft mc, TankEntity tank) {
        boolean commander = tank.getControllingPassenger() == mc.player;
        double v = tank.getDisplaySpeed();
        int bx = gauge(g, mc, Math.abs(v) * 20 * 3.6, 60, v < -0.005 ? "R" : Math.abs(v) < 0.005 ? "N" : "D");
        Component gun = Component.translatable(tank.getReload() > 0 ? "gunsmod.hud.gun_loading" : "gunsmod.hud.gun_ready");
        panel(g, mc, bx, Component.translatable("gunsmod.seat.commander"), tank.getHealth() / tank.getMaxHealth(), gun,
                tank.getReload() > 0 ? 0xFFFFA030 : 0xFF60FF80);
        // turret direction: hull from above, gun line
        int ix = bx + 106, iy = g.guiHeight() - 34;
        g.fill(ix - 6, iy - 10, ix + 6, iy + 10, 0x80303830);
        g.fill(ix - 5, iy - 9, ix + 5, iy + 9, 0xA0607050);
        g.pose().pushPose();
        g.pose().translate(ix + 0.5f, iy + 0.5f, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(tank.getTurretYaw()));
        g.fill(-3, -3, 4, 4, 0xFFB0C090);
        g.fill(0, -16, 1, -3, 0xFFE0F0C0);
        g.pose().popPose();
        if (commander) {
            Component hint = Component.translatable("gunsmod.hud.tank_hint");
            g.drawString(mc.font, hint, g.guiWidth() / 2 - mc.font.width(hint) / 2, 6, 0xA0FFFFFF, true);
        }
    }

    private static void heliHud(GuiGraphics g, Minecraft mc, HelicopterEntity heli) {
        boolean pilot = heli.getControllingPassenger() == mc.player;
        int bx = gauge(g, mc, heli.airSpeed * 20 * 3.6, 120, null);
        HeliType.Weapon w = heli.getWeapon();
        Component wpn = heli.getReload() > 0 ? Component.translatable("gunsmod.hud.weapon_loading", Component.translatable("gunsmod.weapon." + w.id))
                : Component.translatable("gunsmod.hud.weapon_ammo", Component.translatable("gunsmod.weapon." + w.id), heli.getAmmo());
        int seat = heli.seatOf(mc.player);
        panel(g, mc, bx, Component.translatable(seat == 0 ? "gunsmod.seat.pilot" : seat == 1 ? "gunsmod.seat.heli_gunner" : "gunsmod.seat.passenger"),
                heli.getHealth() / heli.getMaxHealth(), wpn, heli.getReload() > 0 ? 0xFFFFA030 : 0xFFFFFFFF);
        Font f = mc.font;
        int x = bx + 106, y = g.guiHeight() - 67;
        double alt = heli.heightAboveGround();
        double vs = heli.climbRate * 20;
        g.blitSprite(PANEL, x - 3, y - 3, 84, 61);
        g.drawString(f, Component.translatable("gunsmod.hud.altitude", String.format("%.0f", alt)), x, y, 0xFFE8E8E8, true);
        g.drawString(f, Component.translatable("gunsmod.hud.climb", String.format("%+.1f", vs)), x, y + 11, vs < -6 ? 0xFFFF6040 : 0xFFC8D8C8, true);
        g.drawString(f, Component.translatable("gunsmod.hud.rotor", Math.round(heli.rotor * 100)), x, y + 22, heli.rotor < 0.9f ? 0xFFFFC040 : 0xFF80E080, true);
        g.drawString(f, Component.translatable("gunsmod.hud.flares", heli.getFlares()), x, y + 33, heli.getFlares() > 0 ? 0xFFFFE0A0 : 0xFFFF6040, true);
        int warn = heli.getWarning();
        boolean blink = (mc.player.tickCount / 4) % 2 == 0;
        if (warn > 0 && blink) {
            Component wtxt = Component.translatable(warn == 2 ? "gunsmod.hud.missile_warning" : "gunsmod.hud.radar_warning");
            g.pose().pushPose();
            g.pose().translate(g.guiWidth() / 2f - f.width(wtxt), g.guiHeight() / 2f - 46, 0);
            g.pose().scale(2f, 2f, 1f);
            g.drawString(f, wtxt, 0, 0, warn == 2 ? 0xFFFF3020 : 0xFFFFC020, true);
            g.pose().popPose();
        }
        if (alt < 6 && heli.climbRate < -0.3 && blink) {
            Component wt = Component.translatable("gunsmod.hud.pull_up");
            g.drawString(f, wt, g.guiWidth() / 2 - f.width(wt) / 2, g.guiHeight() / 2 + 30, 0xFFFF4030, true);
        }
        if (pilot) {
            Component hint = Component.translatable("gunsmod.hud.heli_hint", GunClient.RELOAD.getTranslatedKeyMessage(), GunClient.FLARES.getTranslatedKeyMessage());
            g.drawString(f, hint, g.guiWidth() / 2 - f.width(hint) / 2, 6, 0xA0FFFFFF, true);
        }
    }

    private static void jetHud(GuiGraphics g, Minecraft mc, JetEntity jet) {
        Font f = mc.font;
        int w = g.guiWidth(), h = g.guiHeight(), cy = h / 2;
        int col = 0xE040FF70;
        boolean blink = (mc.player.tickCount / 4) % 2 == 0;
        // speed (left) and altitude (right) boxes
        double kmh = jet.getAirSpeed() * 20 * 3.6;
        String spd = String.valueOf(Math.round(kmh));
        int lx = w / 2 - 120, rx = w / 2 + 92;
        g.fill(lx - 2, cy - 8, lx + 30, cy + 8, 0x80001000);
        g.drawString(f, spd, lx + 26 - f.width(spd), cy - 4, col, false);
        g.drawString(f, "KMH", lx, cy + 10, col, false);
        String alt = String.valueOf(Math.round(jet.heightAboveGround()));
        g.fill(rx - 2, cy - 8, rx + 30, cy + 8, 0x80001000);
        g.drawString(f, alt, rx + 26 - f.width(alt), cy - 4, col, false);
        g.drawString(f, "ALT", rx, cy + 10, col, false);
        // throttle bar with the reheat mark
        int tx = w / 2 - 140, ty = cy + 40, th = 60;
        float thr = jet.getThrottle();
        g.fill(tx, ty - th, tx + 5, ty, 0x80000000);
        g.fill(tx, ty - (int) (th * thr), tx + 5, ty, jet.isAfterburner() ? 0xFFFF9020 : 0xFF40D060);
        g.drawString(f, jet.isAfterburner() ? "AB" : Math.round(thr * 100) + "%", tx - 2, ty + 3, jet.isAfterburner() ? 0xFFFFA030 : col, false);
        // gear lights
        boolean down = jet.gear < 0.02f, moving = jet.gear > 0.02f && jet.gear < 0.98f;
        int gx = w / 2 + 132;
        for (int i = 0; i < 3; i++) {
            int c = down ? 0xFF30E040 : moving ? 0xFFFF4030 : 0x60404040;
            g.fill(gx + i * 7, cy + 34, gx + i * 7 + 5, cy + 39, c);
        }
        g.drawString(f, Component.translatable(down ? "gunsmod.hud.gear_down" : moving ? "gunsmod.hud.gear_moving" : "gunsmod.hud.gear_up"), gx - 4, cy + 42,
                down ? 0xFF30E040 : 0xFFB0B0B0, true);
        // stores, gun, flares, health
        int bx = 6, by = h - 64;
        g.blitSprite(PANEL, bx - 3, by - 4, 126, 58);
        int wpn = jet.getWeapon();
        Component wn = Component.translatable(wpn == 0 ? "gunsmod.weapon.maverick" : "gunsmod.weapon.aim9");
        g.drawString(f, Component.translatable("gunsmod.hud.weapon_ammo", wn, jet.missilesLeft(wpn)), bx, by, 0xFFFFFFFF, true);
        g.drawString(f, Component.translatable("gunsmod.hud.gun_rounds", jet.getGunRounds()), bx, by + 11, 0xFFE0E0E0, true);
        g.drawString(f, Component.translatable("gunsmod.hud.flares", jet.getFlares()), bx, by + 22, jet.getFlares() > 0 ? 0xFFFFE0A0 : 0xFFFF6040, true);
        float hp = jet.getHealth() / jet.getMaxHealth();
        g.fill(bx, by + 36, bx + 118, by + 40, 0x80000000);
        g.fill(bx, by + 36, bx + (int) (118 * hp), by + 40, hp > 0.5f ? 0xFF60D060 : hp > 0.25f ? 0xFFE0B040 : 0xFFE04030);
        // warnings
        int warn = jet.getWarning();
        Component big = null;
        int bigCol = 0xFFFF3020;
        if (warn == 2) big = Component.translatable("gunsmod.hud.missile_warning");
        else if (!jet.onGround() && jet.getAirSpeed() < JetEntity.STALL * 1.1) big = Component.translatable("gunsmod.hud.stall");
        else if (!jet.onGround() && jet.heightAboveGround() < 25 && jet.velocity.y < -0.4) big = Component.translatable("gunsmod.hud.pull_up");
        else if (warn == 1) {
            big = Component.translatable("gunsmod.hud.radar_warning");
            bigCol = 0xFFFFC020;
        }
        if (big != null && blink) {
            g.pose().pushPose();
            g.pose().translate(w / 2f - f.width(big), cy - 70, 0);
            g.pose().scale(2f, 2f, 1f);
            g.drawString(f, big, 0, 0, bigCol, true);
            g.pose().popPose();
        }
        if (jet.onGround() && jet.getAirSpeed() < 0.2) {
            Component hint = Component.translatable("gunsmod.hud.jet_hint", GunClient.RELOAD.getTranslatedKeyMessage(), GunClient.FLARES.getTranslatedKeyMessage());
            g.drawString(f, hint, w / 2 - f.width(hint) / 2, 6 + 40, 0xC0FFFFFF, true);
        }
    }

    private static void carHud(GuiGraphics g, Minecraft mc, ArmoredCarEntity car) {
        boolean commander = car.getControllingPassenger() == mc.player;
        double v = car.getDisplaySpeed();
        int bx = gauge(g, mc, Math.abs(v) * 20 * 3.6, 80, v < -0.005 ? "R" : Math.abs(v) < 0.005 ? "N" : "D");
        Component main;
        int col;
        if (car.kind.antiAir()) {
            main = car.getReload() > 0 ? Component.translatable("gunsmod.hud.weapon_loading", Component.translatable("gunsmod.weapon.sam"))
                    : Component.translatable("gunsmod.hud.weapon_ammo", Component.translatable("gunsmod.weapon.sam"), car.getAmmo());
        } else {
            String wk = car.kind.artillery() ? "gunsmod.weapon.mlrs" : car.kind.cram() ? "gunsmod.weapon.cram20" : "gunsmod.weapon.cannon30";
            main = car.getReload() > 0 ? Component.translatable("gunsmod.hud.weapon_loading", Component.translatable(wk))
                    : Component.translatable("gunsmod.hud.weapon_ammo", Component.translatable(wk), car.getAmmo());
        }
        col = car.getReload() > 0 ? 0xFFFFA030 : 0xFFFFFFFF;
        panel(g, mc, bx, Component.translatable(commander ? "gunsmod.seat.commander" : "gunsmod.seat.passenger"), car.getHealth() / car.getMaxHealth(), main, col);
        if (car.kind.antiAir()) radarScope(g, mc, car, bx + 106);
        if (commander) {
            String hk = car.kind.antiAir() ? "gunsmod.hud.pantsir_hint" : car.kind.artillery() ? "gunsmod.hud.mlrs_hint"
                    : car.kind.cram() ? "gunsmod.hud.cram_hint" : "gunsmod.hud.btr_hint";
            Component hint = Component.translatable(hk);
            g.drawString(mc.font, hint, g.guiWidth() / 2 - mc.font.width(hint) / 2, 6, 0xA0FFFFFF, true);
            if (car.kind.artillery()) {
                // fire control: range to the point under the crosshair, or why there is none
                float range = car.getArtyRange();
                Component rc = range < 0 ? Component.translatable("gunsmod.hud.mlrs_no_point")
                        : Component.translatable("gunsmod.hud.mlrs_range", (int) range);
                int col2 = range < 0 ? 0xFFFFA030 : range > 330 ? 0xFFFF5040 : 0xFF60FF80;
                g.drawString(mc.font, rc, g.guiWidth() / 2 - mc.font.width(rc) / 2, g.guiHeight() / 2 + 14, col2, true);
            }
        }
    }

    /** Pantsir search radar: plan position display, vehicle heading up, helicopters as blips, the tracked one boxed */
    private static void radarScope(GuiGraphics g, Minecraft mc, ArmoredCarEntity car, int x0) {
        int r = 36;
        int cx = x0 + r + 2, cy = g.guiHeight() - r - 8;
        g.fill(cx - r - 2, cy - r - 2, cx + r + 3, cy + r + 3, 0xC0081208);
        for (int ring = 1; ring <= 3; ring++) {
            int rr = r * ring / 3;
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI * 2 / 48;
                int x = cx + (int) Math.round(Math.cos(a) * rr), y = cy + (int) Math.round(Math.sin(a) * rr);
                g.fill(x, y, x + 1, y + 1, 0x8030C040);
            }
        }
        double sweep = Math.toRadians(car.radarAngle + car.getTurretYaw());
        for (int i = 0; i < r; i++) {
            int x = cx + (int) Math.round(Math.sin(sweep) * i), y = cy - (int) Math.round(Math.cos(sweep) * i);
            g.fill(x, y, x + 1, y + 1, 0xC060FF70);
        }
        double range = ArmoredCarEntity.RADAR_RANGE;
        float yaw = car.getYRot();
        for (VehicleEntity h : mc.level.getEntitiesOfClass(VehicleEntity.class, car.getBoundingBox().inflate(range, range, range))) {
            double dx = h.getX() - car.getX(), dz = h.getZ() - car.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > range || !(h instanceof com.gunsmod.entity.Aircraft a) || !a.isAirborne()) continue;
            // bearing from the hull heading: positive = to the right
            double rel = Math.toRadians(Math.toDegrees(Math.atan2(dz, dx)) - 90 - yaw);
            double k = dist / range * r;
            int x = cx + (int) Math.round(Math.sin(rel) * k), y = cy - (int) Math.round(Math.cos(rel) * k);
            boolean enemy = h.getBotTeam() != car.getBotTeam();
            int c = enemy ? 0xFFFF4030 : 0xFF40FF60;
            g.fill(x - 1, y - 1, x + 2, y + 2, c);
            if (h.getId() == car.getTrack()) {
                int bc = car.isLocked() ? 0xFFFF3030 : 0xFFFFD040;
                g.fill(x - 4, y - 4, x + 5, y - 3, bc);
                g.fill(x - 4, y + 4, x + 5, y + 5, bc);
                g.fill(x - 4, y - 4, x - 3, y + 5, bc);
                g.fill(x + 4, y - 4, x + 5, y + 5, bc);
            }
        }
        g.fill(cx, cy - 3, cx + 1, cy + 4, 0xFFFFFFFF);
        Component lbl = Component.translatable(car.isLocked() ? "gunsmod.hud.radar_locked" : car.getTrack() >= 0 ? "gunsmod.hud.radar_tracking" : "gunsmod.hud.radar_search");
        g.drawString(mc.font, lbl, cx - mc.font.width(lbl) / 2, cy - r - 12, car.isLocked() ? 0xFFFF4030 : 0xFF80FF90, true);
    }

    // ------------------------------------------------------------------------------------ soldier info
    public static void botInfo(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (hidden(mc) || !(mc.hitResult instanceof EntityHitResult eh)) return;
        Entity e = eh.getEntity();
        if (!(e instanceof BotEntity bot)) return;
        Font f = mc.font;
        int w = 120, x = g.guiWidth() / 2 + 12, y = g.guiHeight() / 2 - 30;
        g.blitSprite(PANEL, x, y, w, 40);
        g.drawString(f, bot.getDisplayName(), x + 5, y + 4, 0xFFFFFFFF, true);
        ItemStack gun = bot.getMainHandItem();
        g.drawString(f, gun.isEmpty() ? Component.translatable("gunsmod.hud.unarmed") : gun.getHoverName(), x + 5, y + 15, 0xFFB0B8A8, true);
        float hp = bot.getHealth() / bot.getMaxHealth();
        g.fill(x + 5, y + 28, x + w - 5, y + 32, 0x80000000);
        g.fill(x + 5, y + 28, x + 5 + (int) ((w - 10) * hp), y + 32, bot.getBotTeam().color | 0xFF000000);
        Component state = Component.translatable(bot.isAiming() ? "gunsmod.state.combat" : bot.isPassenger() ? "gunsmod.state.vehicle" : "gunsmod.state.patrol");
        g.drawString(f, state, x + w - 5 - f.width(state), y + 15, bot.isAiming() ? 0xFFFF7060 : 0xFF8FD18F, true);
    }
}
