package com.gunsmod.client.screen;

import com.gunsmod.Reg;
import com.gunsmod.client.GunGeometry;
import com.gunsmod.item.GunItem;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Small drawing toolkit for the military-styled screens. */
public final class UiKit {
    private UiKit() {}

    public static final ResourceLocation PANEL = Reg.id("panel");
    public static final ResourceLocation DARK = Reg.id("panel_dark");
    public static final ResourceLocation BUTTON = Reg.id("button");
    public static final ResourceLocation BUTTON_HOVER = Reg.id("button_hover");
    public static final ResourceLocation BUTTON_OFF = Reg.id("button_off");
    public static final ResourceLocation BUTTON_RED = Reg.id("button_red");
    public static final ResourceLocation SLOT = Reg.id("slot");
    public static final ResourceLocation HAZARD = Reg.id("hazard");

    public static final int TEXT = 0xFFE8E2C8;
    public static final int ACCENT = 0xFFE0B040;
    public static final int MUTED = 0xFF9AA290;
    public static final int GOOD = 0xFF7FD87F;
    public static final int BAD = 0xFFFF6A5A;

    public record Btn(int id, int x, int y, int w, int h, Component label, boolean enabled, boolean red) {
        public boolean hit(double mx, double my) {
            return enabled && mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.blitSprite(PANEL, x, y, w, h);
    }

    public static void dark(GuiGraphics g, int x, int y, int w, int h) {
        g.blitSprite(DARK, x, y, w, h);
    }

    public static void slot(GuiGraphics g, int x, int y) {
        g.blitSprite(SLOT, x - 1, y - 1, 18, 18);
    }

    public static void button(GuiGraphics g, Font f, Btn b, int mx, int my) {
        boolean hover = b.enabled && mx >= b.x && my >= b.y && mx < b.x + b.w && my < b.y + b.h;
        ResourceLocation sprite = !b.enabled ? BUTTON_OFF : b.red ? BUTTON_RED : hover ? BUTTON_HOVER : BUTTON;
        g.blitSprite(sprite, b.x, b.y, b.w, b.h);
        int tw = f.width(b.label);
        g.drawString(f, b.label, b.x + (b.w - tw) / 2, b.y + (b.h - 8) / 2, b.enabled ? 0xFFFFFFFF : 0xFF8A8A8A, true);
    }

    public static void title(GuiGraphics g, Font f, Component c, int x, int y) {
        g.drawString(f, c, x, y, ACCENT, true);
        g.fill(x, y + 10, x + f.width(c), y + 11, 0x80E0B040);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, float v, int col) {
        g.fill(x, y, x + w, y + 4, 0xA0000000);
        g.fill(x, y, x + Math.round(w * Math.max(0, Math.min(1, v))), y + 4, col);
        g.fill(x, y, x + w, y + 1, 0x30FFFFFF);
    }

    public static void hazard(GuiGraphics g, int x, int y, int w) {
        for (int i = 0; i < w; i += 64) g.blit(Reg.id("textures/gui/sprites/hazard.png"), x + i, y, 0, 0, Math.min(64, w - i), 8, 64, 8);
    }

    /** draw an item as a big rotatable 3D model inside the given box */
    public static void preview(GuiGraphics g, ItemStack stack, int x, int y, int w, int h, float yaw, float pitch, float zoom) {
        if (stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float span = 16f;
        float cx = 8f, cy = 8f, cz = 8f;
        if (stack.getItem() instanceof GunItem gun) {
            GunGeometry.Geo geo = GunGeometry.get(gun.type.id);
            if (geo != null) {
                float[] b = geo.bounds();
                span = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2])) + 4f;
                cx = (b[0] + b[3]) / 2f;
                cy = (b[1] + b[4]) / 2f;
                cz = (b[2] + b[5]) / 2f;
            }
        }
        float scale = Math.min(w, h) * 16f / span * zoom;
        g.enableScissor(x, y, x + w, y + h);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x + w / 2f, y + h / 2f, 220f);
        ps.scale(scale, -scale, scale);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        // NONE context renders the raw model; ItemRenderer adds the -0.5 offset itself
        ps.translate(-(cx / 16f - 0.5f), -(cy / 16f - 0.5f), -(cz / 16f - 0.5f));
        Lighting.setupFor3DItems();
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, g.bufferSource(), mc.level, 0);
        g.flush();
        ps.popPose();
        g.disableScissor();
    }
}
