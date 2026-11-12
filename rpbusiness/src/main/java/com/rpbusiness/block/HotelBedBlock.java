package com.rpbusiness.block;

import com.rpbusiness.core.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Hotel room bed: guests stay here after checking in; it must be cleaned (Mop) before the next guest. */
public class HotelBedBlock extends ChairBlock {
    public static final BooleanProperty DIRTY = ChairBlock.DIRTY;
    private final int beds;

    public HotelBedBlock(Properties p) { this(p, 1); }

    public HotelBedBlock(Properties p, int beds) {
        super(p, BED_KIND);
        this.beds = beds;
    }

    @Override public int capacity() { return beds; }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ModItems.MOP.get()) && state.getValue(DIRTY)) {
            if (!level.isClientSide) {
                clean(level, pos);
                player.displayClientMessage(Component.translatable("msg.rpbusiness.room_cleaned"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public static void clean(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof HotelBedBlock && s.getValue(DIRTY)) {
            level.setBlock(pos, s.setValue(DIRTY, false), 3);
            level.playSound(null, pos, com.rpbusiness.core.ModSounds.CLEAN.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.0f);
        }
    }

    public static void dirty(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof HotelBedBlock && !s.getValue(DIRTY)) level.setBlock(pos, s.setValue(DIRTY, true), 3);
    }
}
