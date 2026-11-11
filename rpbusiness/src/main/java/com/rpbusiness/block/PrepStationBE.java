package com.rpbusiness.block;

import com.rpbusiness.core.ModBlockEntities;
import com.rpbusiness.core.ModSounds;
import com.rpbusiness.food.Foods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Assembly counter: stack burger/sandwich/salad/pizza components, then assemble. */
public class PrepStationBE extends KitchenBE {
    public static final int MAX = 6;
    private final List<ItemStack> items = new ArrayList<>();

    public PrepStationBE(BlockPos pos, BlockState state) { super(ModBlockEntities.PREP_STATION.get(), pos, state); }

    public List<ItemStack> items() { return items; }
    public boolean isEmpty() { return items.isEmpty(); }

    public static boolean isPart(Item item) {
        for (Foods.Assembly a : Foods.ASSEMBLIES) for (Foods.Group g : a.parts()) if (g.test(item)) return true;
        return false;
    }

    public boolean add(ItemStack s) {
        if (s.isEmpty() || items.size() >= MAX || !isPart(s.getItem())) return false;
        items.add(s.copyWithCount(1));
        if (level != null) level.playSound(null, worldPosition, ModSounds.PLACE_FOOD.get(), SoundSource.BLOCKS, 0.6f, 1.0f + items.size() * 0.05f);
        sync();
        return true;
    }

    public Foods.Assembly match() { return Foods.matchAssembly(items); }

    /** Combines the contents into the finished product (quality = average of the parts). */
    public ItemStack assemble() {
        Foods.Assembly a = match();
        if (a == null) return ItemStack.EMPTY;
        int sum = 0;
        for (ItemStack s : items) sum += Foods.qualityOf(s);
        ItemStack r = Foods.withQuality(a.result().get(), sum / items.size());
        items.clear();
        if (level != null) level.playSound(null, worldPosition, ModSounds.PLACE_FOOD.get(), SoundSource.BLOCKS, 1.0f, 0.7f);
        sync();
        return r;
    }

    public ItemStack takeLast() {
        if (items.isEmpty()) return ItemStack.EMPTY;
        ItemStack r = items.remove(items.size() - 1);
        sync();
        return r;
    }

    @Override public List<ItemStack> contents() { return new ArrayList<>(items); }

    @Override
    protected void saveAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.saveAdditional(t, p);
        ListTag l = new ListTag();
        for (ItemStack s : items) l.add(saveStack(s, p));
        t.put("items", l);
    }

    @Override
    protected void loadAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.loadAdditional(t, p);
        items.clear();
        ListTag l = t.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            ItemStack s = loadStack(l.getCompound(i), p);
            if (!s.isEmpty()) items.add(s);
        }
    }
}
