package com.trafficcraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Facing block with a right-click action and no block entity (control panel, payment terminal, bench, workplace). */
public class UseBlock extends FacingBlock {
    public final String kind;

    public UseBlock(Properties p, double[][] boxes, boolean noCollide, String kind) {
        super(p, boxes, noCollide);
        this.kind = kind;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return BlockInteractions.use(kind, s, level, pos, player);
    }
}
