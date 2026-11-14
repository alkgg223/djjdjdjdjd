package com.vehicleworks.vehicle;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The blocks of a vehicle in structure-local coordinates (the vehicle core is at 0,0,0).
 * Block states and block entity data are preserved exactly, so a disassembled vehicle
 * restores every block with its properties and contents.
 */
public final class VehicleStructure {
    private final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<CompoundTag> blockEntities = new Long2ObjectOpenHashMap<>();
    private final Long2FloatOpenHashMap damage = new Long2FloatOpenHashMap();
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private boolean boundsDirty = true;
    /** incremented on every change; used to invalidate caches (meshes, physics) */
    public int version;

    public int size() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public BlockState get(long key) {
        return blocks.getOrDefault(key, Blocks.AIR.defaultBlockState());
    }

    public BlockState get(int x, int y, int z) {
        return get(BlockPos.asLong(x, y, z));
    }

    public boolean contains(long key) {
        return blocks.containsKey(key);
    }

    public void put(long key, BlockState state, CompoundTag be) {
        if (state.isAir()) {
            remove(key);
            return;
        }
        blocks.put(key, state);
        if (be != null) blockEntities.put(key, be);
        else blockEntities.remove(key);
        damage.remove(key);
        boundsDirty = true;
        version++;
    }

    public void remove(long key) {
        if (blocks.remove(key) != null) {
            blockEntities.remove(key);
            damage.remove(key);
            boundsDirty = true;
            version++;
        }
    }

    public CompoundTag blockEntity(long key) {
        return blockEntities.get(key);
    }

    public void setBlockEntity(long key, CompoundTag tag) {
        if (tag == null) blockEntities.remove(key);
        else blockEntities.put(key, tag);
    }

    public Long2ObjectMap<BlockState> blocks() {
        return blocks;
    }

    public Long2ObjectMap<CompoundTag> blockEntities() {
        return blockEntities;
    }

    public float damage(long key) {
        return damage.get(key);
    }

    public void setDamage(long key, float value) {
        if (value <= 0) damage.remove(key);
        else damage.put(key, value);
    }

    public Long2FloatOpenHashMap damageMap() {
        return damage;
    }

    private void updateBounds() {
        if (!boundsDirty) return;
        boundsDirty = false;
        if (blocks.isEmpty()) {
            minX = minY = minZ = maxX = maxY = maxZ = 0;
            return;
        }
        minX = minY = minZ = Integer.MAX_VALUE;
        maxX = maxY = maxZ = Integer.MIN_VALUE;
        for (long k : blocks.keySet()) {
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
    }

    public int minX() { updateBounds(); return minX; }
    public int minY() { updateBounds(); return minY; }
    public int minZ() { updateBounds(); return minZ; }
    public int maxX() { updateBounds(); return maxX; }
    public int maxY() { updateBounds(); return maxY; }
    public int maxZ() { updateBounds(); return maxZ; }

    public VehicleStructure copy() {
        VehicleStructure s = new VehicleStructure();
        s.blocks.putAll(blocks);
        for (var e : blockEntities.long2ObjectEntrySet()) s.blockEntities.put(e.getLongKey(), e.getValue().copy());
        s.damage.putAll(damage);
        s.version = version;
        s.boundsDirty = true;
        return s;
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        Object2IntOpenHashMap<BlockState> palette = new Object2IntOpenHashMap<>();
        ListTag paletteTag = new ListTag();
        int[] data = new int[blocks.size() * 4];
        int i = 0;
        for (var e : blocks.long2ObjectEntrySet()) {
            BlockState st = e.getValue();
            int idx = palette.computeIfAbsent(st, k -> {
                paletteTag.add(NbtUtils.writeBlockState(st));
                return paletteTag.size() - 1;
            });
            long k = e.getLongKey();
            data[i++] = BlockPos.getX(k);
            data[i++] = BlockPos.getY(k);
            data[i++] = BlockPos.getZ(k);
            data[i++] = idx;
        }
        tag.put("Palette", paletteTag);
        tag.putIntArray("Blocks", data);
        ListTag bes = new ListTag();
        for (var e : blockEntities.long2ObjectEntrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getLongKey());
            t.put("Tag", e.getValue());
            bes.add(t);
        }
        tag.put("BlockEntities", bes);
        ListTag dmg = new ListTag();
        for (var e : damage.long2FloatEntrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getLongKey());
            t.putFloat("D", e.getFloatValue());
            dmg.add(t);
        }
        tag.put("Damage", dmg);
        return tag;
    }

    public void load(CompoundTag tag, Level level) {
        blocks.clear();
        blockEntities.clear();
        damage.clear();
        HolderGetter<Block> getter = level.holderLookup(Registries.BLOCK);
        ListTag paletteTag = tag.getList("Palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) palette.add(NbtUtils.readBlockState(getter, paletteTag.getCompound(i)));
        int[] data = tag.getIntArray("Blocks");
        for (int i = 0; i + 3 < data.length; i += 4) {
            int idx = data[i + 3];
            if (idx < 0 || idx >= palette.size()) continue;
            BlockState st = palette.get(idx);
            if (!st.isAir()) blocks.put(BlockPos.asLong(data[i], data[i + 1], data[i + 2]), st);
        }
        ListTag bes = tag.getList("BlockEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < bes.size(); i++) {
            CompoundTag t = bes.getCompound(i);
            long k = t.getLong("Pos");
            if (blocks.containsKey(k)) blockEntities.put(k, t.getCompound("Tag"));
        }
        ListTag dmg = tag.getList("Damage", Tag.TAG_COMPOUND);
        for (int i = 0; i < dmg.size(); i++) {
            CompoundTag t = dmg.getCompound(i);
            damage.put(t.getLong("Pos"), t.getFloat("D"));
        }
        boundsDirty = true;
        version++;
    }

    // ------------------------------------------------------------------ network

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(blocks.size());
        for (var e : blocks.long2ObjectEntrySet()) {
            buf.writeLong(e.getLongKey());
            buf.writeVarInt(Block.getId(e.getValue()));
        }
        buf.writeVarInt(blockEntities.size());
        for (var e : blockEntities.long2ObjectEntrySet()) {
            buf.writeLong(e.getLongKey());
            buf.writeNbt(clientView(e.getValue()));
        }
        buf.writeVarInt(damage.size());
        for (var e : damage.long2FloatEntrySet()) {
            buf.writeLong(e.getLongKey());
            buf.writeFloat(e.getFloatValue());
        }
    }

    public void read(RegistryFriendlyByteBuf buf) {
        blocks.clear();
        blockEntities.clear();
        damage.clear();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            long k = buf.readLong();
            BlockState st = Block.stateById(buf.readVarInt());
            if (!st.isAir()) blocks.put(k, st);
        }
        int m = buf.readVarInt();
        for (int i = 0; i < m; i++) {
            long k = buf.readLong();
            CompoundTag t = (CompoundTag) buf.readNbt(net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            if (t != null) blockEntities.put(k, t);
        }
        int d = buf.readVarInt();
        for (int i = 0; i < d; i++) damage.put(buf.readLong(), buf.readFloat());
        boundsDirty = true;
        version++;
    }

    /** Block entity data visible to clients (inventories are stripped to keep packets small). */
    public static CompoundTag clientView(CompoundTag tag) {
        if (!tag.contains("Items")) return tag;
        CompoundTag c = tag.copy();
        c.remove("Items");
        return c;
    }
}
