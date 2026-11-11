package com.rpbusiness;

import com.rpbusiness.core.*;
import com.rpbusiness.network.Net;
import com.rpbusiness.server.ServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Roleplay Business Simulator - singleplayer business / restaurant / NPC simulation.
 * All simulation runs on Minecraft's integrated server inside the player's own world.
 */
@Mod(RPBusiness.MODID)
public class RPBusiness {
    public static final String MODID = "rpbusiness";
    public static final Logger LOG = LoggerFactory.getLogger("RPBusiness");

    public RPBusiness(IEventBus modBus, ModContainer container) {
        ModComponents.REG.register(modBus);
        ModBlocks.REG.register(modBus);
        ModItems.REG.register(modBus);
        ModBlockEntities.REG.register(modBus);
        ModEntities.REG.register(modBus);
        ModSounds.REG.register(modBus);
        ModTabs.REG.register(modBus);
        modBus.addListener(ModEntities::attributes);
        modBus.addListener(Net::register);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
