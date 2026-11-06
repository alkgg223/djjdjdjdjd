package com.arkragnarok;

import com.arkragnarok.entity.RexEntity;
import com.arkragnarok.registry.ModEntities;
import com.arkragnarok.registry.ModItems;
import com.arkragnarok.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * ARK - Ragnarok Ultimate Survival.
 * This build contains the hero Tyrannosaurus: a smooth skinned mesh driven by GeckoLib bones and animations.
 */
@Mod(ArkRagnarok.MODID)
public class ArkRagnarok {
    public static final String MODID = "arkragnarok";

    public ArkRagnarok(IEventBus modBus, ModContainer container) {
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        modBus.addListener(ArkRagnarok::attributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.REX.get(), RexEntity.createAttributes().build());
    }
}
