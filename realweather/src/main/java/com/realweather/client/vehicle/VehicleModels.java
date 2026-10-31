package com.realweather.client.vehicle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.realweather.RealWeather;
import com.realweather.vehicle.VehicleType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.HashMap;
import java.util.Map;

/** The vehicle part models (OBJ, loaded as standalone models) and drawing them as entity geometry. */
public final class VehicleModels {
    private static final String[] PARTS = {"body", "glass", "wheel", "cover", "anemometer", "dish", "spike", "skirt", "turret"};
    private static final String[] SHARED = {"steering", "pedal", "pedal_wide", "gear", "wiper"};
    private static final Map<String, ModelResourceLocation> IDS = new HashMap<>();

    private VehicleModels() {}

    public static ModelResourceLocation id(String path) {
        return IDS.computeIfAbsent(path, p -> ModelResourceLocation.standalone(RealWeather.id(p)));
    }

    public static void register(ModelEvent.RegisterAdditional event) {
        for (VehicleType t : VehicleType.values()) {
            for (String part : PARTS) {
                if (Minecraft.getInstance().getResourceManager().getResource(RealWeather.id("models/vehicle/" + t.id + "/" + part + ".json")).isPresent()) {
                    event.register(id("vehicle/" + t.id + "/" + part));
                }
            }
        }
        for (String part : SHARED) {
            event.register(id("vehicle/parts/" + part));
        }
    }

    public static BakedModel model(String path) {
        return Minecraft.getInstance().getModelManager().getModel(id(path));
    }

    private static boolean missing(BakedModel m) {
        return m == Minecraft.getInstance().getModelManager().getMissingModel();
    }

    /** Opaque parts; tinted faces (paint) take the colour. No back-face culling, so the cabin works from inside too. */
    public static void solid(String path, PoseStack ps, MultiBufferSource buf, int light, int overlay, float r, float g, float b) {
        BakedModel m = model(path);
        if (missing(m)) {
            return;
        }
        RenderType rt = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer()
                .renderModel(ps.last(), buf.getBuffer(rt), null, m, r, g, b, light, overlay, ModelData.EMPTY, rt);
    }

    /** See-through parts (glass, snow cover) with an extra alpha. */
    public static void translucent(String path, PoseStack ps, MultiBufferSource buf, int light, int overlay, float alpha) {
        BakedModel m = model(path);
        if (missing(m) || alpha <= 0.01F) {
            return;
        }
        RenderType rt = RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS);
        VertexConsumer vc = buf.getBuffer(rt);
        if (alpha < 0.999F) {
            vc = new AlphaConsumer(vc, alpha);
        }
        Minecraft.getInstance().getBlockRenderer().getModelRenderer()
                .renderModel(ps.last(), vc, null, m, 1F, 1F, 1F, light, overlay, ModelData.EMPTY, rt);
    }

    /** Scales the alpha of everything written through it. */
    static final class AlphaConsumer implements VertexConsumer {
        private final VertexConsumer inner;
        private final float alpha;

        AlphaConsumer(VertexConsumer inner, float alpha) {
            this.inner = inner;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            inner.setColor(r, g, b, Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            inner.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }
    }
}
