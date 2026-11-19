package com.trafficcraft;

import com.mojang.logging.LogUtils;
import com.trafficcraft.net.TCNetwork;
import com.trafficcraft.registry.TCRegistry;
import com.trafficcraft.vehicle.VehicleSpecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(TrafficCraft.MODID)
public class TrafficCraft {
    public static final String MODID = "trafficcraft";
    public static final Logger LOG = LogUtils.getLogger();

    public TrafficCraft(IEventBus modBus, ModContainer container) {
        VehicleSpecs.load();
        TCRegistry.register(modBus);
        modBus.addListener(TCNetwork::register);
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e) ->
                e.put(TCRegistry.PEDESTRIAN.get(), com.trafficcraft.npc.PedestrianEntity.attributes().build()));
        container.registerConfig(ModConfig.Type.COMMON, TCConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
