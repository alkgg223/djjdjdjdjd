package com.gunsmod.block;

import com.gunsmod.Reg;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Centre block of the 3 x 5 heavy launcher. Placing it fills the rest of the footprint with {@link HeavyLauncherPartBlock}s;
 * the whole platform is drawn by the block entity renderer.
 */
public class HeavyLauncherBlock extends FacingBlock implements EntityBlock {
    public HeavyLauncherBlock(Properties props) {
        super(props, box(0, 0, 0, 16, 10, 16));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(HeavyLauncherBlock::new);
    }

    /** the 15 positions (3 wide across the facing, 5 long along it) centred on the master block */
    public static List<BlockPos> footprint(BlockPos centre, Direction facing) {
        List<BlockPos> out = new ArrayList<>(15);
        Direction right = facing.getClockWise();
        for (int along = -2; along <= 2; along++) {
            for (int side = -1; side <= 1; side++) {
                out.add(centre.relative(facing, along).relative(right, side));
            }
        }
        return out;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState st = super.getStateForPlacement(ctx);
        Level level = ctx.getLevel();
        for (BlockPos p : footprint(ctx.getClickedPos(), st.getValue(FACING))) {
            if (p.equals(ctx.getClickedPos())) continue;
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced()) {
                if (level.isClientSide && ctx.getPlayer() != null) {
                    ctx.getPlayer().displayClientMessage(Component.translatable("gunsmod.msg.heavy_no_room"), true);
                }
                return null;
            }
        }
        return st;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        for (BlockPos p : footprint(pos, state.getValue(FACING))) {
            if (!p.equals(pos)) level.setBlock(p, Reg.HEAVY_LAUNCHER_PART.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        LauncherBlock.giveRemote(level, pos, placer);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            LauncherBlock.dropMissile(level, pos);
            for (BlockPos p : footprint(pos, state.getValue(FACING))) {
                if (!p.equals(pos) && level.getBlockState(p).is(Reg.HEAVY_LAUNCHER_PART.get())) level.removeBlock(p, false);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeavyLauncherBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == Reg.HEAVY_LAUNCHER_BE.get() ? (l, p, s, be) -> ((LauncherBlockEntity) be).tick() : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return LauncherBlock.useOnStation(stack, level, pos, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return LauncherBlock.openStation(level, pos, player);
    }
}
