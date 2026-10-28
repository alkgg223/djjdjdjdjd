package com.gunsmod.block;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.ai.CoverFinder;
import com.gunsmod.item.DogTagItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SpawnerBlockEntity extends BlockEntity {
    public final SimpleContainer inventory = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            SpawnerBlockEntity.this.setChanged();
            SpawnerBlockEntity.this.sync();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof DogTagItem;
        }
    };
    public int count = 3;
    public int delay = 5;
    public boolean active = false;
    private final List<UUID> alive = new ArrayList<>();
    private final Map<UUID, Integer> missing = new HashMap<>();
    private int cooldown;
    public int clientSpin;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> count;
                case 1 -> delay;
                case 2 -> active ? 1 : 0;
                case 3 -> alive.size();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> count = v;
                case 1 -> delay = v;
                case 2 -> active = v != 0;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public SpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(Reg.SPAWNER_BE.get(), pos, state);
    }

    public ItemStack tag() {
        return inventory.getItem(0);
    }

    public void button(int id) {
        switch (id) {
            case 0 -> count = Math.max(1, count - 1);
            case 1 -> count = Math.min(32, count + 1);
            case 2 -> delay = Math.max(0, delay - 1);
            case 3 -> delay = Math.min(120, delay + 1);
            case 4 -> {
                active = !active;
                cooldown = 0;
            }
            case 5 -> killAll();
            default -> { }
        }
        setChanged();
        sync();
    }

    private void killAll() {
        if (!(level instanceof ServerLevel sl)) return;
        for (UUID u : new ArrayList<>(alive)) {
            Entity e = sl.getEntity(u);
            if (e != null) e.discard();
        }
        alive.clear();
    }

    public void onBotDied(UUID id) {
        if (alive.remove(id)) cooldown = Math.max(cooldown, delay * 20);
        setChanged();
    }

    public void tick() {
        if (level == null) return;
        if (level.isClientSide) {
            clientSpin++;
            if (active && level.random.nextInt(4) == 0) {
                level.addParticle(Reg.HOLO.get(), worldPosition.getX() + 0.5, worldPosition.getY() + 0.25, worldPosition.getZ() + 0.5, 0, 0.02, 0);
            }
            return;
        }
        if (!(level instanceof ServerLevel sl) || level.getGameTime() % 10 != 0) return;
        // forget soldiers that died while their chunk was unloaded
        alive.removeIf(u -> {
            Entity e = sl.getEntity(u);
            if (e == null) {
                int m = missing.merge(u, 10, Integer::sum);
                return m > 1200;
            }
            missing.remove(u);
            return !e.isAlive();
        });
        if (!active || !(tag().getItem() instanceof DogTagItem) || !DogTagItem.hasData(tag())) return;
        if (cooldown > 0) {
            cooldown -= 10;
            return;
        }
        if (alive.size() < count) {
            spawnOne(sl);
            cooldown = delay * 20;
        }
    }

    private void spawnOne(ServerLevel sl) {
        BlockPos spot = null;
        for (int i = 0; i < 16 && spot == null; i++) {
            int r = 2 + i / 4;
            BlockPos c = worldPosition.offset(sl.random.nextInt(r * 2 + 1) - r, 0, sl.random.nextInt(r * 2 + 1) - r);
            if (c.equals(worldPosition)) continue;
            spot = CoverFinder.ground(sl, c);
        }
        if (spot == null) spot = worldPosition.above();
        BotEntity b = new BotEntity(Reg.BOT.get(), sl);
        b.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, sl.random.nextFloat() * 360f, 0);
        b.applyLoadout(DogTagItem.data(tag()));
        b.spawnerPos = worldPosition;
        b.home = worldPosition;
        if (b.getOrder() == BotEntity.ORDER_FOLLOW) b.setOrder(BotEntity.ORDER_PATROL);
        b.home = worldPosition;
        sl.addFreshEntity(b);
        alive.add(b.getUUID());
        sl.sendParticles(Reg.HOLO.get(), b.getX(), b.getY() + 1, b.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        sl.playSound(null, worldPosition, Reg.sound("ui_confirm"), SoundSource.BLOCKS, 1f, 0.8f);
        setChanged();
    }

    void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("Count", count);
        tag.putInt("Delay", delay);
        tag.putBoolean("Active", active);
        if (!tag().isEmpty()) tag.put("Tag", tag().save(reg));
        ListTag list = new ListTag();
        for (UUID u : alive) list.add(NbtUtils.createUUID(u));
        tag.put("Alive", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        count = Math.max(1, tag.getInt("Count"));
        delay = tag.getInt("Delay");
        active = tag.getBoolean("Active");
        inventory.setItem(0, tag.contains("Tag") ? ItemStack.parse(reg, tag.getCompound("Tag")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY);
        alive.clear();
        for (Tag t : tag.getList("Alive", Tag.TAG_INT_ARRAY)) alive.add(NbtUtils.loadUUID(t));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        saveAdditional(t, reg);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
