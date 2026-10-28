package com.gunsmod.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** 0 = high explosive, 1 = cluster, 2 = incendiary */
public class MissileItem extends Item {
    public final int kind;

    public MissileItem(int kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("gunsmod.desc.missile_" + kind).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.missile_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
