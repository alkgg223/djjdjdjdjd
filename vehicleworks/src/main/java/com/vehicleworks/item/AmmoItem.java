package com.vehicleworks.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Fictional ammunition for vehicle weapon systems. Load it into an ammo box (or directly into a weapon). */
public class AmmoItem extends Item {
    public enum Kind { BULLET, SHELL, MISSILE, BOMB }

    public final Kind kind;
    public final int rounds;

    public AmmoItem(Properties props, Kind kind, int rounds) {
        super(props);
        this.kind = kind;
        this.rounds = rounds;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("vehicleworks.tooltip.ammo", rounds).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
