package com.realweather.device;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Eclipse glasses: worn on the head, they let you look straight at the sun during an eclipse (without them it hurts
 * your eyes in survival). Through them everything is black except the sun itself.
 */
public class EclipseGlasses extends Item implements Equipable {
    public EclipseGlasses(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.realweather.eclipse_glasses.tip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    public static boolean wearing(Player p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof EclipseGlasses;
    }
}
