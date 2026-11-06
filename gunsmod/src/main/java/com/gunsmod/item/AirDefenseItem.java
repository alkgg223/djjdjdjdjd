package com.gunsmod.item;

import com.gunsmod.block.airdef.AirDefenseBlock;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** A radar or launcher as an item: its detection or accuracy and range in the tooltip. */
public class AirDefenseItem extends BlockItem {
    public AirDefenseItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        if (getBlock() instanceof AirDefenseBlock b) {
            if (b.radar != null) {
                tip.add(Component.translatable("gunsmod.ad.detect", Math.round(b.radar.detect * 100)).withStyle(ChatFormatting.GREEN));
                tip.add(Component.translatable("gunsmod.ad.range", (int) b.radar.range).withStyle(ChatFormatting.GRAY));
                if (b.radar.stealthBonus > 1) tip.add(Component.translatable("gunsmod.ad.vhf").withStyle(ChatFormatting.AQUA));
            } else if (b.launcher != null) {
                tip.add(Component.translatable("gunsmod.ad.accuracy", Math.round(b.launcher.accuracy * 100)).withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable("gunsmod.ad.range", (int) b.launcher.range).withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable(b.launcher.gun() ? "gunsmod.ad.kind_gun" : "gunsmod.ad.kind_missile").withStyle(ChatFormatting.GRAY));
            }
            tip.add(Component.translatable("gunsmod.desc.ad." + b.id()).withStyle(ChatFormatting.DARK_GRAY));
            tip.add(Component.translatable("gunsmod.tip.ad_use").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
