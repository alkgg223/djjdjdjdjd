package com.arkragnarok.event;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Resources like in ARK: trees give wood and thatch, grass / bushes / leaves give fiber (much more with a sickle)
 * and berries, stone gives stone and flint.
 */
@EventBusSubscriber(modid = ArkRagnarok.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WorldEvents {
    private WorldEvents() {}

    @SubscribeEvent
    public static void harvestPlants(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || e.getPlayer() == null || e.getPlayer().isCreative()) return;
        BlockState st = e.getState();
        RandomSource rr = level.random;
        net.minecraft.world.item.ItemStack tool = e.getPlayer().getMainHandItem();
        if (st.is(BlockTags.LOGS)) {
            // trees give the mod's wood and thatch (ARK): a hatchet more wood, bare hands or a pick more thatch
            boolean axe = tool.is(net.minecraft.tags.ItemTags.AXES);
            Block.popResource(level, e.getPos(), new ItemStack(com.arkragnarok.economy.ModEconomy.WOOD.get(), (axe ? 2 : 1) + rr.nextInt(2)));
            Block.popResource(level, e.getPos(), new ItemStack(com.arkragnarok.economy.ModEconomy.THATCH.get(), (axe ? 1 : 2) + rr.nextInt(2)));
            return;
        }
        if (st.is(Blocks.STONE) || st.is(Blocks.COBBLESTONE) || st.is(Blocks.ANDESITE) || st.is(Blocks.DIORITE) || st.is(Blocks.GRANITE)) {
            if (rr.nextFloat() < 0.35f) Block.popResource(level, e.getPos(), new ItemStack(com.arkragnarok.economy.ModEconomy.STONE.get()));
            if (rr.nextFloat() < 0.12f) Block.popResource(level, e.getPos(), new ItemStack(com.arkragnarok.economy.ModEconomy.FLINT.get()));
        }
        boolean sickle = tool.is(com.arkragnarok.economy.ModEconomy.METAL_SICKLE.get());
        if (st.is(Blocks.SHORT_GRASS) || st.is(Blocks.TALL_GRASS) || st.is(Blocks.FERN) || st.is(Blocks.LARGE_FERN) || st.is(Blocks.SWEET_BERRY_BUSH)
                || st.is(BlockTags.LEAVES)) {
            int f = (sickle ? 3 : 1) * (rr.nextFloat() < 0.6f ? 1 : 0) + (sickle ? rr.nextInt(2) : 0);
            if (f > 0) Block.popResource(level, e.getPos(), new ItemStack(com.arkragnarok.economy.ModEconomy.FIBER.get(), f));
            if (st.is(BlockTags.LEAVES) && rr.nextFloat() < 0.4f) Block.popResource(level, e.getPos(), new ItemStack(com.arkragnarok.economy.ModEconomy.THATCH.get()));
        }
        boolean bush = st.is(Blocks.SWEET_BERRY_BUSH) || st.is(Blocks.TALL_GRASS) || st.is(Blocks.LARGE_FERN);
        boolean plant = bush || st.is(Blocks.SHORT_GRASS) || st.is(Blocks.FERN) || st.is(BlockTags.LEAVES);
        if (!plant) return;
        RandomSource r = level.random;
        float k = bush ? 1.0f : 0.45f;
        if (r.nextFloat() < 0.5f * k) Block.popResource(level, e.getPos(), new ItemStack(ModItems.MEJOBERRY.get(), 1 + r.nextInt(bush ? 4 : 2)));
        if (r.nextFloat() < 0.3f * k) Block.popResource(level, e.getPos(), new ItemStack(ModItems.NARCOBERRY.get(), 1 + r.nextInt(bush ? 3 : 2)));
    }
}
