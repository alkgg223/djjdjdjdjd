package com.arkragnarok.client.render;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.client.mesh.ArkMesh;
import com.arkragnarok.client.mesh.SkinnedMeshRenderer;
import com.arkragnarok.client.model.RexGeoModel;
import com.arkragnarok.entity.RexEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * GeckoLib runs the skeleton (controllers, keyframes, procedural head/tail); this renderer then draws the smooth
 * skinned Rex mesh with those bones.  Three levels of detail are picked by camera distance.
 */
public class RexRenderer extends GeoEntityRenderer<RexEntity> {
    private static final ResourceLocation[] MESH = {ArkRagnarok.id("arkmesh/rex_lod0.arkm"), ArkRagnarok.id("arkmesh/rex_lod1.arkm"),
            ArkRagnarok.id("arkmesh/rex_lod2.arkm")};
    private static final ResourceLocation[] TEX = {ArkRagnarok.id("textures/entity/rex/rex_lod0.png"),
            ArkRagnarok.id("textures/entity/rex/rex_lod1.png"), ArkRagnarok.id("textures/entity/rex/rex_lod2.png")};
    private static final ResourceLocation[] SADDLE_MESH = {ArkRagnarok.id("arkmesh/rex_saddle_lod0.arkm"),
            ArkRagnarok.id("arkmesh/rex_saddle_lod1.arkm"), ArkRagnarok.id("arkmesh/rex_saddle_lod2.arkm")};
    private static final ResourceLocation[] SADDLE_TEX = {ArkRagnarok.id("textures/entity/rex/rex_saddle_lod0.png"),
            ArkRagnarok.id("textures/entity/rex/rex_saddle_lod1.png"), ArkRagnarok.id("textures/entity/rex/rex_saddle_lod2.png")};
    public static final float MODEL_SCALE = 0.8f;

    public RexRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new RexGeoModel());
        this.shadowRadius = 2.4f;
        this.shadowStrength = 0.9f;
        withScale(MODEL_SCALE);
    }

    /** the death animation lays the body down itself; no vanilla sideways flip */
    @Override
    protected float getDeathMaxRotation(RexEntity animatable) {
        return 0f;
    }

    @Override
    public void actuallyRender(PoseStack poseStack, RexEntity rex, BakedGeoModel model, @Nullable RenderType renderType,
                               MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick,
                               int packedLight, int packedOverlay, int colour) {
        super.actuallyRender(poseStack, rex, model, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        if (isReRender) return;
        Matrix4f base = new Matrix4f(this.modelRenderTranslations);
        int lod = lodFor(rex);
        SkinnedMeshRenderer.draw(base, model, ArkMesh.get(MESH[lod]), TEX[lod], bufferSource, packedLight, packedOverlay, colour);
        if (rex.isSaddled()) {
            SkinnedMeshRenderer.draw(base, model, ArkMesh.get(SADDLE_MESH[lod]), SADDLE_TEX[lod], bufferSource, packedLight, packedOverlay, colour);
        }
    }

    private static int lodFor(RexEntity rex) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d2 = rex.distanceToSqr(cam);
        if (d2 < 30 * 30) return 0;
        if (d2 < 70 * 70) return 1;
        return 2;
    }
}
