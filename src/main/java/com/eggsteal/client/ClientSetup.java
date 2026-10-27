package com.eggsteal.client;

import com.eggsteal.EggSteal;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = EggSteal.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(EggSteal.CREATURE.get(), CreatureRenderer::new);
        e.registerEntityRenderer(EggSteal.BOT.get(), BotRenderer::new);
        e.registerEntityRenderer(EggSteal.AUCTION.get(), AuctionEggRenderer::new);
    }
}
