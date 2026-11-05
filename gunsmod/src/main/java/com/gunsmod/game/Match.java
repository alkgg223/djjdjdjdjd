package com.gunsmod.game;

import com.gunsmod.entity.BotTeam;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * The battle of a dimension, run from a mission control table: its mode and settings, the clock, both sides' tickets
 * and score, the carried and dropped flags of capture the flag, the convoy, and what the optional objectives came to.
 * Saved with the world, so a battle goes on after a restart.
 */
public class Match extends SavedData {
    public GameMode mode = GameMode.CONQUEST;
    public boolean running;
    @Nullable public BlockPos control;
    public int radius = 320;
    public BotTeam attackers = BotTeam.RED;
    public int minutes = GameMode.CONQUEST.minutes(), limit = GameMode.CONQUEST.limit();
    public int timeLeft;
    public final int[] tickets = new int[2], score = new int[2], vehicleKills = new int[2], returns = new int[2];
    /** -1 none yet, 0 / 1 that side won, 2 a draw */
    public int winner = -1;
    // capture the flag: who carries the flag OF each side, and where a dropped one lies
    public final UUID[] carrier = new UUID[2];
    public final Vec3[] dropped = new Vec3[2];
    public final long[] droppedAt = new long[2];
    // convoy
    public final List<UUID> convoy = new ArrayList<>();
    public final Set<UUID> arrived = new HashSet<>();
    public int convoyTotal, convoyLost;
    // optional objectives
    public final boolean[] heldAll = new boolean[2];
    public boolean targetLost;
    public int wave;
    public long nextWave;

    public static Match get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Match::new, Match::load), "gunsmod_match");
    }

    public static int idx(BotTeam t) {
        return t == BotTeam.RED ? 1 : 0;
    }

    public BotTeam defenders() {
        return attackers.enemy();
    }

    /** the escorting side of a convoy battle */
    public BotTeam escort() {
        return defenders();
    }

    public boolean covers(BlockPos p) {
        return control != null && control.distSqr(p) <= (double) radius * radius;
    }

    public boolean covers(Vec3 p) {
        return control != null && Vec3.atCenterOf(control).distanceToSqr(p) <= (double) radius * radius;
    }

    public void touch() {
        setDirty();
    }

    private static Match load(CompoundTag tag, HolderLookup.Provider reg) {
        Match m = new Match();
        try {
            m.mode = GameMode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException e) {
            m.mode = GameMode.CONQUEST;
        }
        m.running = tag.getBoolean("Running");
        if (tag.contains("Control")) m.control = NbtUtils.readBlockPos(tag, "Control").orElse(null);
        m.radius = tag.contains("Radius") ? tag.getInt("Radius") : 320;
        m.attackers = BotTeam.byId(tag.getInt("Attackers"));
        m.minutes = tag.getInt("Minutes");
        m.limit = tag.getInt("Limit");
        m.timeLeft = tag.getInt("TimeLeft");
        m.winner = tag.contains("Winner") ? tag.getInt("Winner") : -1;
        int[] t = tag.getIntArray("Tickets"), s = tag.getIntArray("Score"), v = tag.getIntArray("VehicleKills"), r = tag.getIntArray("Returns");
        for (int i = 0; i < 2; i++) {
            if (t.length > i) m.tickets[i] = t[i];
            if (s.length > i) m.score[i] = s[i];
            if (v.length > i) m.vehicleKills[i] = v[i];
            if (r.length > i) m.returns[i] = r[i];
            if (tag.hasUUID("Carrier" + i)) m.carrier[i] = tag.getUUID("Carrier" + i);
            if (tag.contains("DropX" + i)) m.dropped[i] = new Vec3(tag.getDouble("DropX" + i), tag.getDouble("DropY" + i), tag.getDouble("DropZ" + i));
            m.droppedAt[i] = tag.getLong("DropAt" + i);
            m.heldAll[i] = tag.getBoolean("HeldAll" + i);
        }
        for (Tag e : tag.getList("Convoy", Tag.TAG_INT_ARRAY)) m.convoy.add(NbtUtils.loadUUID(e));
        for (Tag e : tag.getList("Arrived", Tag.TAG_INT_ARRAY)) m.arrived.add(NbtUtils.loadUUID(e));
        m.convoyTotal = tag.getInt("ConvoyTotal");
        m.convoyLost = tag.getInt("ConvoyLost");
        m.targetLost = tag.getBoolean("TargetLost");
        m.wave = tag.getInt("Wave");
        m.nextWave = tag.getLong("NextWave");
        return m;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        tag.putString("Mode", mode.name());
        tag.putBoolean("Running", running);
        if (control != null) tag.put("Control", NbtUtils.writeBlockPos(control));
        tag.putInt("Radius", radius);
        tag.putInt("Attackers", attackers.ordinal());
        tag.putInt("Minutes", minutes);
        tag.putInt("Limit", limit);
        tag.putInt("TimeLeft", timeLeft);
        tag.putInt("Winner", winner);
        tag.putIntArray("Tickets", tickets);
        tag.putIntArray("Score", score);
        tag.putIntArray("VehicleKills", vehicleKills);
        tag.putIntArray("Returns", returns);
        for (int i = 0; i < 2; i++) {
            if (carrier[i] != null) tag.putUUID("Carrier" + i, carrier[i]);
            if (dropped[i] != null) {
                tag.putDouble("DropX" + i, dropped[i].x);
                tag.putDouble("DropY" + i, dropped[i].y);
                tag.putDouble("DropZ" + i, dropped[i].z);
            }
            tag.putLong("DropAt" + i, droppedAt[i]);
            tag.putBoolean("HeldAll" + i, heldAll[i]);
        }
        ListTag c = new ListTag();
        for (UUID u : convoy) c.add(NbtUtils.createUUID(u));
        tag.put("Convoy", c);
        ListTag a = new ListTag();
        for (UUID u : arrived) a.add(NbtUtils.createUUID(u));
        tag.put("Arrived", a);
        tag.putInt("ConvoyTotal", convoyTotal);
        tag.putInt("ConvoyLost", convoyLost);
        tag.putBoolean("TargetLost", targetLost);
        tag.putInt("Wave", wave);
        tag.putLong("NextWave", nextWave);
        return tag;
    }
}
