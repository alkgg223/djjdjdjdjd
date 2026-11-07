package com.arkragnarok.economy;

import com.arkragnarok.building.PieceType;
import com.arkragnarok.building.Tier;
import com.arkragnarok.registry.ModBuilding;
import com.arkragnarok.registry.ModItems;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The production chains of the mod (used by players at the station screens and by survivors at their own stations):
 * campfire (cooking, charcoal), mortar and pestle (powders, paste, narcotics), refining forge (ingots), smithy (metal
 * tools, weapons, saddles, stone / iron structures) and fabricator (polymer, electronics, steel structures, devices).
 */
public final class Recipes {
    private Recipes() {}

    public record Ing(Supplier<Item> item, int count) {}

    public record Recipe(String id, Supplier<Item> result, int count, List<Ing> ingredients) {
        public ItemStack resultStack() {
            return new ItemStack(result.get(), count);
        }
    }

    private static final Map<Station, List<Recipe>> BY_STATION = new EnumMap<>(Station.class);
    private static final List<Recipe> HAND = new ArrayList<>();

    /** what is made in the hands, without a station (the same as the crafting-grid recipes of the data pack) */
    public static List<Recipe> hand() {
        if (BY_STATION.isEmpty()) init();
        return HAND;
    }

    private static void hand(String id, Supplier<? extends Item> result, int count, Ing... ings) {
        HAND.add(new Recipe(id, result::get, count, List.of(ings)));
    }

    public static List<Recipe> of(Station s) {
        if (BY_STATION.isEmpty()) init();
        return BY_STATION.getOrDefault(s, List.of());
    }

    private static Ing i(Supplier<? extends Item> it, int n) {
        return new Ing(it::get, n);
    }

    private static void add(Station s, String id, Supplier<? extends Item> result, int count, Ing... ings) {
        BY_STATION.computeIfAbsent(s, k -> new ArrayList<>()).add(new Recipe(id, result::get, count, List.of(ings)));
    }

