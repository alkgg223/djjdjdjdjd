package com.arkragnarok.entity;

import com.arkragnarok.registry.ModEntities;
import com.arkragnarok.registry.ModItems;
import com.arkragnarok.taming.Taming;
import javax.annotation.Nullable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Tranquilizer arrow: little damage, a lot of torpor (knocks dinosaurs out for taming); slows anything else down. */
public class TranqArrow extends AbstractArrow {
    public TranqArrow(EntityType<? extends TranqArrow> type, Level level) {
        super(type, level);
        setBaseDamage(0.8);
    }

    public TranqArrow(Level level, LivingEntity shooter, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ModEntities.TRANQ_ARROW.get(), shooter, level, pickup, weapon);
        setBaseDamage(0.8);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.TRANQ_ARROW.get());
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        float power = (float) Math.min(1.0, getDeltaMovement().length() / 3.0);
        float torpor = Taming.TRANQ_ARROW * (0.35f + 0.65f * power);
        if (target instanceof ArkCreature c) {
            c.addTorpor(torpor, getOwner());
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2), getEffectSource());
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0), getEffectSource());
        }
    }
}
