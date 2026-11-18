package com.vehicleworks.client;

import com.vehicleworks.VehicleWorks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client entry point: enables the "Config" button of VehicleWorks in the Mods screen. */
@Mod(value = VehicleWorks.MODID, dist = Dist.CLIENT)
public final class VehicleWorksClient {
    public VehicleWorksClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
