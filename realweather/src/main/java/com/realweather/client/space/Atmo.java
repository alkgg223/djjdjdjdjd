package com.realweather.client.space;

/**
 * A planet's air for the scattering pass: the top of the air and its scale height (planet radii), how strongly it
 * scatters each colour (blue skies scatter blue the most; on Mars dust makes it butterscotch with blue sunsets), its
 * haze, and the sunlight.
 */
public record Atmo(float top, float scaleH, float br, float bg, float bb, float mie, float sr, float sg, float sb, float intensity, float alpha) {

    /** The air round a globe seen from space (thin, as the Earth's is). */
    static Atmo globe(float r, float g, float b, float strength, float alpha) {
        float k = 25F * strength;
        return new Atmo(1.05F, 0.012F, k * r * r, k * g * g, k * b * b, 2.5F * strength, 1F, 0.97F, 0.92F, 22F, alpha);
    }

    /** The sky over the ground of a world in the planets dimension (the air reaching up to 700 blocks). */
    static Atmo sky(float r, float g, float b, float strength, float sr, float sg, float sb) {
        float k = 10F * strength;
        return new Atmo(1.156F, 0.03F, k * r * r, k * g * g, k * b * b, 1.2F * strength, sr, sg, sb, 18F, 1F);
    }
}
