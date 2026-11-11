package com.rpbusiness.core;

import com.rpbusiness.RPBusiness;
import com.rpbusiness.block.*;
import com.rpbusiness.food.Foods;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REG = DeferredRegister.createBlocks(RPBusiness.MODID);
    /** Blocks that get a BlockItem, in creative-tab order. */
    public static final Map<String, DeferredBlock<? extends Block>> ITEM_BLOCKS = new LinkedHashMap<>();

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion();
    }
    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f, 3.0f).sound(SoundType.WOOD).noOcclusion();
    }
    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.8f, 6.0f).sound(SoundType.STONE);
    }

    private static <B extends Block> DeferredBlock<B> add(String name, Function<BlockBehaviour.Properties, B> f, BlockBehaviour.Properties p) {
        DeferredBlock<B> b = REG.registerBlock(name, f, p);
        ITEM_BLOCKS.put(name, b);
        return b;
    }

    // ---------------- kitchen ----------------
    public static final DeferredBlock<ShapedBlock> KITCHEN_COUNTER = add("kitchen_counter", p -> new ShapedBlock(p, ShapedBlock.Kind.COUNTER), stone().noOcclusion());
    public static final DeferredBlock<StorageBlock> COUNTER_CABINET = add("counter_cabinet", p -> new StorageBlock(p, 9, StorageBlock.FULL), wood());
    public static final DeferredBlock<CuttingBoardBlock> CUTTING_BOARD = add("cutting_board", CuttingBoardBlock::new, wood());
    public static final DeferredBlock<CookerBlock> GRILL = add("grill", p -> new CookerBlock(p, Foods.Cooker.GRILL), metal().lightLevel(s -> s.getValue(CookerBlock.LIT) ? 7 : 0));
    public static final DeferredBlock<CookerBlock> FRYER = add("fryer", p -> new CookerBlock(p, Foods.Cooker.FRYER), metal());
    public static final DeferredBlock<CookerBlock> OVEN = add("oven", p -> new CookerBlock(p, Foods.Cooker.OVEN), metal().lightLevel(s -> s.getValue(CookerBlock.LIT) ? 9 : 0));
    public static final DeferredBlock<CookerBlock> COOKING_POT = add("cooking_pot", p -> new CookerBlock(p, Foods.Cooker.POT), metal());
    public static final DeferredBlock<PrepStationBlock> PREP_STATION = add("prep_station", PrepStationBlock::new, stone().noOcclusion());
    public static final DeferredBlock<DrinkMachineBlock> DRINK_MACHINE = add("drink_machine", DrinkMachineBlock::new, metal());
    public static final DeferredBlock<FridgeBlock> FRIDGE = add("fridge", FridgeBlock::new, metal());
    public static final DeferredBlock<FreezerBlock> FREEZER = add("freezer", FreezerBlock::new, metal());
    public static final DeferredBlock<StorageBlock> PANTRY_SHELF = add("pantry_shelf", p -> new StorageBlock(p, 18, StorageBlock.FULL), wood());
    public static final DeferredBlock<ServingCounterBlock> SERVING_COUNTER = add("serving_counter", ServingCounterBlock::new, stone().noOcclusion());

    // ---------------- dining room ----------------
    public static final DeferredBlock<TableBlock> DINING_TABLE = add("dining_table", TableBlock::new, wood());
    public static final DeferredBlock<ChairBlock> DINING_CHAIR = add("dining_chair", p -> new ChairBlock(p, ChairBlock.CHAIR_KIND), wood());
    public static final DeferredBlock<ChairBlock> BAR_STOOL = add("bar_stool", p -> new ChairBlock(p, ChairBlock.STOOL_KIND), metal());
    public static final DeferredBlock<ChairBlock> SOFA = add("sofa", p -> new ChairBlock(p, ChairBlock.SOFA_KIND),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.8f).sound(SoundType.WOOL).noOcclusion());
    public static final DeferredBlock<ShapedBlock> PENDANT_LAMP = add("pendant_lamp", p -> new ShapedBlock(p, ShapedBlock.Kind.LAMP), metal().lightLevel(s -> 15));
    public static final DeferredBlock<ShapedBlock> MENU_BOARD = add("menu_board", p -> new ShapedBlock(p, ShapedBlock.Kind.MENU_BOARD), wood());
    public static final DeferredBlock<ShapedBlock> POTTED_PLANT = add("potted_plant", p -> new ShapedBlock(p, ShapedBlock.Kind.PLANT), wood().sound(SoundType.GRASS));
    public static final DeferredBlock<Block> CHECKERED_FLOOR = add("checkered_floor", Block::new, stone());
    public static final DeferredBlock<Block> KITCHEN_TILES = add("kitchen_tiles", Block::new, stone());

    // ---------------- business & money ----------------
    public static final DeferredBlock<ShapedBlock> CASH_REGISTER = add("cash_register", p -> new ShapedBlock(p, ShapedBlock.Kind.REGISTER), metal());
    public static final DeferredBlock<ShapedBlock> PAYMENT_TERMINAL = add("payment_terminal", p -> new ShapedBlock(p, ShapedBlock.Kind.TERMINAL), metal());
    public static final DeferredBlock<ShapedBlock> BUSINESS_COMPUTER = add("business_computer", p -> new ShapedBlock(p, ShapedBlock.Kind.COMPUTER), metal());
    public static final DeferredBlock<ShapedBlock> SUPPLIER_TERMINAL = add("supplier_terminal", p -> new ShapedBlock(p, ShapedBlock.Kind.SUPPLIER), metal());
    public static final DeferredBlock<StorageBlock> DELIVERY_CRATE = add("delivery_crate", p -> new StorageBlock(p, 27, StorageBlock.CRATE), wood());
    public static final DeferredBlock<StorageBlock> PACKAGE = add("package", p -> new StorageBlock(p, 9, StorageBlock.PACKAGE), wood().strength(0.5f));

    // ---------- hotel ----------
    public static final DeferredBlock<HotelBedBlock> HOTEL_BED = add("hotel_bed", HotelBedBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.8f).sound(SoundType.WOOL).noOcclusion());

    // ---------- shops ----------
    public static final DeferredBlock<DisplayBlock> DISPLAY_SHELF = add("display_shelf", p -> new DisplayBlock(p, false), wood());
    public static final DeferredBlock<DisplayBlock> FRIDGE_DISPLAY = add("fridge_display", p -> new DisplayBlock(p, true), metal().lightLevel(s -> 6));

    // ---------- banking ----------
    public static final DeferredBlock<ShapedBlock> BANK_COUNTER = add("bank_counter", p -> new ShapedBlock(p, ShapedBlock.Kind.BANK), stone().noOcclusion());
    public static final DeferredBlock<ShapedBlock> BANK_COMPUTER = add("bank_computer", p -> new ShapedBlock(p, ShapedBlock.Kind.BANK_COMPUTER), metal());
    public static final DeferredBlock<ShapedBlock> ATM = add("atm", p -> new ShapedBlock(p, ShapedBlock.Kind.ATM), metal().lightLevel(s -> 4));
    public static final DeferredBlock<SafeBlock> SAFE = add("safe", SafeBlock::new, metal().strength(25f, 1200f));

    // ---------- delivery ----------
    public static final DeferredBlock<ShapedBlock> DELIVERY_TERMINAL = add("delivery_terminal", p -> new ShapedBlock(p, ShapedBlock.Kind.DELIVERY), metal());

    private ModBlocks() {}
}
