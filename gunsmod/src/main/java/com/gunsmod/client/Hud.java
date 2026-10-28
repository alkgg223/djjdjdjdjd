package com.gunsmod.client;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.JeepEntity;
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

/** HUD layers: ammo counter, dynamic crosshair, hit marker, sniper scope, vehicle speedometer, soldier info. */
public final class Hud {
    private Hud() {}

    private static final ResourceLocation SCOPE = Reg.id("textures/gui/scope.png");
    private static final ResourceLocation GAUGE = Reg.id("textures/gui/speedometer.png");
    private static final ResourceLocation PANEL = Reg.id("panel_dark");

    private static boolean hidden(Minecraft mc) {
        return mc.player == null || mc.options.hideGui || mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
    }

    // ------------------------------------------------------------------------------------ scope overlay
    public static void scope(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (hidden(mc) || !GunClient.scoped()) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int s = Math.min(w, h);
        int x = (w - s) / 2, y = (h - s) / 2;
        RenderSystem.enableBlend();
        g.blit(SCOPE, x, y, 0, 0, s, s, s, s);
        g.fill(0, 0, x, h, 0xFF000000);
        g.fill(x + s, 0, w, h, 0xFF000000);
        g.fill(x, 0, x + s, y, 0xFF000000);
        g.fill(x, y + s, x + s, h, 0xFF000000);
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------------------------ crosshair + hit marker
    public static void crosshair(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (hidden(mc) || !mc.options.getCameraType().isFirstPerson()) return;
        LocalPlayer p = mc.player;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
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
        if (p.getVehicle() instanceof JeepEntity) return;
        ItemStack st = p.getMainHandItem();
        if (!(st.getItem() instanceof GunItem gun)) return;
        Font f = mc.font;
        GunType t = gun.type;
        int ammo = GunItem.ammo(st);
        int mag = GunItem.attachments(st).magSize(t);
        int reserve = p.getAbilities().instabuild ? -1 : GunLogic.countAmmo(p, t.ammo);
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
        if (hidden(mc) || !(mc.player.getVehicle() instanceof JeepEntity jeep)) return;
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
        if (jeep.isGunner(mc.player)) {
            float heat = jeep.getHeat() / 100f;
            int hx = g.guiWidth() / 2 - 40, hy = g.guiHeight() / 2 + 22;
            g.fill(hx, hy, hx + 80, hy + 3, 0x80000000);
            g.fill(hx, hy, hx + (int) (80 * heat), hy + 3, jeep.isOverheated() ? 0xFFFF3020 : 0xFFFFA030);
            Component lbl = Component.translatable(jeep.isOverheated() ? "gunsmod.hud.overheat" : "gunsmod.hud.heat");
            g.drawString(f, lbl, g.guiWidth() / 2 - f.width(lbl) / 2, hy + 5, jeep.isOverheated() ? 0xFFFF5040 : 0xFFD0D0D0, true);
        }
        Component hint = Component.translatable(jeep.isDriver(mc.player) ? "gunsmod.hud.drive_hint" : "gunsmod.hud.seat_hint",
                GunClient.SEAT.getTranslatedKeyMessage(), GunClient.HORN.getTranslatedKeyMessage(), GunClient.ORDER.getTranslatedKeyMessage());
        g.drawString(f, hint, g.guiWidth() / 2 - f.width(hint) / 2, 6, 0xA0FFFFFF, true);
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
        g.drawString(f, Component.translatable("gunsmod.order." + bot.getOrder()), x + w - 5 - f.width(Component.translatable("gunsmod.order." + bot.getOrder())), y + 15, 0xFF8FD18F, true);
    }
}
