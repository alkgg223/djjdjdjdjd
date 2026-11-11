package com.rpbusiness.item;

import com.rpbusiness.core.ModComponents;
import com.rpbusiness.delivery.Deliveries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A customer's parcel for a delivery job. Bring it to the destination and right-click to hand it over. */
public class ParcelItem extends Item {
    public ParcelItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp) Deliveries.handOverByPlayer(sp, st);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        Integer id = stack.get(ModComponents.PARCEL_JOB.get());
        BlockPos d = stack.get(ModComponents.PARCEL_DEST.get());
        if (id != null) tip.add(Component.translatable("tooltip.rpbusiness.parcel_job", id).withStyle(ChatFormatting.GOLD));
        if (d != null) tip.add(Component.translatable("tooltip.rpbusiness.parcel_dest", d.getX(), d.getY(), d.getZ()).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("tooltip.rpbusiness.parcel_use").withStyle(ChatFormatting.GRAY));
    }
}
