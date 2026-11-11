package com.rpbusiness.item;

import com.rpbusiness.business.Business;
import com.rpbusiness.business.BusinessData;
import com.rpbusiness.business.EmployeeRecord;
import com.rpbusiness.core.ModComponents;
import com.rpbusiness.entity.EmployeeEntity;
import com.rpbusiness.server.BusinessManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * Assigns exact positions and walking routes to a hired employee:
 * right-click the employee to select them, sneak + right-click to change mode, right-click blocks to assign.
 */
public class StaffPlannerItem extends Item {
    public static final String[] MODES = {"stand", "work", "service", "storage", "route_add", "route_clear"};

    public StaffPlannerItem(Properties p) { super(p); }

    public static int mode(ItemStack s) { return Math.floorMod(s.getOrDefault(ModComponents.TOOL_MODE.get(), 0), MODES.length); }

    private static Component modeName(int m) { return Component.translatable("staffmode.rpbusiness." + MODES[m]); }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player p, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof EmployeeEntity e)) return InteractionResult.PASS;
        if (p.level().isClientSide) return InteractionResult.SUCCESS;
        Business b = e.business();
        if (b == null || !BusinessManager.isOwner(p, b)) {
            p.displayClientMessage(Component.translatable("msg.rpbusiness.not_your_employee").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        ItemStack held = p.getItemInHand(hand);
        held.set(ModComponents.STAFF_TARGET.get(), e.getUUID());
        held.set(ModComponents.STAFF_NAME.get(), e.npcName());
        p.displayClientMessage(Component.translatable("msg.rpbusiness.staff_selected", e.npcName(), modeName(mode(held))).withStyle(ChatFormatting.AQUA), false);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (p.isShiftKeyDown()) {
            if (!level.isClientSide) {
                st.set(ModComponents.TOOL_MODE.get(), (mode(st) + 1) % MODES.length);
                p.displayClientMessage(Component.translatable("msg.rpbusiness.staff_mode", modeName(mode(st))).withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
        return InteractionResultHolder.pass(st);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player p = ctx.getPlayer();
        ItemStack st = ctx.getItemInHand();
        if (p == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (p.isShiftKeyDown()) {
            st.set(ModComponents.TOOL_MODE.get(), (mode(st) + 1) % MODES.length);
            p.displayClientMessage(Component.translatable("msg.rpbusiness.staff_mode", modeName(mode(st))).withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }
        UUID id = st.get(ModComponents.STAFF_TARGET.get());
        if (id == null) { p.displayClientMessage(Component.translatable("msg.rpbusiness.staff_none").withStyle(ChatFormatting.RED), true); return InteractionResult.CONSUME; }
        BusinessData data = BusinessData.get((ServerLevel) level);
        Business b = data.employerOf(id);
        EmployeeRecord r = b == null ? null : b.employees.get(id);
        if (r == null || !BusinessManager.isOwner(p, b)) { p.displayClientMessage(Component.translatable("msg.rpbusiness.staff_none").withStyle(ChatFormatting.RED), true); return InteractionResult.CONSUME; }
        BlockPos pos = ctx.getClickedPos();
        BlockPos above = pos.relative(ctx.getClickedFace());
        if (!b.contains(pos) && !b.contains(above)) { p.displayClientMessage(Component.translatable("msg.rpbusiness.outside_business").withStyle(ChatFormatting.RED), true); return InteractionResult.CONSUME; }
        int m = mode(st);
        switch (m) {
            case 0 -> r.standPos = above.immutable();
            case 1 -> {
                // toggle this placed workstation in the employee's personal station list
                BlockPos ip = pos.immutable();
                if (r.stations.remove(ip)) {
                    p.displayClientMessage(Component.translatable("msg.rpbusiness.station_removed", r.name, r.stations.size()).withStyle(ChatFormatting.YELLOW), true);
                    data.setDirty();
                    return InteractionResult.CONSUME;
                }
                if (BusinessManager.classify((ServerLevel) level, b, ip) == null) {
                    p.displayClientMessage(Component.translatable("msg.rpbusiness.not_station").withStyle(ChatFormatting.RED), true);
                    return InteractionResult.CONSUME;
                }
                if (r.stations.size() < 16) r.stations.add(ip);
                r.workPos = ip;
            }
            case 2 -> r.servicePos = above.immutable();
            case 3 -> {
                if (!(level.getBlockEntity(pos) instanceof Container)) { p.displayClientMessage(Component.translatable("msg.rpbusiness.not_storage").withStyle(ChatFormatting.RED), true); return InteractionResult.CONSUME; }
                r.storagePos = pos.immutable();
            }
            case 4 -> { if (r.route.size() < 16) r.route.add(above.immutable()); }
            case 5 -> r.route.clear();
        }
        data.setDirty();
        p.displayClientMessage(Component.translatable("msg.rpbusiness.staff_assigned", modeName(m), r.name, m == 4 ? r.route.size() : m == 1 ? r.stations.size() : 0).withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.rpbusiness.staff_1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.rpbusiness.staff_2").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.rpbusiness.staff_3").withStyle(ChatFormatting.GRAY));
        String n = stack.get(ModComponents.STAFF_NAME.get());
        if (n != null) tip.add(Component.translatable("tooltip.rpbusiness.staff_sel", n).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("tooltip.rpbusiness.staff_mode", modeName(mode(stack))).withStyle(ChatFormatting.YELLOW));
    }
}
