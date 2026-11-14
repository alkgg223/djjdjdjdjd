package com.realweather.client;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.realweather.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Feeds the realistic water shader (assets/minecraft/shaders/core/rendertype_translucent): on/off, how rough
 * the water is (calm on still days, choppy in storms), and where the sun or moon is for the glints.
 */
public final class WaterShader {
    private WaterShader() {}

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        ShaderInstance shader = GameRenderer.getRendertypeTranslucentShader();
        ClientLevel level = Minecraft.getInstance().level;
        if (shader == null || level == null) {
            return;
        }
        AbstractUniform water = shader.safeGetUniform("RealWater");
        AbstractUniform sun = shader.safeGetUniform("RealSun");
        if (!ClientConfig.REALISTIC_WATER.get()) {
            water.set(0F, 0F, 0F, 0F);
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float rough = Mth.clamp(0.3F + ClientWeather.wind * 0.9F + ClientWeather.camRain * 0.5F + ClientWeather.camBlizzard * 0.4F, 0.2F, 1.6F);
        float angle = level.getTimeOfDay(pt) * (float) (Math.PI * 2);
        float lx = -Mth.sin(angle);
        float ly = Mth.cos(angle);
        float strength;
        if (ly >= 0F) {
            strength = Mth.clamp(ly * 5F, 0F, 1F);
        } else {
            // Night: the moon is opposite the sun and much dimmer.
            lx = -lx;
            ly = -ly;
            strength = Mth.clamp(ly * 5F, 0F, 1F) * 0.25F;
        }
        strength *= 1F - level.getRainLevel(pt) * 0.85F;
        strength *= 1F - ClientWeather.camSand * 0.9F;
        water.set(1F, rough, strength, 0F);
        sun.set(lx, ly, 0F);
    }
}
