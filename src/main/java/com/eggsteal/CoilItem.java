package com.eggsteal;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Hold it in your main hand: Speed Coil = run fast, Jump Coil = jump very high (no fall damage in the arena). */
public class CoilItem extends Item {
    public final boolean jump;

    public CoilItem(boolean jump) {
        super(new Item.Properties().stacksTo(1));
        this.jump = jump;
    }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }
}
