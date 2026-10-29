package com.realweather.client;

import com.realweather.ModContent;
import com.realweather.RealWeather;
import com.realweather.client.particle.WeatherParticles;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientInit {
    private ClientInit() {}

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(ClientInit::onSetup);
        modBus.addListener(ClientInit::onDimensionEffects);
        modBus.addListener(ClientInit::onGuiLayers);
        modBus.addListener(ClientInit::onClientExtensions);
        modBus.addListener(WeatherParticles::register);

        NeoForge.EVENT_BUS.addListener(ClientWeather::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientWeather::onLogout);
        NeoForge.EVENT_BUS.addListener(WeatherSounds::onPlaySound);
        NeoForge.EVENT_BUS.addListener(WeatherSounds::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(FogEffects::onRenderFog);
        NeoForge.EVENT_BUS.addListener(FogEffects::onFogColor);
        NeoForge.EVENT_BUS.addListener(FogEffects::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(WaterShader::onRenderStage);
        NeoForge.EVENT_BUS.addListener(com.realweather.client.tornado.TornadoRenderer::onRenderStage);
        modBus.addListener(ClientInit::onEntityRenderers);
    }

    private static void onEntityRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, RealLightningRenderer::new);
    }

    private static void onSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModContent.FLOOD.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModContent.FLOOD_FLOWING.get(), RenderType.translucent());
        });
    }

    private static void onDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(BuiltinDimensionTypes.OVERWORLD_EFFECTS, new RealOverworldEffects());
    }

    private static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, RealWeather.id("weather_overlay"), WeatherOverlay::render);
    }

    private static void onClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new FloodWaterRendering(), ModContent.FLOOD_TYPE.get());
    }
}
