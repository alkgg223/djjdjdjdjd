package com.vehicleworks.registry;

import com.vehicleworks.VehicleWorks;
import com.vehicleworks.component.ComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VehicleWorks.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.vehicleworks"))
            .icon(() -> new ItemStack(ModItems.COMPONENT_ITEMS.get(ComponentType.JET_ENGINE).get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.WRENCH.get());
                for (ComponentType t : ComponentType.values()) out.accept(ModItems.COMPONENT_ITEMS.get(t).get());
                out.accept(ModItems.FUEL_CANISTER.get());
                out.accept(ModItems.AMMO_BELT.get());
                out.accept(ModItems.CANNON_SHELL.get());
                out.accept(ModItems.MISSILE.get());
                out.accept(ModItems.AERIAL_BOMB.get());
            })
            .build());

    private ModTabs() {}
}
