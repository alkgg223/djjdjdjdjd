package com.rpbusiness.business;

import java.util.Locale;

public enum Job {
    CHEF(60, 1), ASSISTANT_CHEF(40, 1), WAITER(35, 2), CASHIER(35, 3), CLEANER(25, 4), MANAGER(80, 5),
    SALES_ASSISTANT(32, 3), STOCK_MANAGER(36, 4), DELIVERY_DRIVER(45, 6), WAREHOUSE_WORKER(34, 6), PACKAGE_HANDLER(30, 6), DELIVERY_MANAGER(70, 5);

    public static final Job[] RESTAURANT_JOBS = {CHEF, ASSISTANT_CHEF, WAITER, CASHIER, CLEANER, MANAGER};
    public static final Job[] SHOP_JOBS = {CASHIER, SALES_ASSISTANT, STOCK_MANAGER, CLEANER, MANAGER};
    public static final Job[] DELIVERY_JOBS = {DELIVERY_DRIVER, WAREHOUSE_WORKER, PACKAGE_HANDLER, DELIVERY_MANAGER};

    public static final Job[] HOTEL_JOBS = {CASHIER, CLEANER, MANAGER};

    public static Job[] jobsFor(BusinessType t) {
        if (t == BusinessType.HOTEL) return HOTEL_JOBS;
        if (t.delivery()) return DELIVERY_JOBS;
        if (t.retail && !t.foodService) return SHOP_JOBS;
        if (t == BusinessType.BAKERY) return values();
        return RESTAURANT_JOBS;
    }

    public Job nextFor(BusinessType t) {
        Job[] js = jobsFor(t);
        for (int i = 0; i < js.length; i++) if (js[i] == this) return js[(i + 1) % js.length];
        return js[0];
    }

    public final int baseSalary;
    /** Texture/outfit index used by the renderer. */
    public final int outfit;
    Job(int baseSalary, int outfit) { this.baseSalary = baseSalary; this.outfit = outfit; }
    public String key() { return "job.rpbusiness." + name().toLowerCase(Locale.ROOT); }
    public static Job byName(String n) { try { return valueOf(n); } catch (Exception e) { return WAITER; } }
    public Job next() { return values()[(ordinal() + 1) % values().length]; }
}
