package com.realweather.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.realweather.RealWeather;
import com.realweather.device.EclipseGlasses;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Looking through eclipse glasses: the black film lets almost nothing through, so the world is black and only the
 * sun shows, as a sharp orange disc (or the crescent the moon leaves of it). The cardboard frame shows round the edge.
 */
public final class GlassesOverlay {
    private static final ResourceLocation DISC = RealWeather.id("textures/environment/disc.png");
    private static final ResourceLocation CORONA = RealWeather.id("textures/environment/corona.png");

    private GlassesOverlay() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson() || !EclipseGlasses.wearing(mc.player)) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, 0xF8020203);
        float x = EclipseRenderer.screenSunX;
        boolean visible = !Float.isNaN(x) && ClientEclipse.sunUp > 0F && mc.level != null && mc.level.getRainLevel(1F) < 0.6F;
        if (!visible && mc.level != null) {
            // No eclipse: the plain sun is still the one thing you can see through them.
            visible = plainSun(mc);
            x = EclipseRenderer.screenSunX;
        }
        if (visible) {
            float y = EclipseRenderer.screenSunY, r = Math.max(2F, EclipseRenderer.screenR);
            RenderSystem.enableBlend();
            if (ClientEclipse.corona > 0.05F) {
                float cs = r * com.realweather.server.Eclipse.MOON / 0.2F;
                RenderSystem.setShaderColor(0.25F, 0.26F, 0.28F, ClientEclipse.corona * 0.6F);
                blit(g, CORONA, x, y, cs);
            }
            if (ClientEclipse.coverage < 0.9995F) {
                RenderSystem.setShaderColor(1F, 0.6F, 0.18F, 1F);
                blit(g, DISC, x, y, r);
            }
            if (ClientEclipse.coverage > 0F) {
                RenderSystem.setShaderColor(0.008F, 0.008F, 0.012F, 1F);
                blit(g, DISC, EclipseRenderer.screenMoonX, EclipseRenderer.screenMoonY, r * com.realweather.server.Eclipse.MOON);
            }
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
        // The cardboard frame at the edges of the view.
        int fw = Math.max(6, w / 28);
        g.fill(0, 0, w, fw, 0xFFE8D7A0);
        g.fill(0, h - fw, w, h, 0xFFE8D7A0);
        g.fill(0, 0, fw, h, 0xFFE8D7A0);
        g.fill(w - fw, 0, w, h, 0xFFE8D7A0);
        g.fill(w / 2 - fw / 2, 0, w / 2 + fw / 2, h, 0xFFE8D7A0);
    }

    private static void blit(GuiGraphics g, ResourceLocation tex, float cx, float cy, float r) {
        int s = Math.max(1, Math.round(r * 2F));
        g.blit(tex, Math.round(cx - r), Math.round(cy - r), s, s, 0F, 0F, 128, 128, 128, 128);
    }

    /** Puts the sun's screen position in place when there is no eclipse (the eclipse renderer does it otherwise). */
    private static boolean plainSun(Minecraft mc) {
        return !Float.isNaN(EclipseRenderer.screenSunX) && mc.level.getRainLevel(1F) < 0.6F && Mth.cos(mc.level.getTimeOfDay(1F) * Mth.TWO_PI) > 0F;
    }
}
