package com.gunsmod.item;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.DroneEntity;
import com.gunsmod.gun.GunLogic;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * FPV kamikaze drone: use it and the quad lifts off from your hands and goes for whatever is under the crosshair
 * (a vehicle, a soldier, a block). Shahed-136: sneak + use marks the block you look at as its target (far away is
 * fine), use launches it at the marked spot (or at what you look at when nothing is marked).
 */
public class DroneItem extends Item {
    public static final double FPV_RANGE = 220, SHAHED_RANGE = 600;
    public final DroneEntity.Kind kind;

    public DroneItem(DroneEntity.Kind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    private static double markRange(Level level) {
        if (level instanceof ServerLevel sl) return Math.min(SHAHED_RANGE, Math.max(96, sl.getServer().getPlayerList().getViewDistance() * 16.0));
        return SHAHED_RANGE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        BotTeam team = BotTeam.of(player);
        if (team == null) team = BotTeam.GREEN;
        if (kind == DroneEntity.Kind.FPV) {
            GunLogic.Trace t = GunLogic.trace(level, player, eye, look, FPV_RANGE);
            Entity chase = t.entity() instanceof LivingEntity le && le.isAlive() ? t.entity() : null;
            Vec3 point = t.end();
            DroneEntity.launch(level, kind, team, eye.add(look.scale(1.2)).add(0, -0.3, 0), chase, point, player.getUUID());
            player.displayClientMessage(Component.translatable(chase != null ? "gunsmod.msg.fpv_chase" : "gunsmod.msg.fpv_point",
                    chase != null ? chase.getDisplayName() : Component.literal(""), Mth.floor(eye.distanceTo(point))), true);
            used(player, stack, 30);
            return InteractionResultHolder.success(stack);
        }
        if (player.isShiftKeyDown()) {
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(markRange(level))), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                player.displayClientMessage(Component.translatable("gunsmod.msg.too_far"), true);
                return InteractionResultHolder.success(stack);
            }
            BlockPos t = hit.getBlockPos();
            stack.set(Reg.LINK.get(), t);
            player.displayClientMessage(Component.translatable("gunsmod.msg.shahed_marked", t.getX(), t.getY(), t.getZ(),
                    Mth.floor(Math.sqrt(t.distToCenterSqr(player.getX(), player.getY(), player.getZ())))), true);
            level.playSound(null, player.blockPosition(), Reg.sound("ui_confirm"), SoundSource.PLAYERS, 1f, 1.2f);
            if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
                sl.sendParticles(sp, net.minecraft.core.particles.ParticleTypes.END_ROD, true, t.getX() + 0.5, t.getY() + 1.2, t.getZ() + 0.5, 30, 0.3, 1.5, 0.3, 0.02);
            }
            return InteractionResultHolder.success(stack);
        }
        Vec3 target;
        BlockPos marked = stack.get(Reg.LINK.get());
        if (marked != null) {
            target = Vec3.atCenterOf(marked);
        } else {
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(markRange(level))), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                player.displayClientMessage(Component.translatable("gunsmod.msg.shahed_no_target"), true);
                return InteractionResultHolder.fail(stack);
            }
            target = hit.getLocation();
        }
        if (target.distanceTo(player.position()) < 40) {
            player.displayClientMessage(Component.translatable("gunsmod.msg.shahed_too_close"), true);
            return InteractionResultHolder.fail(stack);
        }
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        DroneEntity.launch(level, kind, team, player.position().add(flat.scale(1.8)).add(0, 1.4, 0), null, target, null);
        player.displayClientMessage(Component.translatable("gunsmod.msg.shahed_launched", Mth.floor(target.x), Mth.floor(target.y), Mth.floor(target.z),
                Mth.floor(target.distanceTo(player.position()))), true);
        used(player, stack, 60);
        return InteractionResultHolder.success(stack);
    }

    private void used(Player player, ItemStack stack, int cooldown) {
        player.getCooldowns().addCooldown(this, cooldown);
        if (!player.getAbilities().instabuild) stack.shrink(1);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        if (kind == DroneEntity.Kind.FPV) {
            tip.add(Component.translatable("gunsmod.tip.fpv1").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("gunsmod.tip.fpv2").withStyle(ChatFormatting.GRAY));
            return;
        }
        BlockPos t = stack.get(Reg.LINK.get());
        if (t != null) tip.add(Component.translatable("gunsmod.tip.shahed_target", t.getX(), t.getY(), t.getZ()).withStyle(ChatFormatting.GREEN));
        else tip.add(Component.translatable("gunsmod.tip.shahed_none").withStyle(ChatFormatting.RED));
        tip.add(Component.translatable("gunsmod.tip.shahed1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.shahed2").withStyle(ChatFormatting.GRAY));
    }
}
