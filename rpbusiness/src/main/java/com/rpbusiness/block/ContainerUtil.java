package com.rpbusiness.block;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/** Inventory helpers that work for mod storage and vanilla chests/barrels alike. */
public final class ContainerUtil {
    private ContainerUtil() {}

    public static int extract(Container c, Predicate<ItemStack> test, int n, List<ItemStack> out) {
        int got = 0;
        for (int i = 0; i < c.getContainerSize() && got < n; i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && test.test(s)) {
                int take = Math.min(n - got, s.getCount());
                out.add(c.removeItem(i, take));
                got += take;
            }
        }
        if (got > 0) c.setChanged();
        return got;
    }

    public static ItemStack insert(Container c, ItemStack stack) {
        ItemStack rem = stack.copy();
        for (int i = 0; i < c.getContainerSize() && !rem.isEmpty(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, rem) && s.getCount() < s.getMaxStackSize() && c.canPlaceItem(i, rem)) {
                int m = Math.min(rem.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(m);
                rem.shrink(m);
            }
        }
        for (int i = 0; i < c.getContainerSize() && !rem.isEmpty(); i++) {
            if (c.getItem(i).isEmpty() && c.canPlaceItem(i, rem)) { c.setItem(i, rem.copy()); rem = ItemStack.EMPTY; }
        }
        c.setChanged();
        return rem;
    }

    public static boolean hasSpace(Container c) {
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).isEmpty()) return true;
        return false;
    }
}
