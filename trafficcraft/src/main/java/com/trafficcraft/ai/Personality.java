package com.trafficcraft.ai;

import com.trafficcraft.TCConfig;

/** Driver personality: speed factor, time headway, acceleration use, patience before honking, overtaking chance. */
public enum Personality {
    PATIENT(0.92f, 1.8f, 0.65f, 2.2f, 0.15f, 2.5f),
    NORMAL(1.0f, 1.4f, 0.8f, 1.0f, 0.5f, 3.0f),
    IMPATIENT(1.1f, 1.0f, 1.0f, 0.45f, 0.9f, 3.6f),
    /** fast and skilled: flashes slower cars, overtakes after checking three road sections ahead, drifts the turns cleanly */
    PRO(1.3f, 0.85f, 1.0f, 0.25f, 1.0f, 4.6f);

    public final float speedFactor, headway, accelUse, patience, overtake, brakeComfort;

    Personality(float sf, float hw, float acc, float pat, float ot, float b) {
        speedFactor = sf;
        headway = hw;
        accelUse = acc;
        patience = pat;
        overtake = ot;
        brakeComfort = b;
    }

    public float speedFactor() {
        return speedFactor * (0.95f + 0.1f * (float) (double) TCConfig.AGGRESSION.get());
    }

    public float headway() {
        return headway * (1.25f - 0.5f * (float) (double) TCConfig.AGGRESSION.get());
    }
}
