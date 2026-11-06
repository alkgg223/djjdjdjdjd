package com.gunsmod.block;

import com.gunsmod.Reg;
import com.gunsmod.item.VehicleItem;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Vehicle pad: use any vehicle item (jeep, tank, helicopter) on it and that vehicle appears on top. A new one only
 * comes out after the last one was destroyed. Sneak + empty hand takes the vehicle item back.
 */
public class VehicleSpawnerBlock extends FacingBlock implements EntityBlock {
    public VehicleSpawnerBlock(Properties props) {
        super(props, box(0, 0, 0, 16, 4, 16));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(VehicleSpawnerBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VehicleSpawnerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == Reg.VEHICLE_SPAWNER_BE.get() ? (l, p, s, be) -> ((VehicleSpawnerBlockEntity) be).tick() : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!VehicleItem.isVehicle(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof VehicleSpawnerBlockEntity be) {
            ItemStack old = be.setVehicle(stack);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
            player.displayClientMessage(Component.translatable("gunsmod.msg.spawner_set", be.vehicle().getHoverName()), true);
            level.playSound(null, pos, Reg.sound("arm_switch"), SoundSource.BLOCKS, 1f, 0.8f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof VehicleSpawnerBlockEntity be) {
            if (be.vehicle().isEmpty()) {
                player.displayClientMessage(Component.translatable("gunsmod.msg.spawner_empty"), true);
            } else if (player.isShiftKeyDown()) {
                ItemStack old = be.takeVehicle();
                if (!player.getInventory().add(old)) player.drop(old, false);
                player.displayClientMessage(Component.translatable("gunsmod.msg.spawner_cleared"), true);
            } else if (be.waiting()) {
                player.displayClientMessage(Component.translatable("gunsmod.msg.spawner_wait", be.vehicle().getHoverName(), be.secondsLeft()), true);
            } else {
                player.displayClientMessage(Component.translatable("gunsmod.msg.spawner_out", be.vehicle().getHoverName()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof VehicleSpawnerBlockEntity be && !be.vehicle().isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.vehicle().copy());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
