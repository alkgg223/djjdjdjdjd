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
        modBus.addListener(com.realweather.client.sky.VolumeClouds::registerShaders);
        modBus.addListener(com.realweather.client.space.SpaceShaders::register);
        modBus.addListener(com.realweather.client.sky.GodRays::register);

        NeoForge.EVENT_BUS.addListener(ClientWeather::onClientTick);
        // The ray-traced space passes keep to what this GPU can draw in time.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderFrameEvent.Pre e) -> com.realweather.client.space.SpaceShaders.frame());
        // The telescope: its zoom, slower turning when zoomed in, no walking or digging at the eyepiece.
        NeoForge.EVENT_BUS.addListener(com.realweather.client.space.Telescope::onFov);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOW, com.realweather.client.space.Telescope::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(com.realweather.client.space.Telescope::onScroll);
        NeoForge.EVENT_BUS.addListener(com.realweather.client.space.Telescope::onInteract);
        NeoForge.EVENT_BUS.addListener(com.realweather.client.space.Telescope::onMovement);
        NeoForge.EVENT_BUS.addListener(com.realweather.client.space.Telescope::onRenderHand);
        NeoForge.EVENT_BUS.addListener(com.realweather.client.space.Telescope::onHighlight);
        NeoForge.EVENT_BUS.addListener(ClientCommands::register);
        NeoForge.EVENT_BUS.addListener(ClientWeather::onLogout);
        NeoForge.EVENT_BUS.addListener(WeatherSounds::onPlaySound);
        NeoForge.EVENT_BUS.addListener(WeatherSounds::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(FogEffects::onRenderFog);
        NeoForge.EVENT_BUS.addListener(FogEffects::onFogColor);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOW, com.realweather.client.space.EarthView::onFogColor);
        NeoForge.EVENT_BUS.addListener(FogEffects::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(WaterShader::onRenderStage);
        // The eclipse first: the clouds pass in front of it.
        NeoForge.EVENT_BUS.addListener(com.realweather.client.sky.EclipseRenderer::onRenderStage);
        // Sky events (planets, comets, satellites, the aurora...) behind the clouds too.
        NeoForge.EVENT_BUS.addListener(com.realweather.client.sky.SpaceRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(com.realweather.client.space.PlanetSky::onRenderStage);
        NeoForge.EVENT_BUS.addListener(com.realweather.client.sky.GodRays::onRenderStage);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOW, com.realweather.client.space.PlanetSky::onFogColor);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOW, com.realweather.client.space.PlanetSky::onRenderFog);
        // Out in space: flying (the movement keys move you through space), and cosmic ray showers in the air.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOW, com.realweather.client.space.SpaceFlight::onInput);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) ->
                com.realweather.client.space.SpaceFlight.tick(net.minecraft.client.Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) ->
                com.realweather.client.space.CosmicEvents.tick(net.minecraft.client.Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(com.realweather.client.sky.CosmicRays::onRenderStage);
        NeoForge.EVENT_BUS.addListener(com.realweather.client.sky.SkyRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(com.realweather.client.tsunami.TsunamiRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(com.realweather.client.quake.CrackRenderer::onRenderStage);
        modBus.addListener(ClientInit::onEntityRenderers);
        modBus.addListener(com.realweather.client.vehicle.VehicleModels::register);
        modBus.addListener(com.realweather.client.vehicle.VehicleClient::onKeys);
        modBus.addListener(com.realweather.client.space.Telescope::onKeys);
        NeoForge.EVENT_BUS.addListener(com.realweather.client.vehicle.VehicleClient::onClientTick);
        com.realweather.client.vehicle.VehicleClient.init();
        modBus.addListener(ClientInit::onAddLayers);
        NeoForge.EVENT_BUS.addListener(com.realweather.client.hail.Footprints::onRenderStage);
    }

    private static void onEntityRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, RealLightningRenderer::new);
        event.registerBlockEntityRenderer(com.realweather.device.ModDevices.MONITOR_BE.get(), com.realweather.client.radar.MonitorRenderer::new);
        event.registerBlockEntityRenderer(com.realweather.device.ModDevices.RADAR_BE.get(), com.realweather.client.radar.RadarDishRenderer::new);
        for (com.realweather.vehicle.VehicleType kind : com.realweather.vehicle.VehicleType.values()) {
            event.registerEntityRenderer(com.realweather.vehicle.VehicleRegistry.type(kind), com.realweather.client.vehicle.VehicleRenderer::new);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onAddLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.world.entity.EntityType<?> type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer r) {
                r.addLayer(new com.realweather.client.hail.HailLayer(r));
            }
        }
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer r) {
                r.addLayer(new com.realweather.client.hail.HailLayer(r));
            }
        }
    }

    private static void onSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModContent.FLOOD.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModContent.FLOOD_FLOWING.get(), RenderType.translucent());
        });
    }

    private static void onDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(BuiltinDimensionTypes.OVERWORLD_EFFECTS, new RealOverworldEffects());
        // The skies of other worlds are drawn by PlanetSky.
        event.register(RealWeather.id("planets"), new com.realweather.client.space.PlanetSky.Effects());
    }

    private static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, RealWeather.id("weather_overlay"), WeatherOverlay::render);
        event.registerAbove(RealWeather.id("weather_overlay"), RealWeather.id("telescope"), com.realweather.client.space.Telescope::renderHud);
        event.registerAbove(RealWeather.id("telescope"), RealWeather.id("space_flight"), com.realweather.client.space.SpaceFlight::hud);
        event.registerBelow(VanillaGuiLayers.HOTBAR, RealWeather.id("tsunami_water"), com.realweather.client.tsunami.TsunamiOverlay::render);
        event.registerBelow(VanillaGuiLayers.HOTBAR, RealWeather.id("eclipse_glasses"), com.realweather.client.sky.GlassesOverlay::render);
        event.registerAbove(VanillaGuiLayers.HOTBAR, RealWeather.id("vehicle_hud"), com.realweather.client.vehicle.VehicleHud::render);
    }

    private static void onClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new FloodWaterRendering(), ModContent.FLOOD_TYPE.get());
    }
}
