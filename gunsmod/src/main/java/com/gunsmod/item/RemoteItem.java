package com.gunsmod.item;

import com.gunsmod.Reg;
import com.gunsmod.block.LauncherBlockEntity;
import com.gunsmod.net.ServerNet;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
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
 * Launch remote controlling up to {@link RemoteLinks#MAX} launch stations. Use on a station to link it (sneak to unlink),
 * sneak + use anywhere to mark the block you look at (up to 400 blocks away) as the selected station's target, use to
 * open the launch control screen.
 */
public class RemoteItem extends Item {
    public RemoteItem(Properties props) {
        super(props);
    }

    public static RemoteLinks links(ItemStack s) {
        RemoteLinks l = s.get(Reg.REMOTE_LINKS.get());
        if (l != null) return l;
        BlockPos old = s.get(Reg.LINK.get());       // remotes from version 1.0 knew a single station
        return old != null ? RemoteLinks.EMPTY.with(old) : RemoteLinks.EMPTY;
    }

    public static void setLinks(ItemStack s, RemoteLinks l) {
        s.set(Reg.REMOTE_LINKS.get(), l);
        s.remove(Reg.LINK.get());
    }

    public static boolean linkedTo(ItemStack s, BlockPos station) {
        return s.getItem() instanceof RemoteItem && links(s).contains(station);
    }

    /** the remote in the main hand, else the off hand, else EMPTY */
    public static ItemStack held(Entity e) {
        if (!(e instanceof net.minecraft.world.entity.LivingEntity le)) return ItemStack.EMPTY;
        if (le.getMainHandItem().getItem() instanceof RemoteItem) return le.getMainHandItem();
        if (le.getOffhandItem().getItem() instanceof RemoteItem) return le.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** link a station to this remote, or select it when already linked */
    public static void link(ItemStack remote, BlockPos station, Player player) {
        RemoteLinks l = links(remote);
        if (l.contains(station)) {
            setLinks(remote, l.with(station));
            player.displayClientMessage(Component.translatable("gunsmod.msg.remote_selected", l.indexOf(station) + 1), true);
            return;
        }
        if (l.isFull()) {
            player.displayClientMessage(Component.translatable("gunsmod.msg.remote_full", RemoteLinks.MAX), true);
            return;
        }
        RemoteLinks n = l.with(station);
        setLinks(remote, n);
        player.displayClientMessage(Component.translatable("gunsmod.msg.remote_linked", station.getX(), station.getY(), station.getZ(),
                n.stations().size(), RemoteLinks.MAX), true);
    }

    public static void unlink(ItemStack remote, BlockPos station, Player player) {
        RemoteLinks l = links(remote);
        if (!l.contains(station)) return;
        setLinks(remote, l.without(station));
        player.displayClientMessage(Component.translatable("gunsmod.msg.remote_unlinked_station", station.getX(), station.getY(), station.getZ()), true);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && stack.has(Reg.LINK.get())) setLinks(stack, links(stack));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        RemoteLinks links = links(stack);
        if (links.isEmpty()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("gunsmod.msg.remote_unlinked"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (player.isShiftKeyDown()) {
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getViewVector(1f).scale(400));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                player.displayClientMessage(Component.translatable("gunsmod.msg.too_far"), true);
                return InteractionResultHolder.success(stack);
            }
            BlockPos t = hit.getBlockPos();
            LauncherBlockEntity be = LauncherBlockEntity.at(level, links.selectedStation());
            if (be == null) {
                player.displayClientMessage(Component.translatable("gunsmod.msg.station_missing"), true);
            } else if (!be.setTarget(t.getX(), t.getY(), t.getZ())) {
                player.displayClientMessage(Component.translatable("gunsmod.msg.station_busy"), true);
            } else {
                player.displayClientMessage(Component.translatable("gunsmod.msg.target_set_n", links.selected() + 1, t.getX(), t.getY(), t.getZ()), true);
                level.playSound(null, player.blockPosition(), Reg.sound("ui_confirm"), SoundSource.PLAYERS, 1f, 1.2f);
                if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
                    sl.sendParticles(sp, net.minecraft.core.particles.ParticleTypes.END_ROD, true,
                            t.getX() + 0.5, t.getY() + 1.2, t.getZ() + 0.5, 30, 0.3, 1.5, 0.3, 0.02);
                }
            }
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer sp) ServerNet.sendRemoteStatus(sp, stack, true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        RemoteLinks l = links(stack);
        if (l.isEmpty()) {
            tip.add(Component.translatable("gunsmod.tip.unlinked").withStyle(ChatFormatting.RED));
        } else {
            tip.add(Component.translatable("gunsmod.tip.linked_n", l.stations().size(), RemoteLinks.MAX).withStyle(ChatFormatting.GREEN));
            BlockPos s = l.selectedStation();
            tip.add(Component.translatable("gunsmod.tip.selected", l.selected() + 1, s.getX(), s.getY(), s.getZ()).withStyle(ChatFormatting.DARK_GREEN));
        }
        tip.add(Component.translatable("gunsmod.tip.remote1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.remote2").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.remote3").withStyle(ChatFormatting.GRAY));
    }
}
