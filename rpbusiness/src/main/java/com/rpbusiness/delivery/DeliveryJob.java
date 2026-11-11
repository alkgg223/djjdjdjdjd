package com.rpbusiness.delivery;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import java.util.UUID;

/** One job of a player-owned delivery company. */
public class DeliveryJob {
    public enum Kind { REQUEST, TRANSFER }
    public enum Status { OFFERED, SENDER_COMING, AWAITING_PICKUP, ON_THE_WAY, DELIVERED, CANCELLED }

    public int id;
    public Kind kind = Kind.REQUEST;
    public Status status = Status.OFFERED;
    public String client = "";
    /** REQUEST: destination point. TRANSFER: destination business. */
    public BlockPos dest;
    public UUID fromBusiness, toBusiness;
    public String item = "";
    public int count;
    public long reward;
    public long created;
    public UUID driver;
    public long statusTick;

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id); t.putString("kind", kind.name()); t.putString("status", status.name());
        t.putString("client", client);
        if (dest != null) t.put("dest", NbtUtils.writeBlockPos(dest));
        if (fromBusiness != null) t.putUUID("from", fromBusiness);
        if (toBusiness != null) t.putUUID("to", toBusiness);
        t.putString("item", item); t.putInt("count", count); t.putLong("reward", reward); t.putLong("created", created);
        if (driver != null) t.putUUID("driver", driver);
        t.putLong("st", statusTick);
        return t;
    }

    public static DeliveryJob load(CompoundTag t) {
        DeliveryJob j = new DeliveryJob();
        j.id = t.getInt("id");
        try { j.kind = Kind.valueOf(t.getString("kind")); } catch (Exception ignored) {}
        try { j.status = Status.valueOf(t.getString("status")); } catch (Exception ignored) {}
        j.client = t.getString("client");
        j.dest = NbtUtils.readBlockPos(t, "dest").orElse(null);
        if (t.hasUUID("from")) j.fromBusiness = t.getUUID("from");
        if (t.hasUUID("to")) j.toBusiness = t.getUUID("to");
        j.item = t.getString("item"); j.count = t.getInt("count"); j.reward = t.getLong("reward"); j.created = t.getLong("created");
        if (t.hasUUID("driver")) j.driver = t.getUUID("driver");
        j.statusTick = t.getLong("st");
        return j;
    }

    public boolean active() { return status != Status.DELIVERED && status != Status.CANCELLED && status != Status.OFFERED; }
}
