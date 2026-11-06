package com.gunsmod.item;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.entity.VehicleKind;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Places a tank, helicopter or armed car of a team. */
public class VehicleItem extends Item {
    public final VehicleKind kind;
    public final BotTeam team;

    public VehicleItem(VehicleKind kind, BotTeam team, Properties props) {
        super(props);
        this.kind = kind;
        this.team = team;
    }

    /** a new vehicle for any vehicle item (jeep, tank, helicopter), not yet added to the level; null for other items */
    @Nullable
    public static Mob create(Level level, ItemStack stack, double x, double y, double z, float yaw) {
        Mob m;
        if (stack.getItem() instanceof JeepItem j) {
            JeepEntity e = new JeepEntity(Reg.JEEP.get(), level);
            e.setTeam(j.team);
            m = e;
        } else if (stack.getItem() instanceof VehicleItem v) {
            m = v.kind.type().create(level);
            if (m == null) return null;
            if (m instanceof VehicleEntity e) e.setTeam(v.team);
            else if (m instanceof JeepEntity j) j.setTeam(v.team);
        } else {
            return null;
        }
        // a route planned for this vehicle (set at its garage): soldiers who drive it follow the route
        com.gunsmod.entity.ai.Waypoints w = RouteToolItem.read(stack);
        if (w != null) {
            if (m instanceof VehicleEntity e) e.waypoints = w.fresh();
            else if (m instanceof JeepEntity j) j.waypoints = w.fresh();
        }
        m.moveTo(x, y, z, yaw, 0);
        m.setYHeadRot(yaw);
        m.setYBodyRot(yaw);
        return m;
    }

    public static boolean isVehicle(ItemStack stack) {
        return stack.getItem() instanceof JeepItem || stack.getItem() instanceof VehicleItem;
    }

    /** soldiers patrol around this point and the spawner is told when the vehicle is destroyed */
    public static void link(Entity e, BlockPos home, @Nullable BlockPos spawner) {
        if (e instanceof JeepEntity j) {
            j.home = home;
            j.spawnerPos = spawner;
        } else if (e instanceof VehicleEntity v) {
            v.home = home;
            v.spawnerPos = spawner;
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        // ships go on the water (see use)
        if (kind.group == VehicleKind.Group.SHIP) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        if (!level.isClientSide) {
            BlockPos p = ctx.getClickedPos().relative(ctx.getClickedFace());
            Mob m = create(level, ctx.getItemInHand(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5, ctx.getRotation());
            if (m == null) return InteractionResult.FAIL;
            if (!level.noCollision(m, m.getBoundingBox())) {
                if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("gunsmod.msg.no_room_vehicle"), true);
                return InteractionResult.FAIL;
            }
            link(m, p, null);
            level.addFreshEntity(m);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 3.0: a ship is put on the water the player looks at; sneaking it comes with a soldier crew that sails it */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (kind.group != VehicleKind.Group.SHIP) return super.use(level, player, hand);
        if (level.isClientSide) return net.minecraft.world.InteractionResultHolder.success(stack);
        Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(player.getViewVector(1f).scale(32)),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER)) {
            player.displayClientMessage(Component.translatable("gunsmod.msg.ship_water").withStyle(ChatFormatting.RED), true);
            return net.minecraft.world.InteractionResultHolder.fail(stack);
        }
        BlockPos p = hit.getBlockPos();
        com.gunsmod.entity.ShipType st = kind.ship();
        Mob m = create(level, stack, p.getX() + 0.5, p.getY() + 0.9 - st.waterline * 0.25, p.getZ() + 0.5, player.getYRot());
        if (m == null) return net.minecraft.world.InteractionResultHolder.fail(stack);
        link(m, p, null);
        level.addFreshEntity(m);
        if (player.isShiftKeyDown() && m instanceof com.gunsmod.entity.ShipEntity ship && level instanceof net.minecraft.server.level.ServerLevel sl) {
            com.gunsmod.entity.BotEntity b = new com.gunsmod.entity.BotEntity(Reg.BOT.get(), sl);
            b.moveTo(m.getX(), m.getY() + 2, m.getZ(), m.getYRot(), 0);
            b.setup(team, sl.random, null);
            b.home = p;
            sl.addFreshEntity(b);
            ship.board(b, 0);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return net.minecraft.world.InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        String k = kind == VehicleKind.B2 ? "b2" : switch (kind.group) {
            case TANK -> "tank";
            case HELI -> "heli";
            case JET -> "jet";
            case SHIP -> "ship";
            default -> kind.item;
        };
        tip.add(Component.translatable("gunsmod.desc.vehicle." + kind.item).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip." + k + "1").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("gunsmod.tip." + k + "2").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("gunsmod.tip.spawner_hint").withStyle(ChatFormatting.DARK_GRAY));
        com.gunsmod.entity.ai.Waypoints w = RouteToolItem.read(stack);
        if (w != null) tip.add(Component.translatable("gunsmod.tip.route_points", w.points.size()).withStyle(ChatFormatting.YELLOW));
    }
}
