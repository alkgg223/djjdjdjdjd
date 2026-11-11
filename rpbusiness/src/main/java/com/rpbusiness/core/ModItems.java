package com.rpbusiness.core;

import com.rpbusiness.RPBusiness;
import com.rpbusiness.item.*;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items REG = DeferredRegister.createItems(RPBusiness.MODID);
    /** Every registered item in creative-tab order. */
    public static final List<DeferredItem<? extends Item>> ALL = new ArrayList<>();
    public static final Map<String, DeferredItem<? extends Item>> BY_NAME = new LinkedHashMap<>();

    private static <T extends Item> DeferredItem<T> add(String name, DeferredItem<T> item) {
        ALL.add(item); BY_NAME.put(name, item); return item;
    }
    private static DeferredItem<FoodItem> food(String name, int nutrition, float sat) {
        return add(name, REG.registerItem(name, p -> new FoodItem(p.food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(sat).build()))));
    }
    private static DeferredItem<FoodItem> ingredient(String name) {
        return add(name, REG.registerItem(name, FoodItem::new));
    }
    private static DeferredItem<CashItem> cash(String name, int value) {
        return add(name, REG.registerItem(name, p -> new CashItem(value, p.stacksTo(64))));
    }

    // ---------- tools ----------
    public static final DeferredItem<ToolItem> KITCHEN_KNIFE = add("kitchen_knife", REG.registerItem("kitchen_knife", p -> new ToolItem("tooltip.rpbusiness.knife", p.stacksTo(1))));
    public static final DeferredItem<ToolItem> SPATULA = add("spatula", REG.registerItem("spatula", p -> new ToolItem("tooltip.rpbusiness.spatula", p.stacksTo(1))));
    public static final DeferredItem<ToolItem> MOP = add("mop", REG.registerItem("mop", p -> new ToolItem("tooltip.rpbusiness.mop", p.stacksTo(1))));
    public static final DeferredItem<BusinessPlannerItem> BUSINESS_PLANNER = add("business_planner", REG.registerItem("business_planner", p -> new BusinessPlannerItem(p.stacksTo(1))));
    public static final DeferredItem<LayoutToolItem> LAYOUT_TOOL = add("layout_tool", REG.registerItem("layout_tool", p -> new LayoutToolItem(p.stacksTo(1))));
    public static final DeferredItem<StaffPlannerItem> STAFF_PLANNER = add("staff_planner", REG.registerItem("staff_planner", p -> new StaffPlannerItem(p.stacksTo(1))));
    public static final DeferredItem<RecipeBookItem> RECIPE_BOOK = add("recipe_book", REG.registerItem("recipe_book", p -> new RecipeBookItem(p.stacksTo(1))));

    // ---------- money ----------
    public static final DeferredItem<CashItem> CASH_1 = cash("cash_1", 1);
    public static final DeferredItem<CashItem> CASH_5 = cash("cash_5", 5);
    public static final DeferredItem<CashItem> CASH_10 = cash("cash_10", 10);
    public static final DeferredItem<CashItem> CASH_20 = cash("cash_20", 20);
    public static final DeferredItem<CashItem> CASH_50 = cash("cash_50", 50);
    public static final DeferredItem<CashItem> CASH_100 = cash("cash_100", 100);
    public static final DeferredItem<CashItem> CASH_BUNDLE = add("cash_bundle", REG.registerItem("cash_bundle", p -> new CashItem(1000, p.stacksTo(64).rarity(Rarity.UNCOMMON))));

    // ---------- raw ingredients ----------
    public static final DeferredItem<FoodItem> TOMATO = food("tomato", 2, 0.3f);
    public static final DeferredItem<FoodItem> ONION = food("onion", 1, 0.2f);
    public static final DeferredItem<FoodItem> LETTUCE = food("lettuce", 1, 0.2f);
    public static final DeferredItem<FoodItem> POTATO = ingredient("potato");
    public static final DeferredItem<FoodItem> RAW_BEEF = ingredient("raw_beef");
    public static final DeferredItem<FoodItem> BURGER_BUN = food("burger_bun", 3, 0.4f);
    public static final DeferredItem<FoodItem> CHEESE_BLOCK = food("cheese_block", 3, 0.4f);
    public static final DeferredItem<FoodItem> BREAD_LOAF = food("bread_loaf", 4, 0.5f);
    public static final DeferredItem<FoodItem> PIZZA_DOUGH = ingredient("pizza_dough");
    public static final DeferredItem<FoodItem> COLA_SYRUP = ingredient("cola_syrup");
    public static final DeferredItem<FoodItem> COFFEE_BEANS = ingredient("coffee_beans");
    public static final DeferredItem<FoodItem> PAPER_CUP = ingredient("paper_cup");

    // ---------- prepared ----------
    public static final DeferredItem<FoodItem> TOMATO_SLICES = food("tomato_slices", 2, 0.3f);
    public static final DeferredItem<FoodItem> CHOPPED_ONION = ingredient("chopped_onion");
    public static final DeferredItem<FoodItem> SHREDDED_LETTUCE = food("shredded_lettuce", 1, 0.2f);
    public static final DeferredItem<FoodItem> RAW_FRIES = ingredient("raw_fries");
    public static final DeferredItem<FoodItem> RAW_PATTY = ingredient("raw_patty");
    public static final DeferredItem<FoodItem> CHEESE_SLICES = food("cheese_slices", 2, 0.3f);
    public static final DeferredItem<FoodItem> BREAD_SLICES = food("bread_slices", 2, 0.3f);
    public static final DeferredItem<FoodItem> RAW_PIZZA = ingredient("raw_pizza");
    public static final DeferredItem<FoodItem> SOUP_MIX = ingredient("soup_mix");

    // ---------- cooking lines (undercooked, cooked, well done, burnt) ----------
    public static final DeferredItem<FoodItem> UNDERCOOKED_PATTY = food("undercooked_patty", 3, 0.3f);
    public static final DeferredItem<FoodItem> COOKED_PATTY = food("cooked_patty", 6, 0.6f);
    public static final DeferredItem<FoodItem> WELL_DONE_PATTY = food("well_done_patty", 6, 0.6f);
    public static final DeferredItem<FoodItem> BURNT_PATTY = food("burnt_patty", 1, 0.1f);
    public static final DeferredItem<FoodItem> RARE_STEAK = food("rare_steak", 5, 0.5f);
    public static final DeferredItem<FoodItem> MEDIUM_STEAK = food("medium_steak", 9, 0.8f);
    public static final DeferredItem<FoodItem> WELL_DONE_STEAK = food("well_done_steak", 8, 0.7f);
    public static final DeferredItem<FoodItem> BURNT_STEAK = food("burnt_steak", 1, 0.1f);
    public static final DeferredItem<FoodItem> SOGGY_FRIES = food("soggy_fries", 2, 0.2f);
    public static final DeferredItem<FoodItem> FRIES = food("fries", 5, 0.5f);
    public static final DeferredItem<FoodItem> CRISPY_FRIES = food("crispy_fries", 5, 0.5f);
    public static final DeferredItem<FoodItem> BURNT_FRIES = food("burnt_fries", 1, 0.1f);
    public static final DeferredItem<FoodItem> DOUGHY_PIZZA = food("doughy_pizza", 4, 0.3f);
    public static final DeferredItem<FoodItem> PIZZA = food("pizza", 10, 0.8f);
    public static final DeferredItem<FoodItem> CRISPY_PIZZA = food("crispy_pizza", 9, 0.7f);
    public static final DeferredItem<FoodItem> BURNT_PIZZA = food("burnt_pizza", 1, 0.1f);
    public static final DeferredItem<FoodItem> LUKEWARM_SOUP = food("lukewarm_soup", 3, 0.3f);
    public static final DeferredItem<FoodItem> TOMATO_SOUP = food("tomato_soup", 7, 0.7f);
    public static final DeferredItem<FoodItem> THICK_SOUP = food("thick_soup", 6, 0.6f);
    public static final DeferredItem<FoodItem> BURNT_SOUP = food("burnt_soup", 1, 0.1f);

    // ---------- assembled dishes & drinks ----------
    public static final DeferredItem<FoodItem> BURGER = food("burger", 10, 0.8f);
    public static final DeferredItem<FoodItem> CHEESEBURGER = food("cheeseburger", 12, 0.9f);
    public static final DeferredItem<FoodItem> CLUB_SANDWICH = food("club_sandwich", 9, 0.7f);
    public static final DeferredItem<FoodItem> GARDEN_SALAD = food("garden_salad", 6, 0.6f);
    public static final DeferredItem<FoodItem> COLA = add("cola", REG.registerItem("cola", p -> new DrinkItem(p.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).alwaysEdible().build()))));
    public static final DeferredItem<FoodItem> COFFEE = add("coffee", REG.registerItem("coffee", p -> new DrinkItem(p.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).alwaysEdible().build()))));

    static {
        // block items, in block registration order
        for (var e : ModBlocks.ITEM_BLOCKS.entrySet()) {
            String name = e.getKey();
            add(name, REG.registerItem(name, p -> new BlockItem(e.getValue().get(), p)));
        }
    }

    private ModItems() {}
}
