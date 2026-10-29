package com.realweather;

import com.mojang.logging.LogUtils;
import com.realweather.net.Network;
import com.realweather.server.WeatherCommands;
import com.realweather.server.WeatherServer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(RealWeather.MODID)
public class RealWeather {
    public static final String MODID = "realweather";
    public static final Logger LOG = LogUtils.getLogger();

    public RealWeather(IEventBus modBus, ModContainer container, Dist dist) {
        ModContent.register(modBus);
        modBus.addListener(Network::register);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(WeatherCommands::register);
        NeoForge.EVENT_BUS.addListener(WeatherServer::onLevelTick);
        NeoForge.EVENT_BUS.addListener(WeatherServer::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(WeatherServer::onLogin);
        NeoForge.EVENT_BUS.addListener(WeatherServer::onChangeDimension);
        NeoForge.EVENT_BUS.addListener(WeatherServer::onRespawn);

        if (dist.isClient()) {
            com.realweather.client.ClientInit.init(modBus, container);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
