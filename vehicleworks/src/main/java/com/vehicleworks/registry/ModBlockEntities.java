package com.vehicleworks.registry;

import com.vehicleworks.VehicleWorks;
import com.vehicleworks.block.ComponentBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VehicleWorks.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ComponentBlockEntity>> COMPONENT = BLOCK_ENTITIES.register("component",
            () -> BlockEntityType.Builder.of(ComponentBlockEntity::new,
                    ModBlocks.COMPONENTS.values().stream().map(d -> (Block) d.get()).toArray(Block[]::new)).build(null));

    private ModBlockEntities() {}
}
