package com.rpbusiness.food;

import com.rpbusiness.core.ModComponents;
import com.rpbusiness.core.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Supplier;

/**
 * All culinary knowledge of the mod: what can be chopped, cooked, assembled and served.
 * Used by the cooking blocks (manual play), the chef AI (automated play) and the recipe book.
 */
public final class Foods {
    public enum Cooker { GRILL, FRYER, OVEN, POT }

    /** Quality of each cooking stage: undercooked, cooked, well-done, burnt. */
    public static final int[] STAGE_QUALITY = {45, 100, 85, 5};
    public static final String[] STAGE_KEYS = {"undercooked", "cooked", "well_done", "burnt"};

    public record Chop(Supplier<? extends Item> in, Supplier<? extends Item> out, int chops) {}

    /** A cooking line: input item turns into 4 successive stages after the given tick thresholds. */
    public record CookLine(Cooker cooker, Supplier<? extends Item> input, List<Supplier<? extends Item>> stages, int[] times, boolean needsFlip) {
        public int stageAt(int ticks) {
            int s = -1;
            for (int i = 0; i < times.length; i++) if (ticks >= times[i]) s = i;
            return s; // -1 = still raw
        }
        public Item itemAt(int ticks) {
            int s = stageAt(ticks);
            return s < 0 ? input.get() : stages.get(s).get();
        }
    }

    /** A set of interchangeable items filling one slot of an assembly. */
    public record Group(String key, List<Supplier<? extends Item>> items) {
        public boolean test(Item i) { for (var s : items) if (s.get() == i) return true; return false; }
        public Item primary() { return items.get(0).get(); }
    }

    public record Assembly(String id, Supplier<? extends Item> result, List<Group> parts) {}

    public static final List<Chop> CHOPS = new ArrayList<>();
    public static final List<CookLine> COOK_LINES = new ArrayList<>();
    public static final List<Assembly> ASSEMBLIES = new ArrayList<>();

    public static final Group BUN = g("bun", ModItems.BURGER_BUN);
    public static final Group TOMATO = g("tomato_slices", ModItems.TOMATO_SLICES);
    public static final Group LETTUCE = g("shredded_lettuce", ModItems.SHREDDED_LETTUCE);
    public static final Group ONION = g("chopped_onion", ModItems.CHOPPED_ONION);
    public static final Group CHEESE = g("cheese_slices", ModItems.CHEESE_SLICES);
    public static final Group BREAD = g("bread_slices", ModItems.BREAD_SLICES);
    public static final Group DOUGH = g("pizza_dough", ModItems.PIZZA_DOUGH);
    /** First entry is the item a chef aims for. */
    public static final Group PATTY = g("patty", ModItems.COOKED_PATTY, ModItems.WELL_DONE_PATTY, ModItems.UNDERCOOKED_PATTY, ModItems.BURNT_PATTY);

    static {
        CHOPS.add(new Chop(ModItems.TOMATO, ModItems.TOMATO_SLICES, 3));
        CHOPS.add(new Chop(ModItems.ONION, ModItems.CHOPPED_ONION, 4));
        CHOPS.add(new Chop(ModItems.LETTUCE, ModItems.SHREDDED_LETTUCE, 3));
        CHOPS.add(new Chop(ModItems.POTATO, ModItems.RAW_FRIES, 4));
        CHOPS.add(new Chop(ModItems.RAW_BEEF, ModItems.RAW_PATTY, 5));
        CHOPS.add(new Chop(ModItems.CHEESE_BLOCK, ModItems.CHEESE_SLICES, 2));
        CHOPS.add(new Chop(ModItems.BREAD_LOAF, ModItems.BREAD_SLICES, 2));

        COOK_LINES.add(new CookLine(Cooker.GRILL, ModItems.RAW_PATTY,
                List.of(ModItems.UNDERCOOKED_PATTY, ModItems.COOKED_PATTY, ModItems.WELL_DONE_PATTY, ModItems.BURNT_PATTY), new int[]{100, 200, 300, 440}, true));
        COOK_LINES.add(new CookLine(Cooker.GRILL, ModItems.RAW_BEEF,
                List.of(ModItems.RARE_STEAK, ModItems.MEDIUM_STEAK, ModItems.WELL_DONE_STEAK, ModItems.BURNT_STEAK), new int[]{120, 240, 360, 520}, true));
        COOK_LINES.add(new CookLine(Cooker.FRYER, ModItems.RAW_FRIES,
                List.of(ModItems.SOGGY_FRIES, ModItems.FRIES, ModItems.CRISPY_FRIES, ModItems.BURNT_FRIES), new int[]{80, 160, 240, 360}, false));
        COOK_LINES.add(new CookLine(Cooker.OVEN, ModItems.RAW_PIZZA,
                List.of(ModItems.DOUGHY_PIZZA, ModItems.PIZZA, ModItems.CRISPY_PIZZA, ModItems.BURNT_PIZZA), new int[]{140, 280, 380, 520}, false));
        COOK_LINES.add(new CookLine(Cooker.POT, ModItems.SOUP_MIX,
                List.of(ModItems.LUKEWARM_SOUP, ModItems.TOMATO_SOUP, ModItems.THICK_SOUP, ModItems.BURNT_SOUP), new int[]{120, 240, 360, 500}, false));

        ASSEMBLIES.add(new Assembly("cheeseburger", ModItems.CHEESEBURGER, List.of(BUN, PATTY, CHEESE, LETTUCE, TOMATO)));
        ASSEMBLIES.add(new Assembly("burger", ModItems.BURGER, List.of(BUN, PATTY, LETTUCE, TOMATO)));
        ASSEMBLIES.add(new Assembly("club_sandwich", ModItems.CLUB_SANDWICH, List.of(BREAD, CHEESE, LETTUCE, TOMATO)));
        ASSEMBLIES.add(new Assembly("garden_salad", ModItems.GARDEN_SALAD, List.of(LETTUCE, TOMATO, ONION)));
        ASSEMBLIES.add(new Assembly("raw_pizza", ModItems.RAW_PIZZA, List.of(DOUGH, TOMATO, CHEESE)));
        ASSEMBLIES.add(new Assembly("soup_mix", ModItems.SOUP_MIX, List.of(TOMATO, ONION)));
    }

