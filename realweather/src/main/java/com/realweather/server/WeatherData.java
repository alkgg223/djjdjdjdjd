package com.realweather.server;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-world weather settings and storm state, saved with the overworld. */
public class WeatherData extends SavedData {
    private static final String NAME = "realweather";

    // Overrides set by commands; -1 means natural (automatic) behaviour.
    public float rainOverride = -1F;
    public float snowOverride = -1F;
    public float thunderOverride = -1F;
    public float windOverride = -1F;

    public float windAngle;
    public long noiseSeed = 1234567L;
    public float eventBase = 0.5F;

    public float sandTarget;
    public float sandStrength;
    public int sandTicks;
    public float blizzardTarget;
    public float blizzardStrength;
    public int blizzardTicks;

    public boolean flood = true;
    public int floodDepth = 2;
    public boolean snowPile = true;
    public boolean freezing = true;
    public boolean villagers = true;
    public boolean natural = true;

    /** Flood water blocks placed by the mod, oldest first, so the water can drain later. */
    public final LongArrayList floodList = new LongArrayList();
    public final LongOpenHashSet floodSet = new LongOpenHashSet();

    public static WeatherData get(ServerLevel level) {
        WeatherData data = level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WeatherData::new, WeatherData::load), NAME);
        return data;
    }

    public static WeatherData load(CompoundTag tag, HolderLookup.Provider registries) {
        WeatherData d = new WeatherData();
        d.rainOverride = tag.getFloat("rainOverride");
        d.snowOverride = tag.getFloat("snowOverride");
        d.thunderOverride = tag.getFloat("thunderOverride");
        d.windOverride = tag.getFloat("windOverride");
        d.windAngle = tag.getFloat("windAngle");
        d.noiseSeed = tag.getLong("noiseSeed");
        d.eventBase = tag.getFloat("eventBase");
        d.sandTarget = tag.getFloat("sandTarget");
        d.sandStrength = tag.getFloat("sandStrength");
        d.sandTicks = tag.getInt("sandTicks");
        d.blizzardTarget = tag.getFloat("blizzardTarget");
        d.blizzardStrength = tag.getFloat("blizzardStrength");
        d.blizzardTicks = tag.getInt("blizzardTicks");
        d.flood = tag.getBoolean("flood");
        d.floodDepth = Math.max(1, tag.getInt("floodDepth"));
        d.snowPile = tag.getBoolean("snowPile");
        d.freezing = tag.getBoolean("freezing");
        d.villagers = tag.getBoolean("villagers");
        d.natural = tag.getBoolean("natural");
        for (long p : tag.getLongArray("floodBlocks")) {
            if (d.floodSet.add(p)) {
                d.floodList.add(p);
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putFloat("rainOverride", rainOverride);
        tag.putFloat("snowOverride", snowOverride);
        tag.putFloat("thunderOverride", thunderOverride);
        tag.putFloat("windOverride", windOverride);
        tag.putFloat("windAngle", windAngle);
        tag.putLong("noiseSeed", noiseSeed);
        tag.putFloat("eventBase", eventBase);
        tag.putFloat("sandTarget", sandTarget);
        tag.putFloat("sandStrength", sandStrength);
        tag.putInt("sandTicks", sandTicks);
        tag.putFloat("blizzardTarget", blizzardTarget);
        tag.putFloat("blizzardStrength", blizzardStrength);
        tag.putInt("blizzardTicks", blizzardTicks);
        tag.putBoolean("flood", flood);
        tag.putInt("floodDepth", floodDepth);
        tag.putBoolean("snowPile", snowPile);
        tag.putBoolean("freezing", freezing);
        tag.putBoolean("villagers", villagers);
        tag.putBoolean("natural", natural);
        tag.putLongArray("floodBlocks", floodList.toLongArray());
        return tag;
    }

    public void addFlood(long pos) {
        if (floodSet.add(pos)) {
            floodList.add(pos);
            setDirty();
        }
    }

    public void removeFlood(long pos) {
        if (floodSet.remove(pos)) {
            floodList.rem(pos);
            setDirty();
        }
    }
}
