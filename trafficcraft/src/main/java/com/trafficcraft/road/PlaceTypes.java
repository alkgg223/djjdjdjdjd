package com.trafficcraft.road;

import java.util.LinkedHashMap;
import java.util.Map;

/** The 25 kinds of map location: key, map colour and the short glyph drawn inside the map pin. */
public final class PlaceTypes {
    public record T(String key, int color, String glyph) {}

    public static final Map<String, T> ALL = new LinkedHashMap<>();

    static void t(String k, int c, String g) {
        ALL.put(k, new T(k, c, g));
    }

    static {
        t("supermarket", 0x2E9BE6, "S");
        t("bank", 0x2E7D32, "$");
        t("restaurant", 0xF57C00, "R");
        t("cafe", 0x8D6E63, "C");
        t("hospital", 0xE53935, "+");
        t("pharmacy", 0xEC407A, "Rx");
        t("gas_station", 0xF9A825, "G");
        t("police", 0x1E3A8A, "P");
        t("school", 0x5C6BC0, "Sc");
        t("university", 0x3949AB, "U");
        t("mosque", 0x00897B, "M");
        t("hotel", 0x7E57C2, "H");
        t("mall", 0x26A69A, "Ml");
        t("bakery", 0xD4A373, "B");
        t("park", 0x43A047, "Pk");
        t("parking", 0x1976D2, "P");
        t("mechanic", 0x546E7A, "W");
        t("car_dealer", 0xC62828, "D");
        t("airport", 0x0288D1, "A");
        t("bus_station", 0x00838F, "Bs");
        t("gym", 0x6D4C41, "Gy");
        t("cinema", 0xAD1457, "Ci");
        t("post_office", 0xFFB300, "Po");
        t("library", 0x795548, "L");
        t("home", 0x78909C, "Hm");
    }

    public static int color(String k) {
        T t = ALL.get(k);
        return t == null ? 0x9E9E9E : t.color();
    }

    public static String glyph(String k) {
        T t = ALL.get(k);
        return t == null ? "?" : t.glyph();
    }
}
