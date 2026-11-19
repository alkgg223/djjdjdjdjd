package com.vehicleworks.vehicle;

/**
 * State of an interactive passenger seat, packed in one int (synced to clients, saved with the
 * vehicle). Which adjustments a seat has depends on its class (1 economy .. 5 first class suite).
 */
public final class CabinSeat {
    private CabinSeat() {}

    // controls (Action argument = seat control id)
    public static final int SLIDE_FWD = 0, SLIDE_BACK = 1, RECLINE_UP = 2, RECLINE_DOWN = 3, LEG_UP = 4, LEG_DOWN = 5,
            FLAT = 6, SCREEN = 7, TRAY = 8, LIGHT = 9, DOOR = 10, PAGE = 11, CALL = 12, SHADE = 13, CONTROL_COUNT = 14;

    public static int slide(int s) { return s & 7; }            // 0..4, 2 = middle
    public static int recline(int s) { return (s >> 3) & 15; }  // 0 upright .. 10 full
    public static int leg(int s) { return (s >> 7) & 7; }       // 0 folded .. 4 raised
    public static boolean flat(int s) { return (s & (1 << 10)) != 0; }
    public static boolean tray(int s) { return (s & (1 << 11)) != 0; }
    public static boolean screen(int s) { return (s & (1 << 12)) != 0; }
    public static int light(int s) { return (s >> 13) & 3; }    // 0 off, 1 reading light, 2 ambient
    public static boolean door(int s) { return (s & (1 << 15)) != 0; }
    public static int page(int s) { return (s >> 16) & 3; }     // screen: 0 flight info, 1 map, 2 entertainment
    public static boolean call(int s) { return (s & (1 << 18)) != 0; }  // attendant call lit
    public static boolean shade(int s) { return (s & (1 << 19)) != 0; } // window shade down
    public static int withScreen(int s, boolean on) { return on ? s | (1 << 12) : s & ~(1 << 12); }
    public static int withCall(int s, boolean on) { return on ? s | (1 << 18) : s & ~(1 << 18); }

    public static final int DEFAULT = 2; // middle slide, upright, everything stowed / off

    public static int with(int slide, int recline, int leg, boolean flat, boolean tray, boolean screen, int light, boolean door, int page) {
        return (slide & 7) | (recline & 15) << 3 | (leg & 7) << 7 | (flat ? 1 << 10 : 0) | (tray ? 1 << 11 : 0) | (screen ? 1 << 12 : 0)
                | (light & 3) << 13 | (door ? 1 << 15 : 0) | (page & 3) << 16;
    }

    /** maximum recline step per class (economy reclines a little, business / first go flat) */
    public static int maxRecline(int cls) {
        return switch (cls) { case 1 -> 3; case 2 -> 4; case 3 -> 6; default -> 10; };
    }

    public static int maxLeg(int cls) {
        return switch (cls) { case 1 -> 0; case 2 -> 1; case 3 -> 3; default -> 4; };
    }

    public static boolean has(int cls, int control) {
        return switch (control) {
            case SLIDE_FWD, SLIDE_BACK -> cls >= 2;
            case LEG_UP, LEG_DOWN -> maxLeg(cls) > 0;
            case FLAT -> cls >= 4;
            case SCREEN, PAGE -> cls >= 3;
            case DOOR -> cls == 5;
            case CALL, SHADE -> cls >= 1;
            default -> true; // recline, tray, light
        };
    }

    /** Apply a control to a seat state. Returns the new state (unchanged if the class lacks it). */
    public static int apply(int cls, int s, int control) {
        if (!has(cls, control)) return s;
        int sl = slide(s), rc = recline(s), lg = leg(s), pg = page(s), li = light(s);
        boolean fl = flat(s), tr = tray(s), sc = screen(s), dr = door(s);
        switch (control) {
            case SLIDE_FWD -> sl = Math.min(4, sl + 1);
            case SLIDE_BACK -> sl = Math.max(0, sl - 1);
            case RECLINE_DOWN -> { rc = Math.min(maxRecline(cls), rc + 1); fl = false; }
            case RECLINE_UP -> { rc = Math.max(0, rc - 1); fl = false; }
            case LEG_UP -> lg = Math.min(maxLeg(cls), lg + 1);
            case LEG_DOWN -> lg = Math.max(0, lg - 1);
            case FLAT -> {
                // lie-flat / bed: back fully down and leg rest fully up (tray stowed)
                fl = !fl;
                rc = fl ? maxRecline(cls) : 0;
                lg = fl ? maxLeg(cls) : 0;
                if (fl) tr = false;
            }
            case SCREEN -> sc = !sc;
            case TRAY -> { tr = !tr; if (tr) fl = false; }
            case LIGHT -> li = (li + 1) % 3;
            case DOOR -> dr = !dr;
            case PAGE -> pg = (pg + 1) % 3;
            default -> {}
        }
        int out = with(sl, rc, lg, fl, tr, sc, li, dr, pg) | (s & (3 << 18));
        if (control == CALL) out ^= 1 << 18;
        if (control == SHADE) out ^= 1 << 19;
        return out;
    }
}
