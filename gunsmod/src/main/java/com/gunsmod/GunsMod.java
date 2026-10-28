package com.gunsmod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@Mod(GunsMod.MODID)
public class GunsMod {
    public static final String MODID = "gunsmod";

    public GunsMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            if ("server".equals(System.getProperty("gunsmod.citest"))) {
                System.out.println("[gunsmod-ci] server started, halting");
                e.getServer().halt(false);
            }
        });
    }
}