    private static void init() {
        hand("stone_pick", ModEconomy.STONE_PICK, 1, i(ModEconomy.WOOD, 1), i(ModEconomy.STONE, 1), i(ModEconomy.THATCH, 3));
        hand("stone_hatchet", ModEconomy.STONE_HATCHET, 1, i(ModEconomy.WOOD, 1), i(ModEconomy.FLINT, 1), i(ModEconomy.STONE, 1), i(ModEconomy.THATCH, 2));
        hand("wooden_club", ModEconomy.WOODEN_CLUB, 1, i(ModEconomy.WOOD, 4), i(ModEconomy.FIBER, 2));
        hand("stone_spear", ModEconomy.STONE_SPEAR, 1, i(ModEconomy.WOOD, 2), i(ModEconomy.FLINT, 2), i(ModEconomy.FIBER, 2));
        hand("primitive_bow", ModEconomy.PRIMITIVE_BOW, 1, i(ModEconomy.WOOD, 2), i(ModEconomy.FIBER, 5));
        hand("stone_arrow", ModEconomy.STONE_ARROW, 2, i(ModEconomy.THATCH, 2), i(ModEconomy.FLINT, 1), i(ModEconomy.FIBER, 2));
        hand("campfire", () -> ModEconomy.CAMPFIRE.get().asItem(), 1, i(ModEconomy.THATCH, 3), i(ModEconomy.FLINT, 1), i(ModEconomy.STONE, 3),
                i(ModEconomy.WOOD, 2));
        hand("mortar_and_pestle", () -> ModEconomy.MORTAR.get().asItem(), 1, i(ModEconomy.STONE, 6), i(ModEconomy.HIDE, 2));
        hand("smithy", () -> ModEconomy.SMITHY.get().asItem(), 1, i(ModEconomy.STONE, 3), i(ModEconomy.WOOD, 3), i(ModEconomy.HIDE, 3));
        hand("storage_box", () -> ModEconomy.STORAGE_BOX.get().asItem(), 1, i(ModEconomy.WOOD, 5), i(ModEconomy.THATCH, 3), i(ModEconomy.FIBER, 1));
        hand("hide_bed", ModEconomy.HIDE_BED_ITEM, 1, i(ModEconomy.WOOD, 3), i(ModEconomy.HIDE, 3), i(ModEconomy.FIBER, 2), i(ModEconomy.THATCH, 1));
        hand("narco_dart", ModEconomy.NARCO_DART, 4, i(ModItems.NARCOBERRY, 2), i(ModEconomy.FIBER, 1), i(ModEconomy.THATCH, 1));
        armour(null, "cloth", new int[][]{{5}, {8}, {7}, {4}}, ModEconomy.FIBER);
        armour(null, "hide", new int[][]{{3, 2}, {5, 3}, {4, 3}, {2, 2}}, ModEconomy.HIDE, ModEconomy.FIBER);
        hand("tranq_arrow", ModItems.TRANQ_ARROW, 2, i(ModEconomy.FLINT, 1), i(ModEconomy.FIBER, 2), i(ModItems.NARCOBERRY, 3), i(ModEconomy.THATCH, 1));
        hand("crop_plot", () -> ModEconomy.CROP_PLOT.get().asItem(), 1, i(ModEconomy.WOOD, 3), i(ModEconomy.THATCH, 2), i(ModEconomy.STONE, 2),
                i(ModEconomy.FIBER, 2));
        // campfire
        add(Station.CAMPFIRE, "cooked_meat", ModEconomy.COOKED_MEAT, 1, i(ModEconomy.RAW_MEAT, 1), i(ModEconomy.WOOD, 1));
        add(Station.CAMPFIRE, "cooked_prime_meat", ModItems.COOKED_PRIME_MEAT, 1, i(ModItems.RAW_PRIME_MEAT, 1), i(ModEconomy.WOOD, 1));
        add(Station.CAMPFIRE, "charcoal", ModEconomy.CHARCOAL, 2, i(ModEconomy.WOOD, 3));
        // kibble: berries and fiber for the basic ones, cooked meat, then prime meat, hide, keratin and crystal for the best
        add(Station.CAMPFIRE, "basic_kibble", () -> ModItems.KIBBLE.get(com.arkragnarok.species.Kibble.BASIC).get(), 2,
                i(ModItems.MEJOBERRY, 5), i(ModEconomy.FIBER, 3));
        add(Station.CAMPFIRE, "simple_kibble", () -> ModItems.KIBBLE.get(com.arkragnarok.species.Kibble.SIMPLE).get(), 2,
                i(ModItems.MEJOBERRY, 6), i(ModEconomy.FIBER, 3), i(ModEconomy.COOKED_MEAT, 1));
        add(Station.CAMPFIRE, "regular_kibble", () -> ModItems.KIBBLE.get(com.arkragnarok.species.Kibble.REGULAR).get(), 2,
                i(ModItems.MEJOBERRY, 8), i(ModEconomy.FIBER, 4), i(ModEconomy.COOKED_MEAT, 2));
        add(Station.CAMPFIRE, "superior_kibble", () -> ModItems.KIBBLE.get(com.arkragnarok.species.Kibble.SUPERIOR).get(), 2,
                i(ModItems.MEJOBERRY, 10), i(ModEconomy.FIBER, 5), i(ModItems.COOKED_PRIME_MEAT, 1), i(ModEconomy.HIDE, 1));
        add(Station.CAMPFIRE, "exceptional_kibble", () -> ModItems.KIBBLE.get(com.arkragnarok.species.Kibble.EXCEPTIONAL).get(), 2,
                i(ModItems.MEJOBERRY, 12), i(ModEconomy.FIBER, 6), i(ModItems.COOKED_PRIME_MEAT, 2), i(ModEconomy.KERATIN, 1));
        add(Station.CAMPFIRE, "extraordinary_kibble", () -> ModItems.KIBBLE.get(com.arkragnarok.species.Kibble.EXTRAORDINARY).get(), 2,
                i(ModItems.MEJOBERRY, 14), i(ModEconomy.FIBER, 8), i(ModItems.COOKED_PRIME_MEAT, 3), i(ModEconomy.CRYSTAL, 1));
        // mortar and pestle
        add(Station.MORTAR, "sparkpowder", ModEconomy.SPARKPOWDER, 2, i(ModEconomy.FLINT, 2), i(ModEconomy.STONE, 1));
        add(Station.MORTAR, "gunpowder", ModEconomy.GUNPOWDER, 2, i(ModEconomy.SPARKPOWDER, 2), i(ModEconomy.CHARCOAL, 2));
        add(Station.MORTAR, "cementing_paste", ModEconomy.CEMENTING_PASTE, 2, i(ModEconomy.STONE, 4), i(ModEconomy.KERATIN, 1));
        add(Station.MORTAR, "cementing_paste_bone", ModEconomy.CEMENTING_PASTE, 1, i(ModEconomy.STONE, 4), i(ModEconomy.DINO_BONE, 1));
        add(Station.MORTAR, "narcotic", ModItems.NARCOTIC, 2, i(ModItems.NARCOBERRY, 5), i(ModEconomy.RAW_MEAT, 1));
        add(Station.MORTAR, "tranq_arrow", ModItems.TRANQ_ARROW, 4, i(ModEconomy.STONE_ARROW, 4), i(ModItems.NARCOTIC, 1));
        add(Station.MORTAR, "potent_tranq_arrow", ModEconomy.POTENT_TRANQ_ARROW, 4, i(ModEconomy.STONE_ARROW, 4), i(ModItems.NARCOTIC, 2),
                i(ModEconomy.METAL_INGOT, 1));
        // refining forge
        add(Station.FORGE, "metal_ingot", ModEconomy.METAL_INGOT, 1, i(ModEconomy.METAL_ORE, 2), i(ModEconomy.WOOD, 1));
        add(Station.FORGE, "metal_ingot_charcoal", ModEconomy.METAL_INGOT, 3, i(ModEconomy.METAL_ORE, 6), i(ModEconomy.CHARCOAL, 1));
        add(Station.FORGE, "steel_ingot", ModEconomy.STEEL_INGOT, 1, i(ModEconomy.METAL_INGOT, 2), i(ModEconomy.CHARCOAL, 2), i(ModEconomy.OBSIDIAN, 1));
        // smithy
        add(Station.SMITHY, "metal_pick", ModEconomy.METAL_PICK, 1, i(ModEconomy.METAL_INGOT, 2), i(ModEconomy.WOOD, 1), i(ModEconomy.HIDE, 1));
        add(Station.SMITHY, "metal_hatchet", ModEconomy.METAL_HATCHET, 1, i(ModEconomy.METAL_INGOT, 2), i(ModEconomy.WOOD, 1), i(ModEconomy.HIDE, 1));
        add(Station.SMITHY, "metal_sickle", ModEconomy.METAL_SICKLE, 1, i(ModEconomy.METAL_INGOT, 2), i(ModEconomy.WOOD, 1), i(ModEconomy.HIDE, 2));
        add(Station.SMITHY, "metal_sword", ModEconomy.METAL_SWORD, 1, i(ModEconomy.METAL_INGOT, 4), i(ModEconomy.HIDE, 2));
        add(Station.SMITHY, "dino_saddle", ModItems.DINO_SADDLE, 1, i(ModEconomy.HIDE, 12), i(ModEconomy.FIBER, 8), i(ModEconomy.METAL_INGOT, 2));
        add(Station.SMITHY, "super_spyglass", ModItems.SUPER_SPYGLASS, 1, i(ModEconomy.METAL_INGOT, 3), i(ModEconomy.CRYSTAL, 3), i(ModEconomy.HIDE, 1));
        add(Station.SMITHY, "large_storage_box", () -> ModEconomy.LARGE_STORAGE_BOX.get().asItem(), 1, i(ModEconomy.WOOD, 12), i(ModEconomy.METAL_INGOT, 2));
        add(Station.SMITHY, "refining_forge", () -> ModEconomy.REFINING_FORGE.get().asItem(), 1, i(ModEconomy.STONE, 12), i(ModEconomy.FLINT, 4),
                i(ModEconomy.HIDE, 2), i(ModEconomy.WOOD, 4));
        add(Station.SMITHY, "fabricator", () -> ModEconomy.FABRICATOR.get().asItem(), 1, i(ModEconomy.METAL_INGOT, 12), i(ModEconomy.CEMENTING_PASTE, 4),
                i(ModEconomy.CRYSTAL, 4), i(ModEconomy.SPARKPOWDER, 4));
        for (Tier t : new Tier[]{Tier.STONE, Tier.IRON}) pieceRecipes(Station.SMITHY, t);
        armour(Station.SMITHY, "chitin", new int[][]{{3, 2, 2}, {5, 3, 3}, {4, 3, 2}, {2, 2, 2}}, ModEconomy.KERATIN, ModEconomy.HIDE, ModEconomy.FIBER);
        armour(Station.SMITHY, "flak", new int[][]{{4, 2, 2}, {8, 3, 3}, {6, 3, 3}, {3, 2, 2}}, ModEconomy.METAL_INGOT, ModEconomy.HIDE, ModEconomy.FIBER);
        add(Station.SMITHY, "tranq_bolt", ModEconomy.TRANQ_BOLT, 2, i(ModEconomy.METAL_INGOT, 2), i(ModItems.NARCOTIC, 3), i(ModEconomy.WOOD, 2));
        // fabricator
        add(Station.FABRICATOR, "polymer", ModEconomy.POLYMER, 2, i(ModEconomy.OBSIDIAN, 2), i(ModEconomy.CEMENTING_PASTE, 2), i(ModEconomy.CRYSTAL, 1));
        add(Station.FABRICATOR, "electronics", ModEconomy.ELECTRONICS, 1, i(ModEconomy.METAL_INGOT, 1), i(ModEconomy.CRYSTAL, 2));
        add(Station.FABRICATOR, "survivor_command_device", ModItems.SURVIVOR_DEVICE, 1, i(ModEconomy.ELECTRONICS, 4), i(ModEconomy.POLYMER, 2),
                i(ModEconomy.CRYSTAL, 4));
        pieceRecipes(Station.FABRICATOR, Tier.STEEL);
        armour(Station.FABRICATOR, "riot", new int[][]{{3, 4, 2, 2}, {5, 8, 3, 3}, {4, 6, 2, 3}, {2, 4, 1, 2}}, ModEconomy.POLYMER,
                ModEconomy.METAL_INGOT, ModEconomy.CRYSTAL, ModEconomy.CEMENTING_PASTE);
        add(Station.FABRICATOR, "shock_tranq_arrow", ModEconomy.SHOCK_TRANQ_ARROW, 4, i(ModEconomy.STONE_ARROW, 4), i(ModItems.NARCOTIC, 2),
                i(ModEconomy.ELECTRONICS, 1));
    }

