package com.arkragnarok.registry;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.economy.StationMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ArkRagnarok.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> STATION = MENUS.register("station",
            () -> IMenuTypeExtension.create(StationMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<com.arkragnarok.survivor.TribeMenu>> TRIBES = MENUS.register("tribes",
            () -> IMenuTypeExtension.create(com.arkragnarok.survivor.TribeMenu::client));
}
