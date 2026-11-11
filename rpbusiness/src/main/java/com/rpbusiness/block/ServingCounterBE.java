package com.rpbusiness.block;

import com.rpbusiness.core.ModBlockEntities;
import com.rpbusiness.core.ModSounds;
import com.rpbusiness.food.Dish;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;

/** The "pass": finished dishes wait here until a waiter (or the player) carries them to the table. */
public class ServingCounterBE extends KitchenBE {
    public static final int SIZE = 4;
    private final ItemStack[] slots = new ItemStack[SIZE];

    public ServingCounterBE(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVING_COUNTER.get(), pos, state);
        Arrays.fill(slots, ItemStack.EMPTY);
    }

    public ItemStack get(int i) { return slots[i]; }
    public boolean hasSpace() { for (ItemStack s : slots) if (s.isEmpty()) return true; return false; }
    public boolean isEmpty() { for (ItemStack s : slots) if (!s.isEmpty()) return false; return true; }

    public boolean insert(ItemStack s) {
        for (int i = 0; i < SIZE; i++) {
            if (slots[i].isEmpty()) {
                slots[i] = s.copyWithCount(1);
                if (level != null) level.playSound(null, worldPosition, ModSounds.PLACE_FOOD.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
                sync();
                return true;
            }
        }
        return false;
    }

    public int find(Dish d) {
        int best = -1, bestQ = -1;
        for (int i = 0; i < SIZE; i++) {
            if (d.accepts(slots[i])) {
                int q = com.rpbusiness.food.Foods.qualityOf(slots[i]);
                if (q > bestQ) { best = i; bestQ = q; }
            }
        }
        return best;
    }

    public int count(Dish d) { int n = 0; for (ItemStack s : slots) if (d.accepts(s)) n++; return n; }

    public ItemStack take(int i) {
        ItemStack r = slots[i];
        slots[i] = ItemStack.EMPTY;
        sync();
        return r;
    }

    public ItemStack takeLast() {
        for (int i = SIZE - 1; i >= 0; i--) if (!slots[i].isEmpty()) return take(i);
        return ItemStack.EMPTY;
    }

    @Override public List<ItemStack> contents() { return Arrays.asList(slots); }

    @Override
    protected void saveAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.saveAdditional(t, p);
        for (int i = 0; i < SIZE; i++) t.put("s" + i, saveStack(slots[i], p));
    }

    @Override
    protected void loadAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.loadAdditional(t, p);
        for (int i = 0; i < SIZE; i++) slots[i] = loadStack(t.getCompound("s" + i), p);
    }
}
