package com.gunsmod.block.airdef;

/**
 * The seven air defence launchers and guns. Accuracy is the chance that one missile (or one shell) finds its target;
 * they only fire at what a linked radar is tracking.
 */
public enum LauncherType {
    //      id              mode            acc    range mag  reload interval speed agil  damage sound          fx
    PATRIOT("sam_patriot", Mode.MISSILE, 0.90f, 300, 4, 140, 40, 6.6, 9.0, 120f, "sam_patriot", 0),
    S400("sam_s400", Mode.VERTICAL, 0.88f, 360, 4, 160, 50, 7.0, 8.0, 130f, "sam_s400", 0),
    NASAMS("sam_nasams", Mode.MISSILE, 0.80f, 210, 6, 90, 25, 7.2, 10.0, 85f, "sam_nasams", 0),
    TAMIR("sam_tamir", Mode.FIXED, 0.85f, 180, 20, 60, 15, 6.0, 12.0, 70f, "sam_tamir", 0),
    GEPARD("aa_gepard", Mode.GUN, 0.45f, 120, 24, 40, 2, 0, 0, 7f, "aa_35mm", -20),
    ZU23("aa_zu23", Mode.GUN, 0.30f, 90, 30, 50, 2, 0, 0, 4.5f, "aa_23mm", -21),
    BOFORS("aa_bofors", Mode.GUN, 0.38f, 110, 8, 30, 4, 0, 0, 10f, "aa_40mm", -22);

    /** MISSILE: turns and elevates; VERTICAL: stands its tubes upright and the missiles turn after launch; FIXED: turns at a set
     * elevation; GUN: a cannon that turns and elevates */
    public enum Mode { MISSILE, VERTICAL, FIXED, GUN }

    public final String id;
    public final Mode mode;
    public final float accuracy;
    public final double range;
    /** missiles ready to fire (guns: rounds in a burst) */
    public final int magazine;
    /** ticks to load one missile (guns: the pause between bursts) */
    public final int reload;
    /** ticks between two shots */
    public final int interval;
    public final double missileSpeed, missileAgility;
    public final float damage;
    public final String sound;
    /** shot effect code for the guns (ClientNet) */
    public final int fx;

    LauncherType(String id, Mode mode, float accuracy, double range, int magazine, int reload, int interval, double missileSpeed, double missileAgility,
                 float damage, String sound, int fx) {
        this.id = id;
        this.mode = mode;
        this.accuracy = accuracy;
        this.range = range;
        this.magazine = magazine;
        this.reload = reload;
        this.interval = interval;
        this.missileSpeed = missileSpeed;
        this.missileAgility = missileAgility;
        this.damage = damage;
        this.sound = sound;
        this.fx = fx;
    }

    public boolean gun() {
        return mode == Mode.GUN;
    }
}
