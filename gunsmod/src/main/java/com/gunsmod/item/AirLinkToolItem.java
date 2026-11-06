package com.gunsmod.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

/** Air defence link tool: use it on a radar, then on a launcher to link them (again to unlink; sneak on a launcher clears its links). */
public class AirLinkToolItem extends Item {
    private static final String KEY = "gunsmod_adlink";

    public AirLinkToolItem(Properties props) {
        super(props);
    }

    @Nullable
    public static BlockPos selected(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        CompoundTag t = d.copyTag();
        return t.contains(KEY) ? BlockPos.of(t.getLong(KEY)) : null;
    }

    public static void select(ItemStack s, BlockPos radar) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        CompoundTag t = d == null ? new CompoundTag() : d.copyTag();
        t.putLong(KEY, radar.asLong());
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return selected(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        BlockPos p = selected(stack);
        if (p != null) tip.add(Component.translatable("gunsmod.tip.adlink_sel", p.getX(), p.getY(), p.getZ()).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("gunsmod.tip.adlink1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.adlink2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
