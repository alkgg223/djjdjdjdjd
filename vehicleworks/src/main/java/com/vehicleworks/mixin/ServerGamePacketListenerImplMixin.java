package com.vehicleworks.mixin;

import com.vehicleworks.vehicle.VehicleRegistry;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players standing on a flying vehicle are not "floating": do not kick them for flying. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Inject(method = "noBlocksAround(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void vehicleworks$vehicleCountsAsBlocks(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!VehicleRegistry.near(entity.level(), entity.getBoundingBox().inflate(2.5, 3.0, 2.5)).isEmpty()) cir.setReturnValue(false);
    }
}
