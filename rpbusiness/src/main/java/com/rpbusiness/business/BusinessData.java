package com.rpbusiness.business;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** World-saved registry of every business. Stored in the Overworld's data folder (data/rpbusiness_businesses.dat). */
public class BusinessData extends SavedData {
    public final LinkedHashMap<UUID, Business> businesses = new LinkedHashMap<>();
    public int nextOrderId = 1;
    public int nextSupplyId = 1;

    public static BusinessData get(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        return ow.getDataStorage().computeIfAbsent(new SavedData.Factory<>(BusinessData::new, BusinessData::load), "rpbusiness_businesses");
    }

    public static BusinessData get(ServerLevel level) { return get(level.getServer()); }

    public Business at(BlockPos pos) {
        for (Business b : businesses.values()) if (b.contains(pos)) return b;
        return null;
    }

    public Business byId(UUID id) { return id == null ? null : businesses.get(id); }

    public boolean overlaps(BlockPos min, BlockPos max, UUID ignore) {
        for (Business b : businesses.values()) {
            if (b.id.equals(ignore)) continue;
            if (min.getX() <= b.max.getX() && max.getX() >= b.min.getX() && min.getY() <= b.max.getY() && max.getY() >= b.min.getY()
                    && min.getZ() <= b.max.getZ() && max.getZ() >= b.min.getZ()) return true;
        }
        return false;
    }

    public Business employerOf(UUID employee) {
        for (Business b : businesses.values()) if (b.employees.containsKey(employee)) return b;
        return null;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag l = new ListTag();
        for (Business b : businesses.values()) l.add(b.save());
        tag.put("businesses", l);
        tag.putInt("nextOrder", nextOrderId);
        tag.putInt("nextSupply", nextSupplyId);
        return tag;
    }

    public static BusinessData load(CompoundTag tag, HolderLookup.Provider provider) {
        BusinessData d = new BusinessData();
        ListTag l = tag.getList("businesses", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            Business b = Business.load(l.getCompound(i));
            d.businesses.put(b.id, b);
        }
        d.nextOrderId = Math.max(1, tag.getInt("nextOrder"));
        d.nextSupplyId = Math.max(1, tag.getInt("nextSupply"));
        return d;
    }
}
