package com.gunsmod.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Where the loaded objectives are (server side), so a battle can find them without searching the world. */
public final class Objectives {
    private Objectives() {}

    private static final Set<ObjectiveBlockEntity> ALL = Collections.newSetFromMap(new WeakHashMap<>());

    static void track(ObjectiveBlockEntity be, boolean on) {
        if (on) ALL.add(be);
        else ALL.remove(be);
    }

    public static List<ObjectiveBlockEntity> near(Level level, BlockPos at, double radius) {
        List<ObjectiveBlockEntity> l = new ArrayList<>();
        for (ObjectiveBlockEntity be : ALL) {
            if (be.getLevel() == level && !be.isRemoved() && be.getBlockPos().distSqr(at) <= radius * radius) l.add(be);
        }
        l.sort((a, b) -> a.kind() != b.kind() ? a.kind().compareTo(b.kind()) : Character.compare(a.label(), b.label()));
        return l;
    }

    /** the next free letter for a new capture point round here */
    public static char nextLabel(Level level, BlockPos at) {
        boolean[] used = new boolean[26];
        for (ObjectiveBlockEntity be : near(level, at, 512)) {
            if (be.getBlockPos().equals(at) || be.kind() != ObjectiveBlock.Kind.POINT) continue;
            int i = be.label() - 'A';
            if (i >= 0 && i < 26) used[i] = true;
        }
        for (int i = 0; i < 26; i++) if (!used[i]) return (char) ('A' + i);
        return 'Z';
    }
}
