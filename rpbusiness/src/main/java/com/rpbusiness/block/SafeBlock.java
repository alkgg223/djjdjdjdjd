package com.rpbusiness.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class SafeBlock extends StorageBlock {
    public SafeBlock(Properties p) { super(p, 27, FULL); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SafeBE(pos, state); }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof SafeBE s && placer instanceof Player p) s.owner = p.getUUID();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SafeBE s) {
            if (s.owner != null && !s.owner.equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("msg.rpbusiness.safe_locked").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            if (s.owner == null) s.owner = player.getUUID();
            player.openMenu(s);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SafeBE s && s.owner != null && !s.owner.equals(player.getUUID())) return 0f;
        return super.getDestroyProgress(state, player, level, pos);
    }
}
