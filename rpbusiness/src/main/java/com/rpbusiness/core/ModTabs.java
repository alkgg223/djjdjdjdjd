package com.rpbusiness.core;

import com.rpbusiness.RPBusiness;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> REG = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RPBusiness.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REG.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpbusiness"))
            .icon(() -> new ItemStack(ModItems.CHEESEBURGER.get()))
            .displayItems((params, out) -> {
                for (var i : ModItems.ALL) out.accept(i.get());
            })
            .build());

    private ModTabs() {}
}
