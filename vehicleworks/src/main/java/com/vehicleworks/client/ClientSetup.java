package com.vehicleworks.client;

import com.vehicleworks.VehicleWorks;
import com.vehicleworks.registry.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = VehicleWorks.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    static {
        // install the client proxy as early as possible (before any payload or entity tick)
        VehicleWorks.PROXY = new ClientProxy();
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent e) {
        VehicleWorks.PROXY = new ClientProxy();
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onInteraction);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onCameraDistance);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onSwapHands);
        NeoForge.EVENT_BUS.addListener(RenderEvents::onRenderStage);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.VEHICLE.get(), VehicleRenderer::new);
        e.registerEntityRenderer(ModEntities.PROJECTILE.get(), ProjectileRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent e) {
        Keybinds.register(e);
    }

    @SubscribeEvent
    public static void onRegisterGui(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(VehicleWorks.MODID, "vehicle_hud"), VehicleHud::render);
    }
}
