package com.vehicleworks.vehicle;

import com.vehicleworks.Config;
import com.vehicleworks.block.ComponentBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Physical properties of blocks used in vehicles. A block stands for a 1 m structural cell
 * (frames, plating, interior) rather than solid material, so masses are "structural" masses:
 * a plank cell ≈ 110 kg, stone ≈ 380 kg, an iron block cell ≈ 1.1 t.
 */
public final class BlockPhysics {
    private static final Map<BlockState, Double> MASS_CACHE = new IdentityHashMap<>();

    private BlockPhysics() {}

    public static synchronized double mass(BlockState state) {
        Double m = MASS_CACHE.get(state);
        if (m == null) {
            m = computeMass(state);
            MASS_CACHE.put(state, m);
        }
        return m * Config.SERVER.blockMassScale.get();
    }

    private static double computeMass(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof ComponentBlock cb) return cb.type.massKg;
        double base;
        SoundType snd = s.getSoundType();
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (s.is(BlockTags.WOOL) || s.is(BlockTags.WOOL_CARPETS)) base = 12;
        else if (s.is(BlockTags.LEAVES)) base = 8;
        else if (snd == SoundType.GLASS || path.contains("glass")) base = 45;
        else if (s.is(BlockTags.PLANKS) || path.contains("planks") || s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_STAIRS)) base = 80;
        else if (s.is(BlockTags.LOGS)) base = 140;
        else if (path.equals("iron_block") || path.equals("netherite_block")) base = path.startsWith("netherite") ? 2200 : 1100;
        else if (path.equals("gold_block")) base = 1800;
        else if (path.contains("copper")) base = 850;
        else if (path.equals("obsidian") || path.equals("crying_obsidian")) base = 700;
        else if (snd == SoundType.METAL || snd == SoundType.NETHERITE_BLOCK || snd == SoundType.COPPER) base = 650;
        else if (snd == SoundType.WOOD || snd == SoundType.BAMBOO_WOOD || snd == SoundType.CHERRY_WOOD || snd == SoundType.NETHER_WOOD) base = 85;
        else if (snd == SoundType.WOOL) base = 12;
        else if (path.contains("concrete") || path.contains("terracotta")) base = 160;
        else if (snd == SoundType.STONE || snd == SoundType.DEEPSLATE || snd == SoundType.DEEPSLATE_BRICKS || snd == SoundType.POLISHED_DEEPSLATE
                || snd == SoundType.TUFF || snd == SoundType.NETHER_BRICKS || snd == SoundType.MUD_BRICKS) base = 380;
        else base = 150 + 40 * Math.min(10, Math.max(0, b.defaultDestroyTime()));
        // shape factor
        if (b instanceof SlabBlock) base *= 0.5;
        else if (b instanceof StairBlock) base *= 0.75;
        else if (b instanceof FenceBlock || b instanceof WallBlock || b instanceof IronBarsBlock || b instanceof FenceGateBlock) base *= 0.3;
        else if (b instanceof TrapDoorBlock || b instanceof CarpetBlock || b instanceof PressurePlateBlock) base *= 0.2;
        else if (b instanceof DoorBlock) base *= 0.35;
        else if (b instanceof TorchBlock || b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof BaseRailBlock
                || b instanceof FlowerBlock || b instanceof SignBlock || b instanceof LadderBlock) base = Math.min(base, 8);
        else if (b instanceof ChestBlock || b instanceof BarrelBlock) base = 60;
        return base;
    }

    /** Coulomb friction coefficient from Minecraft's slipperiness value. */
    public static float friction(BlockState s) {
        float mc = s.getBlock().getFriction();
        return (float) Math.max(0.05, Math.min(1.0, 1.6 - 1.55 * mc));
    }

    /** Ground roughness for tyres: 0 = paved runway, higher = grass, dirt, sand, snow. */
    public static float roughness(BlockState s) {
        if (s.is(net.minecraft.tags.BlockTags.SAND) || s.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK) || s.is(net.minecraft.world.level.block.Blocks.SNOW)
                || s.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW) || s.is(net.minecraft.world.level.block.Blocks.MUD)) return 2.5f;
        if (s.is(net.minecraft.world.level.block.Blocks.GRAVEL) || s.is(net.minecraft.world.level.block.Blocks.FARMLAND) || s.is(net.minecraft.world.level.block.Blocks.SOUL_SAND)
                || s.is(net.minecraft.world.level.block.Blocks.SOUL_SOIL)) return 1.8f;
        if (s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK)) return 1.0f;
        if (s.is(net.minecraft.tags.BlockTags.LOGS) || s.getSoundType() == net.minecraft.world.level.block.SoundType.GRASS) return 0.6f;
        return 0f;
    }

    /** Hit points of a block before it breaks off a vehicle. */
    public static float hitPoints(BlockState s) {
        float hardness = Math.max(0.2f, s.getBlock().defaultDestroyTime());
        float res = Math.min(1200f, s.getBlock().getExplosionResistance());
        if (s.getBlock() instanceof ComponentBlock) { hardness = 3.0f; res = 8f; }
        return Math.max(4f, Math.min(800f, hardness * 18f + res * 4f));
    }
}
