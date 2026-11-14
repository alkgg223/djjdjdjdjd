package com.realweather.device;

import com.realweather.space.Geology;
import com.realweather.space.Tiles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** A planetary material's item, telling what it is: its geology, density, hardness and the temperatures where it is found. */
public class GeoBlockItem extends BlockItem {
    private final int tile;

    public GeoBlockItem(Block block, Properties props, int tile) {
        super(block, props);
        this.tile = tile;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        Geology.Geo g = Tiles.geo(tile);
        if (g == null) {
            return;
        }
        lines.add(Component.translatable("realweather.geo.type", Component.translatable("realweather.geo.type." + Geology.TYPES[g.type()]))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("realweather.geo.density", Component.translatable("realweather.geo.density." + g.density()))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("realweather.geo.hardness", String.format(java.util.Locale.ROOT, "%.1f", g.hardness()))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("realweather.geo.temperature", g.tMin(), g.tMax()).withStyle(ChatFormatting.DARK_AQUA));
        if (g.material() == Tiles.M_GAS) {
            lines.add(Component.translatable("realweather.geo.gas").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
