package com.arkragnarok.client.render;

import com.arkragnarok.client.mesh.ArkMesh;
import com.arkragnarok.client.mesh.SkinnedMeshRenderer;
import com.arkragnarok.client.model.ArkCreatureGeoModel;
import com.arkragnarok.entity.ArkCreature;
import com.arkragnarok.species.Species;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * GeckoLib runs the skeleton (controllers, keyframes, procedural head/tail); this renderer draws the species'
 * smooth skinned mesh with those bones, at real size.  Three levels of detail by camera distance.
 */
public class ArkCreatureRenderer extends GeoEntityRenderer<ArkCreature> {
    private final Species species;
    private final double lod0, lod1;

    public ArkCreatureRenderer(EntityRendererProvider.Context ctx, Species species) {
        super(ctx, new ArkCreatureGeoModel(species));
        this.species = species;
        this.shadowRadius = Math.max(0.3f, species.width * 0.8f);
        this.shadowStrength = 0.9f;
        withScale(species.modelScale);
        double size = Math.max(2.0, species.lengthM);
        this.lod0 = Math.max(18, size * 3.0);
        this.lod1 = Math.max(40, size * 7.0);
    }

    /** the death animation lays the body down itself; no vanilla sideways flip */
    @Override
    protected float getDeathMaxRotation(ArkCreature animatable) {
        return 0f;
    }

    @Override
    public void actuallyRender(PoseStack poseStack, ArkCreature c, BakedGeoModel model, @Nullable RenderType renderType,
                               MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick,
                               int packedLight, int packedOverlay, int colour) {
        super.actuallyRender(poseStack, c, model, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        if (isReRender) return;
        Matrix4f base = new Matrix4f(this.modelRenderTranslations);
        int lod = lodFor(c);
        SkinnedMeshRenderer.draw(base, model, ArkMesh.get(species.mesh(lod)), species.texture(lod), bufferSource, packedLight, packedOverlay, colour);
        if (c.isSaddled() && species.saddle) {
            SkinnedMeshRenderer.draw(base, model, ArkMesh.get(species.saddleMesh(lod)), species.saddleTexture(lod), bufferSource, packedLight,
                    packedOverlay, colour);
        }
    }

    private int lodFor(ArkCreature c) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = Math.sqrt(c.distanceToSqr(cam));
        if (d < lod0) return 0;
        if (d < lod1) return 1;
        return 2;
    }
}
