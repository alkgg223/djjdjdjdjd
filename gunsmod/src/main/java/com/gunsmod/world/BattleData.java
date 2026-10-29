package com.gunsmod.world;

import com.gunsmod.entity.BotTeam;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** The orchard battlefields of a dimension and the kill score of both teams there; saved with the world. */
public class BattleData extends SavedData {
    public final List<BattleMap> maps = new ArrayList<>();
    private int green, red;

    public static BattleData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(BattleData::new, BattleData::load), "gunsmod_battle");
    }

    private static BattleData load(CompoundTag tag, HolderLookup.Provider reg) {
        BattleData d = new BattleData();
        for (Tag t : tag.getList("Maps", Tag.TAG_COMPOUND)) {
            BattleMap m = BattleMap.load((CompoundTag) t);
            if (m != null) d.maps.add(m);
        }
        d.green = tag.getInt("Green");
        d.red = tag.getInt("Red");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag l = new ListTag();
        for (BattleMap m : maps) l.add(m.save());
        tag.put("Maps", l);
        tag.putInt("Green", green);
        tag.putInt("Red", red);
        return tag;
    }

    /** a newly built battlefield: the score starts again */
    public void addMap(BattleMap m) {
        maps.remove(m);
        maps.add(m);
        green = red = 0;
        setDirty();
    }

    @Nullable
    public BattleMap at(Vec3 p) {
        for (BattleMap m : maps) if (m.contains(p)) return m;
        return null;
    }

    /** the battlefield the position is on, or else the closest one */
    @Nullable
    public BattleMap nearest(Vec3 p) {
        BattleMap best = at(p);
        double bestD = Double.MAX_VALUE;
        if (best != null) return best;
        for (BattleMap m : maps) {
            double d = m.center().distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    public int score(BotTeam team) {
        return team == BotTeam.RED ? red : green;
    }

    public void addKill(BotTeam team) {
        if (team == BotTeam.RED) red++;
        else green++;
        setDirty();
    }
}
