package com.arkragnarok.registry;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.item.SuperSpyglassItem;
import com.arkragnarok.item.TranqArrowItem;
import com.arkragnarok.species.Kibble;
import com.arkragnarok.species.Species;
import com.arkragnarok.species.SpeciesList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ArkRagnarok.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArkRagnarok.MODID);

    public static final Map<String, DeferredItem<DeferredSpawnEggItem>> SPAWN_EGGS = new LinkedHashMap<>();

    static {
        for (Species s : SpeciesList.all()) {
            int[] c = eggColours(s.id);
            SPAWN_EGGS.put(s.id, ITEMS.register(s.id + "_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.TYPES.get(s.id), c[0], c[1], new Item.Properties())));
        }
    }

    /** put on any rideable dinosaur (right click), then right click with an empty hand to ride */
    public static final DeferredItem<Item> DINO_SADDLE = ITEMS.register("dino_saddle",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // ------------------------------------------------------------------------------------------- taming
    /** six kibble tiers; every species prefers one (see Species.kibble) */
    public static final Map<Kibble, DeferredItem<Item>> KIBBLE = new EnumMap<>(Kibble.class);

    static {
        for (Kibble k : Kibble.values()) {
            KIBBLE.put(k, ITEMS.register(k.item, () -> new Item(new Item.Properties().stacksTo(64)
                    .rarity(k.ordinal() >= 4 ? Rarity.RARE : k.ordinal() >= 2 ? Rarity.UNCOMMON : Rarity.COMMON)
                    .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3f).build()))));
        }
    }

    public static final DeferredItem<Item> MEJOBERRY = ITEMS.register("mejoberry",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.2f).fast().build())));
    public static final DeferredItem<Item> NARCOBERRY = ITEMS.register("narcoberry",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).fast()
                    .effect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0), 1.0f).build())));
    public static final DeferredItem<Item> NARCOTIC = ITEMS.register("narcotic",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(0).saturationModifier(0f).alwaysEdible()
                    .effect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 1), 1.0f)
                    .effect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0), 1.0f).build())));
    public static final DeferredItem<Item> RAW_PRIME_MEAT = ITEMS.register("raw_prime_meat",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3f)
                    .effect(new MobEffectInstance(MobEffects.HUNGER, 400, 0), 0.4f).build())));
    public static final DeferredItem<Item> COOKED_PRIME_MEAT = ITEMS.register("cooked_prime_meat",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(10).saturationModifier(0.9f).build())));
    public static final DeferredItem<Item> TRANQ_ARROW = ITEMS.register("tranq_arrow",
            () -> new TranqArrowItem(new Item.Properties()));
    public static final DeferredItem<Item> SUPER_SPYGLASS = ITEMS.register("super_spyglass",
            () -> new SuperSpyglassItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arkragnarok"))
            .icon(() -> new ItemStack(SPAWN_EGGS.get("tyrannosaurus").get()))
            .displayItems((params, out) -> {
                out.accept(SUPER_SPYGLASS.get());
                out.accept(TRANQ_ARROW.get());
                out.accept(NARCOTIC.get());
                out.accept(NARCOBERRY.get());
                out.accept(MEJOBERRY.get());
                for (DeferredItem<Item> k : KIBBLE.values()) out.accept(k.get());
                out.accept(RAW_PRIME_MEAT.get());
                out.accept(COOKED_PRIME_MEAT.get());
                out.accept(DINO_SADDLE.get());
                for (var tier : ModBuilding.PIECE_ITEMS.values()) for (var it : tier.values()) out.accept(it.get());
                for (var d : ModBuilding.DOORS.values()) out.accept(d.get());
                for (DeferredItem<DeferredSpawnEggItem> e : SPAWN_EGGS.values()) out.accept(e.get());
            })
            .build());

    private static int[] eggColours(String id) {
        int h = id.hashCode();
        int a = 0x404030 + ((h & 0x3F) << 16) + (((h >> 6) & 0x3F) << 8) + ((h >> 12) & 0x1F);
        int b = 0x804020 + (((h >> 17) & 0x3F) << 16) + (((h >> 23) & 0x3F) << 8);
        return new int[]{a & 0xFFFFFF, b & 0xFFFFFF};
    }
}
