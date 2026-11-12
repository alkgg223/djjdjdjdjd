package com.rpbusiness.block;

import com.mojang.serialization.MapCodec;
import com.rpbusiness.core.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Litter and stains left by hotel guests. A cleaner (or the player with a Mop) removes it. */
public class RoomMessBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 0.5, 15);
    public RoomMessBlock(Properties p) { super(p); }

    @Override protected MapCodec<? extends Block> codec() { return MapCodec.unit(this); }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return net.minecraft.world.phys.shapes.Shapes.empty(); }
    @Override protected boolean isPathfindable(BlockState s, PathComputationType t) { return true; }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ModItems.MOP.get())) {
            if (!level.isClientSide) {
                clean(level, pos);
                player.displayClientMessage(Component.translatable("msg.rpbusiness.room_cleaned"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public static void clean(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof RoomMessBlock) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.playSound(null, pos, com.rpbusiness.core.ModSounds.CLEAN.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.1f);
        }
    }
}
