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

import static com.rpbusiness.block.Shapes.bx;

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
    private static BlockBehaviour.Properties soft() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.8f).sound(SoundType.WOOL).noOcclusion();
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

    // ---------- hotel room furniture & decoration ----------
    public static final DeferredBlock<HotelBedBlock> DOUBLE_BED = add("double_bed", p -> new HotelBedBlock(p, 2),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.8f).sound(SoundType.WOOL).noOcclusion());
    public static final DeferredBlock<TvBlock> TV = add("tv", TvBlock::new, metal().lightLevel(s -> s.getValue(TvBlock.ON) ? 6 : 0));
    public static final DeferredBlock<ChairBlock> ARMCHAIR = add("armchair", p -> new ChairBlock(p, ChairBlock.ARMCHAIR_KIND), soft());
    public static final DeferredBlock<ChairBlock> SOFA_GRAY = add("sofa_gray", p -> new ChairBlock(p, ChairBlock.SOFA_KIND), soft());
    public static final DeferredBlock<ChairBlock> SOFA_BLUE = add("sofa_blue", p -> new ChairBlock(p, ChairBlock.SOFA_KIND), soft());
    public static final DeferredBlock<ChairBlock> OFFICE_CHAIR = add("office_chair", p -> new ChairBlock(p, ChairBlock.OFFICE_KIND), metal());
    public static final DeferredBlock<ChairBlock> TOILET = add("toilet", p -> new ChairBlock(p, ChairBlock.TOILET_KIND), stone().noOcclusion());
    public static final DeferredBlock<ChairBlock> BATHTUB = add("bathtub", p -> new ChairBlock(p, ChairBlock.BATH_KIND), stone().noOcclusion());
    public static final DeferredBlock<DecorBlock> COFFEE_TABLE = add("coffee_table", p -> new DecorBlock(p, bx(1, 0, 3, 15, 7, 13)), wood());
    public static final DeferredBlock<DecorBlock> NIGHTSTAND = add("nightstand", p -> new DecorBlock(p, bx(2, 0, 2, 14, 12, 14)), wood());
    public static final DeferredBlock<DecorBlock> TABLE_LAMP = add("table_lamp", p -> new DecorBlock(p, bx(5, 0, 5, 11, 10, 11)), wood().lightLevel(s -> 13));
    public static final DeferredBlock<DecorBlock> FLOOR_LAMP = add("floor_lamp", p -> new DecorBlock(p, bx(5, 0, 5, 11, 16, 11)), metal().lightLevel(s -> 14));
    public static final DeferredBlock<StorageBlock> WARDROBE = add("wardrobe", p -> new StorageBlock(p, 18, StorageBlock.FULL), wood());
    public static final DeferredBlock<StorageBlock> DRESSER = add("dresser", p -> new StorageBlock(p, 9, StorageBlock.FULL), wood());
    public static final DeferredBlock<StorageBlock> MINIBAR = add("minibar", p -> new StorageBlock(p, 9, StorageBlock.FULL), metal());
    public static final DeferredBlock<DecorBlock> MIRROR = add("mirror", p -> new DecorBlock(p, bx(2, 0, 14, 14, 16, 16)), metal());
    public static final DeferredBlock<DecorBlock> RUG_RED = add("rug_red", p -> new DecorBlock(p, bx(0, 0, 0, 16, 1, 16)), soft());
    public static final DeferredBlock<DecorBlock> RUG_BEIGE = add("rug_beige", p -> new DecorBlock(p, bx(0, 0, 0, 16, 1, 16)), soft());
    public static final DeferredBlock<DecorBlock> RUG_BLUE = add("rug_blue", p -> new DecorBlock(p, bx(0, 0, 0, 16, 1, 16)), soft());
    public static final DeferredBlock<DecorBlock> CURTAINS = add("curtains", p -> new DecorBlock(p, bx(0, 0, 14, 16, 16, 16)), soft());
    public static final DeferredBlock<DecorBlock> PAINTING_SEA = add("painting_sea", p -> new DecorBlock(p, bx(1, 3, 15, 15, 13, 16)), wood());
    public static final DeferredBlock<DecorBlock> PAINTING_CITY = add("painting_city", p -> new DecorBlock(p, bx(1, 3, 15, 15, 13, 16)), wood());
    public static final DeferredBlock<DecorBlock> WALL_CLOCK = add("wall_clock", p -> new DecorBlock(p, bx(4, 4, 15, 12, 12, 16)), wood());
    public static final DeferredBlock<DecorBlock> DESK = add("desk", p -> new DecorBlock(p, bx(0, 13, 2, 16, 16, 14), bx(1, 0, 3, 3, 13, 13), bx(13, 0, 3, 15, 13, 13)), wood());
    public static final DeferredBlock<DecorBlock> SHOWER = add("shower", p -> new DecorBlock(p, bx(0, 0, 0, 16, 1, 16), bx(0, 1, 15, 16, 16, 16), bx(0, 1, 0, 1, 16, 15)), stone().noOcclusion());
    public static final DeferredBlock<DecorBlock> BATHROOM_SINK = add("bathroom_sink", p -> new DecorBlock(p, bx(3, 0, 6, 13, 14, 16)), stone().noOcclusion());
    public static final DeferredBlock<DecorBlock> TOWEL_RACK = add("towel_rack", p -> new DecorBlock(p, bx(2, 4, 13, 14, 14, 16)), metal());
    public static final DeferredBlock<StorageBlock> BOOKSHELF_MODERN = add("bookshelf_modern", p -> new StorageBlock(p, 9, StorageBlock.FULL), wood());
    public static final DeferredBlock<DecorBlock> TALL_PLANT = add("tall_plant", p -> new DecorBlock(p, bx(4, 0, 4, 12, 16, 12)), soft().sound(SoundType.GRASS));
    public static final DeferredBlock<DecorBlock> FLOWER_VASE = add("flower_vase", p -> new DecorBlock(p, bx(5, 0, 5, 11, 12, 11)), stone().noOcclusion());
    public static final DeferredBlock<DecorBlock> CEILING_FAN = add("ceiling_fan", p -> new DecorBlock(p, bx(1, 12, 1, 15, 16, 15)), metal());
    public static final DeferredBlock<DecorBlock> AIR_CONDITIONER = add("air_conditioner", p -> new DecorBlock(p, bx(1, 8, 11, 15, 15, 16)), metal());
    public static final DeferredBlock<DecorBlock> RADIATOR = add("radiator", p -> new DecorBlock(p, bx(1, 1, 12, 15, 11, 16)), metal());
    public static final DeferredBlock<DecorBlock> LUGGAGE_RACK = add("luggage_rack", p -> new DecorBlock(p, bx(1, 0, 3, 15, 9, 13)), wood());
    public static final DeferredBlock<DecorBlock> TRASH_BIN = add("trash_bin", p -> new DecorBlock(p, bx(4, 0, 4, 12, 11, 12)), metal());
    public static final DeferredBlock<DecorBlock> ROOM_PHONE = add("room_phone", p -> new DecorBlock(p, bx(4, 0, 5, 12, 4, 11)), metal());
    public static final DeferredBlock<DecorBlock> ROOM_SIGN = add("room_sign", p -> new DecorBlock(p, bx(4, 5, 15, 12, 11, 16)), metal());
    public static final DeferredBlock<RoomMessBlock> ROOM_MESS = add("room_mess", RoomMessBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.2f).sound(SoundType.WOOL).noOcclusion().noCollission());

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
