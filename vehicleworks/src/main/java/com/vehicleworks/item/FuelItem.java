package com.vehicleworks.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Refined fuel. Right-click a fuel tank (in the world or on a vehicle) to refuel. */
public class FuelItem extends Item {
    public final double kg;

    public FuelItem(Properties props, double kg) {
        super(props);
        this.kg = kg;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("vehicleworks.tooltip.fuel", String.format("%.0f", kg)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
