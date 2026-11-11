package com.rpbusiness.business;

import java.util.Locale;

public enum BusinessType {
    RESTAURANT(true), FAST_FOOD(true), CAFE(true), BAKERY(true),
    GROCERY(false), SUPERMARKET(false), CLOTHING(false), ELECTRONICS(false), FURNITURE(false),
    HOTEL(false), BUTCHER(false), WAREHOUSE(false), DELIVERY(false), FACTORY(false), GAS_STATION(false), OTHER(false);

    /** Whether the restaurant simulation (tables, menu, kitchen) applies. */
    public final boolean foodService;
    BusinessType(boolean foodService) { this.foodService = foodService; }
    public String key() { return "business_type.rpbusiness." + name().toLowerCase(Locale.ROOT); }
    public static BusinessType byName(String n) { try { return valueOf(n); } catch (Exception e) { return RESTAURANT; } }
}
