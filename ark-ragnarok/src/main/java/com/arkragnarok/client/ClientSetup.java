package com.arkragnarok.client;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.client.render.ArkCreatureRenderer;
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
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        for (Species s : SpeciesList.all()) e.registerEntityRenderer(ModEntities.type(s.id), ctx -> new ArkCreatureRenderer(ctx, s));
    }
}
