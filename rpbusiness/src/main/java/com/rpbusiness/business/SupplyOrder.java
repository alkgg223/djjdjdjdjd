package com.rpbusiness.business;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Goods bought from a supplier. They only enter the business inventory when a courier physically delivers them. */
public class SupplyOrder {
    public enum Status { ORDERED, IN_TRANSIT, DELIVERED, CANCELLED }

    public int id;
    public String supplier = "";
    public final List<ItemStack> goods = new ArrayList<>();
    public long cost, fee;
    public long placed, eta;
    public Status status = Status.ORDERED;
    public UUID courier;
    public long courierSpawned;

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("sup", supplier);
        ListTag g = new ListTag();
        for (ItemStack s : goods) {
            CompoundTag e = new CompoundTag();
            e.putString("item", BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            e.putInt("n", s.getCount());
            g.add(e);
        }
        t.put("goods", g);
        t.putLong("cost", cost); t.putLong("fee", fee);
        t.putLong("placed", placed); t.putLong("eta", eta);
        t.putString("status", status.name());
        if (courier != null) t.putUUID("courier", courier);
        t.putLong("cs", courierSpawned);
        return t;
    }

    public static SupplyOrder load(CompoundTag t) {
        SupplyOrder o = new SupplyOrder();
        o.id = t.getInt("id");
        o.supplier = t.getString("sup");
        ListTag g = t.getList("goods", Tag.TAG_COMPOUND);
        for (int i = 0; i < g.size(); i++) {
            CompoundTag e = g.getCompound(i);
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.getString("item")));
            o.goods.add(new ItemStack(it, e.getInt("n")));
        }
        o.cost = t.getLong("cost"); o.fee = t.getLong("fee");
        o.placed = t.getLong("placed"); o.eta = t.getLong("eta");
        try { o.status = Status.valueOf(t.getString("status")); } catch (Exception e) { o.status = Status.ORDERED; }
        if (t.hasUUID("courier")) o.courier = t.getUUID("courier");
        o.courierSpawned = t.getLong("cs");
        return o;
    }
}
