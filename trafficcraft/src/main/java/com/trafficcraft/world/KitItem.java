package com.trafficcraft.world;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/** Places a ready-made location (see {@link Kits}) facing the way the player looks. */
public class KitItem extends Item {
    public final String kind;

    public KitItem(Properties p, String kind) {
        super(p);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!com.trafficcraft.road.RoadEditing.canEdit(sp)) {
            sp.displayClientMessage(Component.translatable("msg.trafficcraft.no_permission"), true);
            return InteractionResult.FAIL;
        }
        if (Kits.place(sp, ctx.getClickedPos(), kind) && !sp.isCreative()) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.trafficcraft.kit_" + kind).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.trafficcraft.kit").withStyle(ChatFormatting.DARK_GRAY));
    }
}
