package com.gunsmod.item;

import com.gunsmod.Reg;
import com.gunsmod.gun.Attachment;
import com.gunsmod.gun.GunAttachments;
import com.gunsmod.gun.GunLogic;
import com.gunsmod.gun.GunType;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class GunItem extends Item {
    public final GunType type;

    public GunItem(GunType type, Properties props) {
        super(props);
        this.type = type;
    }

    public static ItemStack create(GunType type) {
        ItemStack s = new ItemStack(Reg.gun(type));
        s.set(Reg.AMMO.get(), type.magSize);
        return s;
    }

    public static int ammo(ItemStack s) {
        Integer v = s.get(Reg.AMMO.get());
        return v == null ? 0 : v;
    }

    public static void setAmmo(ItemStack s, int ammo) {
        s.set(Reg.AMMO.get(), Math.max(0, ammo));
    }

    public static GunAttachments attachments(ItemStack s) {
        GunAttachments a = s.get(Reg.ATTACHMENTS.get());
        return a == null ? GunAttachments.NONE : a;
    }

    public static boolean reloading(ItemStack s) {
        return s.has(Reg.RELOAD.get());
    }

    public static GunType typeOf(ItemStack s) {
        return s.getItem() instanceof GunItem g ? g.type : null;
    }

    public int magSize(ItemStack s) {
        return attachments(s).magSize(type);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return true;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // aiming and shooting are driven by the client input handler
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof LivingEntity living)) return;
        Long end = stack.get(Reg.RELOAD.get());
        if (end == null) return;
        if (living.getMainHandItem() != stack) {
            stack.remove(Reg.RELOAD.get());
        } else if (level.getGameTime() >= end) {
            GunLogic.finishReload(living, stack);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * Mth.clamp(ammo(stack) / (float) magSize(stack), 0f, 1f));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float f = Mth.clamp(ammo(stack) / (float) magSize(stack), 0f, 1f);
        return Mth.hsvToRgb(f / 3.0f, 0.85f, 1.0f);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("gunsmod.category." + type.category.name().toLowerCase()).withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("gunsmod.tip.ammo", ammo(stack), magSize(stack),
                Component.translatable("item.gunsmod." + type.ammo.itemId)).withStyle(ChatFormatting.GRAY));
        if (type.kind == GunType.Kind.BULLET) {
            String dmg = type.pellets > 1 ? type.pellets + "x" + fmt(type.damage) : fmt(type.damage);
            tip.add(Component.translatable("gunsmod.tip.damage", dmg).withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.translatable("gunsmod.tip.rpm", Math.round(1200f / type.fireDelay),
                Component.translatable("gunsmod.mode." + type.mode.name().toLowerCase())).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.range", type.range).withStyle(ChatFormatting.GRAY));
        GunAttachments att = attachments(stack);
        for (Attachment.Slot s : Attachment.Slot.values()) {
            Attachment a = att.get(s);
            if (a != null) tip.add(Component.literal(" + ").append(Component.translatable("item.gunsmod." + a.id)).withStyle(ChatFormatting.AQUA));
        }
        tip.add(Component.translatable("gunsmod.tip.controls").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String fmt(float f) {
        return f == Math.floor(f) ? Integer.toString((int) f) : String.format("%.1f", f);
    }
}
