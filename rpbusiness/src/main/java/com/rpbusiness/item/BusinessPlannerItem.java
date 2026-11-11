package com.rpbusiness.item;

import com.rpbusiness.business.Business;
import com.rpbusiness.business.BusinessData;
import com.rpbusiness.core.ModComponents;
import com.rpbusiness.server.BusinessManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** Marks the two corners of the building the player built, then registers it as a business. */
public class BusinessPlannerItem extends Item {
    public BusinessPlannerItem(Properties p) { super(p); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player p = ctx.getPlayer();
        if (p == null) return InteractionResult.PASS;
        ItemStack st = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (p.isShiftKeyDown()) {
            Business b = BusinessData.get((ServerLevel) level).at(pos);
            if (b != null && BusinessManager.isOwner(p, b)) {
                b.entrance = pos.relative(ctx.getClickedFace()).immutable();
                BusinessData.get((ServerLevel) level).setDirty();
                p.displayClientMessage(Component.translatable("msg.rpbusiness.entrance_set", b.entrance.toShortString()).withStyle(ChatFormatting.GREEN), false);
                return InteractionResult.CONSUME;
            }
        }
        BlockPos a = st.get(ModComponents.CORNER_A.get());
        BlockPos b = st.get(ModComponents.CORNER_B.get());
        if (a == null || b != null) {
            st.set(ModComponents.CORNER_A.get(), pos.immutable());
            st.remove(ModComponents.CORNER_B.get());
            p.displayClientMessage(Component.translatable("msg.rpbusiness.corner_a", pos.toShortString()).withStyle(ChatFormatting.AQUA), true);
        } else {
            st.set(ModComponents.CORNER_B.get(), pos.immutable());
            int dx = Math.abs(pos.getX() - a.getX()) + 1, dy = Math.abs(pos.getY() - a.getY()) + 1, dz = Math.abs(pos.getZ() - a.getZ()) + 1;
            p.displayClientMessage(Component.translatable("msg.rpbusiness.corner_b", pos.toShortString(), dx, dy, dz).withStyle(ChatFormatting.AQUA), false);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (st.has(ModComponents.CORNER_A.get()) && st.has(ModComponents.CORNER_B.get())) {
            if (level.isClientSide) com.rpbusiness.client.ClientHooks.openRegistration(st.get(ModComponents.CORNER_A.get()), st.get(ModComponents.CORNER_B.get()));
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
        if (!level.isClientSide) p.displayClientMessage(Component.translatable("msg.rpbusiness.planner_hint").withStyle(ChatFormatting.GRAY), true);
        return InteractionResultHolder.pass(st);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.rpbusiness.planner_1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.rpbusiness.planner_2").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.rpbusiness.planner_3").withStyle(ChatFormatting.GRAY));
        BlockPos a = stack.get(ModComponents.CORNER_A.get()), b = stack.get(ModComponents.CORNER_B.get());
        if (a != null) tip.add(Component.literal("A: " + a.toShortString()).withStyle(ChatFormatting.AQUA));
        if (b != null) tip.add(Component.literal("B: " + b.toShortString()).withStyle(ChatFormatting.AQUA));
    }

    @Override public boolean isFoil(ItemStack s) { return s.has(ModComponents.CORNER_B.get()); }
}
