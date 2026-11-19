package com.trafficcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.trafficcraft.TrafficCraft;
import com.trafficcraft.npc.PedSkins;
import com.trafficcraft.npc.PedestrianEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Player-shaped pedestrians with 18 skins (wide and slim arms) and job / mood poses. */
public class PedRenderer extends HumanoidMobRenderer<PedestrianEntity, PlayerModel<PedestrianEntity>> {
    private static final ResourceLocation[] TEX = new ResourceLocation[PedSkins.NAMES.length];
    static {
        for (int i = 0; i < TEX.length; i++) TEX[i] = TrafficCraft.id("textures/entity/ped/" + PedSkins.NAMES[i] + ".png");
    }
    private final PlayerModel<PedestrianEntity> wide, slim;

    public PedRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PedModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.45f);
        wide = model;
        slim = new PedModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(PedestrianEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        model = PedSkins.SLIM[Math.floorMod(e.skin(), PedSkins.SLIM.length)] ? slim : wide;
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PedestrianEntity e) {
        return TEX[Math.floorMod(e.skin(), TEX.length)];
    }

    static final class PedModel extends PlayerModel<PedestrianEntity> {
        PedModel(net.minecraft.client.model.geom.ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void setupAnim(PedestrianEntity e, float limb, float limbAmt, float age, float headYaw, float headPitch) {
            super.setupAnim(e, limb, limbAmt, age, headYaw, headPitch);
            float t = age * 0.1f;
            switch (e.anim()) {
                case PedestrianEntity.A_IDLE -> {
                    rightArm.xRot = Mth.sin(t) * 0.04f;
                    leftArm.xRot = -Mth.sin(t) * 0.04f;
                }
                case PedestrianEntity.A_WAIT -> { // arms crossed-ish, waiting at the kerb
                    rightArm.xRot = -0.9f;
                    rightArm.yRot = -0.55f;
                    leftArm.xRot = -0.9f;
                    leftArm.yRot = 0.55f;
                    head.yRot += Mth.sin(t * 0.6f) * 0.5f; // looks left and right
                }
                case PedestrianEntity.A_PHONE -> {
                    rightArm.xRot = -2.3f;
                    rightArm.yRot = -0.45f;
                    rightArm.zRot = 0.35f;
                    head.xRot = 0.15f;
                    head.zRot = 0.12f;
                }
                case PedestrianEntity.A_SCAN -> { // cashier scanning items
                    float s = Mth.sin(age * 0.35f);
                    rightArm.xRot = -1.15f + s * 0.15f;
                    rightArm.yRot = -0.25f + s * 0.4f;
                    leftArm.xRot = -1.0f;
                    leftArm.yRot = 0.2f;
                    head.xRot = 0.35f;
                }
                case PedestrianEntity.A_REFUEL -> { // holding the nozzle in the filler neck
                    rightArm.xRot = -1.25f;
                    rightArm.yRot = -0.15f;
                    leftArm.xRot = -0.55f;
                    leftArm.zRot = -0.12f;
                    head.xRot = 0.3f;
                }
                case PedestrianEntity.A_INSPECT -> { // checking documents at the driver window
                    leftArm.xRot = -1.2f;
                    leftArm.yRot = 0.35f;
                    rightArm.xRot = -1.0f + Mth.sin(age * 0.15f) * 0.12f;
                    rightArm.yRot = -0.35f;
                    head.xRot = 0.25f + Mth.sin(age * 0.05f) * 0.1f;
                }
                case PedestrianEntity.A_SCARED -> {
                    rightArm.xRot = -2.7f;
                    leftArm.xRot = -2.7f;
                    rightArm.zRot = 0.4f;
                    leftArm.zRot = -0.4f;
                }
                case PedestrianEntity.A_WAVE -> {
                    rightArm.xRot = -2.6f;
                    rightArm.zRot = 0.4f + Mth.sin(age * 0.8f) * 0.35f;
                }
                default -> { }
            }
            hat.copyFrom(head);
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
        }
    }
}
