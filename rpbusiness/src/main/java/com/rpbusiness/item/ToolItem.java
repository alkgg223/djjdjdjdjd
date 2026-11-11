package com.rpbusiness.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Kitchen knife, spatula, mop: simple tools whose behaviour lives in the blocks they are used on. */
public class ToolItem extends Item {
    private final String hint;
    public ToolItem(String hint, Properties p) { super(p); this.hint = hint; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(hint).withStyle(ChatFormatting.GRAY));
    }
}
