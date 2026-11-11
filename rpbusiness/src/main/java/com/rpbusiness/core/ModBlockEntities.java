package com.rpbusiness.core;

import com.rpbusiness.RPBusiness;
import com.rpbusiness.block.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REG = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RPBusiness.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CuttingBoardBE>> CUTTING_BOARD = REG.register("cutting_board",
            () -> BlockEntityType.Builder.of(CuttingBoardBE::new, ModBlocks.CUTTING_BOARD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CookerBE>> COOKER = REG.register("cooker",
            () -> BlockEntityType.Builder.of(CookerBE::new, ModBlocks.GRILL.get(), ModBlocks.FRYER.get(), ModBlocks.OVEN.get(), ModBlocks.COOKING_POT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrepStationBE>> PREP_STATION = REG.register("prep_station",
            () -> BlockEntityType.Builder.of(PrepStationBE::new, ModBlocks.PREP_STATION.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrinkMachineBE>> DRINK_MACHINE = REG.register("drink_machine",
            () -> BlockEntityType.Builder.of(DrinkMachineBE::new, ModBlocks.DRINK_MACHINE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServingCounterBE>> SERVING_COUNTER = REG.register("serving_counter",
            () -> BlockEntityType.Builder.of(ServingCounterBE::new, ModBlocks.SERVING_COUNTER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageBE>> STORAGE = REG.register("storage",
            () -> BlockEntityType.Builder.of(StorageBE::new, ModBlocks.COUNTER_CABINET.get(), ModBlocks.PANTRY_SHELF.get(),
                    ModBlocks.DELIVERY_CRATE.get(), ModBlocks.PACKAGE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnimatedStorageBE>> ANIMATED_STORAGE = REG.register("animated_storage",
            () -> BlockEntityType.Builder.of(AnimatedStorageBE::new, ModBlocks.FRIDGE.get(), ModBlocks.FREEZER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisplayBE>> DISPLAY = REG.register("display",
            () -> BlockEntityType.Builder.of(DisplayBE::new, ModBlocks.DISPLAY_SHELF.get(), ModBlocks.FRIDGE_DISPLAY.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SafeBE>> SAFE = REG.register("safe",
            () -> BlockEntityType.Builder.of(SafeBE::new, ModBlocks.SAFE.get()).build(null));

    private ModBlockEntities() {}
}
