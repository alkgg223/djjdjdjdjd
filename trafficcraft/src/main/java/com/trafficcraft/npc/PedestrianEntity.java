package com.trafficcraft.npc;

import com.trafficcraft.TrafficCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Pedestrian / worker NPC. Walks the pedestrian network, crosses at crossings, or does a job (cashier, attendant, officer). */
public class PedestrianEntity extends PathfinderMob implements Merchant {
    public static final int A_WALK = 0, A_IDLE = 1, A_WAIT = 2, A_PHONE = 3, A_SCAN = 4, A_REFUEL = 5, A_INSPECT = 6, A_SCARED = 7, A_WAVE = 8;
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(PedestrianEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(PedestrianEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(PedestrianEntity.class, EntityDataSerializers.INT);

    public PedBrain brain;
    public BlockPos workplace;
    private Player tradingPlayer;
    private MerchantOffers offers;

    public PedestrianEntity(EntityType<? extends PedestrianEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(SKIN, 0);
        b.define(KIND, "adult");
        b.define(ANIM, A_WALK);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        if (brain == null) brain = new PedBrain(this);
        goalSelector.addGoal(1, brain);
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 5f));
    }

    public int skin() { return entityData.get(SKIN); }
    public void setSkin(int s) { entityData.set(SKIN, s); }
    public String kind() { return entityData.get(KIND); }
    public void setKind(String k) {
        entityData.set(KIND, k);
        var sc = getAttribute(Attributes.SCALE);
        if (sc != null) sc.setBaseValue(k.equals("child") ? 0.62 : k.equals("elderly") ? 0.96 : 1.0);
    }
    public int anim() { return entityData.get(ANIM); }
    public void setAnim(int a) {
        if (anim() == a) return;
        entityData.set(ANIM, a);
        if (!level().isClientSide) {
            ItemStack hand = switch (a) {
                case A_REFUEL -> new ItemStack(com.trafficcraft.registry.TCRegistry.FUEL_NOZZLE.get());
                case A_INSPECT -> new ItemStack(com.trafficcraft.registry.TCRegistry.PASSPORT.get());
                case A_SCAN -> new ItemStack(Items.PAPER);
                default -> ItemStack.EMPTY;
            };
            setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, hand);
            setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f);
        }
    }
    public boolean isWorker() {
        String k = kind();
        return k.equals("cashier") || k.equals("attendant") || k.equals("officer");
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return isWorker() && super.shouldBeSaved();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Skin", skin());
        t.putString("Kind", kind());
        if (workplace != null) t.putLong("Work", workplace.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        setSkin(t.getInt("Skin"));
        setKind(t.contains("Kind") ? t.getString("Kind") : "adult");
        if (t.contains("Work")) workplace = BlockPos.of(t.getLong("Work"));
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (isWorker() && src.getEntity() instanceof Player p && !p.isCreative()) return false;
        boolean r = super.hurt(src, amount);
        if (r && !level().isClientSide) brain.scare(60);
        return r;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        switch (kind()) {
            case "cashier" -> {
                setTradingPlayer(player);
                openTradingScreen(player, Component.translatable("npc.trafficcraft.cashier"), 1);
            }
            case "attendant" -> player.displayClientMessage(Component.translatable("npc.trafficcraft.attendant_hello"), false);
            case "officer" -> Workplaces.officerTalk(this, player);
            default -> {
                player.displayClientMessage(Component.translatable("npc.trafficcraft.ped_hello." + (getRandom().nextInt(4))), true);
                setAnim(A_WAVE);
                brain.pause(40);
            }
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------- merchant (cashier sells the shop goods)
    @Override
    public void setTradingPlayer(@Nullable Player p) {
        tradingPlayer = p;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        if (offers == null) {
            offers = new MerchantOffers();
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(com.trafficcraft.registry.TCRegistry.FUEL_CAN.get()), 64, 1, 0.05f));
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 4), 64, 1, 0.05f));
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.COOKIE, 8), 64, 1, 0.05f));
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.HONEY_BOTTLE, 2), 64, 1, 0.05f));
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(com.trafficcraft.registry.TCRegistry.PASSPORT.get()), 16, 1, 0.05f));
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(com.trafficcraft.registry.TCRegistry.CAR_KEY.get()), 16, 1, 0.05f));
        }
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers o) {
        offers = o;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        Workplaces.sale(this, offer.getCostA().getCount());
    }

    @Override
    public void notifyTradeUpdated(ItemStack s) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getNotifyTradeSound() {
        return net.minecraft.sounds.SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide;
    }

    static {
        TrafficCraft.LOG.debug("pedestrian class loaded");
    }
}
