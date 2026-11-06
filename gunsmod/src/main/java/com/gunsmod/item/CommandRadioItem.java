package com.gunsmod.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** The squad radio: use it to open the squad command board (the U key does the same). */
public class CommandRadioItem extends Item {
    /** set by the client: opens the board */
    public static Runnable openScreen = () -> { };

    public CommandRadioItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) openScreen.run();
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("gunsmod.tip.radio1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.radio2").withStyle(ChatFormatting.GRAY));
    }
}
