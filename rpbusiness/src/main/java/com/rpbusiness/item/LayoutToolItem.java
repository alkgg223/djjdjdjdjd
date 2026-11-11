package com.rpbusiness.item;

import com.rpbusiness.business.Business;
import com.rpbusiness.business.BusinessData;
import com.rpbusiness.business.Equip;
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

/**
 * Business Layout Tool: the player decides what every placed block is used for.
 * Right-click a placed block to apply the current function, sneak + right-click to change the function.
 * Works on mod furniture and on any vanilla block (stairs as seats, slabs as tables, chests as storage...).
 */
public class LayoutToolItem extends Item {
    public static final String[] MODES = {"inspect", "seat", "table", "cashier", "terminal", "storage", "serving", "decor", "exclude", "auto"};

    public LayoutToolItem(Properties p) { super(p); }

    public static int mode(ItemStack s) { return Math.floorMod(s.getOrDefault(ModComponents.TOOL_MODE.get(), 0), MODES.length); }
    private static Component modeName(int m) { return Component.translatable("layoutmode.rpbusiness." + MODES[m]); }

    private static void cycle(ItemStack st, Player p) {
        st.set(ModComponents.TOOL_MODE.get(), (mode(st) + 1) % MODES.length);
        p.displayClientMessage(Component.translatable("msg.rpbusiness.layout_mode", modeName(mode(st))).withStyle(ChatFormatting.YELLOW), true);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (p.isShiftKeyDown()) {
            if (!level.isClientSide) cycle(st, p);
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
        if (p.isShiftKeyDown()) { cycle(st, p); return InteractionResult.CONSUME; }
        ServerLevel sl = (ServerLevel) level;
        BlockPos pos = ctx.getClickedPos().immutable();
        BusinessData data = BusinessData.get(sl);
        Business b = data.at(pos);
        if (b == null || !BusinessManager.isOwner(p, b)) {
            p.displayClientMessage(Component.translatable("msg.rpbusiness.outside_business").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        int m = mode(st);
        Equip target = switch (m) {
            case 1 -> Equip.CHAIR; case 2 -> Equip.TABLE; case 3 -> Equip.REGISTER; case 4 -> Equip.TERMINAL;
            case 5 -> Equip.STORAGE; case 6 -> Equip.SERVING; case 7 -> Equip.DECOR; default -> null;
        };
        if (m == 0) {
            Equip now = BusinessManager.classify(sl, b, pos);
            boolean manual = b.designations.containsKey(pos) || b.excluded.contains(pos);
            p.displayClientMessage(Component.translatable("msg.rpbusiness.layout_inspect",
                    now == null ? Component.translatable("layoutmode.rpbusiness.none") : Component.translatable("equip.rpbusiness." + now.name().toLowerCase()),
                    Component.translatable(manual ? "msg.rpbusiness.layout_manual" : "msg.rpbusiness.layout_auto")).withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.CONSUME;
        }
        BusinessManager.unindex(b, pos);
        b.designations.remove(pos);
        b.excluded.remove(pos);
        b.dirty.remove(pos);
        if (m == 8) b.excluded.add(pos);
        else if (target != null) {
            if (!BusinessManager.designationValid(sl, pos, target)) {
                p.displayClientMessage(Component.translatable("msg.rpbusiness.layout_invalid", modeName(m)).withStyle(ChatFormatting.RED), true);
                BusinessManager.index(sl, b, pos);
                return InteractionResult.CONSUME;
            }
            b.designations.put(pos, target);
        }
        BusinessManager.index(sl, b, pos);
        data.setDirty();
        Equip now = BusinessManager.classify(sl, b, pos);
        p.displayClientMessage(Component.translatable("msg.rpbusiness.layout_set",
                now == null ? Component.translatable("layoutmode.rpbusiness.none") : Component.translatable("equip.rpbusiness." + now.name().toLowerCase())).withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.rpbusiness.layout_1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.rpbusiness.layout_2").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.rpbusiness.layout_mode", modeName(mode(stack))).withStyle(ChatFormatting.YELLOW));
    }
}
