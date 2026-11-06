package com.arkragnarok.survivor;

import java.util.Random;
import net.minecraft.nbt.CompoundTag;

/**
 * Who a survivor is.  Traits in 0..1 that its decisions actually read:
 * aggression (raids, fights, ambushes), caution (flees earlier, builds walls first, prefers defensive designs),
 * ambition (bigger designs, expands sooner, wants more dinosaurs), greed (hoards, mines more, raids storage),
 * leadership (accepts and keeps alliances), intelligence (thinks more often, looks further, plans fewer wasted trips),
 * courage (stands its ground against predators), craft (learns crafting and building faster).
 */
public record Personality(float aggression, float caution, float ambition, float greed, float leadership, float intelligence,
                          float courage, float craft) {

    public static Personality random(long seed) {
        Random r = new Random(seed * 31L + 7);
        // one dominant temperament, then noise: aggressive expansionist, cautious defender, resource baron, fast expander,
        // diplomat, lone hunter
        int type = r.nextInt(6);
        float[] t = new float[8];
        for (int i = 0; i < 8; i++) t[i] = 0.25f + r.nextFloat() * 0.5f;
        switch (type) {
            case 0 -> { t[0] = 0.85f; t[2] = 0.8f; t[1] = 0.2f; }
            case 1 -> { t[1] = 0.9f; t[0] = 0.2f; t[6] = 0.4f; }
            case 2 -> { t[3] = 0.9f; t[2] = 0.6f; }
            case 3 -> { t[2] = 0.95f; t[7] = 0.7f; t[1] = 0.3f; }
            case 4 -> { t[4] = 0.9f; t[0] = 0.3f; t[5] = 0.7f; }
            default -> { t[6] = 0.9f; t[0] = 0.7f; t[4] = 0.15f; }
        }
        return new Personality(t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7]);
    }

    public String temperament() {
        if (aggression > 0.75f && ambition > 0.7f) return "aggressive_expansionist";
        if (caution > 0.8f) return "cautious_defender";
        if (greed > 0.8f) return "resource_baron";
        if (ambition > 0.85f) return "fast_expander";
        if (leadership > 0.8f) return "diplomat";
        if (courage > 0.8f) return "lone_hunter";
        return "survivor";
    }

    /** how close a seen predator may be before it runs (blocks) */
    public double fleeDistance() {
        return 14 + 22 * caution - 10 * courage;
    }

    /** ticks between raids it considers */
    public long raidInterval() {
        return (long) (24000 * (2.2 - 1.8 * aggression));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("ag", aggression);
        t.putFloat("ca", caution);
        t.putFloat("am", ambition);
        t.putFloat("gr", greed);
        t.putFloat("le", leadership);
        t.putFloat("in", intelligence);
        t.putFloat("co", courage);
        t.putFloat("cr", craft);
        return t;
    }

    public static Personality load(CompoundTag t) {
        return new Personality(t.getFloat("ag"), t.getFloat("ca"), t.getFloat("am"), t.getFloat("gr"), t.getFloat("le"), t.getFloat("in"),
                t.getFloat("co"), t.getFloat("cr"));
    }
}
