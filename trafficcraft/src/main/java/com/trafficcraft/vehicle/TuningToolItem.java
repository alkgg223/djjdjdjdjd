package com.trafficcraft.vehicle;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Workshop wrench: right-click a car to open the tuning screen and pick its parts and colour. */
public class TuningToolItem extends Item {
    public TuningToolItem(Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.trafficcraft.tuning_tool.tip"));
    }
}
