package com.trafficcraft.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** The pump's nozzle while a player carries it. Use it on a vehicle to refuel; right-click the pump to hang it back. */
public class FuelNozzleItem extends Item {
    public FuelNozzleItem(Properties p) {
        super(p);
    }

    public static void bind(ItemStack s, BlockPos pump) {
        CompoundTag t = new CompoundTag();
        t.putLong("pump", pump.asLong());
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    public static BlockPos pump(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        CompoundTag t = d.copyTag();
        return t.contains("pump") ? BlockPos.of(t.getLong("pump")) : null;
    }
}
