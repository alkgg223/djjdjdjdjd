package com.gunsmod.item;

import com.gunsmod.Reg;
import com.gunsmod.block.VehicleSpawnerBlockEntity;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.entity.ai.Waypoints;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Route planner. Use on the ground: a new point after the selected one (the selected point can lead on to up to three
 * others; a soldier picks one of them at random). Sneak-use near a point: select it, to branch from there; sneak-use
 * anywhere else: take back the last point. Sneak-use in the air: clear the route.
 * Hit a soldier, a vehicle or a vehicle garage with it to hand the route over. A soldier saved on a dog tag keeps his
 * route; a vehicle garage gives it to every vehicle it sends out, and any soldier driving one of them follows it.
 */
public class RouteToolItem extends Item {
    public static final String KEY = "gunsmod_route";

    public RouteToolItem(Properties props) {
        super(props);
    }

    // ------------------------------------------------------------------------------------ the route on an item
    @Nullable
    public static Waypoints read(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? null : Waypoints.read(d.copyTag(), KEY);
    }

    public static void write(ItemStack s, @Nullable Waypoints w) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = d == null ? new CompoundTag() : d.copyTag();
        if (w == null || w.isEmpty()) root.remove(KEY);
        else root.put(KEY, w.save());
        if (root.isEmpty()) s.remove(DataComponents.CUSTOM_DATA);
        else s.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static Waypoints route(ItemStack s) {
        Waypoints w = read(s);
        if (w == null) {
            w = new Waypoints();
            w.at = -1;
        }
        return w;
    }

    // ------------------------------------------------------------------------------------ planning
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player p = ctx.getPlayer();
        if (level.isClientSide || p == null) return InteractionResult.sidedSuccess(level.isClientSide);
        ItemStack s = ctx.getItemInHand();
        Waypoints w = route(s);
        BlockPos at = ctx.getClickedPos().above();
        if (p.isSecondaryUseActive()) {
            int near = w.nearest(at, 3.0);
            if (near >= 0) {
                w.at = near;
                p.displayClientMessage(Component.translatable("gunsmod.msg.route_selected", near + 1, w.branches(near), Waypoints.MAX_BRANCHES), true);
            } else if (!w.isEmpty()) {
                w.removeLast();
                p.displayClientMessage(Component.translatable("gunsmod.msg.route_undo", w.points.size()), true);
            }
            write(s, w);
            level.playSound(null, at, Reg.sound("ui_beep"), SoundSource.PLAYERS, 0.6f, 0.8f);
            return InteractionResult.SUCCESS;
        }
        int from = w.at;
        int i = w.add(at, from);
        if (i < 0) {
            p.displayClientMessage(Component.translatable(w.points.size() >= Waypoints.MAX_POINTS ? "gunsmod.msg.route_full" : "gunsmod.msg.route_branches",
                    Waypoints.MAX_BRANCHES).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        w.at = i;
        write(s, w);
        boolean branch = from >= 0 && w.branches(from) > 1;
        p.displayClientMessage(Component.translatable(branch ? "gunsmod.msg.route_branch" : "gunsmod.msg.route_added", i + 1, from + 1), true);
        level.playSound(null, at, Reg.sound("ui_beep"), SoundSource.PLAYERS, 0.7f, branch ? 1.4f : 1.15f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!p.isSecondaryUseActive()) return InteractionResultHolder.pass(s);
        if (!level.isClientSide) {
            write(s, null);
            p.displayClientMessage(Component.translatable("gunsmod.msg.route_cleared"), true);
            level.playSound(null, p.blockPosition(), Reg.sound("cover_open"), SoundSource.PLAYERS, 0.8f, 0.8f);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    /** on a vehicle garage: every vehicle it sends out carries the route */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level.getBlockEntity(ctx.getClickedPos()) instanceof VehicleSpawnerBlockEntity be) || be.vehicle().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide && ctx.getPlayer() != null) {
            Waypoints w = read(stack);
            be.setRoute(w);
            ctx.getPlayer().displayClientMessage(Component.translatable(w == null ? "gunsmod.msg.route_removed" : "gunsmod.msg.route_garage",
                    w == null ? 0 : w.points.size()), true);
            level.playSound(null, ctx.getClickedPos(), Reg.sound("ui_confirm"), SoundSource.PLAYERS, 1f, 1.1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** hit a soldier or a vehicle: it gets the route (an empty planner takes the route away) */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        if (player.level().isClientSide) return true;
        Waypoints w = read(stack);
        Waypoints give = w == null ? null : w.fresh();
        boolean done = true;
        if (target instanceof BotEntity bot) {
            bot.waypoints = give;
            bot.getNavigation().stop();
        } else if (target instanceof VehicleEntity v) {
            v.waypoints = give;
            if (v.spawnerPos != null && player.level().getBlockEntity(v.spawnerPos) instanceof VehicleSpawnerBlockEntity be) be.setRoute(w);
        } else if (target instanceof JeepEntity j) {
            j.waypoints = give;
            if (j.spawnerPos != null && player.level().getBlockEntity(j.spawnerPos) instanceof VehicleSpawnerBlockEntity be) be.setRoute(w);
        } else {
            done = false;
        }
        if (done) {
            player.displayClientMessage(Component.translatable(give == null ? "gunsmod.msg.route_removed" : "gunsmod.msg.route_given",
                    target.getDisplayName(), give == null ? 0 : give.points.size()), true);
            player.level().playSound(null, target.blockPosition(), Reg.sound("ui_confirm"), SoundSource.PLAYERS, 1f, 1.2f);
        }
        return true;
    }

    // ------------------------------------------------------------------------------------ showing the route
    /** in the hand: the points glow, the links between them are dotted lines, the selected point sparkles */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity e, int slot, boolean selected) {
        if (!level.isClientSide || !selected || e.tickCount % 4 != 0) return;
        Waypoints w = read(stack);
        if (w == null) return;
        for (int i = 0; i < w.points.size(); i++) {
            Vec3 a = Vec3.atBottomCenterOf(w.points.get(i));
            if (a.distanceToSqr(e.position()) > 160 * 160) continue;
            for (int k = 0; k < 3; k++) level.addParticle(ParticleTypes.END_ROD, a.x, a.y + 0.3 + k * 0.6, a.z, 0, 0.01, 0);
            if (i == w.at) level.addParticle(Reg.HOLO.get(), a.x, a.y + 2.2, a.z, 0, 0.02, 0);
            for (int n : w.next.get(i)) {
                Vec3 b = Vec3.atBottomCenterOf(w.points.get(n));
                double len = a.distanceTo(b);
                int steps = (int) Math.min(80, len / 1.5);
                for (int k = 1; k < steps; k++) {
                    Vec3 q = a.lerp(b, k / (double) steps);
                    level.addParticle(ParticleTypes.HAPPY_VILLAGER, q.x, q.y + 0.4, q.z, 0, 0, 0);
                }
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return read(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        Waypoints w = read(stack);
        tip.add(Component.translatable("gunsmod.tip.route_points", w == null ? 0 : w.points.size()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.route1").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("gunsmod.tip.route2").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("gunsmod.tip.route3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
