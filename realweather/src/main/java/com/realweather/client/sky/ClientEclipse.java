package com.realweather.client.sky;

import com.realweather.net.EclipseSyncPayload;
import com.realweather.server.Eclipse;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/** The eclipse as the client sees it right now: how much of the sun is covered, how dark it is, the diamond-ring flare. */
public final class ClientEclipse {
    static Eclipse current;
    /** 0..1, for this frame (all zero when there is no eclipse or the sun is down). */
    public static float coverage, dark, diamond, corona;
    /** Moon centre relative to the sun's, sun radii. */
    static float moonX, moonZ;
    /** The moon's radius in sun radii (smaller than the sun in an annular eclipse). */
    public static float moonR = Eclipse.MOON;
    /** 0..1: how high the sun stands (the eclipse only darkens the day while the sun is up). */
    static float sunUp;

    private ClientEclipse() {}

    public static void onSync(EclipseSyncPayload p) {
        current = p.active() ? new Eclipse(p.start(), p.duration(), p.miss(), p.angle(), p.kind()) : null;
    }

    public static void clear() {
        current = null;
        coverage = dark = diamond = corona = 0F;
    }

    public static boolean active() {
        return current != null && coverage > 0F;
    }

    /** Called every frame (and every tick) to bring the values up to date. */
    public static void update(ClientLevel level, float pt) {
        Eclipse e = current;
        if (e == null || level == null) {
            coverage = dark = diamond = corona = 0F;
            return;
        }
        double t = level.getGameTime() + pt;
        if (!e.active(t)) {
            coverage = dark = diamond = corona = 0F;
            return;
        }
        float a = level.getTimeOfDay(pt) * Mth.TWO_PI;
        sunUp = Mth.clamp(Mth.cos(a) * 6F + 0.3F, 0F, 1F);
        float[] o = e.moonOffset(t);
        moonX = o[0];
        moonZ = o[1];
        moonR = e.moonRadius(t);
        coverage = e.coverage(t);
        dark = Eclipse.darkness(coverage) * sunUp;
        diamond = Eclipse.diamond(coverage) * sunUp;
        corona = Mth.clamp((coverage - 0.985F) / 0.0149F, 0F, 1F) * sunUp;
        corona *= corona;
    }
}
