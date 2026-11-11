package com.rpbusiness.economy;

import com.rpbusiness.core.ModItems;
import com.rpbusiness.item.CashItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Physical cash handling. All operations run on the logical server and are atomic:
 * items are removed before value is credited elsewhere, so no duplication is possible.
 */
public final class Money {
    private Money() {}

    public static String fmt(long amount) {
        return (amount < 0 ? "-$" : "$") + String.format("%,d", Math.abs(amount));
    }

    public static int valueOf(ItemStack s) {
        return s.getItem() instanceof CashItem c ? c.value * s.getCount() : 0;
    }

    public static long countCash(Player p) {
        long total = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) total += valueOf(inv.getItem(i));
        return total;
    }

    /** Removes exactly {@code amount} worth of cash (making change). Returns false and changes nothing if not enough. */
    public static boolean takeCash(Player p, long amount) {
        if (amount < 0) return false;
        if (amount == 0) return true;
        long total = countCash(p);
        if (total < amount) return false;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof CashItem) inv.setItem(i, ItemStack.EMPTY);
        }
        giveCash(p, total - amount);
        inv.setChanged();
        return true;
    }

    /** Removes all cash from the inventory and returns its value. */
    public static long takeAllCash(Player p) {
        long total = countCash(p);
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof CashItem) inv.setItem(i, ItemStack.EMPTY);
        }
        inv.setChanged();
        return total;
    }

    /** Gives cash as the fewest banknotes possible. Overflow is dropped at the player's feet. */
    public static void giveCash(Player p, long amount) {
        Item[] notes = {ModItems.CASH_BUNDLE.get(), ModItems.CASH_100.get(), ModItems.CASH_50.get(), ModItems.CASH_20.get(),
                ModItems.CASH_10.get(), ModItems.CASH_5.get(), ModItems.CASH_1.get()};
        for (Item note : notes) {
            int v = ((CashItem) note).value;
            long n = amount / v;
            amount -= n * v;
            while (n > 0) {
                int c = (int) Math.min(64, n);
                n -= c;
                ItemStack st = new ItemStack(note, c);
                if (!p.getInventory().add(st)) p.drop(st, false);
            }
        }
    }
}
