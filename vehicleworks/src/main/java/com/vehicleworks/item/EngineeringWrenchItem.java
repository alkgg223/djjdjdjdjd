package com.vehicleworks.item;

import com.vehicleworks.network.ServerActions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/**
 * Engineering wrench: right-click a component to configure it; sneak + right-click a weapon
 * (or other controllable component) to select it, then right-click a Remote Control Unit to link it.
 */
public class EngineeringWrenchItem extends Item {
    public EngineeringWrenchItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (ctx.getPlayer() instanceof ServerPlayer sp) {
            return ServerActions.wrenchOnWorldBlock(sp, ctx.getClickedPos(), ctx.getItemInHand()) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    public static CompoundTag selection(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("Selection");
    }

    /** Store the selected component: vehicle uuid (or empty) plus position (world or local). */
    public static void setSelection(ItemStack stack, String vehicle, BlockPos pos, String label) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag s = new CompoundTag();
            s.putString("Vehicle", vehicle);
            s.putLong("Pos", pos.asLong());
            s.putString("Label", label);
            tag.put("Selection", s);
        });
    }

    public static void clearSelection(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove("Selection"));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("vehicleworks.tooltip.wrench").withStyle(net.minecraft.ChatFormatting.GRAY));
        CompoundTag sel = selection(stack);
        if (!sel.isEmpty()) {
            tooltip.add(Component.translatable("vehicleworks.tooltip.wrench_selected", Component.translatable(sel.getString("Label")))
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        }
    }
}
