package com.trafficcraft.road;

import com.trafficcraft.TCConfig;
import com.trafficcraft.net.OpenEditorPayload;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Road Planner: places and connects road / pedestrian nodes, edits, deletes, bends roads, builds roundabouts,
 * toggles traffic lights and places special nodes. Sneak + right-click in the air opens its settings.
 */
public class RoadToolItem extends Item {
    public RoadToolItem(Properties p) {
        super(p);
    }

    public static CompoundTag settings(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        CompoundTag t = d == null ? new CompoundTag() : d.copyTag();
        if (!t.contains("width")) {
            t.putInt("mode", RoadEditing.M_ROAD);
            t.putInt("lanes", 2);
            t.putBoolean("oneway", false);
            t.putFloat("width", TCConfig.DEFAULT_ROAD_WIDTH.get());
            t.putFloat("speed", TCConfig.DEFAULT_SPEED.get());
            t.putBoolean("pave", true);
            t.putInt("sidewalk", 0);
            t.putBoolean("clear", true);
            t.putBoolean("overtake", true);
            t.putInt("radius", 10);
            t.putFloat("pathWidth", 2);
            t.putString("nodeType", "SPAWN");
        }
        return t;
    }

    public static void setSettings(ItemStack s, CompoundTag t) {
        CompoundTag cur = settings(s);
        cur.merge(t);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(cur));
    }

    @Override
    public Component getName(ItemStack s) {
        int m = settings(s).getInt("mode");
        return Component.translatable("item.trafficcraft.road_planner").append(" - ")
                .append(Component.translatable("mode.trafficcraft." + RoadEditing.MODE_NAMES[Math.max(0, Math.min(RoadEditing.MODE_NAMES.length - 1, m))])
                        .withStyle(ChatFormatting.AQUA));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null) return InteractionResult.PASS;
        if (p.isShiftKeyDown() && settings(ctx.getItemInHand()).getInt("mode") != RoadEditing.M_CURVE) {
            if (p instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, new OpenEditorPayload("tool", settings(ctx.getItemInHand())));
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        BlockPos bp = ctx.getClickedPos();
        Vec3 pos;
        if (ctx.getClickedFace() == Direction.UP) pos = new Vec3(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5);
        else {
            BlockPos q = bp.relative(ctx.getClickedFace());
            pos = new Vec3(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
        }
        RoadEditing.click((ServerPlayer) p, pos, settings(ctx.getItemInHand()));
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!level.isClientSide) {
            if (p.isShiftKeyDown()) PacketDistributor.sendToPlayer((ServerPlayer) p, new OpenEditorPayload("tool", settings(s)));
            else {
                RoadEditing.clearSelection(p);
                p.displayClientMessage(Component.translatable("msg.trafficcraft.selection_cleared"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        CompoundTag t = settings(stack);
        tip.add(Component.translatable("tooltip.trafficcraft.planner1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.trafficcraft.planner2").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal(t.getInt("lanes") + " lanes" + (t.getBoolean("oneway") ? " (one-way)" : "") + ", " + t.getFloat("width") + " m, "
                + Math.round(t.getFloat("speed")) + " km/h").withStyle(ChatFormatting.DARK_AQUA));
    }
}
