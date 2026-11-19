package com.trafficcraft.vehicle;

import com.trafficcraft.registry.TCRegistry;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Places a vehicle of one model where the player clicks. */
public class VehicleItem extends Item {
    public final String modelId;

    public VehicleItem(String modelId, Properties p) {
        super(p);
        this.modelId = modelId;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(VehicleSpecs.get(modelId).name);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Vec3 p = ctx.getClickLocation();
        VehicleEntity v = new VehicleEntity(TCRegistry.VEHICLE.get(), level);
        VehicleSpec spec = VehicleSpecs.get(modelId);
        v.setModel(modelId);
        v.setColor(spec.colors[level.random.nextInt(spec.colors.length)]);
        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0;
        v.moveTo(p.x, Math.floor(p.y + 0.01), p.z, yaw, 0);
        v.setFuel(spec.fuelCapacity * 0.8f);
        v.setPlate(VehicleEntity.randomPlate(level.random));
        if (!level.noCollision(v, v.getBoundingBox().move(0, 0.05, 0))) v.moveTo(p.x, Math.floor(p.y) + 1.0, p.z, yaw, 0);
        level.addFreshEntity(v);
        if (ctx.getPlayer() != null && !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        VehicleSpec s = VehicleSpecs.get(modelId);
        tip.add(Component.translatable("tooltip.trafficcraft.category." + s.category).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal(Math.round(s.maxSpeedKmh) + " km/h  |  " + s.seatCount + " seats  |  " + Math.round(s.fuelCapacity) + " L")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
