package com.vehicleworks.gametest;

import com.vehicleworks.VehicleWorks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@EventBusSubscriber(modid = VehicleWorks.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class GameTestRegistration {
    private GameTestRegistration() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(VehicleGameTests.class);
    }
}
