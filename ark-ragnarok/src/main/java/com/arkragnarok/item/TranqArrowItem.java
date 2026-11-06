package com.arkragnarok.item;

import com.arkragnarok.entity.TranqArrow;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** shot from any bow or crossbow (it is in the minecraft:arrows tag) */
public class TranqArrowItem extends ArrowItem {
    public TranqArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
        return new TranqArrow(level, shooter, ammo.copyWithCount(1), weapon);
    }
}
