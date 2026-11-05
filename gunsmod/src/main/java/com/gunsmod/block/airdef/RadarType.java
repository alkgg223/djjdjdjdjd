package com.gunsmod.block.airdef;

/**
 * The air search radars. Detection is the chance per sweep to pick up an ordinary aircraft near the radar (it
 * falls off towards the edge of the range); stealth aircraft show far less, the B-2 not at all. The P-18's long waves
 * see stealth aircraft about three times better than the microwave radars.
 */
public enum RadarType {
    //      id                detect range sweep stealth sector
    MPQ65("radar_mpq65", 0.92f, 300, 20, 1.0f, true),
    S400_96L6("radar_96l6", 0.95f, 380, 20, 1.0f, false),
    GIRAFFE("radar_giraffe", 0.80f, 230, 14, 1.0f, false),
    SENTINEL("radar_sentinel", 0.76f, 170, 10, 1.0f, false),
    ELM2084("radar_elm2084", 0.88f, 270, 14, 1.0f, false),
    P18("radar_p18", 0.62f, 340, 30, 3.0f, false),
    KUPOL("radar_kupol", 0.85f, 250, 16, 1.0f, false),
    // 3.0
    S300_30N6("radar_30n6", 0.93f, 320, 18, 1.0f, false),
    S350_50N6("radar_50n6", 0.94f, 300, 12, 1.2f, false),
    TPY2("radar_tpy2", 0.97f, 600, 24, 1.6f, true),
    TRML4D("radar_trml4d", 0.90f, 260, 10, 1.1f, false);

    public final String id;
    public final float detect;
    public final double range;
    /** ticks a full turn of the antenna takes (one roll of the dice per aircraft each sweep) */
    public final int sweep;
    /** multiplier on a stealthy aircraft's signature */
    public final float stealthBonus;
    /** a fixed face that looks over a sector (Patriot) instead of turning round */
    public final boolean sector;

    RadarType(String id, float detect, double range, int sweep, float stealthBonus, boolean sector) {
        this.id = id;
        this.detect = detect;
        this.range = range;
        this.sweep = sweep;
        this.stealthBonus = stealthBonus;
        this.sector = sector;
    }

    /** chance this sweep picks up an aircraft of the given signature at the given distance */
    /** the range with the server's radar setting applied */
    public double reach() {
        return range * com.gunsmod.Config.radarRange();
    }

    public double chance(float signature, double dist) {
        double range = reach();
        if (signature <= 0 || dist > range) return 0;
        double f = dist / range;
        return detect * Math.min(1.0, signature * stealthBonus) * (1 - 0.45 * f * f);
    }
}
