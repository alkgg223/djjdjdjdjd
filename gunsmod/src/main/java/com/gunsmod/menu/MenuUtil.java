package com.gunsmod.menu;

import java.util.function.Consumer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class MenuUtil {
    private MenuUtil() {}

    /** 27 inventory slots + 9 hotbar slots with the top-left at (x, y) */
    public static void addPlayerSlots(Consumer<Slot> add, Inventory inv, int x, int y) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) add.accept(new Slot(inv, 9 + r * 9 + c, x + c * 18, y + r * 18));
        }
        for (int c = 0; c < 9; c++) add.accept(new Slot(inv, c, x + c * 18, y + 58));
    }

    /** shift-click between the main inventory and the hotbar; {@code first} = index of the first player slot */
    public static ItemStack moveWithinPlayerInventory(AbstractContainerMenu menu, int index, int first) {
        Slot slot = menu.slots.get(index);
        if (!slot.hasItem() || index < first) return ItemStack.EMPTY;
        ItemStack st = slot.getItem();
        ItemStack copy = st.copy();
        int rel = index - first;
        boolean ok = rel < 27 ? moveTo(menu, st, first + 27, first + 36) : moveTo(menu, st, first, first + 27);
        if (!ok) return ItemStack.EMPTY;
        if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    private static boolean moveTo(AbstractContainerMenu menu, ItemStack st, int from, int to) {
        boolean moved = false;
        for (int i = from; i < to && !st.isEmpty(); i++) {
            Slot s = menu.slots.get(i);
            ItemStack in = s.getItem();
            if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, st)) {
                int max = Math.min(s.getMaxStackSize(in), in.getMaxStackSize());
                int n = Math.min(st.getCount(), max - in.getCount());
                if (n > 0) {
                    in.grow(n);
                    st.shrink(n);
                    s.setChanged();
                    moved = true;
                }
            }
        }
        for (int i = from; i < to && !st.isEmpty(); i++) {
            Slot s = menu.slots.get(i);
            if (!s.hasItem() && s.mayPlace(st)) {
                s.setByPlayer(st.split(Math.min(st.getCount(), s.getMaxStackSize(st))));
                moved = true;
            }
        }
        return moved;
    }
}
