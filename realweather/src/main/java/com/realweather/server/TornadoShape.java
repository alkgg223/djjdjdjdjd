package com.realweather.server;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * The visible form of a tornado's condensation funnel (the basic types storm spotters use). The rating decides how
 * big and strong it is; the shape decides how it looks: radius along the height, where the condensation ends and
 * only a debris whirl reaches the ground, how much the column snakes, how rough and how pale it is.
 */
public enum TornadoShape {
    ROPE, CONE, BOWL, DRILL_BIT, WEDGE, TRUNCATED_CONE, BULB, HOURGLASS, STOVEPIPE, SEGMENTED, LANDSPOUT, FLARED_BASE;

    private static final TornadoShape[] VALUES = values();

    /** Which shapes each rating takes, with weights. */
    private static final TornadoShape[][] BY_RATING = {
            {ROPE, ROPE, LANDSPOUT, LANDSPOUT, SEGMENTED, CONE},
            {ROPE, CONE, CONE, DRILL_BIT, SEGMENTED, LANDSPOUT, FLARED_BASE},
            {CONE, CONE, DRILL_BIT, TRUNCATED_CONE, BOWL, STOVEPIPE, FLARED_BASE, HOURGLASS},
            {CONE, STOVEPIPE, TRUNCATED_CONE, BULB, HOURGLASS, BOWL, WEDGE},
            {WEDGE, WEDGE, STOVEPIPE, BULB, TRUNCATED_CONE, BOWL},
            {WEDGE, WEDGE, WEDGE, STOVEPIPE, BULB},
    };

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TornadoShape byId(int i) {
        return VALUES[Mth.clamp(i, 0, VALUES.length - 1)];
    }

    public static TornadoShape byName(String s) {
        for (TornadoShape t : VALUES) {
            if (t.id().equals(s)) {
                return t;
            }
        }
        return null;
    }

    public static TornadoShape pick(int rating, RandomSource rng) {
        TornadoShape[] list = BY_RATING[Mth.clamp(rating, 0, 5)];
        return list[rng.nextInt(list.length)];
    }

    private static float sm(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    /** Funnel radius in core radii at height fraction f (0 = ground, 1 = cloud base). */
    public float profile(float f) {
        return switch (this) {
            case ROPE -> 0.38F + 0.25F * f * f + 1.7F * sm(0.8F, 1F, f);
            case CONE -> 0.62F + 1.95F * (float) Math.pow(f, 1.7);
            case BOWL -> {
                float u = Mth.clamp((f - 0.48F) / 0.52F, 0F, 1F);
                yield 0.35F + 2.5F * Mth.sqrt(Math.max(0F, 1F - (1F - u) * (1F - u)));
            }
            case DRILL_BIT -> f < 0.36F ? 0.2F + 0.26F * (f / 0.36F) * (f / 0.36F)
                    : 0.46F + 2.05F * (float) Math.pow((f - 0.36F) / 0.64F, 1.25);
            case WEDGE -> 1.28F + 0.42F * (float) Math.pow(f, 2.2) + 0.3F * sm(0.84F, 1F, f);
            case TRUNCATED_CONE -> 1.05F + 1.55F * (float) Math.pow(f, 1.4);
            case BULB -> {
                float u = Mth.clamp((f - 0.16F) / 0.84F, 0F, 1F);
                yield (0.45F + 1.55F * sm(0F, 0.32F, u)) * (1F + 0.22F * Mth.sin(Mth.PI * u)) + 0.45F * sm(0.85F, 1F, u);
            }
            case HOURGLASS -> {
                float g = f - 0.42F;
                yield 0.48F + (g > 0F ? 2.3F * (float) Math.pow(g / 0.58F, 1.6) : 1.35F * (float) Math.pow(-g / 0.42F, 1.6));
            }
            case STOVEPIPE -> 1.05F + 0.1F * f + 0.55F * sm(0.86F, 1F, f);
            case SEGMENTED -> 0.5F + 0.3F * f * f + 1.5F * sm(0.82F, 1F, f);
            case LANDSPOUT -> 0.48F + 0.12F * f + 0.55F * sm(0.9F, 1F, f);
            case FLARED_BASE -> 0.42F + 0.15F * f + 2.4F * (float) Math.exp(-f / 0.07F) + 1.3F * sm(0.84F, 1F, f);
        };
    }

    /**
     * Height fraction below which there is no condensation, only a whirl of debris reaching down to the ground
     * (truncated cone, bowl, bulb); 0 when the funnel itself reaches the ground.
     */
    public float condensationBottom() {
        return switch (this) {
            case BOWL -> 0.48F;
            case TRUNCATED_CONE -> 0.26F;
            case BULB -> 0.16F;
            case DRILL_BIT -> 0.03F;
            default -> 0F;
        };
    }

    /** How visible the condensation is at height fraction f (segmented funnels come in pieces that slide down). */
    public float condensation(float f, float time, long seed) {
        float b = condensationBottom();
        float k = b > 0F ? sm(b, b + 0.07F, f) : 1F;
        if (this == SEGMENTED) {
            float phase = (seed & 255) * 0.1F;
            float bands = Mth.sin(f * 15F + time * 0.035F + phase) + 0.5F * Mth.sin(f * 7F - time * 0.02F + phase * 1.7F);
            float pieces = sm(-0.45F, 0.15F, bands);
            // The upper part stays joined to the cloud; the lowest pieces are short stubs.
            k *= Math.max(pieces, sm(0.62F, 0.78F, f));
        }
        return k;
    }

    /** How strongly the column snakes about (ropes whip around, wedges and stovepipes stand straight). */
    public float snake() {
        return switch (this) {
            case ROPE, SEGMENTED -> 1.25F;
            case DRILL_BIT, LANDSPOUT, FLARED_BASE -> 0.7F;
            case CONE, HOURGLASS -> 0.45F;
            case TRUNCATED_CONE, BULB, BOWL -> 0.3F;
            case STOVEPIPE -> 0.12F;
            case WEDGE -> 0.15F;
        };
    }

    /** Roughness of the outline. */
    public float rag() {
        return switch (this) {
            case LANDSPOUT -> 0.025F;
            case ROPE, STOVEPIPE -> 0.05F;
            case WEDGE, BOWL -> 0.13F;
            default -> 0.08F;
        };
    }

    /** Brightness offset of the condensation (landspouts are pale and smooth, wedges dark with rain and dirt). */
    public float tint() {
        return switch (this) {
            case LANDSPOUT -> 0.16F;
            case ROPE, SEGMENTED -> 0.08F;
            case WEDGE -> -0.08F;
            case BOWL, BULB -> -0.04F;
            default -> 0F;
        };
    }

    /** How opaque the funnel is. */
    public float opacity() {
        return switch (this) {
            case LANDSPOUT -> 0.72F;
            case SEGMENTED, ROPE -> 0.88F;
            default -> 0.95F;
        };
    }

    /** Landspouts come from a growing cumulus without a rotating wall cloud. */
    public boolean wallCloud() {
        return this != LANDSPOUT;
    }

    /** A wide skirt of debris round the base (flared base and the cut-off shapes show most of it). */
    public float debrisSkirt() {
        return switch (this) {
            case FLARED_BASE -> 1.8F;
            case BOWL, TRUNCATED_CONE -> 1.5F;
            case BULB -> 1.3F;
            case LANDSPOUT -> 0.7F;
            default -> 1F;
        };
    }
}
