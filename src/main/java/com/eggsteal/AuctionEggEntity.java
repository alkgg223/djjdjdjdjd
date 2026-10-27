package com.eggsteal;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The very rare egg of the center belt. First it rides the belt; once somebody buys it, it walks to the buyer's base.
 * Anyone with enough money can buy it again on the way: the price doubles and it turns toward the new buyer.
 */
public class AuctionEggEntity extends Entity {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(AuctionEggEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> PRICE = SynchedEntityData.defineId(AuctionEggEntity.class, EntityDataSerializers.LONG);
    public UUID lastBuyer;
    public long lastPaid;
    public int dest = -1, stage, mut;

    public AuctionEggEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(VARIANT, 0);
        b.define(PRICE, 0L);
    }

    public int variant() { return entityData.get(VARIANT); }
    public Creature creature() { return Creature.VALUES[Math.floorMod(variant(), Creature.VALUES.length)]; }
    public long price() { return entityData.get(PRICE); }
    public void setPrice(long p) { entityData.set(PRICE, p); }

    public void init(int variant, long price) {
        entityData.set(VARIANT, variant);
        entityData.set(PRICE, price);
        refreshName();
    }

    void refreshName() {
        if (level().isClientSide || level().getServer() == null) return;
        String holder = "";
        if (lastBuyer != null) {
            Economy.Stats st = Economy.get(level().getServer()).map.get(lastBuyer);
            if (st != null) holder = " §7(" + st.name + "'s)";
        }
        setCustomName(Component.literal(Mutation.of(mut).prefix() + creature().tag() + " §6$" + Economy.fmt(price()) + holder));
        setCustomNameVisible(true);
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isNoGravity() { return true; }
    @Override public boolean isPushable() { return false; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            if (sp.getUUID().equals(lastBuyer)) sp.displayClientMessage(Component.literal("§7It is already yours - it is on its way to your base."), true);
            else if (!Auction.buy(this, sp.getUUID(), sp.server)) sp.displayClientMessage(Component.literal("§cYou need $" + Economy.fmt(price()) + " to buy this egg!"), true);
        }
        return InteractionResult.SUCCESS;
    }

    private boolean stepTo(Vec3 t, double speed) {
        Vec3 d = t.subtract(position());
        double len = d.length();
        if (len <= speed) { setPos(t); return true; }
        setPos(position().add(d.scale(speed / len)));
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        setDeltaMovement(Vec3.ZERO);
        if (dest < 0) {
            setPos(getX() + Auction.BELT_SPEED, getY(), getZ());
            if (getX() > 8.6) {
                level().getServer().getPlayerList().broadcastSystemMessage(Component.literal("§7The " + creature().tag() + " §7egg slipped away unclaimed..."), false);
                discard();
            }
        } else {
            int d = Arena.dir(dest);
            Vec3 outside = new Vec3(Arena.cx(dest) + 0.5, 64.7, Arena.gapZ(dest) + d * 2.5);
            Vec3 home = new Vec3(Arena.cx(dest) + 0.5, 64.7, Arena.cz(dest) + 0.5);
            if (stage == 0) { if (stepTo(outside, Auction.WALK_SPEED)) stage = 1; }
            else if (stepTo(home, Auction.WALK_SPEED)) { Auction.deliver((ServerLevel) level(), this); return; }
        }
        if (tickCount % 20 == 0) refreshName();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("variant", variant());
        t.putLong("price", price());
        t.putLong("paid", lastPaid);
        t.putInt("dest", dest);
        t.putInt("stage", stage);
        t.putInt("mut", mut);
        if (lastBuyer != null) t.putUUID("buyer", lastBuyer);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        entityData.set(VARIANT, t.getInt("variant"));
        entityData.set(PRICE, t.getLong("price"));
        lastPaid = t.getLong("paid");
        dest = t.contains("dest") ? t.getInt("dest") : -1;
        stage = t.getInt("stage");
        mut = t.getInt("mut");
        if (t.hasUUID("buyer")) lastBuyer = t.getUUID("buyer");
    }
}
