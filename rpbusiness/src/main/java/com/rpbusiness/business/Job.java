package com.rpbusiness.business;

import java.util.Locale;

public enum Job {
    CHEF(60, 1), ASSISTANT_CHEF(40, 1), WAITER(35, 2), CASHIER(35, 3), CLEANER(25, 4), MANAGER(80, 5);

    public final int baseSalary;
    /** Texture/outfit index used by the renderer. */
    public final int outfit;
    Job(int baseSalary, int outfit) { this.baseSalary = baseSalary; this.outfit = outfit; }
    public String key() { return "job.rpbusiness." + name().toLowerCase(Locale.ROOT); }
    public static Job byName(String n) { try { return valueOf(n); } catch (Exception e) { return WAITER; } }
    public Job next() { return values()[(ordinal() + 1) % values().length]; }
}
