package com.arkragnarok.client;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.client.render.ArkCreatureRenderer;
import com.arkragnarok.client.render.PieceRenderer;
import com.arkragnarok.client.render.ResourceNodeRenderer;
import com.arkragnarok.registry.ModBuilding;
import com.arkragnarok.client.render.TranqArrowRenderer;
import com.arkragnarok.registry.ModEntities;
import com.arkragnarok.species.Species;
import com.arkragnarok.species.SpeciesList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ArkRagnarok.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e) {
        e.register(com.arkragnarok.registry.ModMenus.STATION.get(), com.arkragnarok.client.hud.StationScreen::new);
        e.register(com.arkragnarok.registry.ModMenus.TRIBES.get(), com.arkragnarok.client.hud.TribeScreen::new);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        for (Species s : SpeciesList.all()) e.registerEntityRenderer(ModEntities.type(s.id), ctx -> new ArkCreatureRenderer(ctx, s));
        e.registerEntityRenderer(ModEntities.TRANQ_ARROW.get(), TranqArrowRenderer::new);
        e.registerEntityRenderer(ModEntities.RESOURCE_NODE.get(), ResourceNodeRenderer::new);
        e.registerBlockEntityRenderer(ModBuilding.PIECE_BE.get(), PieceRenderer::new);
        e.registerEntityRenderer(ModEntities.SURVIVOR.get(), com.arkragnarok.client.render.SurvivorRenderer::new);
    }
}
