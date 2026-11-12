package com.rpbusiness.block;

import com.mojang.serialization.MapCodec;
import com.rpbusiness.core.ModBlockEntities;
import com.rpbusiness.core.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import static com.rpbusiness.block.Shapes.bx;

/** Television: right-click to switch on/off. When on, the screen is animated and it plays sound. */
public class TvBlock extends KitchenBlock {
    public static final BooleanProperty ON = BooleanProperty.create("on");

    public TvBlock(Properties p) {
        super(p, Shapes.of(bx(1, 0, 6, 15, 11, 10)));
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(ON, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ON); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Be(pos, state); }

    public static void setOn(Level level, BlockPos pos, boolean on) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof TvBlock && s.getValue(ON) != on) {
            level.setBlock(pos, s.setValue(ON, on), 3);
            level.playSound(null, pos, ModSounds.CARD_BEEP.get(), SoundSource.BLOCKS, 0.3f, on ? 1.4f : 0.8f);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) setOn(level, pos, !state.getValue(ON));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide) return null;
        return (l, p, s, be) -> {
            if (s.getValue(ON) && (l.getGameTime() + p.asLong()) % 70 == 0)
                l.playLocalSound(p, ModSounds.TV.get(), SoundSource.BLOCKS, 0.55f, 0.95f + l.random.nextFloat() * 0.1f, false);
        };
    }

    public static class Be extends KitchenBE {
        public Be(BlockPos pos, BlockState state) { super(ModBlockEntities.TV.get(), pos, state); }
        @Override public java.util.List<net.minecraft.world.item.ItemStack> contents() { return java.util.List.of(); }
    }
}
