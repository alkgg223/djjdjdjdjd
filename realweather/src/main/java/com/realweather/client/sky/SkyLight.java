package com.realweather.client.sky;

import com.realweather.client.LightningFx;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/** Light on the clouds this frame: their sunlit colour, where the sun is, and which side of a sprite faces it. */
final class SkyLight {
    static float r, g, b;
    static float sunX, sunY, sunZ;
    /** 1 by day, 0 at night. */
    static float day;
    /** -1..1: how much the sun lights the right side of an upright sprite (as seen by the camera). */
    static float sunSide;

    private SkyLight() {}

    static void update(ClientLevel level, float pt, Camera camera) {
        // Daylight through the storm: getSkyDarken already dims for rain and thunder (0.2 at night .. 1 at noon).
        float sky = Mth.clamp((level.getSkyDarken(pt) - 0.2F) / 0.8F, 0F, 1F);
        float bright = 0.09F + 0.91F * sky;
        float angle = level.getTimeOfDay(pt) * Mth.TWO_PI;
        sunX = -Mth.sin(angle);
        sunY = Mth.cos(angle);
        sunZ = 0F;
        day = Mth.clamp(sunY * 3F + 0.3F, 0F, 1F);
        // Low sun: warm light; night: a cold blue.
        float warm = sunY > -0.12F ? Mth.clamp(1F - Math.abs(sunY) * 4F, 0F, 1F) : 0F;
        float night = 1F - sky;
        float flash = LightningFx.flash * 0.75F;
        r = Math.min(1.25F, bright * (1F + 0.22F * warm - 0.12F * night) + flash * 0.85F);
        g = Math.min(1.25F, bright * (1F + 0.04F * warm - 0.05F * night) + flash * 0.9F);
        b = Math.min(1.25F, bright * (1F - 0.18F * warm + 0.1F * night) + flash);
        Vector3f left = camera.getLeftVector();
        sunSide = Mth.clamp(-(left.x * sunX + left.y * sunY + left.z * sunZ) * 1.6F, -1F, 1F) * day;
    }

    /** Extra light on the sun-facing side of a cloud, for a puff offset (dx, dy, dz) from the cloud centre. */
    static float silver(float dx, float dy, float dz) {
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1E-3F) {
            return 0F;
        }
        return Math.max(0F, (dx * sunX + dy * sunY + dz * sunZ) / len) * 0.22F * day;
    }
}
