package com.arkragnarok.entity;

import com.arkragnarok.economy.ModEconomy;
import com.arkragnarok.world.NodeKind;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A resource node like in ARK: one big rock / metal rock / obsidian / crystal cluster you hit as a whole.  Every hit
 * puts resources straight into your inventory (hatchet = more stone, pick = more flint, metal, obsidian, crystal;
 * better tools give more).  When it is used up it crumbles and grows back after about ten minutes.
 * Solid: you collide with it and can stand on it.
 */
public class ResourceNode extends Entity {
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ResourceNode.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(ResourceNode.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DEPLETED = SynchedEntityData.defineId(ResourceNode.class, EntityDataSerializers.BOOLEAN);

    public static final long REGROW_TICKS = 12000;
    private float health = -1;
    private long regrowAt;
    /** client: hit shake */
    public int shake;

    public ResourceNode(EntityType<? extends ResourceNode> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(KIND, 0);
        b.define(SIZE, 1);
        b.define(DEPLETED, false);
    }

    public NodeKind kind() {
        return NodeKind.of(this.entityData.get(KIND));
    }

    public int size() {
        return Mth.clamp(this.entityData.get(SIZE), 0, 2);
    }

    public boolean depleted() {
        return this.entityData.get(DEPLETED);
    }

    public void setup(NodeKind kind, int size, float yaw) {
        this.entityData.set(KIND, kind.ordinal());
        this.entityData.set(SIZE, Mth.clamp(size, 0, 2));
        this.health = maxHealth();
        setYRot(yaw);
        refreshDimensions();
    }

    public float maxHealth() {
        return kind().health * (size() == 0 ? 0.5f : size() == 1 ? 1f : 2f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SIZE.equals(key) || DEPLETED.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (depleted()) return EntityDimensions.fixed(0.1f, 0.1f);
        return switch (size()) {
            case 0 -> EntityDimensions.fixed(1.05f, 0.75f);
            case 2 -> EntityDimensions.fixed(3.0f, 2.1f);
            default -> EntityDimensions.fixed(1.9f, 1.35f);
        };
    }

    @Override
    public boolean canBeCollidedWith() {
        return !depleted();
    }

    @Override
    public boolean isPickable() {
        return !depleted() && !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160 * 160;
    }

    @Override
    public void tick() {
        super.tick();
        if (shake > 0) shake--;
        if (!level().isClientSide && depleted() && level().getGameTime() >= regrowAt) {
            this.entityData.set(DEPLETED, false);
            health = maxHealth();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || depleted() || isInvulnerableTo(source)) return false;
        if (!(source.getEntity() instanceof Player p)) return false;
        return harvest(p, p.getMainHandItem(), amount, st -> {
            if (!p.getInventory().add(st)) p.drop(st, false);
        });
    }

    /**
     * one hit by a player or a survivor: resources go to {@code sink}, the tool wears, the node loses health and
     * crumbles when used up
     */
    public boolean harvest(LivingEntity by, ItemStack tool, float amount, java.util.function.Consumer<ItemStack> sink) {
        if (level().isClientSide || depleted()) return false;
        if (health < 0) health = maxHealth();
        boolean pick = tool.is(ItemTags.PICKAXES);
        boolean axe = tool.is(ItemTags.AXES);
        float tier = tool.getItem() instanceof TieredItem t ? t.getTier().getSpeed() : 1f;
        float m = 0.6f + tier * 0.25f;                       // wood 1.1, stone 1.6, iron 2.1, diamond 2.6, netherite 2.85
        RandomSource r = getRandom();
        NodeKind k = kind();
        switch (k) {
            case ROCK -> {
                give(sink, ModEconomy.STONE.get(), (axe ? 2.6f : pick ? 1.2f : 0.7f) * m, r);
                give(sink, ModEconomy.FLINT.get(), (pick ? 1.3f : axe ? 0.35f : 0.25f) * m, r);
            }
            case METAL -> {
                give(sink, ModEconomy.METAL_ORE.get(), (pick ? 1.2f : 0.25f) * m, r);
                give(sink, ModEconomy.STONE.get(), (axe ? 1.6f : 0.8f) * m, r);
                if (pick && r.nextFloat() < 0.12f * m) give(sink, ModEconomy.METAL_ORE.get(), 2, r);
                if (pick && r.nextFloat() < 0.2f * m) give(sink, ModEconomy.CRYSTAL.get(), 1, r);
            }
            case OBSIDIAN -> {
                if (pick && tier >= 6) give(sink, ModEconomy.OBSIDIAN.get(), 0.8f * m, r);
                else if (pick) give(sink, ModEconomy.FLINT.get(), 0.6f * m, r);
                else by.hurt(damageSources().generic(), 1.0f);       // bare hands on glassy obsidian hurt
            }
            case CRYSTAL -> {
                give(sink, ModEconomy.CRYSTAL.get(), (pick ? 1.5f : 0.4f) * m, r);
                if (pick) give(sink, ModEconomy.CRYSTAL.get(), 0.8f * m, r);
                give(sink, ModEconomy.STONE.get(), 0.4f * m, r);
            }
        }
        if (!tool.isEmpty() && (pick || axe)) tool.hurtAndBreak(1, by, EquipmentSlot.MAINHAND);
        health -= Math.max(1f, amount) * (pick ? 1.2f : axe ? 1.0f : 0.6f);
        level().broadcastEntityEvent(this, (byte) 4);
        BlockState bs = particleBlock();
        level().playSound(null, getX(), getY(), getZ(), bs.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0f, 0.7f + r.nextFloat() * 0.2f);
        if (health <= 0) {
            level().playSound(null, getX(), getY(), getZ(), bs.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.4f, 0.6f);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bs), getX(), getY() + getBbHeight() * 0.5, getZ(),
                        60, getBbWidth() * 0.4, getBbHeight() * 0.4, getBbWidth() * 0.4, 0.15);
            }
            this.entityData.set(DEPLETED, true);
            regrowAt = level().getGameTime() + REGROW_TICKS + r.nextInt(2400);
        }
        return true;
    }

    private static void give(java.util.function.Consumer<ItemStack> sink, Item item, float expected, RandomSource r) {
        int n = (int) expected + (r.nextFloat() < expected - (int) expected ? 1 : 0);
        if (n > 0) sink.accept(new ItemStack(item, n));
    }

    public BlockState particleBlock() {
        return switch (kind()) {
            case METAL -> Blocks.IRON_ORE.defaultBlockState();
            case OBSIDIAN -> Blocks.OBSIDIAN.defaultBlockState();
            case CRYSTAL -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            shake = 6;
            BlockState bs = particleBlock();
            for (int i = 0; i < 14; i++) {
                level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, bs), getX() + (random.nextDouble() - 0.5) * getBbWidth(),
                        getY() + random.nextDouble() * getBbHeight(), getZ() + (random.nextDouble() - 0.5) * getBbWidth(), 0, 0.1, 0);
            }
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(KIND, tag.getInt("Kind"));
        this.entityData.set(SIZE, tag.getInt("Size"));
        this.entityData.set(DEPLETED, tag.getBoolean("Depleted"));
        health = tag.contains("Health") ? tag.getFloat("Health") : -1;
        regrowAt = tag.getLong("RegrowAt");
        refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Kind", kind().ordinal());
        tag.putInt("Size", size());
        tag.putBoolean("Depleted", depleted());
        tag.putFloat("Health", health);
        tag.putLong("RegrowAt", regrowAt);
    }
}
