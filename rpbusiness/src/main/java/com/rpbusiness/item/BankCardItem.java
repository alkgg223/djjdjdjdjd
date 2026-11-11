package com.rpbusiness.item;

import com.rpbusiness.core.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A bank card linked to one account. Use it at ATMs; it is also needed for electronic payments. */
public class BankCardItem extends Item {
    public BankCardItem(Properties p) { super(p); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        String acc = stack.get(ModComponents.CARD_ACCOUNT.get());
        String holder = stack.get(ModComponents.CARD_HOLDER.get());
        if (acc == null) { tip.add(Component.translatable("tooltip.rpbusiness.card_blank").withStyle(ChatFormatting.GRAY)); return; }
        tip.add(Component.translatable("tooltip.rpbusiness.card_account", acc).withStyle(ChatFormatting.AQUA));
        if (holder != null) tip.add(Component.translatable("tooltip.rpbusiness.card_holder", holder).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.rpbusiness.card_use").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override public boolean isFoil(ItemStack s) { return s.has(ModComponents.CARD_ACCOUNT.get()); }
}
