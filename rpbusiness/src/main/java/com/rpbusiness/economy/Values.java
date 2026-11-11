package com.rpbusiness.economy;

import com.rpbusiness.food.Dish;
import com.rpbusiness.item.CashItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;

import java.util.HashMap;
import java.util.Map;

/** Typical market value of an item, used as the default shop price and by customers to judge prices. */
public final class Values {
    private Values() {}
    private static final Map<Item, Integer> CACHE = new HashMap<>();
    private static final Map<String, Integer> FIXED = new HashMap<>();

    static {
        String[][] v = {{"diamond", "120"}, {"emerald", "60"}, {"gold_ingot", "25"}, {"iron_ingot", "8"}, {"copper_ingot", "3"},
                {"netherite_ingot", "700"}, {"coal", "2"}, {"redstone", "2"}, {"lapis_lazuli", "4"}, {"quartz", "3"},
                {"amethyst_shard", "6"}, {"ender_pearl", "20"}, {"book", "6"}, {"paper", "1"}, {"leather", "4"}, {"string", "1"},
                {"torch", "1"}, {"glass", "2"}, {"bucket", "10"}, {"saddle", "60"}, {"name_tag", "40"}, {"golden_apple", "80"},
                {"enchanted_golden_apple", "900"}, {"cake", "15"}, {"milk_bucket", "12"}, {"honey_bottle", "6"}};
        for (String[] e : v) FIXED.put("minecraft:" + e[0], Integer.parseInt(e[1]));
    }

    public static boolean sellable(ItemStack s) { return !s.isEmpty() && !(s.getItem() instanceof CashItem); }

    public static int base(Item item) {
        return CACHE.computeIfAbsent(item, Values::compute);
    }

    private static int compute(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (FIXED.containsKey(id)) return FIXED.get(id);
        Dish d = Dish.servedBy(item);
        if (d != null && item == d.target()) return d.basePrice;
        for (Suppliers.Company c : Suppliers.ALL)
            for (Suppliers.Product p : c.products())
                if (p.item().get() == item) return Math.max(1, Math.round(p.price() * 1.6f / p.pack()));
        ItemStack st = new ItemStack(item);
        FoodProperties food = st.get(net.minecraft.core.component.DataComponents.FOOD);
        int v;
        if (food != null) v = 1 + food.nutrition();
        else if (item instanceof ArmorItem a) v = 15 + a.getDefense() * 12;
        else if (item instanceof TieredItem t) v = 10 + (int) (t.getTier().getAttackDamageBonus() * 25) + t.getTier().getUses() / 40;
        else if (item instanceof BlockItem) v = 2;
        else v = 3;
        Rarity r = st.getRarity();
        if (r == Rarity.UNCOMMON) v *= 3;
        else if (r == Rarity.RARE) v *= 6;
        else if (r == Rarity.EPIC) v *= 12;
        return Math.max(1, v);
    }
}
