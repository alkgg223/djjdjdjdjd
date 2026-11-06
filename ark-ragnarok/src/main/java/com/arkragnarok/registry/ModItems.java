package com.arkragnarok.registry;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.species.Species;
import com.arkragnarok.species.SpeciesList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arkragnarok"))
            .icon(() -> new ItemStack(SPAWN_EGGS.get("tyrannosaurus").get()))
            .displayItems((params, out) -> {
                out.accept(DINO_SADDLE.get());
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
