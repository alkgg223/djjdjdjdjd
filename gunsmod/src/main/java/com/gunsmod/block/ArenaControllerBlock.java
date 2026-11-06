package com.gunsmod.block;

import com.gunsmod.Reg;
import com.gunsmod.menu.ArenaMenu;
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

/** Battle command console: choose N vs M, player or AI-only matches, loadouts; start / stop rounds. */
public class ArenaControllerBlock extends FacingBlock implements EntityBlock {
    public ArenaControllerBlock(Properties props) {
        super(props, box(0, 0, 1, 16, 17.6, 15.5));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(ArenaControllerBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArenaBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && type == Reg.ARENA_BE.get() ? (l, p, s, be) -> ((ArenaBlockEntity) be).tick() : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ArenaBlockEntity be) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ArenaMenu(id, inv, be), Component.translatable("block.gunsmod.arena_controller")), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ArenaBlockEntity be) be.stop();
        super.onRemove(state, level, pos, newState, moved);
    }
}
