package com.rpbusiness.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Shelf or refrigerated display where a shop shows its products. */
public class DisplayBlock extends StorageBlock {
    public final boolean cold;
    public DisplayBlock(Properties p, boolean cold) { super(p, 9, FULL); this.cold = cold; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DisplayBE(pos, state); }
}
