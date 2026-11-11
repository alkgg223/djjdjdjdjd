package com.rpbusiness.client;

import com.rpbusiness.RPBusiness;
import com.rpbusiness.client.render.FoodRenderers;
import com.rpbusiness.client.render.NpcRenderer;
import com.rpbusiness.client.render.StorageGeoRenderer;
import com.rpbusiness.core.ModBlockEntities;
import com.rpbusiness.core.ModEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = RPBusiness.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.CUSTOMER.get(), NpcRenderer::new);
        e.registerEntityRenderer(ModEntities.EMPLOYEE.get(), NpcRenderer::new);
        e.registerEntityRenderer(ModEntities.COURIER.get(), NpcRenderer::new);
        e.registerEntityRenderer(ModEntities.SEAT.get(), NoopRenderer::new);
        e.registerBlockEntityRenderer(ModBlockEntities.CUTTING_BOARD.get(), FoodRenderers.CuttingBoard::new);
        e.registerBlockEntityRenderer(ModBlockEntities.COOKER.get(), FoodRenderers.Cooker::new);
        e.registerBlockEntityRenderer(ModBlockEntities.PREP_STATION.get(), FoodRenderers.Prep::new);
        e.registerBlockEntityRenderer(ModBlockEntities.DRINK_MACHINE.get(), FoodRenderers.Drink::new);
        e.registerBlockEntityRenderer(ModBlockEntities.SERVING_COUNTER.get(), FoodRenderers.Serving::new);
        e.registerBlockEntityRenderer(ModBlockEntities.ANIMATED_STORAGE.get(), c -> new StorageGeoRenderer());
        e.registerBlockEntityRenderer(ModBlockEntities.DISPLAY.get(), FoodRenderers.Display::new);
    }

    @SubscribeEvent
    public static void guiLayers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(RPBusiness.id("hud"), Hud::render);
    }
}
