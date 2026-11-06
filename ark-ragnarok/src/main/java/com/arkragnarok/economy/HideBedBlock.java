package com.arkragnarok.economy;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** wooden bed with a hide mattress and a fur blanket: a real bed (sleep, respawn point), drawn by its 3D model */
public class HideBedBlock extends BedBlock {
    public HideBedBlock(Properties p) {
        super(DyeColor.BROWN, p);
    }

    @Override
    protected RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }
}