    /** a set of armour: helmet, chestplate, leggings, boots, each with its counts of the given materials (station null = in the hands) */
    @SafeVarargs
    private static void armour(Station st, String set, int[][] counts, Supplier<? extends Item>... mats) {
        String[] piece = {"helmet", "chestplate", "leggings", "boots"};
        for (int k = 0; k < 4; k++) {
            final int idx = k;
            Supplier<Item> result = () -> ModEconomy.ARMOR.get(set).get(idx).get();
            Ing[] ings = new Ing[mats.length];
            for (int m = 0; m < mats.length; m++) ings[m] = i(mats[m], counts[k][m]);
            if (st == null) HAND.add(new Recipe(set + "_" + piece[k], result, 1, List.of(ings)));
            else BY_STATION.computeIfAbsent(st, x -> new ArrayList<>()).add(new Recipe(set + "_" + piece[k], result, 1, List.of(ings)));
        }
    }

    /** {material ingredients} of a structure piece in a tier - also what survivors pay */
    public static List<Ing> pieceCost(Tier t, PieceType p, boolean door) {
        int k = p == PieceType.FOUNDATION ? 2 : 1;
        return switch (t) {
            case THATCH -> door ? List.of(i(ModEconomy.WOOD, 1), i(ModEconomy.THATCH, 4), i(ModEconomy.FIBER, 1))
                    : List.of(i(ModEconomy.WOOD, 2 * k), i(ModEconomy.THATCH, 5 * k), i(ModEconomy.FIBER, 2));
            case WOOD -> door ? List.of(i(ModEconomy.WOOD, 4), i(ModEconomy.THATCH, 1), i(ModEconomy.FIBER, 1))
                    : List.of(i(ModEconomy.WOOD, 6 * k), i(ModEconomy.THATCH, 2 * k), i(ModEconomy.FIBER, 1));
            case STONE -> door ? List.of(i(ModEconomy.WOOD, 4), i(ModEconomy.STONE, 4), i(ModEconomy.FIBER, 1))
                    : List.of(i(ModEconomy.STONE, 6 * k), i(ModEconomy.WOOD, 2 * k), i(ModEconomy.THATCH, 1));
            case IRON -> door ? List.of(i(ModEconomy.METAL_INGOT, 3), i(ModEconomy.CEMENTING_PASTE, 1))
                    : List.of(i(ModEconomy.METAL_INGOT, 3 * k), i(ModEconomy.CEMENTING_PASTE, k));
            case STEEL -> door ? List.of(i(ModEconomy.STEEL_INGOT, 3), i(ModEconomy.POLYMER, 1))
                    : List.of(i(ModEconomy.STEEL_INGOT, 3 * k), i(ModEconomy.POLYMER, 1), i(ModEconomy.CEMENTING_PASTE, k));
        };
    }

