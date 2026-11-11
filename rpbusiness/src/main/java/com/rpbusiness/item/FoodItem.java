package com.rpbusiness.item;

import com.rpbusiness.core.ModComponents;
import com.rpbusiness.food.Foods;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Any mod ingredient / prepared food / dish. Shows its quality and what it is used for. */
public class FoodItem extends Item {
    public FoodItem(Properties p) { super(p); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        int q = Foods.qualityOf(stack);
        ChatFormatting c = q >= 85 ? ChatFormatting.GREEN : q >= 60 ? ChatFormatting.YELLOW : q >= 30 ? ChatFormatting.GOLD : ChatFormatting.RED;
        tip.add(Component.translatable("tooltip.rpbusiness.quality", q).withStyle(c));
        String hint = Foods.hintFor(stack.getItem());
        if (hint != null) tip.add(Component.translatable(hint).withStyle(ChatFormatting.GRAY));
        if (stack.has(ModComponents.FLIPPED.get()) && Boolean.TRUE.equals(stack.get(ModComponents.FLIPPED.get())))
            tip.add(Component.translatable("tooltip.rpbusiness.flipped").withStyle(ChatFormatting.DARK_GRAY));
    }
}
