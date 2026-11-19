package com.trafficcraft.road;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-dimension persistent storage of the road network. */
public final class RoadData extends SavedData {
    public final RoadNetwork net = new RoadNetwork();

    public static RoadNetwork get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(RoadData::new, RoadData::load, null), "trafficcraft_roads").net;
    }

    public static RoadData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(RoadData::new, RoadData::load, null), "trafficcraft_roads");
    }

    private static RoadData load(CompoundTag t, HolderLookup.Provider p) {
        RoadData d = new RoadData();
        d.net.load(t);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
        return net.save();
    }

    @Override
    public boolean isDirty() {
        return true;
    }
}
