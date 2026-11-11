package com.rpbusiness.block;

import com.rpbusiness.core.ModBlockEntities;
import com.rpbusiness.core.ModComponents;
import com.rpbusiness.core.ModSounds;
import com.rpbusiness.food.Foods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Grill (2 spots), fryer, oven and cooking pot. Food goes through raw -> undercooked -> cooked -> well-done -> burnt
 * in real time; the cook decides when to take it off. Meat on the grill must be flipped with a spatula.
 */
public class CookerBE extends KitchenBE {
    public final Foods.Cooker cooker;
    private final ItemStack[] input;
    private final int[] ticks;
    private final boolean[] flipped;

    public CookerBE(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COOKER.get(), pos, state);
        cooker = state.getBlock() instanceof CookerBlock cb ? cb.cooker : Foods.Cooker.GRILL;
        int n = cooker == Foods.Cooker.GRILL ? 2 : 1;
        input = new ItemStack[n];
        ticks = new int[n];
        flipped = new boolean[n];
        for (int i = 0; i < n; i++) input[i] = ItemStack.EMPTY;
    }

    public int size() { return input.length; }
    public boolean occupied(int i) { return !input[i].isEmpty(); }
    public int ticks(int i) { return ticks[i]; }
    public boolean flipped(int i) { return flipped[i]; }
    public Foods.CookLine line(int i) { return input[i].isEmpty() ? null : Foods.cookLineFor(cooker, input[i].getItem()); }
    public int stage(int i) { Foods.CookLine l = line(i); return l == null ? -1 : l.stageAt(ticks[i]); }
    public int freeSlot() { for (int i = 0; i < input.length; i++) if (input[i].isEmpty()) return i; return -1; }
    public boolean anyOccupied() { for (ItemStack s : input) if (!s.isEmpty()) return true; return false; }

    /** The item currently visible in slot i (changes as it cooks). */
    public ItemStack displayed(int i) {
        Foods.CookLine l = line(i);
        if (l == null) return ItemStack.EMPTY;
        return new ItemStack(l.itemAt(ticks[i]));
    }

    /** Whether this item can be cooked here (raw input or a partially cooked stage). */
    public boolean canCook(Item item) {
        if (Foods.cookLineFor(cooker, item) != null) return true;
        Foods.CookLine l = Foods.cookLineProducing(item);
        return l != null && l.cooker() == cooker && Foods.stageIndex(item) < 3;
    }

    /** Puts one item on a free spot. Returns slot or -1. */
    public int insert(ItemStack stack) {
        int slot = freeSlot();
        if (slot < 0 || stack.isEmpty() || !canCook(stack.getItem())) return -1;
        Item it = stack.getItem();
        int q = Foods.qualityOf(stack);
        Foods.CookLine l = Foods.cookLineFor(cooker, it);
        int start = 0;
        if (l == null) {
            l = Foods.cookLineProducing(it);
            int st = Foods.stageIndex(it);
            start = l.times()[st];
            // restore the original ingredient quality from the stage quality
            q = Math.min(100, q * 100 / Math.max(1, Foods.STAGE_QUALITY[st]));
        }
        input[slot] = Foods.withQuality(l.input().get(), q);
        ticks[slot] = start;
        flipped[slot] = Boolean.TRUE.equals(stack.get(ModComponents.FLIPPED.get()));
        updateLit();
        sync();
        if (level != null) level.playSound(null, worldPosition, ModSounds.SIZZLE.get(), SoundSource.BLOCKS, 0.7f, 1.1f);
        return slot;
    }

    public boolean canFlip(int i) {
        Foods.CookLine l = line(i);
        return l != null && l.needsFlip() && !flipped[i] && ticks[i] >= l.times()[1] * 0.3;
    }

    public boolean flip(int i) {
        if (!canFlip(i)) return false;
        flipped[i] = true;
        sync();
        if (level instanceof ServerLevel sl) {
            sl.playSound(null, worldPosition, ModSounds.SIZZLE.get(), SoundSource.BLOCKS, 1.0f, 0.8f);
            sl.sendParticles(ParticleTypes.SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, 6, 0.2, 0.05, 0.2, 0.01);
        }
        return true;
    }

    /** Takes the food off in its current state, with a quality value. */
    public ItemStack take(int i) {
        Foods.CookLine l = line(i);
        if (l == null) return ItemStack.EMPTY;
        int st = l.stageAt(ticks[i]);
        ItemStack r;
        if (st < 0) {
            r = input[i].copy();
        } else {
            int q = Foods.qualityOf(input[i]) * Foods.STAGE_QUALITY[st] / 100;
            if (l.needsFlip() && !flipped[i] && (st == 1 || st == 2)) q -= 25;
            r = Foods.withQuality(l.stages().get(st).get(), q);
            if (l.needsFlip()) r.set(ModComponents.FLIPPED.get(), flipped[i]);
        }
        input[i] = ItemStack.EMPTY;
        ticks[i] = 0;
        flipped[i] = false;
        updateLit();
        sync();
        return r;
    }

    @Override
    public void serverTick() {
        if (level == null) return;
        boolean changed = false, active = false;
        for (int i = 0; i < input.length; i++) {
            Foods.CookLine l = line(i);
            if (l == null) continue;
            active = true;
            int before = l.stageAt(ticks[i]);
            if (ticks[i] < l.times()[3] + 20) ticks[i]++;
            int after = l.stageAt(ticks[i]);
            if (before != after) {
                changed = true;
                if (after == 3 && level instanceof ServerLevel sl)
                    sl.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.01);
                if (after == 1) level.playSound(null, worldPosition, ModSounds.ORDER_BELL.get(), SoundSource.BLOCKS, 0.35f, 1.6f);
            }
        }
        if (active && level.getGameTime() % 40 == 0) {
            var snd = cooker == Foods.Cooker.FRYER || cooker == Foods.Cooker.POT ? ModSounds.BUBBLE.get() : ModSounds.SIZZLE.get();
            level.playSound(null, worldPosition, snd, SoundSource.BLOCKS, 0.4f, 0.9f + level.random.nextFloat() * 0.2f);
        }
        if (changed) sync();
    }

    private void updateLit() {
        if (level == null) return;
        BlockState s = getBlockState();
        if (s.hasProperty(CookerBlock.LIT) && s.getValue(CookerBlock.LIT) != anyOccupied())
            level.setBlock(worldPosition, s.setValue(CookerBlock.LIT, anyOccupied()), 3);
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < input.length; i++) if (!input[i].isEmpty()) l.add(displayed(i));
        return l;
    }

    @Override
    protected void saveAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.saveAdditional(t, p);
        for (int i = 0; i < input.length; i++) {
            t.put("in" + i, saveStack(input[i], p));
            t.putInt("t" + i, ticks[i]);
            t.putBoolean("f" + i, flipped[i]);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.loadAdditional(t, p);
        for (int i = 0; i < input.length; i++) {
            input[i] = loadStack(t.getCompound("in" + i), p);
            ticks[i] = t.getInt("t" + i);
            flipped[i] = t.getBoolean("f" + i);
        }
    }
}
