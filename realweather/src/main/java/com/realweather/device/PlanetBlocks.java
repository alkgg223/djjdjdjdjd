package com.realweather.device;

import com.realweather.ModContent;
import com.realweather.space.Tiles;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;

/**
 * The blocks other worlds are made of (one for each tile of {@link Tiles}): what the telescope and the planet map show
 * their ground built from, what their worlds are made of when you travel there, and real blocks to build with too
 * (in the Real Weather tab).
 */
public final class PlanetBlocks {
    public static final List<DeferredBlock<Block>> ALL = new ArrayList<>();
    @SuppressWarnings("unchecked")
    private static final DeferredBlock<Block>[] BY_TILE = new DeferredBlock[Tiles.ALL.length];

    static {
        for (int i = 0; i < Tiles.ALL.length; i++) {
            Tiles.Tile t = Tiles.ALL[i];
            if (t == null) {
                continue;
            }
            BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(color(t)).strength(t.hardness(), t.hardness() * 2F)
                    .sound(sound(t.material())).lightLevel(s -> t.light());
            if (t.material() == Tiles.M_ICE) {
                p = p.friction(0.98F);
            }
            BlockBehaviour.Properties props = p;
            DeferredBlock<Block> b = ModContent.BLOCKS.register(t.name(), () -> new Block(props));
            BY_TILE[i] = b;
            ALL.add(b);
        }
    }

    private PlanetBlocks() {}

    private static SoundType sound(int material) {
        return switch (material) {
            case Tiles.M_DUST -> SoundType.SAND;
            case Tiles.M_ICE, Tiles.M_GLASS -> SoundType.GLASS;
            case Tiles.M_SNOW -> SoundType.SNOW;
            case Tiles.M_CLOUD -> SoundType.WOOL;
            case Tiles.M_PLANT -> SoundType.MOSS;
            case Tiles.M_CRYSTAL -> SoundType.AMETHYST;
            case Tiles.M_METAL -> SoundType.METAL;
            case Tiles.M_MUD -> SoundType.MUD;
            default -> SoundType.STONE;
        };
    }

    private static MapColor color(Tiles.Tile t) {
        String n = t.name();
        if (n.contains("mars") || n.contains("ironstone") || n.contains("hematite")) {
            return MapColor.TERRACOTTA_RED;
        }
        if (n.contains("ice") || n.contains("frost") || n.contains("snow") || n.contains("salt")) {
            return MapColor.ICE;
        }
        if (n.contains("cloud")) {
            return MapColor.WOOL;
        }
        if (n.contains("magma") || n.contains("lava") || n.contains("hot")) {
            return MapColor.NETHER;
        }
        if (n.contains("grass") || n.contains("moss")) {
            return MapColor.PLANT;
        }
        if (n.contains("sand") || n.contains("sulfur")) {
            return MapColor.SAND;
        }
        return MapColor.STONE;
    }

    /** The block of a tile (stone for the sea's tile, which is no block). */
    public static BlockState state(int tile) {
        DeferredBlock<Block> b = tile >= 0 && tile < BY_TILE.length ? BY_TILE[tile] : null;
        return b == null ? Blocks.STONE.defaultBlockState() : b.get().defaultBlockState();
    }

    /** Their items (in the creative tab). */
    static void items() {
        for (DeferredBlock<Block> b : ALL) {
            DeferredItem<BlockItem> item = ModDevices.ITEMS.registerSimpleBlockItem(b.getId().getPath(), b);
            ModDevices.tabItem(item);
        }
    }
}
