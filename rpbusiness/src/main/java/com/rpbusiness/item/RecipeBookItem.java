package com.rpbusiness.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class RecipeBookItem extends Item {
    public RecipeBookItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (level.isClientSide) com.rpbusiness.client.ClientHooks.openRecipeBook();
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
    }
}
