package com.trafficcraft.npc;

import net.minecraft.util.RandomSource;

/** Skin table, same order as assets/trafficcraft/textures/entity/ped/index.json. */
public final class PedSkins {
    public static final String[] NAMES = {"adult_1", "adult_2", "adult_3", "adult_4", "adult_5", "adult_6", "adult_7", "business_1", "business_2",
            "elderly_1", "elderly_2", "child_1", "child_2", "worker_1", "worker_2", "officer", "cashier", "attendant"};
    public static final boolean[] SLIM = {false, true, false, true, true, false, true, false, true, false, true, false, true, false, false, false, true, false};

    public static String kindOf(int skin) {
        String n = NAMES[Math.floorMod(skin, NAMES.length)];
        int u = n.indexOf('_');
        return u < 0 ? n : n.substring(0, u);
    }

    public static int forKind(String kind, RandomSource r) {
        int count = 0;
        for (int i = 0; i < NAMES.length; i++) if (kindOf(i).equals(kind)) count++;
        if (count == 0) return r.nextInt(7);
        int pick = r.nextInt(count);
        for (int i = 0; i < NAMES.length; i++) if (kindOf(i).equals(kind) && pick-- == 0) return i;
        return 0;
    }

    /** Random civilian kind with a plausible mix. */
    public static String randomCivilian(RandomSource r) {
        int x = r.nextInt(100);
        return x < 55 ? "adult" : x < 70 ? "business" : x < 82 ? "elderly" : x < 92 ? "child" : "worker";
    }
}
