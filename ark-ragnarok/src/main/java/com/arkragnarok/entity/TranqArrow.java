package com.arkragnarok.entity;

import com.arkragnarok.registry.ModEntities;
import com.arkragnarok.registry.ModItems;
import com.arkragnarok.taming.Taming;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Tranquilizer arrow: little damage, a lot of torpor (knocks dinosaurs out for taming); slows anything else down. */
public class TranqArrow extends AbstractArrow {
    /** which arrow of the family it is (the client draws its model, the server reads its dose) */
    private static final EntityDataAccessor<ItemStack> VARIANT = SynchedEntityData.defineId(TranqArrow.class, EntityDataSerializers.ITEM_STACK);

    public TranqArrow(EntityType<? extends TranqArrow> type, Level level) {
        super(type, level);
        setBaseDamage(0.8);
    }

    public TranqArrow(Level level, LivingEntity shooter, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ModEntities.TRANQ_ARROW.get(), shooter, level, pickup, weapon);
        this.entityData.set(VARIANT, pickup.copyWithCount(1));
        setBaseDamage(pickup.getItem() instanceof com.arkragnarok.item.TranqArrowItem t ? t.damage : 0.8);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(VARIANT, ItemStack.EMPTY);
    }

    /** the arrow item it was shot as (for the model in flight) */
    public ItemStack variant() {
        ItemStack v = this.entityData.get(VARIANT);
        if (v.isEmpty()) v = getPickupItemStackOrigin();
        return v.isEmpty() ? new ItemStack(ModItems.TRANQ_ARROW.get()) : v;
    }

    private float dose() {
        return variant().getItem() instanceof com.arkragnarok.item.TranqArrowItem t ? t.torpor : Taming.TRANQ_ARROW;
    }

    private boolean shocking() {
        return variant().is(com.arkragnarok.economy.ModEconomy.SHOCK_TRANQ_ARROW.get());
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.TRANQ_ARROW.get());
    }

    @Override
    public void tick() {
        super.tick();
        // the shock arrow trails sparks in flight
        if (level().isClientSide && shocking() && !this.inGround && tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        float power = (float) Math.min(1.0, getDeltaMovement().length() / 3.0);
        float torpor = dose() * (0.35f + 0.65f * power);
        if (shocking() && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(), 30, target.getBbWidth() * 0.4,
                    target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.3);
            level().playSound(null, target.getX(), target.getY(), target.getZ(), net.minecraft.sounds.SoundEvents.BEEHIVE_SHEAR,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.8f, 1.8f);
        }
        if (target instanceof ArkCreature c) {
            c.addTorpor(torpor, getOwner());
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2), getEffectSource());
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0), getEffectSource());
        }
    }
}
