package com.arkragnarok.survivor;

import com.arkragnarok.economy.ModEconomy;
import com.arkragnarok.economy.Recipes;
import com.arkragnarok.economy.Station;
import com.arkragnarok.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What survivors know about the mod's economy: where every raw resource comes from, every way to make a processed
 * item (in the hands or at which station), and which furniture block is which station.  Survivors use only the
 * mod's own items for everything.
 */
public final class Economy {
    private Economy() {}

    /** where a raw resource is gathered */
    public enum Source { TREE, PLANT, ROCK, METAL, CRYSTAL, OBSIDIAN, HUNT }

    /** one way to make an item: in the hands (station null) or at a station */
    public record Way(@Nullable Station station, Recipes.Recipe recipe) {}

    private static Map<Item, List<Way>> ways;

    public static synchronized List<Way> ways(Item item) {
        if (ways == null) {
            ways = new HashMap<>();
            for (Recipes.Recipe r : Recipes.hand()) ways.computeIfAbsent(r.result().get(), k -> new ArrayList<>()).add(new Way(null, r));
            for (Station s : Station.values()) {
                for (Recipes.Recipe r : Recipes.of(s)) ways.computeIfAbsent(r.result().get(), k -> new ArrayList<>()).add(new Way(s, r));
            }
        }
        return ways.getOrDefault(item, List.of());
    }

    @Nullable
    public static Source source(Item it) {
        if (it == ModEconomy.WOOD.get() || it == ModEconomy.THATCH.get()) return Source.TREE;
        if (it == ModEconomy.FIBER.get() || it == ModItems.MEJOBERRY.get() || it == ModItems.NARCOBERRY.get()) return Source.PLANT;
        if (it == ModEconomy.STONE.get() || it == ModEconomy.FLINT.get()) return Source.ROCK;
        if (it == ModEconomy.METAL_ORE.get()) return Source.METAL;
        if (it == ModEconomy.CRYSTAL.get()) return Source.CRYSTAL;
        if (it == ModEconomy.OBSIDIAN.get()) return Source.OBSIDIAN;
        if (it == ModEconomy.HIDE.get() || it == ModEconomy.RAW_MEAT.get() || it == ModEconomy.KERATIN.get() || it == ModEconomy.DINO_BONE.get()
                || it == ModEconomy.DINO_TOOTH.get() || it == ModItems.RAW_PRIME_MEAT.get()) return Source.HUNT;
        return null;
    }

    public static Item furnitureItem(String id) {
        return switch (id) {
            case "bed" -> ModEconomy.HIDE_BED_ITEM.get();
            case "storage_box" -> ModEconomy.STORAGE_BOX.get().asItem();
            case "large_storage_box" -> ModEconomy.LARGE_STORAGE_BOX.get().asItem();
            case "campfire" -> ModEconomy.CAMPFIRE.get().asItem();
            case "mortar_and_pestle" -> ModEconomy.MORTAR.get().asItem();
            case "smithy" -> ModEconomy.SMITHY.get().asItem();
            case "refining_forge" -> ModEconomy.REFINING_FORGE.get().asItem();
            case "fabricator" -> ModEconomy.FABRICATOR.get().asItem();
            default -> ModEconomy.CROP_PLOT.get().asItem();
        };
    }

    public static String furnitureOf(Station s) {
        return s.id;
    }

    public static boolean isMod(ItemStack st) {
        return !st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).getNamespace().equals(com.arkragnarok.ArkRagnarok.MODID);
    }

    public static String key(Item it) {
        return BuiltInRegistries.ITEM.getKey(it).toString();
    }

    /** storage category: 0 raw materials, 1 food, 2 processed / station goods, 3 weapons, tools and ammunition */
    public static int category(ItemStack st) {
        Item it = st.getItem();
        if (st.getMaxStackSize() == 1 || it == ModEconomy.STONE_ARROW.get() || it == ModItems.TRANQ_ARROW.get()) return 3;
        if (st.has(net.minecraft.core.component.DataComponents.FOOD) || ModItems.KIBBLE.values().stream().anyMatch(k -> k.get() == it)) return 1;
        if (source(it) != null) return 0;
        return 2;
    }

    /** which storage category a room's boxes are meant for (-1 any) */
    public static int roomCategory(@Nullable BaseDesign.Room r) {
        if (r == null) return 1;
        return switch (r) {
            case STORAGE, BARN -> 0;
            case KITCHEN, NURSERY -> 1;
            case WORKSHOP, SMELTER, LAB -> 2;
            case ARMORY -> 3;
            default -> -1;
        };
    }
}
