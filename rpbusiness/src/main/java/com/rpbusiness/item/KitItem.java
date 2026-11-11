package com.rpbusiness.item;

import com.rpbusiness.business.BusinessType;
import com.rpbusiness.server.Kits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/** Ready-made building: right-click the ground where the entrance should be. The building faces you. */
public class KitItem extends Item {
    public final String kind;
    public final BusinessType type;

    public KitItem(String kind, BusinessType type, Properties p) { super(p); this.kind = kind; this.type = type; }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.PASS;
        if (Kits.place(p, kind, ctx.getClickedPos())) {
            if (!p.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.rpbusiness.kit_1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.rpbusiness.kit_2").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.rpbusiness.kit_3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
