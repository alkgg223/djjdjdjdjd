package com.gunsmod.block;

import com.gunsmod.Reg;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible filler block of the heavy launcher deck; forwards clicks to the centre block and breaks it with itself. */
public class HeavyLauncherPartBlock extends Block {
    private static final VoxelShape DECK = Block.box(0, 0, 0, 16, 10, 16);

    public HeavyLauncherPartBlock(Properties props) {
        super(props);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends Block> codec() {
        return simpleCodec(HeavyLauncherPartBlock::new);
    }

    /** the centre block whose footprint contains this position */
    @Nullable
    public static BlockPos findMaster(BlockGetter level, BlockPos pos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos c = pos.offset(dx, 0, dz);
                BlockState st = level.getBlockState(c);
                if (st.is(Reg.HEAVY_LAUNCHER.get()) && HeavyLauncherBlock.footprint(c, st.getValue(FacingBlock.FACING)).contains(pos)) return c;
            }
        }
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return DECK;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos m = findMaster(level, pos);
        return m == null ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : LauncherBlock.useOnStation(stack, level, m, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos m = findMaster(level, pos);
        return m == null ? InteractionResult.PASS : LauncherBlock.openStation(level, m, player);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos m = findMaster(level, pos);
        if (m != null && !level.isClientSide) {
            if (player.isCreative()) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            else level.destroyBlock(m, true, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // broken by an explosion or a piston: take the whole launcher down
        if (!state.is(newState.getBlock())) {
            BlockPos m = findMaster(level, pos);
            if (m != null) level.destroyBlock(m, true);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(Reg.HEAVY_LAUNCHER.get());
    }
}
