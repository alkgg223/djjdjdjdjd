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

/** berries like in ARK: harvesting bushes, grass, ferns and leaves gives mejoberries and narcoberries */
@EventBusSubscriber(modid = ArkRagnarok.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WorldEvents {
    private WorldEvents() {}

    @SubscribeEvent
    public static void harvestPlants(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || e.getPlayer() == null || e.getPlayer().isCreative()) return;
        BlockState st = e.getState();
        boolean bush = st.is(Blocks.SWEET_BERRY_BUSH) || st.is(Blocks.TALL_GRASS) || st.is(Blocks.LARGE_FERN);
        boolean plant = bush || st.is(Blocks.SHORT_GRASS) || st.is(Blocks.FERN) || st.is(BlockTags.LEAVES);
        if (!plant) return;
        RandomSource r = level.random;
        float k = bush ? 1.0f : 0.45f;
        if (r.nextFloat() < 0.5f * k) Block.popResource(level, e.getPos(), new ItemStack(ModItems.MEJOBERRY.get(), 1 + r.nextInt(bush ? 4 : 2)));
        if (r.nextFloat() < 0.3f * k) Block.popResource(level, e.getPos(), new ItemStack(ModItems.NARCOBERRY.get(), 1 + r.nextInt(bush ? 3 : 2)));
    }
}
