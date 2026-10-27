package com.eggsteal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Right-click to open the shop (or the base builder). */
public class StationBlock extends Block {
    private final boolean builder;

    public StationBlock(Properties p, boolean builder) {
        super(p);
        this.builder = builder;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) Shop.open(sp, builder);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