    @SafeVarargs
    private static Group g(String key, Supplier<? extends Item>... items) { return new Group(key, List.of(items)); }

    // ------------------------------------------------------------------ lookups

    public static Chop chopFor(Item in) {
        for (Chop c : CHOPS) if (c.in().get() == in) return c;
        return null;
    }
    public static Chop chopProducing(Item out) {
        for (Chop c : CHOPS) if (c.out().get() == out) return c;
        return null;
    }
    public static CookLine cookLineFor(Cooker cooker, Item in) {
        for (CookLine l : COOK_LINES) if (l.cooker() == cooker && l.input().get() == in) return l;
        return null;
    }
    /** Cook line + stage index that produces this item, or null. */
    public static CookLine cookLineProducing(Item out) {
        for (CookLine l : COOK_LINES) for (var s : l.stages()) if (s.get() == out) return l;
        return null;
    }
    public static int stageIndex(Item out) {
        for (CookLine l : COOK_LINES) for (int i = 0; i < l.stages().size(); i++) if (l.stages().get(i).get() == out) return i;
        return -1;
    }
    public static Assembly assemblyProducing(Item out) {
        for (Assembly a : ASSEMBLIES) if (a.result().get() == out) return a;
        return null;
    }

    /** Finds the assembly whose parts are exactly matched by the given items (order free). */
    public static Assembly matchAssembly(List<ItemStack> contents) {
        outer:
        for (Assembly a : ASSEMBLIES) {
            if (a.parts().size() != contents.size()) continue;
            boolean[] used = new boolean[contents.size()];
            for (Group g : a.parts()) {
                boolean found = false;
                for (int i = 0; i < contents.size(); i++) {
                    if (!used[i] && g.test(contents.get(i).getItem())) { used[i] = true; found = true; break; }
                }
                if (!found) continue outer;
            }
            return a;
        }
        return null;
    }

    public static int qualityOf(ItemStack stack) {
        Integer q = stack.get(ModComponents.QUALITY.get());
        if (q != null) return q;
        int st = stageIndex(stack.getItem());
        return st >= 0 ? STAGE_QUALITY[st] : 100;
    }

    public static ItemStack withQuality(Item item, int q) {
        ItemStack s = new ItemStack(item);
        s.set(ModComponents.QUALITY.get(), Math.max(0, Math.min(100, q)));
        return s;
    }

    public static String hintFor(Item item) {
        if (chopFor(item) != null) return "hint.rpbusiness.choppable";
        for (CookLine l : COOK_LINES) if (l.input().get() == item) return "hint.rpbusiness.cook_" + l.cooker().name().toLowerCase(Locale.ROOT);
        for (Assembly a : ASSEMBLIES) for (Group g : a.parts()) if (g.test(item)) return "hint.rpbusiness.assembly_part";
        if (item == ModItems.COLA_SYRUP.get() || item == ModItems.COFFEE_BEANS.get()) return "hint.rpbusiness.drink_refill";
        if (item == ModItems.PAPER_CUP.get()) return "hint.rpbusiness.cup";
        if (Dish.servedBy(item) != null) return "hint.rpbusiness.dish";
        return null;
    }

    private Foods() {}
}
