package com.rpbusiness.block;

import com.rpbusiness.core.ModBlockEntities;
import com.rpbusiness.core.ModSounds;
import com.rpbusiness.food.Foods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class CuttingBoardBE extends KitchenBE {
    private ItemStack item = ItemStack.EMPTY;
    private int chops;

    public CuttingBoardBE(BlockPos pos, BlockState state) { super(ModBlockEntities.CUTTING_BOARD.get(), pos, state); }

    public ItemStack item() { return item; }
    public int chops() { return chops; }
    public boolean isEmpty() { return item.isEmpty(); }
    public boolean canPlace(ItemStack s) { return item.isEmpty() && !s.isEmpty() && Foods.chopFor(s.getItem()) != null; }
    public boolean isChoppable() { return !item.isEmpty() && Foods.chopFor(item.getItem()) != null; }

    public void place(ItemStack s) {
        item = s.copyWithCount(1);
        chops = 0;
        sync();
    }

    /** One knife stroke. Returns true when the ingredient has been fully prepared. */
    public boolean chop() {
        Foods.Chop c = item.isEmpty() ? null : Foods.chopFor(item.getItem());
        if (c == null || level == null) return false;
        chops++;
        level.playSound(null, worldPosition, ModSounds.CHOP.get(), SoundSource.BLOCKS, 0.9f, 0.9f + level.random.nextFloat() * 0.25f);
        if (level instanceof ServerLevel sl)
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, item), worldPosition.getX() + 0.5, worldPosition.getY() + 0.15,
                    worldPosition.getZ() + 0.5, 5, 0.12, 0.05, 0.12, 0.05);
        if (chops >= c.chops()) {
            int q = Foods.qualityOf(item);
            item = Foods.withQuality(c.out().get(), q);
            chops = 0;
            sync();
            return true;
        }
        sync();
        return false;
    }

    public ItemStack take() {
        ItemStack r = item;
        item = ItemStack.EMPTY;
        chops = 0;
        sync();
        return r;
    }

    @Override public List<ItemStack> contents() { return List.of(item); }

    @Override
    protected void saveAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.saveAdditional(t, p);
        t.put("item", saveStack(item, p));
        t.putInt("chops", chops);
    }

    @Override
    protected void loadAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.loadAdditional(t, p);
        item = loadStack(t.getCompound("item"), p);
        chops = t.getInt("chops");
    }
}
