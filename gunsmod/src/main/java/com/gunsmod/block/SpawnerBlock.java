package com.gunsmod.block;

import com.gunsmod.Reg;
import com.gunsmod.menu.SpawnerMenu;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Deployment pad: keeps N soldiers built from a dog tag alive around it. */
public class SpawnerBlock extends FacingBlock implements EntityBlock {
    public SpawnerBlock(Properties props) {
        super(props, box(0, 0, 0, 16, 3, 16));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(SpawnerBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == Reg.SPAWNER_BE.get() ? (l, p, s, be) -> ((SpawnerBlockEntity) be).tick() : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SpawnerBlockEntity be) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SpawnerMenu(id, inv, be), Component.translatable("block.gunsmod.bot_spawner")), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SpawnerBlockEntity be) {
            net.minecraft.world.Containers.dropContents(level, pos, be.inventory);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
