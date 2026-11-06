package com.gunsmod.item;

import com.gunsmod.gun.AmmoType;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Ammo boxes keep their rounds as durability; rockets / grenades / single rounds are plain stacks. */
public class AmmoItem extends Item {
    public final AmmoType type;

    public AmmoItem(AmmoType type, Properties props) {
        super(props);
        this.type = type;
    }

    public int rounds(ItemStack stack) {
        return type.isBox() ? stack.getMaxDamage() - stack.getDamageValue() : stack.getCount();
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xE0B040;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        if (type.isBox()) {
            tip.add(Component.translatable("gunsmod.tip.rounds", rounds(stack), type.perItem).withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.translatable("gunsmod.tip.ammo_for." + type.name().toLowerCase()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
