package com.gunsmod.item;

import com.gunsmod.Reg;
import com.gunsmod.block.LauncherBlockEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Launch remote. Use on a launch station to link it. Sneak + use anywhere to mark the block you look at (up to 400
 * blocks away) as the target. Use to open the launch control screen.
 */
public class RemoteItem extends Item {
    public RemoteItem(Properties props) {
        super(props);
    }

    public static boolean linkedTo(ItemStack s, BlockPos station) {
        return s.getItem() instanceof RemoteItem && station.equals(s.get(Reg.LINK.get()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockPos link = stack.get(Reg.LINK.get());
        if (link == null) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("gunsmod.msg.remote_unlinked"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                Vec3 eye = player.getEyePosition();
                Vec3 end = eye.add(player.getViewVector(1f).scale(400));
                BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockPos t = hit.getBlockPos();
                    level.getChunkAt(link);
                    if (level.getBlockEntity(link) instanceof LauncherBlockEntity be) {
                        be.setTarget(t.getX(), t.getY(), t.getZ(), player);
                        level.playSound(null, player.blockPosition(), Reg.sound("ui_confirm"), SoundSource.PLAYERS, 1f, 1.2f);
                        if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                            sl.sendParticles((ServerPlayer) player, net.minecraft.core.particles.ParticleTypes.END_ROD, true,
                                    t.getX() + 0.5, t.getY() + 1.2, t.getZ() + 0.5, 30, 0.3, 1.5, 0.3, 0.02);
                        }
                    } else {
                        player.displayClientMessage(Component.translatable("gunsmod.msg.station_missing"), true);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("gunsmod.msg.too_far"), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            level.getChunkAt(link);
            if (level.getBlockEntity(link) instanceof LauncherBlockEntity be) be.sendStatus(sp, true);
            else player.displayClientMessage(Component.translatable("gunsmod.msg.station_missing"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        BlockPos link = stack.get(Reg.LINK.get());
        if (link != null) tip.add(Component.translatable("gunsmod.tip.linked", link.getX(), link.getY(), link.getZ()).withStyle(ChatFormatting.GREEN));
        else tip.add(Component.translatable("gunsmod.tip.unlinked").withStyle(ChatFormatting.RED));
        tip.add(Component.translatable("gunsmod.tip.remote1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.remote2").withStyle(ChatFormatting.GRAY));
    }
}
