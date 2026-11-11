package com.rpbusiness.item;

import com.rpbusiness.economy.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Physical banknote. The value of a stack is value * count. */
public class CashItem extends Item {
    public final int value;
    public CashItem(int value, Properties p) { super(p); this.value = value; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.rpbusiness.cash_value", Money.fmt(value)).withStyle(ChatFormatting.GREEN));
        if (stack.getCount() > 1)
            tip.add(Component.translatable("tooltip.rpbusiness.cash_stack", Money.fmt((long) value * stack.getCount())).withStyle(ChatFormatting.DARK_GREEN));
    }
}
