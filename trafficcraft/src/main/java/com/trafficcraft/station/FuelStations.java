package com.trafficcraft.station;

import com.trafficcraft.block.GenericBE;
import com.trafficcraft.registry.TCRegistry;
import com.trafficcraft.vehicle.VehicleEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Fuel pump logic. Take the nozzle (right-click the pump), use it on a vehicle to refuel, right-click the pump again to
 * return it. Fuel comes from the nearest fuel tank; the bill (price per litre, in emeralds) is paid when the nozzle is
 * returned and goes into the station till, which the owner empties at the payment terminal.
 */
public final class FuelStations {
    public static final float RATE = 0.5f; // litres per tick

    public static void usePump(ServerPlayer p, BlockPos pos) {
        if (!(p.level().getBlockEntity(pos) instanceof GenericBE be)) return;
        CompoundTag d = be.data;
        ItemStack held = p.getMainHandItem();
        if (held.getItem() instanceof FuelNozzleItem) {
            returnNozzle(p.serverLevel(), be, p);
            return;
        }
        if (d.hasUUID("holder")) {
            p.displayClientMessage(Component.translatable("msg.trafficcraft.nozzle_in_use"), true);
            return;
        }
        if (!held.isEmpty()) {
            p.displayClientMessage(Component.translatable("msg.trafficcraft.empty_hand"), true);
            return;
        }
        ItemStack noz = new ItemStack(TCRegistry.FUEL_NOZZLE.get());
        FuelNozzleItem.bind(noz, pos);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, noz);
        d.putUUID("holder", p.getUUID());
        d.putFloat("litres", 0);
        d.putInt("vehicle", -1);
        be.sync();
        p.level().playSound(null, pos, TCRegistry.snd("misc.gear_shift"), SoundSource.BLOCKS, 0.8f, 0.7f);
    }

    public static void returnNozzle(ServerLevel level, GenericBE be, Player p) {
        CompoundTag d = be.data;
        float litres = d.getFloat("litres");
        float price = price(d);
        if (p != null) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                if (p.getInventory().getItem(i).getItem() instanceof FuelNozzleItem) p.getInventory().setItem(i, ItemStack.EMPTY);
            }
            int cost = (int) Math.ceil(litres * price);
            if (cost > 0 && !p.getAbilities().instabuild) {
                int paid = take(p, cost);
                d.putInt("till", d.getInt("till") + paid);
                p.displayClientMessage(Component.translatable("msg.trafficcraft.fuel_paid", String.format("%.1f", litres), paid), false);
            } else if (litres > 0) {
                p.displayClientMessage(Component.translatable("msg.trafficcraft.fuel_done", String.format("%.1f", litres)), true);
            }
        }
        d.remove("holder");
        d.putInt("vehicle", -1);
        d.putFloat("lastLitres", litres);
        d.putFloat("litres", 0);
        be.sync();
    }

    static int take(Player p, int n) {
        int got = 0;
        for (int i = 0; i < p.getInventory().getContainerSize() && got < n; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.EMERALD)) {
                int k = Math.min(n - got, s.getCount());
                s.shrink(k);
                got += k;
            }
        }
        return got;
    }

    public static float price(CompoundTag d) {
        return d.contains("price") ? d.getFloat("price") : 0.05f;
    }

    /** Called when the player uses the nozzle on a vehicle. */
    public static void startRefuel(Player p, VehicleEntity v, ItemStack nozzle) {
        BlockPos pump = FuelNozzleItem.pump(nozzle);
        if (pump == null || !(p.level().getBlockEntity(pump) instanceof GenericBE be)) return;
        if (v.spec().electric != (p.level().getBlockState(pump).getBlock().builtInRegistryHolder().key().location().getPath().equals("ev_charger"))) {
            p.displayClientMessage(Component.translatable(v.spec().electric ? "msg.trafficcraft.needs_charger" : "msg.trafficcraft.needs_fuel"), true);
            return;
        }
        be.data.putInt("vehicle", v.getId());
        be.sync();
        p.displayClientMessage(Component.translatable("msg.trafficcraft.refuel_start"), true);
    }

    public static void pumpTick(GenericBE be) {
        Level level = be.getLevel();
        CompoundTag d = be.data;
        if (!d.hasUUID("holder")) {
            // AI / attendant service
            int vid = d.getInt("serviceVehicle");
            if (vid > 0 && level.getEntity(vid) instanceof VehicleEntity v) {
                if (v.getFuel() < v.spec().fuelCapacity - 0.5f) transfer(level, be, v);
                else d.putInt("serviceVehicle", 0);
            }
            return;
        }
        UUID u = d.getUUID("holder");
        Player p = level.getPlayerByUUID(u);
        if (p == null || p.distanceToSqr(be.getBlockPos().getCenter()) > 8 * 8 || !hasNozzle(p)) {
            returnNozzle((ServerLevel) level, be, p);
            return;
        }
        int vid = d.getInt("vehicle");
        if (vid > 0) {
            Entity e = level.getEntity(vid);
            if (!(e instanceof VehicleEntity v) || v.distanceToSqr(be.getBlockPos().getCenter()) > 9 * 9) {
                d.putInt("vehicle", -1);
                be.sync();
                return;
            }
            if (!transfer(level, be, v)) {
                d.putInt("vehicle", -1);
                p.displayClientMessage(Component.translatable("msg.trafficcraft.tank_full"), true);
                be.sync();
            }
        }
    }

    /** Moves fuel from the station's tank into the vehicle. False when the vehicle is full or the station is dry. */
    static boolean transfer(Level level, GenericBE pump, VehicleEntity v) {
        float space = v.spec().fuelCapacity - v.getFuel();
        if (space <= 0.05f) return false;
        GenericBE tank = nearestTank(level, pump.getBlockPos());
        float amount = Math.min(RATE, space);
        if (tank != null) {
            float stock = tank.data.contains("stock") ? tank.data.getFloat("stock") : 20000f;
            if (stock <= 0) return false;
            amount = Math.min(amount, stock);
            tank.data.putFloat("stock", stock - amount);
            if (level.getGameTime() % 40 == 0) tank.sync();
        }
        v.setFuel(v.getFuel() + amount);
        pump.data.putFloat("litres", pump.data.getFloat("litres") + amount);
        pump.data.putFloat("total", pump.data.getFloat("total") + amount);
        if (level.getGameTime() % 10 == 0) pump.sync();
        if (level.getGameTime() % 30 == 0) level.playSound(null, pump.getBlockPos(), TCRegistry.snd("misc.fuel_pump"), SoundSource.BLOCKS, 0.6f, 1f);
        return true;
    }

    public static GenericBE nearestTank(Level level, BlockPos p) {
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-12, -4, -12), p.offset(12, 2, 12))) {
            BlockEntity be = level.getBlockEntity(q);
            if (be instanceof GenericBE g && level.getBlockState(q).getBlock().builtInRegistryHolder().key().location().getPath().equals("fuel_tank")) return g;
        }
        return null;
    }

    static boolean hasNozzle(Player p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).getItem() instanceof FuelNozzleItem) return true;
        return false;
    }

    /** Payment terminal: the owner collects the till of every pump nearby; others see prices. */
    public static void useTerminal(ServerPlayer p, BlockPos pos) {
        int collected = 0;
        float price = 0.05f;
        for (BlockPos q : BlockPos.betweenClosed(pos.offset(-16, -3, -16), pos.offset(16, 3, 16))) {
            if (p.level().getBlockEntity(q) instanceof GenericBE g && g.data.contains("till")) {
                collected += g.data.getInt("till");
                price = price(g.data);
                g.data.putInt("till", 0);
                g.sync();
            }
        }
        if (collected > 0) {
            p.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, collected));
            p.displayClientMessage(Component.translatable("msg.trafficcraft.till_collected", collected), false);
        } else {
            p.displayClientMessage(Component.translatable("msg.trafficcraft.terminal_info", String.format("%.2f", price)), true);
        }
    }
}
