package com.rpbusiness.food;

import com.rpbusiness.core.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Everything that can be put on a restaurant menu. */
public enum Dish {
    BURGER(12, Category.BURGER, 6, ModItems.BURGER),
    CHEESEBURGER(14, Category.BURGER, 8, ModItems.CHEESEBURGER),
    CLUB_SANDWICH(10, Category.LIGHT, 6, ModItems.CLUB_SANDWICH),
    GARDEN_SALAD(8, Category.LIGHT, 3, ModItems.GARDEN_SALAD),
    FRIES(5, Category.SIDE, 1, ModItems.FRIES, ModItems.CRISPY_FRIES, ModItems.SOGGY_FRIES, ModItems.BURNT_FRIES),
    STEAK(22, Category.MEAT, 3, ModItems.MEDIUM_STEAK, ModItems.WELL_DONE_STEAK, ModItems.RARE_STEAK, ModItems.BURNT_STEAK),
    PIZZA(16, Category.PIZZA, 5, ModItems.PIZZA, ModItems.CRISPY_PIZZA, ModItems.DOUGHY_PIZZA, ModItems.BURNT_PIZZA),
    TOMATO_SOUP(9, Category.LIGHT, 2, ModItems.TOMATO_SOUP, ModItems.THICK_SOUP, ModItems.LUKEWARM_SOUP, ModItems.BURNT_SOUP),
    COLA(3, Category.DRINK, 1, ModItems.COLA),
    COFFEE(4, Category.DRINK, 1, ModItems.COFFEE);

    public enum Category { BURGER, LIGHT, SIDE, MEAT, PIZZA, DRINK }

    public final int basePrice;
    public final Category category;
    /** Approximate ingredient cost at supplier prices, shown in the menu editor. */
    public final int ingredientCost;
    private final List<Supplier<? extends Item>> served;

    @SafeVarargs
    Dish(int basePrice, Category category, int ingredientCost, Supplier<? extends Item>... served) {
        this.basePrice = basePrice; this.category = category; this.ingredientCost = ingredientCost; this.served = List.of(served);
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String key() { return "dish.rpbusiness." + id(); }
    public boolean isDrink() { return category == Category.DRINK; }
    /** The ideal item a cook should produce. */
    public Item target() { return served.get(0).get(); }
    public boolean accepts(Item item) { for (var s : served) if (s.get() == item) return true; return false; }
    public boolean accepts(ItemStack st) { return !st.isEmpty() && accepts(st.getItem()); }
    public List<Supplier<? extends Item>> servedItems() { return served; }

    public static Dish byId(String id) {
        for (Dish d : values()) if (d.id().equals(id)) return d;
        return null;
    }
    public static Dish servedBy(Item item) {
        for (Dish d : values()) if (d.accepts(item)) return d;
        return null;
    }
}
