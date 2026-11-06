package com.arkragnarok.registry;

import com.arkragnarok.ArkRagnarok;
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

    public static final DeferredItem<DeferredSpawnEggItem> REX_SPAWN_EGG = ITEMS.register("tyrannosaurus_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.REX, 0x5a4a2c, 0x9a3a1e, new Item.Properties()));
    public static final DeferredItem<Item> REX_SADDLE = ITEMS.register("rex_saddle",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arkragnarok"))
            .icon(() -> new ItemStack(REX_SPAWN_EGG.get()))
            .displayItems((params, out) -> {
                out.accept(REX_SPAWN_EGG.get());
                out.accept(REX_SADDLE.get());
            })
            .build());
}
