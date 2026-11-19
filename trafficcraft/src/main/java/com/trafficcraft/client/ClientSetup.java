package com.trafficcraft.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.trafficcraft.TrafficCraft;
import com.trafficcraft.client.gui.VehicleHud;
import com.trafficcraft.client.render.VehicleRenderer;
import com.trafficcraft.client.render.VehicleShaders;
import com.trafficcraft.registry.TCRegistry;
import com.trafficcraft.vehicle.VehicleEntity;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = TrafficCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent e) {
        VehicleEntity.CLIENT_INPUT = DriverInput::read;
        VehicleEntity.CLIENT_SEND = DriverInput::sendState;
        VehicleEntity.CLIENT_EVENT = com.trafficcraft.client.sound.VehicleSounds::event;
        com.trafficcraft.net.OpenEditorPayload.CLIENT_HANDLER = com.trafficcraft.client.gui.Editors::open;
        ModList.get().getModContainerById(TrafficCraft.MODID).ifPresent(c -> c.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new));
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(TCRegistry.VEHICLE.get(), VehicleRenderer::new);
        e.registerEntityRenderer(TCRegistry.PEDESTRIAN.get(), com.trafficcraft.client.render.PedRenderer::new);
        e.registerBlockEntityRenderer(com.trafficcraft.registry.TCBlocks.TRAFFIC_LIGHT_BE.get(), com.trafficcraft.client.render.BlockRenderers.TrafficLightRenderer::new);
        e.registerBlockEntityRenderer(com.trafficcraft.registry.TCBlocks.BARRIER_GATE_BE.get(), com.trafficcraft.client.render.BlockRenderers.BarrierRenderer::new);
        e.registerBlockEntityRenderer(com.trafficcraft.registry.TCBlocks.GENERIC_BE.get(), com.trafficcraft.client.render.BlockRenderers.GenericRenderer::new);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        for (var k : VehicleKeys.ALL) e.register(k);
    }

    @SubscribeEvent
    public static void gui(RegisterGuiLayersEvent e) {
        e.registerAboveAll(TrafficCraft.id("vehicle_hud"), VehicleHud::render);
    }

    @SubscribeEvent
    public static void shaders(RegisterShadersEvent e) throws IOException {
        e.registerShader(new ShaderInstance(e.getResourceProvider(), TrafficCraft.id("vehicle"), DefaultVertexFormat.NEW_ENTITY), s -> VehicleShaders.vehicle = s);
    }
}
