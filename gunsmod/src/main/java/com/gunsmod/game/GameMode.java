package com.gunsmod.game;

import java.util.Locale;

/**
 * The battle modes a mission control table can run. Attack / defend modes have an attacking side (set on the table);
 * the convoy modes are the same battle seen from either side: in Convoy Escort the table's side is the escort, in
 * Convoy Interception it is the side lying in wait.
 */
public enum GameMode {
    TDM, CONQUEST, DOMINATION, CTF, ASSAULT, SEARCH_DESTROY, CONVOY_ESCORT, CONVOY_INTERCEPT, HQ_DEFENSE, COMBINED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** played over capture points */
    public boolean points() {
        return this == CONQUEST || this == DOMINATION || this == COMBINED;
    }

    /** each side has a pool of lives (tickets) */
    public boolean tickets() {
        return this == CONQUEST || this == COMBINED || this == ASSAULT;
    }

    /** one side attacks, the other defends */
    public boolean attackDefend() {
        return this == ASSAULT || this == SEARCH_DESTROY || this == CONVOY_ESCORT || this == CONVOY_INTERCEPT || this == HQ_DEFENSE;
    }

    /** played over targets (command posts / the HQ) */
    public boolean targets() {
        return this == ASSAULT || this == SEARCH_DESTROY || this == HQ_DEFENSE;
    }

    public boolean convoy() {
        return this == CONVOY_ESCORT || this == CONVOY_INTERCEPT;
    }

    /** minutes on the clock by default */
    public int minutes() {
        return switch (this) {
            case TDM, DOMINATION, CTF -> 15;
            case SEARCH_DESTROY -> 8;
            case HQ_DEFENSE -> 10;
            case CONVOY_ESCORT, CONVOY_INTERCEPT -> 12;
            case COMBINED -> 30;
            default -> 20;
        };
    }

    /** the score to reach (kills, points, captures) or the tickets each side starts with */
    public int limit() {
        return switch (this) {
            case TDM -> 50;
            case CONQUEST -> 150;
            case COMBINED -> 300;
            case DOMINATION -> 250;
            case CTF -> 3;
            case ASSAULT -> 100;
            default -> 0;
        };
    }
}
