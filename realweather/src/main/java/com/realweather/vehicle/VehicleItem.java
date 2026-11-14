package com.realweather.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Places a vehicle where you click, facing the way you look. Dye it in a crafting grid to paint it. */
public class VehicleItem extends Item {
    public final VehicleType kind;

    public VehicleItem(VehicleType kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        Vec3 at = Vec3.atBottomCenterOf(pos);
        StormVehicle v = VehicleRegistry.type(kind).create(server);
        if (v == null) {
            return InteractionResult.FAIL;
        }
        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0F;
        v.moveTo(at.x, at.y, at.z, yaw, 0F);
        v.setYHeadRot(yaw);
        v.yBodyRot = yaw;
        ItemStack stack = ctx.getItemInHand();
        DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
        v.setColor(dye != null ? dye.rgb() : kind.colors[level.random.nextInt(kind.colors.length)]);
        if (!level.noCollision(v, v.getBoundingBox())) {
            v.moveTo(at.x, at.y + 1, at.z, yaw, 0F);
        }
        server.addFreshEntity(v);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.realweather." + kind.id + ".desc").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
