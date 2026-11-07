package com.arkragnarok.item;

import com.arkragnarok.entity.TranqArrow;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A tranquilizer projectile shot from any bow or crossbow (they are in the minecraft:arrows tag).  The family:
 * narco dart (light, made in the hands from berries), tranquilizer arrow, potent tranq arrow (forged head, double
 * dose), shock tranq arrow (electrified - sparks on impact) and tranq bolt (heavy, for the biggest animals).
 */
public class TranqArrowItem extends ArrowItem {
    public final float torpor;
    public final double damage;

    public TranqArrowItem(Properties properties) {
        this(properties, com.arkragnarok.taming.Taming.TRANQ_ARROW, 0.8);
    }

    public TranqArrowItem(Properties properties, float torpor, double damage) {
        super(properties);
        this.torpor = torpor;
        this.damage = damage;
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
        return new TranqArrow(level, shooter, ammo.copyWithCount(1), weapon);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.arkragnarok.tranq.torpor", Math.round(torpor)).withStyle(ChatFormatting.DARK_AQUA));
    }
}
