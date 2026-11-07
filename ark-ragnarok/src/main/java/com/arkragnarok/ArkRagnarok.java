package com.arkragnarok;

import com.arkragnarok.entity.ArkCreature;
import com.arkragnarok.species.Species;
import com.arkragnarok.species.SpeciesList;
import com.arkragnarok.registry.ModBuilding;
import com.arkragnarok.registry.ModEntities;
import com.arkragnarok.registry.ModItems;
import com.arkragnarok.registry.ModSounds;
import com.arkragnarok.registry.ModWorld;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * ARK - Ragnarok Ultimate Survival.
 * 27 creatures, each a smooth skinned mesh driven by GeckoLib bones and animations, at real size (1 m = 1 block).
 */
@Mod(ArkRagnarok.MODID)
public class ArkRagnarok {
    public static final String MODID = "arkragnarok";

    public ArkRagnarok(IEventBus modBus, ModContainer container) {
        ModEntities.ENTITIES.register(modBus);
        ModBuilding.init();
        com.arkragnarok.economy.ModEconomy.init();
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModWorld.FEATURES.register(modBus);
        ModBuilding.BLOCKS.register(modBus);
        com.arkragnarok.economy.ModEconomy.BLOCKS.register(modBus);
        com.arkragnarok.economy.ModEconomy.ARMOR_MATERIALS.register(modBus);
        com.arkragnarok.economy.ModEconomy.BLOCK_ENTITIES.register(modBus);
        com.arkragnarok.registry.ModMenus.MENUS.register(modBus);
        ModBuilding.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ArkRagnarok::attributes);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ArkConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        for (Species s : SpeciesList.all()) e.put(ModEntities.type(s.id), ArkCreature.createAttributes(s).build());
        e.put(ModEntities.SURVIVOR.get(), com.arkragnarok.survivor.Survivor.createAttributes().build());
    }
}
