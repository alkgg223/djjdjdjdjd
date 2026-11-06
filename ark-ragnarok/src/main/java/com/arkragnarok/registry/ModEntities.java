package com.arkragnarok.registry;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.entity.RexEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ArkRagnarok.MODID);

    /** about 9.9 blocks long, 4.2 tall at the head (model scale 0.8) */
    public static final DeferredHolder<EntityType<?>, EntityType<RexEntity>> REX = ENTITIES.register("tyrannosaurus",
            () -> EntityType.Builder.of(RexEntity::new, MobCategory.CREATURE).sized(3.0f, 4.0f).eyeHeight(3.6f)
                    .clientTrackingRange(12).updateInterval(2).build("tyrannosaurus"));
}
