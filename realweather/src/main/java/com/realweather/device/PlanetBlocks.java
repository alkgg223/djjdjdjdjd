package com.realweather.device;

import com.realweather.ModContent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;

/**
 * The blocks other worlds are made of: what the telescope and the planet map show their ground built from, and real
 * blocks to build with too (in the Real Weather tab).
 */
public final class PlanetBlocks {
    public static final List<DeferredBlock<Block>> ALL = new ArrayList<>();

    public static final DeferredBlock<Block> REGOLITH = block("planet_regolith", MapColor.COLOR_LIGHT_GRAY, 0.8F, SoundType.SAND, 0);
    public static final DeferredBlock<Block> BASALT = block("planet_basalt", MapColor.COLOR_BLACK, 2.0F, SoundType.BASALT, 0);
    public static final DeferredBlock<Block> SAND = block("planet_sand", MapColor.SAND, 0.6F, SoundType.SAND, 0);
    public static final DeferredBlock<Block> ICE = ModContent.BLOCKS.register("planet_ice",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.6F).friction(0.98F).sound(SoundType.GLASS)));
    public static final DeferredBlock<Block> FROST = block("planet_frost", MapColor.SNOW, 0.3F, SoundType.SNOW, 0);
    public static final DeferredBlock<Block> MAGMA = block("planet_magma", MapColor.COLOR_ORANGE, 1.5F, SoundType.STONE, 13);
    public static final DeferredBlock<Block> CLOUD = block("planet_cloud", MapColor.WOOL, 0.3F, SoundType.WOOL, 0);
    public static final DeferredBlock<Block> MOSS = block("planet_moss", MapColor.COLOR_GREEN, 0.6F, SoundType.MOSS, 0);
    public static final DeferredBlock<Block> CRYSTAL = block("planet_crystal", MapColor.DIAMOND, 1.5F, SoundType.AMETHYST, 7);
    public static final DeferredBlock<Block> SULFUR = block("planet_sulfur", MapColor.COLOR_YELLOW, 1.0F, SoundType.TUFF, 0);
    public static final DeferredBlock<Block> IRONSTONE = block("planet_ironstone", MapColor.COLOR_RED, 1.8F, SoundType.STONE, 0);
    public static final DeferredBlock<Block> STORM = block("planet_storm_cloud", MapColor.COLOR_BROWN, 0.3F, SoundType.WOOL, 0);
    public static final DeferredBlock<Block> OBSIDIAN = block("planet_obsidian", MapColor.COLOR_PURPLE, 6.0F, SoundType.STONE, 0);
    public static final DeferredBlock<Block> HOTROCK = block("planet_hot_rock", MapColor.COLOR_ORANGE, 1.8F, SoundType.STONE, 9);
    public static final DeferredBlock<Block> ASH = block("planet_ash", MapColor.COLOR_GRAY, 0.5F, SoundType.SAND, 0);

    private PlanetBlocks() {}

    private static DeferredBlock<Block> block(String name, MapColor color, float hardness, SoundType sound, int light) {
        DeferredBlock<Block> b = ModContent.BLOCKS.register(name,
                () -> new Block(BlockBehaviour.Properties.of().mapColor(color).strength(hardness, hardness * 2F).sound(sound).lightLevel(s -> light)));
        ALL.add(b);
        return b;
    }

    /** Their items (in the creative tab). Called once the list above is complete. */
    static void items() {
        List<DeferredBlock<Block>> list = new ArrayList<>(ALL);
        list.add(3, ICE);
        for (DeferredBlock<Block> b : list) {
            DeferredItem<BlockItem> item = ModDevices.ITEMS.registerSimpleBlockItem(b.getId().getPath(), b);
            ModDevices.tabItem(item);
        }
    }
}
