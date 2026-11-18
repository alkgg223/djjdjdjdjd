package com.vehicleworks.mixin;

import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleRegistry;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Players walking on a flying vehicle: not "floating" (no kick for flying), and the fast
 * movement that comes from riding along with the vehicle is not "moved too quickly".
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "noBlocksAround(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void vehicleworks$vehicleCountsAsBlocks(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!VehicleRegistry.near(entity.level(), entity.getBoundingBox().inflate(2.5, 3.0, 2.5)).isEmpty()) cir.setReturnValue(false);
    }

    @Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V", at = @At("HEAD"))
    private void vehicleworks$rideAlongSpeed(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerPlayer p = this.player;
        if (p == null || p.isPassenger()) return;
        // the speed check compares the step with the player's own velocity: give the player the
        // velocity of the vehicle it is walking on (per tick, with margin for bunched packets)
        for (VehicleEntity v : VehicleRegistry.near(p.level(), p.getBoundingBox().inflate(32, 32, 32))) {
            double sp = v.linearVelocity.length();
            if (sp < 5) continue;
            double k = 0.05 * 3.0;
            p.setDeltaMovement(v.linearVelocity.x * k, v.linearVelocity.y * k, v.linearVelocity.z * k);
            break;
        }
    }
}
