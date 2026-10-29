package com.eggsteal.brainrot;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Right-click to open the Brainrot Heist guide: how to play, all brainrots, mutations and chances. */
public class BrGuideItem extends Item {
    public BrGuideItem() { super(new Item.Properties().stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) BrShop.open(sp, true, -1);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
