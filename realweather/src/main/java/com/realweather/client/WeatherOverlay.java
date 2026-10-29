package com.realweather.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.realweather.RealWeather;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Screen effects: the lightning flash and dust in the eyes during a sandstorm. */
public final class WeatherOverlay {
    private static final ResourceLocation SAND = RealWeather.id("textures/misc/sand_overlay.png");

    private WeatherOverlay() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        float sand = ClientWeather.camSand * (0.3F + 0.7F * ClientWeather.exposure);
        if (sand > 0.01F && mc.options.getCameraType().isFirstPerson()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1F, 1F, 1F, Math.min(0.6F, sand * 0.6F));
            g.blit(SAND, 0, 0, -90, 0F, 0F, w, h, w, h);
            g.setColor(1F, 1F, 1F, 1F);
        }
        float f = LightningFx.flash;
        if (f > 0.01F) {
            int a = (int) (Math.min(1F, f) * 0.55F * 255F);
            g.fill(0, 0, w, h, (a << 24) | 0xE6ECFF);
        }
    }
}
