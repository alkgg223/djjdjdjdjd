package com.trafficcraft.road;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Location tool: right-click a block to create a map location there (or to edit / remove the one standing there). */
public class PlaceToolItem extends Item {
    public static java.util.function.Consumer<Vec3> OPEN = p -> {};

    public PlaceToolItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (c.getLevel().isClientSide) OPEN.accept(Vec3.atBottomCenterOf(c.getClickedPos().above()));
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.trafficcraft.place_tool.tip"));
    }
}
