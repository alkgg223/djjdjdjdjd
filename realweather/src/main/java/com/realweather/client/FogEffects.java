package com.realweather.client;

import com.realweather.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Visibility and sky colour: grey veils of heavy rain, white-outs in blizzards, brown walls of sand. */
public final class FogEffects {
    private FogEffects() {}

    private static boolean applies(Camera camera) {
        ClientLevel level = Minecraft.getInstance().level;
        if (!ClientConfig.FOG.get() || !ClientWeather.active(level) || camera.getFluidInCamera() != FogType.NONE) {
            return false;
        }
        return level.getFluidState(camera.getBlockPosition()).isEmpty();
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!applies(event.getCamera())) {
            return;
        }
        float far = event.getFarPlaneDistance();
        float near = event.getNearPlaneDistance();
        float shelter = 0.35F + 0.65F * ClientWeather.exposure;
        float rain = smooth(0.15F, 1F, ClientWeather.camRain) * shelter;
        float snow = ClientWeather.camSnow * shelter;
        float bliz = (float) Math.pow(ClientWeather.camBlizzard * shelter, 0.6);
        float sand = (float) Math.pow(ClientWeather.camSand * shelter, 0.6);

        float target = far;
        target = Math.min(target, Mth.lerp(rain * 0.6F, far, Math.max(28F, far * 0.4F)));
        target = Math.min(target, Mth.lerp(snow * 0.7F, far, Math.max(22F, far * 0.3F)));
        target = Math.min(target, Mth.lerp(bliz, far, 14F));
        target = Math.min(target, Mth.lerp(sand, far, 12F));
        if (target >= far - 0.05F) {
            return;
        }
        float density = Math.max(bliz, sand);
        float newNear;
        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) {
            newNear = 0F;
        } else {
            newNear = Math.min(near, target * Mth.lerp(density, 0.3F, 0F));
        }
        event.setNearPlaneDistance(newNear);
        event.setFarPlaneDistance(target);
        event.setCanceled(true);
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!applies(event.getCamera())) {
            return;
        }
        float r = event.getRed();
        float g = event.getGreen();
        float b = event.getBlue();
        float bright = Mth.clamp(Math.max(r, Math.max(g, b)) / 0.8F, 0.04F, 1F);
        float shelter = 0.35F + 0.65F * ClientWeather.exposure;

        float rain = Math.min(1F, ClientWeather.camRain * 0.6F + ClientWeather.thunder * ClientWeather.camRain * 0.3F) * shelter;
        r = Mth.lerp(rain, r, 0.40F * bright);
        g = Mth.lerp(rain, g, 0.44F * bright);
        b = Mth.lerp(rain, b, 0.49F * bright);

        float bliz = ClientWeather.camBlizzard * 0.9F * shelter;
        r = Mth.lerp(bliz, r, 0.80F * bright + 0.05F);
        g = Mth.lerp(bliz, g, 0.84F * bright + 0.05F);
        b = Mth.lerp(bliz, b, 0.90F * bright + 0.05F);

        float sand = ClientWeather.camSand * 0.92F * shelter;
        r = Mth.lerp(sand, r, 0.80F * bright + 0.03F);
        g = Mth.lerp(sand, g, 0.62F * bright + 0.02F);
        b = Mth.lerp(sand, b, 0.38F * bright + 0.01F);

        float f = LightningFx.flash * 0.35F;
        event.setRed(Math.min(1F, r + f));
        event.setGreen(Math.min(1F, g + f));
        event.setBlue(Math.min(1F, b + f * 1.1F));
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float s = LightningFx.shake;
        if (s < 0.01F || !ClientConfig.CAMERA_SHAKE.get()) {
            return;
        }
        double t = (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0) + event.getPartialTick();
        float amp = s * 0.9F;
        event.setPitch(event.getPitch() + (float) Math.sin(t * 2.7) * amp);
        event.setYaw(event.getYaw() + (float) Math.sin(t * 3.3 + 1.0) * amp * 0.6F);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 4.1 + 2.0) * amp * 0.5F);
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }
}
