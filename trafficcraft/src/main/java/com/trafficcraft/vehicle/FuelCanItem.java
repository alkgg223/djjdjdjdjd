package com.trafficcraft.vehicle;

import net.minecraft.world.item.Item;

/** Refuels a vehicle by 20 L when used on it (handled in VehicleEntity#interact). */
public class FuelCanItem extends Item {
    public static final float LITRES = 20f;

    public FuelCanItem(Properties p) {
        super(p);
    }
}
