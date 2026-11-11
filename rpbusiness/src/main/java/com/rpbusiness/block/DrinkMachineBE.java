package com.rpbusiness.block;

import com.rpbusiness.core.ModBlockEntities;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.core.ModSounds;
import com.rpbusiness.food.Foods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Fills paper cups with cola or coffee, using syrup / bean charges loaded into it. */
public class DrinkMachineBE extends KitchenBE {
    public static final int COLA = 0, COFFEE = 1, MAX_CHARGES = 64, BREW_TICKS = 50;
    private int colaCharges, coffeeCharges, mode, progress;
    private boolean busy;
    private ItemStack output = ItemStack.EMPTY;

    public DrinkMachineBE(BlockPos pos, BlockState state) { super(ModBlockEntities.DRINK_MACHINE.get(), pos, state); }

    public int charges(int m) { return m == COLA ? colaCharges : coffeeCharges; }
    public int mode() { return mode; }
    public boolean busy() { return busy; }
    public int progress() { return progress; }
    public ItemStack output() { return output; }
    public void toggleMode() { mode = 1 - mode; sync(); }

    public boolean addRefill(Item item) {
        if (item == ModItems.COLA_SYRUP.get() && colaCharges + 8 <= MAX_CHARGES) { colaCharges += 8; sync(); return true; }
        if (item == ModItems.COFFEE_BEANS.get() && coffeeCharges + 8 <= MAX_CHARGES) { coffeeCharges += 8; sync(); return true; }
        return false;
    }

    public boolean canDispense(int m) { return !busy && output.isEmpty() && charges(m) > 0; }

    /** Consumes one cup (caller removes it) and starts filling. */
    public boolean start(int m) {
        if (!canDispense(m)) return false;
        mode = m;
        if (m == COLA) colaCharges--; else coffeeCharges--;
        busy = true;
        progress = 0;
        if (level != null) level.playSound(null, worldPosition, ModSounds.POUR.get(), SoundSource.BLOCKS, 0.9f, m == COLA ? 1.1f : 0.85f);
        sync();
        return true;
    }

    public ItemStack take() {
        ItemStack r = output;
        output = ItemStack.EMPTY;
        sync();
        return r;
    }

    @Override
    public void serverTick() {
        if (!busy) return;
        if (++progress >= BREW_TICKS) {
            busy = false;
            output = Foods.withQuality(mode == COLA ? ModItems.COLA.get() : ModItems.COFFEE.get(), 100);
            if (level != null) level.playSound(null, worldPosition, ModSounds.ORDER_BELL.get(), SoundSource.BLOCKS, 0.4f, 1.8f);
            sync();
        }
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> l = new ArrayList<>();
        l.add(output);
        if (busy) l.add(new ItemStack(ModItems.PAPER_CUP.get()));
        return l;
    }

    @Override
    protected void saveAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.saveAdditional(t, p);
        t.putInt("cola", colaCharges); t.putInt("coffee", coffeeCharges); t.putInt("mode", mode);
        t.putInt("progress", progress); t.putBoolean("busy", busy);
        t.put("out", saveStack(output, p));
    }

    @Override
    protected void loadAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.loadAdditional(t, p);
        colaCharges = t.getInt("cola"); coffeeCharges = t.getInt("coffee"); mode = t.getInt("mode");
        progress = t.getInt("progress"); busy = t.getBoolean("busy");
        output = loadStack(t.getCompound("out"), p);
    }
}
