package com.gunsmod.client.render;

import com.gunsmod.Reg;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Helpers for drawing the side-loaded (standalone) JSON models. */
public final class BakedRender {
    private BakedRender() {}

    private static final Map<String, ModelResourceLocation> IDS = new HashMap<>();

    public static ModelResourceLocation id(String path) {
        return IDS.computeIfAbsent(path, p -> ModelResourceLocation.standalone(Reg.id(p)));
    }

    public static BakedModel model(String path) {
        return Minecraft.getInstance().getModelManager().getModel(id(path));
    }

    /** draw a model in its own coordinates (the pose is at the model origin, vertices are in blocks) through the item pipeline */
    public static void item(ItemStack host, String path, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        ps.pushPose();
        ps.translate(0.5f, 0.5f, 0.5f);
        Minecraft.getInstance().getItemRenderer().render(host, ItemDisplayContext.NONE, false, ps, buf, light, overlay, model(path));
        ps.popPose();
    }

    /** draw a model as entity geometry; tinted faces (tintindex 0) get the colour */
    public static void entity(String path, PoseStack ps, MultiBufferSource buf, int light, int overlay, float r, float g, float b) {
        RenderType rt = RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer()
                .renderModel(ps.last(), buf.getBuffer(rt), null, model(path), r, g, b, light, overlay, ModelData.EMPTY, rt);
    }

    public static void entityCutout(String path, PoseStack ps, MultiBufferSource buf, int light, int overlay, float r, float g, float b) {
        RenderType rt = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer()
                .renderModel(ps.last(), buf.getBuffer(rt), null, model(path), r, g, b, light, overlay, ModelData.EMPTY, rt);
    }

    public static ResourceLocation tex(String path) {
        return Reg.id(path);
    }
}
