package com.rpbusiness.item;

import com.rpbusiness.business.Business;
import com.rpbusiness.business.BusinessData;
import com.rpbusiness.business.BusinessType;
import com.rpbusiness.business.HotelRoom;
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
 * Hotel Room Tool: right-click two opposite corners of a room inside your hotel, then right-click the air to
 * create the room. Sneak + right-click a block inside a room removes that room.
 */
public class RoomToolItem extends Item {
    public RoomToolItem(Properties p) { super(p); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player p = ctx.getPlayer();
        if (p == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        BlockPos pos = ctx.getClickedPos().immutable();
        Business b = BusinessData.get(sl).at(pos);
        if (b == null || !BusinessManager.isOwner(p, b) || b.type != BusinessType.HOTEL) {
            p.displayClientMessage(Component.translatable("msg.rpbusiness.room_need_hotel").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        ItemStack st = ctx.getItemInHand();
        if (p.isShiftKeyDown()) {
            HotelRoom r = b.roomAt(pos);
            if (r != null) {
                if (r.guest != null) { p.displayClientMessage(Component.translatable("msg.rpbusiness.room_occupied", r.name).withStyle(ChatFormatting.RED), true); return InteractionResult.CONSUME; }
                b.rooms.remove(r);
                BusinessData.get(sl).setDirty();
                p.displayClientMessage(Component.translatable("msg.rpbusiness.room_removed", r.name).withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResult.CONSUME;
        }
        BlockPos a = st.get(ModComponents.CORNER_A.get());
        if (a == null || st.has(ModComponents.CORNER_B.get())) {
            st.set(ModComponents.CORNER_A.get(), pos);
            st.remove(ModComponents.CORNER_B.get());
            p.displayClientMessage(Component.translatable("msg.rpbusiness.corner_a", pos.toShortString()).withStyle(ChatFormatting.AQUA), true);
        } else {
            st.set(ModComponents.CORNER_B.get(), pos);
            p.displayClientMessage(Component.translatable("msg.rpbusiness.room_corner_b").withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(st);
        BlockPos a = st.get(ModComponents.CORNER_A.get()), c = st.get(ModComponents.CORNER_B.get());
        if (a == null || c == null) return InteractionResultHolder.pass(st);
        ServerLevel sl = (ServerLevel) level;
        Business b = BusinessData.get(sl).at(a);
        if (b == null || !BusinessManager.isOwner(p, b) || b.type != BusinessType.HOTEL || !b.contains(c)) {
            p.displayClientMessage(Component.translatable("msg.rpbusiness.room_need_hotel").withStyle(ChatFormatting.RED), false);
            return InteractionResultHolder.fail(st);
        }
        HotelRoom r = BusinessManager.createRoom(sl, b, a, c);
        st.remove(ModComponents.CORNER_A.get());
        st.remove(ModComponents.CORNER_B.get());
        if (r == null) p.displayClientMessage(Component.translatable("msg.rpbusiness.room_overlap").withStyle(ChatFormatting.RED), false);
        else p.displayClientMessage(Component.translatable("msg.rpbusiness.room_created", r.name, BusinessManager.roomCapacity(sl, b, r)).withStyle(ChatFormatting.GREEN), false);
        return InteractionResultHolder.success(st);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.rpbusiness.roomtool_1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.rpbusiness.roomtool_2").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.rpbusiness.roomtool_3").withStyle(ChatFormatting.GRAY));
    }

    @Override public boolean isFoil(ItemStack s) { return s.has(ModComponents.CORNER_B.get()); }
}
