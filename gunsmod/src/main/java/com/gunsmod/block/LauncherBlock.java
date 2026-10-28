package com.gunsmod.block;

import com.gunsmod.Reg;
import com.gunsmod.item.MissileItem;
import com.gunsmod.item.RemoteItem;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
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

/** Missile launch station. Load a missile, link a remote, set a target, arm, launch. */
public class LauncherBlock extends FacingBlock implements EntityBlock {
    public LauncherBlock(Properties props) {
        super(props, box(0, 0, 0, 16, 9, 16));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(LauncherBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LauncherBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == Reg.LAUNCHER_BE.get() ? (l, p, s, be) -> ((LauncherBlockEntity) be).tick() : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LauncherBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.getItem() instanceof MissileItem missile) {
            if (!level.isClientSide) {
                if (be.hasMissile()) {
                    player.displayClientMessage(Component.translatable("gunsmod.msg.already_loaded"), true);
                } else {
                    be.loadMissile(missile.kind);
                    stack.consume(1, player);
                    level.playSound(null, pos, Reg.sound("mag_in"), SoundSource.BLOCKS, 1.5f, 0.5f);
                    player.displayClientMessage(Component.translatable("gunsmod.msg.missile_loaded"), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.getItem() instanceof RemoteItem) {
            if (!level.isClientSide) {
                stack.set(Reg.LINK.get(), pos);
                level.playSound(null, pos, Reg.sound("ui_confirm"), SoundSource.BLOCKS, 1f, 1f);
                player.displayClientMessage(Component.translatable("gunsmod.msg.remote_linked", pos.getX(), pos.getY(), pos.getZ()), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof LauncherBlockEntity be) {
            be.sendStatus(sp, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p) {
            ItemStack remote = new ItemStack(Reg.REMOTE.get());
            remote.set(Reg.LINK.get(), pos);
            if (!p.getInventory().add(remote)) p.drop(remote, false);
            p.displayClientMessage(Component.translatable("gunsmod.msg.remote_given"), false);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LauncherBlockEntity be && be.hasMissile()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.missileStack());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
