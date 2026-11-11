package com.rpbusiness.business;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistent employment contract & configuration of one hired NPC. */
public class EmployeeRecord {
    public UUID id;
    public String name = "";
    public Job job = Job.WAITER;
    public int salary;
    public int skill = 1;
    public int xp;
    public int rank;
    public int startHour = 7, endHour = 23;
    public BlockPos standPos, workPos, servicePos, storagePos;
    public final List<BlockPos> route = new ArrayList<>();
    /** Specific placed workstations this employee is assigned to (grill, register, counter...). */
    public final List<BlockPos> stations = new ArrayList<>();
    public int skin;
    public int unpaidDays;
    public long hiredDay;
    public long tasksDone;
    /** Live status text (not saved). */
    public transient String status = "";

    public static final String[] RANKS = {"rank.rpbusiness.junior", "rank.rpbusiness.regular", "rank.rpbusiness.senior"};

    public boolean onStrike() { return unpaidDays > 0; }

    /** Efficiency multiplier from skill and rank (1.0 .. ~1.9). */
    public float efficiency() { return 1.0f + (skill - 1) * 0.15f + rank * 0.1f; }

    public boolean onShift(int hour) {
        if (startHour == endHour) return true;
        if (startHour < endHour) return hour >= startHour && hour < endHour;
        return hour >= startHour || hour < endHour;
    }

    public void addXp(int amount) {
        xp += amount;
        int need = 40 * skill * skill;
        if (skill < 5 && xp >= need) { xp -= need; skill++; }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("job", job.name());
        t.putInt("salary", salary);
        t.putInt("skill", skill);
        t.putInt("xp", xp);
        t.putInt("rank", rank);
        t.putInt("start", startHour);
        t.putInt("end", endHour);
        if (standPos != null) t.put("stand", NbtUtils.writeBlockPos(standPos));
        if (workPos != null) t.put("work", NbtUtils.writeBlockPos(workPos));
        if (servicePos != null) t.put("service", NbtUtils.writeBlockPos(servicePos));
        if (storagePos != null) t.put("storage", NbtUtils.writeBlockPos(storagePos));
        ListTag r = new ListTag();
        for (BlockPos p : route) r.add(NbtUtils.writeBlockPos(p));
        t.put("route", r);
        ListTag st = new ListTag();
        for (BlockPos p : stations) st.add(NbtUtils.writeBlockPos(p));
        t.put("stations", st);
        t.putInt("skin", skin);
        t.putInt("unpaid", unpaidDays);
        t.putLong("hired", hiredDay);
        t.putLong("tasks", tasksDone);
        return t;
    }

    public static EmployeeRecord load(CompoundTag t) {
        EmployeeRecord e = new EmployeeRecord();
        e.id = t.getUUID("id");
        e.name = t.getString("name");
        e.job = Job.byName(t.getString("job"));
        e.salary = t.getInt("salary");
        e.skill = Math.max(1, t.getInt("skill"));
        e.xp = t.getInt("xp");
        e.rank = t.getInt("rank");
        e.startHour = t.getInt("start");
        e.endHour = t.getInt("end");
        e.standPos = NbtUtils.readBlockPos(t, "stand").orElse(null);
        e.workPos = NbtUtils.readBlockPos(t, "work").orElse(null);
        e.servicePos = NbtUtils.readBlockPos(t, "service").orElse(null);
        e.storagePos = NbtUtils.readBlockPos(t, "storage").orElse(null);
        ListTag r = t.getList("route", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < r.size(); i++) {
            int[] a = r.getIntArray(i);
            if (a.length == 3) e.route.add(new BlockPos(a[0], a[1], a[2]));
        }
        ListTag st = t.getList("stations", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < st.size(); i++) {
            int[] a = st.getIntArray(i);
            if (a.length == 3) e.stations.add(new BlockPos(a[0], a[1], a[2]));
        }
        e.skin = t.getInt("skin");
        e.unpaidDays = t.getInt("unpaid");
        e.hiredDay = t.getLong("hired");
        e.tasksDone = t.getLong("tasks");
        return e;
    }
}
