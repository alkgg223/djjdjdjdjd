package com.realweather.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.realweather.ClientConfig;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * MAX OPTIMIZE: the four sides of this frame's view (left, right, bottom, top), worked out from the very matrices the
 * frame is drawn with, so clouds, storms, tornado parts, rain sheets and puddles lying wholly off the screen are not
 * built or sent to the graphics card at all. Only what could not be seen anyway is dropped, so the picture is exactly
 * the same. Positions are relative to the camera. With the option off every test passes.
 */
public final class ViewCull {
    /** Four planes (a, b, c, d), normalised so a point's value is its distance in blocks inside that side. */
    private static final float[] P = new float[16];
    private static final Matrix4f M = new Matrix4f();
    /** A little slack so nothing at the very edge of the screen is ever dropped. */
    private static final float MARGIN = 2F;
    private static boolean on;

    private ViewCull() {}

    /** Sets the view from a projection and a view matrix (camera-relative world to clip space). */
    public static void set(Matrix4fc projection, Matrix4fc view) {
        on = ClientConfig.maxOptimize();
        if (!on) {
            return;
        }
        M.set(projection).mul(view);
        plane(0, M.m03() + M.m00(), M.m13() + M.m10(), M.m23() + M.m20(), M.m33() + M.m30());
        plane(1, M.m03() - M.m00(), M.m13() - M.m10(), M.m23() - M.m20(), M.m33() - M.m30());
        plane(2, M.m03() + M.m01(), M.m13() + M.m11(), M.m23() + M.m21(), M.m33() + M.m31());
        plane(3, M.m03() - M.m01(), M.m13() - M.m11(), M.m23() - M.m21(), M.m33() - M.m31());
    }

    /** Sets the view from the matrices currently bound for drawing. */
    public static void fromRenderSystem() {
        set(RenderSystem.getProjectionMatrix(), RenderSystem.getModelViewMatrix());
    }

    /** Stops culling (for anything drawn outside a known view). */
    public static void off() {
        on = false;
    }

    private static void plane(int i, float a, float b, float c, float d) {
        float l = (float) Math.sqrt(a * a + b * b + c * c);
        if (!(l > 1.0E-6F) || !Float.isFinite(d)) {
            // A degenerate matrix: never cull against this side.
            a = b = c = 0F;
            d = 1F;
            l = 1F;
        }
        P[i * 4] = a / l;
        P[i * 4 + 1] = b / l;
        P[i * 4 + 2] = c / l;
        P[i * 4 + 3] = d / l;
    }

    /** Whether any of a sphere could be on the screen. */
    public static boolean sphere(float x, float y, float z, float r) {
        if (!on) {
            return true;
        }
        float lim = -r - MARGIN;
        for (int i = 0; i < 16; i += 4) {
            if (P[i] * x + P[i + 1] * y + P[i + 2] * z + P[i + 3] < lim) {
                return false;
            }
        }
        return true;
    }

    /** Whether any of an axis-aligned box could be on the screen. */
    public static boolean box(float x0, float y0, float z0, float x1, float y1, float z1) {
        if (!on) {
            return true;
        }
        for (int i = 0; i < 16; i += 4) {
            float a = P[i], b = P[i + 1], c = P[i + 2];
            // The corner furthest inside this side.
            float v = a * (a >= 0F ? x1 : x0) + b * (b >= 0F ? y1 : y0) + c * (c >= 0F ? z1 : z0) + P[i + 3];
            if (v < -MARGIN) {
                return false;
            }
        }
        return true;
    }
}
