package com.rpbusiness.business;

import java.util.Locale;

public enum BusinessType {
    RESTAURANT(true, false), FAST_FOOD(true, false), CAFE(true, false), BAKERY(true, true),
    GROCERY(false, true), SUPERMARKET(false, true), CLOTHING(false, true), ELECTRONICS(false, true), FURNITURE(false, true),
    HOTEL(false, false), BUTCHER(false, true), WAREHOUSE(false, false), DELIVERY(false, false), FACTORY(false, false), GAS_STATION(false, true), OTHER(false, true);

    /** Whether the restaurant simulation (tables, menu, kitchen) applies. */
    public final boolean foodService;
    /** Whether customers shop from display shelves and pay at the checkout. */
    public final boolean retail;
    BusinessType(boolean foodService, boolean retail) { this.foodService = foodService; this.retail = retail; }
    public boolean delivery() { return this == DELIVERY || this == WAREHOUSE; }
    public String key() { return "business_type.rpbusiness." + name().toLowerCase(Locale.ROOT); }
    public static BusinessType byName(String n) { try { return valueOf(n); } catch (Exception e) { return RESTAURANT; } }
}