    private static void pieceRecipes(Station s, Tier t) {
        for (PieceType p : PieceType.values()) {
            Supplier<Item> it = () -> ModBuilding.PIECE_ITEMS.get(t).get(p).get();
            List<Ing> c = pieceCost(t, p, false);
            add(s, t.id + "_" + p.getSerializedName(), it, 1, c.toArray(new Ing[0]));
        }
        List<Ing> d = pieceCost(t, PieceType.DOORFRAME, true);
        add(s, t.id + "_door", () -> ModBuilding.DOORS.get(t).get(), 1, d.toArray(new Ing[0]));
    }

    // ---------------------------------------------------------------------------------------------- paying
    public static int count(Container inv, Item item) {
        int n = 0;
        for (int k = 0; k < inv.getContainerSize(); k++) if (inv.getItem(k).is(item)) n += inv.getItem(k).getCount();
        return n;
    }

    public static boolean canPay(Container inv, List<Ing> ings) {
        for (Ing g : ings) if (count(inv, g.item().get()) < g.count()) return false;
        return true;
    }

    public static boolean pay(Container inv, List<Ing> ings) {
        if (!canPay(inv, ings)) return false;
        for (Ing g : ings) {
            int left = g.count();
            for (int k = 0; k < inv.getContainerSize() && left > 0; k++) {
                ItemStack st = inv.getItem(k);
                if (!st.is(g.item().get())) continue;
                int t = Math.min(left, st.getCount());
                st.shrink(t);
                left -= t;
            }
        }
        inv.setChanged();
        return true;
    }

    public static boolean craft(Inventory inv, Recipe r) {
        if (!pay(inv, r.ingredients())) return false;
        ItemStack out = r.resultStack();
        if (!inv.add(out)) inv.player.drop(out, false);
        return true;
    }
}
