package com.gunsmod.block;

import com.gunsmod.Reg;
import com.gunsmod.item.MissileItem;
import com.gunsmod.item.RemoteItem;
import com.gunsmod.item.RemoteLinks;
import com.gunsmod.net.ServerNet;
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
        return useOnStation(stack, level, pos, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return openStation(level, pos, player);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        giveRemote(level, pos, placer);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) dropMissile(level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    // ------------------------------------------------------------------------------------ shared by both launchers
    /** missiles load the station, remotes link to it (sneak: unlink) */
    public static ItemInteractionResult useOnStation(ItemStack stack, Level level, BlockPos station, Player player) {
        if (!(level.getBlockEntity(station) instanceof LauncherBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.getItem() instanceof MissileItem missile) {
            if (!level.isClientSide) {
                if (!be.accepts(missile.kind)) {
                    player.displayClientMessage(Component.translatable(be.isHeavy() ? "gunsmod.msg.missile_too_small" : "gunsmod.msg.missile_too_big"), true);
                } else if (be.hasMissile()) {
                    player.displayClientMessage(Component.translatable("gunsmod.msg.already_loaded"), true);
                } else if (be.loadMissile(missile.kind)) {
                    stack.consume(1, player);
                    level.playSound(null, station, Reg.sound("mag_in"), SoundSource.BLOCKS, 1.5f, be.isHeavy() ? 0.35f : 0.5f);
                    player.displayClientMessage(Component.translatable("gunsmod.msg.missile_loaded"), true);
                } else {
                    player.displayClientMessage(Component.translatable("gunsmod.msg.station_busy"), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.getItem() instanceof RemoteItem) {
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) RemoteItem.unlink(stack, station, player);
                else RemoteItem.link(stack, station, player);
                level.playSound(null, station, Reg.sound("ui_confirm"), SoundSource.BLOCKS, 1f, player.isShiftKeyDown() ? 0.7f : 1f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public static InteractionResult openStation(Level level, BlockPos station, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(station) instanceof LauncherBlockEntity) {
            ServerNet.sendStationStatus(sp, station, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** link the new station to a remote the placer already carries (if it has room), otherwise hand out a new remote */
    public static void giveRemote(Level level, BlockPos pos, @Nullable LivingEntity placer) {
        if (level.isClientSide || !(placer instanceof Player p)) return;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() instanceof RemoteItem && !RemoteItem.links(s).isFull()) {
                RemoteItem.link(s, pos, p);
                return;
            }
        }
        ItemStack remote = new ItemStack(Reg.REMOTE.get());
        RemoteItem.setLinks(remote, RemoteLinks.EMPTY.with(pos));
        if (!p.getInventory().add(remote)) p.drop(remote, false);
        p.displayClientMessage(Component.translatable("gunsmod.msg.remote_given"), false);
    }

    public static void dropMissile(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof LauncherBlockEntity be && be.hasMissile()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.missileStack());
        }
    }
}
