package com.rpbusiness.business;

import com.rpbusiness.food.Dish;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import java.util.UUID;

public class Order {
    public enum Status { PENDING, COOKING, READY, SERVED, CANCELLED }

    public int id;
    public UUID customer;
    public String customerName = "";
    public Dish dish;
    public int price;
    public Status status = Status.PENDING;
    public BlockPos seat;
    public long created;
    public UUID cook;
    public BlockPos readyAt;
    public int quality = -1;
    /** Tick at which the last shortage message was produced, to avoid spam. */
    public transient long shortageTick = -100000;
    public transient String shortage;

    public boolean active() { return status != Status.SERVED && status != Status.CANCELLED; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putUUID("customer", customer);
        t.putString("cname", customerName);
        t.putString("dish", dish.id());
        t.putInt("price", price);
        t.putString("status", status.name());
        if (seat != null) t.put("seat", NbtUtils.writeBlockPos(seat));
        t.putLong("created", created);
        if (cook != null) t.putUUID("cook", cook);
        if (readyAt != null) t.put("readyAt", NbtUtils.writeBlockPos(readyAt));
        t.putInt("quality", quality);
        return t;
    }

    public static Order load(CompoundTag t) {
        Order o = new Order();
        o.id = t.getInt("id");
        o.customer = t.getUUID("customer");
        o.customerName = t.getString("cname");
        o.dish = Dish.byId(t.getString("dish"));
        if (o.dish == null) o.dish = Dish.BURGER;
        o.price = t.getInt("price");
        try { o.status = Status.valueOf(t.getString("status")); } catch (Exception e) { o.status = Status.PENDING; }
        o.seat = NbtUtils.readBlockPos(t, "seat").orElse(null);
        o.created = t.getLong("created");
        if (t.hasUUID("cook")) o.cook = t.getUUID("cook");
        o.readyAt = NbtUtils.readBlockPos(t, "readyAt").orElse(null);
        o.quality = t.getInt("quality");
        return o;
    }
}
