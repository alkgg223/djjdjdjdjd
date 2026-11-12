package com.rpbusiness.business;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import java.util.UUID;

/** A hotel room drawn by the player with the Hotel Room Tool. One group of guests per room, never strangers. */
public class HotelRoom {
    public int id;
    public String name = "";
    public BlockPos min, max;
    /** Leader of the group staying in the room, or null when free. */
    public UUID guest;
    public long checkOutTick;
    public int nights;
    public boolean cleanRequested;
    public long requestTick;
    public long lastMessDay = Long.MIN_VALUE;
    /** Created automatically for a bed when the player has not drawn rooms yet. */
    public boolean auto;

    public boolean contains(BlockPos p) {
        return p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id); t.putString("name", name);
        t.put("min", NbtUtils.writeBlockPos(min)); t.put("max", NbtUtils.writeBlockPos(max));
        if (guest != null) t.putUUID("guest", guest);
        t.putLong("out", checkOutTick); t.putInt("nights", nights);
        t.putBoolean("req", cleanRequested); t.putLong("reqTick", requestTick); t.putLong("mess", lastMessDay); t.putBoolean("auto", auto);
        return t;
    }

    public static HotelRoom load(CompoundTag t) {
        HotelRoom r = new HotelRoom();
        r.id = t.getInt("id"); r.name = t.getString("name");
        r.min = NbtUtils.readBlockPos(t, "min").orElse(BlockPos.ZERO); r.max = NbtUtils.readBlockPos(t, "max").orElse(BlockPos.ZERO);
        if (t.hasUUID("guest")) r.guest = t.getUUID("guest");
        r.checkOutTick = t.getLong("out"); r.nights = t.getInt("nights");
        r.cleanRequested = t.getBoolean("req"); r.requestTick = t.getLong("reqTick"); r.lastMessDay = t.getLong("mess"); r.auto = t.getBoolean("auto");
        return r;
    }
}
