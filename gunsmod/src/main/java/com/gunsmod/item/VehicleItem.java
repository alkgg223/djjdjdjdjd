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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        String k = switch (kind.group) {
            case TANK -> "tank";
            case HELI -> "heli";
            default -> kind.item;
        };
        tip.add(Component.translatable("gunsmod.desc.vehicle." + kind.item).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip." + k + "1").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("gunsmod.tip." + k + "2").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("gunsmod.tip.spawner_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
