package com.eggsteal.brainrot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Right-click to open the Brainrot Heist shop (or the guide). */
public class BrStationBlock extends Block {
    private final boolean guide;

    public BrStationBlock(Properties p, boolean guide) {
        super(p);
        this.guide = guide;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) BrShop.open(sp, guide, -1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
