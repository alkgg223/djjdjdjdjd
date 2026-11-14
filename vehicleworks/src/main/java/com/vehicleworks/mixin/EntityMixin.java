package com.vehicleworks.mixin;

import com.vehicleworks.vehicle.EntityVehicleCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void vehicleworks$collideWithVehicles(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        Vec3 result = EntityVehicleCollision.collide((Entity) (Object) this, movement, cir.getReturnValue());
        if (result != cir.getReturnValue()) cir.setReturnValue(result);
    }

    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("TAIL"))
    private void vehicleworks$afterMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        EntityVehicleCollision.afterMove((Entity) (Object) this);
    }
}
