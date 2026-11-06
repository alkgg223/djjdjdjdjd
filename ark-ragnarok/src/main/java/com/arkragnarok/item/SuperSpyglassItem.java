package com.arkragnarok.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.TooltipFlag;

/**
 * Zooms like a spyglass; while it is held or used, the creature you look at (up to 150 blocks) shows its level,
 * health, torpor, taming progress and exactly which food - and how much of it - tames it (see SpyglassHud).
 */
public class SuperSpyglassItem extends SpyglassItem {
    public SuperSpyglassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.arkragnarok.super_spyglass.desc").withStyle(ChatFormatting.GRAY));
    }
}
