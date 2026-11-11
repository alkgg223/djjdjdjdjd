package com.rpbusiness.block;

import com.mojang.serialization.MapCodec;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.core.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Dining table. Becomes DIRTY after a customer has eaten; customers will not sit at dirty tables. */
public class TableBlock extends Block {
    public static final BooleanProperty DIRTY = BooleanProperty.create("dirty");
    private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(Block.box(0, 13, 0, 16, 16, 16), Block.box(6, 0, 6, 10, 13, 10));

    public TableBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(DIRTY, false));
    }

    @Override protected MapCodec<? extends Block> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(DIRTY); }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ModItems.MOP.get()) && state.getValue(DIRTY)) {
            if (!level.isClientSide) {
                clean(level, pos);
                player.displayClientMessage(Component.translatable("msg.rpbusiness.table_cleaned"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getValue(DIRTY) && !level.isClientSide) {
            player.displayClientMessage(Component.translatable("msg.rpbusiness.table_dirty"), true);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public static void clean(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof TableBlock && s.getValue(DIRTY)) {
            level.setBlock(pos, s.setValue(DIRTY, false), 3);
            level.playSound(null, pos, ModSounds.CLEAN.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
            if (level instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 12, 0.35, 0.05, 0.35, 0.02);
        }
    }

    public static void dirty(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof TableBlock && !s.getValue(DIRTY)) level.setBlock(pos, s.setValue(DIRTY, true), 3);
    }
}
