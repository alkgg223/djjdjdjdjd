package com.vehicleworks.mixin;

import com.vehicleworks.client.ClientPick;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Refines the crosshair target so individual vehicle blocks can be targeted. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void vehicleworks$pickVehicleBlocks(float partialTicks, CallbackInfo ci) {
        ClientPick.refine(partialTicks);
    }
}
